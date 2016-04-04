/*
 * Copyright (C) 2016 jollion
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package plugins.plugins.trackers.trackMate;

import core.Processor;
import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import static fiji.plugin.trackmate.tracking.LAPUtils.checkFeatureMap;
import fiji.plugin.trackmate.tracking.SpotTracker;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_ALTERNATIVE_LINKING_COST_FACTOR;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_LINKING_FEATURE_PENALTIES;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_LINKING_MAX_DISTANCE;
import fiji.plugin.trackmate.tracking.sparselap.costfunction.CostFunction;
import fiji.plugin.trackmate.tracking.sparselap.costfunction.FeaturePenaltyCostFunction;
import fiji.plugin.trackmate.tracking.sparselap.costfunction.SquareDistCostFunction;
import fiji.plugin.trackmate.tracking.sparselap.costmatrix.JaqamanLinkingCostMatrixCreator;
import fiji.plugin.trackmate.tracking.sparselap.linker.JaqamanLinker;
import static fiji.plugin.trackmate.util.TMUtils.checkMapKeys;
import static fiji.plugin.trackmate.util.TMUtils.checkParameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.imglib2.algorithm.MultiThreadedBenchmarkAlgorithm;
import net.imglib2.multithreading.SimpleMultiThreading;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import utils.Pair;

/**
 * Adapted from fiji.plugin.trackmate.tracking.sparselap.SparseLAPFrameToFrameTracker: link spots of low quality to spots of high quality or spots of low quality to other spots of low quality that have already been linked to spots of high quality

 * @author jollion
 */
public class FrameToFrameSpotQualityTracker  extends MultiThreadedBenchmarkAlgorithm {
        public final static String KEY_MAX_FRAME_GAP_LQ = "MAX FRAME GAP LQ";

	private final static String BASE_ERROR_MESSAGE = "FrameToFrameSpotQualityTracker ";

	private final SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph;

	private Logger logger = Logger.VOID_LOGGER;

	private final SpotCollection spots;

	private final Map< String, Object > settings;
        private final int maxFrameGap;
        private final int minFrame, maxFrame;
        private double maxDist;
	/*
	 * CONSTRUCTOR
	 */

	public FrameToFrameSpotQualityTracker( final SpotCollection spots, SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph, final Map< String, Object > settings, int minFrame, int maxFrame)
	{
		this.spots = spots;
		this.settings = settings;
                this.graph=graph;
                this.maxFrameGap = (int)Math.max(1, ((Number)settings.getOrDefault(KEY_MAX_FRAME_GAP_LQ, 1)).intValue());
                this.minFrame=minFrame;
                this.maxFrame=maxFrame;
	}

	/*
	 * METHODS
	 */
        @Override 
        public void setNumThreads(int numThreads) {
            this.numThreads=1; // only monothreaded in order to propagate linkage feature
        }
        @Override 
        public void setNumThreads() {
            this.numThreads=1; // only monothreaded in order to propagate linkage feature
        }
	public SimpleWeightedGraph< Spot, DefaultWeightedEdge > getResult()
	{
		return graph;
	}
        
        @Override
	public boolean checkInput()
	{
		return true;
	}

	public boolean process() {
            /*
             * Check input now.
             */

            // Check that the objects list itself isn't null
            if ( null == spots )
            {
                    errorMessage = BASE_ERROR_MESSAGE + "The spot collection is null.";
                    return false;
            }

            // Check that the objects list contains inner collections.
            if ( spots.keySet().isEmpty() )
            {
                    errorMessage = BASE_ERROR_MESSAGE + "The spot collection is empty.";
                    return false;
            }


            // Check that at least one inner collection contains an object.
            boolean empty = true;
            for ( final int frame : spots.keySet() )
            {
                    if ( spots.getNSpots( frame, true ) > 0 )
                    {
                            empty = false;
                            break;
                    }
            }
            if ( empty )
            {
                    errorMessage = BASE_ERROR_MESSAGE + "The spot collection is empty.";
                    return false;
            }
            // Check parameters
            final StringBuilder errorHolder = new StringBuilder();
            if ( !checkSettingsValidity( settings, errorHolder ) )
            {
                    errorMessage = BASE_ERROR_MESSAGE + errorHolder.toString();
                    return false;
            }
            maxDist = (( Double ) settings.get( KEY_LINKING_MAX_DISTANCE ));
            final double costThreshold = maxDist * maxDist;
            
            /*
             * Process.
             */

            final long start = System.currentTimeMillis();

            // step 1: fill gaps
            List<Gap> gaps = new ArrayList<Gap>();
            List<DefaultWeightedEdge> edgeList = new ArrayList<DefaultWeightedEdge>(graph.edgeSet());
            for (DefaultWeightedEdge e : edgeList) {
                SpotWithinCompartment target = (SpotWithinCompartment)graph.getEdgeTarget(e);
                SpotWithinCompartment source = (SpotWithinCompartment)graph.getEdgeSource(e);
                if (Math.abs(target.timePoint - source.timePoint)>1) {
                    graph.removeEdge(e);
                    gaps.add(new Gap(source, target, e));
                    Processor.logger.debug("remove gap from graph source: {}, target: {}", source, target);
                }
            }
            Collections.sort(gaps); // start with smaller gaps
            
            
            // Prepare frame pairs in order, not necessarily separated by 1.
            final ArrayList< int[] > framePairs = new ArrayList< int[] >();
            for (int gap = 1; gap<=maxFrameGap; ++gap) {
                for (int frame = minFrame; frame<=maxFrame-gap; ++frame) framePairs.add(new int[]{frame, frame+gap}); // fwd
                for (int frame = maxFrame; frame>=minFrame+gap; --frame) framePairs.add(new int[]{frame, frame-gap}); // reverse
            }
            
            // Prepare cost function
            @SuppressWarnings( "unchecked" )
            final Map< String, Double > featurePenalties = ( Map< String, Double > ) settings.get( KEY_LINKING_FEATURE_PENALTIES );
            final CostFunction< Spot, Spot > costFunction;
            if ( null == featurePenalties || featurePenalties.isEmpty() )
            {
                    costFunction = new SquareDistCostFunction();
            }
            else
            {
                    costFunction = new FeaturePenaltyCostFunction( featurePenalties );
            }
            
            final double alternativeCostFactor = ( Double ) settings.get( KEY_ALTERNATIVE_LINKING_COST_FACTOR );

            logger.setStatus( "Frame to frame linking..." );
            boolean ok = true;
            for ( int i = 0; i < framePairs.size(); i++ ) {

                // Get frame pairs
                int frame0 = framePairs.get( i )[ 0 ];
                int frame1 = framePairs.get( i )[ 1 ];

                // Get spots - we have to create a list from each
                // content.
                final List< Spot > sources = new ArrayList< Spot >( spots.getNSpots( frame0, true ) );
                for ( final Iterator< Spot > iterator = spots.iterator( frame0, true ); iterator.hasNext(); )
                {
                        sources.add( iterator.next() );
                }

                final List< Spot > targets = new ArrayList< Spot >( spots.getNSpots( frame1, true ) );
                for ( final Iterator< Spot > iterator = spots.iterator( frame1, true ); iterator.hasNext(); )
                {
                        targets.add( iterator.next() );
                }

                // remove spots that have already been linked between the two time points or remove gap edge from the graph
                Iterator<Spot> it = sources.iterator();
                while(it.hasNext()) {
                    Spot source = it.next();
                    if (!graph.containsVertex(source)) continue;
                    List<DefaultWeightedEdge> edgeList = new ArrayList<DefaultWeightedEdge>(graph.edgesOf(source));
                    Processor.logger.debug("source: {}, f0: {}, f1: {}, edges: {}", source, frame0, frame1, edgeList.size());
                    for (DefaultWeightedEdge e : edgeList) {
                        Spot target = graph.getEdgeTarget(e);
                        if (target==source) target = graph.getEdgeSource(e);
                        int tt = ((SpotWithinCompartment)target).timePoint ;
                        int ts = ((SpotWithinCompartment)source).timePoint;
                        int dt = tt - ts;
                        Processor.logger.debug("source: {}, target: {}, dt {}", source, target, dt);
                        if (tt == frame1) {
                            targets.remove(target);
                            it.remove();
                            Processor.logger.debug("remove source: {}, target: {}", source, target);
                        }
                    }
                }

                if ( sources.isEmpty() || targets.isEmpty() ) continue;



                /*
                 * Run the linker.
                 */

                final JaqamanLinkingCostMatrixCreator< Spot, Spot > creator = new JaqamanLinkingCostMatrixCreator< Spot, Spot >( sources, targets, costFunction, costThreshold, alternativeCostFactor, 1d );
                final JaqamanLinker< Spot, Spot > linker = new JaqamanLinker< Spot, Spot >( creator );
                if ( !linker.checkInput() || !linker.process() ) {
                    ok = false;
                    break;
                }

                /*
                 * Update graph.
                 */

                final Map< Spot, Double > costs = linker.getAssignmentCosts();
                final Map< Spot, Spot > assignment = linker.getResult();
                for ( final Spot source : assignment.keySet() )
                {
                        final double cost = costs.get( source );
                        final Spot target = assignment.get( source );
                        graph.addVertex( source );
                        graph.addVertex( target );
                        final DefaultWeightedEdge edge = graph.addEdge( source, target );
                        graph.setEdgeWeight( edge, cost );
                        ((SpotWithinCompartment)source).isLinkable=true;
                        ((SpotWithinCompartment)target).isLinkable=true;
                }

                logger.setProgress( i / framePairs.size() );

            }

            // gap-processing: if spots are not linked through low quality spots remove those spots and put the old link
            List<Spot> linkers = new ArrayList<Spot>();
            for (Entry<Pair<SpotWithinCompartment, SpotWithinCompartment>, DefaultWeightedEdge> e : gaps.entrySet()) {
                if (!spotLinked(e.getKey().key, e.getKey().value, linkers)) {
                    // remove links with low quality spots and put gaped link
                    for (Spot s : linkers) graph.removeVertex(s);
                    graph.addEdge(e.getKey().key, e.getKey().value, e.getValue());
                    Processor.logger.debug("gap unfilled: {}-{} nb of lq spots removed: {}", e.getKey().key, e.getKey().value, linkers.size());
                } else Processor.logger.debug("gap filled: {}-{} nb of lq spots {}", e.getKey().key, e.getKey().value, linkers.size());
                linkers.clear();
            }
            logger.setProgress( 1d );
            logger.setStatus( "" );

            final long end = System.currentTimeMillis();
            processingTime = end - start;

            return ok;
	}
        private boolean spotLinked(Spot source, Spot target, List<Spot> linkers) {
            Spot n = getNext(source);
            while (n!=null) {
                if (n==target) return true;
                else {
                    linkers.add(n);
                    n = getNext(n);
                }
            }
            // no link can be done, add also previous spots of target
            n = getPrev(target);
            while (n!=null) {
                if (n==source) throw new Error("error with linkers between: "+source.toString()+" and "+target.toString());
                else {
                    linkers.add(n);
                    n = getPrev(n);
                }
            }
            return false;
        }
        private Spot getNext(Spot source) { // based on the assumption: one spot is linked to maximum one other spot
            for (DefaultWeightedEdge e : graph.edgesOf(source)) {
                Spot target = graph.getEdgeTarget(e);
                if (target==source) target = graph.getEdgeSource(e);
                int dt = ((SpotWithinCompartment)target).timePoint - ((SpotWithinCompartment)source).timePoint;
                if (dt>0) return target;
            }
            return null;
        }
        private Spot getPrev(Spot source) { // based on the assumption: one spot is linked to maximum one other spot
            for (DefaultWeightedEdge e : graph.edgesOf(source)) {
                Spot target = graph.getEdgeTarget(e);
                if (target==source) target = graph.getEdgeSource(e);
                int dt = ((SpotWithinCompartment)target).timePoint - ((SpotWithinCompartment)source).timePoint;
                if (dt<0) return target;
            }
            return null;
        }

	private static final boolean checkSettingsValidity( final Map< String, Object > settings, final StringBuilder str )
	{
		if ( null == settings )
		{
			str.append( "Settings map is null.\n" );
			return false;
		}

		boolean ok = true;
		// Linking
		ok = ok & checkParameter( settings, KEY_LINKING_MAX_DISTANCE, Double.class, str );
		ok = ok & checkFeatureMap( settings, KEY_LINKING_FEATURE_PENALTIES, str );
		// Others
		ok = ok & checkParameter( settings, KEY_ALTERNATIVE_LINKING_COST_FACTOR, Double.class, str );

		// Check keys
		final List< String > mandatoryKeys = new ArrayList< String >();
		mandatoryKeys.add( KEY_LINKING_MAX_DISTANCE );
		mandatoryKeys.add( KEY_ALTERNATIVE_LINKING_COST_FACTOR );
		final List< String > optionalKeys = new ArrayList< String >();
		optionalKeys.add( KEY_LINKING_FEATURE_PENALTIES );
                optionalKeys.add( KEY_MAX_FRAME_GAP_LQ );
		ok = ok & checkMapKeys( settings, mandatoryKeys, optionalKeys, str );

		return ok;
	}
        
        private class Gap implements Comparable<Gap> {
            SpotWithinCompartment s1, s2;
            DefaultWeightedEdge e;
            int dt;
            
            public Gap(SpotWithinCompartment s1, SpotWithinCompartment s2, DefaultWeightedEdge e) {
                if (s2.timePoint<s1.timePoint) init(s2, s1, e);
                else init(s1, s2, e);
                
            }
            private void init(SpotWithinCompartment s1, SpotWithinCompartment s2, DefaultWeightedEdge e) {
                this.s1 = s1;
                this.s2 = s2;
                this.e = e;
                this.dt= s2.timePoint - s1.timePoint;
            }
            public boolean fillGap(List<SpotWithinCompartment> linkers) {
                linkers.clear();
                SpotWithinCompartment sL=s1, sR=s2;
                int countLeft = 0;
                int countRight = 0;
                double lim = maxDist * maxDist;
                for (int delta = 1; delta<=(dt/2+1); ++delta) {
                    int tpLeft = s1.timePoint+delta;
                    int tpRight = s2.timePoint-delta;
                    Spot sLT = spots.getClosestSpot(sL, tpLeft, false);
                    Spot sRT = spots.getClosestSpot(sR, tpRight, false);
                    if (tpLeft<tpRight) {    
                        if (sLT!=null && sL.squareDistanceTo(sLT)<lim) {
                            sL = (SpotWithinCompartment)sLT;
                            linkers.add(countLeft++, sL);
                        }
                        if (sRT!=null && sR.squareDistanceTo(sRT)<lim) {
                            sR = (SpotWithinCompartment)sRT;
                            linkers.add(linkers.size() - ++countRight, sR);
                        }
                    } else if (sLT!=null && sLT.equals(sRT)) { // same spot
                        if (sL.squareDistanceTo(sLT)<lim && sR.squareDistanceTo(sLT)<lim) linkers.add(countLeft++, (SpotWithinCompartment)sLT);
                    } else { // same tp, different spots
                        double dL = sLT!=null ? sL.squareDistanceTo(sLT) : Double.POSITIVE_INFINITY;
                        double dR = sRT!=null ? sR.squareDistanceTo(sRT) : Double.POSITIVE_INFINITY;
                        if (dL<lim && dR<lim) { // minimize sum
                            double sumL = dL + sLT.squareDistanceTo(sR);
                            double sumR = dR + sRT.squareDistanceTo(sL);
                            if (sumL<sumR) linkers.add(countLeft++, (SpotWithinCompartment)sLT);
                            else linkers.add(linkers.size() - ++countRight, (SpotWithinCompartment)sRT);
                        } else if (dL<lim) linkers.add(countLeft++, (SpotWithinCompartment)sLT);
                        else if (dR<lim) linkers.add(linkers.size() - ++countRight, (SpotWithinCompartment)sRT);
                    }
                    
                }
                return !linkers.isEmpty();
            }

        public int compareTo(Gap t) {
            return Integer.compare(dt, t.dt);
        }
        }
}
