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
import fiji.plugin.trackmate.tracking.sparselap.SparseLAPFrameToFrameTracker;
import fiji.plugin.trackmate.tracking.sparselap.SparseLAPSegmentTracker;
import java.util.HashMap;
import java.util.Map;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jollion
 */
public class LAPTrackerCore {
    private SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph;
    public final static org.slf4j.Logger logger = LoggerFactory.getLogger(LAPTrackerCore.class);
    private Logger internalLogger = Logger.VOID_LOGGER;
    final private SpotCollection spots;
    int numThreads=1;
    String errorMessage;
    long processingTime;
    
    // FTF settings
    double linkingMaxDistance = 0.75;
    double alternativeLinkingCostFactor = 1.05;
    //double linkingFeaturesPenalities;
    
    // SparseLinker settings
    int gcMaxFrame = 3;
    double gapCLosingMaxDistance = 0.75;
    double gcAlternativeLinkingCostFactor = alternativeLinkingCostFactor;
    public LAPTrackerCore(SpotCollection spots) {
        this.spots=spots;
    }
    
    public LAPTrackerCore setLinkingMaxDistance(double maxDist) {
        linkingMaxDistance = maxDist;
        return this;
    }
    
    public LAPTrackerCore setAlternativeLinkingCostFactor(double cost) {
        alternativeLinkingCostFactor = cost;
        return this;
    }
    
    public SimpleWeightedGraph< Spot, DefaultWeightedEdge > getEdges() {
        return graph;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
    
    
    
    public boolean process() {
        
		final long start = System.currentTimeMillis();

		/*
		 * 1. Frame to frame linking.
		 */


		// Prepare settings object
		final Map< String, Object > ftfSettings = new HashMap< String, Object >();
		ftfSettings.put( KEY_LINKING_MAX_DISTANCE, linkingMaxDistance );
		ftfSettings.put( KEY_ALTERNATIVE_LINKING_COST_FACTOR, alternativeLinkingCostFactor );
		//ftfSettings.put( KEY_LINKING_FEATURE_PENALTIES, settings.get( KEY_LINKING_FEATURE_PENALTIES ) );

		final SparseLAPFrameToFrameTracker frameToFrameLinker = new SparseLAPFrameToFrameTracker( spots, ftfSettings );
		frameToFrameLinker.setNumThreads( numThreads );
		final Logger.SlaveLogger ftfLogger = new Logger.SlaveLogger( internalLogger, 0, 0.5 );
		frameToFrameLinker.setLogger( ftfLogger );

		if ( !frameToFrameLinker.checkInput() || !frameToFrameLinker.process() )
		{
			errorMessage = frameToFrameLinker.getErrorMessage();
			return false;
		}
                
		graph = frameToFrameLinker.getResult();

		/*
		 * 2. Gap-closing, merging and splitting.
		 */
                
		// Prepare settings object
		final Map< String, Object > slSettings = new HashMap< String, Object >();

		slSettings.put( KEY_ALLOW_GAP_CLOSING, gcMaxFrame>0 );
		//slSettings.put( KEY_GAP_CLOSING_FEATURE_PENALTIES, settings.get( KEY_GAP_CLOSING_FEATURE_PENALTIES ) );
		slSettings.put( KEY_GAP_CLOSING_MAX_DISTANCE, gapCLosingMaxDistance );
		slSettings.put( KEY_GAP_CLOSING_MAX_FRAME_GAP, gcMaxFrame );

		slSettings.put( KEY_ALLOW_TRACK_SPLITTING, false );
		//slSettings.put( KEY_SPLITTING_FEATURE_PENALTIES, settings.get( KEY_SPLITTING_FEATURE_PENALTIES ) );
		slSettings.put( KEY_SPLITTING_MAX_DISTANCE, gapCLosingMaxDistance );

		slSettings.put( KEY_ALLOW_TRACK_MERGING, false );
		//slSettings.put( KEY_MERGING_FEATURE_PENALTIES, settings.get( KEY_MERGING_FEATURE_PENALTIES ) );
		slSettings.put( KEY_MERGING_MAX_DISTANCE, gapCLosingMaxDistance );

		slSettings.put( KEY_ALTERNATIVE_LINKING_COST_FACTOR, gcAlternativeLinkingCostFactor );
		slSettings.put( KEY_CUTOFF_PERCENTILE, 1d );

		// Solve.
		final SparseLAPSegmentTracker segmentLinker = new SparseLAPSegmentTracker( graph, slSettings );
		final Logger.SlaveLogger slLogger = new Logger.SlaveLogger( internalLogger, 0.5, 0.5 );
		segmentLinker.setLogger( slLogger );
                segmentLinker.setNumThreads( numThreads );
		if ( !segmentLinker.checkInput() || !segmentLinker.process() )
		{
			errorMessage = segmentLinker.getErrorMessage();
			return false;
		}
                graph = segmentLinker.getResult();
		internalLogger.setStatus( "" );
		internalLogger.setProgress( 1d );
		final long end = System.currentTimeMillis();
		processingTime = end - start;
                return true;
    }
}
