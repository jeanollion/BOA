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
package plugins.plugins.trackers.bacteriaInMicrochannelTracker;

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
import dataStructure.objects.StructureObjectPreProcessing;
import dataStructure.objects.StructureObjectTracker;
import dataStructure.objects.Voxel;
import ij.process.AutoThresholder;
import image.Image;
import image.ImageByte;
import image.ImageMask;
import image.ImageOperations;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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
import plugins.plugins.trackers.ObjectIdxTracker;
import static plugins.plugins.trackers.ObjectIdxTracker.getComparatorObject3D;
import utils.ArrayUtil;
import utils.Pair;
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
    //BoundedNumberParameter divisionCriterion = new BoundedNumberParameter("Division Criterion", 2, 0.80, 0.01, 1);
    BoundedNumberParameter costLimit = new BoundedNumberParameter("Correction: operation cost limit", 3, 1.5, 0, null);
    BoundedNumberParameter cumCostLimit = new BoundedNumberParameter("Correction: cumulative cost limit", 3, 5, 0, null);
    Parameter[] parameters = new Parameter[]{segmenter, minGrowthRate, maxGrowthRate, costLimit, cumCostLimit};

    @Override public SegmenterSplitAndMerge getSegmenter() {
        SegmenterSplitAndMerge s= segmenter.instanciatePlugin();
        if (!Double.isNaN(thresholdValue)) ((UseThreshold)s).setThresholdValue(thresholdValue);
        return s;
    }

    protected SegmenterSplitAndMerge getSegmenter(int frame) {
        SegmenterSplitAndMerge s= segmenter.instanciatePlugin();
        if (thresholdValueT!=null) ((UseThreshold)s).setThresholdValue(thresholdValueT[frame]);
        else if (!Double.isNaN(thresholdValue)) ((UseThreshold)s).setThresholdValue(thresholdValue);
        return s;
    }
    
    // tracking-related attributes
    protected enum Flag {error, correctionMerge, correctionSplit;}
    List<Object3D>[] populations;
    //SegmenterSplitAndMerge[] segmenters;
    private boolean segment, correction;
    Image[] inputImages;
    List<TrackAttribute>[] trackAttributes;
    List<StructureObject> parents;
    int structureIdx;
    int minT, maxT;
    double maxGR, minGR, costLim, cumCostLim;
    
    PreFilterSequence preFilters; 
    PostFilterSequence postFilters;
    
    static int loopLimit=3;
    public static boolean debug=false, debugCorr=false;
    public static double debugThreshold = Double.NaN;
    
    // hidden parameters! 
    // segmentation thld
    final static boolean adaptativeThreshold= true;
    final static double adaptativeCoefficient = 0.75;
    final static int adaptativeThresholdHalfWindow = 5;
    // sizeIncrement -> adaptative SI
    final static int sizeIncrementFrameNumber = 7; // number of frames for sizeIncrement computation
    final static double significativeSIErrorThld = 0.3; // size increment difference > to this value lead to an error
    final static double SIErrorValue=1; //0.9 -> less weight to sizeIncrement error / 1 -> same weight
    final static double SIIncreaseThld = 0.1;
    final static double SIQuiescentThld = 1.05; // under this value we consider cells are not growing -> if break in lineage no error count (cell dies)
    final static boolean setSIErrorsAsErrors = false; // SI errors are set as tracking errors in object attributes
    
    final static double maxFusionSize=200;
    final static int maxCorrectionLength = 20;
    
    
    public BacteriaClosedMicrochannelTrackerLocalCorrections() {}
    
    public BacteriaClosedMicrochannelTrackerLocalCorrections(SegmenterSplitAndMerge segmenter) {
        this.segmenter.setPlugin(segmenter);
    }
    
    public BacteriaClosedMicrochannelTrackerLocalCorrections(SegmenterSplitAndMerge segmenter, double divisionCriterion, double minGrowthRate, double maxGrowthRate, double costLimit, double cumulativeCostLimit) {
        this.segmenter.setPlugin(segmenter);
        this.maxGrowthRate.setValue(maxGrowthRate);
        this.minGrowthRate.setValue(minGrowthRate);
        //this.divisionCriterion.setValue(divisionCriterion);
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
    double[] thresholdValueT;
    
    protected void segmentAndTrack(boolean performCorrection) {
        if (performCorrection && correctionStep) stepParents = new LinkedHashMap<>();
        this.correction=performCorrection;
        if (correction) inputImages=new Image[populations.length];
        // 0) optional compute threshold for all images
        if (Double.isNaN(debugThreshold) && getSegmenter() instanceof UseThreshold) {
            List<Image> planes = new ArrayList<>();
            // TODO -> record segmenters at this step in order not to have to process.. & erase after segmentation.
            for (int t = 0; t<populations.length; ++t) planes.add(((UseThreshold)getSegmenter()).getThresholdImage(getImage(t), structureIdx, parents.get(t)));
            double[] minAndMax = new double[2];
            if (adaptativeThreshold) {
                long t0 = System.currentTimeMillis();
                List<int[]> histos = ImageOperations.getHisto256AsList(planes, minAndMax);
                long t1 = System.currentTimeMillis();
                int[] histoAll = new int[256];
                for (int[] h : histos) ImageOperations.addHisto(h, histoAll, true);
                // saturate histogram to remove device aberations 
                int saturateValue256 = (int)IJAutoThresholder.runThresholder(AutoThresholder.Method.MaxEntropy, histoAll, minAndMax, true); // byteImage=true to get a 8-bit value
                double saturateValue =  planes.get(0) instanceof ImageByte ? saturateValue256 : IJAutoThresholder.convertHisto256Threshold(saturateValue256, minAndMax);
                for (int i = saturateValue256; i<256; ++i) histoAll[i]=0;
                thresholdValue = IJAutoThresholder.runThresholder(AutoThresholder.Method.Otsu, histoAll, minAndMax, planes.get(0) instanceof ImageByte);
                long t2 = System.currentTimeMillis();
                if (debug || debugCorr) logger.debug("Threshold Value over all time: {}, minAndMax: {}, byte?{}, saturate value: {} ()", thresholdValue, minAndMax, planes.get(0) instanceof ImageByte, saturateValue256, saturateValue);
                int[] frameRange = getFramRangeContainingCells();
                long t3 = System.currentTimeMillis();
                if (frameRange==null) return;
                else if (saturateValue256<255 || frameRange[0]>0 || frameRange[1]<populations.length-1) { // update min and max if necessary
                    for (int i = frameRange[0]; i<=frameRange[1]; ++i) populations[i]=null; // reset objects
                    List<Image> planesSub = planes.subList(frameRange[0], frameRange[1]+1);
                    double[] minAndMaxNew = ImageOperations.getMinAndMax(planesSub);
                    minAndMaxNew[1] = saturateValue;
                    if (minAndMaxNew[0]!=minAndMax[0] || minAndMaxNew[1]!=minAndMax[1]) {
                        minAndMax = minAndMaxNew;
                        List<int[]> histosSub = ImageOperations.getHisto256AsList(planesSub, minAndMax);
                        for (int i = 0; i<histosSub.size(); ++i) {
                            histosSub.get(i)[255]=0; // remove last value -> saturation;
                            histos.set(i+frameRange[0], histosSub.get(i)); // replace new histograms
                        } 
                        //resetThreshold = true;
                        histoAll = new int[256];
                        for (int[] h : histosSub) ImageOperations.addHisto(h, histoAll, true);
                        thresholdValue = IJAutoThresholder.runThresholder(AutoThresholder.Method.Otsu, histoAll, minAndMax, planes.get(0) instanceof ImageByte);
                        if (debug || debugCorr) logger.debug("new threshold value: {}", thresholdValue);
                    }
                }
                long t4 = System.currentTimeMillis();
                // adaptative threhsold on sliding window histo mean
                if (adaptativeThresholdHalfWindow*2 < frameRange[1] - frameRange[0] ) {
                    int fMin = frameRange[0]; 
                    int fMax = fMin+2*adaptativeThresholdHalfWindow;
                    // init
                    thresholdValueT = new double[populations.length];
                    int[] histo = new int[256];
                    for (int f = fMin; f<=fMax; ++f) ImageOperations.addHisto(histos.get(f), histo, true);
                    //for (int i = higthThldLimit256; i<256; ++i) histo[i]=0; // saturate histogram to remove device aberations 
                    double t = (1-adaptativeCoefficient) * thresholdValue + adaptativeCoefficient * IJAutoThresholder.runThresholder(AutoThresholder.Method.Otsu, histo, minAndMax, planes.get(0) instanceof ImageByte);
                    for (int f = fMin; f<=fMin+adaptativeThresholdHalfWindow; ++f) thresholdValueT[f] = t; // this histo is valid until fMin + window
                    while (fMax<frameRange[1]) {
                        ++fMax;
                        ImageOperations.addHisto(histos.get(fMax), histo, true); 
                        ImageOperations.addHisto(histos.get(fMin), histo, false);
                        //for (int i = higthThldLimit256; i<256; ++i) histo[i]=0; // saturate histogram to remove device aberations 
                        ++fMin;
                        thresholdValueT[fMin+adaptativeThresholdHalfWindow] = IJAutoThresholder.runThresholder(AutoThresholder.Method.Otsu, histo, minAndMax, planes.get(0) instanceof ImageByte);
                        thresholdValueT[fMin+adaptativeThresholdHalfWindow] = adaptativeCoefficient * thresholdValueT[fMin+adaptativeThresholdHalfWindow] + (1-adaptativeCoefficient) * thresholdValue;
                        /*if (fMin+adaptativeThresholdHalfWindow==521) {
                            logger.debug("thld: {}", thresholdValueT[fMin+adaptativeThresholdHalfWindow]);
                            return;
                        }*/
                    }
                    for (int f = fMin+adaptativeThresholdHalfWindow+1; f<=frameRange[1]; ++f) thresholdValueT[f] = thresholdValueT[fMin+adaptativeThresholdHalfWindow]; // this histo is valid until last frame
                    long t5 = System.currentTimeMillis();
                    if (debug || debugCorr) {
                        logger.debug("framewindow: {}", frameRange);
                        Utils.plotProfile("Thresholds", thresholdValueT);
                        logger.debug("getHistos: {}ms, compute 1st thld: {}ms, getFrameWindow: {}ms, compute new Min&Max: {}ms, compute threshold window: {}ms", t1-t0, t2-t1, t3-t2, t4-t3, t5-t4);
                    }
                }
            } else { // non-adaptative threshold
                int[] histo = ImageOperations.getHisto256(planes, minAndMax);
                // saturate histogram to remove device aberations 
                int saturateValue256 = (int)IJAutoThresholder.runThresholder(AutoThresholder.Method.MaxEntropy, histo, minAndMax, true); // byteImage=true to get a 8-bit value
                double saturateValue =  planes.get(0) instanceof ImageByte ? saturateValue256 : IJAutoThresholder.convertHisto256Threshold(saturateValue256, minAndMax);
                for (int i = saturateValue256; i<256; ++i) histo[i]=0;
                thresholdValue = IJAutoThresholder.runThresholder(AutoThresholder.Method.Otsu, histo, minAndMax, planes.get(0) instanceof ImageByte);
                if (debug || debugCorr) logger.debug("Threshold Value over all time: {}, minAndMax: {}, byte?{}, saturate value: {} ()", thresholdValue, minAndMax, planes.get(0) instanceof ImageByte, saturateValue256, saturateValue);
                int[] frameRange = getFramRangeContainingCells();
                if (frameRange==null) return;
                else if (saturateValue256<255 || frameRange[0]>0 || frameRange[1]<populations.length-1) {
                    for (int i = frameRange[0]; i<=frameRange[1]; ++i) populations[i]=null; // reset objects
                    // getThreshold within frame
                    planes = planes.subList(frameRange[0], frameRange[1]+1);
                    minAndMax = ImageOperations.getMinAndMax(planes);
                    minAndMax[1] = saturateValue;
                    histo = ImageOperations.getHisto256(planes, minAndMax);
                    histo[255] = 0;
                    double newThresholdValue = IJAutoThresholder.runThresholder(AutoThresholder.Method.Otsu, histo, minAndMax, planes.get(0) instanceof ImageByte);
                    if (debug || debugCorr) logger.debug("Threshold Value over frames {}: {}, minAndMax: {}, byte?{}", frameRange,  newThresholdValue, minAndMax, planes.get(0) instanceof ImageByte);
                    if (newThresholdValue != thresholdValue) for (int f = 0; f<populations.length; ++f) populations[f] = null; // clean segmentation performed with previous threshold
                    thresholdValue = newThresholdValue;
                }
            }
        } else if (!Double.isNaN(debugThreshold)) {
            thresholdValue = debugThreshold;
            logger.debug("Threshold used: {}", thresholdValue);
        }
        //if (true) return;
        // 1) assign all. Limit to first continuous segment of cells
        minT = 0;
        while (minT<populations.length && getObjects(minT).isEmpty()) minT++;
        maxT = populations.length;
        for (int t = minT+1; t<populations.length; ++t) {
            if (getObjects(t).isEmpty()) {
                maxT=t;
                if (debug || debugCorr) logger.debug("no objects @frame: {}, threhsold: {}", maxT, this.thresholdValueT[maxT]);
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
        ///if (true) return;
        
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
    
    private int[] getFramRangeContainingCells() {
        int inc = this.populations.length<100 ? 1 : 10;
        int minT = 0;
        while (minT<populations.length && getObjects(minT).isEmpty()) minT+=inc;
        if (minT>=populations.length-1) return null;
        if (inc>1) while (minT>0 && !getObjects(minT-1).isEmpty()) minT--; // backward
        
        int maxT = populations.length-1;
        while (maxT>minT && getObjects(maxT).isEmpty()) maxT-=inc;
        if (maxT<=minT) return null;
        if (inc>1) while (maxT<populations.length-1 && !getObjects(maxT+1).isEmpty()) maxT++; // forward
        return new int[]{minT, maxT};
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
                TrackAttribute taPrev = idx<populations[currentT-1].size() ? getAttribute(currentT-1, idx) : null;
                CorLoop : while (((taPrev!=null && (taPrev.errorCur || taPrev.sizeIncrementError)) || ta.errorPrev || ta.sizeIncrementError) && nLoop<loopLimit) { // il y a une erreur à corriger
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
                            ta = getAttribute(currentT, idx); // in case of correction, attributes may have changed
                            taPrev = idx<populations[currentT-1].size() ? getAttribute(currentT-1, idx) : null;
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
                if (debug) for (StructureObject c : children) if (c.hasTrackLinkError(true, true)) ++errors;
            }
            childrenPrev=children;
        }
        if (debug) logger.debug("Errors: {}", errors);
    }
    private void cleanTracks(int t) {
        if (populations[t]==null || populations[t].isEmpty()) return;
        TrackAttribute lastO = getAttribute(t, populations[t].size()-1); 
        if (lastO.prev!=null && lastO.prev.truncatedDivision && lastO.idx>0) { // remove incomplete divisions
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
            Set<TrackAttribute> curO = new HashSet<>();
            Set<TrackAttribute> nextO = new HashSet<>();
            Set<TrackAttribute> switchList = null;
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
                int frame = curO.iterator().next().timePoint;
                resetIndices(frame);
                if (frame+1<populations.length && nextO.size()==populations[frame+1].size()) {
                    nextO.clear(); // stop while loop
                    for (int f = frame+1; f<populations.length; ++f) { // remove all objects from frame
                        populations[f].clear();
                        trackAttributes[f].clear();
                    }
                }
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
            if (ta.prev==null || childrenPrev==null) children.get(i).resetTrackLinks(true, true);
            else {
                if (ta.prev.idx>=childrenPrev.size()) logger.error("t:{} PREV NOT FOUND ta: {}, prev {}, all prev: {}", ta.timePoint, ta, ta.prev, trackAttributes[ta.timePoint-1]);
                else {
                    childrenPrev.get(ta.prev.idx).setTrackLinks(children.get(i), true, !ta.trackHead);
                }
            }
            StructureObject o = children.get(i);
            o.setAttribute("TrackErrorSizeIncrement", ta.sizeIncrementError);
            o.setAttribute(StructureObject.trackErrorPrev, ta.errorPrev);
            o.setAttribute(StructureObject.trackErrorNext, ta.errorCur);
            o.setAttribute("SizeIncrement", ta.sizeIncrement);
            o.setAttribute("TruncatedDivision", ta.truncatedDivision);
            if (ta.endOfChannelContact>0) o.setAttribute("EndOfChannelContact", ta.endOfChannelContact);
        }
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
        //this.div=this.divisionCriterion.getValue().doubleValue();
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
                ObjectPopulation pop= getSegmenter(timePoint).runSegmenter(input, structureIdx, parent);
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
    
    protected int getErrorNumber(int tpMin, int tpMaxIncluded, boolean assign) {
        if (tpMin<1) tpMin = 1;
        if (tpMaxIncluded>=populations.length) tpMaxIncluded = populations.length-1;
        int res = 0;
        for (int t = tpMin; t<=tpMaxIncluded; ++t) {
            TrackAssigner ta = new TrackAssigner(t).verboseLevel(verboseLevelLimit); //verboseLevelLimit
            while(ta.nextTrack()) {
                res+=ta.getErrorCount();
                if (debugCorr && ta.getErrorCount()>0) logger.debug(ta.toString());
                ta.assignCurrent(false);
            }
            
            //for (int i = 0; i<=Math.min(ta.idxPrevLim-2, ta.idxPrevEnd); ++i) if (getAttribute(t-1, i).next==null && getAttribute(t-1, i).errorCur ) {++res;}  // # of lineage terminaisons cells @t-1 with links index. if cell is not growing -> no error
            if (ta.idxPrevEnd<ta.idxPrevLim-1) res+=ta.idxPrevLim-1 - ta.idxPrevEnd; // # of unlinked cells @T-1, except the last one
            if (ta.idxEnd<ta.idxLim) res+=ta.idxLim - ta.idxEnd; // #of unlinked cells @t
        }
        return res;
    }
    private boolean existeOneLinkAfter(int timePoint, int idxIncluded, boolean next) {
        for (int i = idxIncluded; i<trackAttributes[timePoint].size(); ++i) {
            if ((next && getAttribute(timePoint, i).next!=null) || (!next && getAttribute(timePoint, i).prev!=null)) return true;
        }
        return false;
    }
    protected static double getObjectSize(Object3D o) {
        
        //this.objectSize=GeometricalMeasurements.getVolume(o); // ATTENTION SI VOLUME MODIFIER LA FONCTION GET WIDTH
        //this.objectSize = o.getBounds().getSizeY();
        return GeometricalMeasurements.getFeretMax(o);
        // TODO Pour le squelette: calculer le squelette et pour le fermet: prendre une extremité et chercher le point du contour le + eloingé et le relier a l'autre extremitée, idem pour l'autre extremité.
    }
    public class TrackAttribute {
        
        public int idx;
        final int timePoint;
        TrackAttribute prev;
        TrackAttribute next;
        Flag flag;
        boolean errorPrev, errorCur, truncatedDivision, sizeIncrementError;
        double sizeIncrement=Double.NaN;
        int nPrev;
        public final Object3D o;
        boolean division=false, trackHead=true;
        private double objectSize=Double.NaN;
        
        final boolean touchEndOfChannel;
        double endOfChannelContact;
        protected TrackAttribute(Object3D o, int idx, int timePoint) {
            this.o=o;
            this.idx=idx;
            this.timePoint=timePoint;
            int lim = parents.get(timePoint).getBounds().getSizeY()-1;
            if (o.getBounds().getyMax()==lim) {
                double count = 0;
                for (Voxel v : o.getVoxels()) if (v.y==lim) ++count;
                endOfChannelContact = count/getWidth();
                if (endOfChannelContact>0.45) touchEndOfChannel=true;
                else touchEndOfChannel=false; 
            } else  touchEndOfChannel=false; 
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
                truncatedDivision=false;
            }
        }
        public double getSize() {
            if (Double.isNaN(objectSize)) objectSize = getObjectSize(o);
            return objectSize;
        }
        private double getWidth() { // unscaled
            return (double)o.getVoxels().size() * o.getScaleXY() / getSize(); // do not use getSize() if getSize() return area !!
        }
        private List<Double> getLineageSizeIncrementList() {
            List<Double> res=  new ArrayList<>(sizeIncrementFrameNumber);
            TrackAttribute ta = this.prev;
            Set<TrackAttribute> bucket = new HashSet<>(3);
            WL: while(res.size()<sizeIncrementFrameNumber && ta!=null) {
                if (ta.next==null) logger.error("Prev's NEXT NULL ta: {}: prev: {}, parent th: {}", this, this.prev, parents.get(0));
                if (!ta.errorCur && !ta.truncatedDivision && !ta.touchEndOfChannel && !ta.next.touchEndOfChannel) {
                    if (ta.division) {
                        double nextSize = 0;
                        bucket.clear();
                        Set<TrackAttribute> n = ta.getNext(bucket);
                        if (n.size()>1) {
                            //boolean touch = false;
                            for (TrackAttribute t : n) {
                                nextSize+=t.getSize();
                                //if (t.touchEndOfChannel) touch=true;
                            }
                            res.add(nextSize/ta.getSize()); //if (!touch) 
                        }
                        //if (debug) logger.debug("division: {}, next: {}, nextSize: {}", ta, n, nextSize);
                    } else res.add(ta.next.getSize()/ta.getSize()); 
                }
                ta = ta.prev;
            }
            return res;
        }
        private List<TrackAttribute> getPreviousTrack(int sizeLimit, boolean reverseOrder) {
            List<TrackAttribute> track = new ArrayList<>();
            track.add(this);
            TrackAttribute p = this.prev;
            while(p!=null && track.size()<sizeLimit) {
                track.add(p);
                p=p.prev;
            }
            if (!reverseOrder) Collections.reverse(track);
            return track;
        }
        private List<TrackAttribute> getTrackToPreviousDivision(boolean reverseOrder, List<TrackAttribute> bucket) {
            if (bucket==null) bucket = new ArrayList<>();
            bucket.add(this);
            TrackAttribute p = this.prev;
            while(p!=null && !p.division) {
                bucket.add(p);
                p=p.prev;
            }
            if (!reverseOrder) Collections.reverse(bucket);
            return bucket;
        }
        /*private double getLineageSizeProportionFromPreviousDivision(List<TrackAttribute> bucket) {
            // get previous track until previous parent
            if (bucket==null) bucket=new ArrayList<>();
            List<TrackAttribute> track = getTrackToPreviousDivision(false, bucket);
            if (track.isEmpty() || !track.get(0).division) return Double.NaN;
            List<TrackAttribute> siblings = new ArrayList<>(3);
            List<TrackAttribute> n = div.getNext(siblings);
        }
        private Map<Integer, Double> getLineagePreviousSizes() { // frame -> size
            List<TrackAttribute> track = getPreviousTrack(sizeIncrementFrameNumber, true); // starts with current element
            Map<Integer, Double> res=  new HashMap<>(sizeIncrementFrameNumber);
            
            if (!track.get(0).division) return null;
            Map<Integer, Double> siblingsSize=  new HashMap<>(sizeIncrementFrameNumber);
            
            TrackAttribute ta = this.prev;
            List<TrackAttribute> bucket = new ArrayList<>(3);
            WL: while(res.size()<sizeIncrementFrameNumber && ta!=null) {
                if (!ta.errorCur && !ta.truncatedDivision) {
                    if (ta.next==null) logger.error("Prev's NEXT NULL ta: {}: prev: {}, parent th: {}", this, this.prev, parents.get(0));
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
        }*/
        
        public double getLineageSizeIncrement() {
            List<Double> list = getLineageSizeIncrementList();
            if (list.isEmpty()) return Double.NaN;
            double res = ArrayUtil.median(list);
            if (res<minGR) res = minGR;
            else if (res>maxGR) res = maxGR;
            //if (debug) logger.debug("getSizeIncrement for {}-{}: {} list:{}", timePoint, idx, res, list);
            return res;
        }
        
        /*public double[] getLineageSizeRangeAndEstimation() {
            
        }*/
        
        public Set<TrackAttribute> getNext(Set<TrackAttribute> res) {
            if (trackAttributes.length-1<=timePoint) return res!=null ? res : Collections.EMPTY_SET;
            if (division) {
                if (res==null) res = new HashSet<>();
                for (TrackAttribute t : getAttributes(timePoint+1)) if (t.prev==this) res.add(t);
                return res;
            } else if (next!=null) {
                if (res==null) res = new HashSet<>();
                res.add(next);
                return res;
            } else return res!=null ? res : Collections.EMPTY_SET;
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
        double previousSizeIncrement = Double.NaN;
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
            //res.currentScore=currentScore; // do not duplicate scores & previous SI because duplicated objects are subect to modifications..
            //res.previousSizeIncrement=previousSizeIncrement;
            return res;
        }
        private void updateSizes() {
            size = 0;
            for (int i = idx; i<idxEnd; ++i) size+=getAttribute(timePoint, i).getSize();
            sizePrev=0;
            for (int i = idxPrev; i<idxPrevEnd; ++i) sizePrev+=getAttribute(timePoint-1, i).getSize();
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
            previousSizeIncrement=Double.NaN;
            incrementIfNecessary();
            return true;
        }
        protected void incrementIfNecessary() {
            incrementUntilVerifyInequality();
            if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{} start increment: {}", verboseLevel, this);
            if (!verifyInequality()) return;
            boolean change = true;
            while (change) change = checkNextIncrement();
        }
        
        private boolean checkNextIncrement() {
            TrackAssigner nextSolution = duplicate().verboseLevel(verboseLevel+1);
            // get another solution that verifies inequality
            boolean incrementPrev;
            if (mode==AssignerMode.ADAPTATIVE && !Double.isNaN(getPreviousSizeIncrement(false))) incrementPrev = size/sizePrev>getPreviousSizeIncrement(false);
            else incrementPrev = sizePrev<size;
            if (incrementPrev) {
                if (!nextSolution.incrementPrev()) return false;
            } else {
                if (!nextSolution.increment()) return false;
            }
            nextSolution.incrementUntilVerifyInequality();
            if (!nextSolution.verifyInequality()) return false;
            if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{}, [{};{}]->[{};{}] next solution: [{};{}]->[{};{}], current score: {}, next score: {}", verboseLevel, idxPrev, idxPrevEnd-1, idx, idxEnd-1, nextSolution.idxPrev, nextSolution.idxPrevEnd-1, nextSolution.idx, nextSolution.idxEnd-1, getCurrentScore(), nextSolution.getCurrentScore());
            //if (debug && verboseLevel<verboseLevelLimit) logger.debug("current: {}, next: {}", this, nextSolution);
            // compare the current & new solution
            double[] newScore = nextSolution.getCurrentScore();
            newScore[1]+=SIIncreaseThld; // increment only if significative improvement
            if (compareScores(getCurrentScore(), newScore, mode!=AssignerMode.RANGE)<=0) return false;
            newScore[1]-=SIIncreaseThld;
            this.idxEnd=nextSolution.idxEnd;
            this.idxPrevEnd=nextSolution.idxPrevEnd;
            this.size=nextSolution.size;
            this.sizePrev=nextSolution.sizePrev;
            this.currentScore=nextSolution.currentScore;
            this.previousSizeIncrement=nextSolution.previousSizeIncrement;
            return true;
        }
        
        
        protected void incrementUntilVerifyInequality() {
            previousSizeIncrement=Double.NaN;
            currentScore=null;
            boolean change = false;
            while(!verifyInequality()) {
                change = false;
                if (sizePrev * maxGR < size) {
                    if (!incrementPrev()) return;
                    else change = true;
                } else if (sizePrev * minGR > size) {
                    if (!increment()) return;
                    else change = true;
                } else if (!change) return;
            }
        }
        private boolean incrementPrev() {
            if (idxPrevEnd<idxPrevLim) {
                sizePrev+=getSize(timePoint-1, idxPrevEnd);
                ++idxPrevEnd;
                currentScore=null;
                previousSizeIncrement=Double.NaN;
                return true;
            } else return false;
        }
        private boolean increment() {
            if (idxEnd<idxLim) {
                size+=getSize(timePoint, idxEnd);
                ++idxEnd;
                currentScore=null;
                return true;
            } else return false;
        }
        private double[] getCurrentScore() {
            if (currentScore==null) {
                TrackAssigner currenScenario = duplicate().verboseLevel(verboseLevel+1).setMode(mode);
                currentScore = currenScenario.getAssignmentScoreForWholeScenario(idxPrevLim, idxLim);
            } return currentScore;
        }
        
        protected double getPreviousSizeIncrement(boolean returnDefault) {
            if (Double.isNaN(previousSizeIncrement)) {
                previousSizeIncrement = getAttribute(timePoint-1, idxPrev).getLineageSizeIncrement();
                if (Double.isNaN(previousSizeIncrement)) {
                    if (returnDefault) return defaultSizeIncrement();
                    else return Double.NaN;
                }
                if (idxPrevEnd-idxPrev>1) {
                    double totalSize=getAttribute(timePoint-1, idxPrev).getSize();
                    previousSizeIncrement *= getAttribute(timePoint-1, idxPrev).getSize();
                    for (int i = idxPrev+1; i<idxPrevEnd; ++i) { // size-weighted barycenter of size increment lineage
                        double curSI = getAttribute(timePoint-1, i).getLineageSizeIncrement();
                        if (!Double.isNaN(curSI)) {
                            previousSizeIncrement+= curSI * getAttribute(timePoint-1, i).getSize();
                            totalSize += getAttribute(timePoint-1, i).getSize();
                        }
                    }
                    previousSizeIncrement/=totalSize;
                }
            }
            return previousSizeIncrement;
            
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
            return Double.isFinite(new MergeScenario(BacteriaClosedMicrochannelTrackerLocalCorrections.this , i, iLim-1, tp).cost);
        }
        private int compareScores(double[] s1, double[] s2, boolean useScore) { // 0 = nb errors / 1 = score value. return -1 if first is better
            if (!useScore) return Double.compare(s1[0], s2[0]);
            else {
                if (s1[0]<s2[0]) return -1;
                else if (s1[0]>s2[0]) return 1;
                else if (s1[1]<s2[1]) return -1;
                else if (s1[1]>s2[1]) return 1;
                else return 0;
            }
        }
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
                if (debug && verboseLevel<verboseLevelLimit) logger.debug("{}, sizeIncrementError check: SI:{} lineage SI: {}, error: {}", this, sizeIncrement, prevSizeIncrement, Math.abs(prevSizeIncrement-sizeIncrement));
                return Math.abs(prevSizeIncrement-sizeIncrement)>significativeSIErrorThld;
            }
            } else return !verifyInequality();
        }
        public boolean significantSizeIncrementError() {
            return significantSizeIncrementError(idxPrev, size, sizePrev);
        }
        public boolean truncatedEndOfChannel() {
            return (idxEnd==idxLim   && 
                    (mode==AssignerMode.ADAPTATIVE && !Double.isNaN(getPreviousSizeIncrement(false)) ? getPreviousSizeIncrement(false)-size/sizePrev>significativeSIErrorThld : sizePrev * minGR > size) ); //&& idxEnd-idx==1 // && idxPrevEnd-idxPrev==1
        }
        public boolean needCorrection() {
            return (idxPrevEnd-idxPrev)>1; //|| (sizePrev * maxGR < size); et supprimer @ increment.. 
        }
        public boolean canBeCorrected() {
            return needCorrection();// && (idxEnd-idx==1) ;
        }
        public void resetTrackAttributes() {
            if (trackAttributes[timePoint]!=null) for (TrackAttribute ta : trackAttributes[timePoint]) ta.resetTrackAttributes(true, false);
            if (trackAttributes[timePoint-1]!=null) for (TrackAttribute ta : trackAttributes[timePoint-1]) ta.resetTrackAttributes(false, true);
        }
        
        public void assignCurrent(boolean noticeAll) {
            noticeAll=true;
            
            int nPrev = idxPrevEnd-idxPrev;
            int nCur = idxEnd-idx;
            boolean error = nPrev>1 || nCur>2;
            if (nPrev==1 && nCur==1) {
                TrackAttribute taCur = getAttribute(timePoint, idx);
                TrackAttribute taPrev = getAttribute(timePoint-1, idxPrev);
                taCur.prev=taPrev;
                taPrev.next=taCur;
                taCur.trackHead=false;
                taCur.errorPrev=error;
                taPrev.errorCur=error;
                taCur.nPrev=nPrev;
                if (noticeAll) {
                    taPrev.truncatedDivision = truncatedEndOfChannel();
                    if (!taPrev.truncatedDivision) {
                        taCur.sizeIncrementError = significantSizeIncrementError();
                        taCur.sizeIncrement=taCur.getSize()/taPrev.getSize();
                    } else taCur.sizeIncrement=Double.NaN;
                    if (setSIErrorsAsErrors && taCur.sizeIncrementError && !taCur.errorPrev) taCur.errorPrev=true;
                    if (setSIErrorsAsErrors && taCur.sizeIncrementError && !taPrev.errorCur) taPrev.errorCur=true;
                                        
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
                        taPrev.truncatedDivision = truncatedEndOfChannel();
                        if (!taPrev.truncatedDivision) {
                            ta.sizeIncrementError = significantSizeIncrementError();
                            ta.sizeIncrement = size / sizePrev;
                        } else ta.sizeIncrement=Double.NaN;
                        if (setSIErrorsAsErrors && ta.sizeIncrementError && !ta.errorPrev) ta.errorPrev=true;
                        if (setSIErrorsAsErrors && ta.sizeIncrementError && !taPrev.errorCur) taPrev.errorCur=true;
                    }
                }
            } else if (nPrev>1 && nCur==1) { // merging
                TrackAttribute taCur = getAttribute(timePoint, idx);
                taCur.trackHead=false;
                taCur.prev=getAttribute(timePoint-1, idxPrev);
                taCur.errorPrev=true;
                taCur.nPrev=nPrev;
                boolean truncated = truncatedEndOfChannel();
                if (noticeAll) {
                    if (!truncated) {
                        taCur.sizeIncrementError = significantSizeIncrementError();
                        taCur.sizeIncrement=size/sizePrev;
                    } else taCur.sizeIncrement=Double.NaN;
                    
                }
                for (int i = idxPrev; i<idxPrevEnd; ++i) {
                    TrackAttribute ta = getAttribute(timePoint-1, i);
                    ta.next=taCur;
                    ta.errorCur=true;
                }
            } else if (nPrev>1 && nCur>1) { // algorithm assign first with first (or 2 first) or last with last (or 2 last) (the most likely) and recursive call. If last window -> do not consider 
                TrackAssigner dup = duplicate(); // recursive algo. do not modify current trackAssigner!
                TrackAssigner currentAssigner = duplicate();
                TrackAttribute taCur1 = getAttribute(timePoint, idx);
                TrackAttribute taCur2 = getAttribute(timePoint, idx+1);
                TrackAttribute taPrev1 = getAttribute(timePoint-1, idxPrev);
                double sizeIncrement1 = taPrev1.getLineageSizeIncrement();
                if (Double.isNaN(sizeIncrement1)) sizeIncrement1 = defaultSizeIncrement();
                double score1 = Math.abs(taCur1.getSize()/taPrev1.getSize()-sizeIncrement1);
                double scoreDiv = Math.abs((taCur1.getSize()+taCur2.getSize())/taPrev1.getSize()-sizeIncrement1);
                
                boolean endOfChannel = idxPrevEnd==idxPrevLim; // idxEnd==idxLim ||  // if end of channel : assignement only from start 
                TrackAttribute taCurEnd1, taCurEnd2, taPrevEnd;
                double scoreEnd1=Double.POSITIVE_INFINITY, scoreEndDiv=Double.POSITIVE_INFINITY;
                if (!endOfChannel) {
                    taCurEnd1 = getAttribute(timePoint, idxEnd-1);
                    taCurEnd2 = getAttribute(timePoint, idxEnd-2);
                    taPrevEnd = getAttribute(timePoint-1, idxPrevEnd-1);
                    double sizeIncrementEnd = endOfChannel ? 0 :  taPrevEnd.getLineageSizeIncrement();
                    if (Double.isNaN(sizeIncrementEnd)) sizeIncrementEnd = defaultSizeIncrement();
                    scoreEnd1 = Math.abs(taCurEnd1.getSize()/taPrevEnd.getSize()-sizeIncrementEnd);
                    scoreEndDiv = Math.abs((taCurEnd1.getSize()+taCurEnd2.getSize())/taPrevEnd.getSize()-sizeIncrementEnd);
                }
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
                dup.updateSizes();
                currentAssigner.updateSizes();
                if (debug && verboseLevel<verboseLevelLimit) logger.debug("assignment {} with {} objects, assign {}, div:{}", nPrev, nCur, (score==score1||score==scoreDiv) ? "first" : "last", (score==scoreDiv||score==scoreEndDiv));
                currentAssigner.assignCurrent(noticeAll); // perform current assignement
                dup.assignCurrent(noticeAll); // recursive call
            } else if (nPrev==1 && nCur==0) { // end of lineage
                TrackAttribute ta = getAttribute(timePoint-1, idxPrev);
                ta.resetTrackAttributes(false, true);
                if (idxPrev<idxPrevLim-1 && ta.getLineageSizeIncrement()>SIQuiescentThld) ta.errorCur=true; // no error if cell is not growing
            } else if (nCur==1 && nPrev==0) {
                TrackAttribute ta = getAttribute(timePoint, idx);
                ta.resetTrackAttributes(true, false);
            }
        }
        
        /**
         * 
         * @return minimal/maximal+1 timePoint where correction has been performed
         */
        public int[] performCorrection() {
            if (debugCorr && verboseLevel<verboseLevelLimit) logger.debug("t: {}: performing correction, idxPrev: {}, idxPrevEnd: {}", timePoint, idxPrev, idxPrevEnd);
            if (idxEnd-idx==1) return performCorrectionSplitOrMergeOverMultipleTime();
            else return performCorrectionMultipleObjects();
            //else return null;
        }
        private int[] performCorrectionSplitOrMergeOverMultipleTime() {
            MergeScenario m = new MergeScenario(BacteriaClosedMicrochannelTrackerLocalCorrections.this, idxPrev, idxPrevEnd-1, timePoint-1);
            //if (Double.isInfinite(m.cost)) return -1; //cannot merge
            List<CorrectionScenario> merge = m.getWholeScenario(maxCorrectionLength, costLim, cumCostLim); // merge scenario
            double[] mergeCost = new double[]{Double.POSITIVE_INFINITY, 0}; for (CorrectionScenario c : merge) mergeCost[1]+=c.cost; if (merge.isEmpty()) mergeCost[1]=Double.POSITIVE_INFINITY;
            SplitScenario ss = new SplitScenario(BacteriaClosedMicrochannelTrackerLocalCorrections.this, getAttribute(timePoint, idx), timePoint);
            for (int i = idx+1; i<idxEnd; ++i) {
                SplitScenario sss = new SplitScenario(BacteriaClosedMicrochannelTrackerLocalCorrections.this, getAttribute(timePoint, i), timePoint);
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
                if (mergeCost[0]==currentErrors && mergeCost[1]>0) return null; // if same number of errors and cost >0 do not apply
                saveMerge.restoreAll();
                if (debugCorr && verboseLevel<verboseLevelLimit) logger.debug("apply merge scenario!");
                return new int[]{Math.max(1, timePoint-merge.size()), Math.min(populations.length-1, timePoint+1)};
            } else if (saveSplit!=null && splitCost[0]<=currentErrors && compareScores(mergeCost, splitCost, true)>=0 ) {
                if (splitCost[0]==currentErrors && splitCost[1]>0) return null; // if same number of errors and cost >0 do not apply
                if (debugCorr && verboseLevel<verboseLevelLimit) logger.debug("apply split scenario!");
                saveSplit.restoreAll();
                return new int[]{timePoint, Math.min(populations.length-1, timePoint+split.size()+1)};
            } else return null;
        }
        public int getErrorCount() {
            int res =  Math.max(0, idxEnd-idx-2) + // division in more than 2
                    idxPrevEnd-idxPrev-1; // merging
            //if ((!verifyInequality() || significantSizeIncrementError()) && !truncatedEndOfChannel()) ++res; // bad size increment
            if (!truncatedEndOfChannel()) {
                if (!verifyInequality()) res+=1;
                else if (significantSizeIncrementError()) res+=SIErrorValue;//res+=0.9;
            }
            return res;        
        }
        private int[] performCorrectionMultipleObjects() {
            if (debugCorr) logger.debug("performing correction multiple objects: {}", this);
            
            // Todo : rearrange objects from next au lieu de toutes les combinaisons de merge..
            List<CorrectionScenario> scenarios = new ArrayList<>();
            for (int iMerge = idxPrev; iMerge+1<idxPrevEnd; ++iMerge) scenarios.add(new MergeScenario(BacteriaClosedMicrochannelTrackerLocalCorrections.this, iMerge, iMerge+1, timePoint-1)); // merge one @ previous
            if (idxPrevEnd-idxPrev>2 && idxEnd-idx<=2) scenarios.add(new MergeScenario(BacteriaClosedMicrochannelTrackerLocalCorrections.this, idxPrev, idxPrevEnd-1, timePoint-1)); // merge all previous objects
            
            scenarios.add(new RearrangeObjectsFromPrev(BacteriaClosedMicrochannelTrackerLocalCorrections.this, timePoint, idx, idxEnd-1, idxPrev, idxPrevEnd-1)); // TODO: TEST INSTEAD OF SPLIT / SPLITANDMERGE
            
            scenarios.removeIf(c -> c.cost>costLim);
            if (scenarios.isEmpty()) return null;
            // try all scenarios and check error number
            double currentErrors = getErrorNumber(timePoint-1, timePoint+1, false);
            ObjectAndAttributeSave saveCur = new ObjectAndAttributeSave(timePoint-1, timePoint);
            final Map<CorrectionScenario, ObjectAndAttributeSave> saveMap = new HashMap<>(scenarios.size());
            final Map<CorrectionScenario, Integer> errorMap = new HashMap<>(scenarios.size());

            for (CorrectionScenario c : scenarios) {
                c.applyScenario();
                errorMap.put(c, getErrorNumber(timePoint-1, timePoint+1, true));
                saveMap.put(c, new ObjectAndAttributeSave(c.timePointMin, c.timePointMax));
                if (correctionStep) step("step:"+step+"/"+c, false);
                if (debugCorr && verboseLevel<verboseLevelLimit) logger.debug("correction multiple: errors current: {}, scenario: {}:  errors: {}, cost: {}",currentErrors, c, errorMap.get(c), c.cost);
                saveCur.restore(c.timePointMin, c.timePointMax);
                for (int t = Math.max(1, c.timePointMin); t<=Math.min(c.timePointMax+1, populations.length-1); ++t) assignPrevious(t, false , false);
            }
            CorrectionScenario best = Collections.min(scenarios, (CorrectionScenario o1, CorrectionScenario o2) -> {
                int comp = Integer.compare(errorMap.get(o1), errorMap.get(o2));
                if (comp==0) comp = Double.compare(o1.cost, o2.cost);
                return comp;
            });
            if (errorMap.get(best)<currentErrors || (best.cost==0 && errorMap.get(best)==currentErrors)) {
                saveMap.get(best).restoreAll();
                return new int[]{Math.max(1, best.timePointMin), Math.min(best.timePointMax+1, populations.length-1)};
            }
            
            return null;
        }
        
        @Override public String toString() {
            return "L:"+verboseLevel+ "/t:"+timePoint+ " ["+idxPrev+";"+(idxPrevEnd-1)+"]->["+idx+";"+(idxEnd-1)+"] Size: "+sizePrev+ "->"+size+ "/nPrev: "+(idxPrevEnd-idxPrev)+"/nCur: "+(idxEnd-idx)+"/inequality: "+verifyInequality()+ "/errors: "+getErrorCount()+"/SI:"+size/sizePrev+"/SIprev:"+getPreviousSizeIncrement(false);
        }
    }

    protected class ObjectAndAttributeSave {
        final List<Object3D>[] objects;
        final List<TrackAttribute>[] ta;
        final int tpMin;
        protected ObjectAndAttributeSave(int tpMin, int tpMax) {
            if (tpMin<0) tpMin = 0;
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
    
}
