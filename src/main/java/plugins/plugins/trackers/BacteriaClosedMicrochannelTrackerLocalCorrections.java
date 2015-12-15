/*
 * Copyright (C) 2015 jollion
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
package plugins.plugins.trackers;

import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObject.TrackFlag;
import dataStructure.objects.StructureObjectPreProcessing;
import dataStructure.objects.StructureObjectTracker;
import dataStructure.objects.Voxel;
import image.ImageMask;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import measurement.GeometricalMeasurements;
import plugins.Segmenter;
import plugins.SegmenterSplitAndMerge;
import plugins.Tracker;
import plugins.TrackerSegmenter;
import static plugins.plugins.trackers.ObjectIdxTracker.getComparatorObject3D;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class BacteriaClosedMicrochannelTrackerLocalCorrections implements TrackerSegmenter {
    
    // parametrization-related attributes
    protected PluginParameter<SegmenterSplitAndMerge> segmenter = new PluginParameter<SegmenterSplitAndMerge>("Segmentation algorithm", SegmenterSplitAndMerge.class, false);
    BoundedNumberParameter maxGrowthRate = new BoundedNumberParameter("Maximum growth rate", 2, 1.5, 1, 2);
    BoundedNumberParameter minGrowthRate = new BoundedNumberParameter("Minimum growth rate", 2, 1.1, 1, 2);
    BoundedNumberParameter divCriterion = new BoundedNumberParameter("Division Criterion", 2, 0.9, 0.1, 0.99);
    Parameter[] parameters = new Parameter[]{segmenter, maxGrowthRate, divCriterion, minGrowthRate};

    // tracking-related attributes
    private enum Flag {errorType1, errorType2, correctionMerge, correctionSplit;}
    ArrayList<Object3D>[] populations;
    SegmenterSplitAndMerge[] segmenters;
    ArrayList<TrackAttribute>[] trackAttributes;
    List<StructureObject> parents;
    int structureIdx;
    double maxGR, minGR;
    static int loopLimit=100;
    
    public BacteriaClosedMicrochannelTrackerLocalCorrections() {}
    
    public BacteriaClosedMicrochannelTrackerLocalCorrections(SegmenterSplitAndMerge segmenter, double maxGrowthRate, double minGrowthRate) {
        this.segmenter.setPlugin(segmenter);
        this.maxGrowthRate.setValue(maxGrowthRate);
        this.minGrowthRate.setValue(minGrowthRate);
    }
    
    @Override public void assignPrevious(ArrayList<? extends StructureObjectTracker> previous, ArrayList<? extends StructureObjectTracker> next) {        

    }
    
    

    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    
    public void track(int structureIdx, List<StructureObject> parentTrack) {
        init(parentTrack, structureIdx, false);
        segmentAndTrack(false);
    }

    @Override public void segmentAndTrack(int structureIdx, List<StructureObject> parentTrack) {
        init(parentTrack, structureIdx, true);
        segmentAndTrack(true);
    }
    
    public void segmentAndTrack(boolean performCorrection) {
        int currentTimePoint = 1;
        int maxTimePoint = 1;
        int nLoop = 0;
        while(currentTimePoint<populations.length) {
            int corr = assignPrevious(currentTimePoint, performCorrection);
            if (corr==-1) {
                ++currentTimePoint;
                if (maxTimePoint<currentTimePoint) { // when max timePoint in reached -> reset counter
                    maxTimePoint=currentTimePoint;
                    nLoop=0;
                }
            } else {
                ++nLoop;
                if (nLoop>loopLimit) for (currentTimePoint = corr; currentTimePoint<=maxTimePoint; ++currentTimePoint) assignPrevious(currentTimePoint, false); // no more corrections
                else currentTimePoint = corr;
            }
        }
        // apply to structureObject
        ArrayList<StructureObject> childrenPrev = null;
        ArrayList<StructureObject> children = null;
        for (int t = 0; t<populations.length; ++t) {
            StructureObject parent = this.parents.get(t);
            if (segmenters==null) { // modifiy existing structureObjects
                children = parent.getChildren(structureIdx);
                if (children.size()!=populations[t].size()) logger.error("BCMTLC: error @ parent: {}, children and tracker objects differ in number", parent);
                else setAttributes(t, children, childrenPrev);
            } else { // creates new structureObjects
                children = parent.setChildren(new ObjectPopulation(populations[t], null), structureIdx);
                setAttributes(t, children, childrenPrev);
            }
            childrenPrev=children;
        }
    }
    
    private void setAttributes(int timePoint, ArrayList<StructureObject> children, ArrayList<StructureObject> childrenPrev) {
        for (int i = 0; i<children.size(); ++i) {
            TrackAttribute ta= getAttribute(timePoint, i);
            if (ta.prev==null || childrenPrev==null) children.get(i).resetTrackLinks();
            else children.get(i).setPreviousInTrack(childrenPrev.get(ta.prev.idx), ta.trackHead, ta.flag!=null?TrackFlag.trackError:null);
        }
    }
    
    protected void init(List<StructureObject> parentTrack, int structureIdx, boolean segment) {
        int timePointNumber = parents.size();
        trackAttributes = (ArrayList<TrackAttribute>[])new ArrayList[timePointNumber];
        populations = (ArrayList<Object3D>[])new ArrayList[timePointNumber];
        if (segment) segmenters  = new SegmenterSplitAndMerge[timePointNumber];
        this.maxGR=this.maxGrowthRate.getValue().doubleValue();
        this.minGR=this.minGrowthRate.getValue().doubleValue();
    }
    /**
     * 
     * @param timePoint
     * @param performCorrection
     * @return minimal timePoint where correction has been performed, -1 if no correction has been performed
     */
    protected int assignPrevious(int timePoint, boolean performCorrection) {
        TrackAssginer assigner = new TrackAssginer(timePoint);
        assigner.resetTrackAttributes();
        while(assigner.nextTrack()) {
            if (performCorrection && assigner.needCorrection()) return assigner.performCorrection();
            else assigner.assignCurrent();
        }
        return -1;
    }
    
    protected SegmenterSplitAndMerge getSegmenter(int timePoint) {
        if (segmenters==null) return null;
        if (segmenters[timePoint]==null) segmenters[timePoint] = this.segmenter.instanciatePlugin();
        return segmenters[timePoint];
    }
    
    protected ArrayList<Object3D> getObjects(int timePoint) {
        if (this.populations[timePoint]==null) {
            StructureObject parent = this.parents.get(timePoint);
            if (segmenters==null) { // no segmentation, object should be already set as children of their parents
                ArrayList<StructureObject> list = parent.getChildren(structureIdx);
                if (list!=null) {
                    populations[timePoint] = new ArrayList<Object3D>(list.size());
                    for (StructureObject o : list)  populations[timePoint].add(o.getObject());
                } else populations[timePoint] = new ArrayList<Object3D>(0);
            } else populations[timePoint] = getSegmenter(timePoint).runSegmenter(parent.getRawImage(structureIdx), structureIdx, parent).getObjects();
        }
        return populations[timePoint];
    }
    
    protected ArrayList<TrackAttribute> getAttributes(int timePoint) {
        if (this.trackAttributes[timePoint]==null) createAttributes(timePoint);
        return trackAttributes[timePoint];
    }
    
    protected TrackAttribute getAttribute(int timePoint, int idx) {
        if (this.trackAttributes[timePoint]==null) createAttributes(timePoint);
        return trackAttributes[timePoint].get(idx);
    }
    
    protected double getSize(int timePoint, int idx) {
        if (this.trackAttributes[timePoint]==null) createAttributes(timePoint);
        return trackAttributes[timePoint].get(idx).getSize();
    }
    
    protected void createAttributes(int timePoint) {
        List<Object3D> pop = getObjects(timePoint);
        this.trackAttributes[timePoint] = new ArrayList<TrackAttribute>(pop.size());
        for (int i = 0; i<pop.size(); ++i) trackAttributes[timePoint].add(new TrackAttribute(pop.get(i), i));
    }

    protected class TrackAttribute {
        int idx;
        TrackAttribute prev;
        TrackAttribute next;
        Flag flag;
        Object3D o;
        boolean division=false, trackHead=true;
        private double objectSize=-1;
        protected TrackAttribute(Object3D o, int idx) {
            this.o=o;
            this.idx=idx;
        }
        public void resetTrackAttributes() {
            this.prev=null;
            this.next=null;
            this.flag=null;
            this.division=false;
            trackHead=true;
        }
        public double getSize() {
            if (objectSize==-1) this.objectSize=GeometricalMeasurements.getVolume(o);
            return objectSize;
        }
    }
    
    protected class TrackAssginer {
        int timePoint;
        int idxPrev=0, idxPrevEnd=0, idxPrevLim, idx=0, idxEnd=0, idxLim;
        double sizePrev=0, size=0;
        protected TrackAssginer(int timePoint) {
            if (timePoint<=0) throw new IllegalArgumentException("timePoint cannot be <=0");
            this.timePoint=timePoint;
            idxPrevLim = getAttributes(timePoint-1).size();
            idxLim = getAttributes(timePoint).size();

        }
        protected boolean isValid() {
            return size>0 && sizePrev>0;
        }
        /**
         * 
         * @return true if there is at least 1 remaining object @ timePoint & timePoint -1
         */
        public boolean nextTrack() {
            if (idxPrevEnd==idxPrevLim || idxEnd==idxLim) return false;
            idxPrev = idxPrevEnd;
            sizePrev = getSize(timePoint-1, idxPrevEnd++);
            idx = idxEnd;
            size = getSize(timePoint, idxEnd++);
            incrementIfNecessary();
            return true;
        }
        protected void incrementIfNecessary() {
            if (!verifyInequality()) {
                if (size>=sizePrev) incrementPrev();
                else incrementNext();
            }
        }
        protected void incrementPrev() { // maximum index so that prevSize * minGR remain inferior to size
            while(idxPrevEnd<idxPrevLim) {
                double newSizePrev = sizePrev + getSize(timePoint-1, idxPrevEnd);
                if (newSizePrev * minGR <= size) {
                    sizePrev=newSizePrev;
                    ++idxPrevEnd;
                } else return;
            }
        }
        protected void incrementNext() { // minimal index to reach min growth rate 
            while(idxEnd<idxLim) {
                double newSize = size + getSize(timePoint, idxEnd);
                if (sizePrev * minGR > newSize ) {
                    if (sizePrev * maxGR < newSize) return; // can't grow too much
                    size=newSize;
                    ++idxEnd;
                } else return;
            }
        }
        protected boolean verifyInequality() {
            return sizePrev * minGR <= size && size <= sizePrev * maxGR;
        }
        public boolean needCorrection() {
            return (idxPrevEnd-idxPrev)>1;
        }
        public void resetTrackAttributes() {
            for (TrackAttribute ta : getAttributes(timePoint)) ta.resetTrackAttributes();
            for (TrackAttribute ta : getAttributes(timePoint-1)) ta.next=null;
        }
        public void assignCurrent() {
            boolean error = (idxPrevEnd-idxPrev)>1 || (idxEnd-idx)>2;
            TrackAttribute taCur = getAttribute(timePoint, idx);
            TrackAttribute taPrev = getAttribute(timePoint-1, idx);
            // assignments @ timePoint
            taCur.prev=taPrev;
            taCur.trackHead=false;
            if (error) taCur.flag=Flag.errorType1;
            if ((idxEnd-idx)>1) {
                taPrev.division=true;
                for (int i = idx+1; i<idxEnd; ++i) {
                    getAttribute(timePoint, i).prev=taPrev;
                    if (error) getAttribute(timePoint, i).flag=Flag.errorType1;
                }
            }
            // assignments @ timePoint-1
            taPrev.next=taCur;
            if (error) taPrev.flag=Flag.errorType1;
            if ((idxPrevEnd-idxPrev)>1) {
                for (int i = idxPrev; i<idxPrevEnd; ++i) {
                    getAttribute(timePoint-1, i).next=taCur;
                    getAttribute(timePoint-1, i).flag=Flag.errorType1;
                }
            }
        }
        
        /**
         * 
         * @return minimal timePoint where correction has been performed
         */
        public int performCorrection() {
            ArrayList<CorrectionScenario> merge = new MergeScenario(idxPrev, idxPrevEnd, timePoint-1).getWholeScenario(-1);
            double mergeCost = 0; for (CorrectionScenario c : merge) mergeCost+=c.cost;
            ArrayList<CorrectionScenario> split = new SplitScenario(getAttribute(timePoint, idx), timePoint).getWholeScenario(-1, mergeCost);
            double splitCost = 0; for (CorrectionScenario c : split) splitCost+=c.cost;
            if (mergeCost>splitCost) {
                for (CorrectionScenario c : split) c.applyScenario();
                return timePoint;
            }
            else {
                for (CorrectionScenario c : merge) c.applyScenario();
                return timePoint-merge.size();
            }
        }
    }
    
    protected abstract class CorrectionScenario {
        double cost;
        protected abstract CorrectionScenario getNextScenario();
        /**
         * 
         * @param lengthLimit if >0 limits the length of the scenario
         * @param costLimit if 1 double, limit the cumulative cost of the scenario. if 0 -> no limit
         * @return 
         */
        public ArrayList<CorrectionScenario> getWholeScenario(int lengthLimit, double... costLimit) {
            ArrayList<CorrectionScenario> res = new ArrayList<CorrectionScenario>();
            CorrectionScenario cur = this;
            double sum = 0;
            while(cur!=null && !Double.isNaN(cur.cost)) {
                res.add(cur);
                sum+=cur.cost;
                if (costLimit.length>0 && sum>=costLimit[0]) return res; 
                if (lengthLimit>0 && res.size()>=lengthLimit) return res;
                cur = cur.getNextScenario();
            }
            return res;
        }
        protected abstract void applyScenario();
    }
    protected class MergeScenario extends CorrectionScenario {
        int idxMin, idxMax, timePoint;
        ArrayList<Object3D> listO;
        public MergeScenario(int idxMin, int idxMax, int timePoint) { // idxMax excluded
            this.idxMax=idxMax;
            this.idxMin = idxMin;
            this.timePoint=timePoint;
            listO = new ArrayList<Object3D>(idxMax - idxMin);
            for (int i = idxMin; i<idxMax; ++i) {
                TrackAttribute ta = getAttribute(timePoint, i);
                listO.add(ta.o);
            }
            this.cost = getSegmenter(timePoint).computeMergeCost(listO);
        }
        @Override protected MergeScenario getNextScenario() { // @ previous time, until there is one single parent ie no more bacteria to merge
            if (timePoint==0) return null;
            int iMin = Integer.MAX_VALUE;
            int iMax = Integer.MIN_VALUE;
            for (int i = idxMin; i<idxMax; ++i) { // get all connected trackAttributes from previous timePoint
                TrackAttribute ta = getAttribute(timePoint, i).prev;
                if (ta==null) continue;
                if (iMin>ta.idx) iMin = ta.idx;
                if (iMax<ta.idx) iMax = ta.idx;
            }
            if (iMin==iMax) return null; // no need to merge
            return new MergeScenario(iMin,iMax+1, timePoint-1);
        }

        @Override
        protected void applyScenario() {
            Object3D o = getAttribute(timePoint, idxMin).o;
            for (int i = idxMax-1; i>idxMin; --i) {
                o.addVoxels(getAttribute(timePoint, idxMin).o.getVoxels());
                populations[timePoint].remove(i);
                trackAttributes[timePoint].remove(i);
            }
        }
    }
    
    protected class SplitScenario extends CorrectionScenario {
        TrackAttribute o;
        int timePoint;
        List<Object3D> splitObjects;
        public SplitScenario(TrackAttribute o, int timePoint) {
            this.o=o;
            this.timePoint=timePoint;
            splitObjects= new ArrayList<Object3D>();
            cost = getSegmenter(timePoint).split(o.o, splitObjects);
        }
        @Override protected SplitScenario getNextScenario() { // until next division event
            if (timePoint == populations.length-1 || o.division) return null;
            if (o.next==null) assignPrevious(timePoint+1, false);
            if (o.next!=null) return new SplitScenario(o.next, timePoint+1);
            else return null;
        }

        @Override
        protected void applyScenario() {
            Collections.sort(splitObjects, getComparatorObject3D(ObjectIdxTracker.IndexingOrder.YXZ)); // sort by increasing Y position
            populations[timePoint].remove(o.idx);
            populations[timePoint].addAll(o.idx, splitObjects);
            trackAttributes[timePoint].remove(o.idx);
            int curIdx = o.idx;
            for (Object3D splitObject : splitObjects) {
                trackAttributes[timePoint].add(curIdx, new TrackAttribute(splitObject, curIdx));
                ++curIdx;
            }
        }
    }
}
