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

import boa.ui.ManualEdition;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.plugins.ToolTip;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import boa.plugins.TrackPostFilter;
import static boa.plugins.plugins.track_post_filter.PostFilter.MERGE_POLICY_TT;
import boa.utils.Utils;
import java.util.function.BiPredicate;

/**
 *
 * @author jollion
 */
public class RemoveTracksStartingAfterFrame implements TrackPostFilter, ToolTip {
    
    BoundedNumberParameter startFrame = new BoundedNumberParameter("Maximum starting frame", 0, 0, 0, null);
    ChoiceParameter mergePolicy = new ChoiceParameter("Merge Policy", Utils.toStringArray(PostFilter.MERGE_POLICY.values()), PostFilter.MERGE_POLICY.ALWAYS_MERGE.toString(), false).setToolTipText(MERGE_POLICY_TT);
    Parameter[] parameters = new Parameter[]{startFrame, mergePolicy};
    
    @Override
    public String getToolTipText() {
        return "Remove tracks that start after a user-defined frame";
    }
    
    public RemoveTracksStartingAfterFrame() {}
    
    public RemoveTracksStartingAfterFrame setMergePolicy(PostFilter.MERGE_POLICY policy) {
        mergePolicy.setSelectedItem(policy.toString());
        return this;
    }
    
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
        BiPredicate<StructureObject, StructureObject> mergePredicate = PostFilter.MERGE_POLICY.valueOf(mergePolicy.getSelectedItem()).mergePredicate;
        if (!objectsToRemove.isEmpty()) ManualEdition.deleteObjects(null, objectsToRemove, mergePredicate, false);
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }

    
    
}
