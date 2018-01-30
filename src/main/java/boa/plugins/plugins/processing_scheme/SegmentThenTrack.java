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
import boa.configuration.parameters.TrackPreFilterSequence;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.image.Image;
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
import boa.plugins.TrackPreFilter;
import boa.plugins.Tracker;
import boa.plugins.plugins.track_pre_filters.PreFilters;
import boa.utils.MultipleException;
import boa.utils.Pair;
import boa.utils.ThreadRunner;

/**
 *
 * @author jollion
 */
public class SegmentThenTrack implements ProcessingSchemeWithTracking {
    protected PluginParameter<Tracker> tracker = new PluginParameter<>("Tracker", Tracker.class, true);
    protected PreFilterSequence preFilters = new PreFilterSequence("Pre-Filters");
    protected TrackPreFilterSequence trackPreFilters = new TrackPreFilterSequence("Track Pre-Filters");
    protected PostFilterSequence postFilters = new PostFilterSequence("Post-Filters");
    protected PluginParameter<Segmenter> segmenter = new PluginParameter<>("Segmentation algorithm", Segmenter.class, false);
    protected TrackPostFilterSequence trackPostFilters = new TrackPostFilterSequence("Track Post-Filters");
    protected Parameter[] parameters = new Parameter[]{preFilters, trackPreFilters, segmenter, postFilters, tracker, trackPostFilters};
    public SegmentThenTrack() {}
    public SegmentThenTrack(Segmenter segmenter, Tracker tracker) {
        this.segmenter.setPlugin(segmenter);
        this.tracker.setPlugin(tracker);
    }
    
    public SegmentThenTrack addTrackPostFilters(TrackPostFilter... postFilter) {
        trackPostFilters.add(postFilter);
        return this;
    }
    
    public SegmentThenTrack addTrackPostFilters(Collection<TrackPostFilter> postFilter) {
        trackPostFilters.add(postFilter);
        return this;
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
    @Override public SegmentThenTrack addTrackPreFilters(TrackPreFilter... trackPreFilter) {
        trackPreFilters.add(trackPreFilter);
        return this;
    }
    @Override public SegmentThenTrack addTrackPreFilters(Collection<TrackPreFilter> trackPreFilter) {
        trackPreFilters.add(trackPreFilter);
        return this;
    }
    @Override public PreFilterSequence getPreFilters() {
        return preFilters;
    }
    @Override public TrackPreFilterSequence getTrackPreFilters(boolean addPreFilters) {
        if (addPreFilters && !preFilters.isEmpty()) return trackPreFilters.duplicate().addAtFirst(new PreFilters().add(preFilters));
        return trackPreFilters;
    }
    @Override public PostFilterSequence getPostFilters() {
        return postFilters;
    }
    @Override
    public TrackPostFilterSequence getTrackPostFilters() {
        return trackPostFilters;
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
        List<Pair<String, Exception>> res = new ArrayList<>();
        List<Pair<String, Exception>> l = segmentOnly(structureIdx, parentTrack, executor);
        List<Pair<String, Exception>> l2 = trackOnly(structureIdx, parentTrack, executor);
        res.addAll(l);
        res.addAll(l2);
        try {
            trackPostFilters.filter(structureIdx, parentTrack, executor); // TODO return exceptions
        }catch (MultipleException me) {
            l.addAll(me.getExceptions());
        } catch (Exception ex) {
            l.add(new Pair(parentTrack.get(0).toString(), ex));
        }
        return res;
    }
    public List<Pair<String, Exception>> segmentOnly(final int structureIdx, final List<StructureObject> parentTrack, ExecutorService executor) {
        if (!segmenter.isOnePluginSet()) {
            logger.info("No segmenter set for structure: {}", structureIdx);
            return Collections.EMPTY_LIST;
        }
        if (parentTrack.isEmpty()) return Collections.EMPTY_LIST;
        SegmentOnly seg = new SegmentOnly(segmenter.instanciatePlugin()).setPreFilters(preFilters).setTrackPreFilters(trackPreFilters).setPostFilters(postFilters);
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
        return parameters;
    }
    
}
