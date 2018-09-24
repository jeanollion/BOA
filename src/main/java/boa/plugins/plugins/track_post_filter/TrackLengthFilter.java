/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
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
public class TrackLengthFilter implements TrackPostFilter, ToolTip {
    
    BoundedNumberParameter minSize = new BoundedNumberParameter("Minimum Length", 0, 0, 0, null);
    BoundedNumberParameter maxSize = new BoundedNumberParameter("Maximum Length", 0, 0, 0, null);
    ChoiceParameter mergePolicy = new ChoiceParameter("Merge Policy", Utils.toStringArray(PostFilter.MERGE_POLICY.values()), PostFilter.MERGE_POLICY.ALWAYS_MERGE.toString(), false).setToolTipText(MERGE_POLICY_TT);
    Parameter[] parameters = new Parameter[]{minSize, maxSize, mergePolicy};
    
    @Override
    public String getToolTipText() {
        return "Removes tracks with length out of user-defined range";
    }
    
    public TrackLengthFilter() {}
    
    public TrackLengthFilter setMergePolicy(PostFilter.MERGE_POLICY policy) {
        mergePolicy.setSelectedItem(policy.toString());
        return this;
    }
    
    public TrackLengthFilter setMinSize(int minSize) {
        this.minSize.setValue(minSize);
        return this;
    }
    public TrackLengthFilter setMaxSize(int maxSize) {
        this.maxSize.setValue(maxSize);
        return this;
    }
    
    @Override
    public void filter(int structureIdx, List<StructureObject> parentTrack) {
        int min = minSize.getValue().intValue();
        int max = maxSize.getValue().intValue();
        List<StructureObject> objectsToRemove = new ArrayList<>();
        Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(parentTrack, structureIdx);
        for (Entry<StructureObject, List<StructureObject>> e : allTracks.entrySet()) {
            if (e.getValue().size()<min || (max>0 && e.getValue().size()>max)) objectsToRemove.addAll(e.getValue());
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
