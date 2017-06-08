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
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import image.BoundingBox;
import image.Image;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import plugins.PostFilter;
import plugins.PreFilter;
import plugins.ProcessingScheme;
import plugins.Segmenter;
import plugins.UseMaps;
import utils.ThreadRunner;
import utils.ThreadRunner.ThreadAction;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class SegmentOnly implements ProcessingScheme {
    protected PreFilterSequence preFilters = new PreFilterSequence("Pre-Filters");
    protected PostFilterSequence postFilters = new PostFilterSequence("Post-Filters");
    protected PluginParameter<Segmenter> segmenter = new PluginParameter<Segmenter>("Segmentation algorithm", Segmenter.class, false);
    Parameter[] parameters;
    
    public SegmentOnly() {}
    
    public SegmentOnly(Segmenter segmenter) {
        this.segmenter.setPlugin(segmenter);
    }
    protected SegmentOnly(PluginParameter<Segmenter> segmenter) {
        this.segmenter=segmenter;
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
    public SegmentOnly setPreFilters(PreFilterSequence preFilters) {
        this.preFilters=preFilters;
        return this;
    }
    public SegmentOnly setPostFilters(PostFilterSequence postFilters) {
        this.postFilters=postFilters;
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
        int segParentStructureIdx = parentTrack.get(0).getExperiment().getStructure(structureIdx).getSegmentationParentStructure();
        if (parentTrack.get(0).getMicroscopyField().singleFrame(structureIdx)) {
            ObjectPopulation pop = segment(parentTrack.get(0), structureIdx, segParentStructureIdx);
            for (StructureObject parent : parentTrack) parent.setChildrenObjects(pop.duplicate(), structureIdx);
        } else {
            ThreadAction<StructureObject> ta = (StructureObject parent, int idx) -> {
                parent.setChildrenObjects(segment(parent, structureIdx, segParentStructureIdx), structureIdx);
            };
            ThreadRunner.execute(parentTrack, false, ta);
            //for (StructureObject parent : parentTrack) parent.setChildrenObjects(segment(parent, structureIdx), structureIdx);
        }
        
    }
    
    private ObjectPopulation segment(StructureObject parent, int structureIdx, int segmentationStructureIdx) { // TODO mieux gérer threads -> faire liste. Option filtres avant ou après découpage.. 
        Image input = preFilters.filter(parent.getRawImage(structureIdx), parent);
        if (segmentationStructureIdx>parent.getStructureIdx()) {
            Segmenter seg = segmenter.instanciatePlugin();
            Image[] maps=null;
            if (seg instanceof UseMaps) maps = ((UseMaps)seg).computeMaps(parent.getRawImage(structureIdx), input);
            List<Object3D> objects = new ArrayList<>();
            for (StructureObject subParent : parent.getChildren(segmentationStructureIdx)) {
                seg = segmenter.instanciatePlugin();
                if (maps!=null) ((UseMaps)seg).setMaps(Utils.apply(maps, new Image[maps.length], i -> i.cropWithOffset(subParent.getBounds())));
                ObjectPopulation pop = seg.runSegmenter(input.cropWithOffset(subParent.getBounds()), structureIdx, subParent);
                pop = postFilters.filter(pop, structureIdx, parent);
                pop.translate(subParent.getBounds(), true);
                objects.addAll(pop.getObjects());
            }
            //logger.debug("Segment: Parent: {}, subParents: {}, totalChildren: {}, subPBound: {}, {}, {}, pBOunds: {}", parent, parent.getChildren(segmentationStructureIdx).size(), objects.size(), Utils.toStringList(parent.getChildren(segmentationStructureIdx).subList(0,1), o -> o.getBounds().toString()), Utils.toStringList(parent.getChildren(segmentationStructureIdx).subList(0,1), o -> o.getRelativeBoundingBox(parent).toString()), Utils.toStringList(parent.getChildren(segmentationStructureIdx).subList(0,1), o -> o.getRelativeBoundingBox(parent.getRoot()).toString()), parent.getBounds());
            return new ObjectPopulation(objects, input, true);
        } else {
            ObjectPopulation pop = segmenter.instanciatePlugin().runSegmenter(input, structureIdx, parent);
            return postFilters.filter(pop, structureIdx, parent);
        }
    }

    @Override public void trackOnly(int structureIdx, List<StructureObject> parentTrack) {}

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{preFilters, segmenter, postFilters};
    }
    @Override public Segmenter getSegmenter() {return segmenter.instanciatePlugin();}
}
