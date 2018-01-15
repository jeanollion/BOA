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
public class ImageMask2D implements ImageMask {
    final ImageMask mask;
    final int z;
    public ImageMask2D(ImageMask mask) {
        this.mask = mask;
        this.z=0;
    }
    public ImageMask2D(ImageMask mask, int z) {
        this.mask = mask;
        this.z=z;
    }
    
    @Override
    public boolean insideMask(int x, int y, int z) {
        return mask.insideMask(x, y, this.z);
    }

    @Override
    public boolean insideMask(int xy, int z) {
        return mask.insideMask(xy, this.z);
    }

    @Override
    public boolean insideMaskWithOffset(int x, int y, int z) {
        return mask.insideMaskWithOffset(x, y, this.z);
    }

    @Override
    public boolean insideMaskWithOffset(int xy, int z) {
        return mask.insideMaskWithOffset(xy, this.z);
    }

    @Override
    public int count() {
        return mask.count();
    }

    @Override
    public String getName() {
        return mask.getName();
    }

    @Override
    public int getSizeX() {
        return mask.getSizeX();
    }

    @Override
    public int getSizeY() {
        return mask.getSizeY();
    }

    @Override
    public int getSizeZ() {
        return mask.getSizeZ();
    }

    @Override
    public int getSizeXY() {
        return mask.getSizeXY();
    }

    @Override
    public int getSizeXYZ() {
        return mask.getSizeXYZ();
    }

    @Override
    public int getOffsetX() {
        return mask.getOffsetX();
    }

    @Override
    public int getOffsetXY() {
        return mask.getOffsetXY();
    }

    @Override
    public int getOffsetY() {
        return mask.getOffsetY();
    }

    @Override
    public int getOffsetZ() {
        return mask.getOffsetZ();
    }

    @Override
    public float getScaleXY() {
        return mask.getScaleXY();
    }

    @Override
    public float getScaleZ() {
        return mask.getScaleZ();
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return z>=0 && mask.contains(x, y, this.z);
    }

    @Override
    public boolean containsWithOffset(int x, int y, int z) {
        return z>=mask.getOffsetZ() && mask.containsWithOffset(x, y, this.z);
    }

    @Override
    public BoundingBox getBoundingBox() {
        return mask.getBoundingBox();
    }

    @Override
    public boolean sameSize(ImageProperties image) {
        return mask.sameSize(image);
    }

    @Override
    public ImageProperties getProperties() {
        return mask.getProperties();
    }
    
}
