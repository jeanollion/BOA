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
        neighborhoodZ=new HashMap<>();
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
    
    @Override public void setPixels(int x, int y, int z, Image image, ImageMask mask) {
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

    @Override public void setPixels(Voxel v, Image image, ImageMask mask) {
        setPixels(v.x, v.y, v.z, image, mask);
    }

    @Override public int getSize() {
        return currentNeighborhood.getSize();
    }

    @Override public float[] getPixelValues() {
        return currentNeighborhood.getPixelValues();
    }

    @Override public float[] getDistancesToCenter() {
        return currentNeighborhood.getDistancesToCenter();
    }

    @Override public int getValueCount() {
        return currentNeighborhood.getValueCount();
    }

    @Override public boolean is3D() {
        return currentNeighborhood.is3D();
    }

    @Override public float getMin(int x, int y, int z, Image image, float... outOfBoundValue) {
        return currentNeighborhood.getMin(x, y, z, image, outOfBoundValue);
    }

    @Override public float getMax(int x, int y, int z, Image image) {
        return currentNeighborhood.getMax(x, y, z, image);
    }

    @Override public boolean hasNonNullValue(int x, int y, int z, ImageMask mask, boolean outOfBoundIsNonNull) {
        return currentNeighborhood.hasNonNullValue(x, y, z, mask, outOfBoundIsNonNull);
    }

    @Override public boolean hasNullValue(int x, int y, int z, ImageMask mask, boolean outOfBoundIsNull) {
        return currentNeighborhood.hasNullValue(x, y, z, mask, outOfBoundIsNull);
    }
   
}
