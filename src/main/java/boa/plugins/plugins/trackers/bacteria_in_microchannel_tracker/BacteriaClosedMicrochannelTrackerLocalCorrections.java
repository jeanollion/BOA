/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker;

import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.ConditionalParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.PostFilterSequence;
import boa.configuration.parameters.PreFilterSequence;
import boa.configuration.parameters.TrackPreFilterSequence;
import boa.data_structure.Measurements;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectPreProcessing;
import boa.data_structure.StructureObjectTracker;
import boa.data_structure.StructureObjectUtils;
import boa.data_structure.Voxel;
import boa.image.BlankMask;
import ij.process.AutoThresholder;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageMask;
import boa.image.processing.ImageOperations;
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
import boa.measurement.GeometricalMeasurements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import boa.plugins.MultiThreaded;
import boa.plugins.OverridableThreshold;
import boa.plugins.Segmenter;
import boa.plugins.SegmenterSplitAndMerge;
import boa.plugins.Tracker;
import boa.plugins.TrackerSegmenter;
import boa.plugins.ParameterSetup;
import boa.plugins.ParameterSetupTracker;
import boa.plugins.ToolTip;
import boa.plugins.TrackParametrizable;
import boa.plugins.TrackParametrizable.ApplyToSegmenter;
import boa.plugins.plugins.processing_scheme.SegmentOnly;
import boa.utils.ArrayUtil;
import boa.utils.HashMapGetCreate;
import boa.utils.Pair;
import boa.utils.Utils;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 *
 * @author jollion
 */
public class BacteriaClosedMicrochannelTrackerLocalCorrections implements TrackerSegmenter, MultiThreaded, ParameterSetupTracker, ToolTip {
    public final static Logger logger = LoggerFactory.getLogger(BacteriaClosedMicrochannelTrackerLocalCorrections.class);
    
    protected PluginParameter<SegmenterSplitAndMerge> segmenter = new PluginParameter<>("Segmentation algorithm", SegmenterSplitAndMerge.class, false);
    BoundedNumberParameter maxGrowthRate = new BoundedNumberParameter("Maximum Size Increment", 2, 1.5, 1, null).setToolTipText("Typical maximum ratio of size between two frames");
    BoundedNumberParameter minGrowthRate = new BoundedNumberParameter("Minimum size increment", 2, 0.7, 0.01, null).setToolTipText("Typical minimum ratio of size between two frames");
    BoundedNumberParameter costLimit = new BoundedNumberParameter("Correction: operation cost limit", 3, 1.5, 0, null).setToolTipText("Limits the cost of each single correction operation (merge/split). Value depends on the segmenter and the threshold for splitting set in the segmenter");
    BoundedNumberParameter cumCostLimit = new BoundedNumberParameter("Correction: cumulative cost limit", 3, 5, 0, null).setToolTipText("Limits the sum of costs for a correction over multiple frames");
    BoundedNumberParameter endOfChannelContactThreshold = new BoundedNumberParameter("End of channel contact Threshold", 2, 0.45, 0, 1).setToolTipText("A cell is considered to be partially outside the channel if the contact with the open-end of the channel divided by its thickness is superior to this value");
    Parameter[] parameters = new Parameter[]{segmenter, minGrowthRate, maxGrowthRate, costLimit, cumCostLimit, endOfChannelContactThreshold};

    // multithreaded interface
    ExecutorService executor;
    @Override
    public void setExecutor(ExecutorService executor) {
        this.executor=executor;
    }
    // tooltip interface
    String toolTip = "<b>Bacteria Tracker in closed-end channel</b> "
            + "<ul><li>Tracking is done only using rank and growth rate information (no localization within microchannel), and is thus adapted to moving cells, but detects poorly cell death</li>"
            + "<li>Assignment is done rank-wise between two successive frames starting from the cells located towards the closed-end of microchannel</li>"
            + "<li>First assignement is the minimal that verify the growth inequality: given Sf = sum(size)@Frame=F Sf-1 = sum(size)@Frame=F-1 : Sf-1 * minGrowthRate <= Sf <= Sf-1 * maxGrowthRate </li>"
            + "<li>In order to take into acount a wide range of growth rate, when possible the size ratio between Frames F & F-1 is compared to the median size ratio of the 7 last observations of the same line, and the difference is mimimized</li>"
            + "<li>In order to take into account cells exiting microchannels no errors are counted to assignment of cells close to the microchannel open-end</li>"
            + "<li>When tracking errors are detected (e.g two bacteria merging at next frame), a local correction is intended, comparing the scenario(s) of merging cells at previous frames and splitting cells at next frames. The scenario that minimizes firstly tracking error number and secondly correction cost (defined by the segmenter) will be chosen if its cost is under the thresholds defined by parameters <em>Correction: operation cost limit</em> and <em>Correction: cumulative cost limit</em></li></ul>";

    @Override public String getToolTipText() {return toolTip;}
    
    @Override public SegmenterSplitAndMerge getSegmenter() {
        return segmenter.instanciatePlugin();
    }


    @Override
    public boolean canBeTested(String p) {
        return segmenter.getName().equals(p) || maxGrowthRate.getName().equals(p)|| minGrowthRate.getName().equals(p)|| costLimit.getName().equals(p)|| cumCostLimit.getName().equals(p)|| endOfChannelContactThreshold.getName().equals(p);
    }

    String testParameterName;
    @Override
    public void setTestParameter(String p) {
        this.testParameterName=p;
    }
    @Override 
    public boolean runSegmentAndTrack(String p) {
        return  segmenter.getName().equals(p);
    }
    
    // tracker-related attributes 
    int structureIdx;
    TrackPreFilterSequence trackPreFilters; 
    PostFilterSequence postFilters;
    
    // tracking-related attributes
    protected enum Flag {error, correctionMerge, correctionSplit;}
    Map<Integer, List<Region>> populations;
    Map<Region, TrackAttribute> objectAttributeMap;
    private boolean segment, correction;
    Map<Integer, Image> inputImages;
    ApplyToSegmenter applyToSegmenter;
    TreeMap<Integer, StructureObject> parentsByF;
    HashMapGetCreate<Integer, SegmenterSplitAndMerge> segmenters = new HashMapGetCreate(f->{
        SegmenterSplitAndMerge s= segmenter.instanciatePlugin();
        applyToSegmenter.apply(parentsByF.get(f), s);
        return s;
    });
    int minT, maxT;
    double maxGR, minGR, costLim, cumCostLim;
    double[] baseGrowthRate;
    
    
    // debug static variables
    public static boolean debug=false, debugCorr=false;
    public static double debugThreshold = Double.NaN;
    public static int verboseLevelLimit=1;
    public static int correctionStepLimit = 20;
    public static int bactTestFrame=-1;

    final static int correctionLoopLimit=3;
    final static int sizeIncrementFrameNumber = 7; // number of frames for sizeIncrement computation
    final static double significativeSIErrorThld = 0.25; // size increment difference > to this value lead to an error
    final static double SIErrorValue=1; //equivalence between a size-increment difference error and regular error 
    final static double SIIncreaseThld = 0.1; // a cell is added to the assignment only is the error number is same or inferior and if the size increment difference is less than this value
    final static double SIQuiescentThld = 1.05; // under this value we consider cells are not growing -> if break in lineage no error count (cell dies)
    final static boolean setSIErrorsAsErrors = false; // SI errors are set as tracking errors in object attributes
    final static int correctionLevelLimit = 10; // max bacteria idx for correction
    final static int cellNumberLimitForAssignment = 10; // max bacterai idx for recursive testing in assignment 
    final static int maxCorrectionLength = 500; // limit lenth of correction scenario
    final static boolean useVolumeAsSize=true; // used volume ws length to assess growth // if length -> length should be re-computed for merged objects and not just summed
    
    // functions for assigners
    
    Function<Region, Double> sizeFunction; 
    Function<Region, Double> sizeIncrementFunction;
    BiFunction<Region, Region, Boolean> areFromSameLine, haveSamePreviousObject;
    
    public BacteriaClosedMicrochannelTrackerLocalCorrections setSegmenter(SegmenterSplitAndMerge seg) {
        this.segmenter.setPlugin(seg);
        return this;
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
        Collections.sort(parentTrack, (o1, o2)->Integer.compare(o1.getFrame(), o2.getFrame()));
        for (StructureObject p : parentTrack) getObjects(p.getFrame());
        for (StructureObject p : parentTrack)  setAssignmentToTrackAttributes(p.getFrame(), true);
        applyLinksToParents(parentTrack);
    }

    @Override public void segmentAndTrack(int structureIdx, List<StructureObject> parentTrack, TrackPreFilterSequence trackPreFilters, PostFilterSequence postFilters) {
        this.trackPreFilters=trackPreFilters;
        this.postFilters=postFilters;
        init(parentTrack, structureIdx, true);
        segmentAndTrack(parentTrack, true);
    }

    public static boolean correctionStep;
    public static Map<String, List<StructureObject>> stepParents;
    private static int snapshotIdx = 0;
    
    protected void segmentAndTrack(List<StructureObject> parentTrack, boolean performCorrection) {
        if (performCorrection && (correctionStep)) stepParents = new LinkedHashMap<>();
        this.correction=performCorrection;
        maxT = Collections.max(parentsByF.keySet())+1;
        minT = Collections.min(parentsByF.keySet());
        if (debug) logger.debug("minF: {}, maxF: {}", minT, maxT);
        
        // 1) Segment and keep track of segmenter parametrizer for corrections
        SegmentOnly so = new SegmentOnly(segmenter.instanciatePlugin()).setPostFilters(postFilters);
        if (correction) { // record prefilters & applyToSegmenter
            trackPreFilters.filter(structureIdx, parentTrack, executor);
            inputImages=parentTrack.stream().collect(Collectors.toMap(p->p.getFrame(), p->p.getPreFilteredImage(structureIdx)));
            applyToSegmenter = TrackParametrizable.getApplyToSegmenter(structureIdx, parentTrack, segmenter.instanciatePlugin(), executor);
            so.segmentAndTrack(structureIdx, parentTrack, applyToSegmenter, executor);
        } else { // no need to record the preFilters images
            so.setTrackPreFilters(trackPreFilters);
            so.segmentAndTrack(structureIdx, parentTrack, executor);
        }
        // trim empty frames @ start & end. Limit to first continuous segment ? 
        while (minT<maxT && getObjects(minT).isEmpty()) ++minT;
        while (maxT>minT && getObjects(maxT-1).isEmpty()) --maxT;
        
        if (maxT-minT<=1) {
            for (StructureObject p : parentsByF.values()) p.setChildren(null, structureIdx);
            return;
        }
        if (debugCorr||debug) logger.debug("Frame range: [{};{}]", minT, maxT);
        for (int f = minT; f<maxT; ++f) getObjects(f); // init all objects
        if (correctionStep) snapshot(null, true);

        // 2) perform corrections idx-wise
        if (correction) {
            for (int t = minT+1; t<maxT; ++t) setAssignmentToTrackAttributes(t, false);
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
                            if (correctionStep && stepParents.size()>correctionStepLimit) return;
                            if (corr2) {
                                int[] tRange2 = corrRanges2.get(0);
                                if (tRange2[0]<tRange[0]) tRange[0] = tRange2[0];
                                if (tRange2[1]>tRange[1]) tRange[1] = tRange2[1];
                            }
                            if (idx>0 && corr2 && nLoop<=correctionLoopLimit) { // corrections have been performed : reset idx
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
            if (correctionStep) snapshot("End of process", false);
        }
        
        // 3) final assignement without correction, noticing all errors
        for (int t = minT+1; t<maxT; ++t)  setAssignmentToTrackAttributes(t, true);
        List<StructureObject> parents = new ArrayList<>(parentsByF.values());
        Collections.sort(parents);
        applyLinksToParents(parents);
    }
    /**
     * Performs correction at a specified assignment index (see {@link boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker.Assignment}) within the range of frame [{@param frameMin};{@param frameMaxIncluded}]
     * @param frameMin min frame
     * @param frameMaxIncluded max frame
     * @param idx assignment index at which perform correction
     * @param outRanges frame range containing errors -> will be written to
     * @param limitToOneRange if limit correction to one frame range
     * @return true if a correction was performed
     */
    private boolean performCorrectionsByIdx(int frameMin, int frameMaxIncluded, final int idx, List<int[]> outRanges, boolean limitToOneRange) {
        if (debugCorr) logger.debug("performing corrections [{};{}] @ {}", frameMin, frameMaxIncluded, idx);
        int nLoop=0, rangeLoop=0;
        int currentT = frameMin;
        outRanges.clear();
        int[] currentRange = null;
        while(currentT<=frameMaxIncluded) {
            int minT = currentT;
            boolean change = false;
            if (idx<populations.get(currentT).size()) {
                TrackAttribute ta = getAttribute(currentT, idx);
                TrackAttribute taPrev = idx<populations.get(currentT-1).size() ? getAttribute(currentT-1, idx) : null;
                CorLoop : while (((taPrev!=null && (taPrev.errorCur || taPrev.sizeIncrementError)) || ta.errorPrev || ta.sizeIncrementError) && nLoop<correctionLoopLimit) { // il y a une erreur à corriger
                    if (currentRange == null) currentRange = new int[]{frameMaxIncluded, frameMin};
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
                                snapshot(null, true);
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
                            if (rangeLoop>=correctionLoopLimit) {
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
    
    
    private void applyLinksToParents(List<StructureObject> parents) {
        List<StructureObject> childrenPrev = null;
        List<StructureObject> children = null;
        int errors = 0;
        for (StructureObject parent : parents) {
            int f = parent.getFrame();
            if (!segment) { // modifiy existing structureObjects
                children = parent.getChildren(structureIdx);
                if (children ==null || populations.get(f)==null) {}
                else if (children.size()!=populations.get(f).size()) logger.error("BCMTLC: error @ parent: {}, children and tracker objects differ in number", parent);
                else setAttributesToStructureObjects(f, children, childrenPrev);
            } else { // creates new structureObjects
                List<Region> cObjects;
                if (correctionStep) {
                    cObjects = new ArrayList<>(populations.get(f).size());
                    for (Region o : populations.get(f)) cObjects.add(o.duplicate());
                } else {
                    cObjects = populations.get(f);
                    //RemoveIncompleteDivisions(t);
                }
                children = parent.setChildrenObjects(new RegionPopulation(cObjects, new BlankMask(parent.getMask()).resetOffset()), structureIdx); // will translate all voxels
                setAttributesToStructureObjects(f, children, childrenPrev);
                if (debug) for (StructureObject c : children) if (c.hasTrackLinkError(true, true)) ++errors;
            }
            childrenPrev=children;
        }
        if (debug) logger.debug("Errors: {}", errors);
    }
    
     
    private void removeIncompleteDivisions(int t) {
        if (populations.get(t)==null || populations.get(t).isEmpty()) return;
        TrackAttribute lastO = getAttribute(t, populations.get(t).size()-1); 
        if (lastO.prev!=null && lastO.prev.truncatedDivision && lastO.idx>0) { // remove incomplete divisions
            if (debugCorr) logger.debug("incomplete division at: {}", lastO);
            trimTrack(lastO);
        } 
        //if (t>minT) for (TrackAttribute ta : trackAttributes[t])  if (ta.prev==null) removeTrack(ta); // remove tracks that do not start a min timePoint. // DO NOT REMOVE THEM IN ORDER TO BE ABLE TO PERFORM MANUAL CORRECTION
        
    }
    /**
     * Remove object and all linked objects at next frames
     * @param o 
     */
    private void trimTrack(TrackAttribute o) {
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
                            for (Region r : populations.get(f)) objectAttributeMap.remove(r);
                            populations.get(f).clear();
                        }
                    }
                }
            }
        }
    }
    /**
     * Transfers Tracking information to structureObjects
     * @param timePoint
     * @param children
     * @param childrenPrev 
     */
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
        if (postFilters==null) this.postFilters=new PostFilterSequence("");
        this.segment=segment;
        this.parentsByF = new TreeMap<>(StructureObjectUtils.splitByFrame(parentTrack));
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
        haveSamePreviousObject = (o1, o2) -> {
            if (!objectAttributeMap.containsKey(o1) || !objectAttributeMap.containsKey(o2)) return false;
            return objectAttributeMap.get(o1).prev.equals(objectAttributeMap.get(o2).prev);
        };
        areFromSameLine = (o1, o2) -> {
            if (!objectAttributeMap.containsKey(o1) || !objectAttributeMap.containsKey(o2)) return false;
            TrackAttribute ta1 = objectAttributeMap.get(o1).prev;
            TrackAttribute ta2 = objectAttributeMap.get(o2).prev;
            while(ta1!=null && ta2!=null) {
                if (ta1.equals(ta2)) return true;
                if (ta1.division || ta2.division) return false;
                ta1 = ta1.prev;
                ta2 = ta2.prev;
            }
            return false;
        };
    }
    protected StructureObject getParent(int frame) {
        return getParent(frame, false);
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
        return inputImages!=null ? inputImages.get(frame) : null;
    }
    
    protected List<Region> getObjects(int timePoint) {
        if (this.populations.get(timePoint)==null) {
            StructureObject parent = this.parentsByF.get(timePoint);
            List<StructureObject> list = parent!=null ? parent.getChildren(structureIdx) : null;
            if (list!=null) populations.put(parent.getFrame(), Utils.transform(list, o-> {
                if (segment || correction) o.getObject().translate(parent.getBounds().duplicate().reverseOffset()).setIsAbsoluteLandmark(false); // so that semgneted objects are in parent referential (for split & merge calls to segmenter)
                return o.getObject();
            }));
            else populations.put(timePoint, Collections.EMPTY_LIST); 
            //logger.debug("get object @ {}, size: {}", timePoint, populations.get(timePoint].size());
            createAttributes(timePoint);
        }
        return populations.get(timePoint);
    }
        
    protected TrackAttribute getAttribute(int frame, int idx) {
        Region o = getObjects(frame).get(idx);
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
        List<Region> pop = getObjects(frame);
        for (int i = 0; i<pop.size(); ++i) objectAttributeMap.put(pop.get(i), new TrackAttribute(pop.get(i), i, frame));
    }

    protected void resetIndices(int timePoint) {
        int idx = 0;
        for (Region o : getObjects(timePoint)) {
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
    /**
     * Computes the error number of the current objects by performing assignment between specified frames See {@link boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker.Assignment#getErrorCount() }
     * @param tpMin
     * @param tpMaxIncluded
     * @param assign if assignment should be set to track attributes
     * @return error number in range [{@param tpMin}; {@param tpMaxIncluded}]
     */
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
    
    protected static double getObjectSize(Region o) {
        if (useVolumeAsSize) return GeometricalMeasurements.getVolume(o);
        else return GeometricalMeasurements.getFeretMax(o);
    }
    /**
     * Class holing link information
     */
    public class TrackAttribute {
        
        public int idx;
        final int timePoint;
        TrackAttribute prev;
        TrackAttribute next;
        Flag flag;
        boolean errorPrev, errorCur, truncatedDivision, sizeIncrementError;
        double sizeIncrement=Double.NaN;
        int nPrev;
        public final Region o;
        boolean division=false, trackHead=true;
        private double objectSize=Double.NaN;
        private double objectLength = Double.NaN;
        
        final boolean touchEndOfChannel;
        double endOfChannelContact;
        protected TrackAttribute(Region o, int idx, int timePoint) {
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
        
        public Set<TrackAttribute> getNext(Set<TrackAttribute> res) {
            if (maxT-1<=timePoint) return res!=null ? res : Collections.EMPTY_SET;
            if (division) {
                if (res==null) res = new HashSet<>();
                for (Region o : getObjects(timePoint+1)) {
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
            for (Region o : getObjects(timePoint-1)) {
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
    /**
     *
     * @param frame
     * @return TrackAssigner between frame-1 and frame
     */
    protected TrackAssigner getTrackAssigner(int frame) {
        return new TrackAssigner(populations.get(frame-1), populations.get(frame), baseGrowthRate, true, sizeFunction, sizeIncrementFunction, areFromSameLine, haveSamePreviousObject);
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
        if (populations.get(frame)!=null) for (Region o : populations.get(frame)) if (objectAttributeMap.containsKey(o)) objectAttributeMap.get(o).resetTrackAttributes(true, false);
        if (populations.get(frame-1)!=null) for (Region o : populations.get(frame-1)) if (objectAttributeMap.containsKey(o)) objectAttributeMap.get(o).resetTrackAttributes(false, true);
    }
    /**
     * Transfers assignment information to trackAttributes
     * @param a
     * @param frame
     * @param forceError
     * @param lastAssignment 
     */
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
                taCur.sizeIncrementError = a.getSizeIncrementErrors()>0;
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
                    ta.sizeIncrementError = a.getSizeIncrementErrors()>0;
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
                taCur.sizeIncrementError = a.getSizeIncrementErrors()>0;
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
     * @param a assignment
     * @param frame
    * @return frame range (minimal/maximal+1 ) where correction has been performed
    */
    public int[] performCorrection(Assignment a, int frame) {
        if (debugCorr && a.ta.verboseLevel<verboseLevelLimit) logger.debug("t: {}: performing correction, {}", frame, a.toString(true));
        if (a.prevObjects.size()>1) return performCorrectionSplitAfterOrMergeBeforeOverMultipleTime(a, frame); //if (a.objectCountNext()==1 || (a.objectCountNext()==2 && a.objectCountPrev()==2))
        // TODO create scenarios for split before
        //else return performCorrectionMultipleObjects(a, frame);
        else return null;
    }
    /**
     * Compares two correction scenarios: split at following frames or merge at previous frames
     * @param a assignment where an error is detected
     * @param frame frame where error is detected
     * @return frame range (minimal/maximal+1 ) where correction has been performed
     */
    private int[] performCorrectionSplitAfterOrMergeBeforeOverMultipleTime(Assignment a, int frame) {
        List<CorrectionScenario> allScenarios = new ArrayList<>();
        
        MergeScenario m = new MergeScenario(this, a.idxPrev, a.prevObjects, frame-1);
        allScenarios.add(m.getWholeScenario(maxCorrectionLength, costLim, cumCostLim)); // merge scenario
        
        // sub-merge scenarios
        if (a.prevObjects.size()>4) { // limit to scenario with objects from same line
            Collection<List<Region>> objectsByLine = a.splitPrevObjectsByLine();
            objectsByLine.removeIf(l->l.size()<=1);
            for (List<Region> l : objectsByLine) {
                if (l.size()==1) throw new IllegalArgumentException("merge 1");
                m = new MergeScenario(this, this.populations.get(frame-1).indexOf(l.get(0)), l, frame-1);
                allScenarios.add(m.getWholeScenario(maxCorrectionLength, costLim, cumCostLim)); // merge scenario
            }
        } else { // all combinations
            for (int objectNumber = 2; objectNumber<a.prevObjects.size(); ++objectNumber) {
                for (int idx = 0; idx<=a.prevObjects.size()-objectNumber; ++idx) {
                    m = new MergeScenario(this, this.populations.get(frame-1).indexOf(a.prevObjects.get(idx)), a.prevObjects.subList(idx, idx+objectNumber), frame-1);
                    allScenarios.add(m.getWholeScenario(maxCorrectionLength, costLim, cumCostLim));
                }
            }
        }
        for (Region r : a.nextObjects) {
            SplitScenario ss =new SplitScenario(BacteriaClosedMicrochannelTrackerLocalCorrections.this, r, frame);
            allScenarios.add(ss.getWholeScenario(maxCorrectionLength, costLim, cumCostLim));
        }
        return getBestScenario(allScenarios, a.ta.verboseLevel);
    }
    /**
     * Testing stage. Mutltiple Split/Merge in one scenario
     * @param a
     * @param frame
     * @return 
     */
    private int[] performCorrectionMultipleObjects(Assignment a, int frame) {
        if (debugCorr) logger.debug("performing correction multiple objects: {}", this);

        // Todo : rearrange objects from next au lieu de toutes les combinaisons de merge..
        List<CorrectionScenario> scenarios = new ArrayList<>();
        for (int iMerge = 0; iMerge+1<a.objectCountPrev(); ++iMerge) scenarios.add(new MergeScenario(BacteriaClosedMicrochannelTrackerLocalCorrections.this, iMerge+a.idxPrev, a.prevObjects.subList(iMerge, iMerge+2), frame-1)); // merge one @ previous
        if (a.objectCountPrev()>2 && a.objectCountNext()<=2) scenarios.add(new MergeScenario(BacteriaClosedMicrochannelTrackerLocalCorrections.this, a.idxPrev, a.prevObjects, frame-1)); // merge all previous objects

        scenarios.add(new RearrangeObjectsFromPrev(BacteriaClosedMicrochannelTrackerLocalCorrections.this, frame, a)); // TODO: TEST INSTEAD OF SPLIT / SPLITANDMERGE

        return getBestScenario(scenarios, a.ta.verboseLevel);
    }
    /**
     * 
     * @param scenarios
     * @param verboseLevel
     * @return Best scenario among {@param scenarios}: first minimizes error number, minimal error number should be inferior to current error number, second minimize corection socre among scenario that yield in the same error number
     */
    private int[] getBestScenario(Collection<CorrectionScenario> scenarios, int verboseLevel) {
        scenarios.removeIf(c ->((c instanceof MultipleScenario) && ((MultipleScenario)c).scenarios.isEmpty()) || c.cost > ((c instanceof MultipleScenario)? cumCostLim : costLim));
        if (scenarios.isEmpty()) return null;
        // try all scenarios and check error number
        int fMin = Collections.min(scenarios, (c1, c2)->Integer.compare(c1.frameMin, c2.frameMin)).frameMin;
        int fMax = Collections.max(scenarios, (c1, c2)->Integer.compare(c1.frameMax, c2.frameMax)).frameMax;
        double currentErrors = getErrorNumber(fMin, fMax+1, false);
        ObjectAndAttributeSave saveCur = new ObjectAndAttributeSave(fMin, fMax);
        final Map<CorrectionScenario, ObjectAndAttributeSave> saveMap = new HashMap<>(scenarios.size());
        final Map<CorrectionScenario, Integer> errorMap = new HashMap<>(scenarios.size());

        for (CorrectionScenario c : scenarios) {
            c.applyScenario();
            errorMap.put(c, getErrorNumber(fMin, fMax+1, true));
            saveMap.put(c, new ObjectAndAttributeSave(c.frameMin, c.frameMax));
            if (correctionStep) snapshot("step:"+snapshotIdx+"/"+c, false);
            if (debugCorr && verboseLevel<verboseLevelLimit) logger.debug("compare corrections: errors current: {}, scenario: {}:  errors: {}, cost: {} frames [{};{}]",currentErrors, c, errorMap.get(c), c.cost, c.frameMin, c.frameMax);
            saveCur.restore(c.frameMin, c.frameMax);
            for (int t = Math.max(minT+1, c.frameMin); t<=Math.min(c.frameMax+1, maxT-1); ++t)  setAssignmentToTrackAttributes(t, false);
        }
        CorrectionScenario best = Collections.min(scenarios, (CorrectionScenario o1, CorrectionScenario o2) -> {
            int comp = Integer.compare(errorMap.get(o1), errorMap.get(o2)); // min errors
            if (comp==0) comp = Double.compare(o1.cost, o2.cost); // min cost if same error number
            return comp;
        });
        if (errorMap.get(best)<currentErrors) { 
            saveMap.get(best).restoreAll();
            //return new int[]{Math.max(minT+1, best.frameMin), Math.min(best.frameMax+1, maxT-1)};
            return new int[]{Math.max(minT+1, fMin), Math.min(fMax+1, maxT-1)};
        }

        return null;
    }
    
    /**
     * Class recording object attributes in order to test correction scenario 
     */
    protected class ObjectAndAttributeSave {
        final List<Region>[] objects;
        final Map<Region, TrackAttribute> taMap;
        final int tpMin;
        protected ObjectAndAttributeSave(int tpMin, int tpMax) {
            if (tpMin<0) tpMin = 0;
            if (tpMax>=maxT) tpMax = maxT-1;
            this.tpMin=tpMin;
            objects = new List[tpMax-tpMin+1];
            taMap = new HashMap<>();
            for (int t = tpMin; t<=tpMax; ++t) {
                objects[t-tpMin] = new ArrayList(getObjects(t));
                for (Region o : getObjects(t)) {
                    TrackAttribute ta = objectAttributeMap.get(o);
                    if (ta!=null) taMap.put(o, ta);
                }
            }
        }
        public void restoreAll() {
            for (int i = 0; i<objects.length; i++) {
                populations.put(i+tpMin, new ArrayList(objects[i]));
                for (Region o : objects[i]) objectAttributeMap.replace(o, taMap.get(o));
                resetIndices(i+tpMin);
            }
        }
        public boolean restore(int tMin, int tMaxIncluded) {
            if (tMaxIncluded<tpMin) return false;
            if (tMin<tpMin) tMin=tpMin;
            if (tMaxIncluded>=tpMin+objects.length) tMaxIncluded=tpMin+objects.length-1;
            for (int t = tMin; t<=tMaxIncluded; ++t) {
                populations.put(t, new ArrayList(objects[t-tpMin]));
                for (Region o : populations.get(t)) objectAttributeMap.replace(o, taMap.get(o));
                resetIndices(t);
            }
            return true;
        }
        public boolean restore(int t) {
            if (t<tpMin || t>=tpMin+objects.length) return false;
            populations.put(t,new ArrayList(objects[t-tpMin]));
            for (Region o : populations.get(t)) objectAttributeMap.replace(o, taMap.get(o));
            resetIndices(t);
            return true;
        }
    }
    
    /**
     * For testing purpose: will record a snap shot of the current tracking state
     * @param name
     * @param increment 
     */
    protected void snapshot(String name, boolean increment) {
        if (snapshotIdx>correctionStepLimit) return; // limit the number of snapshots
        if (name==null) {
            if (increment) name = "End of Step: "+snapshotIdx;
            else name = "Step: "+snapshotIdx;
        }
        List<StructureObject> newParents = new ArrayList<>(parentsByF.size());
        for (StructureObject p : parentsByF.values()) newParents.add(p.duplicate(true, false));
        Collections.sort(newParents);
        StructureObjectUtils.setTrackLinks(newParents);
        stepParents.put(name, newParents);
        // perform assignment without corrections?
        for (int t = minT+1; t<maxT; ++t)  setAssignmentToTrackAttributes(t, false);
        applyLinksToParents(newParents);
        if (increment) {
            ++snapshotIdx;
            logger.debug("start of step: {}", snapshotIdx);
        }
    }
    
}
