/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins.plugins.measurements.objectFeatures.object_feature;

import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.image.MutableBoundingBox;
import boa.plugins.GeometricalFeature;
import boa.plugins.ObjectFeature;
import boa.plugins.ToolTip;

/**
 *
 * @author jollion
 */
public class Size implements GeometricalFeature, ToolTip {
    public final static String SCALED_TT = "When Unit is chosen, the size is multiplied by the size of a voxel in unit, depending on the calibration of the image";
    protected BooleanParameter scaled = new BooleanParameter("Scaled", "Unit", "Pixel", true).setToolTipText(SCALED_TT);
    public Size setScaled(boolean unit) {
        scaled.setSelected(unit);
        return this;
    }
    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{scaled};
    }

    @Override
    public ObjectFeature setUp(StructureObject parent, int childStructureIdx, RegionPopulation childPopulation) {
        return this;
    }

    @Override
    public double performMeasurement(Region object) {
        double size = object.size();
        if (scaled.getSelected()) {
            size*=Math.pow(object.getScaleXY(), 2);
            if (!object.is2D()) size*=object.getScaleZ();
        }
        return size;
    }

    @Override
    public String getDefaultName() {
        return "Size";
    }

    @Override
    public String getToolTipText() {
        return "Size is estimated by counting the number of voxels of an object.";
    }
    
}
