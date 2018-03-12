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
import boa.data_structure.Voxel;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import boa.measurement.BasicMeasurements;

/**
 *
 * @author jollion
 */
public class MeanAtBorder extends IntensityMeasurement {
    @Override
    public double performMeasurement(Region object) {
        return BasicMeasurements.getMeanValue(object.getContour(), core.getIntensityMap(true), object.isAbsoluteLandMark());
    }
    @Override
    public String getDefaultName() {
        return "MeanIntensityBorder";
    }
    
}
