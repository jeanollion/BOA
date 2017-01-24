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
package plugins.plugins.measurements;

import configuration.parameters.Parameter;
import configuration.parameters.StructureParameter;
import configuration.parameters.TextParameter;
import dataStructure.objects.StructureObject;
import image.Image;
import java.util.ArrayList;
import java.util.List;
import measurement.BasicMeasurements;
import measurement.MeasurementKey;
import measurement.MeasurementKeyObject;
import plugins.Measurement;

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
        res.add(new MeasurementKeyObject(prefix.getValue()+"Sum", structureIdx));
        //res.add(new MeasurementKeyObject("SigmaIntensity", structureIdx));
        return res;
    }

    @Override public void performMeasurement(StructureObject object) {
        StructureObject parent = object.isRoot() ? object : object.getParent();
        Image image = parent.getRawImage(structureImage.getSelectedStructureIdx());
        double mean = BasicMeasurements.getMeanValue(object.getObject(), image, true);
        object.getMeasurements().setValue(prefix.getValue()+"Mean", mean);
        object.getMeasurements().setValue(prefix.getValue()+"Sum", mean * object.getObject().getSize());
    }

    @Override public Parameter[] getParameters() {
        return parameters;
    }
    
}
