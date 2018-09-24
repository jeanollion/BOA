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
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author jollion
 */
public class MergeScenario extends CorrectionScenario {
        final List<Region> listO;
        int idxMin; // for toString method only
        public MergeScenario(BacteriaClosedMicrochannelTrackerLocalCorrections tracker, List<Region> objects, int frame) {
            super(frame, frame, tracker);
            listO = new ArrayList<>(objects); // avoid concurent modifications
            idxMin =  tracker.populations.get(frameMin).indexOf(listO.get(0));
            if (!listO.isEmpty()) {
                this.cost = tracker.segmenters.getAndCreateIfNecessary(frame).computeMergeCost(tracker.getParent(frame), tracker.structureIdx, listO);
            } else cost = Double.POSITIVE_INFINITY;
            if (debugCorr) logger.debug("Merge scenario: tp: {}, idxMin: {}, #objects: {}, cost: {}", frame, getIdxMin(), listO.size(), cost);
        }
        @Override protected MergeScenario getNextScenario() { // @ previous time, until there is one single parent ie no more bacteria to merge
            if (frameMin==0 || listO.isEmpty()) return null;
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
            return new MergeScenario(tracker, tracker.getObjects(frameMin-1).subList(iMin, iMax+1), frameMin-1);
        }
        private int getIdxMin() {
            return tracker.populations.get(frameMin).indexOf(listO.get(0));
        }
        @Override
        protected void applyScenario() {
            Set<Voxel> vox = new HashSet<>();
            for (Region rem : listO) {
                vox.addAll(rem.getVoxels());
                tracker.objectAttributeMap.remove(rem);
            }
            int idxMin = getIdxMin();
            
            Region o = listO.get(0);
            boolean ok = tracker.populations.get(frameMin).removeAll(listO);
            if (idxMin<0 || !ok) {
                logger.error("could not apply merge scenario: objects absent from population{}", this);
                throw new RuntimeException("Merge scenario cannot be applied -> object absent from population");
            }
            Region merged = new Region(vox, idxMin+1, o.is2D(), o.getScaleXY(), o.getScaleZ()); // new hashcode generated
            tracker.populations.get(frameMin).add(idxMin, merged);
            tracker.objectAttributeMap.put(merged, tracker.new TrackAttribute(merged, idxMin, frameMin));
            tracker.resetIndices(frameMin);
        }
        @Override 
        public String toString() {
            int idxM = getIdxMin();
            if (idxM<0) idxM = idxMin;
            return "Merge@"+frameMin+"["+idxM+";"+(idxM+listO.size()-1)+"]/c="+cost;
        }
    }
