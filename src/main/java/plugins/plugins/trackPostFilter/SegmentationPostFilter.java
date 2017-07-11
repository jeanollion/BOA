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
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import configuration.parameters.PostFilterSequence;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import plugins.MultiThreaded;
import plugins.PostFilter;
import plugins.TrackPostFilter;
import utils.MultipleException;
import utils.Pair;
import utils.ThreadRunner;

/**
 *
 * @author jollion
 */
public class SegmentationPostFilter implements TrackPostFilter, MultiThreaded {
    PostFilterSequence postFilters = new PostFilterSequence("Filter");
    ExecutorService executor;
    @Override
    public void filter(int structureIdx, List<StructureObject> parentTrack) throws MultipleException {
        if (postFilters.getChildCount()==0) return;
        List<StructureObject> objectsToRemove = new ArrayList<>();
        List<Pair<String, Exception>> errors = ThreadRunner.execute(parentTrack, false, (parent, idx) -> {
            ObjectPopulation pop = parent.getObjectPopulation(structureIdx);
            pop=postFilters.filter(pop, structureIdx, parent);
            List<StructureObject> toRemove=null;
            for (StructureObject o : parent.getChildren(structureIdx)) {
                if (!pop.getObjects().contains(o.getObject())) {
                    if (toRemove==null) toRemove= new ArrayList<>();
                    toRemove.add(o);
                }
            }
            if (toRemove!=null) {
                synchronized(objectsToRemove) {
                    objectsToRemove.addAll(toRemove);
                }
            }
            // TOBE ABLE TO INCLUDE POST-FILTERS THAT CREATE NEW OBJECTS -> CHECK INTERSETION INSTEAD OF OBJECT EQUALITY
        }, executor, null);
        if (!objectsToRemove.isEmpty()) { 
            //logger.debug("objects to delete: {}", objectsToRemove);
            //ManualCorrection.prune(null, objectsToRemove, false); // prune tracks
            ManualCorrection.deleteObjects(null, objectsToRemove, false); // only delete
            // TODO other options when deleting tracks: prune, fill-gap, create new track
            
        }
        if (!errors.isEmpty()) { // throw one exception for all
            throw new MultipleException(errors);
        }
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{postFilters};
    }

    @Override
    public void setExecutor(ExecutorService executor) {
        this.executor=executor;
    }
    
}
