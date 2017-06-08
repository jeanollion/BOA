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

import boa.gui.imageInteraction.ImageObjectInterface;
import com.google.common.collect.Sets;
import dataStructure.objects.Object3D;
import dataStructure.objects.StructureObject;
import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_ALLOW_GAP_CLOSING;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_ALLOW_TRACK_MERGING;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_ALLOW_TRACK_SPLITTING;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_ALTERNATIVE_LINKING_COST_FACTOR;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_CUTOFF_PERCENTILE;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_GAP_CLOSING_FEATURE_PENALTIES;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_GAP_CLOSING_MAX_DISTANCE;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_GAP_CLOSING_MAX_FRAME_GAP;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_LINKING_FEATURE_PENALTIES;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_LINKING_MAX_DISTANCE;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_MERGING_FEATURE_PENALTIES;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_MERGING_MAX_DISTANCE;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_SPLITTING_FEATURE_PENALTIES;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_SPLITTING_MAX_DISTANCE;
//import fiji.plugin.trackmate.tracking.sparselap.SparseLAPFrameToFrameTracker;
//import fiji.plugin.trackmate.tracking.sparselap.SparseLAPSegmentTracker;
import ij.gui.Overlay;
import image.BoundingBox;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.slf4j.LoggerFactory;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class LAPTrackerCore {
    private SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph;
    SpotPopulation spotPopulation;
    public final static org.slf4j.Logger logger = LoggerFactory.getLogger(LAPTrackerCore.class);
    private Logger internalLogger = Logger.VOID_LOGGER;
    int numThreads=1;
    String errorMessage;
    
    
    public LAPTrackerCore(SpotPopulation spotPopulation) {
        this.spotPopulation=spotPopulation;
    }
    
    public SimpleWeightedGraph< Spot, DefaultWeightedEdge > getEdges() {
        return graph;
    }
    
    public void resetEdges() {
        graph = null;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
    /**
     * Do take into accound previously created graph
     * @param distanceThreshold
     * @param includeLQ
     * @param includeHQ
     * @return 
     */
    public boolean processFTF(double distanceThreshold, boolean includeHQ, boolean includeLQ) {
        long t0 = System.currentTimeMillis();
        // Prepare settings object
        final Map< String, Object > ftfSettings = new HashMap< String, Object >();
        ftfSettings.put( KEY_LINKING_MAX_DISTANCE, distanceThreshold );
        ftfSettings.put( KEY_ALTERNATIVE_LINKING_COST_FACTOR, 1.05 );
        //ftfSettings.put( KEY_LINKING_FEATURE_PENALTIES, settings.get( KEY_LINKING_FEATURE_PENALTIES ) );

        final SparseLAPFrameToFrameTrackerFromExistingGraph frameToFrameLinker = new SparseLAPFrameToFrameTrackerFromExistingGraph( this.spotPopulation.getSpotCollection(includeHQ, includeLQ), ftfSettings, graph );
        frameToFrameLinker.setNumThreads( numThreads );
        final Logger.SlaveLogger ftfLogger = new Logger.SlaveLogger( internalLogger, 0, 0.5 );
        frameToFrameLinker.setLogger( ftfLogger );

        if ( !frameToFrameLinker.checkInput() || !frameToFrameLinker.process()) {
                errorMessage = frameToFrameLinker.getErrorMessage();
                return false;
        }
        graph = frameToFrameLinker.getResult();
        long t1 = System.currentTimeMillis();
        core.Processor.logger.debug("number of edges after FTF step: {}, nb of vertices: {}, processing time: {}", graph.edgeSet().size(), graph.vertexSet().size(), t1-t0);
        return true;
    }
    public void removeGaps() {
        if (graph==null) throw new Error("Graph not initialized");
        List<DefaultWeightedEdge> edgeList = new ArrayList<DefaultWeightedEdge>(graph.edgeSet());
        for (DefaultWeightedEdge e : edgeList) {
            SpotWithinCompartment target = (SpotWithinCompartment)graph.getEdgeTarget(e);
            SpotWithinCompartment source = (SpotWithinCompartment)graph.getEdgeSource(e);
            if (Math.abs(target.frame - source.frame)>1) {
                graph.removeEdge(e);
            }
            // if vertices become unlinked, remove them from graph
            if (graph.edgesOf(target).isEmpty()) graph.removeVertex(target);
            if (graph.edgesOf(source).isEmpty()) graph.removeVertex(source);
        }
    }
    public boolean processGC(double distanceThreshold, int maxFrameGap, boolean includeHQ, boolean includeLQ) {
        long t0 = System.currentTimeMillis();
        Set<Spot> unlinkedSpots;
        if (graph == null) {
            graph = new SimpleWeightedGraph< Spot, DefaultWeightedEdge >( DefaultWeightedEdge.class );
            unlinkedSpots = new HashSet<Spot>(this.spotPopulation.getSpotSet(includeHQ, includeLQ)); 
        } else {
            Set<Spot> linkedSpots = graph.vertexSet();
            unlinkedSpots = new HashSet<Spot>(Sets.difference(spotPopulation.getSpotSet(includeHQ, includeLQ), linkedSpots));
        }
        Map<Spot, Spot> clonedSpots = new HashMap<Spot, Spot>();
        // duplicate unlinked spots to include them in the gap-closing part
        for (Spot s : unlinkedSpots) {
            SpotWithinCompartment clone = ((SpotWithinCompartment)s).duplicate();
            graph.addVertex(s);
            graph.addVertex(clone);
            clonedSpots.put(clone, s);
            graph.addEdge(s,clone);
        }
        //SpotWithinCompartment.displayPoles=true;
        
        // Prepare settings object
        final Map< String, Object > slSettings = new HashMap< String, Object >();

        slSettings.put( KEY_ALLOW_GAP_CLOSING, maxFrameGap>1 );
        //slSettings.put( KEY_GAP_CLOSING_FEATURE_PENALTIES, settings.get( KEY_GAP_CLOSING_FEATURE_PENALTIES ) );
        slSettings.put( KEY_GAP_CLOSING_MAX_DISTANCE, distanceThreshold );
        slSettings.put( KEY_GAP_CLOSING_MAX_FRAME_GAP, maxFrameGap );

        slSettings.put( KEY_ALLOW_TRACK_SPLITTING, false );
        //slSettings.put( KEY_SPLITTING_FEATURE_PENALTIES, settings.get( KEY_SPLITTING_FEATURE_PENALTIES ) );
        slSettings.put( KEY_SPLITTING_MAX_DISTANCE, distanceThreshold );

        slSettings.put( KEY_ALLOW_TRACK_MERGING, false );
        //slSettings.put( KEY_MERGING_FEATURE_PENALTIES, settings.get( KEY_MERGING_FEATURE_PENALTIES ) );
        slSettings.put( KEY_MERGING_MAX_DISTANCE, distanceThreshold );

        slSettings.put( KEY_ALTERNATIVE_LINKING_COST_FACTOR, 1.05 );
        slSettings.put( KEY_CUTOFF_PERCENTILE, 1d );

        
        
        // Solve.
        final SparseLAPSegmentTracker segmentLinker = new SparseLAPSegmentTracker( graph, slSettings, spotPopulation.distanceParameters);
        //final fiji.plugin.trackmate.tracking.sparselap.SparseLAPSegmentTracker segmentLinker = new fiji.plugin.trackmate.tracking.sparselap.SparseLAPSegmentTracker( graph, slSettings);
        segmentLinker.setNumThreads(numThreads);
        final Logger.SlaveLogger slLogger = new Logger.SlaveLogger( internalLogger, 0.5, 0.5 );
        segmentLinker.setLogger( slLogger );
        if ( !segmentLinker.checkInput() || !segmentLinker.process() ) {
            errorMessage = segmentLinker.getErrorMessage();
            return false;
        }
        for (Entry<Spot, Spot> e : clonedSpots.entrySet()) transferLinks(e.getKey(), e.getValue());
        long t1 = System.currentTimeMillis();
        core.Processor.logger.debug("number of edges after GC step: {}, nb of vertices: {}, processing time: {}", graph.edgeSet().size(), graph.vertexSet().size(), t1-t0);
        return true;
    }
    
    private void transferLinks(Spot from, Spot to) {
        List<DefaultWeightedEdge> edgeList = new ArrayList<DefaultWeightedEdge>(graph.edgesOf(from));
        for (DefaultWeightedEdge e : edgeList) {
            Spot target = graph.getEdgeTarget(e);
            boolean isSource = true;
            if (target==from) {
                target = graph.getEdgeSource(e);
                isSource=false;
            }
            graph.removeEdge(e);
            if (target!=to) graph.addEdge(isSource?to : target, isSource ? target : to, e);          
        }
        graph.removeVertex(from);
    }
    
    public float[] extractDistanceDistribution(boolean onlyHQHQ) {
        if (graph==null) throw new IllegalArgumentException("Graph not initialized");
        List<Double> distances = new ArrayList<Double>();
        for (DefaultWeightedEdge e : graph.edgeSet()) {
            SpotWithinCompartment s1 = (SpotWithinCompartment)graph.getEdgeSource(e);
            SpotWithinCompartment s2 = (SpotWithinCompartment)graph.getEdgeTarget(e);
            if ( (Math.abs(s1.frame-s2.frame)==1) && (!onlyHQHQ || (!s1.lowQuality && !s2.lowQuality)) ) distances.add(s1.squareDistanceTo(s2) );
        }
        return Utils.toFloatArray(distances, false);
    }
    /*public float[] extractDeltaYDistribution(boolean onlyHQHQ) {
        if (graph==null) throw new IllegalArgumentException("Graph not initialized");
        List<Double> distances = new ArrayList<Double>();
        for (DefaultWeightedEdge e : graph.edgeSet()) {
            SpotWithinCompartment s1 = (SpotWithinCompartment)graph.getEdgeSource(e);
            SpotWithinCompartment s2 = (SpotWithinCompartment)graph.getEdgeTarget(e);
            if ( (Math.abs(s1.timePoint-s2.timePoint)==1) && (!onlyHQHQ || (!s1.lowQuality && !s2.lowQuality)) ) distances.add(s1.squareDistanceTo(s2) );
        }
        return Utils.toFloatArray(distances, false);
    }*/
}
