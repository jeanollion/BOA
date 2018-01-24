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
package boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker;

import boa.data_structure.Region;
import boa.data_structure.Voxel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import static boa.plugins.Plugin.logger;
import boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker.BacteriaClosedMicrochannelTrackerLocalCorrections;
import boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker.BacteriaClosedMicrochannelTrackerLocalCorrections.TrackAttribute;
import static boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker.BacteriaClosedMicrochannelTrackerLocalCorrections.debugCorr;

/**
 *
 * @author jollion
 */
public class MergeScenario extends CorrectionScenario {
        int idxMin, idxMax;
        List<Region> listO;
        public MergeScenario(BacteriaClosedMicrochannelTrackerLocalCorrections tracker, int idxMin, List<Region> objects, int frame) { // idxMax included
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
            for (Region o : listO) {
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
            Region o = tracker.populations.get(timePointMin).get(idxMin); 
            for (int i = idxMax; i>=idxMin; --i) {
                Region rem = tracker.populations.get(timePointMin).remove(i);
                vox.addAll(rem.getVoxels());
                tracker.objectAttributeMap.remove(rem);
            }
            Region merged = new Region(vox, idxMin+1, o.is2D(), o.getScaleXY(), o.getScaleZ());
            tracker.populations.get(timePointMin).add(idxMin, merged);
            tracker.objectAttributeMap.put(merged, tracker.new TrackAttribute(merged, idxMin, timePointMin));
            tracker.resetIndices(timePointMin);
        }
        @Override 
        public String toString() {
            return "Merge@"+timePointMin+"["+idxMin+";"+idxMax+"]/c="+cost;
        }
    }
