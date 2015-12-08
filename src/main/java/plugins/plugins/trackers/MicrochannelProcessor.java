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
package plugins.plugins.trackers;

import configuration.parameters.Parameter;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import plugins.Plugin;
import plugins.TrackerSegmenter;
import plugins.plugins.segmenters.MicroChannelFluo2D;
import static plugins.plugins.trackers.ObjectIdxTracker.getComparator;

/**
 *
 * @author jollion
 */
public class MicrochannelProcessor implements TrackerSegmenter {
    MicroChannelFluo2D segmenter = new MicroChannelFluo2D();
    
    public void segmentAndTrack(int structureIdx, List<StructureObject> parentTrack) {
        int refTimePoint = 50;
        StructureObject ref = getRefTimePoint(refTimePoint, parentTrack);
        ObjectPopulation pop = segmenter.runSegmenter(ref.getRawImage(structureIdx), structureIdx, ref);
        ref.setChildren(pop, structureIdx);
        StructureObject prev=null;
        for (StructureObject s : parentTrack) {
            if (s!=ref) s.setChildren(pop, structureIdx);
            if (prev!=null) {
                assignPrevious(prev.getChildObjects(structureIdx), s.getChildObjects(structureIdx));
                prev=s;
            }
        }
    }

    public void assignPrevious(ArrayList<? extends StructureObjectTracker> previous, ArrayList<? extends StructureObjectTracker> next) {
        Collections.sort(previous, getComparator(ObjectIdxTracker.IndexingOrder.XYZ));
        Collections.sort(next, getComparator(ObjectIdxTracker.IndexingOrder.XYZ));
        for (int i = 0; i<Math.min(previous.size(), next.size()); ++i) {
            next.get(i).setPreviousInTrack(previous.get(i), false);
            Plugin.logger.trace("assign previous {} to next {}", previous.get(i), next.get(i));
        }
    }
    
    private static StructureObject getRefTimePoint(int refTimePoint, List<StructureObject> track) {
        if (track.get(0).getTimePoint()>=refTimePoint) return track.get(0);
        else if (track.get(track.size()-1).getTimePoint()<=refTimePoint) return track.get(track.size()-1);
        for (StructureObject t: track) if (t.getTimePoint()==refTimePoint) return t;
        return track.get(0);
    }

    public Parameter[] getParameters() {
        return segmenter.getParameters();
    }
    
}
