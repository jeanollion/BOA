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
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import boa.plugins.TrackPostFilter;
import boa.utils.Utils;
import java.util.function.BiPredicate;

/**
 *
 * @author jollion
 */
public class RemoveTracksStartingAfterFrame implements TrackPostFilter {
    
    BoundedNumberParameter startFrame = new BoundedNumberParameter("Maximum starting frame", 0, 0, 0, null);
    ChoiceParameter mergePolicy = new ChoiceParameter("Merge Policy", Utils.toStringArray(PostFilter.MERGE_POLICY.values()), PostFilter.MERGE_POLICY.ALWAYS_MERGE.toString(), false).setToolTipText("When removing an object/track that has a previous object (p) that was linked to this object and one other object (n). p is now linked to one single object n. This parameter controls wheter / in which conditions should p's track and n's track be merged.<br/><ul><li>NEVER_MERGE: never merge tracks</li><li>ALWAYS_MERGE: always merge tracks</li><li>MERGE_TRACKS_SIZE_COND: merge tracks only if size(n)>0.8 * size(p) (useful for bacteria linking)</li></ul>");
    
    Parameter[] parameters = new Parameter[]{startFrame, mergePolicy};
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
        if (!objectsToRemove.isEmpty()) ManualCorrection.deleteObjects(null, objectsToRemove, mergePredicate, false);
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
}
