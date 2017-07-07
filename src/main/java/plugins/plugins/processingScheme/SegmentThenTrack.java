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
import configuration.parameters.TrackPostFilterSequence;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import image.Image;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import plugins.MultiThreaded;
import plugins.PostFilter;
import plugins.PreFilter;
import plugins.ProcessingScheme;
import plugins.Segmenter;
import plugins.TrackPostFilter;
import plugins.Tracker;
import utils.Pair;
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
    protected TrackPostFilterSequence trackPostFilters = new TrackPostFilterSequence("Track Post-Filters");
    protected Parameter[] parameters;
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
    @Override public PreFilterSequence getPreFilters() {
        return preFilters;
    }
    
    @Override public PostFilterSequence getPostFilters() {
        return postFilters;
    }
    public SegmentThenTrack setPreFilters(PreFilterSequence preFilters) {
        this.preFilters=preFilters;
        return this;
    }
    public SegmentThenTrack setPostFilters(PostFilterSequence postFilters) {
        this.postFilters=postFilters;
        return this;
    }
    @Override
    public Segmenter getSegmenter() {return segmenter.instanciatePlugin();}
    public Tracker getTracker() {return tracker.instanciatePlugin();}

    @Override
    public List<Pair<String, Exception>> segmentAndTrack(final int structureIdx, final List<StructureObject> parentTrack, ExecutorService executor) {
        return segmentThenTrack(structureIdx, parentTrack, executor);
    }
    
    //@Override
    public List<Pair<String, Exception>> segmentThenTrack(final int structureIdx, final List<StructureObject> parentTrack, ExecutorService executor) {
        if (parentTrack.isEmpty()) return Collections.EMPTY_LIST;
        if (!segmenter.isOnePluginSet()) {
            logger.info("No segmenter set for structure: {}", structureIdx);
            return Collections.EMPTY_LIST;
        }
        if (!tracker.isOnePluginSet()) {
            logger.info("No tracker set for structure: {}", structureIdx);
            return Collections.EMPTY_LIST;
        }
        List<Pair<String, Exception>> l = segmentOnly(structureIdx, parentTrack, executor);
        List<Pair<String, Exception>> l2 = trackOnly(structureIdx, parentTrack, executor);
        trackPostFilters.filter(structureIdx, parentTrack, executor); // TODO return exceptions
        if (!l.isEmpty() && !l2.isEmpty()) l.addAll(l2);
        else if (!l2.isEmpty()) return l2;
        return l;
    }
    public List<Pair<String, Exception>> segmentOnly(final int structureIdx, final List<StructureObject> parentTrack, ExecutorService executor) {
        if (!segmenter.isOnePluginSet()) {
            logger.info("No segmenter set for structure: {}", structureIdx);
            return Collections.EMPTY_LIST;
        }
        if (parentTrack.isEmpty()) return Collections.EMPTY_LIST;
        SegmentOnly seg = new SegmentOnly(segmenter.instanciatePlugin()).setPreFilters(preFilters).setPostFilters(postFilters);
        return seg.segmentAndTrack(structureIdx, parentTrack, executor);
    }

    @Override
    public List<Pair<String, Exception>> trackOnly(final int structureIdx, List<StructureObject> parentTrack, ExecutorService executor) {
        if (!tracker.isOnePluginSet()) {
            logger.info("No tracker set for structure: {}", structureIdx);
            return Collections.EMPTY_LIST;
        }
        for (StructureObject parent : parentTrack) {
            for (StructureObject c : parent.getChildren(structureIdx)) c.resetTrackLinks(true, true);
        }
        List<Pair<String, Exception>> l = Collections.EMPTY_LIST;
        try {
            Tracker t = tracker.instanciatePlugin();
            if (t instanceof MultiThreaded) ((MultiThreaded)t).setExecutor(executor);
            t.track(structureIdx, parentTrack);
        } catch (Exception ex) {
            l = new ArrayList<>(1);
            l.add(new Pair(parentTrack.get(0).toString(), ex));
        }
        return l;
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{preFilters, segmenter, postFilters, tracker, trackPostFilters};
    }
    
}
