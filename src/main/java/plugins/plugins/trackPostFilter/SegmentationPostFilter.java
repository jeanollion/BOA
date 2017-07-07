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

import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import configuration.parameters.PostFilterSequence;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import java.util.List;
import java.util.concurrent.ExecutorService;
import plugins.MultiThreaded;
import plugins.PostFilter;
import plugins.TrackPostFilter;
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
    public void filter(int structureIdx, List<StructureObject> parentTrack) {
        if (postFilters.getChildCount()==0) return;
        List<Pair<String, Exception>> errors = ThreadRunner.execute(parentTrack, false, (parent, idx) -> {
            ObjectPopulation pop = parent.getObjectPopulation(structureIdx);
            pop=postFilters.filter(pop, structureIdx, parent);
            parent.setChildrenObjects(pop, structureIdx);
        }, executor, null);
        
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
