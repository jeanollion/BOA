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

import boa.plugins.objectFeature.IntensityMeasurement;
import boa.data_structure.Region;
import boa.image.MutableBoundingBox;
import boa.plugins.ToolTip;

/**
 *
 * @author jollion
 */
public class Max extends IntensityMeasurement implements ToolTip {

    @Override public double performMeasurement(Region object) {
        return core.getIntensityMeasurements(object).max;
    }

    @Override public String getDefaultName() {
        return "Max";
    }
    @Override
    public String getToolTipText() {
        return "Maximal intensity value within object";
    }
}
