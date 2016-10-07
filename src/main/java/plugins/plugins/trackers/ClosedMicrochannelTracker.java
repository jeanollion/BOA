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
package plugins.plugins.trackers;

import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObject.TrackFlag;
import dataStructure.objects.StructureObjectPreProcessing;
import dataStructure.objects.StructureObjectTracker;
import static dataStructure.objects.StructureObjectUtils.setTrackLinks;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import measurement.GeometricalMeasurements;
import plugins.Tracker;
import plugins.TrackerSegmenter;
import static plugins.plugins.trackers.ObjectIdxTracker.getComparator;

/**
 *
 * @author jollion
 */
public class ClosedMicrochannelTracker implements Tracker {
    BoundedNumberParameter maxGrowthRate = new BoundedNumberParameter("Maximum growth rate", 2, 1.5, 1, 2);
    BoundedNumberParameter minGrowthRate = new BoundedNumberParameter("Minimum growth rate", 2, 1.1, 1, 2);
    BoundedNumberParameter divCriterion = new BoundedNumberParameter("Division Criterion", 2, 0.9, 0.1, 0.99);
    Parameter[] parameters = new Parameter[]{maxGrowthRate, divCriterion, minGrowthRate};
    
    public ClosedMicrochannelTracker(){} 
    
    public ClosedMicrochannelTracker(double maximumGrowthRate, double divisionCriterion) {
        maxGrowthRate.setValue(maximumGrowthRate);
        divCriterion.setValue(divisionCriterion);
    } 
    
    private void assignPrevious(List<StructureObject> previous, List<StructureObject> next) {        
        // sort by y order
        Collections.sort(previous, getComparator(ObjectIdxTracker.IndexingOrder.YXZ));
        Collections.sort(next, getComparator(ObjectIdxTracker.IndexingOrder.YXZ));
        double divCriterion = this.divCriterion.getValue().doubleValue();
        double maxGrowthRate = this.maxGrowthRate.getValue().doubleValue();
        double minGrowthRate = this.minGrowthRate.getValue().doubleValue();
        logger.trace("closed microchanel tracker: assingPrevious: timepoint: {}, previous count: {}, next count: {}, divCriterion: {}, maxGrowthRate: {}", next.isEmpty()?"no element":next.get(0).getTimePoint(), previous.size(), next.size(), divCriterion, maxGrowthRate);
        
        // get size for division criterion
        double[] previousSize = new double[previous.size()];
        for (int i = 0; i<previousSize.length; ++i) previousSize[i] = GeometricalMeasurements.getVolume(previous.get(i).getObject());
        double[] nextSize = new double[next.size()];
        for (int i = 0; i<nextSize.length; ++i) nextSize[i] = GeometricalMeasurements.getVolume(next.get(i).getObject());
        
        int previousCounter=0;
        int nextCounter=0;
        while(nextCounter<next.size() && previousCounter<previous.size()) {
            //logger.trace("previous: {}, size: {} next:{}, size:{}", previousCounter, previousSize[previousCounter], nextCounter, nextSize[nextCounter]);
            if (nextSize[nextCounter] > previousSize[previousCounter] * maxGrowthRate) { // under-segmentation error in the next / over-segmentation in the previous
                int previousCounterInit = previousCounter;
                double prevSize = previousSize[previousCounter];
                previousCounter++;
                setTrackLinks(previous.get(previousCounterInit), next.get(nextCounter), true, true); // first child ( & next)
                while(previousCounter<previous.size() && nextSize[nextCounter] > prevSize * minGrowthRate) {
                    setTrackLinks(previous.get(previousCounter), next.get(nextCounter), true, false); // other child with error
                    next.get(nextCounter).setTrackHead(previous.get(previousCounter).getTrackHead(), false).setTrackFlag(TrackFlag.trackError);
                    //next.get(nextCounter).setPreviousInTrack(previous.get(previousCounter), false, TrackFlag.trackError);
                    prevSize+=previousSize[previousCounter];
                    logger.trace("segmentation error detected: previous index: {}, size: {}, next index: {}, size:{}", previousCounter, previousSize[previousCounter], nextCounter, nextSize[nextCounter]);
                    ++previousCounter;
                }
                //next.get(nextCounter).setPreviousInTrack(previous.get(previousCounterInit), false, TrackFlag.trackError); // at the end in order to set the 1st previous as previous of next
                nextCounter++;
            } else if (nextSize[nextCounter]  < previousSize[previousCounter] * divCriterion) { // division
                if (nextCounter<next.size()-1) { 
                    if (previousSize[previousCounter] * maxGrowthRate <= (nextSize[nextCounter+1]+nextSize[nextCounter])) { // false division
                        setTrackLinks(previous.get(previousCounter), next.get(nextCounter), true, true); // first child
                        //next.get(nextCounter).setPreviousInTrack(previous.get(previousCounter), false); // assign first child
                        nextCounter++; previousCounter++;
                    } else { 
                        setTrackLinks(previous.get(previousCounter), next.get(nextCounter), true, true); // first child
                        //next.get(nextCounter).setPreviousInTrack(previous.get(previousCounter), false); // assign first child
                        setTrackLinks(previous.get(previousCounter), next.get(nextCounter+1), true, false); // 2nd child
                        //next.get(nextCounter+1).setPreviousInTrack(previous.get(previousCounter), true);  //assign 2nd child
                        //logger.trace("assign previous: {} to next: {} and {}", previousCounter, nextCounter, nextCounter+1);
                        nextCounter+=2;
                        previousCounter++;
                    } 
                } else {
                    setTrackLinks(previous.get(previousCounter), next.get(nextCounter), true, true); // first child only
                    //next.get(nextCounter).setPreviousInTrack(previous.get(previousCounter), false); // assign first child only
                    nextCounter++; previousCounter++;
                }
            } else { // assign previous to next & vice-versa
                setTrackLinks(previous.get(previousCounter), next.get(nextCounter), true, true);
                //next.get(nextCounter).setPreviousInTrack(previous.get(previousCounter), false);
                //logger.trace("assign previous: {} to next: {}", previousCounter, nextCounter);
                nextCounter++;
                previousCounter++;
            } 
        }
        // reset trackLinks for unset objects
        for (int i = nextCounter; i<next.size(); ++i) next.get(i).resetTrackLinks(true, true);
        
     }

    public Parameter[] getParameters() {
        return parameters;
    }

    public void track(int structureIdx, List<StructureObject> parentTrack) {
        StructureObject prevParent = null;
        for (StructureObject parent : parentTrack) {
            if (prevParent!=null) assignPrevious(prevParent.getChildren(structureIdx), parent.getChildren(structureIdx));
            prevParent=parent;
        }
    }

    
    
}
