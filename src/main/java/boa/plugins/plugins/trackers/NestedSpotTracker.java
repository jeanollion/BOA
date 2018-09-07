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

import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.PostFilterSequence;
import boa.configuration.parameters.StructureParameter;
import boa.configuration.parameters.TrackPreFilterSequence;
import boa.data_structure.Region;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectTracker;
import boa.data_structure.StructureObjectUtils;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import fiji.plugin.trackmate.Spot;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.processing.bacteria_spine.BacteriaSpineCoord;
import boa.image.processing.bacteria_spine.BacteriaSpineFactory;
import static boa.image.processing.bacteria_spine.BacteriaSpineFactory.verboseZoomFactor;
import boa.image.processing.bacteria_spine.BacteriaSpineLocalizer;
import static boa.image.processing.bacteria_spine.BacteriaSpineLocalizer.PROJECTION.NEAREST_POLE;
import static boa.image.processing.bacteria_spine.BacteriaSpineLocalizer.PROJECTION.PROPORTIONAL;
import static boa.image.processing.bacteria_spine.BacteriaSpineLocalizer.project;
import boa.image.processing.bacteria_spine.SpineOverlayDrawer;
import static boa.image.processing.bacteria_spine.SpineOverlayDrawer.trimSpine;
import static boa.measurement.MeasurementExtractor.numberFormater;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.jgrapht.graph.DefaultWeightedEdge;
import boa.plugins.Segmenter;
import boa.plugins.SegmenterSplitAndMerge;
import boa.plugins.TestableProcessingPlugin;
import boa.plugins.ToolTip;
import boa.plugins.Tracker;
import boa.plugins.TrackerSegmenter;
import boa.plugins.plugins.processing_pipeline.SegmentOnly;
import boa.plugins.plugins.segmenters.SpotSegmenter;
import boa.plugins.plugins.trackers.nested_spot_tracker.DistanceComputationParameters;
import boa.plugins.plugins.trackers.nested_spot_tracker.NestedSpot;
import boa.plugins.plugins.trackers.nested_spot_tracker.post_processing.MutationTrackPostProcessing;
import boa.plugins.plugins.trackers.trackmate.TrackMateInterface;
import boa.plugins.plugins.trackers.trackmate.TrackMateInterface.SpotFactory;
import static boa.plugins.plugins.trackers.trackmate.TrackMateInterface.logger;
import boa.ui.GUI;
import boa.utils.ArrayFileWriter;
import boa.utils.HashMapGetCreate;
import boa.utils.MultipleException;
import boa.utils.Pair;
import boa.utils.SlidingOperator;
import boa.utils.SymetricalPair;
import boa.utils.Utils;
import static boa.utils.Utils.parallele;
import boa.utils.geom.Point;
import ij.gui.Overlay;
import java.awt.Color;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 *
 * @author jollion
 */
public class NestedSpotTracker implements TrackerSegmenter, TestableProcessingPlugin, ToolTip {
    private static final String CONF_HINT = "<br />Configuration hint: to display distance between two spots, select the two spots on test images and choose <em>Diplay Spine</em> from right-click menu. Distance will be logged in the console and projection of source spot to destination bacteria displayed";
    protected PluginParameter<Segmenter> segmenter = new PluginParameter<>("Segmentation algorithm", Segmenter.class, new SpotSegmenter(), false);
    StructureParameter compartirmentStructure = new StructureParameter("Compartiment Structure", -1, false, false).setToolTipText("Structure of bacteria objects.");
    NumberParameter spotQualityThreshold = new NumberParameter<>("Spot Quality Threshold", 3, 3.5).setEmphasized(true).setToolTipText("Spot with quality parameter over this threshold are considered as high quality spots, others as low quality spots");
    NumberParameter maxGap = new BoundedNumberParameter("Maximum frame gap", 0, 1, 0, null).setEmphasized(true).setToolTipText("Maximum frame gap for spot linking: if two spots are separated by more frame than this value they cannot be linked together directly");
    NumberParameter maxLinkingDistance = new BoundedNumberParameter("Maximum Linking Distance (FTF)", 2, 0.4, 0, null).setToolTipText("Maximum linking distance for frame-to-frame step, in unit (microns). If two spots are separated by a distance (relative to the nereast pole) superior to this value, they cannot be linked together."+CONF_HINT);
    NumberParameter maxLinkingDistanceGC = new BoundedNumberParameter("Maximum Linking Distance", 2, 0.75, 0, null).setToolTipText("Maximum linking distance for theglobal linking step, in unit (microns). If two spots are separated by a distance (relative to the nereast pole) superior to this value, they cannot be linked together. An additional cost proportional to the gap is added to the distance between spots (see <em>gap penalty</em>"+CONF_HINT);
    NumberParameter gapPenalty = new BoundedNumberParameter("Gap Distance Penalty", 2, 0.15, 0, null).setEmphasized(true).setToolTipText("When two spots are separated by a gap, an additional distance is added to their distance: this value x number of frame of the gap");
    NumberParameter alternativeDistance = new BoundedNumberParameter("Alternative Distance", 2, 0.77, 0, null).setToolTipText("The algorithm performs a global optimization minimizing the global cost. Cost are the distance between spots. Alternative distance represent the cost of being linked with no other spot. If this value is too low, the algorithm won't link any spot, it should be superior to the linking distance threshold");
    ChoiceParameter projectionType = new ChoiceParameter("Projection type", Utils.toStringArray(BacteriaSpineLocalizer.PROJECTION.values()), PROPORTIONAL.toString(), false ).setToolTipText("Spine projection for distance calculation: spine coordinate of source spots are first computed and then projected in destination bacteria. <ol><li>"+PROPORTIONAL+": spine coordinate is multiplied by the ratio of the spine length of the two bacteria</li><li>"+NEAREST_POLE+": distance (in terms of path along the spine) to nearest pole is conseverd</li></ol><br />"+CONF_HINT);
    BooleanParameter projectOnSameSide = new BooleanParameter("Project on same side", true).setToolTipText("Wether point should be projected on same side of the spine as destination point. <br />This allows to remove the rotation of the bacteria along its spine axis");
    Parameter[] parameters = new Parameter[]{segmenter, compartirmentStructure, projectionType, projectOnSameSide, maxLinkingDistance, maxLinkingDistanceGC, maxGap, gapPenalty, alternativeDistance, spotQualityThreshold};
    String toolTip = "<b>Tracker for spots moving within bacteria</b> using <em>TrackMate (https://imagej.net/TrackMate)</em> <br />"
            + "<ul><li>Distance between spots is computed using spine coordinates in order to take into acount bacteria growth and movements</li>"
            + "<li>Bacteria lineage is honoured: two spots can only be linked if they are contained in bacteria from the same track or connected tracks (after division events)</li>"
            + "<li>If segmentation and tracking are performed jointly, a first step of removal of low-quality (LQ) spots (spot that can be either false-negative or true-positive) will be applied: only LQ spots that can be linked (directly or indirectly) to high-quality (HQ) spots (ie spots that are true-positives) are kept, allowing a better selection of true-positives spots of low intensity. HQ/LQ definition depends on the parameter <em>Spot Quality Threshold</em> and depends on the quality parameter defined by the segmenter</li>"
            + "<li>A global linking procedure frame-to-frame then - allowing gaps (if <em>Maximum frame gap</em> is >0) - is applied among remaining spots</li></ul>";
    // multithreaded interface
        
    public NestedSpotTracker setCompartimentStructure(int compartimentStructureIdx) {
        this.compartirmentStructure.setSelectedStructureIdx(compartimentStructureIdx);
        return this;
    }
    public NestedSpotTracker setLinkingMaxDistance(double maxDist, double alternativeLinking) {
        maxLinkingDistance.setValue(maxDist);
        alternativeDistance.setValue(alternativeLinking);
        return this;
    }
    public NestedSpotTracker setGapParameters(double maxDistGapClosing, double gapPenalty, int maxFrameGap) {
        this.maxLinkingDistanceGC.setValue(maxDistGapClosing);
        this.gapPenalty.setValue(gapPenalty);
        this.maxGap.setValue(maxFrameGap);
        return this;
    }
    public NestedSpotTracker setSpotQualityThreshold(double threshold) {
        this.spotQualityThreshold.setValue(threshold);
        return this;
    }
    public NestedSpotTracker setSegmenter(Segmenter s) {
        
        segmenter.setPlugin(s);
        return this;
    }
    @Override public void segmentAndTrack(int structureIdx, List<StructureObject> parentTrack, TrackPreFilterSequence trackPreFilters, PostFilterSequence postFilters) {
        long t0 = System.currentTimeMillis();
        SegmentOnly ps = new SegmentOnly(segmenter.instanciatePlugin()).setTrackPreFilters(trackPreFilters).setPostFilters(postFilters);
        ps.segmentAndTrack(structureIdx, parentTrack);
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
        DistanceComputationParameters distParams = new DistanceComputationParameters()
                .setQualityThreshold(spotQualityThreshold)
                .setGapDistancePenalty(gapPenalty.getValue().doubleValue())
                .setAlternativeDistance(alternativeDistance.getValue().doubleValue())
                .setAllowGCBetweenLQ(true)
                .setMaxFrameDifference(maxGap)
                .setProjectionType(BacteriaSpineLocalizer.PROJECTION.valueOf(projectionType.getSelectedItem()))
                .setProjOnSameSide(this.projectOnSameSide.getSelected());
        
        logger.debug("distanceFTF: {}, distance GC: {}, gapP: {}, atl: {}", maxLinkingDistance, maxLinkingDistanceGC, gapPenalty, alternativeDistance);
        
        final Map<Region, StructureObject> mutationMapParentBacteria = StructureObjectUtils.getAllChildrenAsStream(parentTrack.stream(), structureIdx)
                .collect(Collectors.toMap(m->m.getRegion(), m->{
                    List<StructureObject> candidates = m.getParent().getChildren(compartimentStructure);
                    return StructureObjectUtils.getContainer(m.getRegion(), candidates, null); 
                }));
        final Map<StructureObject, List<Region>> bacteriaMapMutation = mutationMapParentBacteria.keySet().stream().collect(Collectors.groupingBy(m->mutationMapParentBacteria.get(m)));
        // get all potential spine localizer: for each bacteria with mutation look if there are bacteria with mutations in previous bacteria whithin gap range
        Set<StructureObject> parentWithSpine = new HashSet<>();
        parentWithSpine.addAll(bacteriaMapMutation.keySet());
        bacteriaMapMutation.keySet().forEach(b-> {
            int gap = 1;
            StructureObject prev =b.getPrevious();
            while (prev!=null && gap<maxGap && !bacteriaMapMutation.containsKey(prev)) {
                ++gap;
                prev = prev.getPrevious();
            }
            if (prev!=null && (gap<maxGap || bacteriaMapMutation.containsKey(prev))) { // add all bacteria between b & prev
                StructureObject p = b.getPrevious();
                if (b.isTrackHead()) parentWithSpine.addAll(b.getDivisionSiblings(false)); // for division point computation
                while(p!=prev) {
                    parentWithSpine.add(p);
                    if (p.isTrackHead()) parentWithSpine.addAll(p.getDivisionSiblings(false)); // for division point computation
                    p=p.getPrevious();
                }
            }
        });
        //Map<StructureObject, BacteriaSpineLocalizer> lMap = parallele(parentWithSpine.stream(), true).collect(Collectors.toMap(b->b, b->new BacteriaSpineLocalizer(b.getRegion()))); // spine are long to compute: better performance when computed all at once
        MultipleException me = new MultipleException();
        Map<StructureObject, BacteriaSpineLocalizer> lMap = Utils.toMapWithNullValues(parallele(parentWithSpine.stream(), true), b->b, b->new BacteriaSpineLocalizer(b.getRegion()), true, me);  // spine are long to compute: better performance when computed all at once
        final HashMapGetCreate<StructureObject, BacteriaSpineLocalizer> localizerMap = HashMapGetCreate.getRedirectedMap((StructureObject s) -> {
            try {
                return new BacteriaSpineLocalizer(s.getRegion());
            } catch(Throwable t) {
                me.addExceptions(new Pair<>(s.toString(), t));
                return null;
            }
        }, HashMapGetCreate.Syncronization.SYNC_ON_KEY);
        localizerMap.putAll(lMap);
        
        TrackMateInterface<NestedSpot> tmi = new TrackMateInterface<>(new SpotFactory<NestedSpot>() {
            @Override
            public NestedSpot toSpot(Region o, int frame) {
                StructureObject b = mutationMapParentBacteria.get(o);
                if (b==null) {
                    me.addExceptions(new Pair<>(parentTrack.stream().filter(p->p.getFrame()==frame).findAny()+"-spot#"+o.getLabel(), new RuntimeException("Mutation's parent bacteria not found")));
                    return null;
                }
                if (localizerMap.get(b)==null) {
                    me.addExceptions(new Pair<>(b+"-spot#"+o.getLabel(), new RuntimeException("Mutation's parent bacteria spine not found")));
                    return null;
                }
                if (o.getCenter()==null) o.setCenter(o.getGeomCenter(false));
                return new NestedSpot(o, b, localizerMap, distParams);
            }

            @Override
            public NestedSpot duplicate(NestedSpot s) {
                return s.duplicate();
            }
        });
        Map<Integer, List<StructureObject>> objectsF = StructureObjectUtils.getChildrenByFrame(parentTrack, structureIdx);
        long t0 = System.currentTimeMillis();
        tmi.addObjects(objectsF);
        long t1 = System.currentTimeMillis();
        if (LQSpots || logger.isDebugEnabled()) {
            int lQCount = 0;
            for (NestedSpot s : tmi.spotObjectMap.keySet()) if (s.isLowQuality()) ++lQCount;
            logger.debug("LAP Tracker: {}, spot HQ: {}, #spots LQ: {} (thld: {}), time: {}", parentTrack.get(0), tmi.spotObjectMap.size()-lQCount, lQCount, spotQualityThreshold, t1-t0);
        }
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
                MutationTrackPostProcessing postProcessor = new MutationTrackPostProcessing(structureIdx, parentTrack, tmi.objectSpotMap, o->tmi.removeObject(o.getRegion(), o.getFrame()));
                postProcessor.connectShortTracksByDeletingLQSpot(maxLinkingDistanceGC);
                removeUnlinkedLQSpots(parentTrack, structureIdx, tmi);
                objectsF = StructureObjectUtils.getChildrenByFrame(parentTrack, structureIdx);
            } else return;
        }
        long t2 = System.currentTimeMillis();
        boolean ok = true; 
        ok = tmi.processFTF(maxLinkingDistance);
        if (ok) ok = tmi.processGC(maxLinkingDistanceGC, maxGap, false, false);
        if (ok) {
            //switchCrossingLinksWithLQBranches(tmi, maxLinkingDistanceGC/Math.sqrt(2), maxLinkingDistanceGC, maxGap); // remove crossing links
            tmi.setTrackLinks(objectsF);
            MutationTrackPostProcessing postProcessor = new MutationTrackPostProcessing(structureIdx, parentTrack, tmi.objectSpotMap, o->tmi.removeObject(o.getRegion(), o.getFrame())); // TODO : do directly in graph
            postProcessor.connectShortTracksByDeletingLQSpot(maxLinkingDistanceGC); //
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
        
        // DISPLAY SPINE ON TEST IMAGE THROUGH RIGHT-CLICK
        if (stores!=null) {
            BiConsumer<List<StructureObject>, Boolean> displayDistance =  (l, drawDistances) -> {
                if (l.size()==2 && l.get(0).getStructureIdx()==structureIdx && l.get(1).getStructureIdx()==structureIdx) {
                    Collections.sort(l);
                    StructureObject b1 = mutationMapParentBacteria.get(l.get(0).getRegion());
                    if (b1==null) {
                        logger.info("parent bacteria not found for mutation: "+l.get(0));
                        return;
                    }
                    BacteriaSpineLocalizer bsl1 = localizerMap.get(b1);
                    if (bsl1==null) {
                        logger.info("bacteria spine localizer not computable for bacteria: "+b1);
                        return;
                    }
                    bsl1.setTestMode(true);
                    if ( l.get(0).getRegion().getCenter()==null)  l.get(0).getRegion().setCenter( l.get(0).getRegion().getGeomCenter(false).translate(l.get(0).getBounds()));
                    if ( l.get(1).getRegion().getCenter()==null)  l.get(1).getRegion().setCenter( l.get(1).getRegion().getGeomCenter(false).translate(l.get(1).getBounds()));
                    
                    logger.info("spot: {} center: {} bact coords: {} (other : {})", l.get(0), l.get(0).getRegion().getCenter().duplicate().translateRev(l.get(0).getBounds()), bsl1.getSpineCoord(l.get(0).getRegion().getCenter()));
                    StructureObject b2 = mutationMapParentBacteria.get(l.get(1).getRegion());
                    if (b2==null) {
                        logger.info("parent bacteria not found for mutation: "+l.get(1));
                        return;
                    }
                    BacteriaSpineLocalizer bsl2 = localizerMap.get(b2);
                    bsl2.setTestMode(true);
                    if (bsl2==null) {
                        logger.info("bacteria spine localizer not computable for bacteria: "+b2);
                        return;
                    }
                    
                    logger.info("spot: {} center: {}, bact coords: {} (other : {})", l.get(1), l.get(1).getRegion().getCenter().duplicate().translateRev(l.get(1).getBounds()), bsl2.getSpineCoord(l.get(1).getRegion().getCenter()));
                    
                    // draw source with point
                    StructureObject mc1 = b1.getParent();
                    BacteriaSpineCoord coord = bsl1.getSpineCoord(l.get(0).getRegion().getCenter());
                    Overlay spineSource = SpineOverlayDrawer.getSpineOverlay(trimSpine(bsl1.spine, 0.3), mc1.getBounds(), Color.BLUE, Color.YELLOW, 0.5);
                    SpineOverlayDrawer.drawPoint(spineSource, mc1.getBounds(), l.get(0).getRegion().getCenter(), Color.ORANGE, 2);
                    
                    // draw dest with point
                    StructureObject mc2 = b2.getParent();
                    BacteriaSpineCoord coord2 = bsl2.getSpineCoord(l.get(1).getRegion().getCenter());
                    Overlay spineDest = SpineOverlayDrawer.getSpineOverlay(trimSpine(bsl2.spine, 0.3), mc2.getBounds(), Color.BLUE, Color.YELLOW, 0.5);
                    SpineOverlayDrawer.drawPoint(spineDest, mc2.getBounds(), l.get(1).getRegion().getCenter(), new Color(0, 150, 0), 2);
                    
                    // actual projection
                    distParams.includeLQ = true;
                    NestedSpot s1 = new NestedSpot(l.get(0).getRegion(), b1, localizerMap, distParams);
                    NestedSpot s2 = new NestedSpot(l.get(1).getRegion(), b2, localizerMap, distParams);
                    Point proj = project(l.get(0).getRegion().getCenter(), b1, b2, distParams.projectionType, localizerMap, true);
                    SpineOverlayDrawer.drawPoint(spineDest, mc2.getBounds(), proj, Color.ORANGE, 2);
                    
                    // display
                    SpineOverlayDrawer.display("Source", mc1.getRawImage(l.get(0).getStructureIdx()), spineSource);
                    SpineOverlayDrawer.display("Destination", mc2.getRawImage(l.get(0).getStructureIdx()), spineDest);
                    
                    // also add to object attributes
                    l.get(0).setAttribute("Curvilinear Coordinate", coord==null? "could not be computed": numberFormater.apply(coord.curvilinearCoord(false))+"/"+numberFormater.apply(coord.spineLength()));
                    l.get(0).setAttribute("Radial Coordinate", coord==null? "could not be computed": numberFormater.apply(coord.radialCoord(false))+"/"+numberFormater.apply(coord.spineRadius()));
                    l.get(1).setAttribute("Curvilinear Coordinate", coord2==null? "could not be computed": numberFormater.apply(coord2.curvilinearCoord(false))+"/"+numberFormater.apply(coord2.spineLength()));
                    l.get(1).setAttribute("Radial Coordinate", coord2==null? "could not be computed": numberFormater.apply(coord2.radialCoord(false))+"/"+numberFormater.apply(coord2.spineRadius()));
                    
                    // log distance
                    if (proj!=null) {
                        
                        double nDist =  Math.sqrt(s1.squareDistanceTo(s2));
                        GUI.log("Distance "+l.get(0)+"->"+l.get(1)+"="+proj.dist(l.get(1).getRegion().getCenter())*l.get(0).getScaleXY()+"µm"+ " (with corrections: "+nDist+"µm)");
                        logger.info("Distance {} -> {} = {} µm. Nested Spot distance: {}", l.get(0), l.get(1), proj.dist(l.get(1).getRegion().getCenter())*l.get(0).getScaleXY(),nDist);
                        if (Double.isInfinite(nDist)) {
                            logger.debug("2 LQ: {}", !distParams.includeLQ && (s1.isLowQuality() || s2.isLowQuality()));
                            logger.debug("max frame diff: {}", s2.frame()-s1.frame()>distParams.maxFrameDiff);
                            
                        }
                    } else {
                        GUI.log("Point could not be projected");
                        logger.info("Point could not be projected");
                    }
                    
                    /*
                    Image spine1 = bsl1.spine.drawSpine(verboseZoomFactor, drawDistances).setName("Source Spine: "+b1);
                    BacteriaSpineLocalizer.drawPoint(l.get(0).getRegion().getCenter(), spine1, verboseZoomFactor, 1000);
                    spine1.setCalibration(spine1.getScaleXY() * l.get(0).getScaleXY(), 1);
                    ImageWindowManagerFactory.showImage(spine1);
                    Image spine2 = bsl2.spine.drawSpine(verboseZoomFactor, drawDistances).setName("Destination Spine: "+b2);
                    BacteriaSpineLocalizer.drawPoint(l.get(1).getRegion().getCenter(), spine2, verboseZoomFactor, 1001);
                    spine2.setCalibration(spine2.getScaleXY() * l.get(0).getScaleXY(), 1);
                    
                    NestedSpot s1 = new NestedSpot(l.get(0).getRegion(), b1, localizerMap, distParams);
                    NestedSpot s2 = new NestedSpot(l.get(1).getRegion(), b2, localizerMap, distParams);
                    Point proj = project(l.get(0).getRegion().getCenter(), b1, b2, distParams.projectionType, localizerMap, true);
                    if (proj!=null) {
                        BacteriaSpineLocalizer.drawPoint(proj, spine2, verboseZoomFactor, 1000);
                        logger.info("Dist {} -> {}: {} ({})", l.get(0), l.get(1), proj.dist(l.get(1).getRegion().getCenter()) * l.get(0).getScaleXY(), Math.sqrt(tmi.objectSpotMap.get(l.get(0).getRegion()).squareDistanceTo(tmi.objectSpotMap.get(l.get(1).getRegion()))));
                    } else logger.info("Could not project point");
                    
                    ImageWindowManagerFactory.showImage(spine2);
                    */
                    
                }
            };
            parentTrack.forEach((p) -> {
                stores.get(p).addMisc("Display Spine", l->displayDistance.accept(l, false));
                //stores.get(p).addMisc("Display Spine With Distance", l->displayDistance.accept(l, true));
            });
        }
        if (!me.isEmpty()) throw me;
    }
    
    
    private static void trimLQExtremityWithGaps(TrackMateInterface<NestedSpot> tmi, double gapTolerance, boolean start, boolean end) {
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
    private static boolean addLQSpot(TrackMateInterface<NestedSpot> tmi, DefaultWeightedEdge e, double gapTolerance, boolean start, boolean end, Set<DefaultWeightedEdge> toRemove, boolean wholeTrack) {
        //if (true) return false;
        NestedSpot s = tmi.getObject(e, true);
        NestedSpot t = tmi.getObject(e, false);
        //logger.debug("check trim: {}({})->{}({}) no gap? {}", s, s.lowQuality?"LQ":"HQ", t, t.lowQuality?"LQ":"HQ",t.frame-s.frame-1<gapTolerance );
        if (t.frame()-s.frame()-1<gapTolerance) return false; // no gap
        if (start && s.isLowQuality()) {
            NestedSpot prev = tmi.getPrevious(s);
            if (prev==null || toRemove.contains(tmi.getEdge(prev, s))) { // start of track -> remove edge
                //logger.debug("start trim {}->{}", s, t);
                toRemove.add(e);
                if (wholeTrack) {
                    // check if following edge verify same conditions
                    NestedSpot n = tmi.getNext(t);
                    DefaultWeightedEdge nextEdge = tmi.getEdge(t, n);
                    //logger.debug("start trim {}->{}, check trim: {}->{} edge null? {}, LQ: {}, np gap{}", s, t, t, n, nextEdge==null, t.lowQuality, n.frame-t.frame-1<=gapTolerance);
                    while(n!=null && nextEdge!=null && addLQSpot(tmi, nextEdge, gapTolerance, start, end, toRemove, false)) {
                        NestedSpot nn = tmi.getNext(n);
                        nextEdge = tmi.getEdge(n, nn);
                        //logger.debug("start trim {}->{}, check trim: {}->{} edge null? {}", s, t, n, nn, nextEdge==null);
                        n=nn;
                    }
                }
                return true;
            } 
        }
        if (end && t.isLowQuality()) {
            NestedSpot next = tmi.getNext(t);
            if (next==null || toRemove.contains(tmi.getEdge(t, next))) {
                //logger.debug("end trim {}->{}", s, t);
                toRemove.add(e);
                if (wholeTrack) {
                    // check if previous edge verify same conditions
                    NestedSpot p = tmi.getPrevious(s);
                    DefaultWeightedEdge prevEdge = tmi.getEdge(p, s);
                    //logger.debug("end trim {}->{}, check trim: {}->{} edge null? {}, LQ: {}, np gap{}", s, t, p, s, prevEdge==null, t.lowQuality, p.frame-s.frame-1<=gapTolerance);
                    while(p!=null && prevEdge!=null && addLQSpot(tmi, prevEdge, gapTolerance, start, end, toRemove, false)) {
                        NestedSpot pp = tmi.getPrevious(p);
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
    /*
    private static void switchCrossingLinksWithLQBranches(TrackMateInterface<NestedSpot> tmi, double spatialTolerance, double distanceThld, int maxGap) {
        long t0 = System.currentTimeMillis();
        double distanceSqThld = distanceThld*distanceThld;
        Set<SymetricalPair<DefaultWeightedEdge>> crossingLinks = tmi.getCrossingLinks(spatialTolerance, null);
        HashMapGetCreate<DefaultWeightedEdge, List<NestedSpot>> trackBefore = new HashMapGetCreate<>(e -> tmi.getTrack(tmi.getObject(e, true), true, false));
        HashMapGetCreate<DefaultWeightedEdge, List<NestedSpot>> trackAfter = new HashMapGetCreate<>(e -> tmi.getTrack(tmi.getObject(e, false), false, true));
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
            NestedSpot e1 = tmi.getObject(e.getKey(), true);
            NestedSpot t1 = tmi.getObject(e.getKey(), false);
            NestedSpot e2 = tmi.getObject(closestEdge, true);
            NestedSpot t2 = tmi.getObject(closestEdge, false);
            if (t2.frame()>e1.frame() && (t2.frame()-e1.frame()) <=maxGap && e1.squareDistanceTo(t2)<=distanceSqThld)  tmi.addEdge(e1, t2);
            if (t1.frame()>e2.frame() && (t1.frame()-e2.frame()) <=maxGap && e2.squareDistanceTo(t1)<=distanceSqThld)  tmi.addEdge(e2, t1);
            tmi.removeFromGraph(e.getKey());
            tmi.removeFromGraph(closestEdge);
            deletedEdges.add(e.getKey());
            deletedEdges.add(closestEdge);
        }
        long t1 = System.currentTimeMillis();
        tmi.logGraphStatus("switch LQ links", t1-t0);
    }
    
    private static boolean isLowQ(List<NestedSpot> track) {
        for (NestedSpot s : track) if (!s.isLowQuality()) return false;
        return true;
    }
    */
    private static void removeUnlinkedLQSpots(List<StructureObject> parentTrack, int structureIdx, TrackMateInterface<NestedSpot> tmi) {
        Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(parentTrack, structureIdx);
        Set<StructureObject> parentsToRelabel = new HashSet<>();
        int eraseCount = 0;
        for (List<StructureObject> list : allTracks.values()) {
            boolean hQ = false;
            for (StructureObject o : list) {
                NestedSpot s = tmi.objectSpotMap.get(o.getRegion());
                if (s!=null && !s.isLowQuality()) {
                    hQ = true;
                    break;
                }
            }
            if (!hQ) { // erase track
                for (StructureObject o : list) {
                    o.getParent().getChildren(structureIdx).remove(o);
                    tmi.removeObject(o.getRegion(), o.getFrame());
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
    

    @Override
    public String getToolTipText() {
        return toolTip;
    }

    // testable
    Map<StructureObject, TestDataStore> stores;
    @Override public void setTestDataStore(Map<StructureObject, TestDataStore> stores) {
        this.stores=  stores;
    }
    
}
