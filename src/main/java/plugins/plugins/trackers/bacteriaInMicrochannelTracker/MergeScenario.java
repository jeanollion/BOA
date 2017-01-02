/*
 * Copyright (C) 2016 jollion
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
package plugins.plugins.trackers.bacteriaInMicrochannelTracker;

import dataStructure.objects.Object3D;
import dataStructure.objects.Voxel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import static plugins.Plugin.logger;
import plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections;
import plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.TrackAttribute;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.debugCorr;

/**
 *
 * @author jollion
 */
public class MergeScenario extends CorrectionScenario {
        int idxMin, idxMax;
        List<Object3D> listO;
        public MergeScenario(BacteriaClosedMicrochannelTrackerLocalCorrections tracker, int idxMin, List<Object3D> objects, int frame) { // idxMax included
            super(frame, frame, tracker);
            this.idxMax=idxMin+objects.size()-1;
            this.idxMin = idxMin;
            listO = objects;
            if (!listO.isEmpty()) {
                this.cost = tracker.getSegmenter(frame, false).computeMergeCost(tracker.getImage(frame), listO);
            } else cost = Double.POSITIVE_INFINITY;
            if (debugCorr) logger.debug("Merge scenario: tp: {}, idxMin: {}, #objects: {}, cost: {}", frame, idxMin, listO.size(), cost);
        }
        @Override protected MergeScenario getNextScenario() { // @ previous time, until there is one single parent ie no more bacteria to merge
            if (timePointMin==0 || idxMin==idxMax) return null;
            int iMin = Integer.MAX_VALUE;
            int iMax = -1;
            for (Object3D o : listO) {
                TrackAttribute ta = tracker.objectAttributeMap.get(o).prev;
                if (ta==null) continue;
                if (iMin>ta.idx) iMin = ta.idx;
                if (iMax<ta.idx) iMax = ta.idx;
            }
            if (iMin==iMax) return null; // no need to merge
            if (iMin==Integer.MAX_VALUE || iMax==-1) return null; // no previous objects 
            return new MergeScenario(tracker, iMin,tracker.getObjects(timePointMin-1).subList(iMin, iMax+1), timePointMin-1);
        }

        @Override
        protected void applyScenario() {
            List<Voxel> vox = new ArrayList<>();
            Object3D o = tracker.populations[timePointMin].get(idxMin); 
            for (int i = idxMax; i>=idxMin; --i) {
                Object3D rem = tracker.populations[timePointMin].remove(i);
                vox.addAll(rem.getVoxels());
                tracker.objectAttributeMap.remove(rem);
            }
            Object3D merged = new Object3D(vox, idxMin+1, o.getScaleXY(), o.getScaleZ());
            tracker.populations[timePointMin].add(idxMin, merged);
            tracker.objectAttributeMap.put(merged, tracker.new TrackAttribute(merged, idxMin, timePointMin));
            tracker.resetIndices(timePointMin);
        }
        @Override 
        public String toString() {
            return "Merge@"+timePointMin+"["+idxMin+";"+idxMax+"]/c="+cost;
        }
    }
