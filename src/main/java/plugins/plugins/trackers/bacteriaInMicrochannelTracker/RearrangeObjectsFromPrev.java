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
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.verboseLevelLimit;
import plugins.plugins.trackers.bacteriaInMicrochannelTracker.ObjectModifier.Split;
import utils.HashMapGetCreate;
import utils.Pair;

/**
 *
 * @author jollion
 */
public class RearrangeObjectsFromPrev extends ObjectModifier {
    protected List<RearrangeAssignment> assignements;
    protected final Assignment assignment;
    protected HashMapGetCreate<Object3D, Double> sizeMap = new HashMapGetCreate<>(o -> getObjectSize(o));
    public RearrangeObjectsFromPrev(BacteriaClosedMicrochannelTrackerLocalCorrections tracker, int frame, Assignment assignment) { // idxMax included
        super(frame, frame, tracker);
        objects.put(frame, new ArrayList(assignment.nextObjects)); // new arraylist -> can be modified
        objects.put(frame-1, assignment.prevObjects);
        assignements = new ArrayList<>(assignment.sizePrev());
        this.assignment=assignment;
        for (Object3D o : assignment.prevObjects) {
            double[] sizeRange = new double[2];
            double si = tracker.sizeIncrementFunction.apply(o);
            double size = tracker.sizeFunction.apply(o);
            if (Double.isNaN(si)) {
                sizeRange[0] = tracker.minGR * size;
                sizeRange[1] = tracker.maxGR * size;
            } else {
                sizeRange[0] = (si-significativeSIErrorThld/2) * size;
                sizeRange[1] = (si+significativeSIErrorThld/2) * size;
            }
            assignements.add(new RearrangeAssignment(o, sizeRange));
        }
        // TODO: take into acount endo-of-channel
        // split phase
        RearrangeAssignment a = needToSplit();
        while(a!=null) {
            if (debugCorr) logger.debug("RO: assignments: {}", assignements);
            if (!a.split()) break;
            a = needToSplit();
        }
        // merge phase: merge until 2 objects per assignment & remove each merge cost to global cost
        if (a==null) { 
            if (frame+1<tracker.maxT) {
                TrackAssigner ta = tracker.getTrackAssigner(frame+1).setVerboseLevel(verboseLevelLimit);
                ta.assignUntil(assignment.nextObjects.get(assignment.nextObjects.size()-1), true);
                Set<Object3D> nextObjects = new HashSet<>();
                find next objects -> si correspond exactement au cadre -> utiliser pour merge
                ta.sizeIncrementFunction = o -> { // return size increment of previous object
                    RearrangeAssignment ra = getAssignement(o, false, false);
                    if (ra==null) return Double.NaN;
                    else return tracker.sizeIncrementFunction.apply(ra.prevObject);
                };
                if (ta.assignUntil(assignment.nextObjects.get(assignment.nextObjects.size()-1), true)) {
                    for (RearrangeAssignment ass : assignements) ass.mergeUsingNext(ta);
                }
            } 
            for (RearrangeAssignment ass : assignements) if (ass.objects.size()>2) ass.mergeUntil(2);
        }
        if (debugCorr) logger.debug("Rearrange objects: tp: {}, {}, cost: {}", timePointMax, assignment.toString(false), cost);
    }
    

    private int getNextVoidAssignementIndex() {
        for (int i = 0; i<assignements.size(); ++i) if (assignements.get(i).isEmpty()) return i;
        return -1;
    }
    
    protected RearrangeAssignment getAssignement(Object3D o, boolean prev, boolean reset) {
        if (prev) return assignUntil(reset, (a, i) -> a.prevObject==o ? a : null);
        else return assignUntil(reset, (a, i) -> a.contains(o) ? a : null);
    }
          
    protected RearrangeAssignment needToSplit() { // assigns from start and check range size
        return assignUntil(true, (a, i) -> a.overSize() ? a : ((a.underSize() && i>0) ? assignements.get(i-1) : null)); // if oversize: return current, if undersize return previous
    }
        
    protected RearrangeAssignment assignUntil(boolean reset, BiFunction<RearrangeAssignment, Integer, RearrangeAssignment> exitFunction) { // assigns from start with custom exit function -> if return non null value -> exit assignment loop with value
        List<Object3D> allObjects = getObjects(timePointMax);
        if (reset) for (int rangeIdx = 0; rangeIdx<assignements.size(); ++rangeIdx) assignements.get(rangeIdx).clear();
        int currentOIdx = 0;
        if (!reset) {
            int idx = getNextVoidAssignementIndex();
            if (idx>0) currentOIdx = allObjects.indexOf(assignements.get(idx-1).getLastObject());
        }
        for (int rangeIdx = 0; rangeIdx<assignements.size(); ++rangeIdx) {
            RearrangeAssignment cur = assignements.get(rangeIdx);
            if (cur.isEmpty()) {
                while(currentOIdx<allObjects.size() && cur.underSize()) assignements.get(rangeIdx).add(allObjects.get(currentOIdx++));
            }
            RearrangeAssignment a = exitFunction.apply(cur, rangeIdx);
            if (a!=null) return a;
        }
        return null;
    }

    @Override protected RearrangeObjectsFromPrev getNextScenario() { 
        return null;
    }

    @Override
    protected void applyScenario() {
        for (int i = this.assignment.idxNextEnd()-1; i>=assignment.idxNext; --i) tracker.objectAttributeMap.remove(tracker.populations[timePointMax].remove(i));
        List<Object3D> allObjects = getObjects(timePointMax);
        Collections.sort(allObjects, getComparatorObject3D(ObjectIdxTracker.IndexingOrder.YXZ)); // sort by increasing Y position
        int idx = assignment.idxNext;
        for (Object3D o : allObjects) {
            tracker.populations[timePointMax].add(idx, o);
            tracker.objectAttributeMap.put(o, tracker.new TrackAttribute(o, idx, timePointMax));
            idx++;
        }
        tracker.resetIndices(timePointMax);
    }
    
    @Override 
    public String toString() {
        return "Rearrange@"+timePointMax+"["+this.assignment.idxNext+";"+(assignment.idxNextEnd()-1)+"]/c="+cost;
    }
    
    protected class RearrangeAssignment {
        final List<Object3D> objects;
        final Object3D prevObject;
        final double[] sizeRange;
        double size;
        public RearrangeAssignment(Object3D prevObject, double[] sizeRange) {
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
            if (debugCorr) logger.debug("RO: split: {}, cost: {}", allObjects.indexOf(s.source)+assignment.idxNext, s.cost);
            s.apply(objects);
            s.apply(getObjects(s.frame));
            cost+=s.cost;
            Collections.sort(allObjects, getComparatorObject3D(ObjectIdxTracker.IndexingOrder.YXZ));
            return true;
        }
        public void mergeUsingNext(TrackAssigner assignments) {
            if (debugCorr) logger.debug("RO: merge using next assignments: {}", assignments);
            if (objects.size()<=1) return;
            Iterator<Object3D> it = objects.iterator();
            Object3D lastO = it.next();
            Assignment lastAss = assignments.getAssignmentContaining(lastO, true);
            while(it.hasNext()) {
                Object3D currentO = it.next();
                Assignment ass = assignments.getAssignmentContaining(currentO, true);
                if (ass!=null && ass == lastAss && ass.sizeNext()==1) {
                    Merge m = getMerge(timePointMax, new Pair(lastO, currentO));
                    if (Double.isFinite(m.cost)) {
                        if (debugCorr) logger.debug("RO: merge using next: {}", ass);
                        m.apply(objects);
                        m.apply(getObjects(m.frame));
                        m.apply(ass.prevObjects);
                        currentO = m.value;
                    }
                }
                lastO = currentO;
                lastAss = ass;
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
