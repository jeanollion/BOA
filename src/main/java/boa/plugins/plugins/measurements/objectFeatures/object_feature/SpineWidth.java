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
package boa.plugins.plugins.measurements.objectFeatures.object_feature;

import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.measurement.GeometricalMeasurements;
import boa.plugins.GeometricalFeature;
import boa.plugins.ObjectFeature;
import boa.plugins.ToolTip;
import static boa.plugins.plugins.measurements.objectFeatures.object_feature.Size.SCALED_TT;

/**
 *
 * @author jollion
 */
public class SpineWidth implements GeometricalFeature, ToolTip {
    BooleanParameter scaled = new BooleanParameter("Scale", "Unit", "Pixel", false).setToolTipText(SCALED_TT);;
    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{scaled};
    }
    public SpineWidth setScaled(boolean scaled) {
        this.scaled.setSelected(scaled);
        return this;
    }
    @Override
    public ObjectFeature setUp(StructureObject parent, int childStructureIdx, RegionPopulation childPopulation) {
        return this;
    }

    @Override
    public double performMeasurement(Region region) {
        double w= GeometricalMeasurements.getSpineLengthAndWidth(region)[1];
        if (scaled.getSelected()) w *= region.getScaleXY();
        return w;
    }

    @Override
    public String getDefaultName() {
        return "SpineWidth";
    }
    @Override
    public String getToolTipText() {
        return "Median value of the spine radii. Only valid for rod shaped regions";
    }
}
