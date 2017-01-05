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
        Object3D o;
        List<Object3D> splitObjects;
        public SplitScenario(BacteriaClosedMicrochannelTrackerLocalCorrections tracker, Object3D o, int frame) {
            super(frame, frame, tracker);
            this.o=o;
            splitObjects= new ArrayList<>();
            cost = tracker.getSegmenter(frame, false).split(tracker.getImage(frame), o, splitObjects);
            if (debugCorr) logger.debug("Split scenario: tp: {}, idx: {}, cost: {} # objects: {}", frame, tracker.populations[frame].indexOf(o), cost, splitObjects.size());
        }
        public SplitScenario(BacteriaClosedMicrochannelTrackerLocalCorrections tracker, Object3D o, int frame, int objectNumber) {
            super(frame, frame, tracker);
            this.o=o;
            splitObjects= new ArrayList<>();
            cost = tracker.getSegmenter(frame, false).split(tracker.getImage(frame), o, splitObjects);
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
            if (debugCorr) logger.debug("Split scenario: tp: {}, idx: {}, cost: {} # objects: {}", frame, tracker.populations[frame].indexOf(o), cost, splitObjects.size());
        }
        
        private TreeMap<Pair<Double, Object3D>, List<Object3D>> split(List<Object3D> objects) {
            Comparator<Pair<Double, Object3D>> comp = (k1, k2) -> Double.compare(k1.key, k2.key);
            TreeMap<Pair<Double, Object3D>, List<Object3D>> res = new TreeMap(comp);
            for (Object3D oo : objects) {
                List<Object3D> so= new ArrayList<>();
                double c = tracker.getSegmenter(timePointMin, false).split(tracker.getImage(timePointMin), oo, so);
                if (so.size()>=2 && Double.isFinite(c) && !Double.isNaN(c)) res.put(new Pair(c, oo), so);
            }
            return res;
        }
        @Override protected SplitScenario getNextScenario() { // until next division event OR reach end of channel & division with 2n sister lost
            if (timePointMin == tracker.maxT-1) return null;
            TrackAttribute ta = tracker.objectAttributeMap.get(o);
            if (ta==null) return null;
            if (ta.next==null) {
                if (debugCorr) logger.debug("getNextScenario: assign @:{}", timePointMin+1);
                tracker.setAssignmentToTrackAttributes(timePointMin+1, false);
            }
            if (ta.next!=null) {
                if (ta.division || (ta.next.idx==tracker.getObjects(timePointMin+1).size()-1 && o.getSize() * tracker.minGR > ta.next.getSize())) return null;
                else return new SplitScenario(tracker, ta.next.o, timePointMin+1);
            }
            else return null;
        }

        @Override
        protected void applyScenario() {
            Collections.sort(splitObjects, getComparatorObject3D(ObjectIdxTracker.IndexingOrder.YXZ)); // sort by increasing Y position
            int idx = tracker.populations[timePointMin].indexOf(o);
            tracker.populations[timePointMin].remove(idx);
            tracker.populations[timePointMin].addAll(idx, splitObjects);
            tracker.objectAttributeMap.remove(o);
            int curIdx = idx;
            for (Object3D splitObject : splitObjects) tracker.objectAttributeMap.put(splitObject, tracker.new TrackAttribute(splitObject, curIdx++, timePointMin).setFlag(Flag.correctionSplit));
            tracker.resetIndices(timePointMin);
        }
        @Override 
        public String toString() {
            return "Split@"+timePointMin+"["+tracker.populations[timePointMin].indexOf(o)+"]/c="+cost;
        }
    }
