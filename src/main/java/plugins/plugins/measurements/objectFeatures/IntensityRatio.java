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
package plugins.plugins.measurements.objectFeatures;

import dataStructure.objects.Object3D;
import image.BoundingBox;
import plugins.objectFeature.IntensityMeasurementCore.IntensityMeasurements;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class IntensityRatio extends SNR {
    
    
    @Override public double performMeasurement(Object3D object, BoundingBox offset) {
        if (core==null) synchronized(this) {setUpOrAddCore(null);}
        Object3D parentObject; 
        if (childrenParentMap==null) parentObject = super.parent.getObject();
        else parentObject=this.childrenParentMap.get(object);
        if (parentObject==null) return 0;
        IntensityMeasurements iParent = super.core.getIntensityMeasurements(parentObject, null);
        double fore = super.core.getIntensityMeasurements(object, offset).mean;
        return fore/iParent.mean ;
    }

    @Override public String getDefaultName() {
        return "intensity ratio";
    }
    
}
