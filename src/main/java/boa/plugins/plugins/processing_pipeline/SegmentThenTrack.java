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
import boa.data_structure.StructureObject;
import java.util.List;
import boa.plugins.Segmenter;
import boa.plugins.ToolTip;
import boa.plugins.TrackPostFilter;
import boa.plugins.TrackPreFilter;
import boa.plugins.Tracker;

/**
 *
 * @author jollion
 */
public class SegmentThenTrack extends SegmentationAndTrackingProcessingPipeline<SegmentThenTrack> implements ToolTip {
    protected PluginParameter<Tracker> tracker = new PluginParameter<>("Tracker", Tracker.class, true);
    protected PluginParameter<Segmenter> segmenter = new PluginParameter<>("Segmentation algorithm", Segmenter.class, false);
    protected Parameter[] parameters = new Parameter[]{preFilters, trackPreFilters, segmenter, postFilters, tracker, trackPostFilters};
    public SegmentThenTrack() {}
    public SegmentThenTrack(Segmenter segmenter, Tracker tracker) {
        this.segmenter.setPlugin(segmenter);
        this.tracker.setPlugin(tracker);
    }
    
    @Override
    public String getToolTipText() {
        return "Performs the segmentation step followed by the Tracking step (independently)";
    }
    
    @Override
    public Segmenter getSegmenter() {return segmenter.instanciatePlugin();}
    
    public Tracker getTracker() {return tracker.instanciatePlugin();}

    @Override
    public void segmentAndTrack(final int structureIdx, final List<StructureObject> parentTrack) {
        segmentThenTrack(structureIdx, parentTrack);
    }
    
    //@Override
    public void segmentThenTrack(final int structureIdx, final List<StructureObject> parentTrack) {
        if (parentTrack.isEmpty()) return;
        if (!segmenter.isOnePluginSet()) {
            logger.info("No segmenter set for structure: {}", structureIdx);
            return;
        }
        if (!tracker.isOnePluginSet()) {
            logger.info("No tracker set for structure: {}", structureIdx);
            return;
        }
        segmentOnly(structureIdx, parentTrack);
        trackOnly(structureIdx, parentTrack);
        trackPostFilters.filter(structureIdx, parentTrack);
    }
    public void segmentOnly(final int structureIdx, final List<StructureObject> parentTrack) {
        if (!segmenter.isOnePluginSet()) {
            logger.info("No segmenter set for structure: {}", structureIdx);
            return;
        }
        if (parentTrack.isEmpty()) return;
        SegmentOnly seg = new SegmentOnly(segmenter.instanciatePlugin()).setPreFilters(preFilters).setTrackPreFilters(trackPreFilters).setPostFilters(postFilters);
        seg.segmentAndTrack(structureIdx, parentTrack);
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
