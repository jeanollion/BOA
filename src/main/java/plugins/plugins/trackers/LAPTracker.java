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

import boa.gui.imageInteraction.ImageObjectInterface;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectTracker;
import java.util.ArrayList;
import java.util.List;
import plugins.Tracker;
import plugins.plugins.trackers.trackMate.LAPTrackerCore;
import plugins.plugins.trackers.trackMate.SpotPopulation;

/**
 *
 * @author jollion
 */
public class LAPTracker implements Tracker {
    
    static int compartimentStructureIdx = 1;
    NumberParameter spotQualityThreshold = new NumberParameter("Spot Quality Threshold", 3, 3.5);
    NumberParameter maxGap = new BoundedNumberParameter("Maximum frame gap", 0, 2, 0, null);
    NumberParameter maxLinkingDistance = new BoundedNumberParameter("FTF Maximum Linking Distance (0=skip)", 2, 0.75, 0, null);
    NumberParameter maxLinkingDistanceGC = new BoundedNumberParameter("Gap-closing Maximum Linking Distance (0=skip)", 2, 0.75, 0, null);
    NumberParameter maxLinkingDistanceLQ = new BoundedNumberParameter("Low quality spots Maximum Linking distance (0=skip)", 2, 0.75, 0, null);
    Parameter[] parameters = new Parameter[]{maxLinkingDistance, maxLinkingDistanceGC, maxGap, maxLinkingDistanceLQ, spotQualityThreshold};
    
    public LAPTracker setLinkingMaxDistance(double maxDist, double maxDistGapClosing, double maxDistLowQuality) {
        maxLinkingDistance.setValue(maxDist);
        maxLinkingDistanceGC.setValue(maxDistGapClosing);
        maxLinkingDistanceLQ.setValue(maxDistLowQuality);
        return this;
    }
    
    public void track(int structureIdx, List<StructureObject> parentTrack) {
        SpotPopulation spotCollection = new SpotPopulation();
        for (StructureObject p : parentTrack) {
            spotCollection.addSpots(p, structureIdx, p.getObjectPopulation(structureIdx).getObjects(), compartimentStructureIdx, spotQualityThreshold.getValue().doubleValue());
        }
        logger.debug("LAP Tracker: {}, spot HQ: {}, #spots LQ: {}", parentTrack.get(0), spotCollection.getSpotSet(false, true).size(), spotCollection.getSpotSet(true, false).size());
        LAPTrackerCore core = new LAPTrackerCore(spotCollection).setLinkingMaxDistance(maxLinkingDistance.getValue().doubleValue(), maxLinkingDistanceGC.getValue().doubleValue(), maxLinkingDistanceLQ.getValue().doubleValue());
        boolean processOk = core.process();
        if (!processOk) logger.error("LAPTracker error : {}", core.getErrorMessage());
        else spotCollection.setTrackLinks(parentTrack, structureIdx, core.getEdges());
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
}
