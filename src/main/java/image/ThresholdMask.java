/*
 * Copyright (C) 2017 jollion
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
package image;

/**
 *
 * @author jollion
 */
public class ThresholdMask implements ImageMask {
    final InsideMaskFunction insideMask;
    final InsideMaskXYFunction insideMaskXY;
    final ImageProperties image;
    final boolean is2D;
    public ThresholdMask(Image imageToThreshold, double threshold, boolean foregroundOverthreshold, boolean strict) {
        this.image = imageToThreshold;
        if (foregroundOverthreshold) {
            if (strict) insideMask = (x, y, z) -> imageToThreshold.getPixel(x, y, z)>threshold;
            else insideMask = (x, y, z) -> imageToThreshold.getPixel(x, y, z)>threshold;
        } else {
            if (strict) insideMask = (x, y, z) -> imageToThreshold.getPixel(x, y, z)<threshold;
            else insideMask = (x, y, z) -> imageToThreshold.getPixel(x, y, z)<=threshold;
        }
        if (foregroundOverthreshold) {
            if (strict) insideMaskXY = (xy, z) -> imageToThreshold.getPixel(xy, z)>threshold;
            else insideMaskXY = (xy, z) -> imageToThreshold.getPixel(xy, z)>threshold;
        } else {
            if (strict) insideMaskXY = (xy, z) -> imageToThreshold.getPixel(xy, z)<threshold;
            else insideMaskXY = (xy, z) -> imageToThreshold.getPixel(xy, z)<=threshold;
        }
        is2D = false;
    }
    /**
     * Construct ThresholdMask2D
     * @param imageToThreshold
     * @param threshold
     * @param foregroundOverthreshold
     * @param strict
     * @param z 
     */
    public ThresholdMask(Image imageToThreshold, double threshold, boolean foregroundOverthreshold, boolean strict, int z) {
        this.image = imageToThreshold;
        if (foregroundOverthreshold) {
            if (strict) insideMask = (x, y, zz) -> imageToThreshold.getPixel(x, y, z)>threshold;
            else insideMask = (x, y, zz) -> imageToThreshold.getPixel(x, y, z)>threshold;
        } else {
            if (strict) insideMask = (x, y, zz) -> imageToThreshold.getPixel(x, y, z)<threshold;
            else insideMask = (x, y, zz) -> imageToThreshold.getPixel(x, y, z)<=threshold;
        }
        if (foregroundOverthreshold) {
            if (strict) insideMaskXY = (xy, zz) -> imageToThreshold.getPixel(xy, z)>threshold;
            else insideMaskXY = (xy, zz) -> imageToThreshold.getPixel(xy, z)>threshold;
        } else {
            if (strict) insideMaskXY = (xy, zz) -> imageToThreshold.getPixel(xy, z)<threshold;
            else insideMaskXY = (xy, zz) -> imageToThreshold.getPixel(xy, z)<=threshold;
        }
        is2D=true;
    }
    public ThresholdMask(ImageProperties imageProperties, InsideMaskFunction insideMask, InsideMaskXYFunction insideMaskXY, boolean is2D) {
        this.image=imageProperties;
        this.insideMask=insideMask;
        this.insideMaskXY=insideMaskXY;
        this.is2D = is2D;
    }
    public static ThresholdMask or(ThresholdMask mask1, ThresholdMask mask2) {
        if (mask1.getSizeX()!=mask2.getSizeX() || mask1.getSizeY()!=mask2.getSizeY()) throw new IllegalArgumentException("Mask1 & 2 should have same XY dimensions");
        if (mask1.getSizeZ()!=mask2.getSizeZ() && !mask1.is2D && !mask2.is2D) throw new IllegalArgumentException("Mask1 & 2 should either have same Z dimensions or be 2D");
        return new ThresholdMask(mask1.is2D?mask2:mask1, (x, y, z)->mask1.insideMask.insideMask(x, y, z)||mask2.insideMask(x,y, z), (xy, z)->mask1.insideMask(xy, z)||mask2.insideMask(xy, z), mask1.is2D && mask2.is2D);
    }
    public static ThresholdMask and(ThresholdMask mask1, ThresholdMask mask2) {
        if (mask1.getSizeX()!=mask2.getSizeX() || mask1.getSizeY()!=mask2.getSizeY()) throw new IllegalArgumentException("Mask1 & 2 should have same XY dimensions");
        if (mask1.getSizeZ()!=mask2.getSizeZ() && !mask1.is2D && !mask2.is2D) throw new IllegalArgumentException("Mask1 & 2 should either have same Z dimensions or be 2D");
        return new ThresholdMask(mask1.is2D?mask2:mask1, (x, y, z)->mask1.insideMask.insideMask(x, y, z)&&mask2.insideMask(x,y, z), (xy, z)->mask1.insideMask(xy, z)&&mask2.insideMask(xy, z), mask1.is2D && mask2.is2D);
    }
    @Override
    public boolean insideMask(int x, int y, int z) {
        return insideMask.insideMask(x, y, z);
    }

    @Override
    public boolean insideMask(int xy, int z) {
        return insideMaskXY.insideMask(xy, z);
    }

    @Override
    public boolean insideMaskWithOffset(int x, int y, int z) {
        return insideMask.insideMask(x-image.getOffsetX(), y-image.getOffsetY(), z-image.getOffsetZ());
    }

    @Override
    public boolean insideMaskWithOffset(int xy, int z) {
        return insideMaskXY.insideMask(xy-image.getOffsetX(), z-image.getOffsetZ());
    }

    @Override
    public int count() {
        int count = 0;
        for (int z = 0; z< image.getScaleZ(); ++z) {
            for (int xy=0; xy<image.getSizeXY(); ++xy) {
                if (insideMask(xy, z)) ++count;
            }
        }
        return count;
    }

    @Override
    public String getName() {
        return image.getName()+"(Thresholded)";
    }

    @Override
    public int getSizeX() {
        return image.getSizeX();
    }

    @Override
    public int getSizeY() {
        return image.getSizeY();
    }

    @Override
    public int getSizeZ() {
        return image.getSizeZ();
    }

    @Override
    public int getSizeXY() {
        return image.getSizeXY();
    }

    @Override
    public int getSizeXYZ() {
        return image.getSizeXYZ();
    }

    @Override
    public int getOffsetX() {
        return image.getOffsetX();
    }
    
    @Override
    public int getOffsetXY() {
        return image.getOffsetXY();
    }

    @Override
    public int getOffsetY() {
        return image.getOffsetY();
    }

    @Override
    public int getOffsetZ() {
        return image.getOffsetZ();
    }

    @Override
    public float getScaleXY() {
        return image.getScaleXY();
    }

    @Override
    public float getScaleZ() {
        return image.getScaleZ();
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return image.contains(x, y, z);
    }

    @Override
    public boolean containsWithOffset(int x, int y, int z) {
        return image.containsWithOffset(x, y, z);
    }

    @Override
    public BoundingBox getBoundingBox() {
        return image.getBoundingBox();
    }

    @Override
    public boolean sameSize(ImageProperties image) {
        return image.sameSize(image);
    }

    @Override
    public ImageProperties getProperties() {
        return image.getProperties();
    }
    public interface InsideMaskFunction {
        public boolean insideMask(int x, int y, int z);
    }
    public interface InsideMaskXYFunction {
        public boolean insideMask(int xy, int z);
    }
}
