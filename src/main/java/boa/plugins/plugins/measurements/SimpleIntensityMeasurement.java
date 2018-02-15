/*
 * Copyright (C) 2017 jollion
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
package boa.plugins.plugins.measurements;

import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.StructureParameter;
import boa.configuration.parameters.TextParameter;
import boa.data_structure.StructureObject;
import boa.image.Image;
import java.util.ArrayList;
import java.util.List;
import boa.measurement.BasicMeasurements;
import boa.measurement.MeasurementKey;
import boa.measurement.MeasurementKeyObject;
import boa.plugins.Measurement;

/**
 *
 * @author jollion
 */
public class SimpleIntensityMeasurement implements Measurement {
    protected StructureParameter structureObject = new StructureParameter("Object", -1, false, false);
    protected StructureParameter structureImage = new StructureParameter("Image", -1, false, false);
    TextParameter prefix = new TextParameter("Prefix", "Intensity", false);
    protected Parameter[] parameters = new Parameter[]{structureObject, structureImage, prefix};
    
    public SimpleIntensityMeasurement() {}
    
    public SimpleIntensityMeasurement(int object, int image) {
        this.structureObject.setSelectedStructureIdx(object);
        this.structureImage.setSelectedStructureIdx(image);
    }
    
    public SimpleIntensityMeasurement setPrefix(String prefix) {
        this.prefix.setValue(prefix);
        return this;
    } 
    
    @Override public int getCallStructure() {
        return structureObject.getSelectedStructureIdx();
    }

    @Override public boolean callOnlyOnTrackHeads() {
        return false;
    }

    @Override public List<MeasurementKey> getMeasurementKeys() {
        int structureIdx = structureObject.getSelectedStructureIdx();
        ArrayList<MeasurementKey> res = new ArrayList<>();
        res.add(new MeasurementKeyObject(prefix.getValue()+"Mean", structureIdx));
        res.add(new MeasurementKeyObject(prefix.getValue()+"Sigma", structureIdx));
        //res.add(new MeasurementKeyObject("SigmaIntensity", structureIdx));
        return res;
    }

    @Override public void performMeasurement(StructureObject object) {
        StructureObject parent = object.isRoot() ? object : object.getParent();
        Image image = parent.getRawImage(structureImage.getSelectedStructureIdx());
        double[] meanSd = BasicMeasurements.getMeanSdValue(object.getObject(), image);
        object.getMeasurements().setValue(prefix.getValue()+"Mean", meanSd[0]);
        object.getMeasurements().setValue(prefix.getValue()+"Sigma", meanSd[1]);
    }

    @Override public Parameter[] getParameters() {
        return parameters;
    }
    
}
