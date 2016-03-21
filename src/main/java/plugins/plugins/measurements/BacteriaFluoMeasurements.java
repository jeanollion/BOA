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
public class BacteriaFluoMeasurements implements Measurement {
    protected StructureParameter bacteria = new StructureParameter("Bacteria Structure", 1, false, false);
    protected StructureParameter mutation = new StructureParameter("Mutation Structure", 2, false, false);
    protected Parameter[] parameters = new Parameter[]{bacteria, mutation};
    
    
    public BacteriaFluoMeasurements(){}
    
    public BacteriaFluoMeasurements(int bacteriaStructureIdx, int mutationStructureIdx){
        this.bacteria.setSelectedIndex(bacteriaStructureIdx);
        this.mutation.setSelectedIndex(mutationStructureIdx);
    }
    
    public int getCallStructure() {
        return bacteria.getParentStructureIdx();
    }

    public boolean callOnlyOnTrackHeads() {
        return false;
    }

    public List<MeasurementKey> getMeasurementKeys() {
        int structureIdx = bacteria.getSelectedStructureIdx();
        ArrayList<MeasurementKey> res = new ArrayList<MeasurementKey>();
        res.add(new MeasurementKeyObject("CenterX", structureIdx));
        res.add(new MeasurementKeyObject("CenterY", structureIdx));
        res.add(new MeasurementKeyObject("MeanRFPInBacteria", structureIdx));
        res.add(new MeasurementKeyObject("MeanYFPInBacteria", structureIdx));
        res.add(new MeasurementKeyObject("Length", structureIdx));
        res.add(new MeasurementKeyObject("Area", structureIdx));
        res.add(new MeasurementKeyObject("MutationCount", structureIdx));
        
        return res;
    }

    public void performMeasurement(StructureObject object, List<StructureObject> modifiedObjects) {
        Object3D bactObject = object.getObject();
        Image bactImage = object.getRawImage(bacteria.getSelectedIndex());
        Image mutImage = object.getRawImage(mutation.getSelectedIndex());
        double[] center = bactObject.getCenter(bactImage, true);
        BoundingBox parentOffset = object.getParent().getBounds();
        object.getMeasurements().setValue("BacteriaCenterX", center[0]-parentOffset.getxMin());
        object.getMeasurements().setValue("BacteriaCenterY", center[1]-parentOffset.getyMin());
        object.getMeasurements().setValue("MeanRFPInBacteria", BasicMeasurements.getMeanValue(bactObject, bactImage, true));
        object.getMeasurements().setValue("MeanYFPInBacteria", BasicMeasurements.getMeanValue(bactObject, mutImage, true));
        object.getMeasurements().setValue("BacteriaLength", GeometricalMeasurements.getFeretMax(bactObject));
        object.getMeasurements().setValue("BacteriaArea", GeometricalMeasurements.getVolume(bactObject));
        int includedMutations = ObjectInclusionCount.count(object, mutation.getSelectedIndex(), 0, true);
        object.getMeasurements().setValue("MutationCountInBacteria", includedMutations);
        
        ArrayList<StructureObject> mutList = object.getChildren(mutation.getSelectedIndex()); // return included mutations
        if (includedMutations!=mutList.size()) logger.warn("Mutation count in: {}: {} vs: {}", object, includedMutations, mutList.size());
        
        modifiedObjects.add(object);
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
}
