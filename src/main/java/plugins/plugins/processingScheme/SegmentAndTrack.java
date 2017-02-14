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
import dataStructure.objects.StructureObject;
import java.util.Collection;
import java.util.List;
import plugins.PostFilter;
import plugins.PreFilter;
import plugins.ProcessingScheme;
import plugins.Segmenter;
import plugins.Tracker;
import plugins.TrackerSegmenter;

/**
 *
 * @author jollion
 */
public class SegmentAndTrack implements ProcessingScheme {
    int nThreads;
    protected PreFilterSequence preFilters = new PreFilterSequence("Pre-Filters");
    protected PostFilterSequence postFilters = new PostFilterSequence("Post-Filters");
    PluginParameter<TrackerSegmenter> tracker = new PluginParameter<TrackerSegmenter>("Tracker", TrackerSegmenter.class, true);
    Parameter[] parameters= new Parameter[]{tracker, preFilters, postFilters};
    
    public SegmentAndTrack(){}
    
    public SegmentAndTrack(TrackerSegmenter tracker){
        this.tracker.setPlugin(tracker);
    }
    @Override
    public SegmentAndTrack addPreFilters(PreFilter... preFilter) {
        preFilters.add(preFilter);
        return this;
    }
    @Override
    public SegmentAndTrack addPostFilters(PostFilter... postFilter) {
        postFilters.add(postFilter);
        return this;
    }
    @Override public SegmentAndTrack addPreFilters(Collection<PreFilter> preFilter) {
        preFilters.add(preFilter);
        return this;
    }
    @Override public SegmentAndTrack addPostFilters(Collection<PostFilter> postFilter){
        postFilters.add(postFilter);
        return this;
    }
    @Override public PreFilterSequence getPreFilters() {
        return preFilters;
    }
    
    @Override public PostFilterSequence getPostFilters() {
        return postFilters;
    }
    @Override
    public void segmentAndTrack(int structureIdx, List<StructureObject> parentTrack) {
        if (!tracker.isOnePluginSet()) {
            logger.info("No tracker set for structure: {}", structureIdx);
            return;
        }
        //logger.debug("segmentAndTrack: # prefilters: {}", preFilters.getChildCount());
        TrackerSegmenter t = tracker.instanciatePlugin();
        t.segmentAndTrack(structureIdx, parentTrack, preFilters, postFilters);
    }

    @Override
    public void trackOnly(int structureIdx, List<StructureObject> parentTrack) {
        if (!tracker.isOnePluginSet()) {
            logger.info("No tracker set for structure: {}", structureIdx);
            return;
        }
        for (StructureObject parent : parentTrack) {
            for (StructureObject c : parent.getChildren(structureIdx)) c.resetTrackLinks(true, true);
        }
        TrackerSegmenter t = tracker.instanciatePlugin();
        t.track(structureIdx, parentTrack);
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }

    /*public int setThreadNumber(int numThreads) {
        nThreads = numThreads;
        return nThreads;
    }*/
    @Override public Segmenter getSegmenter() {
        TrackerSegmenter t = tracker.instanciatePlugin();
        if (t!=null) return t.getSegmenter();
        else return null;
    }
}
