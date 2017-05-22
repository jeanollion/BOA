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

import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.ParameterUtils;
import configuration.parameters.PostFilterSequence;
import configuration.parameters.PreFilterSequence;
import dataStructure.objects.Object3D;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import static dataStructure.objects.StructureObjectUtils.setTrackLinks;
import fiji.plugin.trackmate.Spot;
import image.BlankMask;
import image.BoundingBox;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import plugins.Segmenter;
import plugins.TrackerSegmenter;
import plugins.plugins.segmenters.MicrochannelPhase2D;
import static plugins.plugins.trackers.ObjectIdxTracker.getComparator;
import plugins.plugins.trackers.trackMate.TrackMateInterface;
import plugins.plugins.transformations.CropMicroChannels.Result;
import utils.SlidingOperator;
import static utils.SlidingOperator.performSlide;
import utils.ThreadRunner;
import utils.ThreadRunner.ThreadAction;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class MicrochannelProcessorPhase implements TrackerSegmenter {
    Parameter[] segmenterParams = new MicrochannelPhase2D().getParameters();
    NumberParameter maxShift = new BoundedNumberParameter("Maximal Shift (pixels)", 0, 100, 1, null);
    NumberParameter maxDistanceWidthFactor = new BoundedNumberParameter("Maximal Distance for Tracking (x mean channel width)", 1, 2, 1, 3);
    private static double widthQuantile = 0.9;
    public static boolean debug = false;
    @Override public void track(int structureIdx, List<StructureObject> parentTrack) {
        if (parentTrack.isEmpty()) return;
        TrackMateInterface<Spot> tmi = new TrackMateInterface(TrackMateInterface.defaultFactory());
        tmi.addObjects(parentTrack, structureIdx);
        double meanWidth = 0;
        double count = 0;
        for (StructureObject p : parentTrack) for (StructureObject o : p.getChildren(structureIdx)) {meanWidth+=o.getBounds().getSizeX(); ++count;}
        meanWidth = parentTrack.get(0).getScaleXY() * meanWidth/count;
        double maxDistance = maxShift.getValue().doubleValue()*parentTrack.get(0).getScaleXY();
        boolean ok = tmi.processFTF(maxDistanceWidthFactor.getValue().doubleValue() *meanWidth);
        if (ok) ok = tmi.processGC(maxDistance, parentTrack.size());
        if (ok) tmi.setTrackLinks(parentTrack, structureIdx);
        closeGaps(structureIdx, parentTrack);
    }

    @Override
    public void segmentAndTrack(int structureIdx, List<StructureObject> parentTrack, PreFilterSequence preFilters, PostFilterSequence postFilters) {
        // segmentation
        final Result[] boundingBoxes = new Result[parentTrack.size()];
        ThreadAction<StructureObject> ta = new ThreadAction<StructureObject>() {
            @Override
            public void run(StructureObject parent, int idx, int threadIdx) {
                boundingBoxes[idx] = getSegmenter().segment(preFilters.filter(parent.getRawImage(structureIdx), parent));
                parent.setChildrenObjects(postFilters.filter(boundingBoxes[idx].getObjectPopulation(parent.getRawImage(structureIdx), false), structureIdx, parent), structureIdx); // no Y - shift here because the mean shift is added afterwards
            }
        };
        ThreadRunner.execute(parentTrack, ta);
        Map<StructureObject, Result> parentBBMap = new HashMap<>(boundingBoxes.length);
        for (int i = 0; i<boundingBoxes.length; ++i) parentBBMap.put(parentTrack.get(i), boundingBoxes[i]);
        
        // tracking
        track(structureIdx, parentTrack);
        
        // compute mean of Y-shifts & width for each microchannel and modify objects
        Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(parentTrack, structureIdx);
        logger.debug("trackHead number: {}", allTracks.size());
        for (List<StructureObject> track : allTracks.values()) { // compute median shift on the whole track + mean width
            if (track.isEmpty()) continue;
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
            Collections.sort(shifts);
            int shift = shifts.get(shifts.size()/2); // median shift
            double meanWidth = 0, c=0; for (Double d : widths) if (d!=null) {meanWidth+=d; ++c;} // global mean value
            meanWidth/=c;
            Collections.sort(widths);
            int width = (int)Math.round(widths.get((int)(widths.size()*widthQuantile)));
            //widths = performSlide(widths, 10, SlidingOperator.slidingMean(mean)); // sliding and not global mean because if channels gets empty -> width too small 
            if (debug) {
                logger.debug("track: {} ymin-shift: {}, width: {} (max: {}, mean: {})", track.get(0), shift, width, widths.get(widths.size()-1), meanWidth);
            }
            // modify all objects of the track with the shift
            for (int i = 0; i<track.size(); ++i) {
                StructureObject o = track.get(i);
                BoundingBox b = o.getBounds();
                //int width = (int)Math.round(widths.get(i));
                int offX = b.getxMin() + (int)Math.round((b.getSizeX()-width)/2d + Double.MIN_VALUE); // if width change -> offset X change
                int offY = b.getyMin() + shift; // shift was not included before
                BoundingBox parentBounds = o.getParent().getBounds();
                if (width+offX>parentBounds.getxMax()) width = parentBounds.getxMax()-offX;
                int height = b.getSizeY();
                if (height+offY>parentBounds.getyMax()) height = parentBounds.getyMax()-offY;
                BlankMask m = new BlankMask("", width, height, b.getSizeZ(), offX, offY, b.getzMin(), o.getScaleXY(), o.getScaleZ());
                o.setObject(new Object3D(m, o.getIdx()+1));
            }
        }
    }

    private static void closeGaps(int structureIdx, List<StructureObject> parentTrack) {
        Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(parentTrack, structureIdx);
        Map<Integer, StructureObject> reference = getOneElementOfSize(allTracks, parentTrack.size());
        int minParentFrame = parentTrack.get(0).getFrame();
        for (List<StructureObject> track : allTracks.values()) {
            Iterator<StructureObject> it = track.iterator();
            StructureObject prev = it.next();
            while (it.hasNext()) {
                StructureObject cur = it.next();
                if (cur.getFrame()>prev.getFrame()+1) {
                    if (debug) logger.debug("gap: {}->{}", prev, cur);
                    Map<Integer, StructureObject> localReference = reference==null? getReference(allTracks,prev.getFrame(), cur.getFrame()) : reference;
                    if (localReference==null) {
                        prev.resetTrackLinks(false, true);
                        cur.resetTrackLinks(true, false);
                    } else {
                        StructureObject refPrev=localReference.get(prev.getFrame());
                        StructureObject refNext=localReference.get(cur.getFrame());
                        int deltaOffX = Math.round((prev.getBounds().getxMin()-refPrev.getBounds().getxMin() + cur.getBounds().getxMin()-refNext.getBounds().getxMin() )/2);
                        int deltaOffY = Math.round((prev.getBounds().getyMin()-refPrev.getBounds().getyMin() + cur.getBounds().getyMin()-refNext.getBounds().getyMin() ) /2);
                        int deltaOffZ = Math.round((prev.getBounds().getzMin()-refPrev.getBounds().getzMin() + cur.getBounds().getzMin()-refNext.getBounds().getzMin() ) /2);
                        int xSize = Math.round((prev.getBounds().getSizeX()+cur.getBounds().getSizeX())/2);
                        int ySize = Math.round((prev.getBounds().getSizeY()+cur.getBounds().getSizeY())/2);
                        int zSize = Math.round((prev.getBounds().getSizeZ()+cur.getBounds().getSizeZ())/2);
                        int startFrame = prev.getFrame()+1;
                        if (debug) logger.debug("mc close gap between: {}&{}, off: {}/{}/{}, size:{}/{}/{}", prev.getFrame(), cur.getFrame(), deltaOffX, deltaOffY, deltaOffZ, xSize, ySize, zSize);
                        if (debug) logger.debug("reference: {}", localReference);
                        for (int f = startFrame; f<cur.getFrame(); ++f) { 
                            StructureObject ref=localReference.get(f);
                            if (debug) logger.debug("mc close gap: f:{}, ref: {}", f, ref);
                            int offX = deltaOffX + ref.getBounds().getxMin();
                            int offY = deltaOffY + ref.getBounds().getyMin();
                            int offZ = deltaOffZ + ref.getBounds().getzMin();
                            StructureObject parent = parentTrack.get(f-minParentFrame);
                            BlankMask m = new BlankMask("", xSize, ySize, zSize, offX, offY, offZ, ref.getScaleXY(), ref.getScaleZ());
                            int idx = parent.getChildren(structureIdx).size(); // idx = last element -> in order to be consistent with the bounding box map because objects are adjusted afterwards
                            Object3D o = new Object3D(m, idx+1);
                            StructureObject s = new StructureObject(f, structureIdx, idx, o, parent);
                            parent.getChildren(structureIdx).add(s);
                            if (debug) logger.debug("add object: {}, bounds: {}", s, s.getBounds());
                            // set links
                            prev.setTrackLinks(s, true, true);
                            prev = s;
                        }
                        prev.setTrackLinks(cur, true, true);
                    }
                }
                prev = cur;
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
        for (Entry<StructureObject, List<StructureObject>> e : allTracks.entrySet()) {
            if (e.getKey().getFrame()<=fStart && e.getValue().get(e.getValue().size()-1).getFrame()>=fEnd) {
                if (isContinuousBetweenFrames(e.getValue(), fStart, fEnd)) return e.getValue().stream().collect(Collectors.toMap(s->s.getFrame(), s->s));
            }
        }
        return null;
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
    public MicrochannelPhase2D getSegmenter() {
        MicrochannelPhase2D segmenter = new MicrochannelPhase2D();
        ParameterUtils.setContent(segmenter.getParameters(), segmenterParams);
        return segmenter;
    }

    @Override
    public Parameter[] getParameters() {
        Parameter[] res=  ParameterUtils.aggregate(segmenterParams, maxShift, maxDistanceWidthFactor);
        return res;
    }
    
    
}
