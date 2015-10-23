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
package plugins.plugins.trackCorrector;

import configuration.parameters.BooleanParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObject.TrackFlag;
import dataStructure.objects.StructureObjectTrackCorrection;
import java.util.ArrayList;
import plugins.ObjectSplitter;
import plugins.TrackCorrector;

/**
 *
 * @author jollion
 */
public class MicroChannelBacteriaTrackCorrector implements TrackCorrector {
    public BooleanParameter defaultAction = new BooleanParameter("Default Correction", "Over-segmentation", "Under-segmentation", true);
    public void correctTrack(StructureObjectTrackCorrection track, ObjectSplitter splitter, ArrayList<StructureObjectTrackCorrection> modifiedObjects) { 
        ArrayList<StructureObjectTrackCorrection> toCorrectAfterwards=new ArrayList<StructureObjectTrackCorrection>();
        if (!track.hasTrackLinkError()) track = track.getNextTrackError();
        while(track != null) {
            StructureObjectTrackCorrection uncorrected = performCorrection(track, splitter, false, defaultAction.getSelected(), modifiedObjects);
            if (uncorrected !=null) toCorrectAfterwards.add(uncorrected);
            track = track.getNextTrackError();
        }
        // perform for uncorrectedObject
        for (StructureObjectTrackCorrection o : toCorrectAfterwards) performCorrection(o, splitter, true, defaultAction.getSelected(), modifiedObjects);
    }
    public MicroChannelBacteriaTrackCorrector setDefaultCorrection(boolean correctAsOverSegmentation) {
        defaultAction.setSelected(correctAsOverSegmentation);
        return this;
    }

    public static StructureObjectTrackCorrection performCorrection(StructureObjectTrackCorrection error, ObjectSplitter splitter, boolean correctAmbiguousCases, boolean overSegmentationInAmbiguousCases, ArrayList<StructureObjectTrackCorrection> modifiedObjects) {
        ArrayList<StructureObjectTrackCorrection> prevSiblings = error.getPreviousDivisionSiblings();
            ArrayList<StructureObjectTrackCorrection> nextSiblings = error.getNextDivisionSiblings();
            int tDivPrev = prevSiblings!=null? prevSiblings.get(0).getTimePoint() : 0;
            int tDivNext = nextSiblings!=null?nextSiblings.get(0).getTimePoint() : getMaxTimePoint(error)+1;
            int tError = error.getTimePoint();
            if (prevSiblings!=null && prevSiblings.size()>2) logger.warn("More than 2 division sibling at time point {}, for object: {}", prevSiblings.get(0).getTimePoint(), prevSiblings.get(0).getPrevious());
            if (nextSiblings!=null && nextSiblings.size()>2) logger.warn("More than 2 division sibling at time point {}, for object: {}", nextSiblings.get(0).getTimePoint(), nextSiblings.get(0).getPrevious());
            logger.trace("track correction: tDivPrev: {} tError: {}, tDivNext:{}, prevSiblings: {}, nextSiblings: {}", tDivPrev, tError, tDivNext, prevSiblings!=null?prevSiblings.size():0, nextSiblings!=null?nextSiblings.size():0);
            if ((tDivNext-tError)>(tError-tDivPrev) || (correctAmbiguousCases && overSegmentationInAmbiguousCases)) { // overSegmentation: merge siblings between tDivPrev & tError
                if (prevSiblings==null) {
                    logger.error("Oversegmentation detected but no siblings found");
                } else {
                    logger.trace("Over-Segmentation detected between timepoint {} and {}, number of divided cells before: {}, undivided cells after error: {}", tDivPrev, tError, tError-tDivPrev, tDivNext-tError);
                    mergeTracks(prevSiblings.get(0), prevSiblings.get(1), tError, modifiedObjects);
                    error.setTrackFlag(null); // remove error tag @tError
                    if (modifiedObjects!=null) modifiedObjects.add(error);
                }
                return null;
            } else if ((tDivNext-tError)<(tError-tDivPrev)  || (correctAmbiguousCases && !overSegmentationInAmbiguousCases)) { // underSegmentation: split object between tError & tDivNext
                logger.trace("Under-Segmentation detected between timepoint {} and {}, number of divided cells before: {}, undivided cells after error: {}", tError, tDivNext, tError-tDivPrev, tDivNext-tError);
                // get previous object for the future splitted object:
                StructureObjectTrackCorrection splitPrevious = prevSiblings.get(1);
                while(splitPrevious.getTimePoint()<tError-1) splitPrevious=splitPrevious.getNext();
                StructureObjectTrackCorrection lastSplitObject= splitTrack(error, splitPrevious, splitter, tDivNext, modifiedObjects);
                if (nextSiblings!=null) { // assign as previous of nextDiv
                    StructureObject next = (StructureObject)nextSiblings.get(1);
                    next.setPreviousInTrack(lastSplitObject, false, false);
                    if (modifiedObjects!=null) modifiedObjects.add(next);
                    // update trackHead of the whole track after nextSiblings1
                    next.resetTrackHead();
                    if (modifiedObjects!=null) {
                        while(next.getNext()!=null && next.getNext().getPrevious()==next) {
                            next=next.getNext();
                            if (next!=null) modifiedObjects.add(next);
                        }
                    }
                    
                }
                return null;
            } else return error;
    }
    
    private static int getMaxTimePoint(StructureObjectTrackCorrection track) {
        while(track.getNext()!=null) track=track.getNext();
        return track.getTimePoint();
    }
    
    public static void mergeTracks(StructureObjectTrackCorrection track1, StructureObjectTrackCorrection track2, int timePointLimit, ArrayList<StructureObjectTrackCorrection> modifiedObjects) {
        if (track1.getTimePoint()!=track2.getTimePoint()) throw new IllegalArgumentException("merge tracks error: tracks should be at the same time point");
        while(track1.getTimePoint()<timePointLimit) {
            track1.merge(track2);
            if (modifiedObjects!=null) {
                modifiedObjects.add(track1);
                modifiedObjects.add(track2);
            }
            track1 = track1.getNext();
            track2 = track2.getNext();
        }
    }
    
    public static StructureObjectTrackCorrection splitTrack(StructureObjectTrackCorrection track, StructureObjectTrackCorrection splitPrevious, ObjectSplitter splitter, int timePointLimit, ArrayList<StructureObjectTrackCorrection> modifiedObjects) {
        if (track.getTimePoint()-1!=splitPrevious.getTimePoint()) throw new IllegalArgumentException("split tracks error: split previous time point should be: "+(track.getTimePoint()-1)+" but is "+splitPrevious.getTimePoint());
        while(track!=null && track.getTimePoint()<timePointLimit) {
            StructureObjectTrackCorrection newObject = track.split(splitter);
            if (newObject!=null) {
                newObject.setPreviousInTrack(splitPrevious, false, false);
                if (modifiedObjects!=null) modifiedObjects.add(newObject);
                splitPrevious = newObject;
            } else {
                splitPrevious = track;
            }
            if (modifiedObjects!=null) modifiedObjects.add(track);
            track = track.getNext();
        }
        return splitPrevious;
    }
    
    public Parameter[] getParameters() {
        return new Parameter[0];
    }

    public boolean does3D() {
        return true;
    }

    
    
}
