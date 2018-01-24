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
package boa.plugins.plugins.processing_scheme;

import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.PostFilterSequence;
import boa.configuration.parameters.PreFilterSequence;
import boa.configuration.parameters.TrackPostFilterSequence;
import boa.data_structure.StructureObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import boa.plugins.MultiThreaded;
import boa.plugins.PostFilter;
import boa.plugins.PreFilter;
import boa.plugins.ProcessingScheme;
import boa.plugins.ProcessingSchemeWithTracking;
import boa.plugins.Segmenter;
import boa.plugins.TrackPostFilter;
import boa.plugins.Tracker;
import boa.plugins.TrackerSegmenter;
import boa.utils.MultipleException;
import boa.utils.Pair;

/**
 *
 * @author jollion
 */
public class SegmentAndTrack implements ProcessingSchemeWithTracking {
    int nThreads;
    protected PreFilterSequence preFilters = new PreFilterSequence("Pre-Filters");
    protected PostFilterSequence postFilters = new PostFilterSequence("Post-Filters");
    PluginParameter<TrackerSegmenter> tracker = new PluginParameter<TrackerSegmenter>("Tracker", TrackerSegmenter.class, true);
    protected TrackPostFilterSequence trackPostFilters = new TrackPostFilterSequence("Track Post-Filters");
    Parameter[] parameters= new Parameter[]{tracker, preFilters, postFilters, trackPostFilters};
    
    public SegmentAndTrack(){}
    
    public SegmentAndTrack(TrackerSegmenter tracker){
        this.tracker.setPlugin(tracker);
    }
    public SegmentAndTrack addTrackPostFilters(TrackPostFilter... postFilter) {
        trackPostFilters.add(postFilter);
        return this;
    }
    
    public SegmentAndTrack addTrackPostFilters(Collection<TrackPostFilter> postFilter) {
        trackPostFilters.add(postFilter);
        return this;
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
    public TrackPostFilterSequence getTrackPostFilters() {
        return trackPostFilters;
    }
    
    @Override
    public List<Pair<String, Exception>> segmentAndTrack(int structureIdx, List<StructureObject> parentTrack, ExecutorService executor) {
        if (!tracker.isOnePluginSet()) {
            logger.info("No tracker set for structure: {}", structureIdx);
            return Collections.EMPTY_LIST;
        }
        if (parentTrack.isEmpty()) return Collections.EMPTY_LIST;
        //logger.debug("segmentAndTrack: # prefilters: {}", preFilters.getChildCount());
        List<Pair<String, Exception>> l = new ArrayList<>();
        try {
            TrackerSegmenter t = tracker.instanciatePlugin();
            if (t instanceof MultiThreaded) ((MultiThreaded)t).setExecutor(executor);
            t.segmentAndTrack(structureIdx, parentTrack, preFilters, postFilters);
            //logger.debug("executing #{} trackPostFilters for parents track: {} structure: {}", trackPostFilters.getChildren().size(), parentTrack.get(0), structureIdx);
            trackPostFilters.filter(structureIdx, parentTrack, executor); 
        } catch (MultipleException me) {
            l.addAll(me.getExceptions());
        } catch (Exception ex) {
            l.add(new Pair(parentTrack.get(0).toString(), ex));
        }
        return l;
    }

    @Override
    public List<Pair<String, Exception>> trackOnly(int structureIdx, List<StructureObject> parentTrack, ExecutorService executor) {
        if (!tracker.isOnePluginSet()) {
            logger.info("No tracker set for structure: {}", structureIdx);
            return Collections.EMPTY_LIST;
        }
        for (StructureObject parent : parentTrack) {
            for (StructureObject c : parent.getChildren(structureIdx)) c.resetTrackLinks(true, true);
        }
        
        List<Pair<String, Exception>> l = new ArrayList<>();
        try {
            TrackerSegmenter t = tracker.instanciatePlugin();
            if (t instanceof MultiThreaded) ((MultiThreaded)t).setExecutor(executor);
            t.track(structureIdx, parentTrack);
            trackPostFilters.filter(structureIdx, parentTrack, executor); // TODO return exceptions
        } catch (MultipleException me) {
            l.addAll(me.getExceptions());
        } catch (Exception ex) {
            l.add(new Pair(parentTrack.get(0).toString(), ex));
        }
        
        return l;
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
