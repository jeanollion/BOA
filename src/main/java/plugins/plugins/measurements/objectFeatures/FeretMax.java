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
package plugins.plugins.measurements.objectFeatures;

import configuration.parameters.ChoiceParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import image.BoundingBox;
import measurement.GeometricalMeasurements;
import plugins.ObjectFeature;

/**
 *
 * @author jollion
 */
public class FeretMax implements ObjectFeature {
    ChoiceParameter scaled = new ChoiceParameter("Scale", new String[]{"Pixel", "Unit"}, "Unit", false);
    public FeretMax setScale(boolean unit) {
        scaled.setSelectedIndex(unit?1:0);
        return this;
    }
    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{scaled};
    }

    @Override
    public ObjectFeature setUp(StructureObject parent, int childStructureIdx, ObjectPopulation childPopulation) {
        return this;
    }

    @Override
    public double performMeasurement(Object3D object, BoundingBox offset) {
        double feret = GeometricalMeasurements.getFeretMax(object);
        if (scaled.getSelectedIndex()==0) feret/=object.getScaleXY();
        return feret;
    }

    @Override
    public String getDefaultName() {
        return "FeretMax";
    }
    
}
