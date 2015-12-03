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
import java.util.Collections;
import java.util.List;

/**
 *
 * @author jollion
 */
public class BasicMeasurements {
    public static double getSum(Object3D object, Image image) {
        double value=0;
        for (Voxel v : object.getVoxels()) value+=image.getPixel(v.x, v.y, v.z);
        return value;
    }
    public static double getMeanValue(Object3D object, Image image) {
        return getMeanValue(object.getVoxels(), image);
    }
    public static double getMeanValue(List<Voxel> voxels, Image image) {
        double value=0;
        for (Voxel v : voxels) value+=image.getPixel(v.x, v.y, v.z);
        if (!voxels.isEmpty()) return value/voxels.size();
        else return 0;
    }
    public static double getSdValue(Object3D object, Image image) {
        double value=0;
        double value2=0;
        double tmp;
        for (Voxel v : object.getVoxels()) {
            tmp=image.getPixel(v.x, v.y, v.z);
            value+=tmp;
            value2+=tmp*tmp;
        }
        if (!object.getVoxels().isEmpty()) {
            value/=(double)object.getVoxels().size();
            value2/=(double)object.getVoxels().size();
            return Math.sqrt(value2-value*value);
        } else return 0;
    }
    public static double getMaxValue(Object3D object, Image image) {
        double max=-Double.MAX_VALUE;
        for (Voxel v : object.getVoxels()) if (image.getPixel(v.x, v.y, v.z)>max) max = image.getPixel(v.x, v.y, v.z);
        return max;
    }
    public static double getMinValue(Object3D object, Image image) {
        double min=Double.MAX_VALUE;
        for (Voxel v : object.getVoxels()) if (image.getPixel(v.x, v.y, v.z)<min) min = image.getPixel(v.x, v.y, v.z);
        return min;
    }
    public static double getPercentileValue(Object3D object, double percentile, Image image) {
        if (object.getVoxels().isEmpty()) return Double.NaN;
        if (percentile<=0) return getMinValue(object, image);
        if (percentile>=1) return getMaxValue(object, image);
        for (Voxel v : object.getVoxels()) v.value=image.getPixel(v.x, v.y, v.z);
        Collections.sort(object.getVoxels());
        double idxD = percentile * object.getVoxels().size();
        int idx = (int) idxD;
        double delta = idxD - idx;
        return object.getVoxels().get(idx).value * (1 - delta) + (delta) * object.getVoxels().get(idx+1).value;
    }
}
