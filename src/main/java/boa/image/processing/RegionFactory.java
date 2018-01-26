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
package boa.image.processing;

import boa.data_structure.Region;
import boa.data_structure.Voxel;
import boa.image.BoundingBox;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.utils.HashMapGetCreate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 *
 * @author jollion
 */
public class RegionFactory {
    public static Region[] getRegions(ImageInteger labelImage, boolean ensureContinuousLabels) {
        HashMapGetCreate<Integer, List<Voxel>> objects = new HashMapGetCreate<>(new HashMapGetCreate.ListFactory<>());
        int label;
        int sizeX = labelImage.getSizeX();
        for (int z = 0; z < labelImage.getSizeZ(); ++z) {
            for (int xy = 0; xy < labelImage.getSizeXY(); ++xy) {
                label = labelImage.getPixelInt(xy, z);
                if (label != 0) objects.getAndCreateIfNecessary(label).add(new Voxel(xy % sizeX, xy / sizeX, z));
            }
        }
        TreeMap<Integer, List<Voxel>> tm = new TreeMap(objects);
        Region[] res = new Region[tm.size()];
        int i = 0;
        for (Entry<Integer, List<Voxel>> e : tm.entrySet()) {
            res[i] = new Region(e.getValue(), ensureContinuousLabels?(i + 1):e.getKey(), labelImage.getSizeZ()==1, labelImage.getScaleXY(), labelImage.getScaleZ());
            ++i;
        }
        return res;
    }
  
    public static TreeMap<Integer, BoundingBox> getBounds(ImageInteger labelImage) {
        HashMap<Integer, BoundingBox> bounds = new HashMap<>();
        int label;
        int sizeX = labelImage.getSizeX();
        for (int z = 0; z<labelImage.getSizeZ(); ++z) {
            for (int xy = 0; xy < labelImage.getSizeXY(); ++xy) {
                label = labelImage.getPixelInt(xy, z);
                if (label != 0) {
                    BoundingBox b = bounds.get(label);
                    if (b == null) {
                        b = new BoundingBox(xy % sizeX, xy / sizeX, z);
                        bounds.put(label, b);
                    } else b.expand(xy % sizeX, xy / sizeX, z);
                    
                }
            }
        }
        return new TreeMap<>(bounds);
    }
    
    public static Region[] getObjectsImage(ImageInteger labelImage, boolean ensureContinuousLabels) {
        return getObjectsImage(labelImage, null, ensureContinuousLabels);
    }
    
    public static Region[] getObjectsImage(ImageInteger labelImage, TreeMap<Integer, BoundingBox> bounds,  boolean ensureContinuousLabels) {
        if (bounds==null) bounds = getBounds(labelImage);
        Region[] res = new Region[bounds.size()];
        int i = 0;
        
        for (Entry<Integer, BoundingBox> e : bounds.entrySet()) {
            ImageByte label = labelImage.cropLabel(e.getKey(), e.getValue());
            res[i] = new Region(label, ensureContinuousLabels?(i + 1):e.getKey(), labelImage.getSizeZ()==1);
            ++i;
        }
        return res;
    }
    
    public static void relabelImage(ImageInteger labelImage){
        relabelImage(labelImage, null);
    }
    
    public static void relabelImage(ImageInteger labelImage, TreeMap<Integer, BoundingBox> bounds) {
        if (bounds==null) bounds = getBounds(labelImage);
        int newLabel = 1;
        int currentLabel;
        for (Entry<Integer, BoundingBox> e : bounds.entrySet()) {
            currentLabel = e.getKey();
            if (currentLabel!=newLabel) {
                BoundingBox b= e.getValue();
                for (int z = b.getzMin(); z<=b.getzMax(); ++z) {
                    for (int y = b.getyMin(); y<=b.getyMax(); ++y) {
                        for (int x = b.getxMin(); x<=b.getxMax(); ++x) {
                            if (labelImage.getPixelInt(x, y, z)==currentLabel) labelImage.setPixel(x, y, z, newLabel);
                        }
                    }
                }
            }
            ++newLabel;
        }
    }
    public static List<Region> createSeedObjectsFromSeeds(List<int[]> seedsXYZ, boolean is2D, double scaleXY, double scaleZ) {
        List<Region> seedObjects = new ArrayList<>(seedsXYZ.size());
        int label = 0;
        for (int[] seed : seedsXYZ) seedObjects.add(new Region(new Voxel(seed), ++label, is2D, (float)scaleXY, (float)scaleZ));
        return seedObjects;
    }
    
}
