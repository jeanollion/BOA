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
public class BacteriaMeasurementsWoleMC implements Measurement {
    protected StructureParameter structure = new StructureParameter("Bacteria Structure", -1, false, false);
    protected StructureParameter mutation = new StructureParameter("Mutation Structure", -1, false, false);
    protected Parameter[] parameters = new Parameter[]{structure, mutation};
    
    
    public BacteriaMeasurementsWoleMC(){}
    
    public BacteriaMeasurementsWoleMC(int bacteriaStructureIdx, int mutationStructureIdx){
        this.structure.setSelectedIndex(bacteriaStructureIdx);
        this.mutation.setSelectedIndex(mutationStructureIdx);
    }
    
    public int getCallStructure() {
        return structure.getParentStructureIdx();
    }

    public boolean callOnlyOnTrackHeads() {
        return false;
    }

    public List<MeasurementKey> getMeasurementKeys() {
        int parentIdx = structure.getParentStructureIdx();
        ArrayList<MeasurementKey> res = new ArrayList<MeasurementKey>();
        res.add(new MeasurementKeyObject("BacteriaMeanIntensity", parentIdx));
        res.add(new MeasurementKeyObject("MutationMeanIntensity", parentIdx));
        //res.add(new MeasurementKeyObject("FeretMin(units)", structure.getSelectedIndex()));
        //res.add(new MeasurementKeyObject("Squeleton", structure.getSelectedIndex()));
        res.add(new MeasurementKeyObject("MutationCount", parentIdx));
        res.add(new MeasurementKeyObject("BacteriaCount", parentIdx));
        return res;
    }

    public void performMeasurement(StructureObject object, List<StructureObject> modifiedObjects) {
        // measurements on microchannels
        Object3D o = object.getObject();
        object.getMeasurements().setValue("MutationCount", ObjectInclusionCount.count(object, mutation.getSelectedIndex(), 0, true));
        object.getMeasurements().setValue("BacteriaCount", ObjectInclusionCount.count(object, structure.getSelectedIndex(), 0, false));
        Image bact = object.getRawImage(structure.getSelectedIndex());
        Image mut = object.getRawImage(mutation.getSelectedIndex());
        BoundingBox bounds = o.getBounds().duplicate();
        o.translate(bounds, true);
        object.getMeasurements().setValue("BacteriaMeanIntensity", BasicMeasurements.getMeanValue(o, bact));
        object.getMeasurements().setValue("MutationMeanIntensity", BasicMeasurements.getMeanValue(o, mut));
        o.translate(bounds, false);
        modifiedObjects.add(object);
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
}
