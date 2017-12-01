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
package plugins.plugins.trackers;

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.ParameterUtils;
import configuration.parameters.PluginParameter;
import configuration.parameters.PostFilterSequence;
import configuration.parameters.PreFilterSequence;
import dataStructure.objects.Object3D;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import static dataStructure.objects.StructureObjectUtils.setTrackLinks;
import fiji.plugin.trackmate.Spot;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageOperations;
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
import plugins.MultiThreaded;
import plugins.Segmenter;
import plugins.Thresholder;
import plugins.TrackerSegmenter;
import plugins.OverridableThreshold;
import plugins.OverridableThresholdWithSimpleThresholder;
import plugins.plugins.segmenters.MicroChannelFluo2D;
import plugins.plugins.segmenters.MicrochannelPhase2D;
import plugins.plugins.segmenters.MicrochannelSegmenter;
import static plugins.plugins.trackers.ObjectIdxTracker.getComparator;
import plugins.plugins.trackers.trackMate.TrackMateInterface;
import plugins.plugins.transformations.CropMicroChannelBF2D;
import plugins.plugins.transformations.CropMicroChannels.Result;
import utils.ArrayUtil;
import utils.HashMapGetCreate;
import utils.HashMapGetCreate.Factory;
import utils.Pair;
import utils.SlidingOperator;
import static utils.SlidingOperator.performSlide;
import utils.ThreadRunner;
import utils.ThreadRunner.ThreadAction;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class MicrochannelTracker implements TrackerSegmenter, MultiThreaded {
    protected PluginParameter<MicrochannelSegmenter> segmenter = new PluginParameter<>("Segmentation algorithm", MicrochannelSegmenter.class, new MicrochannelPhase2D(), false);
    NumberParameter maxShift = new BoundedNumberParameter("Maximal Shift (pixels)", 0, 100, 1, null);
    NumberParameter maxDistanceWidthFactor = new BoundedNumberParameter("Maximal Distance for Tracking (x [mean channel width])", 1, 1, 0, null);
    NumberParameter yShiftQuantile = new BoundedNumberParameter("Y-shift Quantile", 2, 0.5, 0, 1);
    //NumberParameter minTrackLength = new BoundedNumberParameter("Minimum Track Length", 0, 100, 10, null);
    
    Parameter[] parameters = new Parameter[]{segmenter, maxShift, maxDistanceWidthFactor, yShiftQuantile};
    private static double widthQuantile = 0.9;
    public static boolean debug = false;
    
    public MicrochannelTracker setSegmenter(MicrochannelSegmenter s) {
        this.segmenter.setPlugin(s);
        return this;
    }
    public MicrochannelTracker setYShiftQuantile(double quantile) {
        this.yShiftQuantile.setValue(quantile);
        return this;
    }
    public MicrochannelTracker setTrackingParameters(int maxShift, double maxDistanceWidthFactor) {
        this.maxShift.setValue(maxShift);
        this.maxDistanceWidthFactor.setValue(maxDistanceWidthFactor);
        return this;
    }
    
    @Override public void track(int structureIdx, List<StructureObject> parentTrack) {
        if (parentTrack.isEmpty()) return;
        TrackMateInterface<Spot> tmi = new TrackMateInterface(TrackMateInterface.defaultFactory());
        Map<Integer, List<StructureObject>> map = StructureObjectUtils.getChildrenMap(parentTrack, structureIdx);
        
        logger.debug("tracking: {}", Utils.toStringList(map.entrySet(), e->"t:"+e.getKey()+"->"+e.getValue().size()));
        tmi.addObjects(map);
        if (tmi.objectSpotMap.isEmpty()) {
            logger.debug("No objects to track");
            return;
        }
        double meanWidth = Utils.flattenMap(map).stream().mapToDouble(o->o.getBounds().getSizeX()).average().getAsDouble()*parentTrack.get(0).getScaleXY();
        if (debug) logger.debug("mean width {}", meanWidth );
        double maxDistance = maxShift.getValue().doubleValue()*parentTrack.get(0).getScaleXY();
        double ftfDistance = maxDistanceWidthFactor.getValue().doubleValue() *meanWidth;
        logger.debug("ftfDistance: {}", ftfDistance);
        boolean ok = tmi.processFTF(ftfDistance);
        if (ok) ok = tmi.processGC(maxDistance, parentTrack.size(), false, false);
        if (ok) tmi.removeCrossingLinksFromGraph(meanWidth/4); 
        if (ok) ok = tmi.processGC(maxDistance, parentTrack.size(), false, false); // second GC for crossing links!
        tmi.setTrackLinks(map);
    }
    
    @Override
    public void segmentAndTrack(int structureIdx, List<StructureObject> parentTrack, PreFilterSequence preFilters, PostFilterSequence postFilters) {
        if (parentTrack.isEmpty()) return;
        // segmentation
        final Result[] boundingBoxes = new Result[parentTrack.size()];
        
        Image[] inputImages = new Image[parentTrack.size()];
        MicrochannelSegmenter[] segmenters = new MicrochannelSegmenter[parentTrack.size()];
        for (int i = 0; i<parentTrack.size(); ++i) segmenters[i] = getSegmenter();
        ThreadRunner.execute(parentTrack, false, (StructureObject parent, int idx) -> {
            inputImages[idx] = preFilters.filter(parent.getRawImage(structureIdx), parent);
        }, executor, null);
        
        if (segmenters[0] instanceof OverridableThresholdWithSimpleThresholder) {
            Image[] inputImagesToThld = new Image[inputImages.length];
            for (int i = 0; i<parentTrack.size(); ++i) {
                inputImagesToThld[i] = ((OverridableThresholdWithSimpleThresholder)segmenters[i]).getThresholdImage(inputImages[i], structureIdx, parentTrack.get(i));
            }
            Image globalImage = Image.mergeZPlanes(Arrays.asList(inputImagesToThld));
            plugins.SimpleThresholder t = ((OverridableThresholdWithSimpleThresholder)segmenters[0]).getThresholder();
            double globalThld = t.runSimpleThresholder(globalImage, null);
            for (int i = 0; i<parentTrack.size(); ++i) ((OverridableThresholdWithSimpleThresholder)segmenters[i]).setThresholdValue(globalThld);
            logger.debug("MicrochannelTracker on {}: global Treshold = {}", parentTrack.get(0), globalThld);
        }
        ThreadAction<StructureObject> ta = (StructureObject parent, int idx) -> {
            boundingBoxes[idx] = segmenters[idx].segment(inputImages[idx]);
            if (boundingBoxes[idx]==null) parent.setChildren(new ArrayList<>(), structureIdx); // if not set and call to getChildren() -> DAO will set old children
            //else parent.setChildrenObjects(postFilters.filter(boundingBoxes[idx].getObjectPopulation(inputImages[idx], false), structureIdx, parent), structureIdx); // no Y - shift here because the mean shift is added afterwards // TODO if post filter remove objects or modify -> how to link with result object??
            else parent.setChildrenObjects(boundingBoxes[idx].getObjectPopulation(inputImages[idx], false), structureIdx); // no Y - shift here because the mean shift is added afterwards
            inputImages[idx]=null;
            segmenters[idx]=null;
        };
        /*MicrochannelPhase2D.debug=true;
        ta.run(parentTrack.get(0), structureIdx);
        MicrochannelPhase2D.debug=false;
        */
        
        List<Pair<String, Exception>> exceptions = ThreadRunner.execute(parentTrack, false, ta, executor, null);
        for (Pair<String, Exception> p : exceptions) logger.debug(p.key, p.value);
        Map<StructureObject, Result> parentBBMap = new HashMap<>(boundingBoxes.length);
        for (int i = 0; i<boundingBoxes.length; ++i) parentBBMap.put(parentTrack.get(i), boundingBoxes[i]);
        if (debug && boundingBoxes.length>0) {
            for (int i = 0; i<boundingBoxes[0].size(); ++i) logger.debug("bb {}-> {}",i, boundingBoxes[0].getBounds(i, true));
        }
        // tracking
        if (debug) logger.debug("mc2: {}", Utils.toStringList(parentTrack, p->"t:"+p.getFrame()+"->"+p.getChildren(structureIdx).size()));
        track(structureIdx, parentTrack);
        fillGaps(structureIdx, parentTrack, true);
        if (debug) logger.debug("mc3: {}", Utils.toStringList(parentTrack, p->"t:"+p.getFrame()+"->"+p.getChildren(structureIdx).size()));
        // compute mean of Y-shifts & width for each microchannel and modify objects
        Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(parentTrack, structureIdx);
        if (debug) logger.debug("mc4: {}", Utils.toStringList(parentTrack, p->"t:"+p.getFrame()+"->"+p.getChildren(structureIdx).size()));
        logger.debug("Microchannel tracker: trackHead number: {}", allTracks.size());
        List<StructureObject> toRemove = new ArrayList<>();
        for (List<StructureObject> track : allTracks.values()) { // compute median shift on the whole track + mean width
            if (track.isEmpty()) continue;
            /*if (!debug && track.size()<this.minTrackLength.getValue().intValue()) {
                toRemove.addAll(track);
                continue;
            }*/
            
            List<Integer> shifts = new ArrayList<>(track.size());
            List<Double> widths = new ArrayList<>(track.size());
            for (StructureObject o : track) {
                Result r = parentBBMap.get(o.getParent());
                if (o.getIdx()>=r.size()) { // object created from gap closing 
                    if (!widths.isEmpty()) widths.add(widths.get(widths.size()-1)); // for index consitency
                    else widths.add(null);
                } else {
                    shifts.add(r.yMinShift[o.getIdx()]);
                    widths.add((double)r.getXWidth(o.getIdx()));
                }
            }
            if (shifts.isEmpty()) {
                logger.error("no shifts in track : {} length: {}", track.get(0).getTrackHead(), track.size());
                continue;
            }
            int shift = (int)Math.round(ArrayUtil.quantileInt(shifts, yShiftQuantile.getValue().doubleValue()));
            double meanWidth = 0, c=0; for (Double d : widths) if (d!=null) {meanWidth+=d; ++c;} // global mean value
            meanWidth/=c;
            int width = (int)Math.round(ArrayUtil.quantile(widths, widthQuantile));
            
            if (debug) {
                logger.debug("track: {} ymin-shift: {}, width: {} (max: {}, mean: {})", track.get(0), shift, width, widths.get(widths.size()-1), meanWidth);
            }
            // modify all objects of the track with the y-shift & width
            
            for (int i = 0; i<track.size(); ++i) {
                StructureObject o = track.get(i);
                BoundingBox b = o.getBounds();
                BoundingBox parentBounds = o.getParent().getBounds();
                int offY = b.getyMin() + shift; // shift was not included before
                int offX; // if width change -> offset X change
                //int offX = (int)Math.round( b.getXMean()-width/2d ); 
                double offXd = b.getXMean()-(width-1d)/2d;
                double offXdr = offXd-(int)offXd;
                if (false && offXdr==0) offX=(int)offXd;
                else { // adjust localy: compare light in both cases
                    BoundingBox bLeft = new BoundingBox((int)offXd, (int)offXd+width-1, offY, offY+b.getSizeY()-1, b.getzMin(), b.getzMax());
                    BoundingBox bRight = bLeft.duplicate().translate(1, 0, 0);
                    BoundingBox bLeft2 = bLeft.duplicate().translate(-1, 0, 0);
                    bLeft.contract(parentBounds);
                    bRight.contract(parentBounds);
                    bLeft2.contract(parentBounds);
                    Image r = o.getParent().getRawImage(structureIdx);
                    double valueLeft = ImageOperations.getMeanAndSigmaWithOffset(r, bLeft.getImageProperties(1, 1), null)[0];
                    double valueLeft2 = ImageOperations.getMeanAndSigmaWithOffset(r, bLeft2.getImageProperties(1, 1), null)[0];
                    double valueRight = ImageOperations.getMeanAndSigmaWithOffset(r, bRight.getImageProperties(1, 1), null)[0];
                    if (valueLeft2>valueRight && valueLeft2>valueLeft2) offX=(int)offXd-1;
                    else if (valueRight>valueLeft && valueRight>valueLeft2) offX=(int)offXd+1;
                    else offX=(int)offXd;
                    //logger.debug("offX for element: {}, width:{}>{}, left:{}={}, right:{}={} left2:{}={}", o, b, width, bLeft, valueLeft, bRight, valueRight, bLeft2, valueLeft2);
                }
                
                
                
                if (width+offX>parentBounds.getxMax() || offX<0) {
                    if (debug) logger.debug("remove out of bound track: {}", track.get(0).getTrackHead());
                    toRemove.addAll(track);
                    break;
                    //currentWidth = parentBounds.getxMax()-offX;
                    //if (currentWidth<0) logger.error("negative wigth: object:{} parent: {}, current: {}, prev: {}, next:{}", o, o.getParent().getBounds(), o.getBounds(), o.getPrevious().getBounds(), o.getNext().getBounds());
                }
                int height = b.getSizeY();
                if (height+offY>parentBounds.getyMax()) height = parentBounds.getyMax()-offY;
                BlankMask m = new BlankMask("", width, height, b.getSizeZ(), offX, offY, b.getzMin(), o.getScaleXY(), o.getScaleZ());
                o.setObject(new Object3D(m, o.getIdx()+1));
            }
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
                        int deltaOffX = Math.round((prev.getBounds().getxMin()-refPrev.getBounds().getxMin() + next.getBounds().getxMin()-refNext.getBounds().getxMin() )/2);
                        int deltaOffY = Math.round((prev.getBounds().getyMin()-refPrev.getBounds().getyMin() + next.getBounds().getyMin()-refNext.getBounds().getyMin() ) /2);
                        int deltaOffZ = Math.round((prev.getBounds().getzMin()-refPrev.getBounds().getzMin() + next.getBounds().getzMin()-refNext.getBounds().getzMin() ) /2);
                        int xSize = Math.round((prev.getBounds().getSizeX()+next.getBounds().getSizeX())/2);
                        int ySize = Math.round((prev.getBounds().getSizeY()+next.getBounds().getSizeY())/2);
                        int zSize = Math.round((prev.getBounds().getSizeZ()+next.getBounds().getSizeZ())/2);
                        int startFrame = prev.getFrame()+1;
                        if (debug) logger.debug("mc close gap between: {}&{}, delta offset: [{};{};{}], size:[{};{};{}], prev:{}, next:{}", prev.getFrame(), next.getFrame(), deltaOffX, deltaOffY, deltaOffZ, xSize, ySize, zSize, prev.getBounds(), next.getBounds());
                        if (debug) logger.debug("references: {}", localReference.size());
                        StructureObject gcPrev = prev;
                        for (int f = startFrame; f<next.getFrame(); ++f) { 
                            StructureObject parent = parentTrack.get(f-minParentFrame);
                            StructureObject ref=localReference.get(f);
                            if (debug) logger.debug("mc close gap: f:{}, ref: {}, parent: {}", f, ref, parent);
                            int offX = deltaOffX + ref.getBounds().getxMin();
                            int offY = deltaOffY + ref.getBounds().getyMin();
                            int offZ = deltaOffZ + ref.getBounds().getzMin();
                            
                            BlankMask m = new BlankMask("", xSize, ySize+offY>=parent.getBounds().getSizeY()?parent.getBounds().getSizeY()-offY:ySize, zSize, offX, offY, offZ, ref.getScaleXY(), ref.getScaleZ());
                            BoundingBox bds = m.getBoundingBox();
                            int maxIntersect = parent.getChildren(structureIdx).stream().mapToInt(o->o.getBounds().getIntersection(bds).getSizeXYZ()).max().getAsInt();
                            if (!bds.isIncluded(parent.getBounds()) || maxIntersect>0) {
                                if (debug) {
                                    logger.debug("stop filling gap! parent:{}, gapfilled:{}, maxIntersect: {} erase from: {} to {}", parent.getBounds(), m.getBoundingBox(), maxIntersect, gcPrev, prev);
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
                            Object3D o = new Object3D(m, idx+1);
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
            res+=getDistanceSquare(prev.getObject().getGeomCenter(false), next.getObject().getGeomCenter(false));
            prev = next;
        }
        return res/(list.size()-1);
    }
    private static double getDistanceSquare(double[] c1, double[] c2) {
        return Math.pow((c1[0]-c2[0]), 2) + Math.pow((c1[1]-c2[1]), 2) +(c1.length>2 && c2.length>2  ?  Math.pow((c1[2]-c2[2]), 2) : 0);
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
    
    //multithreaded interface
    ExecutorService executor;
    @Override
    public void setExecutor(ExecutorService executor) {
        this.executor=executor;
    }
    
    
}
