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
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectTracker;
import dataStructure.objects.StructureObjectUtils;
import image.Image;
import image.ImageFloat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import plugins.Tracker;
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
public class LAPTracker implements Tracker {
    
    static int compartimentStructureIdx = 1;
    NumberParameter spotQualityThreshold = new NumberParameter("Spot Quality Threshold", 3, 3.5);
    NumberParameter maxGap = new BoundedNumberParameter("Maximum frame gap", 0, 2, 0, null);
    NumberParameter maxLinkingDistance = new BoundedNumberParameter("FTF Maximum Linking Distance (0=skip)", 2, 0.65, 0, null);
    NumberParameter maxLinkingDistanceGC = new BoundedNumberParameter("Gap-closing Maximum Linking Distance (0=skip)", 2, 0.65, 0, null);
    NumberParameter gapPenalty = new BoundedNumberParameter("Gap Distance Penalty", 2, 0.25, 0, null);
    NumberParameter alternativeDistance = new BoundedNumberParameter("Alternative Distance (>maxLinkinDistance)", 2, 0.7, 0, null);
    Parameter[] parameters = new Parameter[]{maxLinkingDistance, maxLinkingDistanceGC, maxGap, gapPenalty, alternativeDistance, spotQualityThreshold};
    
    public LAPTracker setLinkingMaxDistance(double maxDist, double maxDistGapClosing) {
        maxLinkingDistance.setValue(maxDist);
        maxLinkingDistanceGC.setValue(maxDistGapClosing);
        return this;
    }
    
    public void track(int structureIdx, List<StructureObject> parentTrack) {
        int maxGap = this.maxGap.getValue().intValue()+1; // parameter = count only the frames where the spot is invisible
        double spotQualityThreshold = this.spotQualityThreshold.getValue().doubleValue();
        double maxLinkingDistance = this.maxLinkingDistance.getValue().doubleValue();
        double maxLinkingDistanceGC = this.maxLinkingDistanceGC.getValue().doubleValue();
        double gapPenalty = Math.pow(this.gapPenalty.getValue().doubleValue(), 2);
        double alternativeDistance = this.alternativeDistance.getValue().doubleValue();
        DistanceComputationParameters distParams = new DistanceComputationParameters(alternativeDistance).setQualityThreshold(spotQualityThreshold).setGapSquareDistancePenalty(gapPenalty);
        SpotPopulation spotCollection = new SpotPopulation(distParams);
        long t0 = System.currentTimeMillis();
        for (StructureObject p : parentTrack) spotCollection.addSpots(p, structureIdx, p.getObjectPopulation(structureIdx).getObjects(), compartimentStructureIdx);
        long t1 = System.currentTimeMillis();
        logger.debug("LAP Tracker: {}, spot HQ: {}, #spots LQ: {}, time: {}", parentTrack.get(0), spotCollection.getSpotSet(true, false).size(), spotCollection.getSpotSet(false, true).size(), t1-t0);
        LAPTrackerCore core = new LAPTrackerCore(spotCollection);
        
        // first run to select  LQ spots linked to HQ spots
        double maxD = Math.max(maxLinkingDistanceGC, maxLinkingDistance);
        boolean processOk = true;
        processOk = processOk && core.processFTF(maxD, true, false); //FTF only with HQ
        processOk = processOk && core.processFTF(maxD, true, true); // FTF HQ+LQ
        processOk = processOk && core.processGC(maxD, maxGap, true, false); // GC HQ
        processOk = processOk && core.processGC(maxD, maxGap, true, true); // GC HQ + LQ
        if (!processOk) logger.error("LAPTracker error : {}", core.getErrorMessage());
        else {
            spotCollection.setTrackLinks(parentTrack, structureIdx, core.getEdges());
            spotCollection.removeLQSpotsUnlinkedToHQSpots(parentTrack, structureIdx, true);
        }
        
        // second run with all spots at the same time
        core.resetEdges();
        SpotWithinCompartment.displayPoles=true;
        processOk = core.processGC(maxLinkingDistanceGC, maxGap, true, true);
        if (!processOk) logger.error("LAPTracker error : {}", core.getErrorMessage());
        else spotCollection.setTrackLinks(parentTrack, structureIdx, core.getEdges());
        
        // post-processing
        MutationTrackPostProcessing postProcessor = new MutationTrackPostProcessing(structureIdx, parentTrack, spotCollection);
        postProcessor.connectShortTracksByDeletingLQSpot(maxLinkingDistanceGC);
        postProcessor.splitLongTracks();
        
        // ETUDIER LES DEPLACEMENTS EN Y
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
