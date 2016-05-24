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
package plugins.plugins.measurements;

import boa.gui.imageInteraction.IJImageDisplayer;
import configuration.parameters.Parameter;
import configuration.parameters.StructureParameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import dataStructure.objects.Voxel;
import image.BoundingBox;
import image.Image;
import java.util.ArrayList;
import java.util.List;
import measurement.BasicMeasurements;
import measurement.GeometricalMeasurements;
import measurement.MeasurementKey;
import measurement.MeasurementKeyObject;
import plugins.Measurement;

/**
 *
 * @author jollion
 */
public class MutationTrackMeasurements implements Measurement {
    protected StructureParameter bacteria = new StructureParameter("Bacteria Structure", 1, false, false);
    protected StructureParameter mutation = new StructureParameter("Mutation Structure", 2, false, false);
    protected Parameter[] parameters = new Parameter[]{bacteria, mutation};
    
    
    public MutationTrackMeasurements(){}
    
    public MutationTrackMeasurements(int bacteriaStructureIdx, int mutationStructureIdx){
        this.bacteria.setSelectedIndex(bacteriaStructureIdx);
        this.mutation.setSelectedIndex(mutationStructureIdx);
    }
    
    public int getCallStructure() {
        return mutation.getSelectedStructureIdx();
    }

    public boolean callOnlyOnTrackHeads() {
        return true;
    }

    public List<MeasurementKey> getMeasurementKeys() {
        int structureIdx = mutation.getSelectedStructureIdx();
        ArrayList<MeasurementKey> res = new ArrayList<MeasurementKey>();
        //res.add(new MeasurementKeyObject("ParentBacteriaIdx", structureIdx));
        res.add(new MeasurementKeyObject("TrackLength", structureIdx));
        res.add(new MeasurementKeyObject("MutationNumber", structureIdx));
        return res;
    }

    public void performMeasurement(StructureObject object, List<StructureObject> modifiedObjects) {
        // limit to trackHead of mother bacteria
        StructureObject parentBacteria;
        if (bacteria.getSelectedStructureIdx()==object.getParent().getStructureIdx()) parentBacteria = object.getParent();
        else parentBacteria = StructureObjectUtils.getInclusionParent(object.getObject(), object.getParent().getChildren(bacteria.getSelectedStructureIdx()), null);
        if (parentBacteria == null || parentBacteria.getIdx()!=0) return;
        
        List<StructureObject> track = StructureObjectUtils.getTrack(object, false);
        object.getMeasurements().setValue("TrackLength", track.get(track.size()-1).getTimePoint() - object.getTimePoint());
        object.getMeasurements().setValue("MutationNumber", track.size());
        modifiedObjects.add(object);
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
}
