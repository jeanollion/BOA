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

import configuration.parameters.Parameter;
import configuration.parameters.StructureParameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.StructureObject;
import dataStructure.objects.Voxel;
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
public class BacteriaMeasurements implements Measurement {
    protected StructureParameter structure = new StructureParameter("Bacteria Structure", -1, false, false);
    protected StructureParameter mutation = new StructureParameter("Mutation Structure", -1, false, false);
    protected Parameter[] parameters = new Parameter[]{structure, mutation};
    
    
    public BacteriaMeasurements(){}
    
    public BacteriaMeasurements(int bacteriaStructureIdx, int mutationStructureIdx){
        this.structure.setSelectedIndex(bacteriaStructureIdx);
        this.mutation.setSelectedIndex(mutationStructureIdx);
    }
    
    public int getCallStructure() {
        return structure.getSelectedIndex();
    }

    public boolean callOnlyOnTrackHeads() {
        return false;
    }

    public List<MeasurementKey> getMeasurementKeys() {
        ArrayList<MeasurementKey> res = new ArrayList<MeasurementKey>();
        res.add(new MeasurementKeyObject("Area(units)", structure.getSelectedIndex()));
        res.add(new MeasurementKeyObject("Length(units)", structure.getSelectedIndex()));
        res.add(new MeasurementKeyObject("SignalIntegratedIntensity", structure.getSelectedIndex()));
        //res.add(new MeasurementKeyObject("FeretMin(units)", structure.getSelectedIndex()));
        //res.add(new MeasurementKeyObject("Squeleton", structure.getSelectedIndex()));
        res.add(new MeasurementKeyObject("MutationCount", structure.getSelectedIndex()));
        return res;
    }

    public void performMeasurement(StructureObject object, List<StructureObject> modifiedObjects) {
        // measurements on bacteria
        Object3D o = object.getObject();
        //object.getMeasurements().setValue("Area(units)", GeometricalMeasurements.getVolume(o));
        object.getMeasurements().setValue("Length(units)", GeometricalMeasurements.getFeretMax(o));
        //object.getMeasurements().setValue("MutationCount", ObjectInclusionCount.count(object, mutation.getSelectedIndex(), 0.1d, true));
        StructureObject parent = object.isRoot()?object:object.getParent();
        //object.getMeasurements().setValue("SignalIntegratedIntensity", BasicMeasurements.getSum(o, parent.getRawImage(object.getStructureIdx())));
        modifiedObjects.add(object);
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    
}
