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
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.PostFilterSequence;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.image.SimpleBoundingBox;
import boa.plugins.ToolTip;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import boa.plugins.TrackPostFilter;
import boa.utils.MultipleException;
import boa.utils.Pair;
import boa.utils.ThreadRunner;
import boa.utils.Utils;
import static boa.utils.Utils.parallele;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 *
 * @author jollion
 */
public class PostFilter implements TrackPostFilter, ToolTip {
    PluginParameter<boa.plugins.PostFilter> filter = new PluginParameter<>("Filter",boa.plugins.PostFilter.class, false);
    final static String[] METHODS = new String[]{"Delete single objects", "Delete whole track", "Prune Track"};
    ChoiceParameter deleteMethod = new ChoiceParameter("Delete method", METHODS, METHODS[0], false)
            .setToolTipText("How to cope with lineage break when post-filter deletes objects. <ol>"
                    + "<li><em>Delete single objects</em>: will delete only the objects deleted by the post-filter, create a new branch starting from next object (if exist).</li>"
                    + "<li><em>Delete whole track</em>: deleted every previous and next tracks</li>"
                    + "<li><em>Prune Track</em>: delete the track from objects to be deleted plus the following connected tracks</li></ol>");

    public enum MERGE_POLICY {
        NERVER_MERGE(ManualEdition.NERVE_MERGE), 
        ALWAYS_MERGE(ManualEdition.ALWAYS_MERGE), 
        MERGE_TRACKS_BACT_SIZE_COND(ManualEdition.MERGE_TRACKS_BACT_SIZE_COND);
        public final BiPredicate<StructureObject, StructureObject> mergePredicate;
        private MERGE_POLICY(BiPredicate<StructureObject, StructureObject> mergePredicate) {
            this.mergePredicate=mergePredicate; 
        }
    }
    public final static String MERGE_POLICY_TT = "When removing an object/track that has a previous object (p) that was linked to this object and one other object (n). p is now linked to one single object n. This parameter controls wheter / in which conditions should p's track and n's track be merged.<br/><ul><li>NEVER_MERGE: never merge tracks</li><li>ALWAYS_MERGE: always merge tracks</li><li>MERGE_TRACKS_SIZE_COND: merge tracks only if size(n)>0.8 * size(p) (useful for bacteria linking)</li></ul>";
    ChoiceParameter mergePolicy = new ChoiceParameter("Merge Policy", Utils.toStringArray(MERGE_POLICY.values()), MERGE_POLICY.ALWAYS_MERGE.toString(), false).setToolTipText(MERGE_POLICY_TT);
    @Override 
    public String getToolTipText() {
        return "Performs regular post-filter frame-by-frame and manage lineage breaks";
    }
    public PostFilter setMergePolicy(PostFilter.MERGE_POLICY policy) {
        mergePolicy.setSelectedItem(policy.toString());
        return this;
    }
    
    public PostFilter() {}
    public PostFilter(boa.plugins.PostFilter filter) {
        this.filter.setPlugin(filter);
    }
    public PostFilter setDeleteMethod(int method) {
        this.deleteMethod.setSelectedIndex(method);
        return this;
    }
    
    @Override
    public void filter(int structureIdx, List<StructureObject> parentTrack) {
        boolean rootParent = parentTrack.stream().findAny().get().isRoot();
        Set<StructureObject> objectsToRemove = new HashSet<>();
        Consumer<StructureObject> exe = parent -> {
            RegionPopulation pop = parent.getObjectPopulation(structureIdx);
            //logger.debug("seg post-filter: {}", parent);
            if (!rootParent) pop.translate(new SimpleBoundingBox(parent.getBounds()).reverseOffset(), false); // go back to relative landmark for post-filter
            //if(parent.getFrame()==858) postFilters.set
            pop=filter.instanciatePlugin().runPostFilter(parent, structureIdx, pop);
            List<StructureObject> toRemove=null;
            if (parent.getChildren(structureIdx)!=null) {
                List<StructureObject> children = parent.getChildren(structureIdx);
                if (pop.getRegions().size()==children.size()) { // map each object by index
                    for (int i = 0; i<pop.getRegions().size(); ++i) {
                        children.get(i).setRegion(pop.getRegions().get(i));
                    }
                } else { // map object by region hashcode -> preFilter should not create new region, but only delete or modify. TODO: use matching algorithm to solve creation case
                    for (StructureObject o : parent.getChildren(structureIdx)) {
                        if (!pop.getRegions().contains(o.getRegion())) {
                            if (toRemove==null) toRemove= new ArrayList<>();
                            toRemove.add(o);
                        } 
                    }
                }
            }
            if (!rootParent) pop.translate(parent.getBounds(), true); // go back to absolute landmark
            if (toRemove!=null) {
                synchronized(objectsToRemove) {
                    objectsToRemove.addAll(toRemove);
                }
            }
             // TODO ABLE TO INCLUDE POST-FILTERS THAT CREATE NEW OBJECTS -> CHECK INTERSETION INSTEAD OF OBJECT EQUALITY
            
        };
        ThreadRunner.executeAndThrowErrors(parallele(parentTrack.stream(), true), exe);
        if (!objectsToRemove.isEmpty()) { 
            //logger.debug("delete method: {}, objects to delete: {}", this.deleteMethod.getSelectedItem(), objectsToRemove.size());
            BiPredicate<StructureObject, StructureObject> mergePredicate = MERGE_POLICY.valueOf(mergePolicy.getSelectedItem()).mergePredicate;
            switch (this.deleteMethod.getSelectedIndex()) {
                case 0:
                    ManualEdition.deleteObjects(null, objectsToRemove, mergePredicate, false); // only delete
                    break;
                case 2:
                    ManualEdition.prune(null, objectsToRemove, mergePredicate, false); // prune tracks
                    break;
                case 1:
                    Set<StructureObject> trackHeads = new HashSet<>(Utils.transform(objectsToRemove, o->o.getTrackHead()));
                    objectsToRemove.clear();
                    for (StructureObject th : trackHeads) objectsToRemove.addAll(StructureObjectUtils.getTrack(th, false));
                    ManualEdition.deleteObjects(null, objectsToRemove, mergePredicate, false);
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{filter, deleteMethod, mergePolicy};
    }
    
}
