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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static plugins.Plugin.logger;
import plugins.plugins.trackers.ObjectIdxTracker;
import static plugins.plugins.trackers.ObjectIdxTracker.getComparatorObject3D;
import plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.Flag;
import plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.TrackAttribute;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.debugCorr;
import utils.Pair;

/**
 *
 * @author jollion
 */
public class SplitScenario extends CorrectionScenario {
        BacteriaClosedMicrochannelTrackerLocalCorrections.TrackAttribute o;
        List<Object3D> splitObjects;
        public SplitScenario(BacteriaClosedMicrochannelTrackerLocalCorrections tracker, TrackAttribute o, int timePoint) {
            super(timePoint, timePoint, tracker);
            this.o=o;
            splitObjects= new ArrayList<>();
            cost = tracker.getSegmenter(timePoint).split(tracker.getImage(timePoint), o.o, splitObjects);
            if (debugCorr) logger.debug("Split scenario: tp: {}, idx: {}, cost: {} # objects: {}", timePoint, o.idx, cost, splitObjects.size());
        }
        public SplitScenario(BacteriaClosedMicrochannelTrackerLocalCorrections tracker, TrackAttribute o, int timePoint, int objectNumber) {
            super(timePoint, timePoint, tracker);
            this.o=o;
            splitObjects= new ArrayList<>();
            cost = tracker.getSegmenter(timePoint).split(tracker.getImage(timePoint), o.o, splitObjects);
            if (splitObjects.size()>=2 && Double.isFinite(cost) && !Double.isNaN(cost)) {
                while(splitObjects.size()<objectNumber) {
                    //Collections.sort(splitObjects, (o1, o2) -> Integer.compare(o2.getSize(), o1.getSize())); // biggest object first
                    TreeMap<Pair<Double, Object3D>, List<Object3D>> splits = split(splitObjects);
                    if (splits.isEmpty()) break;
                    while(!splits.isEmpty() && splitObjects.size()<objectNumber) {
                        Map.Entry<Pair<Double, Object3D>, List<Object3D>> e = splits.pollFirstEntry();
                        splitObjects.remove(e.getKey().value);
                        splitObjects.addAll(e.getValue());
                        cost+=e.getKey().key;
                    }
                }
            }
            if (debugCorr) logger.debug("Split scenario: tp: {}, idx: {}, cost: {} # objects: {}", timePoint, o.idx, cost, splitObjects.size());
        }
        
        private TreeMap<Pair<Double, Object3D>, List<Object3D>> split(List<Object3D> objects) {
            Comparator<Pair<Double, Object3D>> comp = (k1, k2) -> Double.compare(k1.key, k2.key);
            TreeMap<Pair<Double, Object3D>, List<Object3D>> res = new TreeMap(comp);
            for (Object3D oo : objects) {
                List<Object3D> so= new ArrayList<>();
                double c = tracker.getSegmenter(timePointMin).split(tracker.getImage(timePointMin), oo, so);
                if (so.size()>=2 && Double.isFinite(c) && !Double.isNaN(c)) res.put(new Pair(c, oo), so);
            }
            return res;
        }
        @Override protected SplitScenario getNextScenario() { // until next division event OR reach end of channel & division with 2n sister lost
            if (timePointMin == tracker.populations.length-1) return null;
            if (o.next==null) {
                if (debugCorr) logger.debug("getNextScenario: assign @:{}", timePointMin+1);
                tracker.assignPrevious(timePointMin+1, false, false);
            }
            if (o.next!=null) {
                if (o.division || (o.next.idx==tracker.getObjects(timePointMin+1).size()-1 && o.getSize() * tracker.minGR > o.next.getSize())) return null;
                else return new SplitScenario(tracker, o.next, timePointMin+1);
            }
            else return null;
        }

        @Override
        protected void applyScenario() {
            Collections.sort(splitObjects, getComparatorObject3D(ObjectIdxTracker.IndexingOrder.YXZ)); // sort by increasing Y position
            tracker.populations[timePointMin].remove(o.idx);
            tracker.populations[timePointMin].addAll(o.idx, splitObjects);
            tracker.trackAttributes[timePointMin].remove(o.idx);
            int curIdx = o.idx;
            for (Object3D splitObject : splitObjects) {
                tracker.trackAttributes[timePointMin].add(curIdx, tracker.new TrackAttribute(splitObject, curIdx, timePointMin).setFlag(Flag.correctionSplit));
                ++curIdx;
            }
            tracker.resetIndices(timePointMin);
        }
        @Override 
        public String toString() {
            return "Split@"+timePointMin+"["+o.idx+"]/c="+cost;
        }
    }
