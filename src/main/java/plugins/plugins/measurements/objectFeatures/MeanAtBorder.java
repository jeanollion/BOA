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

import plugins.objectFeature.IntensityMeasurement;
import dataStructure.objects.Object3D;
import dataStructure.objects.Voxel;
import image.BoundingBox;
import image.Image;

/**
 *
 * @author jollion
 */
public class MeanAtBorder extends IntensityMeasurement {
    @Override
    public double performMeasurement(Object3D object, BoundingBox offset) {
        Image im = core.getIntensityMap(true);
        if (offset==null) offset=new BoundingBox(0, 0, 0);
        int offX=offset.getxMin()-intensityMap.getOffsetX();
        int offY=offset.getyMin()-intensityMap.getOffsetY();
        int offZ=offset.getzMin()-intensityMap.getOffsetZ();
        double sum = 0;
        for (Voxel v : object.getContour()) sum += im.getPixel(v.x+offX, v.y+offY, v.z+offZ);
        return sum/=object.getContour().size();
    }
    @Override
    public String getDefaultName() {
        return "MeanIntensityBorder";
    }
    
}
