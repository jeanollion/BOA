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
import configuration.parameters.PostFilterSequence;
import configuration.parameters.PreFilterSequence;
import dataStructure.objects.Measurements;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObject.TrackFlag;
import dataStructure.objects.StructureObjectPreProcessing;
import dataStructure.objects.StructureObjectTracker;
import dataStructure.objects.Voxel;
import ij.process.AutoThresholder;
import image.Image;
import image.ImageByte;
import image.ImageMask;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import measurement.GeometricalMeasurements;
import static plugins.Plugin.logger;
import plugins.Segmenter;
import plugins.SegmenterSplitAndMerge;
import plugins.Tracker;
import plugins.TrackerSegmenter;
import plugins.UseThreshold;
import plugins.plugins.segmenters.BacteriaFluo;
import plugins.plugins.thresholders.IJAutoThresholder;
import static plugins.plugins.trackers.ObjectIdxTracker.getComparatorObject3D;
import utils.ArrayUtil;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class BacteriaClosedMicrochannelTrackerLocalCorrections implements TrackerSegmenter {
    
    // parametrization-related attributes
    protected PluginParameter<SegmenterSplitAndMerge> segmenter = new PluginParameter<>("Segmentation algorithm", SegmenterSplitAndMerge.class, false);
    BoundedNumberParameter maxGrowthRate = new BoundedNumberParameter("Maximum Size Increment", 2, 1.5, 1, null);
    BoundedNumberParameter minGrowthRate = new BoundedNumberParameter("Minimum size increment", 2, 0.8, 0.01, null);
    BoundedNumberParameter divisionCriterion = new BoundedNumberParameter("Division Criterion", 2, 0.80, 0.01, 1);
    BoundedNumberParameter costLimit = new BoundedNumberParameter("Correction: operation cost limit", 3, 1, 0, null);
    BoundedNumberParameter cumCostLimit = new BoundedNumberParameter("Correction: cumulative cost limit", 3, 5, 0, null);
    Parameter[] parameters = new Parameter[]{segmenter, divisionCriterion, minGrowthRate, maxGrowthRate, costLimit, cumCostLimit};

    @Override public SegmenterSplitAndMerge getSegmenter() {
        SegmenterSplitAndMerge s= segmenter.instanciatePlugin();
        if (!Double.isNaN(thresholdValue)) ((UseThreshold)s).setThresholdValue(thresholdValue);
        return s;
    }

    // tracking-related attributes
    private enum Flag {error, correctionMerge, correctionSplit;}
    List<Object3D>[] populations;
    //SegmenterSplitAndMerge[] segmenters;
    private boolean segment, correction;
    Image[] inputImages;
    List<TrackAttribute>[] trackAttributes;
    List<StructureObject> parents;
    int structureIdx;
    int minT, maxT;
    double maxGR, minGR, div, costLim, cumCostLim;
    final static int maxCorrectionLength = 10;
    PreFilterSequence preFilters; 
    PostFilterSequence postFilters;
    
    static int loopLimit=3;
    public static boolean debug=false, debugCorr=false;
    
    // hidden parameters! 
    static double maxSizeIncrementError = 0.3;
    static double maxFusionSize=200;
    
    public BacteriaClosedMicrochannelTrackerLocalCorrections() {}
    
    public BacteriaClosedMicrochannelTrackerLocalCorrections(SegmenterSplitAndMerge segmenter) {
        this.segmenter.setPlugin(segmenter);
    }
    
    public BacteriaClosedMicrochannelTrackerLocalCorrections(SegmenterSplitAndMerge segmenter, double divisionCriterion, double minGrowthRate, double maxGrowthRate, double costLimit, double cumulativeCostLimit) {
        this.segmenter.setPlugin(segmenter);
        this.maxGrowthRate.setValue(maxGrowthRate);
        this.minGrowthRate.setValue(minGrowthRate);
        this.divisionCriterion.setValue(divisionCriterion);
        this.costLimit.setValue(costLimit);
        this.cumCostLimit.setValue(cumulativeCostLimit);
    }
    
    public BacteriaClosedMicrochannelTrackerLocalCorrections setCostParameters(double operationCostLimit, double cumulativeCostLimit) {
        this.costLimit.setValue(operationCostLimit);
        this.cumCostLimit.setValue(cumulativeCostLimit);
        return this;
    }
    
    @Override public Parameter[] getParameters() {
        return parameters;
    }
    
    @Override public void track(int structureIdx, List<StructureObject> parentTrack) {
        init(parentTrack, structureIdx, false);
        segmentAndTrack(false);
    }

    @Override public void segmentAndTrack(int structureIdx, List<StructureObject> parentTrack, PreFilterSequence preFilters, PostFilterSequence postFilters) {
        this.preFilters=preFilters;
        this.postFilters=postFilters;
        init(parentTrack, structureIdx, true);
        segmentAndTrack(true);
    }
    
    public void segmentAndTrack(int structureIdx, List<StructureObject> parentTrack, boolean performCorrection) {
        init(parentTrack, structureIdx, true);
        segmentAndTrack(performCorrection);
    }
    public static boolean correctionStep;
    public static Map<String, List<StructureObject>> stepParents;
    private static int step = 0;
    double thresholdValue = Double.NaN;
    protected void segmentAndTrack(boolean performCorrection) {
        if (performCorrection && correctionStep) stepParents = new LinkedHashMap<>();
        this.correction=performCorrection;
        if (correction) inputImages=new Image[populations.length];
        // 0) optional compute threshold for all images
        if (getSegmenter() instanceof UseThreshold) {
            List<Image> planes = new ArrayList<>();
            // TODO -> record segmenters at this step in order not to have to process.. & erase after segmentation.
            for (int t = 0; t<populations.length; ++t) planes.add(((UseThreshold)getSegmenter()).getThresholdImage(getImage(t), structureIdx, parents.get(t)));
            double[] minAndMax = new double[2];
            int[] histo = Image.getHisto256(planes, minAndMax);
            //Image thresholdImage = Image.mergeZPlanes(planes);
            //thresholdValue = ((UseThreshold)s).getThresholder().runThresholder(thresholdImage, null);
            thresholdValue = IJAutoThresholder.runThresholder(AutoThresholder.Method.Otsu, histo, minAndMax, planes.get(0) instanceof ImageByte);
            logger.debug("Threshold Value over time: {}", thresholdValue);
        }
        // 1) assign all. Limit to first continuous segment of cells
        minT = 0;
        while (minT<populations.length && getObjects(minT).isEmpty()) minT++;
        maxT = populations.length;
        for (int t = minT+1; t<populations.length; ++t) {
            if (getObjects(t).isEmpty()) {
                maxT=t;
                break;
            }
            assignPrevious(t, false, false);
        }
        if (maxT-minT<=1) {
            for (StructureObject p : parents) p.setChildren(null, structureIdx);
            return;
        } else for (int t = maxT; t<populations.length; ++t) if (populations[t]!=null) populations[t].clear();
        
        // 2) perform corrections idx-wise
        if (correctionStep) step(null, true);
        //if (true) return;
        //if (true) return;
        int idxMax=0;
        int idxLim = populations[minT].size();
        for (int t = minT+1; t<maxT; ++t) if (populations[t].size()>idxLim) idxLim=populations[t].size();
        List<int[]> corrRanges = new ArrayList<>();
        List<int[]> corrRanges2 = new ArrayList<>(1);
        while(idxMax<idxLim) {
            boolean corr = performCorrectionsByIdx(minT+1, maxT-1, idxMax, corrRanges, false);
            if (corr && idxMax>0) { // corrections have been performed : run correction from 0 to idxMax within each time range// TODO: necessaire?
                for (int[] tRange : corrRanges) {
                    int nLoop=1;
                    for (int idx = 0; idx<=idxMax; ++idx) {
                        boolean corr2 = performCorrectionsByIdx(tRange[0], tRange[1], idx, corrRanges2, true);
                        //if (stepParents.size()>=12) return;
                        if (corr2) {
                            int[] tRange2 = corrRanges2.get(0);
                            if (tRange2[0]<tRange[0]) tRange[0] = tRange2[0];
                            if (tRange2[1]>tRange[1]) tRange[1] = tRange2[1];
                        }
                        if (idx>0 && corr2 && nLoop<=loopLimit) { // corrections have been performed : reset idx
                            idx = 0;
                            nLoop++;
                        } 
                    }
                    for (int t = tRange[0]; t<=tRange[1]; ++t) if (populations[t].size()>idxLim) idxLim=populations[t].size();
                }
            }
            idxMax++;
        }
        if (correctionStep) step("End of process", false);
        // 3) final assignement without correction, noticing all errors
        for (int t = minT+1; t<maxT; ++t) assignPrevious(t, false, true);
        applyLinksToParents(parents);
    }
    
    private boolean performCorrectionsByIdx(int tMin, int tMax, int idx, List<int[]> outRanges, boolean limitToOneRange) {
        if (debugCorr) logger.debug("performing corrections [{};{}] @ {}", tMin, tMax, idx);
        int nLoop=0, rangeLoop=0;
        int currentT = tMin;
        outRanges.clear();
        int[] currentRange = null;
        while(currentT<=tMax) {
            int minT = currentT;
            boolean change = false;
            if (idx<populations[currentT].size()) {
                TrackAttribute ta = getAttribute(currentT, idx);
                CorLoop : while (ta.errorPrev && nLoop<loopLimit) { // il y a une erreur à corriger (type nPrev>1)
                    if (currentRange == null) currentRange = new int[]{tMax, tMin};
                    TrackAssigner assigner = new TrackAssigner(currentT);
                    while (assigner.nextTrack() && assigner.idxEnd<=idx){} // idx > idxEnd 
                    //logger.debug("t:{}, idx:{}, ass: {}, can be corrected: {}, ta: {}",currentT, idx, assigner, assigner.canBeCorrected(), ta);
                    if (assigner.canBeCorrected()) {
                        int[] corrT = assigner.performCorrection();
                        if (corrT!=null) {
                            change = true;
                            minT = Math.min(minT, corrT[0]);
                            currentRange[0] = Math.min(currentRange[0], corrT[0]);
                            currentRange[1] = Math.max(currentRange[1], corrT[1]);
                            for (int t = corrT[0]; t<=corrT[1]; ++t) assignPrevious(t, false, false);
                            if (correctionStep) {
                                step(null, true);
                                //if (stepParents.size()>=12) return true;
                            }
                            ta = getAttribute(currentT, idx); // in case of correction..
                        } else break CorLoop;
                    } else break CorLoop;
                    nLoop++;
                }
                nLoop=0;
                if (currentRange!=null && currentRange[0]<=currentRange[1]) {
                    if (!outRanges.isEmpty()) {
                        int[] lastRange = outRanges.get(outRanges.size()-1);
                        if (lastRange[1]>=currentRange[0] && lastRange[0]<=currentRange[1]) { // overlap
                            lastRange[1]=Math.max(lastRange[1], currentRange[1]);
                            lastRange[0]=Math.min(lastRange[0], currentRange[0]);
                            ++rangeLoop;
                            if (rangeLoop>=loopLimit) {
                                currentT=lastRange[1]+1;
                                minT=currentT;
                                change=true;
                            }
                        } else {
                            outRanges.add(currentRange);
                            rangeLoop=0;
                        }
                    } else {
                        outRanges.add(currentRange);
                        rangeLoop=0;
                    }
                    currentRange=null;
                }
            }
            if (minT<currentT) currentT=minT;
            else if (!change) ++currentT;
        }
        if (outRanges.size()>1) { 
            Collections.sort(outRanges, (int[] o1, int[] o2) -> {
                if (o1[0]<o2[0]) return -1;
                else if (o1[0]>o2[0]) return 1;
                else if (o1[1]<o2[1]) return -1;
                else if (o1[1]>o2[1]) return -1;
                else return 0;
            });
            Iterator<int[]> it = outRanges.iterator();
            if (limitToOneRange) { // merge all
                int[] range = it.next();
                while(it.hasNext()) {
                    range[1] = Math.max(range[1], it.next()[1]);
                    it.remove();
                }
            } else { //merge if overlap
                int[] prev = it.next();
                while(it.hasNext()) {
                    int[] cur = it.next();
                    if (prev[1]>=cur[0]) {
                        prev[1] = cur[1];
                        it.remove();
                    } else prev=cur;
                }
            }
        }
        if (debugCorr) logger.debug("out range for @ {}: [{};{}]", idx, outRanges.toArray());
        return !outRanges.isEmpty();
    }
    
    private void step(String name, boolean increment) {
        if (name==null) {
            if (increment) name = "End of Step: "+step;
            else name = "Step: "+step;
        }
        List<StructureObject> newParents = new ArrayList<>(parents.size());
        for (StructureObject p : parents) newParents.add(p.duplicate());
        stepParents.put(name, newParents);
        // perform assignment without corrections?
        for (int t = 1; t<populations.length; ++t) assignPrevious(t, false, true);
        applyLinksToParents(newParents);
        if (increment) {
            ++step;
            logger.debug("start of step: {}", step);
        }
    }
    private void applyLinksToParents(List<StructureObject> parents) {
        List<StructureObject> childrenPrev = null;
        List<StructureObject> children = null;
        int errors = 0;
        for (int t = 0; t<populations.length; ++t) {
            StructureObject parent = parents.get(t);
            //logger.debug("setting objects from parent: {}, prevChildren null?", parent, childrenPrev==null);
            if (!segment) { // modifiy existing structureObjects
                children = parent.getChildren(structureIdx);
                if (children ==null || populations[t]==null) {}
                else if (children.size()!=populations[t].size()) logger.error("BCMTLC: error @ parent: {}, children and tracker objects differ in number", parent);
                else setAttributes(t, children, childrenPrev);
            } else { // creates new structureObjects
                List<Object3D> cObjects;
                if (correctionStep) {
                    cObjects = new ArrayList<>(populations[t].size());
                    for (Object3D o : populations[t]) cObjects.add(o.duplicate());
                } else {
                    cObjects = populations[t];
                    cleanTracks(t);
                }
                children = parent.setChildrenObjects(new ObjectPopulation(cObjects, null), structureIdx); // will translate all voxels
                setAttributes(t, children, childrenPrev);
                if (debug) for (StructureObject c : children) if (c.getTrackFlag()==StructureObject.TrackFlag.trackError) ++errors;
            }
            childrenPrev=children;
        }
        if (debug) logger.debug("Errors: {}", errors);
    }
    private void cleanTracks(int t) {
        if (populations[t]==null || populations[t].isEmpty()) return;
        TrackAttribute lastO = getAttribute(t, populations[t].size()-1); 
        if (lastO.prev!=null && lastO.prev.incompleteDivision) { // remove incomplete divisions
            if (debugCorr) logger.debug("incomplete division at: {}", lastO);
            removeTrack(lastO);
        } 
        //if (t>minT) for (TrackAttribute ta : trackAttributes[t])  if (ta.prev==null) removeTrack(ta); // remove tracks that do not start a min timePoint. // DO NOT REMOVE THEM IN ORDER TO BE ABLE TO PERFORM MANUAL CORRECTION
        
    }
    private void removeTrack(TrackAttribute o) {
        populations[o.timePoint].remove(o.o);
        trackAttributes[o.timePoint].remove(o);
        resetIndices(o.timePoint);
        if (o.next!=null) {
            List<TrackAttribute> curO = new ArrayList<>(3);
            List<TrackAttribute> nextO = new ArrayList<>(3);
            List<TrackAttribute> switchList = null;
            o.getNext(nextO);
            while(!nextO.isEmpty()) {
                switchList = curO;
                curO=nextO;
                nextO=switchList;
                nextO.clear();
                for (TrackAttribute ta : curO) {
                    ta.getNext(nextO);
                    populations[ta.timePoint].remove(ta.o);
                    trackAttributes[ta.timePoint].remove(ta);
                }
                resetIndices(curO.get(0).timePoint);
            }
        }
    }
    
    
    private void freeMemoryUntil(int timePoint) {
        /**if (segmenters==null) return;
        --timePoint;
        if (timePoint<0) return;
        while(true) {
            segmenters[timePoint--]=null;
            if (timePoint<0 || segmenters[timePoint]==null) return;
        }**/
        if (inputImages==null) return;
        --timePoint;
        if (timePoint<0) return;
        while(true) {
            inputImages[timePoint--]=null;
            if (timePoint<0 || inputImages[timePoint]==null) return;
        }
    }
    private void setAttributes(int timePoint, List<StructureObject> children, List<StructureObject> childrenPrev) {
        for (int i = 0; i<children.size(); ++i) {
            TrackAttribute ta= getAttribute(timePoint, i);
            if (ta.prev==null || childrenPrev==null) children.get(i).resetTrackLinks();
            else {
                if (ta.prev.idx>=childrenPrev.size()) logger.error("t:{} PREV NOT FOUND ta: {}, prev {}, all prev: {}", ta.timePoint, ta, ta.prev, trackAttributes[ta.timePoint-1]);
                else childrenPrev.get(ta.prev.idx).setTrackLinks(children.get(i), true, !ta.trackHead, getFlag(ta));
            }
            Measurements m = children.get(i).getMeasurements();
            if (ta.sizeIncrementError) m.setValue("TrackErrorSizeIncrement", true);
            if (ta.errorPrev) m.setValue("TrackErrorPrev", true);
            if (ta.errorCur) m.setValue("TrackErrorNext", true);
            m.setValue("SizeIncrement", ta.sizeIncrement);
        }
    }
    
    private TrackFlag getFlag(TrackAttribute ta) {
        if (ta==null) return null;
        if (ta.errorPrev || ta.errorCur) return TrackFlag.trackError;
        if (ta.flag==Flag.correctionSplit) return TrackFlag.correctionSplit;
        if (ta.flag==Flag.correctionMerge) return TrackFlag.correctionMerge;
        return null;
    }
    
    protected void init(List<StructureObject> parentTrack, int structureIdx, boolean segment) {
        if (preFilters==null) this.preFilters=new PreFilterSequence("");
        if (postFilters==null) this.postFilters=new PostFilterSequence("");
        this.segment=segment;
        this.parents=parentTrack;
        int timePointNumber = parentTrack.size();
        trackAttributes = (ArrayList<TrackAttribute>[])new ArrayList[timePointNumber];
        populations = (ArrayList<Object3D>[])new ArrayList[timePointNumber];
        //if (segment) segmenters  = new SegmenterSplitAndMerge[timePointNumber];
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
     * @return minimal/maximal timePoint where correction has been performed, -1 if no correction has been performed
     */
    protected int[] assignPrevious(int timePoint, boolean performCorrection, boolean noticeAllErrors) {
        if (debug) logger.debug("assign previous timePoint: {}, correction? {}", timePoint, performCorrection);
        TrackAssigner assigner = new TrackAssigner(timePoint);
        assigner.resetTrackAttributes();
        while(assigner.nextTrack()) {
            if (debug) logger.debug("assigner: {}", assigner);
            if (performCorrection && assigner.needCorrection()) {
                int[] res =  assigner.performCorrection();
                if (res==null) assigner.assignCurrent(noticeAllErrors); // no correction was performed
                else return res;
            } else assigner.assignCurrent(noticeAllErrors);
        }
        return null;
    }
    
    /*protected SegmenterSplitAndMerge getSegmenter(int timePoint) {
        if (segmenters==null) return null;
        if (segmenters[timePoint]==null) segmenters[timePoint] = this.segmenter.instanciatePlugin();
        return segmenters[timePoint];
        return segmenter.instanciatePlugin();
    }*/
    
    protected Image getImage(int timePoint) {
        if (inputImages==null || inputImages[timePoint]==null) {
            StructureObject parent = this.parents.get(timePoint);
            Image input = preFilters.filter(parent.getRawImage(structureIdx), parent);
            if (inputImages!=null) inputImages[timePoint] = input;
            return input;
        }
        return inputImages[timePoint];
    }
    
    protected List<Object3D> getObjects(int timePoint) {
        if (this.populations[timePoint]==null) {
            StructureObject parent = this.parents.get(timePoint);
            if (!segment) { // no segmentation, object should be already set as children of their parents
                List<StructureObject> list = parent.getChildren(structureIdx);
                if (list!=null) {
                    populations[timePoint] = new ArrayList<Object3D>(list.size());
                    for (StructureObject o : list)  populations[timePoint].add(o.getObject());
                } else populations[timePoint] = new ArrayList<Object3D>(0);
            } else {
                //logger.debug("tp: {}, seg null? {} image null ? {}", timePoint, getSegmenter(timePoint)==null, parent.getRawImage(structureIdx)==null);
                Image input = preFilters.filter(parent.getRawImage(structureIdx), parent);
                ObjectPopulation pop= getSegmenter().runSegmenter(input, structureIdx, parent);
                pop = postFilters.filter(pop, structureIdx, parent);
                if (pop!=null) populations[timePoint] = pop.getObjects();
                else populations[timePoint] = new ArrayList<Object3D>(0);
            }
            //logger.debug("get object @ {}, size: {}", timePoint, populations[timePoint].size());
        }
        return populations[timePoint];
    }
    
    protected List<TrackAttribute> getAttributes(int timePoint) {
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
        for (int i = 0; i<pop.size(); ++i) trackAttributes[timePoint].add(new TrackAttribute(pop.get(i), i, timePoint));
    }

    protected void resetIndices(int timePoint) {
        if (trackAttributes[timePoint]!=null) for (int i = 0; i<trackAttributes[timePoint].size(); ++i) trackAttributes[timePoint].get(i).idx=i;
    }
    protected double defaultSizeIncrement() {
        return (minGR+maxGR)/2.0;
    }
    
    protected int getErrorNumber(int tpMin, int tpMax, boolean assign) {
        if (tpMin<1) tpMin = 1;
        if (tpMax>=populations.length) tpMax = populations.length-1;
        int res = 0;
        for (int t = tpMin; t<=tpMax; ++t) {
            TrackAssigner ta = new TrackAssigner(t).verboseLevel(verboseLevelLimit); //verboseLevelLimit
            while(ta.nextTrack()) {
                res+=ta.getErrorCount();
                if (debugCorr && ta.getErrorCount()>0) logger.debug(ta.toString());
                ta.assignCurrent(false);
            }
            if (ta.idxPrevEnd<ta.idxPrevLim-1) res+=ta.idxPrevLim-1 - ta.idxPrevEnd; // # of unlinked cells @T-1, except the last one
            if (ta.idxEnd<ta.idxLim) res+=ta.idxLim - ta.idxEnd; // #of unlinked cells @t
        }
        return res;
    }
    
    protected class TrackAttribute {
        final static int sizeIncrementLimit = 5;
        int idx;
        final int timePoint;
        TrackAttribute prev;
        TrackAttribute next;
        Flag flag;
        boolean errorPrev, errorCur, incompleteDivision, sizeIncrementError;
        double sizeIncrement=Double.NaN;
        int nPrev;
        Object3D o;
        boolean division=false, trackHead=true;
        private double objectSize=Double.NaN;
        
        //final boolean touchEndOfChannel;
        protected TrackAttribute(Object3D o, int idx, int timePoint) {
            this.o=o;
            this.idx=idx;
            this.timePoint=timePoint;
            //touchEndOfChannel=idx!=0 && o.getBounds().getyMax()==parents.get(timePoint).getBounds().getSizeY(); // TODO: error here -> problem absolute/relative landmark?
        }
        protected TrackAttribute setFlag(Flag flag) {this.flag=flag; return this;}
        public void resetTrackAttributes(boolean previous, boolean next) {
            if (previous) {
                this.prev=null;
                errorPrev=false;
                trackHead=true;
                sizeIncrement=Double.NaN;
                sizeIncrementError=false;
            }
            if (next) {
                this.next=null;
                errorCur=false;
                division=false;
                incompleteDivision=false;
            }
        }
        public double getSize() {
            if (Double.isNaN(objectSize)) {
                //this.objectSize=GeometricalMeasurements.getVolume(o);
                //this.objectSize = o.getBounds().getSizeY();
                this.objectSize = GeometricalMeasurements.getFeretMax(o);
                // TODO Pour le squelette: calculer le squelette et pour le fermet: prendre une extremité et chercher le point du contour le + eloingé et le relier a l'autre extremitée, idem pour l'autre extremité.
            }
            return objectSize;
        }
        private List<Double> getLineageSizeIncrementList() {
            List<Double> res=  new ArrayList<>(sizeIncrementLimit);
            TrackAttribute ta = this.prev;
            List<TrackAttribute> bucket = new ArrayList<>(3);
            WL: while(res.size()<sizeIncrementLimit && ta!=null) {
                if (!ta.errorCur) {
                    if (ta.next==null) logger.error("Prev's NEXT NULL ta: {}: prev: {}", this, this.prev);
                    if (ta.division) {
                        double nextSize = 0;
                        bucket.clear();
                        List<TrackAttribute> n = ta.getNext(bucket);
                        if (n.size()>1) {
                            //boolean touch = false;
                            for (TrackAttribute t : n) {
                                nextSize+=t.getSize();
                                //if (t.touchEndOfChannel) touch=true;
                            }
                            res.add(nextSize/ta.getSize()); //if (!touch) 
                        }
                        //if (debug) logger.debug("division: {}, next: {}, nextSize: {}", ta, n, nextSize);
                    } else res.add(ta.next.getSize()/ta.getSize()); //if (!ta.next.touchEndOfChannel) 
                }
                ta = ta.prev;
            }
            return res;
        }
        public double getLineageSizeIncrement() {
            List<Double> list = getLineageSizeIncrementList();
            if (list.isEmpty()) return Double.NaN;
            double res = ArrayUtil.median(list);
            if (res<minGR) res = minGR;
            else if (res>maxGR) res = maxGR;
            //if (debug) logger.debug("getSizeIncrement for {}-{}: {} list:{}", timePoint, idx, res, list);
            return res;
        }
        public List<TrackAttribute> getNext(List<TrackAttribute> res) {
            if (trackAttributes.length-1<=timePoint) return res!=null ? res : Collections.EMPTY_LIST;
            if (true) {
                if (res==null) res = new ArrayList<>(3);
                for (TrackAttribute t : getAttributes(timePoint+1)) if (t.prev==this) res.add(t);
                return res;
            } else if (next!=null) {
                if (res==null) res = new ArrayList<>(1);
                res.add(next);
                return res;
            } else return res!=null ? res : Collections.EMPTY_LIST;
        }
        @Override public String toString() {
            return timePoint+"-"+idx+"(s:"+getSize()+"/th:"+this.trackHead+"/div:"+division+"/nPrev:"+nPrev+")";
        }
    }
    public static int verboseLevelLimit = 2;
    private static enum AssignerMode {ADAPTATIVE, RANGE};
    protected class TrackAssigner {
        int timePoint;
        int idxPrev=0, idxPrevEnd=0, idxPrevLim, idx=0, idxEnd=0, idxLim;
        double sizePrev=0, size=0;
        private int verboseLevel = 0;
        AssignerMode mode = AssignerMode.ADAPTATIVE;
        double[] currentScore = null;
        protected TrackAssigner(int timePoint) {
            if (timePoint<=0) throw new IllegalArgumentException("timePoint cannot be <=0");
            this.timePoint=timePoint;
            idxPrevLim = getObjects(timePoint-1).size();
            idxLim = getObjects(timePoint).size();
            if (timePoint==1) mode = AssignerMode.RANGE;
            //logger.debug("ini assigner: {}", timePoint);
        }
        private TrackAssigner verboseLevel(int verboseLevel) {
            this.verboseLevel=verboseLevel;
            return this;
        }
        private TrackAssigner setMode(AssignerMode mode) {
            this.mode = mode;
            return this;
        }
        protected TrackAssigner duplicate() {
            TrackAssigner res = new TrackAssigner(timePoint);
            res.idx=idx;
            res.idxEnd=idxEnd;
            res.idxPrev=idxPrev;
            res.idxPrevEnd=idxPrevEnd;
            res.size=size;
            res.sizePrev=sizePrev;
            res.mode=mode;
            res.verboseLevel=verboseLevel;
            res.currentScore=currentScore;
            return res;
        }
        protected boolean isValid() {
            return size>0 && sizePrev>0;
        }
        /**
         * 
         * @return true if there is at least 1 remaining object @ timePoint & timePoint -1
         */
        public boolean nextTrack() {
            //if (debug && idxEnd!=0) logger.debug("t:{}, [{};{}]->[{};{}]", timePoint, idxPrev, idxPrevEnd-1, idx, idxEnd-1);
            idxPrev = idxPrevEnd;
            idx = idxEnd;
            currentScore= null;
            if (idxPrevEnd==idxPrevLim || idxEnd==idxLim) return false;
            sizePrev = getSize(timePoint-1, idxPrevEnd++);
            size = getSize(timePoint, idxEnd++);
            incrementIfNecessary();
            return true;
        }
        protected void incrementIfNecessary() {
            incrementUntilVerifyInequality();
            if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{} start increment: {}", verboseLevel, this);
            if (!verifyInequality()) return;
            boolean change = true;
            while (change) {
                double prevSI = mode==AssignerMode.ADAPTATIVE ? getPreviousSizeIncrement(false) : Double.NaN;
                if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{} increment if necessary: PSI: {}, SI: {}, mode: {}", verboseLevel, prevSI, size/sizePrev, mode);
                if (!Double.isNaN(prevSI)) {
                    if (size/sizePrev<prevSI) change=increment(mode); // introduire notion d'erreur?
                    else change=incrementPrev(mode);
                } else {
                    if (size<sizePrev) {
                        change = increment(AssignerMode.RANGE);
                        //if (!change) change = incrementPrev(AssignerMode.RANGE);
                    } else {
                        change = incrementPrev(AssignerMode.RANGE);
                        if (!change) change = increment(AssignerMode.RANGE);
                    }
                }
                
                //if (!change) change=incrementPrevAndCur();
            }
        }
        private double[] getCurrentScore() {
            if (currentScore==null) {
                TrackAssigner currenScenario = duplicate().verboseLevel(verboseLevel+1).setMode(mode);
                currentScore = currenScenario.getAssignmentScoreForWholeScenario(idxPrevLim, idxLim);
            } return currentScore;
        }
        protected void incrementUntilVerifyInequality() {
            while(!verifyInequality()) {
                if (sizePrev * maxGR < size) {
                    if (idxPrevEnd<idxPrevLim) {
                        sizePrev+=getSize(timePoint-1, idxPrevEnd);
                        ++idxPrevEnd;
                        currentScore=null;
                    } else return;
                } else if (sizePrev * minGR > size) {
                    if (idxEnd<idxLim) {
                        size+=getSize(timePoint, idxEnd);
                        ++idxEnd;
                        currentScore=null;
                    } else return;
                } else return;
            }
        }
        protected boolean incrementPrev(AssignerMode mode) { // maximum index so that prevSize * minGR remain inferior to size-1
            boolean change = false;
            if(idxPrevEnd<idxPrevLim) {
                double newSizePrev = sizePrev + getSize(timePoint-1, idxPrevEnd);
                if (sizePrev * maxGR < size || newSizePrev * minGR < size) { // previous is too small compared to current -> add another second one, so that is is not too big
                    //if (verifyInequality()) { 
                    TrackAssigner newScenario = duplicate().verboseLevel(verboseLevel+1).setMode(mode);
                    newScenario.idxPrevEnd+=1;
                    newScenario.sizePrev=newSizePrev;
                    double[] scoreCur = getCurrentScore();
                    if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{}, [{};{}]->[{};{}] increment prev: getting score for other scenario...", verboseLevel, idxPrev, idxPrevEnd-1, idx, idxEnd-1);
                    double[] scoreNew = newScenario.getAssignmentScoreForWholeScenario(idxPrevLim, idxLim); //newScenario.idxPrevEnd, idxLim
                    if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{}, [{};{}]->[{};{}] increment prev: comparison of two solution for prevInc: old {} new: {}", verboseLevel, idxPrev, idxPrevEnd-1, idx, idxEnd-1, scoreCur, scoreNew);
                    if (verifyInequality()) scoreNew[1]+=maxSizeIncrementError; // if the current assignment already verify the inequality, increment only if there is improvement
                    if (compareScores(scoreCur, scoreNew, mode!=AssignerMode.RANGE)<=0) return change;
                    //}
                    if (verifyInequality()) scoreNew[1]-=maxSizeIncrementError;
                    currentScore=scoreNew;
                    sizePrev=newSizePrev;
                    ++idxPrevEnd;
                    change = true;
                    if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{}, t: {}, [{};{}][->[{};{}] (incremented prev), old size prev: {} (size€[{};{}]) new size prev: {} (size€[{};{}]), size: {}", verboseLevel, timePoint, idxPrev, idxPrevEnd-1, idx, idxEnd-1,sizePrev, sizePrev*minGR, sizePrev*maxGR, newSizePrev, newSizePrev*minGR, newSizePrev*maxGR, size);
                } 
                /*else if (!verifyInequality() && idxEnd<idxLim) { // increment cur & prev
                    TrackAssigner newScenario = duplicate().verboseLevel(verboseLevel+1).setMode(mode);
                    newScenario.idxPrevEnd+=1;
                    newScenario.sizePrev=newSizePrev;
                    newScenario.idxEnd+=1;
                    newScenario.size +=getSize(timePoint,idxEnd);
                    TrackAssigner currenScenario = duplicate().verboseLevel(verboseLevel+1).setMode(mode);
                    if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{}, [{};{}]->[{};{}] increment prev: getting score for current scenario...", verboseLevel, idxPrev, idxPrevEnd-1, idx, idxEnd-1);
                    double[] scoreCur = currenScenario.getAssignmentScoreForWholeScenario(idxPrevLim, idxLim); //newScenario.idxPrevEnd, idxLim ?
                    if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{}, [{};{}]->[{};{}] increment prev: getting score for other scenario...", verboseLevel, idxPrev, idxPrevEnd-1, idx, idxEnd-1);
                    double[] scoreNew = newScenario.getAssignmentScoreForWholeScenario(idxPrevLim, idxLim); //newScenario.idxPrevEnd, idxLim
                    if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{}, [{};{}]->[{};{}] increment prev: comparison of two solution for prevInc: old {} new: {}", verboseLevel, idxPrev, idxPrevEnd-1, idx, idxEnd-1, scoreCur, scoreNew);
                    if (verifyInequality()) scoreNew[1]+=maxSizeIncrementError; // if the current assignment already verify the inequality, increment only if there is improvement
                    if (compareScores(scoreCur, scoreNew, mode!=AssignerMode.RANGE)<=0) return change;
                    //}
                    sizePrev=newSizePrev;
                    ++idxPrevEnd;
                    change = true;
                    if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{}, t: {}, [{};{}][->[{};{}] (incremented prev), old size prev: {} (size€[{};{}]) new size prev: {} (size€[{};{}]), size: {}", verboseLevel, timePoint, idxPrev, idxPrevEnd-1, idx, idxEnd-1,sizePrev, sizePrev*minGR, sizePrev*maxGR, newSizePrev, newSizePrev*minGR, newSizePrev*maxGR, size);
                } */
                else return change;
            }
            return change;
        }
        
        protected boolean increment(AssignerMode mode) {
            boolean change = false;
            if(idxEnd<idxLim) {
                double newSize = size + getSize(timePoint, idxEnd);
                if (sizePrev * div > size ) { // division criterion + don't grow too much. Div criterio is mostly used in range mode //&& sizePrev * maxGR > newSize
                    size=newSize;
                    ++idxEnd;
                    change = true;
                    if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{}, t: {}, increment: [{};{}]->[{};{}], old size: {} new size: {}, size prev: {}, theo size€[{};{}]", verboseLevel, timePoint, idxPrev, idxPrevEnd-1, idx, idxEnd-1,size, newSize, sizePrev, sizePrev*minGR, sizePrev*maxGR);
                } else if (sizePrev * maxGR > newSize) { // don't grow too much & compare 2 scenarios
                    TrackAssigner newScenario = duplicate().verboseLevel(verboseLevel+1).setMode(mode);
                    newScenario.idxEnd+=1;
                    newScenario.size=newSize;
                    double[] scoreCur = getCurrentScore();
                    if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{}, [{};{}]->[{};{}] increment: getting score for other scenario...", verboseLevel, idxPrev, idxPrevEnd-1, idx, idxEnd-1);
                    double[] scoreNew = newScenario.getAssignmentScoreForWholeScenario(idxPrevLim, idxLim); //idxPrevLim, newScenario.idxEnd ??
                    if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{}, [{};{}]->[{};{}] increment: comparison of two solution for curInc: old {} new: {}", verboseLevel, idxPrev, idxPrevEnd-1, idx, idxEnd-1, scoreCur, scoreNew);
                    if (compareScores(scoreCur, scoreNew, mode!=AssignerMode.RANGE)<=0) return change;
                    else {
                        size=newSize;
                        ++idxEnd;
                        change = true;
                        currentScore = scoreNew;
                        if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{}, t: {}, increment: [{};{}]->[{};{}], old size: {} new size: {}, size prev: {}, theo size€[{};{}]", verboseLevel, timePoint, idxPrev, idxPrevEnd-1, idx, idxEnd-1,size, newSize, sizePrev, sizePrev*minGR, sizePrev*maxGR);
                    }
                } else if (sizePrev * minGR > size && idxPrevEnd<idxPrevLim ) { // cannot increment because grow too much but need to: increment & increment prev. //TODO REMOVE AND PUT IN SEPARATED METHOD?
                    TrackAssigner newScenario = duplicate().verboseLevel(verboseLevel+1).setMode(mode);
                    newScenario.idxEnd+=1;
                    newScenario.idxPrevEnd+=1;
                    newScenario.size=newSize;
                    newScenario.sizePrev+=getSize(timePoint-1, idxPrevEnd);
                    double[] scoreCur = getCurrentScore();
                    if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{}, [{};{}]->[{};{}] increment p&n : getting score for other scenario... ", verboseLevel, idxPrev, idxPrevEnd-1, idx, idxEnd-1);
                    double[] scoreNew = newScenario.getAssignmentScoreForWholeScenario(idxPrevLim, idxLim); //idxPrevLim, newScenario.idxEnd ??
                    if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{}, [{};{}]->[{};{}] increment p&n : comparison of two solution for prevInc&curInc: old {} new: {}", verboseLevel, idxPrev, idxPrevEnd-1, idx, idxEnd-1, scoreCur, scoreNew);
                    if (verifyInequality()) scoreNew[1]+=maxSizeIncrementError; // increment only if there is a significant amelioration if already verify inequality
                    if (compareScores(scoreCur, scoreNew, mode!=AssignerMode.RANGE)<=0) return change;
                    else { 
                        if (verifyInequality()) scoreNew[1]-=maxSizeIncrementError;
                        this.currentScore=scoreNew;
                        size=newSize;
                        ++idxEnd;
                        sizePrev+=getSize(timePoint-1, idxPrevEnd);
                        ++idxPrevEnd;
                        change = true;
                        if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{}, t: {}, [{};{}]->[{};{}] (increment p&n), old size: {} new size: {}, size prev: {}, theo size€[{};{}]", verboseLevel, timePoint, idxPrev, idxPrevEnd-1, idx, idxEnd-1,size, newSize, sizePrev, sizePrev*minGR, sizePrev*maxGR);
                    }
                }
                else return change;
            }
            return change;
        }
        
        /*protected boolean incrementPrevAndCur(AssignerMode mode) { 
            boolean change = false;
            double newSize = size;
            double newSizePrev=sizePrev;
            while(!verifyInequality() && idxEnd<idxLim && idxPrevEnd<idxPrevLim) {
                double newSize = size + getSize(timePoint, idxEnd);
                double newSizePrev = sizePrev+getSize(timePoint-1, idxPrevEnd);
                if (sizePrev * minGR > size && idxPrevEnd<idxPrevLim ) { // cannot increment because grow too much but need to: increment & increment prev. //TODO REMOVE AND PUT IN SEPARATED METHOD?
                    TrackAssigner newScenario = duplicate().verboseLevel(verboseLevel+1).setMode(mode);
                    newScenario.idxEnd+=1;
                    newScenario.idxPrevEnd+=1;
                    newScenario.size=newSize;
                    newScenario.sizePrev+=getSize(timePoint-1, idxPrevEnd);
                    TrackAssigner currenScenario = duplicate().verboseLevel(verboseLevel+1).setMode(mode);
                    if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{}, [{};{}]->[{};{}] increment p&n : getting score for current scenario...", verboseLevel, idxPrev, idxPrevEnd-1, idx, idxEnd-1);
                    double[] scoreCur = currenScenario.getAssignmentScoreForWholeScenario(idxPrevLim, idxLim); //idxPrevLim, newScenario.idxEnd ??
                    if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{}, [{};{}]->[{};{}] increment p&n : getting score for other scenario... ", verboseLevel, idxPrev, idxPrevEnd-1, idx, idxEnd-1);
                    double[] scoreNew = newScenario.getAssignmentScoreForWholeScenario(idxPrevLim, idxLim); //idxPrevLim, newScenario.idxEnd ??
                    if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{}, [{};{}]->[{};{}] increment p&n : comparison of two solution for prevInc&curInc: old {} new: {}", verboseLevel, idxPrev, idxPrevEnd-1, idx, idxEnd-1, scoreCur, scoreNew);
                    if (verifyInequality()) scoreNew[1]+=maxSizeIncrementError; // increment only if there is a significant amelioration if already verify inequality
                    if (compareScores(scoreCur, scoreNew, mode!=AssignerMode.RANGE)<=0) return change;
                    else { 
                        size=newSize;
                        ++idxEnd;
                        sizePrev+=getSize(timePoint-1, idxPrevEnd);
                        ++idxPrevEnd;
                        change = true;
                        if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{}, t: {}, [{};{}]->[{};{}] (increment p&n), old size: {} new size: {}, size prev: {}, theo size€[{};{}]", verboseLevel, timePoint, idxPrev, idxPrevEnd-1, idx, idxEnd-1,size, newSize, sizePrev, sizePrev*minGR, sizePrev*maxGR);
                    }
                }
                else return change;
            }
            return change;
        }*/
        protected double getPreviousSizeIncrement(boolean returnDefault) {
            double prevSizeIncrement = getAttribute(timePoint-1, idxPrev).getLineageSizeIncrement();
            if (Double.isNaN(prevSizeIncrement)) {
                if (returnDefault) return defaultSizeIncrement();
                else return Double.NaN;
            }
            if (idxPrevEnd-idxPrev>1) {
                double totalSize=getAttribute(timePoint-1, idxPrev).getSize();
                prevSizeIncrement *= getAttribute(timePoint-1, idxPrev).getSize();
                for (int i = idxPrev+1; i<idxPrevEnd; ++i) { // size-weighted barycenter of size increment lineage
                    double curSI = getAttribute(timePoint-1, i).getLineageSizeIncrement();
                    if (!Double.isNaN(curSI)) {
                        prevSizeIncrement+= curSI * getAttribute(timePoint-1, i).getSize();
                        totalSize += getAttribute(timePoint-1, i).getSize();
                    }
                }
                prevSizeIncrement/=totalSize;
            }
            return prevSizeIncrement;
            
        }
        protected double[] getCurrentAssignmentScore() {
            double prevSizeIncrement = mode==AssignerMode.ADAPTATIVE ? getPreviousSizeIncrement(false) : Double.NaN;
            if (Double.isNaN(prevSizeIncrement)) return new double[]{this.getErrorCount(), Double.NaN};
            if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{}, assignement score: prevSI: {}, SI: {}", verboseLevel, prevSizeIncrement, size/sizePrev);
            return new double[]{getErrorCount(), Math.abs(prevSizeIncrement - size/sizePrev)};
        }

        protected double[] getAssignmentScoreForWholeScenario(int idxPrevLimit, int idxLimit) { // will modify the current scenario!!
            if (truncatedEndOfChannel()) return new double[]{0, 0}; 
            double[] score = getCurrentAssignmentScore();
            if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{}, t:{}, [{};{}]->[{};{}] score start:{}", verboseLevel, timePoint, idxPrev, idxPrevEnd-1, idx, idxEnd-1, score);
            //if (Double.isNaN(score[1])) return score;
            while(nextTrack() && !truncatedEndOfChannel() && (idxEnd<=idxLimit || idxPrevEnd<=idxPrevLimit)) { // worst case scenario cannot be incomplete division
                double[] newScore=getCurrentAssignmentScore();
                score[1] = Math.max(score[1], newScore[1]); // maximum score = worst case scenario
                score[0]+=newScore[0];
                if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{} score for whole scenario: new score for [{};{}]->[{};{}]={}, wcs: {}", verboseLevel, idxPrev, idxPrevEnd-1, idx, idxEnd-1, newScore, score);
            }
            // add unlinked cells
            if (idxPrevLimit==idxPrevLim && idxPrevEnd<idxPrevLim-1) score[0]+=idxPrevLim-1 - idxPrevEnd; // # of unlinked cells, except the last one
            if (idxLimit==idxLim && idxEnd<idxLim-1) score[0]+=idxLim-1 - idxEnd;
            return score;
        }
        protected boolean canMerge(int i, int iLim, int tp) {
            return Double.isFinite(new MergeScenario(i, iLim, tp).cost);
        }
        private int compareScores(double[] s1, double[] s2, boolean useScore) { // 0 = nb errors / 1 = score value. return -1 if first has less errors & lower score
            if (!useScore) return Double.compare(s1[0], s2[0]);
            else {
                if (s1[0]<s2[0]) return -1;
                else if (s1[0]>s2[0]) return 1;
                else if (s1[1]<s2[1]) return -1;
                else if (s1[1]>s2[1]) return 1;
                else return 0;
            }
        }
        /*protected boolean incrementPrevAndCur() { // CONDITIONS??
            boolean change = false;
            if(idxEnd<idxLim && idxPrevEnd<idxPrevLim) {
                double newSize = size + getSize(timePoint, idxEnd);
                double newSizePrev = sizePrev + getSize(timePoint-1, idxPrevEnd);
                if (debug) logger.debug("t: {}, incrementPrev&Cur: [{};{}[->[{};{}[, old size: {} new size: {}, size prev: {}, theo size€[{};{}], will increment: {}", timePoint, idxPrev, idxPrevEnd, idx, idxEnd,size, newSize, sizePrev, sizePrev*minGR, sizePrev*maxGR, sizePrev * maxGR < newSize && newSizePrev * minGR < size && newSizePrev * minGR < newSize && newSizePrev * maxGR>newSize );
                if (sizePrev * maxGR < newSize && newSizePrev * minGR < size && newSizePrev * minGR < newSize && newSizePrev * maxGR>newSize ) { // 1) cannot increment or increment prev only, because grow too much but need to: increment & increment prev
                    TrackAssigner newScenario = duplicate();
                    newScenario.idxEnd+=1;
                    newScenario.idxPrevEnd+=1;
                    newScenario.size=newSize;
                    newScenario.sizePrev=newSizePrev;
                    TrackAssigner currenScenario = duplicate();
                    if (debug) logger.debug("[{};{}[->[{};{}[: getting score for current scenario...", idxPrev, idxPrevEnd, idx, idxEnd);
                    double scoreCur = currenScenario.getAssignmentScoreForWholeScenario(idxPrevLim, newScenario.idxEnd);
                    if (debug) logger.debug("[{};{}[->[{};{}[: getting score for other scenario... ", idxPrev, idxPrevEnd, idx, idxEnd);
                    double scoreNew = newScenario.getAssignmentScoreForWholeScenario(idxPrevLim, newScenario.idxEnd);
                    if (debug) logger.debug("[{};{}[->[{};{}[: comparison of two solution for prevInc&curInc: old {} new: {}", idxPrev, idxPrevEnd, idx, idxEnd, scoreCur, scoreNew);
                    if (scoreCur<scoreNew) return change;
                    else {
                        size=newSize;
                        ++idxEnd;
                        sizePrev=newSizePrev;
                        ++idxPrevEnd;
                        change = true;
                    }
                }
                else return change;
            }
            return change;
        }*/
        protected boolean verifyInequality() {
            return verifyInequality(size, sizePrev);
        }
        protected boolean verifyInequality(double size, double sizePrev) {
            return sizePrev * minGR <= size && size <= sizePrev * maxGR;
        }
        public boolean significantSizeIncrementError(int idxPrev, double size, double sizePrev) {
            if (mode==AssignerMode.ADAPTATIVE) {
            double prevSizeIncrement = getAttribute(timePoint-1, idxPrev).getLineageSizeIncrement();
            if (Double.isNaN(prevSizeIncrement)) {
                return !verifyInequality();
            } else {
                double sizeIncrement = size/sizePrev;
                if (debug && verboseLevel<verboseLevelLimit) logger.debug("{}, sizeIncrementError check: {}, SI:{} lineage: {}, error: {}", this, sizeIncrement, prevSizeIncrement, Math.abs(prevSizeIncrement-sizeIncrement));
                return Math.abs(prevSizeIncrement-sizeIncrement)>maxSizeIncrementError;
            }
            } else return !verifyInequality();
        }
        public boolean significantSizeIncrementError() {
            return significantSizeIncrementError(idxPrev, size, sizePrev);
        }
        public boolean truncatedEndOfChannel() {
            return (idxEnd==idxLim  && idxPrevEnd-idxPrev==1 && sizePrev * minGR > size); //&& idxEnd-idx==1
        }
        public boolean needCorrection() {
            return (idxPrevEnd-idxPrev)>1; //|| (sizePrev * maxGR < size); et supprimer @ increment.. 
        }
        public boolean canBeCorrected() {
            //return true;
            return needCorrection();// && (idxEnd-idx==1) ;
        }
        public void resetTrackAttributes() {
            if (trackAttributes[timePoint]!=null) for (TrackAttribute ta : trackAttributes[timePoint]) ta.resetTrackAttributes(true, false);
            if (trackAttributes[timePoint-1]!=null) for (TrackAttribute ta : trackAttributes[timePoint-1]) ta.resetTrackAttributes(false, true);
        }
        public void assignCurrent(boolean noticeAll) {
            int nPrev = idxPrevEnd-idxPrev;
            int nCur = idxEnd-idx;
            boolean error = nPrev>1 || nCur>2;
            if (nPrev==1 && nCur==1) {
                for (int i = 0; i<nPrev; ++i) {
                    TrackAttribute taCur = getAttribute(timePoint, idx+i);
                    TrackAttribute taPrev = getAttribute(timePoint-1, idxPrev+i);
                    taCur.prev=taPrev;
                    taPrev.next=taCur;
                    taCur.trackHead=false;
                    taCur.errorPrev=error;
                    taPrev.errorCur=error;
                    taCur.nPrev=nPrev;
                    if (noticeAll) {
                        taPrev.incompleteDivision = truncatedEndOfChannel();
                        if (!taPrev.incompleteDivision) taCur.sizeIncrementError = significantSizeIncrementError(idxPrev+i, taCur.getSize(), taPrev.getSize());
                        if (taCur.sizeIncrementError && !taCur.errorPrev) taCur.errorPrev=true;
                        if (taCur.sizeIncrementError && !taPrev.errorCur) taPrev.errorCur=true;
                        taCur.sizeIncrement=taCur.getSize()/taPrev.getSize();
                    }
                }
            } else if (nPrev==1 && nCur>1) { // division
                TrackAttribute taPrev = getAttribute(timePoint-1, idxPrev);
                TrackAttribute taCur = getAttribute(timePoint, idx);
                taPrev.division=true;
                taPrev.errorCur=error;
                taPrev.next=taCur;
                taCur.trackHead=false;
                for (int i = idx; i<idxEnd; ++i) {
                    TrackAttribute ta = getAttribute(timePoint, i);
                    ta.prev=taPrev;
                    ta.errorPrev=error;
                    ta.nPrev=nPrev;
                    if (noticeAll) {
                        ta.sizeIncrementError = significantSizeIncrementError();
                        ta.sizeIncrement = size / sizePrev;
                        if (ta.sizeIncrementError && !ta.errorPrev) ta.errorPrev=true;
                        if (ta.sizeIncrementError && !taPrev.errorCur) taPrev.errorCur=true;
                    }
                }
            } else if (nPrev>1 && nCur==1) { // merging
                TrackAttribute taCur = getAttribute(timePoint, idx);
                taCur.trackHead=false;
                taCur.prev=getAttribute(timePoint-1, idxPrev);
                taCur.errorPrev=true;
                taCur.nPrev=nPrev;
                if (noticeAll) {
                    taCur.sizeIncrementError = significantSizeIncrementError();
                    taCur.sizeIncrement=size/sizePrev;
                }
                for (int i = idxPrev; i<idxPrevEnd; ++i) {
                    getAttribute(timePoint-1, i).next=taCur;
                    getAttribute(timePoint-1, i).errorCur=true;
                }
            } else if (nPrev>1 && nCur>1) { // algorithm assign first with first (or 2 first) or last with last (or 2 last) (the most likely) and recursive call.
                TrackAssigner dup = duplicate(); // recursive algo. do not modify current trackAssigner!
                TrackAssigner currentAssigner = duplicate();
                TrackAttribute taCur1 = getAttribute(timePoint, idx);
                TrackAttribute taCur2 = getAttribute(timePoint, idx+1);
                TrackAttribute taPrev1 = getAttribute(timePoint-1, idxPrev);
                double sizeIncrement1 = taPrev1.getLineageSizeIncrement();
                if (Double.isNaN(sizeIncrement1)) sizeIncrement1 = defaultSizeIncrement();
                double score1 = Math.abs(taCur1.getSize()/taPrev1.getSize()-sizeIncrement1);
                double scoreDiv = Math.abs((taCur1.getSize()+taCur2.getSize())/taPrev1.getSize()-sizeIncrement1);
                TrackAttribute taCurEnd1 = getAttribute(timePoint, idxEnd-1);
                TrackAttribute taCurEnd2 = getAttribute(timePoint, idxEnd-2);
                TrackAttribute taPrevEnd = getAttribute(timePoint-1, idxPrevEnd-1);
                double sizeIncrementEnd = taPrevEnd.getLineageSizeIncrement();
                if (Double.isNaN(sizeIncrementEnd)) sizeIncrementEnd = defaultSizeIncrement();
                double scoreEnd1 = Math.abs(taCurEnd1.getSize()/taPrevEnd.getSize()-sizeIncrementEnd);
                double scoreEndDiv = Math.abs((taCurEnd1.getSize()+taCurEnd2.getSize())/taPrevEnd.getSize()-sizeIncrementEnd);
                double score=Math.min(scoreEndDiv, Math.min(scoreEnd1, Math.min(score1, scoreDiv)));
                if (score1==score) { // assign first with first
                    dup.idx++;
                    dup.idxPrev++;
                    currentAssigner.idxEnd=dup.idx;
                    currentAssigner.idxPrevEnd=dup.idxPrev;
                } else if (score==scoreEnd1) { // assign last with last
                    dup.idxEnd--;
                    dup.idxPrevEnd--;
                    currentAssigner.idx=dup.idxEnd;
                    currentAssigner.idxPrev=dup.idxPrevEnd;
                } else if (scoreDiv==score) { // assign first with 2 first
                    dup.idx+=2;
                    dup.idxPrev++;
                    currentAssigner.idxEnd=dup.idx;
                    currentAssigner.idxPrevEnd=dup.idxPrev;
                } else { // assign last with 2 lasts
                    dup.idxEnd-=2;
                    dup.idxPrevEnd--;
                    currentAssigner.idx=dup.idxEnd;
                    currentAssigner.idxPrev=dup.idxPrevEnd;
                }
                if (debug && verboseLevel<verboseLevelLimit) logger.debug("assignment {} with {} objects, assign {}, div:{}", nPrev, nCur, (score==score1||score==scoreEndDiv) ? "first" : "last", (score==scoreDiv||score==scoreEndDiv));
                currentAssigner.assignCurrent(noticeAll); // perform current assignement
                dup.assignCurrent(noticeAll); // recursive call
            }
        }
        
        /**
         * 
         * @return minimal/maximal+1 timePoint where correction has been performed
         */
        public int[] performCorrection() {
            if (debugCorr && verboseLevel<verboseLevelLimit) logger.debug("t: {}: performing correction, idxPrev: {}, idxPrevEnd: {}", timePoint, idxPrev, idxPrevEnd);
            if (idxEnd-idx==1) return performCorrectionSplitOrMergeOverMultipleTime();
            else if (idxPrevEnd-idxPrev>1) return performCorrectionMultipleObjects();
            else return null;
        }
        private int[] performCorrectionSplitOrMergeOverMultipleTime() {
            MergeScenario m = new MergeScenario(idxPrev, idxPrevEnd, timePoint-1);
            //if (Double.isInfinite(m.cost)) return -1; //cannot merge
            List<CorrectionScenario> merge = m.getWholeScenario(maxCorrectionLength, costLim, cumCostLim); // merge scenario
            double[] mergeCost = new double[]{Double.POSITIVE_INFINITY, 0}; for (CorrectionScenario c : merge) mergeCost[1]+=c.cost; if (merge.isEmpty()) mergeCost[1]=Double.POSITIVE_INFINITY;
            SplitScenario ss = new SplitScenario(getAttribute(timePoint, idx), timePoint);
            for (int i = idx+1; i<idxEnd; ++i) {
                SplitScenario sss = new SplitScenario(getAttribute(timePoint, i), timePoint);
                if (sss.cost<ss.cost) ss=sss;
            }
            List<CorrectionScenario> split =ss.getWholeScenario(maxCorrectionLength, costLim, cumCostLim);
            double[] splitCost = new double[]{Double.POSITIVE_INFINITY, 0}; for (CorrectionScenario c : split) splitCost[1]+=c.cost; if (split.isEmpty()) splitCost[1]=Double.POSITIVE_INFINITY;
            
            if (Double.isInfinite(mergeCost[1]) && Double.isInfinite(splitCost[1])) return null;
            int tMin = timePoint-merge.size();
            int tMax = timePoint+split.size();
            double currentErrors = getErrorNumber(tMin, tMax, false);
            ObjectAndAttributeSave saveCur = new ObjectAndAttributeSave(timePoint-merge.size(), timePoint+split.size()-1);
            ObjectAndAttributeSave saveMerge=null, saveSplit=null;
            if (Double.isFinite(splitCost[1])) {
                for (CorrectionScenario c : split) c.applyScenario();
                splitCost[0] = getErrorNumber(tMin, tMax, true);
                saveSplit = new ObjectAndAttributeSave(timePoint, timePoint+split.size()-1);
                if (correctionStep) step("step:"+step+"/split scenario ["+timePoint+";"+(timePoint+split.size()-1)+"]", false);
                saveCur.restore(timePoint, timePoint+split.size()-1);
                for (int t = timePoint;t <Math.min(timePoint+split.size()+1, populations.length-1); ++t) assignPrevious(t, false, false);
            }
            if (Double.isFinite(mergeCost[1])) {
                for (CorrectionScenario c : merge) c.applyScenario();
                mergeCost[0] = getErrorNumber(tMin, tMax, true);
                saveMerge = new ObjectAndAttributeSave(timePoint-merge.size(), timePoint-1);
                if (correctionStep) step("step:"+step+"/merge scenario ["+(timePoint-merge.size())+";"+(timePoint-1)+"]", false);
                saveCur.restore(timePoint-merge.size(), timePoint-1);
                for (int t = Math.max(1, timePoint-merge.size());t<=timePoint; ++t) assignPrevious(t, false, false);
            }
            if (debugCorr && verboseLevel<verboseLevelLimit) logger.debug("t: {}: performing correction: errors: {}, merge scenario length: {}, cost: {}, errors: {}, split scenario: length {}, cost: {}, errors: {}", timePoint, currentErrors, merge.size(), mergeCost[1], mergeCost[0], split.size(), splitCost[1], splitCost[0]);
            if (saveMerge!=null && mergeCost[0]<=currentErrors && compareScores(mergeCost, splitCost, true)<=0 ) {
                saveMerge.restoreAll();
                if (debugCorr && verboseLevel<verboseLevelLimit) logger.debug("apply merge scenario!");
                return new int[]{Math.max(1, timePoint-merge.size()), Math.min(populations.length-1, timePoint+1)};
            } else if (saveSplit!=null && splitCost[0]<=currentErrors && compareScores(mergeCost, splitCost, true)>=0 ) {
                if (debugCorr && verboseLevel<verboseLevelLimit) logger.debug("apply split scenario!");
                saveSplit.restoreAll();
                return new int[]{timePoint, Math.min(populations.length-1, timePoint+split.size()+1)};
            } else return null;
        }
        public int getErrorCount() {
            return Math.max(0, idxEnd-idx-2) + // division in more than 2
                    idxPrevEnd-idxPrev-1 + // merging
                    (verifyInequality() ? 0 : truncatedEndOfChannel()?0:1); // bad size increment
        }
        private int[] performCorrectionMultipleObjects() {
            double sizeInc1 = getAttribute(timePoint, idx).getSize()/getAttribute(timePoint-1, idxPrev).getSize();
            double expectedInc1 = getAttribute(timePoint-1, idxPrev).getLineageSizeIncrement();
            // TODO : plus de scenarios: merge all before, merge all before & after, merge all but one... 
            //Faire un type de scenario qui encapsule plusieurs scenarios. P0 23-2-0 si fusion = 50
            if (Math.abs(sizeInc1-expectedInc1)>maxSizeIncrementError) {
                CorrectionScenario before, after;
                if (sizeInc1>expectedInc1) { // split after VS merge before & split
                    before = new SplitAndMerge(timePoint-1, idxPrev, false, false); // ajouter merge all
                    //after = new SplitAndMerge(timePoint, idx, true, true);
                    after = new SplitScenario(getAttribute(timePoint, idx), timePoint);
                } else { // merge & split before vs split & merge after
                    before = new SplitAndMerge(timePoint-1, idxPrev, true, false);
                    //after = new SplitAndMerge(timePoint, idx, false, true);
                    after = new SplitScenario(getAttribute(timePoint, idx+1), timePoint);
                }
                if (before.cost>=costLim && after.cost>=costLim) return null;
                // try both scenarios and check error number @t-1, t & t+1
                double currentErrors = getErrorNumber(timePoint-1, timePoint+1, false);
                ObjectAndAttributeSave saveCur = new ObjectAndAttributeSave(timePoint-1, timePoint);
                ObjectAndAttributeSave saveBefore=null, saveAfter=null;
                double errorsBefore=Double.POSITIVE_INFINITY, errorsAfter=Double.POSITIVE_INFINITY;
                if (before.cost<costLim) {
                    before.applyScenario();
                    errorsBefore= getErrorNumber(timePoint-1, timePoint+1, true);
                    saveBefore= new ObjectAndAttributeSave(timePoint-1, timePoint-1);
                    if (correctionStep) step("step:"+step+(sizeInc1>expectedInc1 ?("/split&merge@"+(timePoint-1)) : "merge&split@"+(timePoint-1)) , false);
                    saveCur.restore(timePoint-1);
                    for (int t = Math.max(1, timePoint-1); t<=timePoint; ++t) assignPrevious(t, false , false);
                }
                if (after.cost<costLim) {
                    after.applyScenario();
                    errorsAfter= getErrorNumber(timePoint-1, timePoint+1, true);
                    saveAfter= new ObjectAndAttributeSave(timePoint, timePoint);
                    if (correctionStep) step("step:"+step+(sizeInc1>expectedInc1 ?("/split@"+(timePoint))+"/idx:"+idx : "split@"+(timePoint-1)+"/idx:"+(idx+1)) , false);
                    saveCur.restore(timePoint);
                    for (int t = timePoint; t<=Math.min(timePoint+1, populations.length-1); ++t) assignPrevious(t, false , false);
                }
                if (debugCorr && verboseLevel<verboseLevelLimit) logger.debug("split&merge: errors current: {}, errors sc.tp-1: {}, cost: {}, errors sc.tp: {}, cost{}",currentErrors, errorsBefore, before.cost, errorsAfter, after.cost );
                if (saveBefore!=null && errorsBefore<=currentErrors && (errorsBefore<errorsAfter && before.cost<after.cost || (errorsBefore==errorsAfter && before.cost<after.cost))) {
                    saveBefore.restore(timePoint-1);
                    return new int[]{Math.max(1, timePoint-1), timePoint};
                } else if (saveAfter!=null && errorsAfter<=currentErrors && (errorsAfter<errorsBefore && after.cost<before.cost || (errorsBefore==errorsAfter && after.cost<before.cost))) {
                    saveAfter.restore(timePoint);
                    return new int[]{Math.max(1, timePoint-1), Math.min(populations.length-1, timePoint+1)};
                } else return null;
            }
            return null;
        }
        
        @Override public String toString() {
            return "L:"+verboseLevel+ "/t:"+timePoint+ " ["+idxPrev+";"+(idxPrevEnd-1)+"]->["+idx+";"+(idxEnd-1)+"] Size: "+sizePrev+ "->"+size+ "/nPrev: "+(idxPrevEnd-idxPrev)+"/nCur: "+(idxEnd-idx)+"/inequality: "+verifyInequality()+ "/errors: "+getErrorCount();
        }
    }

    protected class ObjectAndAttributeSave {
        final List<Object3D>[] objects;
        final List<TrackAttribute>[] ta;
        final int tpMin;
        protected ObjectAndAttributeSave(int tpMin, int tpMax) {
            if (tpMin<1) tpMin = 1;
            if (tpMax>=populations.length) tpMax = populations.length-1;
            this.tpMin=tpMin;
            objects = new List[tpMax-tpMin+1];
            ta=new List[tpMax-tpMin+1];
            for (int t = tpMin; t<=tpMax; ++t) {
                objects[t-tpMin] = new ArrayList(populations[t]);
                ta[t-tpMin] = new ArrayList(trackAttributes[t]);
            }
        }
        public void restoreAll() {
            for (int i = 0; i<objects.length; i++) {
                populations[i+tpMin] = new ArrayList(objects[i]);
                trackAttributes[i+tpMin] = new ArrayList(ta[i]);
                resetIndices(i+tpMin);
            }
        }
        public boolean restore(int tMin, int tMax) {
            if (tMax<tpMin) return false;
            if (tMin<tpMin) tMin=tpMin;
            if (tMax>=tpMin+objects.length) tMax=tpMin+objects.length-1;
            for (int t = tMin; t<=tMax; ++t) {
                populations[t] = new ArrayList(objects[t-tpMin]);
                trackAttributes[t] = new ArrayList(ta[t-tpMin]);
                resetIndices(t);
            }
            return true;
        }
        public boolean restore(int t) {
            if (t<tpMin || t>=tpMin+objects.length) return false;
            populations[t] = new ArrayList(objects[t-tpMin]);
            trackAttributes[t] = new ArrayList(ta[t-tpMin]);
            resetIndices(t);
            return true;
        }
    }
    
    protected abstract class CorrectionScenario {
        double cost=Double.POSITIVE_INFINITY;
        final int timePointMin, timePointMax;
        protected CorrectionScenario(int timePointMin, int timePointMax) {this.timePointMin=timePointMin; this.timePointMax=timePointMax;}
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
            while(cur!=null && (!Double.isNaN(cur.cost)) && Double.isFinite(cur.cost)) {
                res.add(cur);
                sum+=cur.cost;
                if (cur.cost > costLimit) return Collections.emptyList();
                if (cumulativeCostLimit>0 && sum>cumulativeCostLimit) return Collections.emptyList();
                if (lengthLimit>0 && res.size()>=lengthLimit) return Collections.emptyList();
                cur = cur.getNextScenario();
            }
            return res;
        }
        protected abstract void applyScenario();
    }
    protected class MergeScenario extends CorrectionScenario {
        int idxMin, idxMax;
        ArrayList<Object3D> listO;
        public MergeScenario(int idxMin, int idxMax, int timePoint) { // idxMax excluded
            super(timePoint, timePoint);
            this.idxMax=idxMax;
            this.idxMin = idxMin;
            listO = new ArrayList<Object3D>(idxMax - idxMin);
            for (int i = idxMin; i<idxMax; ++i) {
                TrackAttribute ta = getAttribute(timePoint, i);
                listO.add(ta.o);
            }
            if (!listO.isEmpty()) {
                this.cost = getSegmenter().computeMergeCost(getImage(timePoint), listO);
                // check for small objects
                if (Double.isFinite(cost)) { // could merge
                    int nSmall = 0;
                    for (Object3D o :  listO) if (o.getSize()<maxFusionSize) ++nSmall;
                    if (nSmall>=listO.size()-1) cost = 0;
                }
            } else cost = Double.POSITIVE_INFINITY;
            if (debugCorr) logger.debug("Merge scenario: tp: {}, idxMin: {}, #objects: {}, cost: {}", timePoint, idxMin, listO.size(), cost);
        }
        @Override protected MergeScenario getNextScenario() { // @ previous time, until there is one single parent ie no more bacteria to merge
            if (timePointMin==0 || idxMin==idxMax) return null;
            int iMin = Integer.MAX_VALUE;
            int iMax = -1;
            for (int i = idxMin; i<idxMax; ++i) { // get all connected trackAttributes from previous timePoint
                TrackAttribute ta = getAttribute(timePointMin, i).prev;
                if (ta==null) continue;
                //if (ta.division) for (TrackAttribute taDiv : ta.getNext()) if (taDiv.idx<idxMin || taDiv.idx>=idxMax) return null; // if division & on of the divided objects is not in the current objects to merge: stop
                if (iMin>ta.idx) iMin = ta.idx;
                if (iMax<ta.idx) iMax = ta.idx;
                if (ta.idx != trackAttributes[timePointMin-1].indexOf(ta)) logger.error("BCMTLC: inconsistent data: t: {}, expected idx: {}, actual: {}", timePointMin-1, ta.idx, trackAttributes[timePointMin-1].indexOf(ta));
            }
            if (iMin==iMax) return null; // no need to merge
            if (iMin==Integer.MAX_VALUE || iMax==-1) return null; // no previous objects 
            return new MergeScenario(iMin,iMax+1, timePointMin-1);
        }

        @Override
        protected void applyScenario() {
            List<Voxel> vox = new ArrayList<>();
            Object3D o = populations[timePointMin].get(idxMin); 
            for (int i = idxMax-1; i>=idxMin; --i) {
                Object3D rem = populations[timePointMin].remove(i);
                vox.addAll(rem.getVoxels());
                trackAttributes[timePointMin].remove(i);
            }
            Object3D merged = new Object3D(vox, idxMin+1, o.getScaleXY(), o.getScaleZ());
            populations[timePointMin].add(idxMin, merged);
            trackAttributes[timePointMin].add(idxMin, new TrackAttribute(merged, idxMin, timePointMin));
            resetIndices(timePointMin);
        }
    }
    
    protected class SplitScenario extends CorrectionScenario {
        TrackAttribute o;
        List<Object3D> splitObjects;
        public SplitScenario(TrackAttribute o, int timePoint) {
            super(timePoint, timePoint);
            this.o=o;
            splitObjects= new ArrayList<Object3D>();
            cost = getSegmenter().split(getImage(timePoint), o.o, splitObjects);
            if (debugCorr) logger.debug("Split scenario: tp: {}, idx: {}, cost: {} # objects: {}", timePoint, o.idx, cost, splitObjects.size());
        }
        @Override protected SplitScenario getNextScenario() { // until next division event OR reach end of channel & division with 2n sister lost
            if (timePointMin == populations.length-1) return null;
            if (o.next==null) {
                if (debugCorr) logger.debug("getNextScenario: assign @:{}", timePointMin+1);
                assignPrevious(timePointMin+1, false, false);
            }
            if (o.next!=null) {
                if (o.division || (o.next.idx==getObjects(timePointMin+1).size()-1 && o.getSize() * minGR > o.next.getSize())) return null;
                else return new SplitScenario(o.next, timePointMin+1);
            }
            else return null;
        }

        @Override
        protected void applyScenario() {
            Collections.sort(splitObjects, getComparatorObject3D(ObjectIdxTracker.IndexingOrder.YXZ)); // sort by increasing Y position
            populations[timePointMin].remove(o.idx);
            populations[timePointMin].addAll(o.idx, splitObjects);
            trackAttributes[timePointMin].remove(o.idx);
            int curIdx = o.idx;
            for (Object3D splitObject : splitObjects) {
                trackAttributes[timePointMin].add(curIdx, new TrackAttribute(splitObject, curIdx, timePointMin).setFlag(Flag.correctionSplit));
                ++curIdx;
            }
            resetIndices(timePointMin);
        }
    }
    protected class SplitAndMerge extends CorrectionScenario {
        List<Object3D> splitObjects, mergeObjects;
        final int idx;
        final boolean splitFirst;
        public SplitAndMerge(int timePoint, int idx, boolean splitFirst, boolean mergeOptional) {
            super(timePoint, timePoint);
            this.idx=idx;
            this.splitFirst=splitFirst;
            splitObjects= new ArrayList<Object3D>(2);
            if (splitFirst) {
                cost = getSegmenter().split(getImage(timePoint), getAttribute(timePoint, idx).o, splitObjects);
                Collections.sort(splitObjects, getComparatorObject3D(ObjectIdxTracker.IndexingOrder.YXZ));
                if (splitObjects.size()==2 && cost<costLim) {
                    mergeObjects = new ArrayList<>(2);
                    mergeObjects.add(splitObjects.get(1));
                    mergeObjects.add(getAttribute(timePoint, idx+1).o);
                    double mergeCost = getSegmenter().computeMergeCost(getImage(timePoint), mergeObjects);
                    // check for small objects
                    if (Double.isFinite(mergeCost)) { // could merge
                        int nSmall = 0;
                        for (Object3D o :  mergeObjects) if (o.getSize()<maxFusionSize) ++nSmall;
                        if (nSmall>=mergeObjects.size()-1) mergeCost = 0;
                    }
                    if (!mergeOptional || mergeCost+cost<costLim) {
                        cost+=mergeCost;
                    } else mergeObjects=null;
                }
            } else {
                cost = getSegmenter().split(getImage(timePoint), getAttribute(timePoint, idx+1).o, splitObjects);
                Collections.sort(splitObjects, getComparatorObject3D(ObjectIdxTracker.IndexingOrder.YXZ));
                if (splitObjects.size()==2 && cost<costLim) {
                    mergeObjects = new ArrayList<>(2);
                    mergeObjects.add(getAttribute(timePoint, idx).o);
                    mergeObjects.add(splitObjects.get(0));
                    double mergeCost = getSegmenter().computeMergeCost(getImage(timePoint), mergeObjects);
                    // check for small objects
                    if (Double.isFinite(mergeCost)) { // could merge
                        int nSmall = 0;
                        for (Object3D o :  mergeObjects) if (o.getSize()<maxFusionSize) ++nSmall;
                        if (nSmall>=mergeObjects.size()-1) mergeCost = 0;
                    }
                    if (!mergeOptional || mergeCost+cost<costLim) {
                        cost+=mergeCost;
                    } else mergeObjects=null;
                }
            }
            if (!mergeOptional && mergeObjects==null) cost = Double.POSITIVE_INFINITY;
            if (debugCorr) logger.debug("Split&Merge scenario: t: {}, idx {}: cost: {}, splitFist: {}, merge? {}", timePoint, idx, cost, splitFirst, mergeObjects!=null);
        }

        @Override
        protected CorrectionScenario getNextScenario() {
            return null;
        }

        @Override
        protected void applyScenario() { //TODO : if reverse if needed -> create new object & new trackAttribute
            if (debugCorr) logger.debug("t: {}, idx {}: performing correction: split&merge scenario, cost: {}, splitFist: {}", timePointMin, idx, cost, splitFirst);
            
            if (splitFirst) {
                populations[timePointMin].remove(idx);
                trackAttributes[timePointMin].remove(idx);
                populations[timePointMin].add(idx, splitObjects.get(0));
                trackAttributes[timePointMin].add(idx, new TrackAttribute(splitObjects.get(0), idx, timePointMin).setFlag(Flag.correctionSplit));
                Object3D nextObject;
                if (mergeObjects!=null) {
                    List<Voxel> mergedVox = new ArrayList<>();
                    for (Object3D o : mergeObjects) mergedVox.addAll(o.getVoxels());
                    nextObject = new Object3D(mergedVox, idx+2, mergeObjects.get(0).getScaleXY(), mergeObjects.get(0).getScaleZ());
                } else nextObject = splitObjects.get(1);
                if (mergeObjects!=null) {
                    populations[timePointMin].remove(idx+1);
                    trackAttributes[timePointMin].remove(idx+1);
                }
                populations[timePointMin].add(idx+1, nextObject);
                trackAttributes[timePointMin].add(idx+1, new TrackAttribute(nextObject, idx+1, timePointMin).setFlag(mergeObjects!=null ? Flag.correctionMerge : Flag.correctionSplit));
            } else {
                if (mergeObjects!=null) {
                    List<Voxel> mergedVox = new ArrayList<>();
                    for (Object3D o : mergeObjects) mergedVox.addAll(o.getVoxels());
                    Object3D mergeObject = new Object3D(mergedVox, idx+1, mergeObjects.get(0).getScaleXY(), mergeObjects.get(0).getScaleZ());
                    
                    populations[timePointMin].remove(idx);
                    trackAttributes[timePointMin].remove(idx);
                    populations[timePointMin].add(idx, mergeObject);
                    trackAttributes[timePointMin].add(idx, new TrackAttribute(mergeObject, idx, timePointMin).setFlag(Flag.correctionMerge));
                    
                    populations[timePointMin].remove(idx+1);
                    trackAttributes[timePointMin].remove(idx+1);
                    populations[timePointMin].add(idx+1, splitObjects.get(1));
                    trackAttributes[timePointMin].add(idx+1, new TrackAttribute(splitObjects.get(1), idx+1, timePointMin).setFlag(Flag.correctionSplit));
                } else {
                    populations[timePointMin].remove(idx+1);
                    trackAttributes[timePointMin].remove(idx+1);
                    for (int i = 0; i<splitObjects.size(); ++i) {
                        populations[timePointMin].add(idx+1+i, splitObjects.get(i));
                        trackAttributes[timePointMin].add(idx+1+i, new TrackAttribute(splitObjects.get(i), idx+1+i, timePointMin).setFlag(Flag.correctionSplit));
                    }
                }
            }
            resetIndices(timePointMin);
        }
        
    }
    protected class MultipleScenario extends CorrectionScenario {
        final List<CorrectionScenario> scenarios;

        public MultipleScenario(List<CorrectionScenario> sortedScenarios) {
            super(sortedScenarios.isEmpty()? 0 :sortedScenarios.get(0).timePointMin, sortedScenarios.isEmpty()? 0 : sortedScenarios.get(sortedScenarios.size()-1).timePointMax);
            this.scenarios = sortedScenarios;
        }
        
        @Override
        protected CorrectionScenario getNextScenario() {
            return null;
        }

        @Override
        protected void applyScenario() {
            for (CorrectionScenario s : scenarios) s.applyScenario();
        }
        
    }
}
