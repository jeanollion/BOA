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
import dataStructure.objects.StructureObjectPreProcessing;
import dataStructure.objects.StructureObjectTracker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import measurement.GeometricalMeasurements;
import plugins.Tracker;
import static plugins.plugins.trackers.ObjectIdxTracker.getComparator;

/**
 *
 * @author jollion
 */
public class ClosedMicrochannelTracker implements Tracker {
    BoundedNumberParameter maxGrowthRate = new BoundedNumberParameter("Maximum growth rate", 2, 1.5, 1, 2);
    BoundedNumberParameter divCriterion = new BoundedNumberParameter("Division Criterion", 2, 0.9, 0.1, 0.99);
    Parameter[] parameters = new Parameter[]{maxGrowthRate, divCriterion};
    
    public ClosedMicrochannelTracker(){} 
    
    public ClosedMicrochannelTracker(double maximumGrowthRate, double divisionCriterion) {
        maxGrowthRate.setValue(maximumGrowthRate);
        divCriterion.setValue(divisionCriterion);
    } 
    
    public void assignPrevious(ArrayList<? extends StructureObjectTracker> previous, ArrayList<? extends StructureObjectTracker> next) {        
        // sort by y order
        Collections.sort(previous, getComparator(ObjectIdxTracker.IndexingOrder.YXZ));
        Collections.sort(next, getComparator(ObjectIdxTracker.IndexingOrder.YXZ));
        double divCriterion = this.divCriterion.getValue().doubleValue();
        double maxGrowthRate = this.maxGrowthRate.getValue().doubleValue();
        logger.trace("closed microchanel tracker: assingPrevious: timepoint: {}, previous count: {}, next count: {}, divCriterion: {}, maxGrowthRate: {}", next.isEmpty()?"no element":next.get(0).getTimePoint(), previous.size(), next.size(), divCriterion, maxGrowthRate);
        int previousCounter=0;
        int nextCounter=0;
        double[] previousSize = new double[previous.size()];
        for (int i = 0; i<previousSize.length; ++i) previousSize[i] = GeometricalMeasurements.getVolume(previous.get(i).getObject());
        double[] nextSize = new double[next.size()];
        for (int i = 0; i<nextSize.length; ++i) nextSize[i] = GeometricalMeasurements.getVolume(next.get(i).getObject());
        while(nextCounter<next.size() && previousCounter<previous.size()) {
            //logger.trace("previous: {}, size: {} next:{}, size:{}", previousCounter, previousSize[previousCounter], nextCounter, nextSize[nextCounter]);
            if (nextSize[nextCounter] > previousSize[previousCounter] * maxGrowthRate) { // under-segmentation error in the next
                if (previousCounter<previous.size()-1) next.get(nextCounter).setPreviousInTrack(previous.get(previousCounter+1), false, true); //signal an error
                logger.trace("segmentation error detected: previous index: {}, size: {}, next index: {}, size:{}", previousCounter, previousSize[previousCounter], nextCounter, nextSize[nextCounter]);
                next.get(nextCounter).setPreviousInTrack(previous.get(previousCounter), false, true);
                previousCounter+=2; // 2 previous were assigned to next signal an error
                nextCounter++;
            } else if (nextSize[nextCounter]  < previousSize[previousCounter] * divCriterion) { // division
                next.get(nextCounter).setPreviousInTrack(previous.get(previousCounter), false, false); // assign first child
                if (nextCounter<next.size()-1) { // assign second child
                    if (previousSize[previousCounter] * maxGrowthRate <= (nextSize[nextCounter+1]+nextSize[nextCounter])) { // over-segmentation error in the previous
                        logger.trace("segmentation error detected (division): previous index: {}, size: {}, next index: {}, size:{}", previousCounter, previousSize[previousCounter], nextCounter, nextSize[nextCounter]);
                        if (previousCounter<previous.size()-1) {
                            next.get(nextCounter+1).setPreviousInTrack(previous.get(previousCounter+1), false, true); // assing 2nd child to following in the previous + signal an error
                            next.get(nextCounter+1).setPreviousInTrack(previous.get(previousCounter), true, true); // assign 2nd child + signal an error
                            //logger.trace("assign previous: {} to next: {} and {} & previous {} to next: {}", previousCounter, nextCounter, nextCounter+1, previousCounter+1, nextCounter+1);
                            nextCounter+=2;
                            previousCounter+=2;
                        } else { // assing 2nd child+signal an error
                            next.get(nextCounter+1).setPreviousInTrack(previous.get(previousCounter), true, true); 
                            //logger.trace("assign previous: {} to next: {} and {}", previousCounter, nextCounter, nextCounter+1);
                            nextCounter+=2;
                            previousCounter++;
                        } 
                    } else { //assign 2nd child
                        next.get(nextCounter+1).setPreviousInTrack(previous.get(previousCounter), true, false); 
                        //logger.trace("assign previous: {} to next: {} and {}", previousCounter, nextCounter, nextCounter+1);
                        nextCounter+=2;
                        previousCounter++;
                    } 
                } else {nextCounter++; previousCounter++;}
            } else { // assign previous to next & vice-versa
                next.get(nextCounter).setPreviousInTrack(previous.get(previousCounter), false, false);
                //logger.trace("assign previous: {} to next: {}", previousCounter, nextCounter);
                nextCounter++;
                previousCounter++;
            } 
        }
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    
}
