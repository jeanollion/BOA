/*
 * Copyright (C) 2017 jollion
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
package plugins.plugins.trackPostFilter;

import boa.gui.ManualCorrection;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import plugins.TrackPostFilter;

/**
 *
 * @author jollion
 */
public class RemoveSaturatedMicrochannels implements TrackPostFilter {
    
    BoundedNumberParameter minPercentageOfSaturatedObjects = new BoundedNumberParameter("Min. percentage of track", 0, 0, 0, 100).setToolTipText("If the track has more than this proportion of saturated objects, it will be removed");
    BoundedNumberParameter minPercentageOfSaturatedPixels = new BoundedNumberParameter("Min. percentage of saturated pixel", 0, 0, 1, 100).setToolTipText("If an object has more than this proportion of saturated pixel it will be considered as a saturated object");
    Parameter[] parameters = new Parameter[]{minPercentageOfSaturatedPixels, minPercentageOfSaturatedObjects};
    public RemoveSaturatedMicrochannels() {}
    public RemoveSaturatedMicrochannels(double percentageOfSaturatedObjects, double percentageOfSaturatedPixels) {
        this.minPercentageOfSaturatedObjects.setValue(percentageOfSaturatedObjects);
        this.minPercentageOfSaturatedPixels.setValue(percentageOfSaturatedPixels);
    }
    @Override
    public void filter(int structureIdx, List<StructureObject> parentTrack) throws Exception {
        
        List<StructureObject> objectsToRemove = new ArrayList<>();
        Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(parentTrack, structureIdx);
        for (Entry<StructureObject, List<StructureObject>> e : allTracks.entrySet()) {
            if (isSaturated(e.getValue())) objectsToRemove.addAll(e.getValue());
        }
        //logger.debug("remove track trackLength: #objects to remove: {}", objectsToRemove.size());
        if (!objectsToRemove.isEmpty()) ManualCorrection.deleteObjects(null, objectsToRemove, false);
    }
    private boolean isSaturated(List<StructureObject> track) {
        int saturatedObjectCount = 0;
        double stauratedObjectThld = track.size() * minPercentageOfSaturatedObjects.getValue().doubleValue() / 100d;
        for (StructureObject o : track) {
            if (isSaturated(o)) {
                ++saturatedObjectCount;
                if (saturatedObjectCount>=stauratedObjectThld) return true;
            }
        }
        return false;
    }
    private boolean isSaturated(StructureObject o) {
        
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
}
