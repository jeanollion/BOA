/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins.plugins.measurements.objectFeatures;

import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.measurement.GeometricalMeasurements;
import boa.plugins.GeometricalFeature;
import boa.plugins.ObjectFeature;
import boa.plugins.ToolTip;
import static boa.plugins.plugins.measurements.objectFeatures.Size.SCALED_TT;

/**
 *
 * @author jollion
 */
public class SpineLength implements GeometricalFeature, ToolTip {
    BooleanParameter scaled = new BooleanParameter("Scale", "Unit", "Pixel", false).setToolTipText(SCALED_TT);
    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{scaled};
    }
    public SpineLength setScaled(boolean scaled) {
        this.scaled.setSelected(scaled);
        return this;
    }
    @Override
    public ObjectFeature setUp(StructureObject parent, int childStructureIdx, RegionPopulation childPopulation) {
        return this;
    }

    @Override
    public double performMeasurement(Region region) {
        double l =  GeometricalMeasurements.getSpineLength(region);
        if (scaled.getSelected()) l*=region.getScaleXY();
        return l;
    }

    @Override
    public String getDefaultName() {
        return "SpineLength";
    }

    @Override
    public String getToolTipText() {
        return "Length of the spine (skeleton): takes into acount rippling deformation. Only valid for rod shaped regions";
    }
    
}
