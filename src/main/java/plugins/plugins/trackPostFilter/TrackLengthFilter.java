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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import plugins.TrackPostFilter;

/**
 *
 * @author jollion
 */
public class TrackLengthFilter implements TrackPostFilter {
    
    BoundedNumberParameter minSize = new BoundedNumberParameter("Minimum Length", 0, 0, 0, null);
    BoundedNumberParameter maxSize = new BoundedNumberParameter("Maximum Length", 0, 0, 0, null);
    Parameter[] parameters = new Parameter[]{minSize, maxSize};
    
    public TrackLengthFilter() {}
    public TrackLengthFilter setMinSize(int minSize) {
        this.minSize.setValue(minSize);
        return this;
    }
    public TrackLengthFilter setMaxSize(int maxSize) {
        this.maxSize.setValue(maxSize);
        return this;
    }
    
    @Override
    public void filter(int structureIdx, List<StructureObject> parentTrack) throws Exception {
        int min = minSize.getValue().intValue();
        int max = maxSize.getValue().intValue();
        List<StructureObject> objectsToRemove = new ArrayList<>();
        Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(parentTrack, structureIdx);
        for (Entry<StructureObject, List<StructureObject>> e : allTracks.entrySet()) {
            if (e.getValue().size()<min || (max>0 && e.getValue().size()>max)) objectsToRemove.addAll(e.getValue());
        }
        //logger.debug("remove track trackLength: #objects to remove: {}", objectsToRemove.size());
        if (!objectsToRemove.isEmpty()) ManualCorrection.deleteObjects(null, objectsToRemove, false);
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
}
