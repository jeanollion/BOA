/*
 * Copyright (C) 2016 jollion
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
