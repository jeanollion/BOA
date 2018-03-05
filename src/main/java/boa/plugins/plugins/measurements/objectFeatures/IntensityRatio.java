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

import boa.data_structure.Region;
import boa.image.MutableBoundingBox;
import boa.plugins.objectFeature.IntensityMeasurementCore.IntensityMeasurements;
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
