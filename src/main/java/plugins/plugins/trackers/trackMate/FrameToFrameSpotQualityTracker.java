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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.imglib2.algorithm.MultiThreadedBenchmarkAlgorithm;
import net.imglib2.multithreading.SimpleMultiThreading;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

/**
 * Adapted from fiji.plugin.trackmate.tracking.sparselap.SparseLAPFrameToFrameTracker: link spots of low quality to spots of high quality or spots of low quality to other spots of low quality that have already been linked to spots of high quality

 * @author jollion
 */
public class FrameToFrameSpotQualityTracker  extends MultiThreadedBenchmarkAlgorithm {

	private final static String BASE_ERROR_MESSAGE = "[SparseLAPFrameToFrameTracker] ";

	private final SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph;

	private Logger logger = Logger.VOID_LOGGER;

	private final SpotCollection spots;

	private final Map< String, Object > settings;

	/*
	 * CONSTRUCTOR
	 */

	public FrameToFrameSpotQualityTracker( final SpotCollection spots, SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph, final Map< String, Object > settings )
	{
		this.spots = spots;
		this.settings = settings;
                this.graph=graph;
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

	public boolean process()
	{
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

		/*
		 * Process.
		 */

		final long start = System.currentTimeMillis();

		// Prepare frame pairs in order, not necessarily separated by 1.
		final ArrayList< int[] > framePairs = new ArrayList< int[] >( (spots.keySet().size() - 1)*2 );
		Iterator< Integer > frameIterator = spots.keySet().iterator();
		int frame0 = frameIterator.next();
		int frame1;
		while ( frameIterator.hasNext() ) { // ascending order
			frame1 = frameIterator.next();
			framePairs.add( new int[] { frame0, frame1 } );
			frame0 = frame1;
		}
                frameIterator = spots.keySet().descendingIterator();
                frame0 = frameIterator.next();
		while ( frameIterator.hasNext() ) { // descending order
			frame1 = frameIterator.next();
			framePairs.add( new int[] { frame0, frame1 } );
			frame0 = frame1;
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
		final Double maxDist = ( Double ) settings.get( KEY_LINKING_MAX_DISTANCE );
		final double costThreshold = maxDist * maxDist;
		final double alternativeCostFactor = ( Double ) settings.get( KEY_ALTERNATIVE_LINKING_COST_FACTOR );

		// Prepare threads
		final Thread[] threads = SimpleMultiThreading.newThreads( numThreads );

		// Prepare the thread array
		final AtomicInteger ai = new AtomicInteger( 0 );
		final AtomicInteger progress = new AtomicInteger( 0 );
		final AtomicBoolean ok = new AtomicBoolean( true );
		for ( int ithread = 0; ithread < threads.length; ithread++ )
		{
			threads[ ithread ] = new Thread( BASE_ERROR_MESSAGE + " thread " + ( 1 + ithread ) + "/" + threads.length )
			{
				@Override
				public void run()
				{
					for ( int i = ai.getAndIncrement(); i < framePairs.size(); i = ai.getAndIncrement() )
					{
						if ( !ok.get() )
						{
							break;
						}

						// Get frame pairs
						final int frame0 = framePairs.get( i )[ 0 ];
						final int frame1 = framePairs.get( i )[ 1 ];

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

                                                // remove spots that have already been linked between the two time points
                                                for (Spot source : sources) {
                                                    for (DefaultWeightedEdge e : graph.edgesOf(source)) {
                                                        Spot target = graph.getEdgeTarget(e);
                                                        if (target==source) target = graph.getEdgeSource(e);
                                                        if (targets.remove(target)) sources.remove(source);
                                                    }
                                                }
                                                
						if ( sources.isEmpty() || targets.isEmpty() ) continue;
						
                                                
                                                
						/*
						 * Run the linker.
						 */

						final JaqamanLinkingCostMatrixCreator< Spot, Spot > creator = new JaqamanLinkingCostMatrixCreator< Spot, Spot >( sources, targets, costFunction, costThreshold, alternativeCostFactor, 1d );
						final JaqamanLinker< Spot, Spot > linker = new JaqamanLinker< Spot, Spot >( creator );
						if ( !linker.checkInput() || !linker.process() )
						{
							errorMessage = "At frame " + frame0 + " to " + frame1 + ": " + linker.getErrorMessage();
							ok.set( false );
							return;
						}

						/*
						 * Update graph.
						 */

						synchronized ( graph )
						{
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
						}

						logger.setProgress( progress.incrementAndGet() / framePairs.size() );

					}
				}
			};
		}

		logger.setStatus( "Frame to frame linking..." );
		SimpleMultiThreading.startAndJoin( threads );
		logger.setProgress( 1d );
		logger.setStatus( "" );

		final long end = System.currentTimeMillis();
		processingTime = end - start;

		return ok.get();
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
		ok = ok & checkMapKeys( settings, mandatoryKeys, optionalKeys, str );

		return ok;
	}
}
