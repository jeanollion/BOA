/*
 * Copyright (C) 2015 jollion
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
package plugins.plugins.processingScheme;

import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import configuration.parameters.PostFilterSequence;
import configuration.parameters.PreFilterSequence;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import image.Image;
import java.util.Collection;
import java.util.List;
import plugins.PostFilter;
import plugins.PreFilter;
import plugins.ProcessingScheme;
import plugins.Segmenter;
import utils.ThreadRunner;
import utils.ThreadRunner.ThreadAction;

/**
 *
 * @author jollion
 */
public class SegmentOnly implements ProcessingScheme {
    protected PreFilterSequence preFilters = new PreFilterSequence("Pre-Filters");
    protected PostFilterSequence postFilters = new PostFilterSequence("Post-Filters");
    protected PluginParameter<Segmenter> segmenter = new PluginParameter<Segmenter>("Segmentation algorithm", Segmenter.class, false);
    Parameter[] parameters= new Parameter[]{preFilters, segmenter, postFilters};
    
    public SegmentOnly() {}
    
    public SegmentOnly(Segmenter segmenter) {
        this.segmenter.setPlugin(segmenter);
    }
    @Override public SegmentOnly addPreFilters(PreFilter... preFilter) {
        preFilters.add(preFilter);
        return this;
    }
    @Override public SegmentOnly addPostFilters(PostFilter... postFilter) {
        postFilters.add(postFilter);
        return this;
    }
    @Override public SegmentOnly addPreFilters(Collection<PreFilter> preFilter) {
        preFilters.add(preFilter);
        return this;
    }
    @Override public SegmentOnly addPostFilters(Collection<PostFilter> postFilter){
        postFilters.add(postFilter);
        return this;
    }
    
    @Override public PreFilterSequence getPreFilters() {
        return preFilters;
    }
    
    @Override public PostFilterSequence getPostFilters() {
        return postFilters;
    }
    
    @Override public void segmentAndTrack(final int structureIdx, final List<StructureObject> parentTrack) {
        if (!segmenter.isOnePluginSet()) {
            logger.info("No segmenter set for structure: {}", structureIdx);
            return;
        }
        if (parentTrack.isEmpty()) return;
        if (parentTrack.get(0).getMicroscopyField().singleFrame(structureIdx)) {
            ObjectPopulation pop = segment(parentTrack.get(0), structureIdx);
            for (StructureObject parent : parentTrack) parent.setChildrenObjects(pop.duplicate(), structureIdx);
        } else {
            ThreadAction<StructureObject> ta = new ThreadAction<StructureObject>() {
                @Override public void run(StructureObject parent, int idx, int threadIdx) {
                    parent.setChildrenObjects(segment(parent, structureIdx), structureIdx);
                }
            };
            ThreadRunner.execute(parentTrack, ta);
            //for (StructureObject parent : parentTrack) parent.setChildrenObjects(segment(parent, structureIdx), structureIdx);
        }
    }
    
    private ObjectPopulation segment(StructureObject parent, int structureIdx) {
        Segmenter s = segmenter.instanciatePlugin();
        Image input = preFilters.filter(parent.getRawImage(structureIdx), parent);
        ObjectPopulation pop = s.runSegmenter(input, structureIdx, parent);
        return postFilters.filter(pop, structureIdx, parent);
    }

    @Override public void trackOnly(int structureIdx, List<StructureObject> parentTrack) {}

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    @Override public Segmenter getSegmenter() {return segmenter.instanciatePlugin();}
}
