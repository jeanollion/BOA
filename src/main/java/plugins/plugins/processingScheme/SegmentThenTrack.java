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
import plugins.Tracker;
import utils.ThreadRunner;

/**
 *
 * @author jollion
 */
public class SegmentThenTrack implements ProcessingScheme {
    protected PluginParameter<Tracker> tracker = new PluginParameter<Tracker>("Tracker", Tracker.class, true);
    protected PreFilterSequence preFilters = new PreFilterSequence("Pre-Filters");
    protected PostFilterSequence postFilters = new PostFilterSequence("Post-Filters");
    protected PluginParameter<Segmenter> segmenter = new PluginParameter<Segmenter>("Segmentation algorithm", Segmenter.class, false);
    protected Parameter[] parameters= new Parameter[]{preFilters, segmenter, postFilters, tracker};
    public SegmentThenTrack() {}
    public SegmentThenTrack(Segmenter segmenter, Tracker tracker) {
        this.segmenter.setPlugin(segmenter);
        this.tracker.setPlugin(tracker);
    }
    @Override
    public SegmentThenTrack addPreFilters(PreFilter... preFilter) {
        preFilters.add(preFilter);
        return this;
    }
    @Override
    public SegmentThenTrack addPostFilters(PostFilter... postFilter) {
        postFilters.add(postFilter);
        return this;
    }
    @Override public SegmentThenTrack addPreFilters(Collection<PreFilter> preFilter) {
        preFilters.add(preFilter);
        return this;
    }
    @Override public SegmentThenTrack addPostFilters(Collection<PostFilter> postFilter){
        postFilters.add(postFilter);
        return this;
    }
    @Override
    public Segmenter getSegmenter() {return segmenter.instanciatePlugin();}
    public Tracker getTracker() {return tracker.instanciatePlugin();}

    @Override
    public void segmentAndTrack(final int structureIdx, final List<StructureObject> parentTrack) {
        if (!segmenter.isOnePluginSet()) {
            logger.info("No segmenter set for structure: {}", structureIdx);
            return;
        }
        if (!tracker.isOnePluginSet()) {
            logger.info("No tracker set for structure: {}", structureIdx);
            return;
        }
        segmentOnly(structureIdx, parentTrack);
        Tracker t = tracker.instanciatePlugin();
        t.track(structureIdx, parentTrack);
    }
    public void segmentOnly(final int structureIdx, final List<StructureObject> parentTrack) {
        if (!segmenter.isOnePluginSet()) {
            logger.info("No segmenter set for structure: {}", structureIdx);
            return;
        }
        if (parentTrack.isEmpty()) return;
        if (parentTrack.get(0).getMicroscopyField().singleFrame(structureIdx)) {
            ObjectPopulation pop = segment(parentTrack.get(0), structureIdx);
            for (StructureObject parent : parentTrack) parent.setChildrenObjects(pop.duplicate(), structureIdx);
        } else {
            ThreadRunner.ThreadAction<StructureObject> ta = new ThreadRunner.ThreadAction<StructureObject>() {
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
        if (s==null) throw new Error("No Segmenter Found for structure: "+structureIdx);
        Image input = preFilters.filter(parent.getRawImage(structureIdx), parent);
        ObjectPopulation pop = s.runSegmenter(input, structureIdx, parent);
        pop = postFilters.filter(pop, structureIdx, parent);
        return pop;
    }

    @Override
    public void trackOnly(final int structureIdx, List<StructureObject> parentTrack) {
        if (!tracker.isOnePluginSet()) {
            logger.info("No tracker set for structure: {}", structureIdx);
            return;
        }
        for (StructureObject parent : parentTrack) {
            for (StructureObject c : parent.getChildren(structureIdx)) c.resetTrackLinks(true, true);
        }
        Tracker t = tracker.instanciatePlugin();
        t.track(structureIdx, parentTrack);
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
}
