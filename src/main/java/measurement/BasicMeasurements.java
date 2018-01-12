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

import dataStructure.objects.Region;
import dataStructure.objects.Voxel;
import image.BoundingBox;
import image.BoundingBox.LoopFunction;
import image.Image;
import image.ImageInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static plugins.Plugin.logger;

/**
 *
 * @author jollion
 */
public class BasicMeasurements {
    public static double getSum(Region object, Image image, boolean useOffset) {
        double value=0;
        if (useOffset) for (Voxel v : object.getVoxels()) value+=image.getPixelWithOffset(v.x, v.y, v.z);
        else for (Voxel v : object.getVoxels()) value+=image.getPixel(v.x, v.y, v.z);
        return value;
    }
    public static double getMeanValue(Region object, Image image, boolean useOffset) {
        return getMeanValue(object.getVoxels(), image, useOffset);
    }
    public static double getMeanValue(List<Voxel> voxels, Image image, boolean useOffset) {
        double value=0;
        if (useOffset) for (Voxel v : voxels) value+=image.getPixelWithOffset(v.x, v.y, v.z);
        else for (Voxel v : voxels) value+=image.getPixel(v.x, v.y, v.z);
        return value/(double)voxels.size();
    }
    public static double getSdValue(Region object, Image image, boolean useOffset) {
        double value=0;
        double value2=0;
        double tmp;
        if (useOffset) {
            for (Voxel v : object.getVoxels()) {
                tmp=image.getPixelWithOffset(v.x, v.y, v.z);
                value+=tmp;
                value2+=tmp*tmp;
            }
        } else {
            for (Voxel v : object.getVoxels()) {
                tmp=image.getPixel(v.x, v.y, v.z);
                value+=tmp;
                value2+=tmp*tmp;
            }
        }
        if (!object.getVoxels().isEmpty()) {
            value/=(double)object.getVoxels().size();
            value2/=(double)object.getVoxels().size();
            return Math.sqrt(value2-value*value);
        } else return Double.NaN;
    }
    public static double[] getMeanSdValue(List<Voxel> voxels, Image image, boolean useOffset) {
        double value=0;
        double value2=0;
        double tmp;
        if (useOffset) {
            for (Voxel v : voxels) {
                tmp=image.getPixelWithOffset(v.x, v.y, v.z);
                value+=tmp;
                value2+=tmp*tmp;
            }
        } else {
            for (Voxel v : voxels) {
                tmp=image.getPixel(v.x, v.y, v.z);
                value+=tmp;
                value2+=tmp*tmp;
            }
        }
        if (!voxels.isEmpty()) {
            value/=(double)voxels.size();
            value2/=(double)voxels.size();
            return new double[] {value, Math.sqrt(value2-value*value)};
        } else return null;
    }

    /**
     * 
     * @param foreground
     * @param background
     * @param image
     * @return [SNR, Mean ForeGround, Mean BackGround, Sd Background]
     */
    public static double[] getSNR(List<Voxel> foreground, List<Voxel> background, Image image, boolean useOffset) { 
        if (foreground.isEmpty() || background.isEmpty()) return null;
        List<Voxel> bck = new ArrayList<Voxel> (background);
        bck.removeAll(foreground);
        double[] sdMeanBack = getMeanSdValue(bck, image, useOffset);
        double fore = getMeanValue(foreground, image, useOffset);
        return new double[] {(fore - sdMeanBack[0]) / sdMeanBack[1], fore, sdMeanBack[0], sdMeanBack[1]};
        
    }
    public static double getMaxValue(Region object, Image image, boolean useOffset) {
        double max=-Double.MAX_VALUE;
        if (useOffset) {
            for (Voxel v : object.getVoxels()) if (image.getPixelWithOffset(v.x, v.y, v.z)>max) max = image.getPixelWithOffset(v.x, v.y, v.z);
        }
        else {
            for (Voxel v : object.getVoxels()) if (image.getPixel(v.x, v.y, v.z)>max) max = image.getPixel(v.x, v.y, v.z);
        }
        return max;
    }
    public static double getMinValue(Region object, Image image, boolean useOffset) {
        double min=Double.MAX_VALUE;
        if (useOffset) {
            for (Voxel v : object.getVoxels()) if (image.getPixelWithOffset(v.x, v.y, v.z)<min) min = image.getPixelWithOffset(v.x, v.y, v.z);
        } else {
            for (Voxel v : object.getVoxels()) if (image.getPixel(v.x, v.y, v.z)<min) min = image.getPixelWithOffset(v.x, v.y, v.z);
        }
        return min;
    }
    public static double getPercentileValue(Region object, double percentile, Image image, boolean useOffset) {
        if (object.getVoxels().isEmpty()) return Double.NaN;
        if (percentile<=0) return getMinValue(object, image, useOffset);
        if (percentile>=1) return getMaxValue(object, image, useOffset);
        object.setVoxelValues(image, useOffset);
        Collections.sort(object.getVoxels());
        double idxD = percentile * object.getVoxels().size();
        int idx = (int) idxD;
        double delta = idxD - idx;
        return object.getVoxels().get(idx).value * (1 - delta) + (delta) * object.getVoxels().get(idx+1).value;
    }
}
