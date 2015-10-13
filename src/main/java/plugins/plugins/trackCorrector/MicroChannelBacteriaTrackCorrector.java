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
    public ArrayList<StructureObjectTrackCorrection> correctTrack(StructureObjectTrackCorrection track, ObjectSplitter splitter) { // faire le tri a posteriori
        ArrayList<StructureObjectTrackCorrection> modifiedObjects = new ArrayList<StructureObjectTrackCorrection>();
        ArrayList<StructureObjectTrackCorrection> toCorrectAfterwards=new ArrayList<StructureObjectTrackCorrection>();
        track = track.getNextTrackError();
        while(track != null) {
            StructureObjectTrackCorrection uncorrected = performCorrection(track, splitter, false, defaultAction.getSelected(), modifiedObjects);
            if (uncorrected !=null) toCorrectAfterwards.add(uncorrected);
            track = track.getNextTrackError();
        }
        // perform for uncorrectedObject
        for (StructureObjectTrackCorrection o : toCorrectAfterwards) performCorrection(o, splitter, true, defaultAction.getSelected(), modifiedObjects);
        return modifiedObjects;
    }
    public MicroChannelBacteriaTrackCorrector setDefaultCorrection(boolean correctAsOverSegmentation) {
        defaultAction.setSelected(correctAsOverSegmentation);
        return this;
    }

    public static StructureObjectTrackCorrection performCorrection(StructureObjectTrackCorrection error, ObjectSplitter splitter, boolean correctAmbiguousCases, boolean overSegmentationInAmbiguousCases, ArrayList<StructureObjectTrackCorrection> modifiedObjects) {
        ArrayList<StructureObjectTrackCorrection> prevSiblings = error.getPreviousDivisionSiblings();
            ArrayList<StructureObjectTrackCorrection> nextSiblings = error.getNextDivisionSiblings();
            int tDivPrev = prevSiblings!=null? prevSiblings.get(0).getTimePoint() : 0;
            int tDivNext = nextSiblings!=null?nextSiblings.get(0).getTimePoint() : Integer.MAX_VALUE;
            int tError = error.getTimePoint();
            if (prevSiblings!=null && prevSiblings.size()>2) logger.warn("More than 2 division sibling at time point {}, for object: {}", prevSiblings.get(0).getTimePoint(), prevSiblings.get(0).getPrevious());
            if (nextSiblings!=null && nextSiblings.size()>2) logger.warn("More than 2 division sibling at time point {}, for object: {}", nextSiblings.get(0).getTimePoint(), nextSiblings.get(0).getPrevious());
            
            if ((tDivNext-tError)>(tError-tDivPrev) || (correctAmbiguousCases && overSegmentationInAmbiguousCases)) { // overSegmentation: merge siblings between tDivPrev & tError
                if (prevSiblings==null) {
                    logger.error("Oversegmentation detected but no siblings found");
                } else {
                    logger.debug("Over-Segmentation detected between timepoint {} and {}, number of divided cells before: {}, merged cells after error: {}", tError, tDivNext, tError-tDivPrev, tDivNext-tError);
                    mergeTracks(prevSiblings.get(0), prevSiblings.get(1), tError, modifiedObjects);
                }
                return null;
            } else if ((tDivNext-tError)<(tError-tDivPrev)  || (correctAmbiguousCases && !overSegmentationInAmbiguousCases)) { // underSegmentation: split object between tError & tDivNext
                logger.debug("Under-Segmentation detected between timepoint {} and {}, number of divided cells before: {}, merged cells after error: {}", tError, tDivNext, tError-tDivPrev, tDivNext-tError);
                // get previous object for the future splited object:
                StructureObjectTrackCorrection splitPrevious = prevSiblings.get(1);
                while(splitPrevious.getTimePoint()<tError-1) splitPrevious=splitPrevious.getNext();
                StructureObjectTrackCorrection lastSplitObject= splitTrack(error, splitPrevious, splitter, tDivNext, modifiedObjects);
                if (nextSiblings!=null) { // assign as previous of nextDiv
                    nextSiblings.get(1).setPreviousInTrack(lastSplitObject, false, false);
                    modifiedObjects.add(nextSiblings.get(1));
                }
                return null;
            } else return error;
    }
    
    public static void mergeTracks(StructureObjectTrackCorrection track1, StructureObjectTrackCorrection track2, int timePointLimit, ArrayList<StructureObjectTrackCorrection> modifiedObjects) {
        if (track1.getTimePoint()!=track2.getTimePoint()) throw new IllegalArgumentException("merge tracks error: tracks should be at the same time point");
        while(track1.getTimePoint()<timePointLimit) {
            track1.merge(track2);
            modifiedObjects.add(track1);
            modifiedObjects.add(track2);
            track1 = track1.getNext();
            track2 = track2.getNext();
        }
    }
    
    public static StructureObjectTrackCorrection splitTrack(StructureObjectTrackCorrection track, StructureObjectTrackCorrection splitPrevious, ObjectSplitter splitter, int timePointLimit, ArrayList<StructureObjectTrackCorrection> modifiedObjects) {
        if (track.getTimePoint()!=splitPrevious.getTimePoint()-1) throw new IllegalArgumentException("split tracks error: split previous time point should be: "+(track.getTimePoint()-1)+" but is "+splitPrevious.getTimePoint());
        while(track.getTimePoint()<timePointLimit) {
            StructureObjectTrackCorrection newObject = track.split(splitter);
            newObject.setPreviousInTrack(splitPrevious, false, false);
            modifiedObjects.add(newObject);
            modifiedObjects.add(track);
            track = track.getNext();
            splitPrevious = newObject;
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
