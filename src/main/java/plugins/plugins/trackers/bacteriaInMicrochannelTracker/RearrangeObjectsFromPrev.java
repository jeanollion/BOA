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
import java.util.function.BiFunction;
import static plugins.Plugin.logger;
import plugins.plugins.trackers.ObjectIdxTracker;
import static plugins.plugins.trackers.ObjectIdxTracker.getComparatorObject3D;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.debugCorr;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.getObjectSize;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.significativeSIErrorThld;
import plugins.plugins.trackers.bacteriaInMicrochannelTracker.ObjectModifier.Split;
import utils.Pair;

/**
 *
 * @author jollion
 */
public class RearrangeObjectsFromPrev extends ObjectModifier {
    List<Assignement> assignements;
    int idxMin, idxMax;
    
    public RearrangeObjectsFromPrev(BacteriaClosedMicrochannelTrackerLocalCorrections tracker, int frame, int idxMin, int idxMaxIncluded, int idxPrevMin, int idxPrevMaxIncluded) { // idxMax included
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
            assignements.add(new Assignement(tracker.populations[frame-1].get(i), sizeRange));
        }
        // split phase
        Assignement a = needToSplit();
        while(a!=null) {
            if (debugCorr) logger.debug("RO: assignments: {}", assignements);
            if (!a.split()) break;
            a = needToSplit();
        }
        // merge phase: merge until 2 objects per assignment & remove each merge cost to global cost
        if (a==null) {
            for (Assignement ass : assignements) {
                if (ass.objects.size()>2) ass.merge();
            }
        }
        if (debugCorr) logger.debug("Rearrange objects: tp: {}, idx: [{};{}], cost: {}", timePointMax, idxMin, idxMax, cost);
    }

    private int getNextVoidAssignementIndex() {
        for (int i = 0; i<assignements.size(); ++i) if (assignements.get(i).isEmpty()) return i;
        return -1;
    }
    
    protected Assignement getAssignement(Object3D o, boolean prev, boolean reset) {
        if (prev) return assignUntil(reset, (a, i) -> a.prevObject==o ? a : null);
        else return assignUntil(reset, (a, i) -> a.contains(o) ? a : null);
    }
          
    protected Assignement needToSplit() { // assigns from start and check range size
        return assignUntil(true, (a, i) -> a.overSize() ? a : ((a.underSize() && i>0) ? assignements.get(i-1) : null)); // if oversize: return current, if undersize return previous
    }
        
    protected Assignement assignUntil(boolean reset, BiFunction<Assignement, Integer, Assignement> exitFunction) { // assigns from start with custom exit function -> if return non null value -> exit assignment loop with value
        List<Object3D> allObjects = getObjects(timePointMax);
        if (reset) for (int rangeIdx = 0; rangeIdx<assignements.size(); ++rangeIdx) assignements.get(rangeIdx).clear();
        int currentOIdx = 0;
        if (!reset) {
            int idx = getNextVoidAssignementIndex();
            if (idx>0) currentOIdx = allObjects.indexOf(assignements.get(idx-1).getLastObject());
        }
        for (int rangeIdx = 0; rangeIdx<assignements.size(); ++rangeIdx) {
            Assignement cur = assignements.get(rangeIdx);
            if (cur.isEmpty()) {
                while(currentOIdx<allObjects.size() && cur.underSize()) assignements.get(rangeIdx).add(allObjects.get(currentOIdx++));
            }
            Assignement a = exitFunction.apply(cur, rangeIdx);
            if (a!=null) return a;
        }
        return null;
    }

    @Override protected RearrangeObjectsFromPrev getNextScenario() { 
        return null;
    }

    @Override
    protected void applyScenario() {
        for (int i = idxMax; i>=idxMin; --i) {
            tracker.populations[timePointMax].remove(i);
            tracker.trackAttributes[timePointMax].remove(i);
        }
        List<Object3D> allObjects = getObjects(timePointMax);
        Collections.sort(allObjects, getComparatorObject3D(ObjectIdxTracker.IndexingOrder.YXZ)); // sort by increasing Y position
        int idx = idxMin;
        for (Object3D o : allObjects) {
            tracker.populations[timePointMax].add(idx, o);
            tracker.trackAttributes[timePointMax].add(idx, tracker.new TrackAttribute(o, idx, timePointMax));
            idx++;
        }
        tracker.resetIndices(timePointMax);
    }
    
    @Override 
    public String toString() {
        return "Rearrange@"+timePointMax+"["+idxMin+";"+idxMax+"]/c="+cost;
    }
    

    private class Assignement {
        final List<Object3D> objects;
        final Object3D prevObject;
        final double[] sizeRange;
        double size;
        public Assignement(Object3D prevObject, double[] sizeRange) {
            this.prevObject=prevObject;
            this.sizeRange=sizeRange;
            this.objects = new ArrayList<>(3);
        }
        public void add(Object3D o) {
            this.objects.add(o);
            this.size+=getObjectSize(o);
        }
        public boolean isEmpty() {
            return objects.isEmpty();
        }
        public boolean contains(Object3D o) {
            return objects.contains(o);
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
        public Object3D getLastObject() {
            if (objects.isEmpty()) return null;
            return objects.get(objects.size()-1);
        }
        public boolean split() { 
            TreeSet<Split> res = new TreeSet<>();
            for (Object3D o : objects) {
                Split s = getSplit(timePointMax, o);
                if (Double.isFinite(s.cost)) res.add(s);
            }
            if (res.isEmpty()) return false;
            Split s = res.first(); // lowest cost
            List<Object3D> allObjects = getObjects(timePointMax);
            if (debugCorr) logger.debug("RO: split: {}, cost: {}", allObjects.indexOf(s.source)+idxMin, s.cost);
            s.apply();
            cost+=s.cost;
            Collections.sort(allObjects, getComparatorObject3D(ObjectIdxTracker.IndexingOrder.YXZ));
            return true;
        }
        public void merge() {
            Collections.sort(objects, getComparatorObject3D(ObjectIdxTracker.IndexingOrder.YXZ));
            TreeSet<Merge> res = new TreeSet();
            for (int i = 0; i<objects.size()-1; ++i) {
                for (int j = i+1; j<objects.size(); ++j) {
                    Merge m = getMerge(timePointMax, new Pair(objects.get(i), objects.get(j)));
                    if (Double.isFinite(m.cost)) res.add(m);
                }
            }
            while(objects.size()>2) { // critère merge = cout le plus bas. // TODO: inclure les objets suivants
                Merge m = res.first();
                m.apply();
                cost+=m.cost;
            }
        }
        
        @Override public String toString() {
            return "RO:["+tracker.populations[timePointMax-1].indexOf(this.prevObject)+"]->#"+objects.size()+"/size: "+size+"/cost: "+cost+ "/sizeRange: ["+this.sizeRange[0]+";"+this.sizeRange[1]+"]";
        }
        }

}
