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
import dataStructure.configuration.Experiment;
import dataStructure.objects.Region;
import dataStructure.objects.RegionPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import image.BoundingBox;
import image.Image;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import plugins.PostFilter;
import plugins.PreFilter;
import plugins.ProcessingScheme;
import plugins.Segmenter;
import plugins.UseMaps;
import utils.HashMapGetCreate;
import utils.Pair;
import utils.ThreadRunner;
import utils.ThreadRunner.ThreadAction;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class SegmentOnly implements ProcessingScheme {
    @FunctionalInterface public static interface ApplyToSegmenter { public void apply(StructureObject o, Segmenter segmenter);}
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
    @Override public List<Pair<String, Exception>> segmentAndTrack(final int structureIdx, final List<StructureObject> parentTrack, ExecutorService executor) {
        return segmentAndTrack(structureIdx, parentTrack, executor, null);
    }
    public List<Pair<String, Exception>> segmentAndTrack(final int structureIdx, final List<StructureObject> parentTrack, ExecutorService executor, ApplyToSegmenter applyToSegmenter) {
        if (!segmenter.isOnePluginSet()) {
            logger.info("No segmenter set for structure: {}", structureIdx);
            return Collections.EMPTY_LIST;
        }
        if (parentTrack.isEmpty()) return Collections.EMPTY_LIST;
        int parentStructureIdx = parentTrack.get(0).getStructureIdx();
        int segParentStructureIdx = parentTrack.get(0).getExperiment().getStructure(structureIdx).getSegmentationParentStructure();
        boolean subSegmentation = segParentStructureIdx>parentStructureIdx;
        boolean useMaps =  subSegmentation && segmenter.instanciatePlugin() instanceof UseMaps;
        boolean singleFrame = parentTrack.get(0).getMicroscopyField().singleFrame(structureIdx); // will semgent only on first frame
        
        HashMapGetCreate<StructureObject, Image> inputImages =  new HashMapGetCreate<>(parentTrack.size(), parent->preFilters.filter(parent.getRawImage(structureIdx), parent));
        HashMapGetCreate<StructureObject, Image[]> subMaps = useMaps? new HashMapGetCreate<>(parentTrack.size(), parent->((UseMaps)segmenter.instanciatePlugin()).computeMaps(parent.getRawImage(structureIdx), inputImages.getAndCreateIfNecessarySyncOnKey(parent))) : null; //
        // segment in direct parents
        List<StructureObject> allParents = singleFrame ? StructureObjectUtils.getAllChildren(parentTrack.subList(0, 1), segParentStructureIdx) : StructureObjectUtils.getAllChildren(parentTrack, segParentStructureIdx);
        Collections.shuffle(allParents); // reduce thread blocking
        final boolean ref2D= !allParents.isEmpty() && allParents.get(0).is2D() && parentTrack.get(0).getRawImage(structureIdx).getSizeZ()>1;
        List<Pair<String, Exception>> errors = new ArrayList<>();
        long t0 = System.currentTimeMillis();
        for (StructureObject p : parentTrack) p.getRawImage(structureIdx);
        long t1 = System.currentTimeMillis();
        //if (useMaps) errors.addAll(ThreadRunner.execute(parentTrack, false, (p, idx) -> subMaps.getAndCreateIfNecessarySyncOnKey(p), executor, null));
        long t2 = System.currentTimeMillis();
        RegionPopulation[] pops = new RegionPopulation[allParents.size()];
        errors.addAll(ThreadRunner.execute(allParents, false, (subParent, idx) -> {
            StructureObject globalParent = subParent.getParent(parentStructureIdx);
            Segmenter seg = segmenter.instanciatePlugin();
            if (useMaps) {
                Image[] maps = subMaps.getAndCreateIfNecessarySyncOnKey(globalParent);
                if (subSegmentation) ((UseMaps)seg).setMaps(Utils.transform(maps, new Image[maps.length], i -> i.cropWithOffset(ref2D? subParent.getBounds().duplicate().fitToImageZ(i):subParent.getBounds())));
                else ((UseMaps)seg).setMaps(maps);
            }
            if (applyToSegmenter!=null) applyToSegmenter.apply(subParent, seg);
            Image input = inputImages.getAndCreateIfNecessarySyncOnKey(globalParent);
            if (subSegmentation) input = input.cropWithOffset(ref2D?subParent.getBounds().duplicate().fitToImageZ(input):subParent.getBounds());
            RegionPopulation pop = seg.runSegmenter(input, structureIdx, subParent);
            pop = postFilters.filter(pop, structureIdx, subParent);
            if (subSegmentation && pop!=null) pop.translate(subParent.getBounds(), true);
            pops[idx] = pop;
        }, executor, null));
        inputImages.clear();
        if (useMaps) subMaps.clear();
        long t3 = System.currentTimeMillis();
        if (subSegmentation) { // collect if necessary and set to parent
            HashMapGetCreate<StructureObject, List<Region>> parentObjectMap = new HashMapGetCreate<>(parentTrack.size(), new HashMapGetCreate.ListFactory());
            HashMap<RegionPopulation, StructureObject> popParentMap = new HashMap<>(pops.length);
            for (int i = 0; i<pops.length; ++i) popParentMap.put(pops[i], allParents.get(i));
            Arrays.sort(pops, (p1, p2)->popParentMap.get(p1).compareTo(popParentMap.get(p2)));
            for (int i = 0; i<pops.length; ++i) {
                StructureObject subParent = popParentMap.get(pops[i]);
                StructureObject parent = subParent.getParent(parentStructureIdx);
                if (pops[i]!=null) {
                    List<Region> objects =  parentObjectMap.getAndCreateIfNecessary(parent);
                    int label = objects.size();
                    if (label>0) for (Region o : pops[i].getObjects()) o.setLabel(label++);
                    objects.addAll(pops[i].getObjects());
                }
                else logger.debug("pop null for subParent: {}", allParents.get(i));
            }
            RegionPopulation pop=null;
            for (Entry<StructureObject, List<Region>> e : parentObjectMap.entrySet()) {
                pop = new RegionPopulation(e.getValue(), e.getKey().getRawImage(structureIdx), true); // should keep 3D information
                e.getKey().setChildrenObjects(pop, structureIdx);
            }
            if (singleFrame) {
                if (parentObjectMap.size()>1) logger.error("Segmentation of structure: {} from track: {}, single frame but several populations", structureIdx, parentTrack.get(0));
                else {
                    for (StructureObject parent : parentTrack.subList(1, parentTrack.size())) parent.setChildrenObjects(pop!=null ? pop.duplicate() : null, structureIdx);
                }
            } else { // also set no children to remove children already present and avoid access to dao
                Collection<StructureObject> parentsWithNoChildren = new HashSet<>(parentTrack);
                parentsWithNoChildren.removeAll(parentObjectMap.keySet());
                for (StructureObject p : parentsWithNoChildren)  p.setChildrenObjects(null, structureIdx);
            }
        } else {
           for (int i = 0; i<pops.length; ++i) allParents.get(i).setChildrenObjects(pops[i], structureIdx);
           if (singleFrame) {
               if (pops.length>1) logger.error("Segmentation of structure: {} from track: {}, single frame but several populations", structureIdx, parentTrack.get(0));
               else for (StructureObject parent : parentTrack.subList(1, parentTrack.size())) parent.setChildrenObjects(pops[0]!=null ? pops[0].duplicate(): null, structureIdx);
           }
        }
        long t4 = System.currentTimeMillis();
        logger.debug("SegmentOnly: {}(trackLength: {}) total time: {}, load images: {}ms, compute maps: {}ms, process: {}ms, set to parents: {}", parentTrack.get(0), parentTrack.size(), t4-t0, t1-t0, t2-t1, t3-t2, t4-t3);
        return errors;
    }
    
    @Override public List<Pair<String, Exception>> trackOnly(int structureIdx, List<StructureObject> parentTrack, ExecutorService executor) {return Collections.EMPTY_LIST;}

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{preFilters, segmenter, postFilters};
    }
    @Override public Segmenter getSegmenter() {return segmenter.instanciatePlugin();}
}
