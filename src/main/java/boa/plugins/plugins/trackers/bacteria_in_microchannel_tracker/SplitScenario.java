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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static boa.plugins.Plugin.logger;
import boa.plugins.plugins.trackers.ObjectIdxTracker;
import static boa.plugins.plugins.trackers.ObjectIdxTracker.getComparatorRegion;
import boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker.BacteriaClosedMicrochannelTrackerLocalCorrections.Flag;
import boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker.BacteriaClosedMicrochannelTrackerLocalCorrections.TrackAttribute;
import static boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker.BacteriaClosedMicrochannelTrackerLocalCorrections.debugCorr;
import boa.utils.Pair;

/**
 *
 * @author jollion
 */
public class SplitScenario extends CorrectionScenario {
        Region o;
        List<Region> splitObjects;
        int idx;
        public SplitScenario(BacteriaClosedMicrochannelTrackerLocalCorrections tracker, Region o, int frame) {
            super(frame, frame, tracker);
            this.o=o;
            splitObjects= new ArrayList<>();
            cost = tracker.segmenters.getAndCreateIfNecessary(frame).split(tracker.getParent(frame), tracker.structureIdx, o, splitObjects);
            idx = tracker.populations.get(frame).indexOf(o);
            if (idx<0) throw new IllegalArgumentException("Error SplitScenario at frame: "+frame+" object with bounds: "+o.getBounds()+ " not found");
            if (debugCorr) logger.debug("Split scenario: tp: {}, idx: {}, cost: {} # objects: {}", frame, idx, cost, splitObjects.size());
        }
        /*public SplitScenario(BacteriaClosedMicrochannelTrackerLocalCorrections tracker, Region o, int frame, int objectNumber) {
            super(frame, frame, tracker);
            this.o=o;
            splitObjects= new ArrayList<>();
            cost = tracker.segmenters.getAndCreateIfNecessary(frame).split(tracker.getParent(frame), tracker.structureIdx, o, splitObjects);
            if (splitObjects.size()>=2 && Double.isFinite(cost) && !Double.isNaN(cost)) {
                while(splitObjects.size()<objectNumber) {
                    //Collections.sort(splitObjects, (o1, o2) -> Integer.compare(o2.getSize(), o1.getSize())); // biggest object first
                    TreeMap<Pair<Double, Region>, List<Region>> splits = split(splitObjects);
                    if (splits.isEmpty()) break;
                    while(!splits.isEmpty() && splitObjects.size()<objectNumber) {
                        Map.Entry<Pair<Double, Region>, List<Region>> e = splits.pollFirstEntry();
                        splitObjects.remove(e.getKey().value);
                        splitObjects.addAll(e.getValue());
                        cost+=e.getKey().key;
                    }
                }
            }
            if (debugCorr) logger.debug("Split scenario: tp: {}, idx: {}, cost: {} # objects: {}", frame, tracker.populations.get(frame).indexOf(o), cost, splitObjects.size());
        }*/
        
        private TreeMap<Pair<Double, Region>, List<Region>> split(List<Region> objects) {
            Comparator<Pair<Double, Region>> comp = (k1, k2) -> Double.compare(k1.key, k2.key);
            TreeMap<Pair<Double, Region>, List<Region>> res = new TreeMap(comp);
            for (Region oo : objects) {
                List<Region> so= new ArrayList<>();
                double c = tracker.segmenters.getAndCreateIfNecessary(frameMin).split(tracker.getParent(frameMin), tracker.structureIdx, oo, so);
                if (so.size()>=2 && Double.isFinite(c) && !Double.isNaN(c)) res.put(new Pair(c, oo), so);
            }
            return res;
        }
        @Override protected SplitScenario getNextScenario() { // until next division event OR reach end of channel & division with 2n sister lost
            if (frameMin == tracker.maxT-1) return null;
            TrackAttribute ta = tracker.objectAttributeMap.get(o);
            if (ta==null) return null;
            if (ta.next==null) {
                if (debugCorr) logger.debug("getNextScenario: assign @:{}", frameMin+1);
                tracker.setAssignmentToTrackAttributes(frameMin+1, false);
            }
            if (ta.next!=null) {
                if (ta.division || (ta.next.idx==tracker.getObjects(frameMin+1).size()-1 && o.getSize() * tracker.minGR > ta.next.getSize())) return null;
                else return new SplitScenario(tracker, ta.next.o, frameMin+1);
            }
            else return null;
        }

        @Override
        protected void applyScenario() {
            Collections.sort(splitObjects, getComparatorRegion(ObjectIdxTracker.IndexingOrder.YXZ)); // sort by increasing Y position
            int idx = tracker.populations.get(frameMin).indexOf(o);
            if (idx<0) throw new RuntimeException("Error SplitScenario at frame: "+frameMin+" object with bounds: "+o.getBounds()+ " not found");
            tracker.populations.get(frameMin).remove(idx);
            tracker.populations.get(frameMin).addAll(idx, splitObjects);
            tracker.objectAttributeMap.remove(o);
            int curIdx = idx;
            for (Region splitObject : splitObjects) tracker.objectAttributeMap.put(splitObject, tracker.new TrackAttribute(splitObject, curIdx++, frameMin).setFlag(Flag.correctionSplit));
            tracker.resetIndices(frameMin);
        }
        @Override 
        public String toString() {
            return "Split@"+frameMin+"["+idx+"]/c="+cost;
        }
    }
