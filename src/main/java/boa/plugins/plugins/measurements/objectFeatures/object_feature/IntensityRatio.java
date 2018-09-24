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

import boa.data_structure.Region;
import boa.image.MutableBoundingBox;
import boa.plugins.object_feature.IntensityMeasurementCore.IntensityMeasurements;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class IntensityRatio extends SNR {
    
    
    @Override public double performMeasurement(Region object) {
        if (core==null) synchronized(this) {setUpOrAddCore(null, null);}
        Region parentObject; 
        if (foregroundMapBackground==null) parentObject = super.parent.getRegion();
        else parentObject=this.foregroundMapBackground.get(object);
        if (parentObject==null) return 0;
        IntensityMeasurements iParent = super.core.getIntensityMeasurements(parentObject);
        double fore = super.core.getIntensityMeasurements(object).mean;
        return fore/iParent.mean ;
    }

    @Override public String getDefaultName() {
        return "IntensityRatio";
    }
    
}
