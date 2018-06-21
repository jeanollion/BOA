/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins.plugins.processing_pipeline;

import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.PostFilterSequence;
import boa.configuration.parameters.PreFilterSequence;
import boa.configuration.parameters.TrackPostFilterSequence;
import boa.configuration.parameters.TrackPreFilterSequence;
import boa.data_structure.StructureObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import boa.plugins.MultiThreaded;
import boa.plugins.PostFilter;
import boa.plugins.PreFilter;
import boa.plugins.Segmenter;
import boa.plugins.ToolTip;
import boa.plugins.TrackPostFilter;
import boa.plugins.TrackPreFilter;
import boa.plugins.Tracker;
import boa.plugins.TrackerSegmenter;
import boa.plugins.plugins.track_pre_filters.PreFilters;
import boa.utils.MultipleException;
import boa.utils.Pair;
import boa.plugins.ProcessingPipeline;
import boa.plugins.ProcessingPipelineWithTracking;

/**
 *
 * @author jollion
 */
public class SegmentAndTrack extends SegmentationAndTrackingProcessingPipeline<SegmentAndTrack> implements ToolTip {
    int nThreads;
    PluginParameter<TrackerSegmenter> tracker = new PluginParameter<>("Tracker", TrackerSegmenter.class, true);
    Parameter[] parameters= new Parameter[]{preFilters, trackPreFilters, tracker, postFilters, trackPostFilters};
    
    public SegmentAndTrack(){}
    
    public SegmentAndTrack(TrackerSegmenter tracker){
        this.tracker.setPlugin(tracker);
    }
    
    @Override
    public String getToolTipText() {
        return "Performs the segmentation and Tracking steps jointly";
    }
    
    public TrackerSegmenter getTracker() {
        return tracker.instanciatePlugin();
    }
    
    @Override
    public void segmentAndTrack(int structureIdx, List<StructureObject> parentTrack) {
        if (!tracker.isOnePluginSet()) {
            logger.info("No tracker set for structure: {}", structureIdx);
            return;
        }
        if (parentTrack.isEmpty()) return;
        //logger.debug("segmentAndTrack: # prefilters: {}", preFilters.getChildCount());
        TrackerSegmenter t = tracker.instanciatePlugin();
        if (t instanceof MultiThreaded) ((MultiThreaded)t).setMultithread(true);
        TrackPreFilterSequence tpf = getTrackPreFilters(true);
        t.segmentAndTrack(structureIdx, parentTrack, tpf, postFilters);
        logger.debug("executing #{} trackPostFilters for parents track: {} structure: {}", trackPostFilters.getChildren().size(), parentTrack.get(0), structureIdx);
        trackPostFilters.filter(structureIdx, parentTrack); 
        logger.debug("executed #{} trackPostFilters for parents track: {} structure: {}", trackPostFilters.getChildren().size(), parentTrack.get(0), structureIdx);
    }

    @Override
    public void trackOnly(int structureIdx, List<StructureObject> parentTrack) {
        if (!tracker.isOnePluginSet()) {
            logger.info("No tracker set for structure: {}", structureIdx);
            return;
        }
        for (StructureObject parent : parentTrack) {
            if (parent.getChildren(structureIdx)==null) continue;
            for (StructureObject c : parent.getChildren(structureIdx)) c.resetTrackLinks(true, true);
        }
        TrackerSegmenter t = tracker.instanciatePlugin();
        if (t instanceof MultiThreaded) ((MultiThreaded)t).setMultithread(true);
        t.track(structureIdx, parentTrack);
        trackPostFilters.filter(structureIdx, parentTrack); 
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
