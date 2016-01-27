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
import configuration.parameters.NumberParameter;
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
import static plugins.Plugin.logger;
import plugins.Segmenter;
import plugins.SegmenterSplitAndMerge;
import plugins.Tracker;
import plugins.TrackerSegmenter;
import plugins.plugins.segmenters.BacteriaFluo;
import static plugins.plugins.trackers.ObjectIdxTracker.getComparatorObject3D;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class BacteriaClosedMicrochannelTrackerLocalCorrections implements TrackerSegmenter {
    
    // parametrization-related attributes
    protected PluginParameter<SegmenterSplitAndMerge> segmenter = new PluginParameter<SegmenterSplitAndMerge>("Segmentation algorithm", SegmenterSplitAndMerge.class, new BacteriaFluo(), false);
    BoundedNumberParameter maxGrowthRate = new BoundedNumberParameter("Maximum growth rate", 2, 1.7, 1, 2);
    BoundedNumberParameter minGrowthRate = new BoundedNumberParameter("Minimum growth rate", 2, 1.1, 1, 2);
    BoundedNumberParameter divisionCriterion = new BoundedNumberParameter("Division Criterio", 2, 0.9, 0.5, 1.1);
    BoundedNumberParameter costLimit = new BoundedNumberParameter("Correction: operation cost limit", 3, 1, 0, null);
    BoundedNumberParameter cumCostLimit = new BoundedNumberParameter("Correction: cumulative cost limit", 3, 5, 0, null);
    Parameter[] parameters = new Parameter[]{segmenter, maxGrowthRate, minGrowthRate, divisionCriterion, costLimit, cumCostLimit};

    // tracking-related attributes
    private enum Flag {error, correctionMerge, correctionSplit;}
    ArrayList<Object3D>[] populations;
    SegmenterSplitAndMerge[] segmenters;
    ArrayList<TrackAttribute>[] trackAttributes;
    List<StructureObject> parents;
    int structureIdx;
    double maxGR, minGR, div, costLim, cumCostLim;
    static int loopLimit=10;
    public static boolean debug=false;
    public BacteriaClosedMicrochannelTrackerLocalCorrections() {}
    
    public BacteriaClosedMicrochannelTrackerLocalCorrections(SegmenterSplitAndMerge segmenter, double divisionCriterion, double minGrowthRate, double maxGrowthRate, double costLimit, double cumulativeCostLimit) {
        this.segmenter.setPlugin(segmenter);
        this.maxGrowthRate.setValue(maxGrowthRate);
        this.minGrowthRate.setValue(minGrowthRate);
        this.divisionCriterion.setValue(divisionCriterion);
        this.costLimit.setValue(costLimit);
        this.cumCostLimit.setValue(cumulativeCostLimit);
    }
    
    @Override public void assignPrevious(ArrayList<? extends StructureObjectTracker> previous, ArrayList<? extends StructureObjectTracker> next) {        

    }
    
    

    public Parameter[] getParameters() {
        return parameters;
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
            if (corr==-1) ++currentTimePoint;   
            else {
                if (corr==0) corr=1;
                ++nLoop;
                //logger.debug("t: {}, correction performed return to: {}, loop: {}", currentTimePoint, corr, nLoop);
                if (nLoop>loopLimit) {
                    logger.warn("BCMTLC: loop limit exceded, performing assignment without correction from t: {} to: {}", corr, maxTimePoint);
                    for (currentTimePoint = corr; currentTimePoint<=maxTimePoint; ++currentTimePoint) assignPrevious(currentTimePoint, false);
                    currentTimePoint = maxTimePoint+1;
                    nLoop=0;
                } else currentTimePoint = corr;
            }
            if (maxTimePoint<currentTimePoint) { // when max timePoint in reached -> reset counter
                maxTimePoint=currentTimePoint;
                nLoop=0;
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
                children = parent.setChildrenObjects(new ObjectPopulation(populations[t], null), structureIdx); // will translate all voxels
                setAttributes(t, children, childrenPrev);
            }
            childrenPrev=children;
        }
    }
    
    private void setAttributes(int timePoint, ArrayList<StructureObject> children, ArrayList<StructureObject> childrenPrev) {
        for (int i = 0; i<children.size(); ++i) {
            TrackAttribute ta= getAttribute(timePoint, i);
            if (ta.prev==null || childrenPrev==null) children.get(i).resetTrackLinks();
            else children.get(i).setPreviousInTrack(childrenPrev.get(ta.prev.idx), ta.trackHead, getFlag(ta.flag));
        }
    }
    
    private TrackFlag getFlag(Flag flag) {
        if (flag==null) return null;
        if (flag==Flag.error) return TrackFlag.trackError;
        if (flag==Flag.correctionSplit) return TrackFlag.correctionSplit;
        if (flag==Flag.correctionMerge) return TrackFlag.correctionMerge;
        return null;
    }
    
    protected void init(List<StructureObject> parentTrack, int structureIdx, boolean segment) {
        this.parents=parentTrack;
        int timePointNumber = parentTrack.size();
        trackAttributes = (ArrayList<TrackAttribute>[])new ArrayList[timePointNumber];
        populations = (ArrayList<Object3D>[])new ArrayList[timePointNumber];
        if (segment) segmenters  = new SegmenterSplitAndMerge[timePointNumber];
        this.maxGR=this.maxGrowthRate.getValue().doubleValue();
        this.minGR=this.minGrowthRate.getValue().doubleValue();
        this.div=this.divisionCriterion.getValue().doubleValue();
        this.costLim = this.costLimit.getValue().doubleValue();
        this.cumCostLim = this.cumCostLimit.getValue().doubleValue();
        this.structureIdx=structureIdx;
    }
    /**
     * 
     * @param timePoint
     * @param performCorrection
     * @return minimal timePoint where correction has been performed, -1 if no correction has been performed
     */
    protected int assignPrevious(int timePoint, boolean performCorrection) {
        if (debug) logger.debug("assign previous timePOint: {}, correction? {}", timePoint, performCorrection);
        TrackAssginer assigner = new TrackAssginer(timePoint);
        assigner.resetTrackAttributes();
        while(assigner.nextTrack()) {
            if (debug) logger.debug("assigner: {}", assigner);
            if (performCorrection && assigner.needCorrection()) {
                int res =  assigner.performCorrection();
                if (res==-1) assigner.assignCurrent(); // no correction was performed
                else return res;
            } else assigner.assignCurrent();
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
            } else {
                //logger.debug("tp: {}, seg null? {} image null ? {}", timePoint, getSegmenter(timePoint)==null, parent.getRawImage(structureIdx)==null);
                ObjectPopulation pop= getSegmenter(timePoint).runSegmenter(parent.getRawImage(structureIdx), structureIdx, parent);
                if (pop!=null) populations[timePoint] = pop.getObjects();
                else populations[timePoint] = new ArrayList<Object3D>(0);
            }
            //logger.debug("get object @ {}, size: {}", timePoint, populations[timePoint].size());
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

    protected void resetIndicies(int timePoint) {
        if (trackAttributes[timePoint]!=null) for (int i = 0; i<trackAttributes[timePoint].size(); ++i) trackAttributes[timePoint].get(i).idx=i;
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
        protected TrackAttribute setFlag(Flag flag) {this.flag=flag; return this;}
        public void resetTrackAttributes() {
            this.prev=null;
            this.next=null;
            if (flag==Flag.error) this.flag=null; // do no reset merge & split flags..
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
            idxPrevLim = getObjects(timePoint-1).size();
            idxLim = getObjects(timePoint).size();
            //logger.debug("ini assigner: {}", timePoint);
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
                else increment();
            }
        }
        protected void incrementPrev() { // maximum index so that prevSize * minGR remain inferior to size-1
            while(idxPrevEnd<idxPrevLim) {
                double newSizePrev = sizePrev + getSize(timePoint-1, idxPrevEnd);
                if (debug) logger.debug("t: {}, increment prev: {}, old size prev: {} new size prev: {}, size: {}, maxGR: {}, minGR: {}, will increment: {}", timePoint, idxPrevEnd,sizePrev, newSizePrev, size, maxGR, minGR, sizePrev * maxGR < size || newSizePrev * minGR <= size  );
                if (sizePrev * maxGR < size || newSizePrev * minGR <= size) { // previous is too small compared to current -> add a second one, so that is is not too big
                    sizePrev=newSizePrev;
                    ++idxPrevEnd;
                } else return;
            }
        }
        protected void increment() {  
            while(idxEnd<idxLim) {
                double newSize = size + getSize(timePoint, idxEnd);
                if (debug) logger.debug("t: {}, increment: {}, old size: {} new size: {}, size prev: {}, maxGR: {}, minGR: {}, will increment: {}", timePoint, idxEnd,size, newSize, sizePrev, maxGR, minGR, sizePrev * minGR > size && sizePrev * maxGR > newSize );
                if ( size < sizePrev * div && sizePrev * maxGR > newSize) { // division, but don't grow to much // (sizePrev * minGR > size) // 
                    size=newSize;
                    ++idxEnd;
                } else return;
            }
        }
        protected boolean verifyInequality() {
            return sizePrev * minGR <= size && size <= sizePrev * maxGR;
        }
        public boolean needCorrection() {
            return (idxPrevEnd-idxPrev)>1; //|| (sizePrev * maxGR < size); et supprimer @ increment.. 
        }
        public void resetTrackAttributes() {
            if (trackAttributes[timePoint]!=null) for (TrackAttribute ta : trackAttributes[timePoint]) ta.resetTrackAttributes();
            if (trackAttributes[timePoint-1]!=null) for (TrackAttribute ta : trackAttributes[timePoint-1]) ta.next=null;
        }
        public void assignCurrent() {
            boolean error = (idxPrevEnd-idxPrev)>1 || (idxEnd-idx)>2;
            TrackAttribute taCur = getAttribute(timePoint, idx);
            TrackAttribute taPrev = getAttribute(timePoint-1, idxPrev);
            // assignments @ timePoint
            taCur.prev=taPrev;
            taCur.trackHead=false;
            if (error) taCur.flag=Flag.error;
            if ((idxEnd-idx)>1) {
                taPrev.division=true;
                for (int i = idx+1; i<idxEnd; ++i) {
                    getAttribute(timePoint, i).prev=taPrev;
                    if (error) getAttribute(timePoint, i).flag=Flag.error;
                }
            }
            // assignments @ timePoint-1
            taPrev.next=taCur;
            if (error) taPrev.flag=Flag.error;
            if ((idxPrevEnd-idxPrev)>1) {
                for (int i = idxPrev+1; i<idxPrevEnd; ++i) {
                    getAttribute(timePoint-1, i).next=taCur;
                    getAttribute(timePoint-1, i).flag=Flag.error;
                }
            }
        }
        
        /**
         * 
         * @return minimal timePoint where correction has been performed
         */
        public int performCorrection() {
            //logger.debug("t: {}: performing correction", timePoint);
            List<CorrectionScenario> merge = new MergeScenario(idxPrev, idxPrevEnd, timePoint-1).getWholeScenario(-1, costLim, cumCostLim);
            double mergeCost = 0; for (CorrectionScenario c : merge) mergeCost+=c.cost; if (merge.isEmpty()) mergeCost=-1;
            List<CorrectionScenario> split = new SplitScenario(getAttribute(timePoint, idx), timePoint).getWholeScenario(-1, costLim, mergeCost>0? Math.min(mergeCost, cumCostLim) : cumCostLim);
            double splitCost = 0; for (CorrectionScenario c : split) splitCost+=c.cost; if (split.isEmpty()) splitCost=-1;
            if (debug) logger.debug("t: {}: performing correction: merge scenario length: {}, cost: {}, split scenario: length {}, cost: {}", timePoint, merge.size(), mergeCost, split.size(), splitCost);
            if (mergeCost>splitCost && !split.isEmpty()) {
                for (CorrectionScenario c : split) c.applyScenario();
                return Math.max(1, timePoint);
            }
            else if (!merge.isEmpty()) {
                for (CorrectionScenario c : merge) c.applyScenario();
                return Math.max(1, timePoint-merge.size());
            } else return -1;
        }
        @Override public String toString() {
            return "timePoint: "+timePoint+ " prev: ["+idxPrev+";"+idxPrevEnd+"[ (lim: "+idxPrevLim+ ") next: ["+idx+";"+idxEnd+"[ (lim: "+idxLim+ ") size prev: "+sizePrev+ " size: "+size+ " error: "+verifyInequality();
        }
    }
    
    protected abstract class CorrectionScenario {
        double cost;
        protected abstract CorrectionScenario getNextScenario();
        /**
         * 
         * @param lengthLimit if >0 limits the length of the scenario
         * @param costLimit if >0 cost limit per operation
         * @param cumulativeCostLimit if >0 cost limit for the whole scenario
         * @return 
         */
        public List<CorrectionScenario> getWholeScenario(int lengthLimit, double costLimit, double cumulativeCostLimit) {
            ArrayList<CorrectionScenario> res = new ArrayList<CorrectionScenario>();
            CorrectionScenario cur = this;
            if (cur instanceof MergeScenario && ((MergeScenario)cur).listO.isEmpty()) return Collections.emptyList();
            double sum = 0;
            while(cur!=null && !Double.isNaN(cur.cost)) {
                res.add(cur);
                sum+=cur.cost;
                if (cur.cost > costLimit) return Collections.emptyList();
                if (cumulativeCostLimit>0 && sum>cumulativeCostLimit) return res; 
                if (lengthLimit>0 && res.size()>lengthLimit) return res;
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
            if (!listO.isEmpty()) this.cost = getSegmenter(timePoint).computeMergeCost(listO);
        }
        @Override protected MergeScenario getNextScenario() { // @ previous time, until there is one single parent ie no more bacteria to merge
            if (timePoint==0 || idxMin==idxMax) return null;
            int iMin = Integer.MAX_VALUE;
            int iMax = Integer.MIN_VALUE;
            for (int i = idxMin; i<idxMax; ++i) { // get all connected trackAttributes from previous timePoint
                TrackAttribute ta = getAttribute(timePoint, i).prev;
                if (ta==null) continue;
                if (iMin>ta.idx) iMin = ta.idx;
                if (iMax<ta.idx) iMax = ta.idx;
                if (ta.idx != trackAttributes[timePoint-1].indexOf(ta)) logger.error("BCMTLC: inconsistent data: t: {}, expected idx: {}, actual: {}", timePoint-1, ta.idx, trackAttributes[timePoint-1].indexOf(ta));
            }
            if (iMin==iMax) return null; // no need to merge
            if (iMin==Integer.MAX_VALUE || iMax==Integer.MIN_VALUE) return null; // no previous objects 
            return new MergeScenario(iMin,iMax+1, timePoint-1);
        }

        @Override
        protected void applyScenario() {
            Object3D o = populations[timePoint].get(idxMin);
            trackAttributes[timePoint].get(idxMin).flag=Flag.correctionMerge;
            trackAttributes[timePoint].get(idxMin).objectSize=-1; // reset object size;
            for (int i = idxMax-1; i>idxMin; --i) {
                Object3D rem = populations[timePoint].remove(i);
                o.addVoxels(rem.getVoxels());
                trackAttributes[timePoint].remove(i);
            }
            resetIndicies(timePoint);
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
            if (timePoint == populations.length-1) return null;
            if (o.next==null) assignPrevious(timePoint+1, false);
            if (o.next!=null) {
                if (o.division) return null;
                else return new SplitScenario(o.next, timePoint+1);
            }
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
                trackAttributes[timePoint].add(curIdx, new TrackAttribute(splitObject, curIdx).setFlag(Flag.correctionSplit));
                ++curIdx;
            }
            resetIndicies(timePoint);
        }
    }
}
