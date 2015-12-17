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
import dataStructure.objects.StructureObject;
import java.util.List;
import plugins.ProcessingScheme;
import plugins.Segmenter;
import plugins.Tracker;
import plugins.TrackerSegmenter;

/**
 *
 * @author jollion
 */
public class SegmentAndTrack implements ProcessingScheme {
    PluginParameter<TrackerSegmenter> tracker = new PluginParameter<TrackerSegmenter>("Tracker", TrackerSegmenter.class, true);
    Parameter[] parameters= new Parameter[]{tracker};
    
    public SegmentAndTrack(){}
    
    public SegmentAndTrack(TrackerSegmenter tracker){
        this.tracker.setPlugin(tracker);
    }
    
    public void segmentAndTrack(int structureIdx, List<StructureObject> parentTrack) {
        TrackerSegmenter t = tracker.instanciatePlugin();
        t.segmentAndTrack(structureIdx, parentTrack);
    }

    public void trackOnly(int structureIdx, List<StructureObject> parentTrack) {
        TrackerSegmenter t = tracker.instanciatePlugin();
        t.track(structureIdx, parentTrack);
        /*StructureObject prevParent = parentTrack.get(0);
        StructureObject currentParent;
        for (int i = 1; i<parentTrack.size(); ++i) {
            currentParent = parentTrack.get(i);
            t.assignPrevious(prevParent.getChildren(structureIdx), currentParent.getChildren(structureIdx));
            prevParent = currentParent;
        }*/
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
}
