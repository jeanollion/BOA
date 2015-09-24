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

import dataStructure.objects.Object3D;
import dataStructure.objects.Object3D;
import dataStructure.objects.Voxel3D;
import dataStructure.objects.Voxel3D;
import image.BlankMask;
import image.ImageInt;
import image.ImageInteger;
import image.ImageMask;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jollion
 */
public class ImageLabeller {
    int[][] labels;
    int sizeX;
    HashMap<Integer, Spot> spots;
    ImageMask mask;
    protected static final int[][] neigh3D = new int[][]{
            {1, 1, -1}, {0, 1, -1}, {-1, 1, -1}, {1, 0, -1}, {0, 0, -1}, {-1, 0, -1}, {1, -1, -1}, {0, 1, -1}, {-1, -1, -1},
            {1, -1, 0}, {0, -1, 0}, {-1, -1, 0}, {-1, 0, 0}
        };
    protected static final int[][] neigh2D = new int[][]{
            {1, -1, 0}, {0, -1, 0}, {-1, -1, 0}, {-1, 0, 0}
        };
    int[][] neigh;
    
    protected ImageLabeller(ImageMask mask) {
        this.mask=mask;
        ImageInt imLabels = new ImageInt("labels", mask);
        labels = imLabels.getPixelArray();
        sizeX = mask.getSizeX();
        spots = new HashMap<Integer, Spot>();
    }
    
    public static Object3D[] labelImage(ImageMask mask) {
        if (mask instanceof BlankMask) return new Object3D[]{new Object3D((BlankMask)mask, 1)};
        else {
            ImageLabeller il = new ImageLabeller(mask);
            if (mask.getSizeZ()>1) il.neigh=ImageLabeller.neigh3D;
            else il.neigh=ImageLabeller.neigh2D;
            il.labelSpots();
            return il.getObjects();
        }
    }
    
    protected Object3D[] getObjects() {
        Object3D[] res = new Object3D[spots.size()];
        int label = 0;
        for (Spot s : spots.values()) {
            ArrayList<Voxel3D> voxels = s.voxels;
            voxels = new ArrayList(new HashSet(voxels)); // revmove duplicate voxels because of neighbourhood overlap
            res[label++]= new Object3D(voxels, label);
        }
        return res;
    }
    
    private void labelSpots() {
        int currentLabel = 1;
        Spot currentSpot;
        Voxel3D v;
        int nextLabel;
        int xy;
        for (int z = 0; z < mask.getSizeZ(); ++z) {
            for (int y = 0; y < mask.getSizeY(); ++y) {
                for (int x = 0; x < sizeX; ++x) {
                    xy = x + y * sizeX;
                    if (mask.insideMask(xy, z)) {
                        currentSpot = null;
                        v = new Voxel3D(x, y, z);
                        for (int[] t : neigh) {
                            if (mask.contains(x + t[0], y + t[1], z + t[2])) {
                                nextLabel = labels[z + t[2]][xy + t[0] + t[1] * sizeX];
                                if (nextLabel != 0) {
                                    if (currentSpot == null) {
                                        currentSpot = spots.get(nextLabel);
                                        currentSpot.addVox(v);
                                    } else if (nextLabel != currentSpot.label) {
                                        currentSpot = currentSpot.fusion(spots.get(nextLabel));
                                        currentSpot.addVox(v);
                                    }
                                }
                            }
                        }
                        if (currentSpot == null) {
                            spots.put(currentLabel, new Spot(currentLabel++, v));
                        }
                    }
                }
            }
        }
    }
    
    private class Spot {

        ArrayList<Voxel3D> voxels;
        int label;

        public Spot(int label, Voxel3D v) {
            this.label = label;
            this.voxels = new ArrayList<Voxel3D>();
            voxels.add(v);
            labels[v.z][v.x+v.y*sizeX] = label;
        }

        public void addVox(Voxel3D v) {
            voxels.add(v);
            labels[v.z][v.x+v.y*sizeX] = label;
        }

        public void setLabel(int label) {
            this.label = label;
            for (Voxel3D v : voxels) {
                labels[v.z][v.x+v.y*sizeX] = label;
            }
        }

        public Spot fusion(Spot other) {
            if (other.label < label) {
                return other.fusion(this);
            }
            spots.remove(other.label);
            voxels.addAll(other.voxels);
            other.setLabel(label);
            return this;
        }

        public int getSize() {
            return voxels.size();
        }
    }
}
