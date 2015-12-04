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
import configuration.parameters.PluginParameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObject.TrackFlag;
import dataStructure.objects.StructureObjectPreProcessing;
import dataStructure.objects.StructureObjectTracker;
import dataStructure.objects.Voxel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import measurement.GeometricalMeasurements;
import plugins.Segmenter;
import plugins.SegmenterSplitAndMerge;
import plugins.Tracker;
import plugins.TrackerSegmenter;
import static plugins.plugins.trackers.ObjectIdxTracker.getComparator;

/**
 *
 * @author jollion
 */
public class ClosedMicrochannelTrackerLocalCorrections implements TrackerSegmenter {
    
    // parametrization-related attributes
    protected PluginParameter<SegmenterSplitAndMerge> segmenter = new PluginParameter<SegmenterSplitAndMerge>("Segmentation algorithm", SegmenterSplitAndMerge.class, false);
    BoundedNumberParameter maxGrowthRate = new BoundedNumberParameter("Maximum growth rate", 2, 1.5, 1, 2);
    BoundedNumberParameter minGrowthRate = new BoundedNumberParameter("Minimum growth rate", 2, 1.1, 1, 2);
    BoundedNumberParameter divCriterion = new BoundedNumberParameter("Division Criterion", 2, 0.9, 0.1, 0.99);
    Parameter[] parameters = new Parameter[]{segmenter, maxGrowthRate, divCriterion, minGrowthRate};
    
    // tracking-related attributes
    private enum Flag {errorType1, errorType2, correctionMerge, correctionSplit;}
    ObjectPopulation[] populations;
    SegmenterSplitAndMerge[] segmenters;
    HashMap<Object3D, TrackAttribute> trackAttributes;
    List<StructureObject> parents;
    int structureIdx;
    
    public ClosedMicrochannelTrackerLocalCorrections(){} 
    
    public ClosedMicrochannelTrackerLocalCorrections(double maximumGrowthRate, double divisionCriterion) {
        maxGrowthRate.setValue(maximumGrowthRate);
        divCriterion.setValue(divisionCriterion);
    } 
    
    @Override public void assignPrevious(ArrayList<? extends StructureObjectTracker> previous, ArrayList<? extends StructureObjectTracker> next) {        

    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }

    public void segmentAndTrack(int structureIdx, List<StructureObject> parentTrack) {
        
    }
    
    
    protected boolean assginPrevious(int timePoint) {
        ArrayList<Object3D> oNext = getObjectPopulation(timePoint).getObjects();
        ArrayList<Object3D> oPrev = getObjectPopulation(timePoint-1).getObjects();
        int nextMax = oNext.size();
        
    }
    
    protected SegmenterSplitAndMerge getSegmenter(int timePoint) {
        if (segmenters[timePoint]==null) segmenters[timePoint] = this.segmenter.instanciatePlugin();
        return segmenters[timePoint];
    }
    
    protected ObjectPopulation getObjectPopulation(int timePoint) {
        if (this.populations[timePoint]==null) {
            StructureObject parent = this.parents.get(timePoint);
            populations[timePoint] = getSegmenter(timePoint).runSegmenter(parent.getRawImage(structureIdx), structureIdx, parent);
        }
        return populations[timePoint];
    }
    
    private class TrackAttribute {
        int prevIdx, nextIdx;
        Flag flag;
    }
    
}
