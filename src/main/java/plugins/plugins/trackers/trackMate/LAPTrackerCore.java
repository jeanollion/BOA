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
import fiji.plugin.trackmate.tracking.sparselap.SparseLAPFrameToFrameTracker;
import fiji.plugin.trackmate.tracking.sparselap.SparseLAPSegmentTracker;
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
    long processingTime;
    
    // FTF settings
    double linkingMaxDistance = 0;
    double alternativeLinkingCostFactor = 1.05;
    //double linkingFeaturesPenalities;
    
    // SparseLinker settings
    int gcMaxFrame = 3;
    double gapCLosingMaxDistance = 0.75;
    double gcAlternativeLinkingCostFactor = alternativeLinkingCostFactor;
    
    // FTF low quality settings
    double linkingMaxDistanceLQ = 0.75;
    double alternativeLinkingCostFactorLQ = 1.05;
    
    public LAPTrackerCore(SpotPopulation spotPopulation) {
        this.spotPopulation=spotPopulation;
    }
    
    public LAPTrackerCore setLinkingMaxDistance(double maxDist, double maxDistGapClosing, double maxDistLowQuality) {
        linkingMaxDistance = maxDist;
        gapCLosingMaxDistance = maxDistGapClosing;
        linkingMaxDistanceLQ = maxDistLowQuality;
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
        double linkingMaxDistance = this.linkingMaxDistance;
        final long start = System.currentTimeMillis();
        boolean doFTF = linkingMaxDistance>0;
        boolean doGC = gapCLosingMaxDistance>0 && gcMaxFrame>0;
        boolean doLQ = linkingMaxDistanceLQ>0;
        if (gcMaxFrame==0 && gapCLosingMaxDistance>0) { // do not perform GC but FTF instead, with the maximum linkingDistance
            linkingMaxDistance = Math.max(linkingMaxDistance, gapCLosingMaxDistance);
            doFTF = true;
        }
        
        Set<Spot> unlinkedSpots;
        List<DefaultWeightedEdge> edgesToRemove = new ArrayList<DefaultWeightedEdge>();
        /*
        * 1. Frame to frame linking. / excluding spots of lowQuality
        */
        if (doFTF) {    
            // Prepare settings object
            final Map< String, Object > ftfSettings = new HashMap< String, Object >();
            ftfSettings.put( KEY_LINKING_MAX_DISTANCE, linkingMaxDistance );
            ftfSettings.put( KEY_ALTERNATIVE_LINKING_COST_FACTOR, alternativeLinkingCostFactor );
            //ftfSettings.put( KEY_LINKING_FEATURE_PENALTIES, settings.get( KEY_LINKING_FEATURE_PENALTIES ) );

            final SparseLAPFrameToFrameTracker frameToFrameLinker = new SparseLAPFrameToFrameTracker( this.spotPopulation.getSpotCollection(true), ftfSettings );
            frameToFrameLinker.setNumThreads( numThreads );
            final Logger.SlaveLogger ftfLogger = new Logger.SlaveLogger( internalLogger, 0, 0.5 );
            frameToFrameLinker.setLogger( ftfLogger );

            if ( !frameToFrameLinker.checkInput() || !frameToFrameLinker.process()) {
                    errorMessage = frameToFrameLinker.getErrorMessage();
                    return false;
            }
            graph = frameToFrameLinker.getResult();
            
            Set<Spot> linkedSpots = graph.vertexSet();
            unlinkedSpots = new HashSet<Spot>(Sets.difference(spotPopulation.getSpotSet(true, false), linkedSpots));
            logger.debug("unlinked spots after FTF step: {}", unlinkedSpots.size());
        } else {
            graph = new SimpleWeightedGraph< Spot, DefaultWeightedEdge >( DefaultWeightedEdge.class );
            unlinkedSpots = new HashSet<Spot>(this.spotPopulation.getSpotSet(true, false));
        }

        /*
         * 2. Gap-closing, merging and splitting / excluding spots of lowQuality
         * IDéE: ajouter une pénalité sur les spots de lowQuality et les inclure dans la procédure globale....
         */
        if (doGC) {
            // duplicate unlinked spots to include them in the gap-closing part
            for (Spot s : unlinkedSpots) {
                SpotWithinCompartment clone = spotPopulation.duplicateSpot((SpotWithinCompartment)s);
                graph.addVertex(s);
                graph.addVertex(clone);
                edgesToRemove.add(graph.addEdge(s,clone));
            }
            //SpotWithinCompartment.displayPoles=false;


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
            //final SparseLAPSegmentTrackerIncludingUnlinkedSpots segmentLinker = new SparseLAPSegmentTrackerIncludingUnlinkedSpots( graph, slSettings, unlinkedSpots, unlinkedSpots2 );
            final SparseLAPSegmentTracker segmentLinker = new SparseLAPSegmentTracker( graph, slSettings);
            final Logger.SlaveLogger slLogger = new Logger.SlaveLogger( internalLogger, 0.5, 0.5 );
            segmentLinker.setLogger( slLogger );
            segmentLinker.setNumThreads( numThreads );
            if ( !segmentLinker.checkInput() || !segmentLinker.process() )
            {
                    errorMessage = segmentLinker.getErrorMessage();
                    return false;
            }
            for (DefaultWeightedEdge e : edgesToRemove) graph.removeEdge(e);
        }
        /*
         * 3. Forward & reverse frame-to-frame linking including spots of lowQuality
        */
        if (doLQ) {
        // Prepare settings object
            final Map< String, Object > ftfSettings = new HashMap< String, Object >();
            ftfSettings.put( KEY_LINKING_MAX_DISTANCE, linkingMaxDistanceLQ );
            ftfSettings.put( KEY_ALTERNATIVE_LINKING_COST_FACTOR, alternativeLinkingCostFactorLQ );
            //ftfSettings.put( KEY_LINKING_FEATURE_PENALTIES, settings.get( KEY_LINKING_FEATURE_PENALTIES ) );

            final FrameToFrameSpotQualityTracker frameToFrameLinker = new FrameToFrameSpotQualityTracker( this.spotPopulation.getSpotCollection(false), graph, ftfSettings );
            frameToFrameLinker.setNumThreads( 1 );
            
            if ( !frameToFrameLinker.checkInput() || !frameToFrameLinker.process()) {
                    errorMessage = frameToFrameLinker.getErrorMessage();
                    return false;
            }
        }
        internalLogger.setStatus( "" );
        internalLogger.setProgress( 1d );
        final long end = System.currentTimeMillis();
        processingTime = end - start;
        return true;
    }
}
