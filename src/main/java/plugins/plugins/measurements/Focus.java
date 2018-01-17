/*
 * Copyright (C) 2018 jollion
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
import configuration.parameters.ScaleXYZParameter;
import configuration.parameters.StructureParameter;
import dataStructure.objects.StructureObject;
import dataStructure.objects.Voxel;
import image.Image;
import java.util.ArrayList;
import java.util.List;
import measurement.BasicMeasurements;
import measurement.MeasurementKey;
import measurement.MeasurementKeyObject;
import plugins.Measurement;
import processing.ImageFeatures;

/**
 *
 * @author jollion
 */
public class Focus implements Measurement {
    StructureParameter structure = new StructureParameter("Structure");
    ScaleXYZParameter scale = new ScaleXYZParameter("Gradient Scale");
    @Override
    public int getCallStructure() {
        return structure.getParentStructureIdx();
    }

    @Override
    public boolean callOnlyOnTrackHeads() {
        return false;
    }

    @Override
    public List<MeasurementKey> getMeasurementKeys() {
        ArrayList<MeasurementKey> res = new ArrayList<>(2);
        res.add(new MeasurementKeyObject("Focus", structure.getSelectedStructureIdx()));
        res.add(new MeasurementKeyObject("Focus_"+structure.getSelectedStructureIdx(), structure.getParentStructureIdx()));
        return res;
    }

    @Override
    public void performMeasurement(StructureObject object) {
        int structureIdx = structure.getSelectedStructureIdx();
        Image input = object.getRawImage(structure.getParentStructureIdx());
        Image grad = ImageFeatures.getGradientMagnitude(input, scale.getScaleXY(), scale.getScaleZ(input.getScaleXY(), input.getScaleZ()), false);
        double gradAtBorder = 0, intensity=0, borderCount=0, count=0;
        for (StructureObject o : object.getChildren(structureIdx)) {
            List<Voxel> contour = o.getObject().getContour();
            for (Voxel v : contour) gradAtBorder+=grad.getPixel(v.x, v.y, v.z);
            borderCount += contour.size();
            count+=o.getObject().getSize();
            intensity+=BasicMeasurements.getSum(o.getObject(), input, true);
        }
        gradAtBorder/=borderCount;
        intensity/=count;
        double value = gradAtBorder / intensity; 
        for (StructureObject o : object.getChildren(structureIdx)) o.getMeasurements().setValue("Focus", value);
        object.getMeasurements().setValue("Focus_"+structureIdx, value);
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{structure, scale};
    }
    
}
