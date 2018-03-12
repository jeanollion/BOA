/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.image;

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
    public boolean sameDimensions(ImageProperties image) {
        return mask.sameDimensions(image);
    }

    @Override
    public ImageProperties getProperties() {
        return mask.getProperties();
    }

    @Override
    public ImageMask2D duplicateMask() {
        return new ImageMask2D(mask.duplicateMask(), z);
    }

    @Override
    public ImageMask2D addOffset(BoundingBox bounds) {
        mask.addOffset(bounds); // TODO will modify mask -> should it be duplicated ? 
        return this;
    }

    @Override
    public ImageMask2D setCalibration(ImageProperties properties) {
        this.mask.setCalibration(properties);
        return this;
    }

    @Override
    public ImageMask2D setCalibration(float scaleXY, float scaleZ) {
        mask.setCalibration(scaleXY, scaleZ);
        return this;
    }

    @Override
    public ImageMask2D addOffset(ImageProperties bounds) {
        mask.addOffset(bounds);
        return this;
    }
    
}
