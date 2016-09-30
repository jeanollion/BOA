/*
 * Copyright (C) 2015 jollion
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
package measurement;

import dataStructure.objects.Object3D;
import dataStructure.objects.Voxel;
import image.Image;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author jollion
 */
public class GeometricalMeasurements {
    
    public static double getVolume(Object3D o) {
        int count = o.getVoxels().size();
        if (o.is3D()) return count * o.getScaleXY() * o.getScaleXY() * o.getScaleZ();
        else  return count * o.getScaleXY() * o.getScaleXY();
    }
    public static double getFeretMax(Object3D o) {
        double d2Max = 0;
        List<Voxel> list = o.getContour();
        int voxCount = list.size();
        double scaleXY = o.getScaleXY();
        double scaleZ = o.getScaleZ();
        for (int i = 0; i<voxCount-1; ++i) {
            for (int j = i+1; j<voxCount; ++j) {
                double d2Temp = list.get(i).getDistanceSquare(list.get(j), scaleXY, scaleZ);
                if (d2Temp>d2Max) d2Max = d2Temp;
            }
        }
        return Math.sqrt(d2Max);
    }
    
    public static double getDistance(Object3D o1, Object3D o2) {
        return getDistance(o1.getCenter(false), o2.getCenter(false), o1.getScaleXY(), o1.getScaleZ());
    }
    public static double getDistance(Object3D o1, Object3D o2, Image im1, Image im2) {
        return getDistance(o1.getCenter(im1, false), o2.getCenter(im2, false), o1.getScaleXY(), o1.getScaleZ());
    }
    
    public static double getDistance(double[] c1, double[] c2, double scaleXY, double scaleZ) {
        return Math.sqrt(Math.pow((c1[0]-c2[0])*scaleXY, 2) + Math.pow((c1[1]-c2[1])*scaleXY, 2) + Math.pow((c1[2]-c2[2])*scaleZ, 2));
    }
    
}
