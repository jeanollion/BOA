/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins.plugins.track_post_filter;

import boa.gui.ManualCorrection;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import boa.plugins.TrackPostFilter;

/**
 *
 * @author jollion
 */
public class RemoveTracksStartingAfterFrame implements TrackPostFilter {
    
    BoundedNumberParameter startFrame = new BoundedNumberParameter("Maximum starting frame", 0, 0, 0, null);
    Parameter[] parameters = new Parameter[]{startFrame};
    public RemoveTracksStartingAfterFrame() {}
    public RemoveTracksStartingAfterFrame(int startFrame) {
        this.startFrame.setValue(startFrame);
    }
    @Override
    public void filter(int structureIdx, List<StructureObject> parentTrack) {
        int start = startFrame.getValue().intValue();
        List<StructureObject> objectsToRemove = new ArrayList<>();
        Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(parentTrack, structureIdx);
        for (Entry<StructureObject, List<StructureObject>> e : allTracks.entrySet()) {
            if (e.getKey().getFrame()>start) objectsToRemove.addAll(e.getValue());
        }
        //logger.debug("remove track trackLength: #objects to remove: {}", objectsToRemove.size());
        if (!objectsToRemove.isEmpty()) ManualCorrection.deleteObjects(null, objectsToRemove, false, false);
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
}
