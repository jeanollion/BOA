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
import java.util.List;
import java.util.Map;
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
    @Override public void track(int structureIdx, List<StructureObject> parentTrack) {
        if (parentTrack.isEmpty()) return;
        TrackMateInterface<Spot> tmi = new TrackMateInterface(TrackMateInterface.defaultFactory());
        tmi.addObjects(parentTrack, structureIdx);
        boolean ok = tmi.processFTF(maxShift.getValue().doubleValue()*parentTrack.get(0).getScaleXY());
        if (ok) tmi.setTrackLinks(parentTrack, structureIdx);
    }

    @Override
    public void segmentAndTrack(int structureIdx, List<StructureObject> parentTrack, PreFilterSequence preFilters, PostFilterSequence postFilters) {
        // segmentation
        final Result[] boundingBoxes = new Result[parentTrack.size()];
        ThreadAction<StructureObject> ta = new ThreadAction<StructureObject>() {
            @Override
            public void run(StructureObject parent, int idx, int threadIdx) {
                boundingBoxes[idx] = segmenter.segment(parent.getRawImage(structureIdx));
                parent.setChildrenObjects(boundingBoxes[idx].getObjectPopulation(parent.getRawImage(structureIdx)), structureIdx);
            }
        };
        ThreadRunner.execute(parentTrack, ta);
        Map<StructureObject, Result> parentBBMap = new HashMap<>(boundingBoxes.length);
        for (int i = 0; i<boundingBoxes.length; ++i) parentBBMap.put(parentTrack.get(i), boundingBoxes[i]);
        
        // tracking
        track(structureIdx, parentTrack);
        
        // compute mean of Y-shifts for each microchannel and modify objects
        Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(parentTrack, structureIdx);
        logger.debug("trackHead number: {}", allTracks.size());
        for (List<StructureObject> track : allTracks.values()) {
            // compute mean shift on the whole track
            double meanShift = 0;
            for (StructureObject o : track) meanShift+=parentBBMap.get(o.getParent()).yMinShift[o.getIdx()];
            int meanShiftInt = (int)Math.round(meanShift/(double)track.size());
            if (meanShiftInt==0) continue;
            //List<Integer> shifts = new ArrayList<>();
            //for (StructureObject o : track) shifts.add(parentBBMap.get(o.getParent()).yMinShift[o.getIdx()]);
            //logger.debug("trackHead: {}, shift: {}, shifts{}", track.get(0), meanShiftInt, shifts);
            // modify all objects of the track with the shift
            for (StructureObject o : track) {
                Result r = parentBBMap.get(o.getParent());
                BoundingBox b = r.getBounds(o.getIdx());
                b.setyMin(b.getyMin()-r.yMinShift[o.getIdx()]+meanShiftInt);
                o.setObject(new Object3D(new BlankMask(b.getImageProperties(o.getScaleXY(), o.getScaleZ())), o.getIdx()+1));
            }
        }
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
