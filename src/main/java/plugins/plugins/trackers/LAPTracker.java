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
package plugins.plugins.trackers;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageObjectInterface;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.GroupParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import configuration.parameters.PostFilterSequence;
import configuration.parameters.PreFilterSequence;
import configuration.parameters.StructureParameter;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectTracker;
import dataStructure.objects.StructureObjectUtils;
import fiji.plugin.trackmate.Spot;
import image.Image;
import image.ImageFloat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import plugins.Segmenter;
import plugins.SegmenterSplitAndMerge;
import plugins.Tracker;
import plugins.TrackerSegmenter;
import plugins.plugins.processingScheme.SegmentThenTrack;
import plugins.plugins.segmenters.BacteriaFluo;
import plugins.plugins.segmenters.MutationSegmenterScaleSpace;
import plugins.plugins.trackers.trackMate.LAPTrackerCore;
import plugins.plugins.trackers.trackMate.postProcessing.MutationTrackPostProcessing;
import plugins.plugins.trackers.trackMate.SpotPopulation;
import plugins.plugins.trackers.trackMate.SpotWithinCompartment;
import plugins.plugins.trackers.trackMate.SpotWithinCompartment.DistanceComputationParameters;
import utils.ArrayFileWriter;

/**
 *
 * @author jollion
 */
public class LAPTracker implements TrackerSegmenter {
    protected PluginParameter<Segmenter> segmenter = new PluginParameter<Segmenter>("Segmentation algorithm", Segmenter.class, new MutationSegmenterScaleSpace(), false);
    StructureParameter compartirmentStructure = new StructureParameter("Compartiment Structure");
    NumberParameter spotQualityThreshold = new NumberParameter("Spot Quality Threshold", 3, 3.5);
    NumberParameter maxGap = new BoundedNumberParameter("Maximum frame gap", 0, 2, 0, null);
    NumberParameter maxLinkingDistance = new BoundedNumberParameter("FTF Maximum Linking Distance (0=skip)", 2, 0.75, 0, null);
    NumberParameter maxLinkingDistanceGC = new BoundedNumberParameter("Gap-closing Maximum Linking Distance (0=skip)", 2, 0.75, 0, null);
    NumberParameter gapPenalty = new BoundedNumberParameter("Gap Distance Penalty", 2, 0.25, 0, null);
    NumberParameter alternativeDistance = new BoundedNumberParameter("Alternative Distance (>maxLinkinDistance)", 2, 0.8, 0, null);
    NumberParameter minimalDistanceForTrackSplittingPenalty = new BoundedNumberParameter("Minimal Distance For Track Splitting Penalty", 2, 0.2, 0, null);
    NumberParameter maximalTrackSplittingPenalty = new BoundedNumberParameter("Maximal Track Splitting Penalty", 5, 0.00001, 0.01, 1);
    NumberParameter minimalTrackFrameNumber = new BoundedNumberParameter("Minimal Track Frame Number", 0, 6, 1, null);
    NumberParameter maximalTrackFrameNumber = new BoundedNumberParameter("Maximal Track Frame Number", 0, 15, 1, null);
    GroupParameter trackSplittingParameters = new GroupParameter("Track Post-Processing", minimalTrackFrameNumber, maximalTrackFrameNumber, maximalTrackSplittingPenalty, minimalDistanceForTrackSplittingPenalty);
    Parameter[] parameters = new Parameter[]{segmenter, compartirmentStructure, maxLinkingDistance, maxLinkingDistanceGC, maxGap, gapPenalty, alternativeDistance, spotQualityThreshold, trackSplittingParameters};
    
    public LAPTracker setCompartimentStructure(int compartimentStructureIdx) {
        this.compartirmentStructure.setSelectedStructureIdx(compartimentStructureIdx);
        return this;
    }
    public LAPTracker setLinkingMaxDistance(double maxDist, double maxDistGapClosing) {
        maxLinkingDistance.setValue(maxDist);
        maxLinkingDistanceGC.setValue(maxDistGapClosing);
        return this;
    }
    
    @Override public void segmentAndTrack(int structureIdx, List<StructureObject> parentTrack, PreFilterSequence preFilters, PostFilterSequence postFilters) {
        SegmentThenTrack stt = new SegmentThenTrack(segmenter.instanciatePlugin(), this).addPostFilters(postFilters.get()).addPreFilters(preFilters.get());
        stt.segmentOnly(structureIdx, parentTrack);
        track(structureIdx, parentTrack, true);
    }

    @Override public Segmenter getSegmenter() {
        return segmenter.instanciatePlugin();
    }
    @Override public void track(int structureIdx, List<StructureObject> parentTrack) {
        track(structureIdx, parentTrack, false);
    }
    
    public void track(int structureIdx, List<StructureObject> parentTrack, boolean LQSpots) {
        int compartirmentStructure=this.compartirmentStructure.getSelectedIndex();
        int maxGap = this.maxGap.getValue().intValue()+1; // parameter = count only the frames where the spot is missing
        double spotQualityThreshold = LQSpots ? this.spotQualityThreshold.getValue().doubleValue() : Double.NEGATIVE_INFINITY;
        double maxLinkingDistance = this.maxLinkingDistance.getValue().doubleValue();
        double maxLinkingDistanceGC = this.maxLinkingDistanceGC.getValue().doubleValue();
        double gapPenalty = Math.pow(this.gapPenalty.getValue().doubleValue(), 2);
        double alternativeDistance = this.alternativeDistance.getValue().doubleValue();
        DistanceComputationParameters distParams = new DistanceComputationParameters(alternativeDistance).setQualityThreshold(spotQualityThreshold).setGapSquareDistancePenalty(gapPenalty);
        SpotPopulation spotCollection = new SpotPopulation(distParams);
        long t0 = System.currentTimeMillis();
        for (StructureObject p : parentTrack) spotCollection.addSpots(p, structureIdx, p.getObjectPopulation(structureIdx).getObjects(), compartirmentStructure);
        //SpotWithinCompartment s1 = (SpotWithinCompartment)spotCollection.getSpotCollection(true, false).iterator(1, false).next();
        //SpotWithinCompartment s2 = (SpotWithinCompartment)spotCollection.getSpotCollection(true, false).iterator(2, false).next();
        //if (s1!=null &&  s2!=null) logger.debug("distance 1-2: {}, scale 1: {}, 1 isAbsolute: {}, cent1: {}, cent2: {}", s1.squareDistanceTo(s2), s1.getObject().getScaleXY(), s1.getObject().isAbsoluteLandMark(), s1.getObject().getCenter(true), s2.getObject().getCenter(true));
        
        long t1 = System.currentTimeMillis();
        logger.debug("LAP Tracker: {}, spot HQ: {}, #spots LQ: {}, time: {}", parentTrack.get(0), spotCollection.getSpotSet(true, false).size(), spotCollection.getSpotSet(false, true).size(), t1-t0);
        LAPTrackerCore core = new LAPTrackerCore(spotCollection);
        boolean processOk = true;
        if (LQSpots) {
            // first run to select  LQ spots linked to HQ spots
            double maxD = Math.max(maxLinkingDistanceGC, maxLinkingDistance);
            processOk = processOk && core.processFTF(maxD, true, false); //FTF only with HQ
            processOk = processOk && core.processFTF(maxD, true, true); // FTF HQ+LQ
            processOk = processOk && core.processGC(maxD, maxGap, true, false); // GC HQ
            processOk = processOk && core.processGC(maxD, 2, true, true); // GC HQ + LQ // only one gap for LQ spots
            if (!processOk) logger.error("LAPTracker error : {}", core.getErrorMessage());
            else {
                spotCollection.setTrackLinks(parentTrack, structureIdx, core.getEdges());
                spotCollection.removeLQSpotsUnlinkedToHQSpots(parentTrack, structureIdx, true);
            }
            core.resetEdges();
        }
        // second run with all spots at the same time
        //SpotWithinCompartment.displayPoles=true;
        processOk = core.processGC(maxLinkingDistanceGC, maxGap, true, true);
        if (!processOk) logger.error("LAPTracker error : {}", core.getErrorMessage());
        else spotCollection.setTrackLinks(parentTrack, structureIdx, core.getEdges());
        
        // post-processing
        MutationTrackPostProcessing postProcessor = new MutationTrackPostProcessing(structureIdx, parentTrack, spotCollection);
        postProcessor.connectShortTracksByDeletingLQSpot(maxLinkingDistanceGC);
        distParams.setGapSquareDistancePenalty(gapPenalty*4); // double penalty (square distance=>*4) 
        //postProcessor.printDistancesOnOverlay();
        postProcessor.splitLongTracks(minimalTrackFrameNumber.getValue().intValue()-1, minimalDistanceForTrackSplittingPenalty.getValue().doubleValue(), maxLinkingDistanceGC, maximalTrackSplittingPenalty.getValue().doubleValue());
        postProcessor.flagShortAndLongTracks(minimalTrackFrameNumber.getValue().intValue(), maximalTrackFrameNumber.getValue().intValue());
        
        // ETUDE DES DEPLACEMENTS EN Y
        /*
        // get distance distribution
        float[] dHQ= core.extractDistanceDistribution(true);
        float[] dHQLQ = core.extractDistanceDistribution(false);
        new ArrayFileWriter().addArray("HQ", dHQ).addArray("HQLQ", dHQLQ).writeToFile("/home/jollion/Documents/LJP/Analyse/SpotDistanceDistribution/SpotDistanceDistribution.csv");
        */
        long t2 = System.currentTimeMillis();
        logger.debug("LAP Tracker: {}, total processing time: {}", parentTrack.get(0), t2-t1);
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    
    
}
