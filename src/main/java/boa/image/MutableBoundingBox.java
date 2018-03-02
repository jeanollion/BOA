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
package boa.image;

import boa.data_structure.Voxel;

/**
 *
 * @author jollion
 */

public class MutableBoundingBox extends SimpleBoundingBox<MutableBoundingBox>  {

    public MutableBoundingBox(){
        super();
    }
    
    public MutableBoundingBox(int xMin, int xMax, int yMin, int yMax, int zMin, int zMax) {
        super(xMin, xMax, yMin, yMax, zMin, zMax);
    }
    public MutableBoundingBox(int x, int y, int z) {
        super(x, x, y, y, z, z);
    }
    public MutableBoundingBox(BoundingBox other) {
        super(other);
    }
    
    public MutableBoundingBox setxMin(int xMin) {
        this.xMin = xMin;
        return this;
    }

    public MutableBoundingBox setxMax(int xMax) {
        this.xMax = xMax;
        return this;
    }

    public MutableBoundingBox setyMin(int yMin) {
        this.yMin = yMin;
        return this;
    }

    public MutableBoundingBox setyMax(int yMax) {
        this.yMax = yMax;
        return this;
    }

    public MutableBoundingBox setzMin(int zMin) {
        this.zMin = zMin;
        return this;
    }

    public MutableBoundingBox setzMax(int zMax) {
        this.zMax = zMax;
        return this;
    }
    
    
    /**
     * Modify the bounds so that is contains the {@param x} coordinate
     * @param x coordinate in the X-Axis
     */
    public MutableBoundingBox expandX(int x) {
        if (x < xMin) {
            xMin = x;
        } 
        if (x > xMax) {
            xMax = x;
        }
        return this;
    }
    /**
     * Modify the bounds so that is contains the {@param y} coordinate
     * @param y coordinate in the X-Axis
     */
    public MutableBoundingBox expandY(int y) {
        if (y < yMin) {
            yMin = y;
        } 
        if (y > yMax) {
            yMax = y;
        }
        return this;
    }
    /**
     * Modify the bounds so that is contains the {@param z} coordinate
     * @param z coordinate in the X-Axis
     */
    public MutableBoundingBox expandZ(int z) {
        if (z < zMin) {
            zMin = z;
        } 
        if (z > zMax) {
            zMax = z;
        }
        return this;
    }
    /**
     * Copies zMin & zMax from {@param bb}
     * @param bb
     * @return this object modified
     */
    public MutableBoundingBox copyZ(BoundingBox bb) {
        this.zMin = bb.zMin();
        this.zMax = bb.zMax();
        return this;
    }
    public MutableBoundingBox contractX(int xm, int xM) {
        if (xm > xMin) {
            xMin = xm;
        } 
        if (xM < xMax) {
            xMax = xM;
        }
        return this;
    }
    public MutableBoundingBox contractY(int ym, int yM) {
        if (ym > yMin) {
            yMin = ym;
        } 
        if (yM < yMax) {
            yMax = yM;
        }
        return this;
    }
    public MutableBoundingBox contractZ(int zm, int zM) {
        if (zm > zMin) {
            zMin = zm;
        } 
        if (zM < zMax) {
            zMax = zM;
        }
        return this;
    }
    
    public MutableBoundingBox expand(int x, int y, int z) {
        expandX(x);
        expandY(y);
        expandZ(z);
        return this;
    }
    
    public MutableBoundingBox expand(Voxel v) {
        expandX(v.x);
        expandY(v.y);
        expandZ(v.z);
        return this;
    }
    
    public MutableBoundingBox expand(BoundingBox other) {
        expandX(other.xMin());
        expandX(other.xMax());
        expandY(other.yMin());
        expandY(other.yMax());
        expandZ(other.zMin());
        expandZ(other.zMax());
        return this;
    }
    public MutableBoundingBox contract(BoundingBox other) {
        contractX(other.xMin(), other.xMax());
        contractY(other.yMin(), other.yMax());
        contractZ(other.zMin(), other.zMax());
        return this;
    }
    
    public MutableBoundingBox center(BoundingBox other) {
        int deltaX = (int)(other.xMean() - this.xMean() + 0.5);
        int deltaY = (int)(other.yMean() - this.yMean() + 0.5);
        int deltaZ = (int)(other.zMean() - this.zMean() + 0.5);
        return translate(deltaX, deltaY, deltaZ);
    }
    
    /**
     * add {@param border} value in each direction and both ways
     * @param border value of the border
     */
    public MutableBoundingBox addBorder(int border, boolean addInZDirection) {
        xMin-=border;
        xMax+=border;
        yMin-=border;
        yMax+=border;
        if (addInZDirection) {
            zMin-=border;
            zMax+=border;
        }
        return this;
    }
    /**
     * adds a border of 1 pixel in each directions and both ways
     */
    public MutableBoundingBox addBorder() {
        xMin--;
        xMax++;
        yMin--;
        yMax++;
        zMin--;
        zMax++;
        return this;
    }
    /**
     * ensures the bounds are included in the bounds of the {@param properties} object.
     * @param properties 
     * @return  current modified boundingbox object
     */
    public MutableBoundingBox trim(MutableBoundingBox properties) {
        if (xMin<properties.xMin) xMin=properties.xMin;
        if (yMin<properties.yMin) yMin=properties.yMin;
        if (zMin<properties.zMin) zMin=properties.zMin;
        if (xMax>properties.xMax) xMax=properties.xMax;
        if (yMax>properties.yMax) yMax=properties.yMax;
        if (zMax>properties.zMax) zMax=properties.zMax;
        return this;
    }
    
    public MutableBoundingBox extend(BoundingBox extent) {
        xMax += extent.xMax();
        xMin += extent.xMin();
        yMax += extent.yMax();
        yMin += extent.yMin();
        zMax += extent.zMax();
        zMin += extent.zMin();
        return this;
    }
    
    
}