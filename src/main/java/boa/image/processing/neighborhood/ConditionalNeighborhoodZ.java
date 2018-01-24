/*
 * Copyright (C) 2016 jollion
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
package boa.image.processing.neighborhood;

import boa.data_structure.Voxel;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageMask;
import java.util.HashMap;

/**
 *
 * @author jollion
 */
public class ConditionalNeighborhoodZ implements Neighborhood {
    HashMap<Integer, Neighborhood>  neighborhoodZ;
    Neighborhood defaultNeighborhood;
    Neighborhood currentNeighborhood;
    
    public ConditionalNeighborhoodZ(Neighborhood  defaultNeighborhood) {
        this.defaultNeighborhood=defaultNeighborhood;
        neighborhoodZ=new HashMap<Integer, Neighborhood>();
    }
    public ConditionalNeighborhoodZ setNeighborhood(int z, Neighborhood n) {
        neighborhoodZ.put(z, n);
        return this;
    }
    
    public Neighborhood getNeighborhood(int z) {
        Neighborhood res = neighborhoodZ.get(z);
        if (res==null) return defaultNeighborhood;
        else return res;
    }
    
    public void setPixels(int x, int y, int z, Image image, ImageMask mask) {
        currentNeighborhood = getNeighborhood(z);
        currentNeighborhood.setPixels(x, y, z, image, mask);
    }
    
    @Override public BoundingBox getBoundingBox() {
        BoundingBox res=null;
        for (Neighborhood n : neighborhoodZ.values()) {
            if (res == null) res = n.getBoundingBox();
            else res.expand(n.getBoundingBox());
        }
        return res;
    }

    public void setPixels(Voxel v, Image image, ImageMask mask) {
        setPixels(v.x, v.y, v.z, image, mask);
    }

    public int getSize() {
        return currentNeighborhood.getSize();
    }

    public float[] getPixelValues() {
        return currentNeighborhood.getPixelValues();
    }

    public float[] getDistancesToCenter() {
        return currentNeighborhood.getDistancesToCenter();
    }

    public int getValueCount() {
        return currentNeighborhood.getValueCount();
    }

    public boolean is3D() {
        return currentNeighborhood.is3D();
    }

    public float getMin(int x, int y, int z, Image image, float... outOfBoundValue) {
        return currentNeighborhood.getMin(x, y, z, image, outOfBoundValue);
    }

    public float getMax(int x, int y, int z, Image image) {
        return currentNeighborhood.getMax(x, y, z, image);
    }

    public boolean hasNonNullValue(int x, int y, int z, Image image, boolean outOfBoundIsNonNull) {
        return currentNeighborhood.hasNonNullValue(x, y, z, image, outOfBoundIsNonNull);
    }

    public boolean hasNullValue(int x, int y, int z, Image image, boolean outOfBoundIsNull) {
        return currentNeighborhood.hasNullValue(x, y, z, image, outOfBoundIsNull);
    }
   
}
