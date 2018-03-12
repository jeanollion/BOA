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
package boa.plugins.plugins.trackers;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.GroupParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.PostFilterSequence;
import boa.configuration.parameters.PreFilterSequence;
import boa.configuration.parameters.StructureParameter;
import boa.configuration.parameters.TrackPreFilterSequence;
import boa.data_structure.Region;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectTracker;
import boa.data_structure.StructureObjectUtils;
import fiji.plugin.trackmate.Spot;
import boa.image.Image;
import boa.image.ImageFloat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import org.apache.commons.lang.ArrayUtils;
import org.jgrapht.graph.DefaultWeightedEdge;
import boa.plugins.MultiThreaded;
import boa.plugins.ParameterSetupTracker;
import boa.plugins.Segmenter;
import boa.plugins.SegmenterSplitAndMerge;
import boa.plugins.ToolTip;
import boa.plugins.Tracker;
import boa.plugins.TrackerSegmenter;
import boa.plugins.plugins.processing_scheme.SegmentOnly;
import boa.plugins.plugins.segmenters.MutationSegmenter;
import boa.plugins.plugins.trackers.trackmate.DistanceComputationParameters;
import boa.plugins.plugins.trackers.trackmate.SpotCompartiment;
import static boa.plugins.plugins.trackers.trackmate.SpotCompartiment.isTruncated;
import boa.plugins.plugins.trackers.trackmate.post_processing.MutationTrackPostProcessing;
import boa.plugins.plugins.trackers.trackmate.SpotPopulation;
import boa.plugins.plugins.trackers.trackmate.SpotWithinCompartment;
import boa.plugins.plugins.trackers.trackmate.TrackMateInterface;
import boa.plugins.plugins.trackers.trackmate.TrackMateInterface.SpotFactory;
import static boa.plugins.plugins.trackers.trackmate.TrackMateInterface.logger;
import boa.utils.ArrayFileWriter;
import boa.utils.HashMapGetCreate;
import boa.utils.MultipleException;
import boa.utils.Pair;
import boa.utils.SlidingOperator;
import boa.utils.SymetricalPair;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class MutationTracker implements TrackerSegmenter, MultiThreaded, ParameterSetupTracker, ToolTip {
    public static boolean registerTMI=false;
    public static TrackMateInterface<SpotWithinCompartment> debugTMI;
    protected PluginParameter<Segmenter> segmenter = new PluginParameter<>("Segmentation algorithm", Segmenter.class, new MutationSegmenter(), false);
    StructureParameter compartirmentStructure = new StructureParameter("Compartiment Structure", 1, false, false).setToolTipText("Structure of bacteria objects.");
    NumberParameter spotQualityThreshold = new NumberParameter("Spot Quality Threshold", 3, 3.5).setToolTipText("Spot with quality parameter over this threshold are considered as high quality spots, others as low quality spots");
    NumberParameter maxGap = new BoundedNumberParameter("Maximum frame gap", 0, 2, 0, null).setToolTipText("Maximum frame gap for spot linking: if two spots are separated by more frame than this value they cannot be linked together directly");
    NumberParameter maxLinkingDistance = new BoundedNumberParameter("Maximum Linking Distance (FTF)", 2, 0.75, 0, null).setToolTipText("Maximum linking distance for frame-to-frame step, in unit (microns). If two spots are separated by a distance (relative to the nereast pole) superior to this value, they cannot be linked together");
    NumberParameter maxLinkingDistanceGC = new BoundedNumberParameter("Maximum Linking Distance", 2, 0.75, 0, null).setToolTipText("Maximum linking distance for theglobal linking step, in unit (microns). If two spots are separated by a distance (relative to the nereast pole) superior to this value, they cannot be linked together. An additional cost proportional to the gap is added to the distance between spots (see <em>gap penalty</em>");
    NumberParameter gapPenalty = new BoundedNumberParameter("Gap Distance Penalty", 2, 0.25, 0, null).setToolTipText("When two spots are separated by a gap, an additional distance is added to their distance: this value x number of frame of the gap");
    NumberParameter alternativeDistance = new BoundedNumberParameter("Alternative Distance)", 2, 0.8, 0, null).setToolTipText("The algorithm performs a global optimization minimizing the global cost. Cost are the distance between spots. Alternative distance represent the cost of being linked with no other spot. If this value is too low, the algorithm won't link any spot, it should be superior to the linking distance threshold");
    Parameter[] parameters = new Parameter[]{segmenter, compartirmentStructure, maxLinkingDistance, maxLinkingDistanceGC, maxGap, gapPenalty, alternativeDistance, spotQualityThreshold};
    String toolTip = "<b>Mutation tracking within bacteria</b> using <em>TrackMate (https://imagej.net/TrackMate)</em> <br />"
            + "<ul><li>Distance between spots is relative to the nearest bacteria pole (or division point for dividing bacteria) in order to take into acount bacteria growth</li>"
            + "<li>Bacteria lineage is honoured: two spots can only be linked if they are contained in bacteria from the same line</li>"
            + "<li>If segmentation and tracking is run at the same time, a first step of removal of low-quality (LQ) spots (spot that can be either false-negative or true-positive) will be applied: only LQ spots that can be linked (directly or indirectly) to high-quality (HQ) spots (ie spots that are true-positive for sure) are kept, allowing a better selection of true-positives spots of low intensity. HQ/LQ definition depends on the parameter <em>Spot Quality Threshold</em> and depends on the quality defined by the segmenter</li>"
            + "<li>A global linking procedure - allowing gaps (if <em>Maximum frame gap</em> is >0) - is applied among remaining spots</li></ul>";
    ExecutorService executor;
    @Override
    public void setExecutor(ExecutorService executor) {
        this.executor=executor;
    }
    
    public MutationTracker setCompartimentStructure(int compartimentStructureIdx) {
        this.compartirmentStructure.setSelectedStructureIdx(compartimentStructureIdx);
        return this;
    }
    public MutationTracker setLinkingMaxDistance(double maxDist, double alternativeLinking) {
        maxLinkingDistance.setValue(maxDist);
        alternativeDistance.setValue(alternativeLinking);
        return this;
    }
    public MutationTracker setGapParameters(double maxDistGapClosing, double gapPenalty, int maxFrameGap) {
        this.maxLinkingDistanceGC.setValue(maxDistGapClosing);
        this.gapPenalty.setValue(gapPenalty);
        this.maxGap.setValue(maxFrameGap);
        return this;
    }
    public MutationTracker setSpotQualityThreshold(double threshold) {
        this.spotQualityThreshold.setValue(threshold);
        return this;
    }
    public MutationTracker setSegmenter(Segmenter s) {
        
        segmenter.setPlugin(s);
        return this;
    }
    @Override public void segmentAndTrack(int structureIdx, List<StructureObject> parentTrack, TrackPreFilterSequence trackPreFilters, PostFilterSequence postFilters) {
        long t0 = System.currentTimeMillis();
        SegmentOnly ps = new SegmentOnly(segmenter.instanciatePlugin()).setTrackPreFilters(trackPreFilters).setPostFilters(postFilters);
        ps.segmentAndTrack(structureIdx, parentTrack, executor);
        long t1= System.currentTimeMillis();
        track(structureIdx, parentTrack, true);
    }

    @Override public Segmenter getSegmenter() {
        return segmenter.instanciatePlugin();
    }
    @Override public void track(int structureIdx, List<StructureObject> parentTrack) {
        track(structureIdx, parentTrack, false);
    }
    /**
     * Mutation tracking within bacteria using <a href="https://imagej.net/TrackMate" target="_top">TrackMate</a>
     * Distance between spots is relative to the nearest bacteria pole (or division point for dividing bacteria)
     * If {@param LQSpots} is true, a first step of removal of low-quality (LQ) spots will be applied: only LQ spots that can be linked (directly or indirectly) to high-quality (HQ) spots are kept, allowing a better selection of true-positives spots of low intensity
     * A global linking with remaining LQ and HQ spots is applied allowing gaps
     * @param structureIdx mutation structure index
     * @param parentTrack parent track containing objects to link at structure {@param structureIdx}
     * @param LQSpots whether objects of structure: {@param structureIdx} contain high- and low-quality spots (unlinkable low quality spots will be removed)
     */
    public void track(int structureIdx, List<StructureObject> parentTrack, boolean LQSpots) {
        //if (true) return;
        int compartimentStructure=this.compartirmentStructure.getSelectedIndex();
        int maxGap = this.maxGap.getValue().intValue()+1; // parameter = count only the frames where the spot is missing
        double spotQualityThreshold = LQSpots ? this.spotQualityThreshold.getValue().doubleValue() : Double.NEGATIVE_INFINITY;
        double maxLinkingDistance = this.maxLinkingDistance.getValue().doubleValue();
        double maxLinkingDistanceGC = this.maxLinkingDistanceGC.getValue().doubleValue();
        double gapPenalty = this.gapPenalty.getValue().doubleValue();
        double alternativeDistance = this.alternativeDistance.getValue().doubleValue();
        DistanceComputationParameters distParams = new DistanceComputationParameters().setQualityThreshold(spotQualityThreshold).setGapDistancePenalty(gapPenalty).setAlternativeDistance(alternativeDistance).setAllowGCBetweenLQ(true);
        
        logger.debug("distanceFTF: {}, distance GC: {}, gapP: {}, atl: {}", maxLinkingDistance, maxLinkingDistanceGC, gapPenalty, alternativeDistance);
        
        final Map<Integer, StructureObject> parentsByF = StructureObjectUtils.splitByFrame(parentTrack);
        final HashMapGetCreate<StructureObject, SpotCompartiment> compartimentMap = new HashMapGetCreate<>((StructureObject s) -> new SpotCompartiment(s));
        TrackMateInterface<SpotWithinCompartment> tmi = new TrackMateInterface<>(new SpotFactory<SpotWithinCompartment>() {
            @Override
            public SpotWithinCompartment toSpot(Region o, int frame) {
                StructureObject parent = parentsByF.get(frame);
                List<StructureObject> candidates = parent.getChildren(compartimentStructure);
                StructureObject compartimentSO = StructureObjectUtils.getInclusionParent(o, candidates, null); 
                //if (compartimentSO==null) return null;
                SpotCompartiment compartiment = compartimentMap.getAndCreateIfNecessary(compartimentSO);
                if (compartiment==null) return null;
                double[] center = o.getCenter();
                if (center==null) center = o.getGeomCenter(false);
                else center =center.clone(); // in order to avoid modifying original array
                center[0]*=o.getScaleXY();
                center[1]*=o.getScaleXY();
                if (center.length>2) center[2]*=o.getScaleZ();
                return new SpotWithinCompartment(o, frame, compartiment, center, distParams);
            }

            @Override
            public SpotWithinCompartment duplicate(SpotWithinCompartment s) {
                double[] center = new double[]{s.getFeature(Spot.POSITION_X), s.getFeature(Spot.POSITION_Y), s.getFeature(Spot.POSITION_Z)};
                return new SpotWithinCompartment(s.getObject(), s.frame, s.compartiment, center, distParams);
            }
        });
        if (registerTMI) debugTMI=tmi;
        Map<Integer, List<StructureObject>> objectsF = StructureObjectUtils.getChildrenByFrame(parentTrack, structureIdx);
        long t0 = System.currentTimeMillis();
        tmi.addObjects(objectsF);
        long t1 = System.currentTimeMillis();
        if (logger.isDebugEnabled()) {
            int lQCount = 0;
            for (SpotWithinCompartment s : tmi.spotObjectMap.keySet()) if (s.lowQuality) ++lQCount;
            logger.debug("LAP Tracker: {}, spot HQ: {}, #spots LQ: {} (thld: {}), time: {}", parentTrack.get(0), tmi.spotObjectMap.size()-lQCount, lQCount, spotQualityThreshold, t1-t0);
        }
        setSizeIncrementForTruncatedCells(compartimentMap, parentTrack, compartimentStructure);
        if (LQSpots) { // sequence to remove LQ spots
            distParams.includeLQ=false;
            boolean ok = tmi.processFTF(maxLinkingDistance); //FTF only with HQ
            distParams.includeLQ=true;
            if (ok) ok = tmi.processFTF(maxLinkingDistance); // FTF HQ+LQ
            distParams.includeLQ=true;
            if (ok) ok = tmi.processGC(maxLinkingDistanceGC, maxGap, false, false); // GC HQ+LQ (dist param: no gap closing between LQ spots)
            if (ok) {
                tmi.setTrackLinks(objectsF);
                tmi.resetEdges();
                MutationTrackPostProcessing postProcessor = new MutationTrackPostProcessing(structureIdx, parentTrack, tmi.objectSpotMap, o->tmi.removeObject(o.getObject(), o.getFrame()));
                postProcessor.connectShortTracksByDeletingLQSpot(maxLinkingDistanceGC);
                removeUnlinkedLQSpots(parentTrack, structureIdx, tmi);
                objectsF = StructureObjectUtils.getChildrenByFrame(parentTrack, structureIdx);
            } else return;
        }
        long t2 = System.currentTimeMillis();
        boolean ok = true; 
        if (ok) ok = tmi.processGC(maxLinkingDistanceGC, maxGap, false, false);
        if (ok) {
            //switchCrossingLinksWithLQBranches(tmi, maxLinkingDistanceGC/Math.sqrt(2), maxLinkingDistanceGC, maxGap); // remove crossing links
            tmi.setTrackLinks(objectsF);
            MutationTrackPostProcessing postProcessor = new MutationTrackPostProcessing(structureIdx, parentTrack, tmi.objectSpotMap, o->tmi.removeObject(o.getObject(), o.getFrame())); // TODO : do directly in graph
            //postProcessor.connectShortTracksByDeletingLQSpot(maxLinkingDistanceGC); //
            trimLQExtremityWithGaps(tmi, 2, true, true); // a track cannot start with a LQ spot separated by a gap
        }
        if (ok) {
            objectsF = StructureObjectUtils.getChildrenByFrame(parentTrack, structureIdx);
            tmi.setTrackLinks(objectsF);
        }
        
        if (LQSpots) {
            tmi.resetEdges();
            removeUnlinkedLQSpots(parentTrack, structureIdx, tmi);
        }
        
        long t3 = System.currentTimeMillis();
        
        // relabel
        for (StructureObject p: parentTrack) {
            Collections.sort(p.getChildren(structureIdx), ObjectIdxTracker.getComparator(ObjectIdxTracker.IndexingOrder.YXZ));
            p.relabelChildren(structureIdx);
        }

        logger.debug("Mutation Tracker: {}, total processing time: {}, create spots: {}, remove LQ: {}, link: {}", parentTrack.get(0), t3-t0, t1-t0, t2-t1, t3-t2);
    }
    
    private static void setSizeIncrementForTruncatedCells(Map<StructureObject, SpotCompartiment> compartimentMap , List<StructureObject> parentTrack, int compartimentStructure) {
        // compute sizeIncrements if truncated cells are present
        boolean truncated = false;
        for (SpotCompartiment c : compartimentMap.values()) if (c.truncated) {truncated=true; break;}
        if (!truncated) return;
        if (truncated) { // compute only on mother tracks
            List<StructureObject> pTrack = parentTrack;
            Map<StructureObject, List<StructureObject>> compartimentTracks = StructureObjectUtils.getAllTracks(pTrack, compartimentStructure);
            List<StructureObject> compartimentList = new ArrayList<>();
            List<Double> SIList=new ArrayList<>();
            int startMother = 0;
            while(startMother<parentTrack.size() && parentTrack.get(startMother).getChildren(compartimentStructure).isEmpty()) ++startMother;
            Comparator<StructureObject> c = (o1, o2)-> Integer.compare(o1.getBounds().getyMin(), o2.getBounds().getyMin());
            if (startMother<parentTrack.size()) {
                List<StructureObject> nexts = new ArrayList<>(5);
                StructureObject mother = Collections.min(parentTrack.get(startMother).getChildren(compartimentStructure),  c);
                List<StructureObject> currentTrack = compartimentTracks.get(mother.getTrackHead());
                while (currentTrack!=null) {
                    for (int i = 1; i<currentTrack.size(); ++i) {
                        compartimentList.add(currentTrack.get(i));
                        if (isTruncated(currentTrack.get(i)) || isTruncated(currentTrack.get(i-1))) SIList.add(Double.NaN);
                        else SIList.add((double)currentTrack.get(i).getObject().getSize() / (double)currentTrack.get(i-1).getObject().getSize());
                    }
                    putNext(currentTrack.get(currentTrack.size()-1), nexts);
                    //logger.debug("nexts: {}", nexts);
                    if (nexts.isEmpty()) break;
                    else {
                        Collections.sort(nexts, c);
                        double sizeNext = 0; for (StructureObject n : nexts) sizeNext+=n.getObject().getSize();
                        compartimentList.add(nexts.get(0));
                        if (isTruncated(currentTrack.get(currentTrack.size()-1)) || isTruncated(nexts.get(nexts.size()-1))) SIList.add(Double.NaN);
                        else SIList.add(sizeNext / currentTrack.get(currentTrack.size()-1).getObject().getSize());
                        currentTrack = compartimentTracks.get(nexts.get(0));
                    }
                }
            }
            List<Double> SIMedian = SlidingOperator.performSlideLeft(SIList, 7, SlidingOperator.slidingMedian(5));
            // Replace values where NaN obseved
            double lastNonNan=Double.NaN;
            for (int i = 0; i<SIMedian.size(); ++i) {
                if (Double.isNaN(SIList.get(i)) || Double.isNaN(SIMedian.get(i))) {
                    SIMedian.set(i, lastNonNan);
                } else lastNonNan = SIMedian.get(i);
            }
            //logger.debug("Objects sizes: {}", Utils.toStringList(compartimentList, o->o.getObject().getSize()));
            //logger.debug("Objects SI: {}", SIList);
            //logger.debug("Object SI Med: {}", SIMedian);
            for (int i =0; i<compartimentList.size(); ++i) {
                SpotCompartiment sc = compartimentMap.get(compartimentList.get(i));
                if (sc!=null) sc.sizeIncrement = SIMedian.get(i);
            }
        }
    }
    
    private static void trimLQExtremityWithGaps(TrackMateInterface<SpotWithinCompartment> tmi, double gapTolerance, boolean start, boolean end) {
        long t0 = System.currentTimeMillis();
        //--gapTolerance;
        Set<DefaultWeightedEdge> toRemove = new HashSet<>();
        for (DefaultWeightedEdge e : tmi.getEdges()) {
            if (toRemove.contains(e)) continue;
            addLQSpot(tmi, e, gapTolerance, start, end, toRemove, true);
        }
        tmi.logGraphStatus("before trim extremities ("+toRemove.size()+")", 0);
        tmi.removeFromGraph(toRemove, null, false);
        long t1 = System.currentTimeMillis();
        tmi.logGraphStatus("trim extremities ("+toRemove.size()+")", t1-t0);
    }
    private static boolean addLQSpot(TrackMateInterface<SpotWithinCompartment> tmi, DefaultWeightedEdge e, double gapTolerance, boolean start, boolean end, Set<DefaultWeightedEdge> toRemove, boolean wholeTrack) {
        //if (true) return false;
        SpotWithinCompartment s = tmi.getObject(e, true);
        SpotWithinCompartment t = tmi.getObject(e, false);
        //logger.debug("check trim: {}({})->{}({}) no gap? {}", s, s.lowQuality?"LQ":"HQ", t, t.lowQuality?"LQ":"HQ",t.frame-s.frame-1<gapTolerance );
        if (t.frame-s.frame-1<gapTolerance) return false; // no gap
        if (start && s.lowQuality) {
            SpotWithinCompartment prev = tmi.getPrevious(s);
            if (prev==null || toRemove.contains(tmi.getEdge(prev, s))) { // start of track -> remove edge
                //logger.debug("start trim {}->{}", s, t);
                toRemove.add(e);
                if (wholeTrack) {
                    // check if following edge verify same conditions
                    SpotWithinCompartment n = tmi.getNext(t);
                    DefaultWeightedEdge nextEdge = tmi.getEdge(t, n);
                    //logger.debug("start trim {}->{}, check trim: {}->{} edge null? {}, LQ: {}, np gap{}", s, t, t, n, nextEdge==null, t.lowQuality, n.frame-t.frame-1<=gapTolerance);
                    while(n!=null && nextEdge!=null && addLQSpot(tmi, nextEdge, gapTolerance, start, end, toRemove, false)) {
                        SpotWithinCompartment nn = tmi.getNext(n);
                        nextEdge = tmi.getEdge(n, nn);
                        //logger.debug("start trim {}->{}, check trim: {}->{} edge null? {}", s, t, n, nn, nextEdge==null);
                        n=nn;
                    }
                }
                return true;
            } 
        }
        if (end && t.lowQuality) {
            SpotWithinCompartment next = tmi.getNext(t);
            if (next==null || toRemove.contains(tmi.getEdge(t, next))) {
                //logger.debug("end trim {}->{}", s, t);
                toRemove.add(e);
                if (wholeTrack) {
                    // check if previous edge verify same conditions
                    SpotWithinCompartment p = tmi.getPrevious(s);
                    DefaultWeightedEdge prevEdge = tmi.getEdge(p, s);
                    //logger.debug("end trim {}->{}, check trim: {}->{} edge null? {}, LQ: {}, np gap{}", s, t, p, s, prevEdge==null, t.lowQuality, p.frame-s.frame-1<=gapTolerance);
                    while(p!=null && prevEdge!=null && addLQSpot(tmi, prevEdge, gapTolerance, start, end, toRemove, false)) {
                        SpotWithinCompartment pp = tmi.getPrevious(p);
                        prevEdge = tmi.getEdge(pp, p);
                        //logger.debug("end trim {}->{}, check trim: {}->{} edge null? {}", s, p, s, pp, prevEdge==null);
                        p=pp;
                    }
                }
                return true;
            }
        }
        return false;
    }
    private static void switchCrossingLinksWithLQBranches(TrackMateInterface<SpotWithinCompartment> tmi, double spatialTolerance, double distanceThld, int maxGap) {
        long t0 = System.currentTimeMillis();
        double distanceSqThld = distanceThld*distanceThld;
        Set<SymetricalPair<DefaultWeightedEdge>> crossingLinks = tmi.getCrossingLinks(spatialTolerance, null);
        HashMapGetCreate<DefaultWeightedEdge, List<SpotWithinCompartment>> trackBefore = new HashMapGetCreate<>(e -> tmi.getTrack(tmi.getObject(e, true), true, false));
        HashMapGetCreate<DefaultWeightedEdge, List<SpotWithinCompartment>> trackAfter = new HashMapGetCreate<>(e -> tmi.getTrack(tmi.getObject(e, false), false, true));
        Function<SymetricalPair<DefaultWeightedEdge>, Double> distance = p -> {
            boolean beforeLQ1 = isLowQ(trackBefore.getAndCreateIfNecessary(p.key));
            boolean afterLQ1 = isLowQ(trackAfter.getAndCreateIfNecessarySync(p.key));
            if (beforeLQ1!=afterLQ1) return Double.POSITIVE_INFINITY;
            boolean beforeLQ2 = isLowQ(trackBefore.getAndCreateIfNecessary(p.value));
            boolean afterLQ2 = isLowQ(trackAfter.getAndCreateIfNecessarySync(p.value));
            if (beforeLQ2!=afterLQ2 || beforeLQ1==beforeLQ2) return Double.POSITIVE_INFINITY;
            if (beforeLQ1) { // link before2 and after1
                return tmi.getObject(p.value, true).squareDistanceTo(tmi.getObject(p.key, false));
            } else { // link before1 and after2
                return tmi.getObject(p.key, true).squareDistanceTo(tmi.getObject(p.value, false));
            }
        };
        HashMapGetCreate<SymetricalPair<DefaultWeightedEdge>, Double> linkDistance = new HashMapGetCreate<>(p -> distance.apply(p));
        crossingLinks.removeIf(p -> linkDistance.getAndCreateIfNecessary(p)>distanceSqThld);
        Map<DefaultWeightedEdge, Set<DefaultWeightedEdge>> map = Pair.toMapSym(crossingLinks);
        Set<DefaultWeightedEdge> deletedEdges = new HashSet<>();
        for (Entry<DefaultWeightedEdge, Set<DefaultWeightedEdge>> e : map.entrySet()) {
            if (deletedEdges.contains(e.getKey())) continue;
            e.getValue().removeAll(deletedEdges);
            if (e.getValue().isEmpty()) continue;
            DefaultWeightedEdge closestEdge = e.getValue().size()==1? e.getValue().iterator().next() : Collections.min(e.getValue(), (e1, e2)-> Double.compare(linkDistance.getAndCreateIfNecessary(new SymetricalPair<>(e.getKey(), e1)), linkDistance.getAndCreateIfNecessary(new SymetricalPair<>(e.getKey(), e2))));
            SpotWithinCompartment e1 = tmi.getObject(e.getKey(), true);
            SpotWithinCompartment t1 = tmi.getObject(e.getKey(), false);
            SpotWithinCompartment e2 = tmi.getObject(closestEdge, true);
            SpotWithinCompartment t2 = tmi.getObject(closestEdge, false);
            if (t2.frame>e1.frame && (t2.frame-e1.frame) <=maxGap && e1.squareDistanceTo(t2)<=distanceSqThld)  tmi.addEdge(e1, t2);
            if (t1.frame>e2.frame && (t1.frame-e2.frame) <=maxGap && e2.squareDistanceTo(t1)<=distanceSqThld)  tmi.addEdge(e2, t1);
            tmi.removeFromGraph(e.getKey());
            tmi.removeFromGraph(closestEdge);
            deletedEdges.add(e.getKey());
            deletedEdges.add(closestEdge);
        }
        long t1 = System.currentTimeMillis();
        tmi.logGraphStatus("switch LQ links", t1-t0);
    }
    
    private static boolean isLowQ(List<SpotWithinCompartment> track) {
        for (SpotWithinCompartment s : track) if (!s.lowQuality) return false;
        return true;
    }
    private static void removeUnlinkedLQSpots(List<StructureObject> parentTrack, int structureIdx, TrackMateInterface<SpotWithinCompartment> tmi) {
        Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(parentTrack, structureIdx);
        Set<StructureObject> parentsToRelabel = new HashSet<>();
        int eraseCount = 0;
        for (List<StructureObject> list : allTracks.values()) {
            boolean hQ = false;
            for (StructureObject o : list) {
                SpotWithinCompartment s = tmi.objectSpotMap.get(o.getObject());
                if (s!=null && !s.lowQuality) {
                    hQ = true;
                    break;
                }
            }
            if (!hQ) { // erase track
                for (StructureObject o : list) {
                    o.getParent().getChildren(structureIdx).remove(o);
                    tmi.removeObject(o.getObject(), o.getFrame());
                    parentsToRelabel.add(o.getParent());
                    eraseCount++;
                }
            }
        }
        for (StructureObject p : parentsToRelabel) p.relabelChildren(structureIdx);
        logger.debug("erased LQ spots: {}", eraseCount);
    }
    
    private static void putNext(StructureObject prev, List<StructureObject> bucket) {
        bucket.clear();
        StructureObject nextP = prev.getParent().getNext();
        if (nextP==null) return;
        for (StructureObject o : nextP.getChildren(prev.getStructureIdx())) {
            if (prev.equals(o.getPrevious())) bucket.add(o);
        }
    }
    
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    // parameter setup
    @Override
    public boolean runSegmentAndTrack(String p) {
        return p.equals(spotQualityThreshold.getName());
    }

    @Override
    public boolean canBeTested(String p) {
        if (p==null) return false;
        return p.equals(spotQualityThreshold.getName()) || p.equals(maxGap.getName());
    }
    String testParam;
    @Override
    public void setTestParameter(String p) {
        testParam = p;
        logger.debug("test parameter: {}", p);
    }

    @Override
    public String getToolTipText() {
        return toolTip;
    }

    

    
    
}
