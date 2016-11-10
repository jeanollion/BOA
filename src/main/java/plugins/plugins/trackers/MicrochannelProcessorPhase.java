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
import plugins.plugins.segmenters.MicroChannelPhase2D;
import static plugins.plugins.trackers.ObjectIdxTracker.getComparator;
import plugins.plugins.trackers.trackMate.TrackMateInterface;
import plugins.plugins.transformations.CropMicroChannels.Result;
import utils.ThreadRunner;
import utils.ThreadRunner.ThreadAction;

/**
 *
 * @author jollion
 */
public class MicrochannelProcessorPhase implements TrackerSegmenter {
    MicroChannelPhase2D segmenter = new MicroChannelPhase2D();
    NumberParameter maxShift = new BoundedNumberParameter("Maximal Shift (pixels)", 0, 100, 1, null);
    public static boolean debug = false;
    @Override public void track(int structureIdx, List<StructureObject> parentTrack) {
        if (parentTrack.isEmpty()) return;
        TrackMateInterface<Spot> tmi = new TrackMateInterface(TrackMateInterface.defaultFactory());
        tmi.addObjects(parentTrack, structureIdx);
        double maxDistance = maxShift.getValue().doubleValue()*parentTrack.get(0).getScaleXY();
        boolean ok = tmi.processFTF(maxDistance);
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
                boundingBoxes[idx] = segmenter.segment(parent.getRawImage(structureIdx));
                parent.setChildrenObjects(boundingBoxes[idx].getObjectPopulation(parent.getRawImage(structureIdx), false), structureIdx); // no shift here because the mean shift is added afterwards
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
        for (List<StructureObject> track : allTracks.values()) {
            if (track.isEmpty()) continue;
            // compute mean shift on the whole track + mean width
            List<Integer> shifts = new ArrayList<>(track.size());
            List<Integer> widths = new ArrayList<>(track.size());
            for (StructureObject o : track) {
                Result r = parentBBMap.get(o.getParent());
                if (o.getIdx()>=r.size()) continue; // exclude objects created from gap closing 
                shifts.add(r.yMinShift[o.getIdx()]);
                widths.add(r.getXWidth(o.getIdx()));
            }
            Collections.sort(widths);
            Collections.sort(shifts);
            
            int shift = shifts.get(shifts.size()/2);
            int width = widths.get(widths.size()/2);
            if (debug) logger.debug("track: {} ymin-shift: {}, width: {}", track.get(0), shift, width);
            // modify all objects of the track with the shift
            for (StructureObject o : track) {
                BoundingBox b = o.getBounds();
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
        int minParentFrame = parentTrack.get(0).getTimePoint();
        for (List<StructureObject> track : allTracks.values()) {
            Iterator<StructureObject> it = track.iterator();
            StructureObject prev = it.next();
            while (it.hasNext()) {
                StructureObject cur = it.next();
                if (cur.getTimePoint()>prev.getTimePoint()+1) {
                    if (debug) logger.debug("gap: {}->{}", prev, cur);
                    Map<Integer, StructureObject> localReference = reference==null? getReference(allTracks,prev.getTimePoint(), cur.getTimePoint()) : reference;
                    if (localReference==null) {
                        prev.resetTrackLinks(false, true);
                        cur.resetTrackLinks(true, false);
                    } else {
                        StructureObject refPrev=localReference.get(prev.getTimePoint());
                        StructureObject refNext=localReference.get(cur.getTimePoint());
                        int deltaOffX = Math.round((prev.getBounds().getxMin()-refPrev.getBounds().getxMin() + cur.getBounds().getxMin()-refNext.getBounds().getxMin() )/2);
                        int deltaOffY = Math.round((prev.getBounds().getyMin()-refPrev.getBounds().getyMin() + cur.getBounds().getyMin()-refNext.getBounds().getyMin() ) /2);
                        int deltaOffZ = Math.round((prev.getBounds().getzMin()-refPrev.getBounds().getzMin() + cur.getBounds().getzMin()-refNext.getBounds().getzMin() ) /2);
                        int xSize = Math.round((prev.getBounds().getSizeX()+cur.getBounds().getSizeX())/2);
                        int ySize = Math.round((prev.getBounds().getSizeY()+cur.getBounds().getSizeY())/2);
                        int zSize = Math.round((prev.getBounds().getSizeZ()+cur.getBounds().getSizeZ())/2);
                        int startFrame = prev.getTimePoint()+1;
                        for (int f = startFrame; f<cur.getTimePoint(); ++f) { 
                            StructureObject ref=localReference.get(f);
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
            return e.getValue().stream().collect(Collectors.toMap(s->s.getTimePoint(), s->s));
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
            if (e.getKey().getTimePoint()<=fStart) {
                int lastFrame = e.getKey().getTimePoint()+e.getValue().size();
                int lastFrame2 = e.getValue().get(e.getValue().size()-1).getTimePoint();
                if (lastFrame>=fEnd && lastFrame==lastFrame2) return e.getValue().stream().collect(Collectors.toMap(s->s.getTimePoint(), s->s));
            }
        }
        return null;
    }
    
    @Override
    public Segmenter getSegmenter() {
        return segmenter;
    }

    @Override
    public Parameter[] getParameters() {
        return ParameterUtils.aggregate(segmenter.getParameters(), maxShift);
    }
}
