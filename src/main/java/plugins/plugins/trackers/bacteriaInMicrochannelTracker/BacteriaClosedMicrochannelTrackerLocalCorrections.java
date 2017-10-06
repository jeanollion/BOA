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

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.ChoiceParameter;
import configuration.parameters.ConditionalParameter;
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
import dataStructure.objects.StructureObjectUtils;
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
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.Function;
import measurement.GeometricalMeasurements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import plugins.MultiThreaded;
import plugins.Segmenter;
import plugins.SegmenterSplitAndMerge;
import plugins.Tracker;
import plugins.TrackerSegmenter;
import plugins.OverridableThreshold;
import plugins.ParameterSetup;
import plugins.ParameterSetupTracker;
import plugins.plugins.processingScheme.SegmentOnly;
import plugins.plugins.segmenters.BacteriaTrans;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.TrackAssigner.compareScores;
import utils.ArrayUtil;
import utils.Pair;
import utils.ThreadRunner;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class BacteriaClosedMicrochannelTrackerLocalCorrections implements TrackerSegmenter, MultiThreaded, ParameterSetupTracker {
    public final static Logger logger = LoggerFactory.getLogger(BacteriaClosedMicrochannelTrackerLocalCorrections.class);
    // parametrization-related attributes
    protected PluginParameter<SegmenterSplitAndMerge> segmenter = new PluginParameter<>("Segmentation algorithm", SegmenterSplitAndMerge.class, false);
    BoundedNumberParameter maxGrowthRate = new BoundedNumberParameter("Maximum Size Increment", 2, 1.5, 1, null);
    BoundedNumberParameter minGrowthRate = new BoundedNumberParameter("Minimum size increment", 2, 0.85, 0.01, null); // augmenter à 0.9 ?
    //BoundedNumberParameter divisionCriterion = new BoundedNumberParameter("Division Criterion", 2, 0.80, 0.01, 1);
    BoundedNumberParameter costLimit = new BoundedNumberParameter("Correction: operation cost limit", 3, 1.5, 0, null);
    BoundedNumberParameter cumCostLimit = new BoundedNumberParameter("Correction: cumulative cost limit", 3, 5, 0, null);
    BoundedNumberParameter endOfChannelContactThreshold = new BoundedNumberParameter("End of channel contact Threshold", 2, 0.45, 0, 1);
    
    ChoiceParameter thresholdMethod = new ChoiceParameter("Threshold method", new String[]{"From Segmenter", "Local Contrast Adaptative By Frame", "Local Contrast Adaptative By Frame and Y", "Autothreshold", "Autothreshold Adaptative by Frame"}, "From Segmenter", false);
    BoundedNumberParameter adaptativeCoefficient = new BoundedNumberParameter("Adaptative coefficient", 2, 1, 0, 1);
    BoundedNumberParameter frameHalfWindow = new BoundedNumberParameter("Adaptative by Frame: half-window", 1, 25, 1, null);
    BoundedNumberParameter yHalfWindow = new BoundedNumberParameter("Adaptative by Y: half-window", 1, 15, 10, null);
    BoundedNumberParameter contrastThreshold = new BoundedNumberParameter("Contrast Threshold", 3, 0.05, 0.01, 0.2);
    ChoiceParameter autothresholdMethod = new ChoiceParameter("Method", AutoThresholder.getMethods(), AutoThresholder.Method.Otsu.toString(), false);
    ConditionalParameter thresholdCond = new ConditionalParameter(thresholdMethod);
    
    Parameter[] parameters = new Parameter[]{segmenter, minGrowthRate, maxGrowthRate, costLimit, cumCostLimit, endOfChannelContactThreshold, thresholdCond};

    ExecutorService executor;
    @Override
    public void setExecutor(ExecutorService executor) {
        this.executor=executor;
    }
    
    @Override public SegmenterSplitAndMerge getSegmenter() {
        SegmenterSplitAndMerge s= segmenter.instanciatePlugin();
        if (s instanceof OverridableThreshold) ((OverridableThreshold)s).setThresholdValue(threshold!= null ? threshold.getThreshold(): debugThreshold);
        return s;
    }

    protected SegmenterSplitAndMerge getSegmenter(int frame, boolean setMask) {
        SegmenterSplitAndMerge s= segmenter.instanciatePlugin();
        if (setMask && applyToSegmenter!=null) applyToSegmenter.apply(frame, s);
        return s;
    }

    @Override
    public boolean canBeTested(String p) {
        if (adaptativeCoefficient.getName().equals(p) || frameHalfWindow.getName().equals(p) || yHalfWindow.getName().equals(p) || contrastThreshold.getName().equals(p)) {
            return getSegmenter() instanceof OverridableThreshold;
        }
        return segmenter.getName().equals(p) || maxGrowthRate.getName().equals(p)|| minGrowthRate.getName().equals(p)|| costLimit.getName().equals(p)|| cumCostLimit.getName().equals(p)|| endOfChannelContactThreshold.getName().equals(p);
    }

    String testParameterName;
    @Override
    public void setTestParameter(String p) {
        this.testParameterName=p;
    }
    @Override 
    public boolean runSegmentAndTrack(String p) {
        return adaptativeCoefficient.getName().equals(p) || frameHalfWindow.getName().equals(p) || yHalfWindow.getName().equals(p) || contrastThreshold.getName().equals(p) || segmenter.getName().equals(p);
    }
    
    // tracking-related attributes
    protected enum Flag {error, correctionMerge, correctionSplit;}
    HashMap<Integer, List<Object3D>> populations;
    HashMap<Object3D, TrackAttribute> objectAttributeMap;
    //SegmenterSplitAndMerge[] segmenters;
    private boolean segment, correction;
    HashMap<Integer, Image> inputImages;
    Map<Integer, StructureObject> parentsByF;
    int structureIdx;
    int minT, maxT;
    double maxGR, minGR, costLim, cumCostLim;
    double[] baseGrowthRate;
    PreFilterSequence preFilters; 
    PostFilterSequence postFilters;
    Threshold threshold;
    
    static int loopLimit=3;
    public static boolean debug=false, debugCorr=false;
    public static double debugThreshold = Double.NaN;
    public static int verboseLevelLimit=1;
    
    // hidden parameters! 
    // sizeIncrement -> adaptative SI
    final static int sizeIncrementFrameNumber = 7; // number of frames for sizeIncrement computation
    final static double significativeSIErrorThld = 0.3; // size increment difference > to this value lead to an error
    final static double SIErrorValue=1; //0.9 -> less weight to sizeIncrement error / 1 -> same weight
    final static double SIIncreaseThld = 0.1;
    final static double SIQuiescentThld = 1.05; // under this value we consider cells are not growing -> if break in lineage no error count (cell dies)
    final static boolean setSIErrorsAsErrors = false; // SI errors are set as tracking errors in object attributes
    final static int correctionLevelLimit = 7;
    final static int cellNumberLimitForAssignment = 7;
    final static int maxCorrectionLength = 1000;
    private static final double beheadedCellsSizeLimit = 300;
    final static boolean useVolumeAsSize=false; // if length -> length should be re-computed for merged objects and not just summed
    // functions for assigners
    
    Function<Object3D, Double> sizeFunction; 
    Function<Object3D, Double> sizeIncrementFunction;
    BiFunction<Object3D, Object3D, Boolean> areFromSameLine;
    
    
    public BacteriaClosedMicrochannelTrackerLocalCorrections() {
        thresholdCond.setActionParameters("Local Contrast Adaptative By Frame", new Parameter[]{contrastThreshold, adaptativeCoefficient, frameHalfWindow});
        thresholdCond.setActionParameters("Local Contrast Adaptative By Frame and Y", new Parameter[]{contrastThreshold, adaptativeCoefficient, frameHalfWindow, yHalfWindow});
        thresholdCond.setActionParameters("Autothreshold", new Parameter[]{autothresholdMethod});
        thresholdCond.setActionParameters("Autothreshold Adaptative by Frame", new Parameter[]{autothresholdMethod, adaptativeCoefficient, frameHalfWindow});
    }
    
    public BacteriaClosedMicrochannelTrackerLocalCorrections setSegmenter(SegmenterSplitAndMerge seg) {
        this.segmenter.setPlugin(seg);
        return this;
    }
    
    public BacteriaClosedMicrochannelTrackerLocalCorrections setCostParameters(double operationCostLimit, double cumulativeCostLimit) {
        this.costLimit.setValue(operationCostLimit);
        this.cumCostLimit.setValue(cumulativeCostLimit);
        return this;
    }
    public BacteriaClosedMicrochannelTrackerLocalCorrections setThresholdParameters(int method, double adaptativeCoefficient, int frameHalfWindow, int yHalfWindow) {
        this.thresholdMethod.setSelectedIndex(method);
        this.adaptativeCoefficient.setValue(adaptativeCoefficient);
        this.frameHalfWindow.setValue(frameHalfWindow);
        this.yHalfWindow.setValue(yHalfWindow);
        return this;
    }
    
    @Override public Parameter[] getParameters() {
        return parameters;
    }
    
    @Override public void track(int structureIdx, List<StructureObject> parentTrack) {
        init(parentTrack, structureIdx, false);
        Collections.sort(parentTrack, (o1, o2)->Integer.compare(o1.getFrame(), o2.getFrame()));
        for (StructureObject p : parentTrack) getObjects(p.getFrame());
        for (StructureObject p : parentTrack)  setAssignmentToTrackAttributes(p.getFrame(), true);
        applyLinksToParents(parentTrack);
    }

    @Override public void segmentAndTrack(int structureIdx, List<StructureObject> parentTrack, PreFilterSequence preFilters, PostFilterSequence postFilters) {
        this.preFilters=preFilters;
        this.postFilters=postFilters;
        init(parentTrack, structureIdx, true);
        segmentAndTrack(true);
    }

    public static boolean correctionStep;
    public static Map<String, List<StructureObject>> stepParents;
    private static int step = 0;
    
    
    @FunctionalInterface public static interface ApplyToSegmenter { public void apply(int frame, Segmenter segmenter);}
    ApplyToSegmenter applyToSegmenter;
    
    protected void segmentAndTrack(boolean performCorrection) {
        if (performCorrection && (correctionStep)) stepParents = new LinkedHashMap<>();
        this.correction=performCorrection;
        maxT = Collections.max(parentsByF.keySet())+1;
        minT = Collections.min(parentsByF.keySet());
        if (debug) logger.debug("minF: {}, maxF: {}", minT, maxT);
        if (correction) inputImages=new HashMap<>(parentsByF.size());
        // 0) optional compute threshold for all images
        SegmentOnly so = new SegmentOnly(segmenter.instanciatePlugin()).setPostFilters(postFilters).setPreFilters(preFilters);
        applyToSegmenter = (frame, s) -> {
            if (s instanceof OverridableThreshold) {
                if (threshold!=null) {
                    if (threshold.hasAdaptativeByY()) {
                        ((OverridableThreshold)s).setThresholdedImage(threshold.getThresholdedPlane(frame, false));
                        //if (frame==300) ImageWindowManagerFactory.showImage(threshold.getThresholdedPlane(frame, false));
                    }
                    else ((OverridableThreshold)s).setThresholdValue(threshold.getThreshold(frame));
                } else ((OverridableThreshold)s).setThresholdValue(debugThreshold);
            }
        };
        if (Double.isNaN(debugThreshold) && this.thresholdMethod.getSelectedIndex()>0 && getSegmenter() instanceof OverridableThreshold) {
            List<Image> planes = new ArrayList<>(parentsByF.size());
            for (int t = minT; t<maxT; ++t) planes.add(((OverridableThreshold)getSegmenter()).getThresholdImage(getImage(t), structureIdx, getParent(t, true)));
            logger.debug("threshold method: {}. Adaptative coeff: {}, hwf: {}, hwy: {}", this.thresholdMethod.getSelectedItem(), adaptativeCoefficient.getValue().doubleValue(), frameHalfWindow.getValue().intValue(), yHalfWindow.getValue().intValue());
            //logger.debug("minF: [{}-{}], nb planes: {}, nbParents: {}", minT, maxT, planes.size(), this.parentsByF.size());
            int method = thresholdMethod.getSelectedIndex();
            if (method==1 || method==2) {
                threshold = new ThresholdLocalContrast(planes, minT, contrastThreshold.getValue().doubleValue()); // TODO TEST THRESHOLD CLASS: OFFSET HAS BEEN ADDED
            } else {
                if (debug || debugCorr) logger.debug("threshold method: {}", autothresholdMethod.getSelectedItem() );
                threshold = new ThresholdHisto(planes, minT, true, AutoThresholder.Method.valueOf(autothresholdMethod.getSelectedItem()));
            }
            if (method==1 || method==2 || method==3 || method==4) {
                threshold.setAdaptativeThreshold(adaptativeCoefficient.getValue().doubleValue(), frameHalfWindow.getValue().intValue()); 
            }
            
            int[] fr = getFrameRangeContainingCells(so); // will use the adaptative threshold by Frame because global threshold not relevant for cell range computation if some channel are void
            if (fr!=null) {
                threshold.setFrameRange(fr);
                if (method==3) ((ThresholdHisto)threshold).unsetAdaptativeByF();
            }
            if (debug || debugCorr) {
                logger.debug("frame range: {}", fr);
                for (int i = fr[0]; i<fr[1]; i+=100) logger.debug("thld={} F={}", threshold.getThreshold(i), i);
            }
            if (fr !=null && thresholdMethod.getSelectedIndex()==2) { // adaptative by F & Y
                ((ThresholdLocalContrast)threshold).setAdaptativeByFY(frameHalfWindow.getValue().intValue(), yHalfWindow.getValue().intValue()); // TODO parametrer!
            }            
            threshold.freeMemory();
        } else if (!Double.isNaN(debugThreshold)) {
            logger.debug("Threshold used: {}", debugThreshold);
        }
        
        
        //if (true) return;
        // 1) segment. Limit to first continuous segment of cells
        if (true && threshold!=null && threshold.getFrameRange()!=null) {
            maxT = threshold.getFrameRange()[1]+1;
            minT = threshold.getFrameRange()[0];
        }
        //logger.debug("minT: {}, maxT: {}", minT, maxT);
        // get All Objects
        
        List<StructureObject> subParentTrack = new ArrayList<>(parentsByF.values());
        subParentTrack.removeIf(o->o.getFrame()<minT||o.getFrame()>=maxT);
        Collections.sort(subParentTrack, (o1, o2)->Integer.compare(o1.getFrame(), o2.getFrame()));
        List<Pair<String, Exception>> l = so.segmentAndTrack(structureIdx, subParentTrack, executor, (o, s) -> {applyToSegmenter.apply(o.getFrame(), s);});
        for (Pair<String, Exception> p : l) logger.debug(p.key, p.value);
        //for (StructureObject p : parents.subList(minT, maxT)) populations.get(p.getFrame()) = Utils.transform(p.getChildren(structureIdx), o->o.getObject());
        // trim empty frames
        while (minT<maxT && getObjects(minT).isEmpty()) ++minT;
        while (maxT>minT && getObjects(maxT-1).isEmpty()) --maxT;
        //logger.debug("after seg: minT: {}, maxT: {}", minT, maxT);
        if (maxT-minT<=1) {
            for (StructureObject p : parentsByF.values()) p.setChildren(null, structureIdx);
            return;
        } //else for (int t = maxT; t<parents.size(); ++t) if (populations.get(t]!=null) populations.get(t].clear();
        if (debugCorr||debug) logger.debug("Frame range: [{};{}]", minT, maxT);
        for (int f = minT; f<maxT; ++f) getObjects(f); // init
        if (debugCorr||debug) logger.debug("getObjects ok");
        if (correctionStep) step(null, true);
        
        //if (debugCorr) correction=false; // TO REMOVE
        
        // correct beheaded cells bias
        if (correction) {
            for (int t = minT+1; t<maxT; ++t) {
                /*if (getObjects(t).isEmpty()) { // would limit to first continuous segment
                    maxT=t;
                    if ((debug || debugCorr)) logger.debug("no objects @frame: {}, threhsold: {}", maxT, threshold!=null ? threshold.getThreshold(maxT) : debugThreshold);
                    break;
                }*/
                setAssignmentToTrackAttributes(t, true); // last assignment -> for beheaded cell correction
            }
            correctBeheadedCells();
            for (int t = minT+1; t<maxT; ++t) setAssignmentToTrackAttributes(t, false);
        }
        if (correctionStep) step("after correct beheaded cells", true);
        // 2) perform corrections idx-wise
        if (correction) {
            int idxMax=0;
            int idxLim = populations.get(minT).size();
            for (int t = minT+1; t<maxT; ++t) if (populations.get(t).size()>idxLim) idxLim=populations.get(t).size();
            idxLim = Math.min(correctionLevelLimit, idxLim);
            List<int[]> corrRanges = new ArrayList<>();
            List<int[]> corrRanges2 = new ArrayList<>(1);
            while(idxMax<idxLim) {
                boolean corr = performCorrectionsByIdx(minT+1, maxT-1, idxMax, corrRanges, false);
                if (corr && idxMax>0) { // corrections have been performed : run correction from 0 to idxMax within each time range// TODO: necessaire?
                    for (int[] tRange : corrRanges) {
                        int nLoop=1;
                        for (int idx = 0; idx<=idxMax; ++idx) {
                            boolean corr2 = performCorrectionsByIdx(tRange[0], tRange[1], idx, corrRanges2, true);
                            if (correctionStep && stepParents.size()>5) return;
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
                        for (int t = tRange[0]; t<=tRange[1]; ++t) {
                            if (populations.get(t).size()>idxLim) idxLim=populations.get(t).size();
                        }
                        idxLim = Math.min(correctionLevelLimit, idxLim);
                    }
                }
                idxMax++;
            }
            if (correctionStep) step("End of process", false);
        }
        // 3) final assignement without correction, noticing all errors
        for (int t = minT+1; t<maxT; ++t)  setAssignmentToTrackAttributes(t, true);
        List<StructureObject> parents = new ArrayList<>(parentsByF.values());
        Collections.sort(parents);
        applyLinksToParents(parents);
    }
    private boolean hasNoObjects(SegmentOnly so, int f) {
        if (!parentsByF.containsKey(f)) return true;
        List<StructureObject> before = parentsByF.get(f).getChildren(structureIdx);
        so.segmentAndTrack(structureIdx, new ArrayList<StructureObject>(1){{add(parentsByF.get(f));}}, executor, (o, s) -> {applyToSegmenter.apply(o.getFrame(), s);});
        List<StructureObject> after = parentsByF.get(f).getChildren(structureIdx);
        parentsByF.get(f).setChildren(before, structureIdx);
        return after==null || after.isEmpty();
    }
    private int[] getFrameRangeContainingCells(SegmentOnly so) {
        int inc = this.parentsByF.size()<100 ? 1 : 10;
        int minF = Collections.min(parentsByF.keySet());
        int maxF = Collections.max(parentsByF.keySet());
        final int minFF=minF;
        final int maxFF = maxF;
        while (minF<maxF && hasNoObjects(so, minF)) minF+=inc;
        if (minF>=maxF) {
            if (debug || debugCorr) logger.debug("getFrameRange: [{}-{}]", minF, maxF);
            return null;
        }
        if (inc>1) while (minF>minFF && !hasNoObjects(so, minF-1)) minF--; // backward 
        
        while (maxF>minF && hasNoObjects(so, maxF)) maxF-=inc;
        if (debug || debugCorr) logger.debug("getFrameRange: [{}-{}]", minF, maxF);
        if (maxF<=minF) return null;
        if (inc>1) while (maxF<maxFF-1 && !hasNoObjects(so, maxF+1)) maxF++; // forward 
        
        return new int[]{minF, maxF};
    }
    
    private boolean performCorrectionsByIdx(int tMin, int tMax, final int idx, List<int[]> outRanges, boolean limitToOneRange) {
        if (debugCorr) logger.debug("performing corrections [{};{}] @ {}", tMin, tMax, idx);
        int nLoop=0, rangeLoop=0;
        int currentT = tMin;
        outRanges.clear();
        int[] currentRange = null;
        while(currentT<=tMax) {
            int minT = currentT;
            boolean change = false;
            if (idx<populations.get(currentT).size()) {
                TrackAttribute ta = getAttribute(currentT, idx);
                TrackAttribute taPrev = idx<populations.get(currentT-1).size() ? getAttribute(currentT-1, idx) : null;
                CorLoop : while (((taPrev!=null && (taPrev.errorCur || taPrev.sizeIncrementError)) || ta.errorPrev || ta.sizeIncrementError) && nLoop<loopLimit) { // il y a une erreur à corriger
                    if (currentRange == null) currentRange = new int[]{tMax, tMin};
                    TrackAssigner assigner = getTrackAssigner(currentT);
                    //logger.debug("t:{}, idx:{}, ass: {}, can be corrected: {}, ta: {}",currentT, idx, assigner, assigner.canBeCorrected(), ta);
                    if (assigner.assignUntil(idx, false) && assigner.currentAssignment.canBeCorrected()) {
                        int[] corrT = performCorrection(assigner.currentAssignment, currentT);
                        if (corrT!=null) {
                            change = true;
                            minT = Math.min(minT, corrT[0]);
                            currentRange[0] = Math.min(currentRange[0], corrT[0]);
                            currentRange[1] = Math.max(currentRange[1], corrT[1]);
                            for (int t = corrT[0]; t<=corrT[1]; ++t)  setAssignmentToTrackAttributes(t, false);
                            if (correctionStep) {
                                step(null, true);
                            }
                            if (idx>=populations.get(currentT).size()) break CorLoop; // merge in correction -> idx is not reached anymore
                            ta = getAttribute(currentT, idx); // in case of correction, attributes may have changed
                            taPrev = idx<populations.get(currentT-1).size() ? getAttribute(currentT-1, idx) : null;
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
    
    protected void step(String name, boolean increment) {
        if (step>5) return;
        if (name==null) {
            if (increment) name = "End of Step: "+step;
            else name = "Step: "+step;
        }
        List<StructureObject> newParents = new ArrayList<>(parentsByF.size());
        for (StructureObject p : parentsByF.values()) newParents.add(p.duplicate(true));
        Collections.sort(newParents);
        StructureObjectUtils.setTrackLinks(newParents);
        stepParents.put(name, newParents);
        // perform assignment without corrections?
        for (int t = minT+1; t<maxT; ++t)  setAssignmentToTrackAttributes(t, false);
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
        for (StructureObject parent : parents) {
            int f = parent.getFrame();
            //logger.debug("setting objects from parent: {}, prevChildren null?", parent, childrenPrev==null);
            if (!segment) { // modifiy existing structureObjects
                children = parent.getChildren(structureIdx);
                if (children ==null || populations.get(f)==null) {}
                else if (children.size()!=populations.get(f).size()) logger.error("BCMTLC: error @ parent: {}, children and tracker objects differ in number", parent);
                else setAttributesToStructureObjects(f, children, childrenPrev);
            } else { // creates new structureObjects
                List<Object3D> cObjects;
                if (correctionStep) {
                    cObjects = new ArrayList<>(populations.get(f).size());
                    for (Object3D o : populations.get(f)) cObjects.add(o.duplicate());
                } else {
                    cObjects = populations.get(f);
                    //RemoveIncompleteDivisions(t);
                }
                children = parent.setChildrenObjects(new ObjectPopulation(cObjects, null), structureIdx); // will translate all voxels
                setAttributesToStructureObjects(f, children, childrenPrev);
                if (debug) for (StructureObject c : children) if (c.hasTrackLinkError(true, true)) ++errors;
            }
            childrenPrev=children;
        }
        if (debug) logger.debug("Errors: {}", errors);
    }
    
    private boolean correctBeheadedCells() {
        if (debugCorr) logger.debug("correcting beheaded cells");
        List<TrackAttribute> bucket = new ArrayList<>();
        Set<TrackAttribute> getBucket = new HashSet<>();
        boolean correctionsHaveBeenPerformed = false;
        Function<TrackAttribute, Boolean> isCandidate = ta -> ta.idx==0 && ta.o.getSize()<=beheadedCellsSizeLimit && 
                (populations.get(ta.timePoint).size()==1  // no other cell
                || ta.o.getSize() <  2 * populations.get(ta.timePoint).get(1).getSize())  // head < 1/2 of total size (1/3?)
                //|| (populations.get(ta.timePoint).size()>2 && populations.get(ta.timePoint).get(1).getSize()<2*beheadedCellsSizeLimit) // case of fractionated cells: follwing object is small -> will be able to merge several times
                ;
        for (int f = minT+1; f<maxT; ++f) {
            if(!populations.get(f).isEmpty() && isCandidate.apply(objectAttributeMap.get(populations.get(f).get(0)))) {
                boolean hasMerged = false;
                List<TrackAttribute> track = objectAttributeMap.get(populations.get(f).get(0)).getTrackToNextDivision(bucket, ta -> ta.errorCur ? 1 : 0);
                if (debugCorr) logger.debug("Merge beheaded cells candidate frames: [{}->{}], sizes: {}", track.get(0).timePoint, track.get(track.size()-1).timePoint, Utils.toStringList(track, ta -> ""+ta.o.getSize()));
                int trackSize = track.size();
                track.removeIf( ta -> !isCandidate.apply(ta));
                if (debugCorr) logger.debug("Merge beheaded cells: remove {} -> {}", trackSize, track.size());
                if (track.size()==trackSize) { // all objects verify beheaded conditions
                    TrackAttribute end = track.get(track.size()-1);
                    getBucket.clear();
                    boolean canMerge = end.next!=null && end.next.getPrevious(getBucket).size()>1;
                    if (debugCorr && canMerge) logger.debug("Merge beheaded cells candidate frames: [{}->{}] ends with merge", track.get(0).timePoint, track.get(track.size()-1).timePoint);
                    if (!canMerge) {// end: merge with next object
                        if (track.get(0).prev!=null && track.get(0).prev.division) { //start with division with error
                            getBucket.clear();
                            if (track.get(0).prev.getNext(getBucket).size()>2) canMerge=true;
                        }
                        if (debugCorr && canMerge) logger.debug("Merge beheaded cells candidate frames: [{}->{}] starts with division", track.get(0).timePoint, track.get(track.size()-1).timePoint);
                    }
                    if (canMerge) { // create merge scenario
                        List<MergeScenario> merge = new ArrayList<>(track.size());
                        for (TrackAttribute ta : track) {
                            if (populations.get(ta.timePoint).size()>1) {
                                MergeScenario m = new MergeScenario(this, 0, populations.get(ta.timePoint).subList(0, 2), ta.timePoint);
                                if (ta.o.getSize()<beheadedCellsSizeLimit || m.cost<=costLim) merge.add(m);
                                else {
                                    merge.clear();
                                    break;
                                }
                            }
                        }
                        if (!merge.isEmpty()) {
                            correctionsHaveBeenPerformed=true;
                            if (debugCorr) logger.debug("Merge beheaded cells frames: [{}->{}]", track.get(0).timePoint, track.get(track.size()-1).timePoint);
                            for (MergeScenario m : merge) m.applyScenario();
                            for (int ff = f; ff<Math.min(maxT, f+trackSize+1); ++ff)  setAssignmentToTrackAttributes(ff, false);
                            hasMerged = true;
                        }
                    }
                }
                if (hasMerged) f-=1; // for overfragemented cells -> go back
                else f+=track.size()-1;
            }
        }
        return correctionsHaveBeenPerformed;
    }
        
    private void removeIncompleteDivisions(int t) {
        if (populations.get(t)==null || populations.get(t).isEmpty()) return;
        TrackAttribute lastO = getAttribute(t, populations.get(t).size()-1); 
        if (lastO.prev!=null && lastO.prev.truncatedDivision && lastO.idx>0) { // remove incomplete divisions
            if (debugCorr) logger.debug("incomplete division at: {}", lastO);
            removeTrack(lastO);
        } 
        //if (t>minT) for (TrackAttribute ta : trackAttributes[t])  if (ta.prev==null) removeTrack(ta); // remove tracks that do not start a min timePoint. // DO NOT REMOVE THEM IN ORDER TO BE ABLE TO PERFORM MANUAL CORRECTION
        
    }
    private void removeTrack(TrackAttribute o) {
        populations.get(o.timePoint).remove(o.o);
        objectAttributeMap.remove(o.o);
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
                    populations.get(ta.timePoint).remove(ta.o);
                    objectAttributeMap.remove(ta.o);
                }
                int frame = curO.iterator().next().timePoint;
                resetIndices(frame);
                if (frame+1<maxT && nextO.size()==populations.get(frame+1).size()) {
                    nextO.clear(); // stop while loop
                    for (int f = frame+1; f<maxT; ++f) { // remove all objects from frame
                        if (populations.get(f)!=null) {
                            for (Object3D r : populations.get(f)) objectAttributeMap.remove(r);
                            populations.get(f).clear();
                        }
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
            inputImages.remove(timePoint--);
            if (timePoint<0 || !inputImages.containsKey(timePoint)) return;
        }
    }
    private void setAttributesToStructureObjects(int timePoint, List<StructureObject> children, List<StructureObject> childrenPrev) {
        for (int i = 0; i<children.size(); ++i) {
            TrackAttribute ta= getAttribute(timePoint, i);
            if (ta.prev==null || childrenPrev==null) children.get(i).resetTrackLinks(true, true);
            else {
                if (ta.prev.idx>=childrenPrev.size()) logger.error("t:{} PREV NOT FOUND ta: {}, prev {}", ta.timePoint, ta, ta.prev);
                else {
                    childrenPrev.get(ta.prev.idx).setTrackLinks(children.get(i), true, !ta.prev.division && !(ta.prev.truncatedDivision&&ta.endOfChannelContact<endOfChannelContactThreshold.getValue().doubleValue())); //!ta.trackHead
                }
            }
            StructureObject o = children.get(i);
            o.setAttribute("TrackErrorSizeIncrement", ta.sizeIncrementError);
            o.setAttribute(StructureObject.trackErrorPrev, ta.errorPrev);
            o.setAttribute(StructureObject.trackErrorNext, ta.errorCur);
            o.setAttribute("SizeIncrement", ta.sizeIncrement);
            o.setAttribute("TruncatedDivision", ta.prev==null?false : ta.prev.truncatedDivision&&ta.endOfChannelContact<endOfChannelContactThreshold.getValue().doubleValue());
            if (ta.endOfChannelContact>0) o.setAttribute("EndOfChannelContact", ta.endOfChannelContact);
        }
    }
    
    protected void init(List<StructureObject> parentTrack, int structureIdx, boolean segment) {
        if (preFilters==null) this.preFilters=new PreFilterSequence("");
        if (postFilters==null) this.postFilters=new PostFilterSequence("");
        this.segment=segment;
        this.parentsByF = StructureObjectUtils.splitByFrame(parentTrack);
        objectAttributeMap = new HashMap<>();
        populations = new HashMap<>(parentTrack.size());
        //if (segment) segmenters  = new SegmenterSplitAndMerge[timePointNumber];
        this.maxGR=this.maxGrowthRate.getValue().doubleValue();
        this.minGR=this.minGrowthRate.getValue().doubleValue();
        this.baseGrowthRate = new double[]{minGR, maxGR};
        this.costLim = this.costLimit.getValue().doubleValue();
        this.cumCostLim = this.cumCostLimit.getValue().doubleValue();
        this.structureIdx=structureIdx;
        
        sizeFunction = o -> objectAttributeMap.containsKey(o) ? objectAttributeMap.get(o).getSize() : getObjectSize(o);
        sizeIncrementFunction = o -> objectAttributeMap.containsKey(o) ? objectAttributeMap.get(o).getLineageSizeIncrement() : Double.NaN;
        areFromSameLine = (o1, o2) -> objectAttributeMap.containsKey(o1) && objectAttributeMap.containsKey(o2) ? objectAttributeMap.get(o1).prev == objectAttributeMap.get(o2).prev : false;
        
    }
    protected StructureObject getParent(int frame, boolean searchClosestIfAbsent) {
        StructureObject parent = parentsByF.get(frame);
        if (parent==null && searchClosestIfAbsent) {
            if (parentsByF.isEmpty()) return null;
            int delta = 1;
            while(true) {
                if (frame-delta>=0 && parentsByF.containsKey(frame-delta)) return parentsByF.get(frame-delta);
                if ((maxT==0 || frame+delta<=maxT) && parentsByF.containsKey(frame+delta)) return parentsByF.get(frame+delta);
                delta++;
            }
        }
        return parent;
    }
    protected Image getImage(int frame) {
        Image res = inputImages!=null ? inputImages.get(frame) : null;
        if (res==null) {
            StructureObject parent = this.getParent(frame, true);
            if (parent==null) return null;
            res = preFilters.filter(parent.getRawImage(structureIdx), parent);
            if (inputImages!=null) inputImages.put(frame, res);
        }
        return res;
    }
    
    protected List<Object3D> getObjects(int timePoint) {
        if (this.populations.get(timePoint)==null) {
            StructureObject parent = this.parentsByF.get(timePoint);
            List<StructureObject> list = parent!=null ? parent.getChildren(structureIdx) : null;
            if (list!=null) populations.put(parent.getFrame(), Utils.transform(list, o-> {
                if (segment) o.getObject().translate(parent.getBounds().duplicate().reverseOffset()); // so that semgneted objects are in parent referential (for split & merge calls to segmenter)
                return o.getObject();
            }));
            else populations.put(timePoint, Collections.EMPTY_LIST); 
            //logger.debug("get object @ {}, size: {}", timePoint, populations.get(timePoint].size());
            createAttributes(timePoint);
        }
        return populations.get(timePoint);
    }
        
    protected TrackAttribute getAttribute(int frame, int idx) {
        Object3D o = getObjects(frame).get(idx);
        TrackAttribute res = objectAttributeMap.get(o);
        if (res==null) {
            createAttributes(frame);
            res = objectAttributeMap.get(o);
        }
        return res;
    }
    
    protected double getSize(int frame, int idx) {
        return getAttribute(frame, idx).getSize();
    }
    
    private void createAttributes(int frame) {
        List<Object3D> pop = getObjects(frame);
        for (int i = 0; i<pop.size(); ++i) objectAttributeMap.put(pop.get(i), new TrackAttribute(pop.get(i), i, frame));
    }

    protected void resetIndices(int timePoint) {
        int idx = 0;
        for (Object3D o : getObjects(timePoint)) {
            if (!objectAttributeMap.containsKey(o)) {
                if (idx!=0 && debug)  logger.warn("inconsistent attributes for timePoint: {} will be created de novo", timePoint); 
                createAttributes(timePoint);
                return;
            } // has not been created ? 
            objectAttributeMap.get(o).idx=idx++;
        }
    }
    protected double defaultSizeIncrement() {
        return (minGR+maxGR)/2.0;
    }
    
    protected int getErrorNumber(int tpMin, int tpMaxIncluded, boolean assign) {
        if (tpMin<minT+1) tpMin = minT+1;
        if (tpMaxIncluded>=maxT) tpMaxIncluded = maxT-1;
        int res = 0;
        for (int t = tpMin; t<=tpMaxIncluded; ++t) {
            //if (getObjects(t-1)==null) logger.debug("getError number: no objects prev @ t={}", t);
            TrackAssigner ta = getTrackAssigner(t).setVerboseLevel(verboseLevelLimit);
            if (assign) resetTrackAttributes(t);
            ta.assignAll();
            res+=ta.getErrorCount();
            if (assign) setAssignmentToTrackAttributes(t, false);
        }
        return res;
    }
    
    protected static double getObjectSize(Object3D o) {
        if (useVolumeAsSize) return GeometricalMeasurements.getVolume(o);
        //this.objectSize = o.getBounds().getSizeY();
        else return GeometricalMeasurements.getFeretMax(o);
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
        private double objectLength = Double.NaN;
        
        final boolean touchEndOfChannel;
        double endOfChannelContact;
        protected TrackAttribute(Object3D o, int idx, int timePoint) {
            this.o=o;
            this.idx=idx;
            this.timePoint=timePoint;
            int lim = parentsByF.get(timePoint).getBounds().getSizeY()-1;
            if (o.getBounds().getyMax()==lim) {
                double count = 0;
                for (Voxel v : o.getVoxels()) if (v.y==lim) ++count;
                endOfChannelContact = count/getWidth();
                if (endOfChannelContact>endOfChannelContactThreshold.getValue().doubleValue()) touchEndOfChannel=true;
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
        public double getLength() {
            if (Double.isNaN(objectLength)) {
                if (!useVolumeAsSize) objectLength = getSize();
                else objectLength = GeometricalMeasurements.getFeretMax(o);
            }
            return objectLength;
        }
        public double getSize() {
            if (Double.isNaN(objectSize)) objectSize = getObjectSize(o);
            return objectSize;
        }
        private double getWidth() { // unscaled
            return (double)o.getVoxels().size() * o.getScaleXY() / getLength(); // do not use getSize() if getSize() return area !!
        }
        private List<Double> getLineageSizeIncrementList() {
            List<Double> res=  new ArrayList<>(sizeIncrementFrameNumber);
            TrackAttribute ta = this.prev;
            Set<TrackAttribute> bucket = new HashSet<>(3);
            WL: while(res.size()<sizeIncrementFrameNumber && ta!=null) {
                if (ta.next==null) {
                    StructureObject p = parentsByF.get(ta.timePoint);
                    logger.error("Prev's NEXT NULL db:{}, position: {}, parent: {}, current: {}: ta with no next: {}, last of channel: {}", p.getDAO().getMasterDAO().getDBName(), p.getDAO().getPositionName(), p, this, ta, ta.idx==populations.get(ta.timePoint).size()-1);
                }
                if (!ta.errorCur && !ta.truncatedDivision && !ta.touchEndOfChannel) {
                    if (ta.division || ta.next==null) {
                        double nextSize = 0;
                        bucket.clear();
                        Set<TrackAttribute> n = ta.getNext(bucket);
                        for (TrackAttribute t: n) if (t.touchEndOfChannel) {n.clear();break;} // do not take into acount if touches end of channel
                        if ((ta.division && n.size()>1) || (!ta.division && n.size()==1)) {
                            for (TrackAttribute t : n) nextSize+=t.getSize();
                            res.add(nextSize/ta.getSize()); 
                        }
                    } else if (!ta.next.touchEndOfChannel) res.add(ta.next.getSize()/ta.getSize()); 
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
        private List<TrackAttribute> getTrackToNextDivision(List<TrackAttribute> bucket, Function<TrackAttribute, Integer> stopCondition) { // -1 stop without adding last element, 0 continue, 1 stop after adding last element
        if (bucket==null) bucket = new ArrayList<>();
        else bucket.clear();
        Integer stop = stopCondition.apply(this);
        if (stop>=0) {
            bucket.add(this);
            if (stop>0) return bucket;
        }
        TrackAttribute ta= this;
        while(!ta.division && ta.next!=null) {
            ta = ta.next;
            stop = stopCondition.apply(ta);
            if (stop>=0) {
                bucket.add(ta);
                if (stop>0) return bucket;
            }
        }
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
            if (list.size()<=1) return Double.NaN;
            double res = ArrayUtil.median(list);
            if (res<minGR) res = minGR;
            else if (res>maxGR) res = maxGR;
            if (debug) logger.debug("getSizeIncrement for {}-{}: {} list:{}", timePoint, idx, res, list);
            return res;
        }
        
        /*public double[] getLineageSizeRangeAndEstimation() {
            
        }*/
        
        public Set<TrackAttribute> getNext(Set<TrackAttribute> res) {
            if (maxT-1<=timePoint) return res!=null ? res : Collections.EMPTY_SET;
            if (division) {
                if (res==null) res = new HashSet<>();
                for (Object3D o : getObjects(timePoint+1)) {
                    TrackAttribute ta = objectAttributeMap.get(o);
                    if (ta!=null && ta.prev==this) res.add(ta);
                }
                res.add(next);
                return res;
            } else if (next!=null) {
                if (res==null) res = new HashSet<>();
                res.add(next);
                return res;
            } else return res!=null ? res : Collections.EMPTY_SET;
        }
        
        public Set<TrackAttribute> getPrevious(Set<TrackAttribute> res) {
            if (timePoint==minT) return res!=null ? res : Collections.EMPTY_SET;
            if (res==null) res = new HashSet<>();
            for (Object3D o : getObjects(timePoint-1)) {
                TrackAttribute ta = objectAttributeMap.get(o);
                if (ta!=null && ta.next==this) res.add(ta);
            }
            res.add(prev);
            return res;
        }
                
        @Override public String toString() {
            return timePoint+"-"+idx+"(s:"+getSize()+"/th:"+this.trackHead+"/div:"+division+"/nPrev:"+nPrev+")";
        }
    }
    protected TrackAssigner getTrackAssigner(int frame) {
        return new TrackAssigner(populations.get(frame-1), populations.get(frame), baseGrowthRate, true, sizeFunction, sizeIncrementFunction, areFromSameLine);
    }
    protected void setAssignmentToTrackAttributes(int frame, boolean lastAssignment) {
        if (debug) logger.debug("assign previous: frame: {}", frame);
        resetTrackAttributes(frame);
        TrackAssigner assigner = getTrackAssigner(frame).setVerboseLevel(0);
        assigner.assignAll();
        if (debug) logger.debug("L: {} assign previous frame: {}, number of assignments: {}, : {}", assigner.verboseLevel, frame, assigner.assignments.size(), Utils.toStringList(assigner.assignments, a -> a.toString(true)));
        for (Assignment ass : assigner.assignments) setAssignmentToTrackAttributes(ass, frame, false, lastAssignment);
    }
    
    public void resetTrackAttributes(int frame) {
        if (populations.get(frame)!=null) for (Object3D o : populations.get(frame)) if (objectAttributeMap.containsKey(o)) objectAttributeMap.get(o).resetTrackAttributes(true, false);
        if (populations.get(frame-1)!=null) for (Object3D o : populations.get(frame-1)) if (objectAttributeMap.containsKey(o)) objectAttributeMap.get(o).resetTrackAttributes(false, true);
    }
    
    private void setAssignmentToTrackAttributes(Assignment a, int frame, boolean forceError, boolean lastAssignment) {
        boolean error = forceError || a.objectCountPrev()>1 || a.objectCountNext()>2;
        if (a.objectCountPrev()==1 && a.objectCountNext()==1) {
            TrackAttribute taCur = getAttribute(frame, a.idxNext);
            TrackAttribute taPrev = getAttribute(frame-1, a.idxPrev);
            taPrev.division=false;
            taCur.prev=taPrev;
            taPrev.next=taCur;
            taCur.trackHead=false;
            taCur.errorPrev=error;
            taPrev.errorCur=error;
            taCur.nPrev=a.objectCountPrev();
            taPrev.truncatedDivision = a.truncatedEndOfChannel();
            if (!taPrev.truncatedDivision) {
                taCur.sizeIncrementError = a.significantSizeIncrementError();
                taCur.sizeIncrement=taCur.getSize()/taPrev.getSize();
            } else taCur.sizeIncrement=Double.NaN;
            if (setSIErrorsAsErrors && taCur.sizeIncrementError && !taCur.errorPrev) taCur.errorPrev=true;
            if (setSIErrorsAsErrors && taCur.sizeIncrementError && !taPrev.errorCur) taPrev.errorCur=true;
        } else if (a.objectCountPrev()==1 && a.objectCountNext()>1) { // division
            TrackAttribute taPrev = getAttribute(frame-1, a.idxPrev);
            TrackAttribute taCur = getAttribute(frame, a.idxNext);
            taPrev.division=true;
            taPrev.errorCur=error;
            taPrev.next=taCur;
            taCur.trackHead=false;
            for (int i = a.idxNext; i<a.idxNextEnd(); ++i) {
                TrackAttribute ta = getAttribute(frame, i);
                ta.prev=taPrev;
                ta.errorPrev=error;
                ta.nPrev=a.objectCountPrev();
                taPrev.truncatedDivision = a.truncatedEndOfChannel();
                if (!taPrev.truncatedDivision) {
                    ta.sizeIncrementError = a.significantSizeIncrementError();
                    ta.sizeIncrement = a.sizeNext / a.sizePrev;
                } else ta.sizeIncrement=Double.NaN;
                if (setSIErrorsAsErrors && ta.sizeIncrementError && !ta.errorPrev) ta.errorPrev=true;
                if (setSIErrorsAsErrors && ta.sizeIncrementError && !taPrev.errorCur) taPrev.errorCur=true;

            }
        } else if (a.objectCountPrev()>1 && a.objectCountNext()==1) { // merging
            TrackAttribute taCur = getAttribute(frame, a.idxNext);
            taCur.trackHead=false;
            TrackAttribute prev= getAttribute(frame-1, a.idxPrev); 
            //if (!lastAssignment) { // assign biggest prev object to current
                for (int i = a.idxPrev+1; i<a.idxPrevEnd(); ++i) if (getAttribute(frame-1, i).getSize()>prev.getSize()) prev= getAttribute(frame-1, i);
            //}
            taCur.prev=prev;
            taCur.errorPrev=true;
            taCur.nPrev=a.objectCountPrev();
            boolean truncated = a.truncatedEndOfChannel();
            if (!truncated) {
                taCur.sizeIncrementError = a.significantSizeIncrementError();
                taCur.sizeIncrement=a.sizeNext/a.sizePrev;
            } else taCur.sizeIncrement=Double.NaN;

            for (int i = a.idxPrev; i<a.idxPrevEnd(); ++i) {
                TrackAttribute ta = getAttribute(frame-1, i);
                ta.next=taCur;
                ta.errorCur=true;
            }
        } else if (a.objectCountPrev()>1 && a.objectCountNext()>1) { // algorithm assign first with first (or 2 first) or last with last (or 2 last) (the most likely) and recursive call. If last window -> do not consider 
            Assignment dup = a.duplicate(a.ta);
            Assignment currentAssigner = a.duplicate(a.ta);
            TrackAttribute taCur1 = getAttribute(frame, a.idxNext);
            TrackAttribute taCur2 = getAttribute(frame, a.idxNext+1);
            TrackAttribute taPrev1 = getAttribute(frame-1, a.idxPrev);
            double sizeIncrement1 = taPrev1.getLineageSizeIncrement();
            if (Double.isNaN(sizeIncrement1)) sizeIncrement1 = defaultSizeIncrement();
            double score1 = Math.abs(taCur1.getSize()/taPrev1.getSize()-sizeIncrement1);
            double scoreDiv = Math.abs((taCur1.getSize()+taCur2.getSize())/taPrev1.getSize()-sizeIncrement1);

            boolean endOfChannel = a.idxPrevEnd()==a.ta.idxPrevLim; // idxEnd==idxLim ||  // if end of channel : assignement only from start 
            TrackAttribute taCurEnd1, taCurEnd2, taPrevEnd;
            double scoreEnd1=Double.POSITIVE_INFINITY, scoreEndDiv=Double.POSITIVE_INFINITY;
            if (!endOfChannel) {
                taCurEnd1 = getAttribute(frame, a.idxNextEnd()-1);
                taCurEnd2 = getAttribute(frame, a.idxNextEnd()-2);
                taPrevEnd = getAttribute(frame-1, a.idxPrevEnd()-1);
                double sizeIncrementEnd = endOfChannel ? 0 :  taPrevEnd.getLineageSizeIncrement();
                if (Double.isNaN(sizeIncrementEnd)) sizeIncrementEnd = defaultSizeIncrement();
                scoreEnd1 = Math.abs(taCurEnd1.getSize()/taPrevEnd.getSize()-sizeIncrementEnd);
                scoreEndDiv = Math.abs((taCurEnd1.getSize()+taCurEnd2.getSize())/taPrevEnd.getSize()-sizeIncrementEnd);
            }
            double score=Math.min(scoreEndDiv, Math.min(scoreEnd1, Math.min(score1, scoreDiv)));
            int nextIdxError = -1;
            if (score1==score) { // assign first with first
                dup.remove(false, true);
                dup.remove(true, true);
                currentAssigner.removeUntil(true, false, 1);
                currentAssigner.removeUntil(false, false, 1);
                if (dup.idxNextEnd() == dup.idxNext) nextIdxError = dup.idxNext-1;
            } else if (score==scoreEnd1) { // assign last with last
                dup.remove(true, false);
                dup.remove(false, false);
                currentAssigner.removeUntil(true, true, 1);
                currentAssigner.removeUntil(false, true, 1);
                if (dup.idxNextEnd() == dup.idxNext) nextIdxError = dup.idxNextEnd()+1;
            } else if (scoreDiv==score) { // assign first with 2 first
                dup.remove(true, true);
                dup.removeUntil(false, true, dup.objectCountNext()-2);
                currentAssigner.removeUntil(true, false, 1);
                currentAssigner.removeUntil(false, false, 2);
                if (dup.idxNextEnd() == dup.idxNext) nextIdxError = dup.idxNext-1;
            } else { // assign last with 2 lasts
                dup.remove(true, false);
                dup.removeUntil(false, false, dup.objectCountNext()-2);
                currentAssigner.removeUntil(true, true, 1);
                currentAssigner.removeUntil(false, true, 2);
                if (dup.idxNextEnd() == dup.idxNext) nextIdxError = dup.idxNextEnd()+1;
            }
            if (debug && a.ta.verboseLevel<verboseLevelLimit) logger.debug("assignment {} with {} objects, assign {}, div:{} / ass1={}, ass2={}", a.objectCountPrev(), a.objectCountNext(), (score==score1||score==scoreDiv) ? "first" : "last", (score==scoreDiv||score==scoreEndDiv), currentAssigner.toString(false), dup.toString(false));
            setAssignmentToTrackAttributes(currentAssigner, frame, !lastAssignment, lastAssignment); // perform current assignement
            setAssignmentToTrackAttributes(dup, frame, !lastAssignment, lastAssignment); // recursive call
            if (nextIdxError>=0) { // case of assignemnet 1 with O in dup : set next & signal error
                getAttribute(frame-1, dup.idxPrev).next = getAttribute(frame, nextIdxError);
                getAttribute(frame-1, dup.idxPrev).errorCur=true;
            }
        } else if (a.objectCountPrev()==1 && a.objectCountNext()==0) { // end of lineage
            TrackAttribute ta = getAttribute(frame-1, a.idxPrev);
            ta.resetTrackAttributes(false, true);
            if (a.idxPrev<a.ta.idxPrevLim-1 && ta.getLineageSizeIncrement()>SIQuiescentThld) ta.errorCur=true; // no error if cell is not growing
        } else if (a.objectCountNext()==1 && a.objectCountPrev()==0) {
            TrackAttribute ta = getAttribute(frame, a.idxNext);
            ta.resetTrackAttributes(true, false);
        }
    }

    
    /**
    * 
    * @return minimal/maximal+1 timePoint where correction has been performed
    */
    public int[] performCorrection(Assignment a, int frame) {
        if (debugCorr && a.ta.verboseLevel<verboseLevelLimit) logger.debug("t: {}: performing correction, {}", frame, a.toString(true));
        if (a.objectCountNext()==1) return performCorrectionSplitOrMergeOverMultipleTime(a, frame);
        else return performCorrectionMultipleObjects(a, frame);
        //else return null;
    }
    private int[] performCorrectionSplitOrMergeOverMultipleTime(Assignment a, int frame) {
        MergeScenario m = new MergeScenario(this, a.idxPrev, a.prevObjects, frame-1);
        //if (Double.isInfinite(m.cost)) return -1; //cannot merge
        List<CorrectionScenario> merge = m.getWholeScenario(maxCorrectionLength, costLim, cumCostLim); // merge scenario
        double[] mergeCost = new double[]{Double.POSITIVE_INFINITY, 0}; for (CorrectionScenario c : merge) mergeCost[1]+=c.cost; if (merge.isEmpty()) mergeCost[1]=Double.POSITIVE_INFINITY;
        SplitScenario ss =new SplitScenario(BacteriaClosedMicrochannelTrackerLocalCorrections.this, a.nextObjects.get(0), frame);
        
        List<CorrectionScenario> split =ss.getWholeScenario(maxCorrectionLength, costLim, cumCostLim);
        double[] splitCost = new double[]{Double.POSITIVE_INFINITY, 0}; for (CorrectionScenario c : split) splitCost[1]+=c.cost; if (split.isEmpty()) splitCost[1]=Double.POSITIVE_INFINITY;

        if (Double.isInfinite(mergeCost[1]) && Double.isInfinite(splitCost[1])) return null;
        int tMin = frame-merge.size();
        int tMax = frame+split.size();
        double currentErrors = getErrorNumber(tMin, tMax, false);
        ObjectAndAttributeSave saveCur = new ObjectAndAttributeSave(frame-merge.size(), frame+split.size()-1);
        ObjectAndAttributeSave saveMerge=null, saveSplit=null;
        if (Double.isFinite(splitCost[1])) {
            for (CorrectionScenario c : split) c.applyScenario();
            splitCost[0] = getErrorNumber(tMin, tMax, true);
            saveSplit = new ObjectAndAttributeSave(frame, frame+split.size()-1);
            if (correctionStep) step("step:"+step+"/split scenario ["+frame+";"+(frame+split.size()-1)+"]", false);
            saveCur.restore(frame, frame+split.size()-1);
            for (int t = frame;t <Math.min(frame+split.size()+1, maxT-1); ++t) setAssignmentToTrackAttributes(t, false);
        }
        if (Double.isFinite(mergeCost[1])) {
            for (CorrectionScenario c : merge) c.applyScenario();
            mergeCost[0] = getErrorNumber(tMin, tMax, true);
            saveMerge = new ObjectAndAttributeSave(frame-merge.size(), frame-1);
            if (correctionStep) step("step:"+step+"/merge scenario ["+(frame-merge.size())+";"+(frame-1)+"]", false);
            saveCur.restore(frame-merge.size(), frame-1);
            for (int t = Math.max(minT+1, frame-merge.size());t<=frame; ++t) setAssignmentToTrackAttributes(t, false);
        }
        if (debugCorr && a.ta.verboseLevel<verboseLevelLimit) logger.debug("t: {}: performing correction: errors: {}, merge scenario length: {}, cost: {}, errors: {}, split scenario: length {}, cost: {}, errors: {}", frame, currentErrors, merge.size(), mergeCost[1], mergeCost[0], split.size(), splitCost[1], splitCost[0]);
        if (saveMerge!=null && mergeCost[0]<=currentErrors && compareScores(mergeCost, splitCost, true)<=0 ) {
            if (mergeCost[0]==currentErrors) return null; // if same number of errors do not apply  // && mergeCost[1]>0
            saveMerge.restoreAll();
            if (debugCorr && a.ta.verboseLevel<verboseLevelLimit) logger.debug("apply merge scenario!");
            return new int[]{Math.max(minT+1, frame-merge.size()), Math.min(maxT-1, frame+1)};
        } else if (saveSplit!=null && splitCost[0]<=currentErrors && compareScores(mergeCost, splitCost, true)>=0 ) {
            if (splitCost[0]==currentErrors ) return null; // if same number of errors do not apply // && splitCost[1]>0
            if (debugCorr && a.ta.verboseLevel<verboseLevelLimit) logger.debug("apply split scenario!");
            saveSplit.restoreAll();
            return new int[]{frame, Math.min(maxT-1, frame+split.size()+1)};
        } else return null;
    }

    private int[] performCorrectionMultipleObjects(Assignment a, int frame) {
        if (debugCorr) logger.debug("performing correction multiple objects: {}", this);

        // Todo : rearrange objects from next au lieu de toutes les combinaisons de merge..
        List<CorrectionScenario> scenarios = new ArrayList<>();
        for (int iMerge = 0; iMerge+1<a.objectCountPrev(); ++iMerge) scenarios.add(new MergeScenario(BacteriaClosedMicrochannelTrackerLocalCorrections.this, iMerge+a.idxPrev, a.prevObjects.subList(iMerge, iMerge+2), frame-1)); // merge one @ previous
        if (a.objectCountPrev()>2 && a.objectCountNext()<=2) scenarios.add(new MergeScenario(BacteriaClosedMicrochannelTrackerLocalCorrections.this, a.idxPrev, a.prevObjects, frame-1)); // merge all previous objects

        scenarios.add(new RearrangeObjectsFromPrev(BacteriaClosedMicrochannelTrackerLocalCorrections.this, frame, a)); // TODO: TEST INSTEAD OF SPLIT / SPLITANDMERGE

        scenarios.removeIf(c -> c.cost>costLim);
        if (scenarios.isEmpty()) return null;
        // try all scenarios and check error number
        double currentErrors = getErrorNumber(frame-1, frame+1, false);
        ObjectAndAttributeSave saveCur = new ObjectAndAttributeSave(frame-1, frame);
        final Map<CorrectionScenario, ObjectAndAttributeSave> saveMap = new HashMap<>(scenarios.size());
        final Map<CorrectionScenario, Integer> errorMap = new HashMap<>(scenarios.size());

        for (CorrectionScenario c : scenarios) {
            c.applyScenario();
            errorMap.put(c, getErrorNumber(frame-1, frame+1, true));
            saveMap.put(c, new ObjectAndAttributeSave(c.timePointMin, c.timePointMax));
            if (correctionStep) step("step:"+step+"/"+c, false);
            if (debugCorr && a.ta.verboseLevel<verboseLevelLimit) logger.debug("correction multiple: errors current: {}, scenario: {}:  errors: {}, cost: {}",currentErrors, c, errorMap.get(c), c.cost);
            saveCur.restore(c.timePointMin, c.timePointMax);
            for (int t = Math.max(minT+1, c.timePointMin); t<=Math.min(c.timePointMax+1, maxT-1); ++t)  setAssignmentToTrackAttributes(t, false);
        }
        CorrectionScenario best = Collections.min(scenarios, (CorrectionScenario o1, CorrectionScenario o2) -> {
            int comp = Integer.compare(errorMap.get(o1), errorMap.get(o2));
            if (comp==0) comp = Double.compare(o1.cost, o2.cost);
            return comp;
        });
        if (errorMap.get(best)<currentErrors) { //|| (best.cost==0 && errorMap.get(best)==currentErrors)
            saveMap.get(best).restoreAll();
            return new int[]{Math.max(minT+1, best.timePointMin), Math.min(best.timePointMax+1, maxT-1)};
        }

        return null;
    }

    protected class ObjectAndAttributeSave {
        final List<Object3D>[] objects;
        final Map<Object3D, TrackAttribute> taMap;
        final int tpMin;
        protected ObjectAndAttributeSave(int tpMin, int tpMax) {
            if (tpMin<0) tpMin = 0;
            if (tpMax>=maxT) tpMax = maxT-1;
            this.tpMin=tpMin;
            objects = new List[tpMax-tpMin+1];
            taMap = new HashMap<>();
            for (int t = tpMin; t<=tpMax; ++t) {
                objects[t-tpMin] = new ArrayList(getObjects(t));
                for (Object3D o : getObjects(t)) {
                    TrackAttribute ta = objectAttributeMap.get(o);
                    if (ta!=null) taMap.put(o, ta);
                }
            }
        }
        public void restoreAll() {
            for (int i = 0; i<objects.length; i++) {
                populations.put(i+tpMin, new ArrayList(objects[i]));
                for (Object3D o : objects[i]) objectAttributeMap.replace(o, taMap.get(o));
                resetIndices(i+tpMin);
            }
        }
        public boolean restore(int tMin, int tMax) {
            if (tMax<tpMin) return false;
            if (tMin<tpMin) tMin=tpMin;
            if (tMax>=tpMin+objects.length) tMax=tpMin+objects.length-1;
            for (int t = tMin; t<=tMax; ++t) {
                populations.put(t, new ArrayList(objects[t-tpMin]));
                for (Object3D o : populations.get(t)) objectAttributeMap.replace(o, taMap.get(o));
                resetIndices(t);
            }
            return true;
        }
        public boolean restore(int t) {
            if (t<tpMin || t>=tpMin+objects.length) return false;
            populations.put(t,new ArrayList(objects[t-tpMin]));
            for (Object3D o : populations.get(t)) objectAttributeMap.replace(o, taMap.get(o));
            resetIndices(t);
            return true;
        }
    }
    
}
