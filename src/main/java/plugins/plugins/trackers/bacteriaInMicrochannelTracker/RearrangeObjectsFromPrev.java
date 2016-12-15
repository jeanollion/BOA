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
import java.util.Iterator;
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
import utils.HashMapGetCreate;
import utils.Pair;

/**
 *
 * @author jollion
 */
public class RearrangeObjectsFromPrev extends ObjectModifier {
    protected List<Assignement> assignements;
    protected int idxMin, idxMax;
    protected HashMapGetCreate<Object3D, Double> sizeMap = new HashMapGetCreate<>(o -> getObjectSize(o));
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
        // TODO: take into acount endo-of-channel
        // split phase
        Assignement a = needToSplit();
        while(a!=null) {
            if (debugCorr) logger.debug("RO: assignments: {}", assignements);
            if (!a.split()) break;
            a = needToSplit();
        }
        // merge phase: merge until 2 objects per assignment & remove each merge cost to global cost
        if (a==null) { 
            if (frame+1<tracker.maxT) {
                List<PrevToNextObjects> ptn = assignLists(objects.get(frame), tracker.getObjects(frame+1));
                for (Assignement ass : assignements) ass.mergeUsingNext(ptn);
            } else for (Assignement ass : assignements) if (ass.objects.size()>2) ass.mergeUntil(2);
        }
        if (debugCorr) logger.debug("Rearrange objects: tp: {}, idx: [{};{}], cost: {}", timePointMax, idxMin, idxMax, cost);
    }
    
    
    
    protected RearrangeObjectsFromPrev(BacteriaClosedMicrochannelTrackerLocalCorrections tracker, int frame, int idxMin, int idxMaxIncluded) {
        super(frame, frame, tracker);
        this.idxMin=idxMin;
        this.idxMax=idxMaxIncluded;
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
    
    public static PrevToNextObjects searchInPrev(Object3D o, List<PrevToNextObjects> assignments) {
        for (PrevToNextObjects ptn : assignments) if (ptn.prev.contains(o)) return ptn;
        return null;
    }
    public static class PrevToNextObjects {
        List<Object3D> prev;
        List<Object3D> next;
        boolean overGrowing, underGrowing;
        public PrevToNextObjects(List<Object3D> prev, List<Object3D> next, boolean overGrowing, boolean underGrowing) {
            this.overGrowing=overGrowing;
            this.underGrowing=underGrowing;
            this.prev = prev;
            this.next = next;
        }
    }
    protected List<PrevToNextObjects> assignLists(List<Object3D> prev, List<Object3D> next) { // [0] -> stop idx excluded, [2] -> 0 verifie inegalité, sinon non
        List<PrevToNextObjects> res=  new ArrayList();
        int currentNext = 0;
        int currentPrev = 0;
        int lastNext = 0;
        int lastPrev = 0;
        double sizePrev = sizeMap.getAndCreateIfNecessary(prev.get(currentPrev++));
        double sizeNext = sizeMap.getAndCreateIfNecessary(next.get(currentNext++));
        do {
            while(sizePrev * tracker.minGR > sizeNext || sizeNext > sizePrev * tracker.maxGR) {
                if (sizePrev * tracker.maxGR < sizeNext) {
                    if (currentPrev<prev.size()) {
                        sizePrev += sizeMap.getAndCreateIfNecessary(prev.get(currentPrev++));
                    } else {
                        res.add(new PrevToNextObjects(prev.subList(lastPrev, currentPrev), next.subList(lastNext, currentNext), true, false));
                        return res;
                    }
                } else if (sizePrev * tracker.minGR > sizeNext) {
                    if (currentNext<next.size()) {
                        sizeNext += sizeMap.getAndCreateIfNecessary(next.get(currentNext++));
                    } else {
                        res.add(new PrevToNextObjects(prev.subList(lastPrev, currentPrev), next.subList(lastNext, currentNext), false, true));
                        return res;
                    }
                }
            }
            res.add(new PrevToNextObjects(prev.subList(lastPrev, currentPrev), next.subList(lastNext, currentNext), false, false));
            lastNext = currentNext;
            lastPrev = currentPrev;
            if (currentNext<next.size() && currentPrev<prev.size()) {
                sizePrev = sizeMap.getAndCreateIfNecessary(prev.get(currentPrev++));
                sizeNext = sizeMap.getAndCreateIfNecessary(next.get(currentNext++));
            } else return res;
            logger.debug("lastPrev: {}/{}, lastNext: {}/{}, ass:{}", lastPrev, prev.size(), lastNext, next.size(), res.size());
        } while (true);
    }
    
    
    @Override 
    public String toString() {
        return "Rearrange@"+timePointMax+"["+idxMin+";"+idxMax+"]/c="+cost;
    }
    
    protected class Assignement {
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
            this.size+=sizeMap.getAndCreateIfNecessary(o);
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
            s.apply(objects);
            s.apply(getObjects(s.frame));
            cost+=s.cost;
            Collections.sort(allObjects, getComparatorObject3D(ObjectIdxTracker.IndexingOrder.YXZ));
            return true;
        }
        public void mergeUsingNext(List<PrevToNextObjects> assignments) {
            if (objects.size()<=1) return;
            Iterator<Object3D> it = objects.iterator();
            Object3D lastO = it.next();
            PrevToNextObjects lastPTN = searchInPrev(lastO, assignments);
            while(it.hasNext()) {
                Object3D currentO = it.next();
                PrevToNextObjects p = searchInPrev(currentO, assignments);
                if (p == lastPTN && p.next.size()==1) {
                    Merge m = getMerge(timePointMax, new Pair(lastO, currentO));
                    if (Double.isFinite(m.cost)) {
                        m.apply(objects);
                        m.apply(getObjects(m.frame));
                        p.prev.set(p.prev.indexOf(lastO), m.value);
                        p.prev.remove(currentO);
                        currentO = m.value;
                    }
                }
                lastO = currentO;
                lastPTN = p;
            }
        }
        public void mergeUntil(int limit) {
            double additionalCost = Double.NEGATIVE_INFINITY;
            while(objects.size()>limit) { // critère merge = cout le plus bas. // TODO: inclure les objets du temps suivants dans les contraintes
                Merge m = getBestMerge();
                if (m!=null) {
                    m.apply(objects);
                    m.apply(getObjects(m.frame));
                    if (m.cost>additionalCost) additionalCost=m.cost;
                } else break;
            }
            if (Double.isFinite(additionalCost)) cost+=additionalCost;
        }
        private Merge getBestMerge() {
            Collections.sort(objects, getComparatorObject3D(ObjectIdxTracker.IndexingOrder.YXZ));
            TreeSet<Merge> res = new TreeSet();
            Iterator<Object3D> it = objects.iterator();
            Object3D lastO = it.next();
            while (it.hasNext()) {
                Object3D currentO = it.next();
                Merge m = getMerge(timePointMax, new Pair(lastO, currentO));
                if (Double.isFinite(m.cost)) res.add(m);
                lastO = currentO;
            }
            if (res.isEmpty()) return null;
            else return res.first();
        }
        
        @Override public String toString() {
            return "RO:["+tracker.populations[timePointMax-1].indexOf(this.prevObject)+"]->#"+objects.size()+"/size: "+size+"/cost: "+cost+ "/sizeRange: ["+this.sizeRange[0]+";"+this.sizeRange[1]+"]";
        }
        
        
    }

}
