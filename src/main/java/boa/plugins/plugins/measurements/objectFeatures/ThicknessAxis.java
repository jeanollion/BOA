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

import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.image.processing.ImageTransformation;
import boa.image.processing.ImageTransformation.MainAxis;
import boa.measurement.GeometricalMeasurements;
import boa.plugins.ObjectFeature;
import boa.plugins.ToolTip;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class ThicknessAxis implements ObjectFeature, ToolTip {
    ChoiceParameter axis = new ChoiceParameter("Axis", Utils.toStringArray(MainAxis.values()), ImageTransformation.MainAxis.X.toString(), false);
    ChoiceParameter statistics = new ChoiceParameter("Statistics", new String[]{"Mean", "Median", "Max"}, "Mean", false);
    String toolTip = "Estimates the thickness of a region along a given axis (X, Y or Z)";
    public ThicknessAxis setAxis(MainAxis axis) {
        this.axis.setSelectedItem(axis.name());
        return this;
    }
    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{axis, statistics};
    }

    @Override
    public ObjectFeature setUp(StructureObject parent, int childStructureIdx, RegionPopulation childPopulation) {
        return this;
    }

    @Override
    public double performMeasurement(Region region) {
        MainAxis ax = MainAxis.valueOf(axis.getSelectedItem());
        switch(ax) {
            case X:
            default:
                switch (statistics.getSelectedIndex()) {
                    case 0:
                    default:
                        return GeometricalMeasurements.meanThicknessX(region);
                    case 1:
                        return GeometricalMeasurements.medianThicknessX(region);
                    case 2:
                        return GeometricalMeasurements.maxThicknessX(region);
                }
            case Y:
                switch (statistics.getSelectedIndex()) {
                    case 0:
                    default:
                        return GeometricalMeasurements.meanThicknessY(region);
                    case 1:
                        return GeometricalMeasurements.medianThicknessY(region);
                    case 2:
                        return GeometricalMeasurements.maxThicknessY(region);
                }
            case Z:
                switch (statistics.getSelectedIndex()) {
                    case 0:
                    default:
                        return GeometricalMeasurements.meanThicknessZ(region);
                    case 1:
                        return GeometricalMeasurements.medianThicknessZ(region);
                    case 2:
                        return GeometricalMeasurements.maxThicknessZ(region);
                }    
        }
    }

    @Override
    public String getDefaultName() {
        return "ThicknessAxis";
    }

    @Override
    public String getToolTipText() {
        return toolTip;
    }
    
}
