/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.image;

import boa.utils.JSONSerializable;
import org.json.simple.JSONArray;

/**
 *
 * @author Jean Ollion
 */
public class SimpleBoundingBox<T extends SimpleBoundingBox<T>> implements BoundingBox<T>,  JSONSerializable {
    int xMin, xMax, yMin, yMax, zMin, zMax;
    public SimpleBoundingBox(){
        xMin=Integer.MAX_VALUE;
        yMin=Integer.MAX_VALUE;
        zMin=Integer.MAX_VALUE;
        xMax=Integer.MIN_VALUE;
        yMax=Integer.MIN_VALUE;
        zMax=Integer.MIN_VALUE;
    }
    
    public SimpleBoundingBox(int xMin, int xMax, int yMin, int yMax, int zMin, int zMax) {
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
        this.zMin = zMin;
        this.zMax = zMax;
    }
    public SimpleBoundingBox(BoundingBox other) {
        this(other.xMin(), other.xMax(), other.yMin(), other.yMax(), other.zMin(), other.zMax());
    }
    
    @Override public int xMin() { return xMin; }
    @Override public int xMax() { return xMax; }
    @Override public int yMin() { return yMin; }
    @Override public int yMax() { return yMax; }
    @Override public int zMin() { return zMin; }
    @Override public int zMax() { return zMax; }
    @Override public int sizeX() { return xMax-xMin+1; }
    @Override public int sizeY() { return yMax-yMin+1; }
    @Override public int sizeZ() { return zMax-zMin+1; }
    @Override 
    public int getIntPosition(int dim) {
        switch(dim) {
            case 0:
                return xMin;
            case 1:
                return yMin;
            case 2:
                return zMin;
            default:
                return 0;
        }
    }
    
    @Override public boolean sameBounds(BoundingBox boundingBox) {
        return xMin==boundingBox.xMin() && yMin==boundingBox.yMin() && zMin==boundingBox.zMin() && xMax==boundingBox.xMax() && yMax==boundingBox.yMax() && zMax==boundingBox.zMax();
    }
    @Override public boolean sameDimensions(BoundingBox bounds) {
        return sizeX() == bounds.sizeX() && sizeY() == bounds.sizeY() && sizeZ() == bounds.sizeZ();
    }
    /**
     * Translate the bounding box in the 3 axes
     * @param dX translation in the X-Axis in pixels
     * @param dY translation in the Y-Axis in pixels
     * @param dZ translation in the X-Axis in pixels
     * @return the same instance of bounding box, after the translation operation
     */
    public T translate(int dX, int dY, int dZ) {
        xMin+=dX; xMax+=dX; yMin+=dY; yMax+=dY; zMin+=dZ; zMax+=dZ;
        return (T)this;
    }
    @Override public T translate(Offset offset) {
        return translate(offset.xMin(), offset.yMin(), offset.zMin());
    }
    
    @Override public T resetOffset() {
        return translate(-xMin, -yMin, -zMin);
    }
    
    @Override public T reverseOffset() {
        return translate(-2*xMin, -2*yMin, -2*zMin);
    }
    
    public T duplicate() {
        return (T)new MutableBoundingBox(xMin, xMax, yMin, yMax, zMin, zMax);
    }
    
    public int getSizeXY() {
        return (xMax-xMin+1) * (yMax-yMin+1);
    }
    
    public int getSizeXYZ() {
        return (xMax-xMin+1) * (yMax-yMin+1) * (zMax-zMin+1);
    }
    
    @Override public double xMean() {
        return (xMin+xMax)/2d;
    }
    
    @Override public double yMean() {
        return (yMin+yMax)/2d;
    }
    
    @Override public double zMean() {
        if (sizeZ()<=1) return zMin;
        return (zMin+zMax)/2d;
    }
    public boolean isEmpty() {
        return sizeX()<=0 || sizeY()<=0 || sizeZ()<=0;
    }
    @Override
    public boolean contains(int x, int y, int z) {
        return 0<=x && xMax-xMin>=x && 0<=y && yMax-yMin>=y && 0<=z && zMax-zMin>=z;
    }
    
    @Override
    public boolean containsWithOffset(int x, int y, int z) {
        return xMin<=x && xMax>=x && yMin<=y && yMax>=y && zMin<=z && zMax>=z;
    }
    
    public boolean isOffsetNull() {
        return xMin==0 && yMin==0 && zMin==0;
    }
    
    public BlankMask getBlankMask(float scaleXY, float scaleZ) {
        return new BlankMask(this, scaleXY, scaleZ);
    }
    
    public BlankMask getBlankMask() {
        return new BlankMask(this, 1, 1);
    }
    
    public SimpleOffset getOffset() {
        return new SimpleOffset(xMin, yMin, zMin);
    }
    
    // JSON Serializable Impl
    
    @Override
    public void initFromJSONEntry(Object json) {
        JSONArray bds =  (JSONArray)json;
        xMin = (((Number)bds.get(0)).intValue());
        xMax = (((Number)bds.get(1)).intValue());
        yMin = (((Number)bds.get(2)).intValue());
        yMax = (((Number)bds.get(3)).intValue());
        if (bds.size()>=6) {
            zMin = (((Number)bds.get(4)).intValue());
            zMax = (((Number)bds.get(5)).intValue());
        } else {
            zMin = 0;
            zMax = 0;
        }
    }
    @Override
    public JSONArray toJSONEntry() {
        JSONArray bds =  new JSONArray();
        bds.add(xMin);
        bds.add(xMax);
        bds.add(yMin);
        bds.add(yMax);
        if (sizeZ()>1 || zMin()!=0) {
            bds.add(zMin());
            bds.add(zMax());
        }
        return bds;
    }
    
    // systems method
    /*@Override
    public boolean equals(Object other) {
        if (other instanceof MutableBoundingBox) {
            MutableBoundingBox otherBB = (MutableBoundingBox) other;
            return xMin==otherBB.xMin() && yMin==otherBB.yMin() && zMin==otherBB.zMin() && xMax==otherBB.xMax() && yMax==otherBB.yMax() && zMax==otherBB.zMax();
        } else if (other instanceof ImageProperties) {
            return this.sameBounds((ImageProperties)other);
        } else return false;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 29 * hash + this.xMin;
        hash = 29 * hash + this.xMax;
        hash = 29 * hash + this.yMin;
        hash = 29 * hash + this.yMax;
        hash = 29 * hash + this.zMin;
        hash = 29 * hash + this.zMax;
        return hash;
    }*/
    
    @Override
    public String toString() {
        return "[["+xMin+";"+xMax+"]x["+yMin+";"+yMax+"]x["+zMin+";"+zMax+"]]";
    }
    public String toStringOffset() {
        return "["+xMin+";"+yMin+";"+zMin+"]";
    }

}
