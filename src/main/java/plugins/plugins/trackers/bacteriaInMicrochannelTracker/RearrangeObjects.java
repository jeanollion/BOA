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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import static plugins.Plugin.logger;
import plugins.plugins.trackers.ObjectIdxTracker;
import static plugins.plugins.trackers.ObjectIdxTracker.getComparatorObject3D;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.debugCorr;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.getObjectSize;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.significativeSIErrorThld;
import plugins.plugins.trackers.bacteriaInMicrochannelTracker.ObjectCorrector.Split;
import utils.Pair;

/**
 *
 * @author jollion
 */
public class RearrangeObjects extends ObjectCorrector {
    List<Assignement> assignements;
    int idxMin, idxMax;
    
    public RearrangeObjects(BacteriaClosedMicrochannelTrackerLocalCorrections tracker, int frame, int idxMin, int idxMaxIncluded, int idxPrevMin, int idxPrevMaxIncluded) { // idxMax included
        super(frame, frame, tracker);
        this.idxMin=idxMin;
        this.idxMax=idxMaxIncluded;
        objects.put(frame, new ArrayList(tracker.populations[frame].subList(idxMin, idxMaxIncluded+1)));
        objects.put(frame-1, new ArrayList(tracker.populations[frame-1].subList(idxPrevMin, idxPrevMaxIncluded+1)));
        assignements = new ArrayList<>(idxPrevMaxIncluded-idxPrevMin+1);
        for (int i = idxPrevMin; i<=idxPrevMaxIncluded; ++i) {
            double[] sizeRange = new double[2];
            double si = tracker.trackAttributes[frame-1].get(i).getLineageSizeIncrement();
            double size = tracker.trackAttributes[frame-1].get(i).getSize();
            if (Double.isNaN(si)) {
                sizeRange[0] = tracker.minGR * size;
                sizeRange[1] = tracker.maxGR * size;
            } else {
                sizeRange[0] = (si-significativeSIErrorThld/2) * size;
                sizeRange[1] = (si+significativeSIErrorThld/2) * size;
            }
            assignements.add(new Assignement(sizeRange));
        }
        // split phase
        Assignement a = needToSplit();
        while(a!=null) {
            logger.debug("assignments: {}", assignements);
            if (!a.split()) break;
            a = needToSplit();
        }
        // merge phase: merge until 2 objects per assignment & remove each merge cost to global cost
        if (a==null) {
            for (Assignement ass : assignements) {
                if (ass.objects.size()>2) ass.merge();
            }
        }
        if (tracker.debugCorr) logger.debug("Rearrange scenario: tp: {}, idx: [{};{}], cost: {}", timePointMin, idxMin, idxMax, cost);
    }

          
    private Assignement needToSplit() { // assigns from start and check range size
        List<Object3D> allObjects = getObjects(timePointMin);
        for (int rangeIdx = 0; rangeIdx<assignements.size(); ++rangeIdx) assignements.get(rangeIdx).clear();
        int currentOIdx = 0;
        for (int rangeIdx = 0; rangeIdx<assignements.size(); ++rangeIdx) {
            Assignement cur = assignements.get(rangeIdx);
            while(currentOIdx<allObjects.size() && cur.underSize()) assignements.get(rangeIdx).add(allObjects.get(currentOIdx++));
            if (cur.overSize()) return cur;
            if (cur.underSize() && rangeIdx>0) return assignements.get(rangeIdx-1); // split in previous
        }
        return null;
    }
        

    @Override protected RearrangeObjects getNextScenario() { 
        return null;
    }

    @Override
    protected void applyScenario() {
        for (int i = idxMax; i>=idxMin; --i) {
            tracker.populations[timePointMin].remove(i);
            tracker.trackAttributes[timePointMin].remove(i);
        }
        List<Object3D> allObjects = getObjects(timePointMin);
        Collections.sort(allObjects, getComparatorObject3D(ObjectIdxTracker.IndexingOrder.YXZ)); // sort by increasing Y position
        int idx = idxMin;
        for (Object3D o : allObjects) {
            tracker.populations[timePointMin].add(idx, o);
            tracker.trackAttributes[timePointMin].add(idx, tracker.new TrackAttribute(o, idx, timePointMin));
            idx++;
        }
        tracker.resetIndices(timePointMin);
    }
    @Override 
    public String toString() {
        return "Readange@"+timePointMin+"["+idxMin+";"+idxMax+"]/c="+cost;
    }
    

    private class Assignement {
        final List<Object3D> objects;
        final double[] sizeRange;
        double size;
        public Assignement(double[] sizeRange) {
            this.sizeRange=sizeRange;
            this.objects = new ArrayList<>(3);
        }
        public void add(Object3D o) {
            this.objects.add(o);
            this.size+=getObjectSize(o);
        }
        public void clear() {
            size=0;
            objects.clear();
        }
        public boolean overSize() {
            return size>sizeRange[1];
        }
        public boolean underSize() {
            return size<sizeRange[0];
        }
        public boolean split() { 
            TreeSet<Split> res = new TreeSet<>();
            for (Object3D o : objects) {
                Split s = getSplit(timePointMin, o);
                if (Double.isFinite(s.cost)) res.add(s);
            }
            if (res.isEmpty()) return false;
            Split s = res.first(); // lowest cost
            List<Object3D> allObjects = getObjects(timePointMin);
            if (debugCorr) logger.debug("rearrange: split: {}, cost: {}", allObjects.indexOf(s.source)+idxMin, s.cost);
            s.apply();
            cost+=s.cost;
            Collections.sort(allObjects, getComparatorObject3D(ObjectIdxTracker.IndexingOrder.YXZ));
            return true;
        }
        public void merge() {
            Collections.sort(objects, getComparatorObject3D(ObjectIdxTracker.IndexingOrder.YXZ));
            TreeMap<Double, List<Object3D>> costMap = new TreeMap();
            for (int i = 0; i<objects.size()-1; ++i) {
                for (int j = i+1; j<objects.size(); ++j) {
                    List<Object3D> l = new ArrayList<>(2);
                    l.add(objects.get(i));
                    l.add(objects.get(j));
                    double c = tracker.getSegmenter(timePointMin).computeMergeCost(getImage(timePointMin), l);
                    if (Double.isFinite(c) && !Double.isNaN(c)) costMap.put(c, l);
                }
            }
            while(objects.size()>2) {
                Map.Entry<Double, List<Object3D>> e = costMap.pollFirstEntry();
                List<Voxel> vox = new ArrayList<>();
                for (Object3D o : e.getValue()) {
                    vox.addAll(o.getVoxels());
                    objects.remove(o);
                    allObjects.remove(o);
                }
                Object3D merged = new Object3D(vox, e.getValue().get(0).getLabel(), e.getValue().get(0).getScaleXY(), e.getValue().get(0).getScaleZ());
                objects.add(merged);
                allObjects.add(merged);
                cost-=e.getKey();
            }
        }
            @Override public String toString() {
                return ""+objects.size();
            }
        }

}
