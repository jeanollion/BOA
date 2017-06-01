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

import image.BoundingBox.LoopFunction2;

/**
 *
 * @author jollion
 */
public class ThresholdMask implements ImageMask {
    final InsideMaskFunction insideMask;
    final InsideMaskXYFunction insideMaskXY;
    final Image image;
    final double threshold;
    public ThresholdMask(Image imageToThreshold, double thld, boolean foregroundOverthreshold, boolean strict) {
        this.image = imageToThreshold;
        this.threshold=thld;
        if (foregroundOverthreshold) {
            if (strict) insideMask = (x, y, z) -> image.getPixel(x, y, z)>threshold;
            else insideMask = (x, y, z) -> image.getPixel(x, y, z)>threshold;
        } else {
            if (strict) insideMask = (x, y, z) -> image.getPixel(x, y, z)<threshold;
            else insideMask = (x, y, z) -> image.getPixel(x, y, z)<=threshold;
        }
        if (foregroundOverthreshold) {
            if (strict) insideMaskXY = (xy, z) -> image.getPixel(xy, z)>threshold;
            else insideMaskXY = (xy, z) -> image.getPixel(xy, z)>threshold;
        } else {
            if (strict) insideMaskXY = (xy, z) -> image.getPixel(xy, z)<threshold;
            else insideMaskXY = (xy, z) -> image.getPixel(xy, z)<=threshold;
        }
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
        return insideMask.insideMask(x-image.offsetX, y-image.offsetY, z-image.offsetZ);
    }

    @Override
    public boolean insideMaskWithOffset(int xy, int z) {
        return insideMaskXY.insideMask(xy-image.offsetXY, z-image.offsetZ);
    }

    @Override
    public int count() {
        int count = 0;
        for (int z = 0; z< image.sizeZ; ++z) {
            for (int xy=0; xy<image.sizeXY; ++xy) {
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
    private interface InsideMaskFunction {
        public boolean insideMask(int x, int y, int z);
    }
    private interface InsideMaskXYFunction {
        public boolean insideMask(int xy, int z);
    }
}
