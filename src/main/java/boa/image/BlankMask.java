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


public class BlankMask extends BoundingBox implements ImageMask<BlankMask> {
    float scaleXY, scaleZ;
    public BlankMask(int sizeX, int sizeY, int sizeZ, int offsetX, int offsetY, int offsetZ, float scaleXY, float scaleZ) {
        super(offsetX, offsetX+sizeX-1, offsetY, offsetY+sizeY-1, offsetZ, offsetZ+sizeZ-1);
        this.scaleXY=scaleXY;
        this.scaleZ=scaleZ;
    }

    public BlankMask(int sizeX, int sizeY, int sizeZ) {
        this(sizeX, sizeY, sizeZ, 0, 0, 0, 1, 1);
    }
    
    public BlankMask(ImageProperties properties) {
        this(properties.getSizeX(), properties.getSizeY(), properties.getSizeZ(), properties.getOffsetX(), properties.getOffsetY(), properties.getOffsetZ(), properties.getScaleXY(), properties.getScaleZ());
    }
    
    public BlankMask(BoundingBox bounds, float scaleXY, float scaleZ) {
        this(bounds.getSizeX(), bounds.getSizeY(), bounds.getSizeZ(), bounds.getxMin(), bounds.getyMin(), bounds.getzMin(), scaleXY, scaleZ);
    }
    
    @Override
    public boolean insideMask(int x, int y, int z) {
        return true; // contains should already be checked
        //return (x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ);
    }

    @Override
    public boolean insideMask(int xy, int z) {
        return true; // contains should already be checked
        //return (xy >= 0 && xy < sizeXY && z >= 0 && z < sizeZ);
    }
    
    @Override public int count() {
        return getSizeXYZ();
    }
    
    @Override
    public boolean insideMaskWithOffset(int x, int y, int z) {
        return true; // contains should already be checked
        //x-=offsetX; y-=offsetY; z-=offsetZ;
        //return (x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ);
    }
    @Override
    public boolean insideMaskWithOffset(int xy, int z) {
        return true; // contains should already be checked
        //xy-=offsetXY;  z-=offsetZ;
        //return (xy >= 0 && xy < sizeXY &&  z >= 0 && z < sizeZ);
    }


    @Override
    public BlankMask duplicateMask() {
        return new BlankMask(this, scaleXY, scaleZ);
    }

    @Override
    public String getName() {
        return "mask";
    }

    @Override
    public float getScaleXY() {
        return scaleXY;
    }

    @Override
    public float getScaleZ() {
        return scaleZ;
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return 0<=x && xMax-xMin>=x && 0<=y && yMax-yMin>=y && 0<=z && zMax-zMin>=z;
    }

    @Override
    public BoundingBox getBoundingBox() {
        return super.duplicate();
    }


    @Override
    public ImageProperties getProperties() {
        return this.duplicateMask();
    }

    @Override
    public BlankMask addOffset(BoundingBox bounds) {
        translate(bounds);
        return this;
    }
    @Override
    public BlankMask addOffset(ImageProperties bounds) {
        translate(bounds.getOffsetX(), bounds.getOffsetY(), bounds.getOffsetZ());
        return this;
    }

    @Override
    public int getOffsetX() {
        return getxMin();
    }

    @Override
    public int getOffsetXY() {
        return xMin+yMin*getSizeX();
    }

    @Override
    public int getOffsetY() {
        return yMin;
    }

    @Override
    public int getOffsetZ() {
        return zMin;
    }
    public BlankMask resetOffset() {
        this.translateToOrigin();
        return this;
    }

    @Override
    public BlankMask setCalibration(ImageProperties properties) {
        this.scaleXY = properties.getScaleXY();
        this.scaleZ = properties.getScaleZ();
        return this;
    }

    @Override
    public BlankMask setCalibration(float scaleXY, float scaleZ) {
        this.scaleXY = scaleXY;
        this.scaleZ = scaleZ;
        return this;
    }
}
