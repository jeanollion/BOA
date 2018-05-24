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
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.ConditionalParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.ParameterUtils;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.PostFilterSequence;
import boa.configuration.parameters.PreFilterSequence;
import boa.configuration.parameters.TrackPreFilterSequence;
import boa.data_structure.Region;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import static boa.data_structure.StructureObjectUtils.setTrackLinks;
import fiji.plugin.trackmate.Spot;
import boa.image.BlankMask;
import boa.image.BoundingBox;
import static boa.image.BoundingBox.getIntersection;
import static boa.image.BoundingBox.isIncluded2D;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import boa.image.SimpleBoundingBox;
import boa.image.processing.ImageOperations;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;
import boa.plugins.MultiThreaded;
import boa.plugins.Segmenter;
import boa.plugins.Thresholder;
import boa.plugins.ToolTip;
import boa.plugins.TrackParametrizable;
import boa.plugins.TrackerSegmenter;
import boa.plugins.plugins.segmenters.MicrochannelPhase2D;
import boa.plugins.MicrochannelSegmenter;
import boa.plugins.MicrochannelSegmenter.Result;
import static boa.plugins.plugins.trackers.ObjectIdxTracker.getComparator;
import boa.plugins.plugins.trackers.trackmate.TrackMateInterface;
import boa.utils.ArrayUtil;
import boa.utils.HashMapGetCreate;
import boa.utils.HashMapGetCreate.Factory;
import boa.utils.Pair;
import boa.utils.SlidingOperator;
import static boa.utils.SlidingOperator.performSlide;
import boa.utils.ThreadRunner;
import boa.utils.ThreadRunner.ThreadAction;
import boa.utils.Utils;
import java.util.TreeMap;
import boa.plugins.TrackParametrizable.TrackParametrizer;
import static boa.utils.Utils.parallele;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

/**
 *
 * @author jollion
 */
public class MicrochannelTracker implements TrackerSegmenter, MultiThreaded, ToolTip {
    protected PluginParameter<MicrochannelSegmenter> segmenter = new PluginParameter<>("Segmentation algorithm", MicrochannelSegmenter.class, new MicrochannelPhase2D(), false);
    NumberParameter maxShiftGC = new BoundedNumberParameter("Maximal Distance for Gap-Closing procedure", 0, 100, 1, null).setToolTipText("<html>Maximal Distance (in pixels) used for for the gap-closing step<br /> Increase the value to take into acound XY shift between two successive frames due to stabilization issues, but not too much to avoid connecting distinct microchannels</html>");
    NumberParameter maxDistanceFTFWidthFactor = new BoundedNumberParameter("Maximal Distance Factor for Frame-to-Frame Tracking", 1, 1, 0, null).setToolTipText("<html>The distance threshold for Frame-to-Frame tracking procedure will be this value multiplied by the mean width of microchannels.<br />If two microchannels between two successive frames are separated by a distance superior to this threshold they can't be linked. <br />Increase the value to take into acound XY shift between two successive frames due to stabilization issues, but not too much to avoid connecting distinct microchannels</html>");
    NumberParameter yShiftQuantile = new BoundedNumberParameter("Y-shift Quantile", 2, 0.5, 0, 1).setToolTipText("After Tracking, microchannel region relative y-shift (compared to the base line) are unifomized per-track, for each track: the y-shift is replaced by the quantile of all y-shift");
    NumberParameter widthQuantile = new BoundedNumberParameter("With Quantile", 2, 0.9, 0, 1).setToolTipText("After Tracking, microchannel width  are uniformized per-track, for each track: the with of every object is replaced by the quantile of all width");;
    BooleanParameter allowGaps = new BooleanParameter("Allow Gaps", true).setToolTipText("If a frame contains no microchannels (tipically when focus is lost), allow to connect microchannels track prior to the gap with thoses after the gap. This will result in microchannel tracks containing gaps. If false tracks will be disconnected");
    BooleanParameter normalizeWidths = new BooleanParameter("Normalize Widths", false);
    ConditionalParameter widthCond = new ConditionalParameter(normalizeWidths).setActionParameters("true", new Parameter[]{widthQuantile});
    BooleanParameter normalizeYshift = new BooleanParameter("Normalize Y-shifts", false);
    ConditionalParameter shiftCond = new ConditionalParameter(normalizeYshift).setActionParameters("true", new Parameter[]{yShiftQuantile});
    Parameter[] parameters = new Parameter[]{segmenter, maxShiftGC, maxDistanceFTFWidthFactor, shiftCond, widthCond, allowGaps};
    public static boolean debug = false;
    
    String toolTip = "<html><b>Microchannel tracker</b>"
            + "<p><em>Tracking procedure:</em> using TrackMate (https://imagej.net/TrackMate) in 4 steps:"
            + "<ol><li>Frame to Frame linking using \"Maximal Distance Factor for Frame-to-Frame Tracking\" parameter</li>"
            + "<li>Gap-closing linking using \"Maximal Distance for Gap-Closing procedure\" parameter</li>"
            + "<li>Removal of crossing links: if some micro-channels are missing, the gap-closing procedure can produce links that cross, as microchannels are not moving relatively.<br />Those links should be removed in order to be able to apply the gap-filling procdure (see below)</li>"
            + "<li>Gap-closing tracking for the links removed in step 3</li></ol></p>"
            + "<p><em>Gap-filling procedure:</em>"
            + "<ul><li>If a track contains a gap, tries to fill it by creating microchannels with the same dimensions as microchannels before and after the gap, and the same relative position to another reference track that exists throughout the gap</li>"
            + "<li>If no reference exist throughout the gap, ie when there are frames that contain no microchannel (tipically occurs when focus is lost), gap cannot be filled, in this case if \"Allow Gaps\" is set to false, tracks will be disconnected</li></ul></p>"
            + "<p><em>Track-wise unifomization of microchannel regions:</em>"
            + "<ul><li>Uniformization of Y-shift (relative to base line). See \"Y-shift Quantile\" parameter</li>"
            + "<li>Uniformization of width. See\"With Quantile\" parameter </li></ul></p>"
            + "</html>";
    
    public MicrochannelTracker setSegmenter(MicrochannelSegmenter s) {
        this.segmenter.setPlugin(s);
        return this;
    }
    public MicrochannelTracker setAllowGaps(boolean allowGaps) {
        this.allowGaps.setSelected(allowGaps);
        return this;
    }
    public MicrochannelTracker setYShiftQuantile(double quantile) {
        this.normalizeYshift.setSelected(true);
        this.yShiftQuantile.setValue(quantile);
        return this;
    }
    public MicrochannelTracker setWidthQuantile(double quantile) {
        this.normalizeWidths.setSelected(true);
        this.widthQuantile.setValue(quantile);
        return this;
    }
    public MicrochannelTracker setTrackingParameters(int maxShift, double maxDistanceWidthFactor) {
        this.maxShiftGC.setValue(maxShift);
        this.maxDistanceFTFWidthFactor.setValue(maxDistanceWidthFactor);
        return this;
    }
    /**
     * Tracking of microchannels using <a href="https://imagej.net/TrackMate" target="_top">TrackMate</a> in 4 steps
     * 1) Frame to Frame tracking using "Maximal Distance Factor for Frame-to-Frame Tracking" parameter
     * 2) Gap-closing tracking using "Maximal Distance for Gap-Closing procedure" parameter
     * 3) Removal of crossing links: if some micro-channels are missing, the gap-closing procedure can produce links that cross, as microchannels are not moving relatively, those links should be removed in order to be able to apply the {@link #fillGaps(int, java.util.List, boolean) gap-filling procdure}
     * 4) Gap-closing tracking for the links removed in step 3
     * @param structureIdx index of the microchannel structure
     * @param parentTrack parent track containing segmented microchannels at index {@param structureIdx}
     */
    @Override public void track(int structureIdx, List<StructureObject> parentTrack) {
        if (parentTrack.isEmpty()) return;
        TrackMateInterface<Spot> tmi = new TrackMateInterface(TrackMateInterface.defaultFactory());
        Map<Integer, List<StructureObject>> map = StructureObjectUtils.getChildrenByFrame(parentTrack, structureIdx);
        
        logger.debug("tracking: {}", Utils.toStringList(map.entrySet(), e->"t:"+e.getKey()+"->"+e.getValue().size()));
        tmi.addObjects(map);
        if (tmi.objectSpotMap.isEmpty()) {
            logger.debug("No objects to track");
            return;
        }
        double meanWidth = Utils.flattenMap(map).stream().mapToDouble(o->o.getBounds().sizeX()).average().getAsDouble()*parentTrack.get(0).getScaleXY();
        if (debug) logger.debug("mean width {}", meanWidth );
        double maxDistance = maxShiftGC.getValue().doubleValue()*parentTrack.get(0).getScaleXY();
        double ftfDistance = maxDistanceFTFWidthFactor.getValue().doubleValue() *meanWidth;
        logger.debug("ftfDistance: {}", ftfDistance);
        boolean ok = tmi.processFTF(ftfDistance);
        if (ok) ok = tmi.processGC(maxDistance, parentTrack.size(), false, false);
        if (ok) tmi.removeCrossingLinksFromGraph(meanWidth/4); 
        if (ok) ok = tmi.processGC(maxDistance, parentTrack.size(), false, false); // second GC for crossing links!
        tmi.setTrackLinks(map);
    }
    /**
     * 1) Segmentation of microchannels depending on the chosen {@link boa.plugins.MicrochannelSegmenter segmenter} 
     * 2) {@link #track(int, java.util.List) tracking of microchannels}
     * 3) {@link #fillGaps(int, java.util.List, boolean) gap-filling procedure}
     * 4) Track-Wise Normalization of microchannels width and relative y position 
     * @param structureIdx microchannel structure index
     * @param parentTrack microchannel parent track
     * @param trackPreFilters optional track pre-filters to be applied prio to segmentation step
     * @param postFilters  optinal post filters to be applied after segmentation and before tracking
     */
    @Override
    public void segmentAndTrack(int structureIdx, List<StructureObject> parentTrack, TrackPreFilterSequence trackPreFilters, PostFilterSequence postFilters) {
        if (parentTrack.isEmpty()) return;
        // segmentation
        final Result[] boundingBoxes = new Result[parentTrack.size()];
        trackPreFilters.filter(structureIdx, parentTrack);
        TrackParametrizer<? super MicrochannelSegmenter> applyToSegmenter = TrackParametrizable.getTrackParametrizer(structureIdx, parentTrack, segmenter.instanciatePlugin());
        Consumer<Integer> exe =  idx -> {
            StructureObject parent = parentTrack.get(idx);
            MicrochannelSegmenter s = segmenter.instanciatePlugin();
            if (applyToSegmenter !=null) applyToSegmenter.apply(parent, s);
            boundingBoxes[idx] = s.segment(parent.getPreFilteredImage(structureIdx));
            if (boundingBoxes[idx]==null) parent.setChildren(new ArrayList<>(), structureIdx); // if not set and call to getChildren() -> DAO will set old children
            //else parent.setChildrenObjects(postFilters.filter(boundingBoxes[idx].getObjectPopulation(inputImages[idx], false), structureIdx, parent), structureIdx); // no Y - shift here because the mean shift is added afterwards // TODO if post filter remove objects or modify -> how to link with result object??
            else parent.setChildrenObjects(boundingBoxes[idx].getObjectPopulation(parent.getPreFilteredImage(structureIdx), false), structureIdx); // no Y - shift here because the mean shift is added afterwards
            //parent.setPreFilteredImage(null, structureIdx); // save memory
        };
        ThreadRunner.executeAndThrowErrors(parallele(IntStream.range(0, parentTrack.size()).mapToObj(i->(Integer)i), multithreaded), exe);
        Map<StructureObject, Result> parentBBMap = new HashMap<>(boundingBoxes.length);
        for (int i = 0; i<boundingBoxes.length; ++i) parentBBMap.put(parentTrack.get(i), boundingBoxes[i]);
        
        // tracking
        if (debug) logger.debug("mc2: {}", Utils.toStringList(parentTrack, p->"t:"+p.getFrame()+"->"+p.getChildren(structureIdx).size()));
        track(structureIdx, parentTrack);
        fillGaps(structureIdx, parentTrack, allowGaps.getSelected());
        if (debug) logger.debug("mc3: {}", Utils.toStringList(parentTrack, p->"t:"+p.getFrame()+"->"+p.getChildren(structureIdx).size()));
        // compute mean of Y-shifts & width for each microchannel and modify objects
        Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(parentTrack, structureIdx);
        if (debug) logger.debug("mc4: {}", Utils.toStringList(parentTrack, p->"t:"+p.getFrame()+"->"+p.getChildren(structureIdx).size()));
        logger.debug("Microchannel tracker: trackHead number: {}", allTracks.size());
        List<StructureObject> toRemove = new ArrayList<>();
        for (List<StructureObject> track : allTracks.values()) { // set shift & width to all objects
            if (track.isEmpty()) continue;
            List<Integer> shifts = new ArrayList<>(track.size());
            List<Double> widths = new ArrayList<>(track.size());
            for (StructureObject o : track) {
                Result r = parentBBMap.get(o.getParent());
                if (o.getIdx()>=r.size()) { // object created from gap closing 
                    if (!widths.isEmpty()) widths.add(widths.get(widths.size()-1)); // for index consitency
                    else widths.add(null);
                    if (!shifts.isEmpty()) shifts.add(shifts.get(shifts.size()-1)); // for index consitency
                    else shifts.add(null);
                } else {
                    shifts.add(r.yMinShift[o.getIdx()]);
                    widths.add((double)r.getXWidth(o.getIdx()));
                }
            }
            // replace null at begining values by following non null value
            int nonNullIdx = 0;
            while(nonNullIdx<shifts.size() && shifts.get(nonNullIdx)==null) ++nonNullIdx;
            int nonNullValue = nonNullIdx<shifts.size() ? shifts.get(nonNullIdx) : 0;
            
            for (int i = 0; i<nonNullIdx; ++i) shifts.set(i, nonNullValue);
            //if (debug) logger.debug("shifts non null idx: {} values: {}", nonNullIdx, shifts);
            nonNullIdx = 0;
            while(nonNullIdx<widths.size() && widths.get(nonNullIdx)==null) ++nonNullIdx;
            if (nonNullIdx>=widths.size()) continue;
            for (int i = 0; i<nonNullIdx; ++i) widths.set(i, widths.get(nonNullIdx));
            //if (debug) logger.debug("widths non null idx: {} values: {}", nonNullIdx, widths);
            boolean normWidth = this.normalizeWidths.getSelected();
            boolean normShift = this.normalizeYshift.getSelected();
            int shift = !normShift ? -1 : (int)Math.round(ArrayUtil.quantiles(shifts.stream().mapToInt(Integer::intValue).toArray(), yShiftQuantile.getValue().doubleValue())[0]); // quantile function sorts the values!!
            int width = !normWidth ? -1 : (int)Math.round(ArrayUtil.quantiles(widths.stream().mapToDouble(Double::doubleValue).toArray(), widthQuantile.getValue().doubleValue())[0]); // quantile function sorts the values!!
            
            if ((normShift || normWidth) && debug)  logger.debug("track: {} ymin-shift: {}, width: {} (max: {}, )", track.get(0), shift, width, widths.get(widths.size()-1));
 
            //if (debug) logger.debug("track: {} before norm & shiftY: {}", track.get(0), Utils.toStringList(track, o->o.getBounds()));
            
            // 4) track-wise normalization of width & y-shift
            for (int i = 0; i<track.size(); ++i) {
                StructureObject o = track.get(i);
                BoundingBox b = o.getBounds();
                BoundingBox parentBounds = o.getParent().getBounds();
                int offY = b.yMin() + (normShift ?  shift : shifts.get(i)); // shift was not included before
                int offX; // if width change -> offset X change
                int currentWidth;
                if (normWidth) {
                    currentWidth = width;
                    double offXd = b.xMean()-(width-1d)/2d;
                    double offXdr = offXd-(int)offXd;
                    if (offXdr<0.5) offX=(int)offXd;
                    else if (offXdr>0.5) offX = (int)offXd + 1;
                    else { // adjust localy: compare light in both cases //TODO not a good criterion -> biais on the right side
                        MutableBoundingBox bLeft = new MutableBoundingBox((int)offXd, (int)offXd+width-1, offY, offY+b.sizeY()-1, b.zMin(), b.zMax());
                        MutableBoundingBox bRight = bLeft.duplicate().translate(1, 0, 0);
                        MutableBoundingBox bLeft2 = bLeft.duplicate().translate(-1, 0, 0);
                        bLeft.contract(parentBounds);
                        bRight.contract(parentBounds);
                        bLeft2.contract(parentBounds);
                        Image r = o.getParent().getRawImage(structureIdx);
                        double valueLeft = ImageOperations.getMeanAndSigmaWithOffset(r, bLeft.getBlankMask(), null)[0];
                        double valueLeft2 = ImageOperations.getMeanAndSigmaWithOffset(r, bLeft2.getBlankMask(), null)[0];
                        double valueRight = ImageOperations.getMeanAndSigmaWithOffset(r, bRight.getBlankMask(), null)[0];
                        if (valueLeft2>valueRight && valueLeft2>valueLeft) offX=(int)offXd-1;
                        else if (valueRight>valueLeft && valueRight>valueLeft2) offX=(int)offXd+1;
                        else offX=(int)offXd;
                        //logger.debug("offX for element: {}, width:{}>{}, left:{}={}, right:{}={} left2:{}={}", o, b, width, bLeft, valueLeft, bRight, valueRight, bLeft2, valueLeft2);
                    }
                } else {
                    offX = b.xMin();
                    currentWidth = b.sizeX();
                }
                if (currentWidth+offX>parentBounds.xMax() || offX<0) {
                    if (debug) logger.debug("remove out of bound track: {}", track.get(0).getTrackHead());
                    toRemove.addAll(track);
                    break;
                    //currentWidth = parentBounds.getxMax()-offX;
                    //if (currentWidth<0) logger.error("negative wigth: object:{} parent: {}, current: {}, prev: {}, next:{}", o, o.getParent().getBounds(), o.getBounds(), o.getPrevious().getBounds(), o.getNext().getBounds());
                }
                int height = b.sizeY();
                if (height+offY>parentBounds.yMax()) height = parentBounds.yMax()-offY;
                BlankMask m = new BlankMask( currentWidth, height, b.sizeZ(), offX, offY, b.zMin(), o.getScaleXY(), o.getScaleZ());
                o.setRegion(new Region(m, o.getIdx()+1, o.is2D()));
            }
            //if (debug) logger.debug("track: {} after norm & shiftY: {}", track.get(0), Utils.toStringList(track, o->o.getBounds()));
        }
        
        if (debug) logger.debug("mc after adjust width: {}", Utils.toStringList(parentTrack, p->"t:"+p.getFrame()+"->"+p.getChildren(structureIdx).size()));
        if (!toRemove.isEmpty()) {
            Map<StructureObject, List<StructureObject>> toRemByParent = StructureObjectUtils.splitByParent(toRemove);
            for (Entry<StructureObject, List<StructureObject>> e : toRemByParent.entrySet()) {
                e.getKey().getChildren(structureIdx).removeAll(e.getValue());
                e.getKey().relabelChildren(structureIdx);
            }
        }
        if (debug) logger.debug("mc after remove: {}", Utils.toStringList(parentTrack, p->"t:"+p.getFrame()+"->"+p.getChildren(structureIdx).size()));
        // relabel by trackHead appearance
        HashMapGetCreate<StructureObject, Integer> trackHeadIdxMap = new HashMapGetCreate(new Factory<StructureObject, Integer>() {
            int count = -1;
            @Override
            public Integer create(StructureObject key) {
                ++count;
                return count;
            }
        });
        for (StructureObject p : parentTrack) {
            List<StructureObject> children = p.getChildren(structureIdx);
            Collections.sort(children, ObjectIdxTracker.getComparator(ObjectIdxTracker.IndexingOrder.XYZ));
            for (StructureObject c : children) {
                int idx = trackHeadIdxMap.getAndCreateIfNecessary(c.getTrackHead());
                if (idx!=c.getIdx()) c.setIdx(idx);
            }
        }
        
        if (debug) logger.debug("mc end: {}", Utils.toStringList(parentTrack, p->"t:"+p.getFrame()+"->"+p.getChildren(structureIdx).size()));
    }

    /**
     * Gap-filling procedure 
     * If a track contains a gap, tries to fill it by creating microchannels with the same dimensions as microchannels before and after the gap, and the same relative position to another reference track that exists throughout the gap
     * If no reference exist throughout the gap, ie when there are frames that contain no microchannel, gap cannot be filled, in this case if {@param allowUnfilledGaps} is set to false, tracks will be disconnected
     * @param structureIdx  microchannel structure index
     * @param parentTrack microchannel parent track containing segmented and tracked microchannels
     * @param allowUnfilledGaps If a frame contains no microchannels (tipically when focus is lost), allow to connect microchannels track prior to the gap with thoses after the gap. This will result in microchannel tracks containing gaps. If false tracks will be disconnected
     */
    private static void fillGaps(int structureIdx, List<StructureObject> parentTrack, boolean allowUnfilledGaps) {
        Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(parentTrack, structureIdx);
        Map<Integer, StructureObject> reference = null; //getOneElementOfSize(allTracks, parentTrack.size()); // local reference with minimal MSD
        int minParentFrame = parentTrack.get(0).getFrame();
        for (List<StructureObject> track : allTracks.values()) {
            Iterator<StructureObject> it = track.iterator();
            StructureObject prev = it.next();
            while (it.hasNext()) {
                StructureObject next = it.next();
                if (next.getFrame()>prev.getFrame()+1) {
                    if (debug) logger.debug("gap: {}->{}", prev, next);
                    Map<Integer, StructureObject> localReference = reference==null? getReference(allTracks,prev.getFrame(), next.getFrame()) : reference;
                    if (localReference==null) { // case no object detected in frame -> no reference. allow unfilled gaps ? 
                        if (allowUnfilledGaps) {
                            prev=next;
                            continue;
                        }
                        else {
                            prev.resetTrackLinks(false, true);
                            next.resetTrackLinks(true, false, true, null);
                        }
                    } else {
                        StructureObject refPrev=localReference.get(prev.getFrame());
                        StructureObject refNext=localReference.get(next.getFrame());
                        int deltaOffX = Math.round((prev.getBounds().xMin()-refPrev.getBounds().xMin() + next.getBounds().xMin()-refNext.getBounds().xMin() )/2);
                        int deltaOffY = Math.round((prev.getBounds().yMin()-refPrev.getBounds().yMin() + next.getBounds().yMin()-refNext.getBounds().yMin() ) /2);
                        int deltaOffZ = Math.round((prev.getBounds().zMin()-refPrev.getBounds().zMin() + next.getBounds().zMin()-refNext.getBounds().zMin() ) /2);
                        int xSize = Math.round((prev.getBounds().sizeX()+next.getBounds().sizeX())/2);
                        int ySize = Math.round((prev.getBounds().sizeY()+next.getBounds().sizeY())/2);
                        int zSize = Math.round((prev.getBounds().sizeZ()+next.getBounds().sizeZ())/2);
                        int startFrame = prev.getFrame()+1;
                        if (debug) logger.debug("mc close gap between: {}&{}, delta offset: [{};{};{}], size:[{};{};{}], prev:{}, next:{}", prev.getFrame(), next.getFrame(), deltaOffX, deltaOffY, deltaOffZ, xSize, ySize, zSize, prev.getBounds(), next.getBounds());
                        if (debug) logger.debug("references: {}", localReference.size());
                        StructureObject gcPrev = prev;
                        for (int f = startFrame; f<next.getFrame(); ++f) { 
                            StructureObject parent = parentTrack.get(f-minParentFrame);
                            StructureObject ref=localReference.get(f);
                            if (debug) logger.debug("mc close gap: f:{}, ref: {}, parent: {}", f, ref, parent);
                            int offX = deltaOffX + ref.getBounds().xMin();
                            int offY = deltaOffY + ref.getBounds().yMin();
                            int offZ = deltaOffZ + ref.getBounds().zMin();
                            
                            BlankMask m = new BlankMask( xSize, ySize+offY>=parent.getBounds().sizeY()?parent.getBounds().sizeY()-offY:ySize, zSize, offX, offY, offZ, ref.getScaleXY(), ref.getScaleZ());
                            int maxIntersect = parent.getChildren(structureIdx).stream().mapToInt(o->getIntersection(o.getBounds(), m).getSizeXYZ()).max().getAsInt();
                            if (!isIncluded2D(m, parent.getBounds()) || maxIntersect>0) {
                                if (debug) {
                                    logger.debug("stop filling gap! parent:{}, gapfilled:{}, maxIntersect: {} erase from: {} to {}", parent.getBounds(), new SimpleBoundingBox(m), maxIntersect, gcPrev, prev);
                                    logger.debug("ref: {} ({}), prev:{}({})", ref, ref.getBounds(), ref.getPrevious(), ref.getPrevious().getBounds());
                                }
                                // stop filling gap 
                                while(gcPrev!=null && gcPrev.getFrame()>prev.getFrame()) {
                                    if (debug) logger.debug("erasing: {}, prev: {}", gcPrev, gcPrev.getPrevious());
                                    StructureObject p = gcPrev.getPrevious();
                                    gcPrev.resetTrackLinks(true, true);
                                    gcPrev.getParent().getChildren(structureIdx).remove(gcPrev);
                                    gcPrev = p;
                                }
                                prev.resetTrackLinks(false, true);
                                next.resetTrackLinks(true, false, true, null);
                                gcPrev=null;
                                break;
                            }
                            int idx = parent.getChildren(structureIdx).size(); // idx = last element -> in order to be consistent with the bounding box map because objects are adjusted afterwards
                            Region o = new Region(m, idx+1, parent.is2D());
                            StructureObject s = new StructureObject(f, structureIdx, idx, o, parent);
                            parent.getChildren(structureIdx).add(s);
                            if (debug) logger.debug("add object: {}, bounds: {}, refBounds: {}", s, s.getBounds(), ref.getBounds());
                            // set links
                            gcPrev.setTrackLinks(s, true, true);
                            gcPrev = s;
                        }
                        if (gcPrev!=null) gcPrev.setTrackLinks(next, true, true);
                    }
                }
                prev = next;
            }
        }
        
    }
    private static Map<Integer, StructureObject> getOneElementOfSize(Map<StructureObject, List<StructureObject>> allTracks, int size) {
        for (Entry<StructureObject, List<StructureObject>> e : allTracks.entrySet()) if (e.getValue().size()==size) {
            return e.getValue().stream().collect(Collectors.toMap(s->s.getFrame(), s->s));
        }
        return null;
    }
    /**
     * Return a track that is continuous between fStart & fEnd, included
     * @param allTracks
     * @param fStart
     * @param fEnd
     * @return 
     */
    private static Map<Integer, StructureObject> getReference(Map<StructureObject, List<StructureObject>> allTracks, int fStart, int fEnd) { 
        List<Pair<List<StructureObject>, Double>> refMSDMap = new ArrayList<>();
        for (Entry<StructureObject, List<StructureObject>> e : allTracks.entrySet()) {
            if (e.getKey().getFrame()<=fStart && e.getValue().get(e.getValue().size()-1).getFrame()>=fEnd) {
                if (isContinuousBetweenFrames(e.getValue(), fStart, fEnd)) {
                    List<StructureObject> ref = new ArrayList<>(e.getValue());
                    ref.removeIf(o->o.getFrame()<fStart||o.getFrame()>fEnd);
                    refMSDMap.add(new Pair<>(ref, msd(ref)));
                    
                }
            }
        }
        if (!refMSDMap.isEmpty()) {
            List<StructureObject> ref = refMSDMap.stream().min((p1, p2)->Double.compare(p1.value, p2.value)).get().key;
            return ref.stream().collect(Collectors.toMap(s->s.getFrame(), s->s));
        }
        return null;
    }
    private static double msd(List<StructureObject> list) {
        if (list.size()<=1) return 0;
        double res = 0;
        Iterator<StructureObject> it = list.iterator();
        StructureObject prev= it.next();
        while(it.hasNext()) {
            StructureObject next = it.next();
            res+=prev.getRegion().getGeomCenter(false).distSq(next.getRegion().getGeomCenter(false));
            prev = next;
        }
        return res/(list.size()-1);
    }
    
    private static boolean isContinuousBetweenFrames(List<StructureObject> list, int fStart, int fEnd) {
        Iterator<StructureObject> it = list.iterator();    
        StructureObject prev=it.next();
        while (it.hasNext()) {
            StructureObject cur = it.next();
            if (cur.getFrame()>=fStart) {
                if (cur.getFrame()!=prev.getFrame()+1) return false;
                if (cur.getFrame()>=fEnd) return true;
            }
            prev = cur;
        }
        return false;
    }
    
    @Override
    public MicrochannelSegmenter getSegmenter() {
        return this.segmenter.instanciatePlugin();
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
    // multithreaded interface
    boolean multithreaded;
    @Override
    public void setMultithread(boolean multithreaded) {
        this.multithreaded=multithreaded;
    }
    // tool tip interface
    @Override
    public String getToolTipText() {
        return toolTip;
    }
    
    
}
