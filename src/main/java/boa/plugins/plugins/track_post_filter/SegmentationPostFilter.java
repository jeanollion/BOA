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
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.PostFilterSequence;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.image.SimpleBoundingBox;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import boa.plugins.MultiThreaded;
import boa.plugins.PostFilter;
import boa.plugins.TrackPostFilter;
import boa.utils.MultipleException;
import boa.utils.Pair;
import boa.utils.ThreadRunner;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class SegmentationPostFilter implements TrackPostFilter, MultiThreaded {
    final static String[] methods = new String[]{"Delete single objects", "Delete whole track", "Prune Track"};
    ChoiceParameter deleteMethod = new ChoiceParameter("Delete method", methods, methods[0], false);
    PostFilterSequence postFilters = new PostFilterSequence("Filter");
    ExecutorService executor;
    
    public SegmentationPostFilter setDeleteMethod(int method) {
        this.deleteMethod.setSelectedIndex(method);
        return this;
    }
    public SegmentationPostFilter addPostFilters(PostFilter... postFilters) {
        this.postFilters.add(postFilters);
        return this;
    }
    
    @Override
    public void filter(int structureIdx, List<StructureObject> parentTrack) throws MultipleException {
        if (postFilters.getChildCount()==0) return;
        List<StructureObject> objectsToRemove = new ArrayList<>();
        ThreadRunner.execute(parentTrack, false, (parent, idx) -> {
            RegionPopulation pop = parent.getObjectPopulation(structureIdx);
            //logger.debug("seg post-filter: {}", parent);
            pop.translate(new SimpleBoundingBox(parent.getBounds()).reverseOffset(), false); // go back to relative landmark
            pop=postFilters.filter(pop, structureIdx, parent);
            List<StructureObject> toRemove=null;
            if (parent.getChildren(structureIdx)!=null) {
                for (StructureObject o : parent.getChildren(structureIdx)) {
                    if (!pop.getRegions().contains(o.getRegion())) {
                        if (toRemove==null) toRemove= new ArrayList<>();
                        toRemove.add(o);
                    }
                }
            }
            pop.translate(parent.getBounds(), true); // go back to absolute landmark
            if (toRemove!=null) {
                synchronized(objectsToRemove) {
                    objectsToRemove.addAll(toRemove);
                }
            }
            // TODO ABLE TO INCLUDE POST-FILTERS THAT CREATE NEW OBJECTS -> CHECK INTERSETION INSTEAD OF OBJECT EQUALITY
        }, executor, null);
        if (!objectsToRemove.isEmpty()) { 
            //logger.debug("delete method: {}, objects to delete: {}", this.deleteMethod.getSelectedItem(), objectsToRemove.size());
            if (this.deleteMethod.getSelectedIndex()==0) ManualCorrection.deleteObjects(null, objectsToRemove, false); // only delete
            else if (this.deleteMethod.getSelectedIndex()==2) ManualCorrection.prune(null, objectsToRemove, false); // prune tracks
            else if (this.deleteMethod.getSelectedIndex()==1) { 
                Set<StructureObject> trackHeads = new HashSet<>(Utils.transform(objectsToRemove, o->o.getTrackHead()));
                objectsToRemove.clear();
                for (StructureObject th : trackHeads) objectsToRemove.addAll(StructureObjectUtils.getTrack(th, false));
                ManualCorrection.deleteObjects(null, objectsToRemove, false);
            }
        }
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{deleteMethod, postFilters};
    }

    @Override
    public void setExecutor(ExecutorService executor) {
        this.executor=executor;
    }
    
}
