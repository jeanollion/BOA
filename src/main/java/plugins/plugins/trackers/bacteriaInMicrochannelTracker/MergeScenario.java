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
import java.util.List;
import static plugins.Plugin.logger;
import plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.debugCorr;

/**
 *
 * @author jollion
 */
public class MergeScenario extends CorrectionScenario {
        int idxMin, idxMax;
        ArrayList<Object3D> listO;
        public MergeScenario(BacteriaClosedMicrochannelTrackerLocalCorrections tracker, int idxMin, int idxMaxIncluded, int timePoint) { // idxMax included
            super(timePoint, timePoint, tracker);
            this.idxMax=idxMaxIncluded;
            this.idxMin = idxMin;
            listO = new ArrayList<>(idxMaxIncluded - idxMin +1 );
            for (int i = idxMin; i<=idxMaxIncluded; ++i) {
                BacteriaClosedMicrochannelTrackerLocalCorrections.TrackAttribute ta = tracker.getAttribute(timePoint, i);
                listO.add(ta.o);
            }
            if (!listO.isEmpty()) {
                this.cost = tracker.getSegmenter(timePoint).computeMergeCost(tracker.getImage(timePoint), listO);
                // check for small objects
                /*if (Double.isFinite(cost)) { // could merge
                    int nSmall = 0;
                    for (Object3D o :  listO) if (o.getSize()<tracker.maxFusionSize) ++nSmall;
                    if (nSmall>=listO.size()-1) cost = 0;
                }*/
            } else cost = Double.POSITIVE_INFINITY;
            if (debugCorr) logger.debug("Merge scenario: tp: {}, idxMin: {}, #objects: {}, cost: {}", timePoint, idxMin, listO.size(), cost);
        }
        @Override protected MergeScenario getNextScenario() { // @ previous time, until there is one single parent ie no more bacteria to merge
            if (timePointMin==0 || idxMin==idxMax) return null;
            int iMin = Integer.MAX_VALUE;
            int iMax = -1;
            for (int i = idxMin; i<=idxMax; ++i) { // get all connected trackAttributes from previous timePoint
                BacteriaClosedMicrochannelTrackerLocalCorrections.TrackAttribute ta = tracker.getAttribute(timePointMin, i).prev;
                if (ta==null) continue;
                //if (ta.division) for (TrackAttribute taDiv : ta.getNext()) if (taDiv.idx<idxMin || taDiv.idx>=idxMax) return null; // if division & on of the divided objects is not in the current objects to merge: stop
                if (iMin>ta.idx) iMin = ta.idx;
                if (iMax<ta.idx) iMax = ta.idx;
                if (ta.idx != tracker.trackAttributes[timePointMin-1].indexOf(ta)) logger.error("BCMTLC: inconsistent data: t: {}, expected idx: {}, actual: {}", timePointMin-1, ta.idx, tracker.trackAttributes[timePointMin-1].indexOf(ta));
            }
            if (iMin==iMax) return null; // no need to merge
            if (iMin==Integer.MAX_VALUE || iMax==-1) return null; // no previous objects 
            return new MergeScenario(tracker, iMin,iMax, timePointMin-1);
        }

        @Override
        protected void applyScenario() {
            List<Voxel> vox = new ArrayList<>();
            Object3D o = tracker.populations[timePointMin].get(idxMin); 
            for (int i = idxMax; i>=idxMin; --i) {
                Object3D rem = tracker.populations[timePointMin].remove(i);
                vox.addAll(rem.getVoxels());
                tracker.trackAttributes[timePointMin].remove(i);
            }
            Object3D merged = new Object3D(vox, idxMin+1, o.getScaleXY(), o.getScaleZ());
            tracker.populations[timePointMin].add(idxMin, merged);
            tracker.trackAttributes[timePointMin].add(idxMin, tracker.new TrackAttribute(merged, idxMin, timePointMin));
            tracker.resetIndices(timePointMin);
        }
        @Override 
        public String toString() {
            return "Merge@"+timePointMin+"["+idxMin+";"+idxMax+"]/c="+cost;
        }
    }
