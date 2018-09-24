/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins.plugins.processing_pipeline;

import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.TrackPreFilterSequence;
import boa.data_structure.StructureObject;
import java.util.List;
import boa.plugins.Segmenter;
import boa.plugins.ToolTip;
import boa.plugins.TrackerSegmenter;

/**
 *
 * @author Jean Ollion
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
