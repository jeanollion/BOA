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
package image;

/**
 *
 * @author jollion
 */
public class BoundingBox {

    
    int xMin, xMax, yMin, yMax, zMin, zMax, count;
    public BoundingBox(){}
    public BoundingBox(int xMin, int xMax, int yMin, int yMax, int zMin, int zMax) {
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
        this.zMin = zMin;
        this.zMax = zMax;
    }
    /**
     * creates a new bounding box containing the voxel of coordinates {@param x}, {@param y}, {@param z}
     * @param x coordinate in the X-Axis
     * @param y coordinate in the Y-Axis
     * @param z coordinate in the Z-Axis
     */
    public BoundingBox(int x, int y, int z) {
        xMin=x;
        xMax=x;
        yMin=y;
        yMax=y;
        zMin=z;
        zMax=z;
        count=1;
    }
    /**
     * Modify the bounds so that is contains the {@param x} coordinate
     * @param x coordinate in the X-Axis
     */
    public void expandX(int x) {
        if (x < xMin) {
            xMin = x;
        } else if (x > xMax) {
            xMax = x;
        }
    }
    /**
     * Modify the bounds so that is contains the {@param y} coordinate
     * @param y coordinate in the X-Axis
     */
    public void expandY(int y) {
        if (y < yMin) {
            yMin = y;
        } else if (y > yMax) {
            yMax = y;
        }
    }
    /**
     * Modify the bounds so that is contains the {@param z} coordinate
     * @param z coordinate in the X-Axis
     */
    public void expandZ(int z) {
        if (z < zMin) {
            zMin = z;
        } else if (z > zMax) {
            zMax = z;
        }
    }
    public void addToCounter() {
        count++;
    }
    public void setCounter(int count) {
        this.count = count;
    }
    /**
     * add {@param border} value in each direction and both ways
     * @param border value of the border
     */
    public void addBorder(int border) {
        xMin-=border;
        xMax+=border;
        yMin-=border;
        yMax+=border;
        zMin-=border;
        zMax+=border;
    }
    /**
     * adds a border of 1 pixel in each directions and both ways
     */
    public void addBorder() {
        xMin--;
        xMax++;
        yMin--;
        yMax++;
        zMin--;
        zMax++;
    }
    /**
     * ensures the bounds are included in the bounds of the {@param properties} object
     * @param properties 
     */
    public void trimToImage(ImageProperties properties) {
        if (xMin<0) xMin=0;
        if (yMin<0) yMin=0;
        if (zMin<0) zMin=0;
        if (xMax>=properties.getSizeX()) xMax=properties.getSizeX()-1;
        if (yMax>=properties.getSizeY()) yMax=properties.getSizeY()-1;
        if (zMax>=properties.getSizeZ()) zMax=properties.getSizeZ()-1;
    }
    
    public boolean sameBounds(ImageProperties properties) {
        return xMin==0 && yMin==0 && zMin==0 && xMax==(properties.getSizeX()-1) && yMax==(properties.getSizeY()-1) && zMax==(properties.getSizeZ()-1);
    }
    
    public int getxMin() {
        return xMin;
    }

    public int getxMax() {
        return xMax;
    }

    public int getyMin() {
        return yMin;
    }

    public int getyMax() {
        return yMax;
    }

    public int getzMin() {
        return zMin;
    }

    public int getzMax() {
        return zMax;
    }

    public int getCount() {
        return count;
    }
    
    public int getXLength() {
        return xMax-xMin+1;
    }
    
    public int getYLength() {
        return yMax-yMin+1;
    }
    
    public int getZLength() {
        return zMax-zMin+1;
    }
}
