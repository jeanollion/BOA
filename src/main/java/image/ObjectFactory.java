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
import dataStructure.objects.Voxel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 *
 * @author jollion
 */
public class ObjectFactory {
    public static Object3D[] getObjectsVoxels(ImageInteger labelImage, boolean ensureContinuousLabels) {
        HashMap<Integer, ArrayList<Voxel>> objects = new HashMap<Integer, ArrayList<Voxel>>();
        int label;
        int sizeX = labelImage.getSizeX();
        for (int z = 0; z < labelImage.getSizeZ(); ++z) {
            for (int xy = 0; xy < labelImage.getSizeXY(); ++xy) {
                label = labelImage.getPixelInt(xy, z);
                if (label != 0) {
                    ArrayList<Voxel> al = objects.get(label);
                    if (al == null) {
                        al = new ArrayList<Voxel>();
                        objects.put(label, al);
                    }
                    al.add(new Voxel(xy % sizeX, xy / sizeX, z));
                }
            }
        }
        TreeMap<Integer, ArrayList<Voxel>> tm = new TreeMap(objects);
        Object3D[] res = new Object3D[tm.size()];
        int i = 0;
        for (Entry<Integer, ArrayList<Voxel>> e : tm.entrySet()) {
            res[i] = new Object3D(e.getValue(), ensureContinuousLabels?(i + 1):e.getKey(), labelImage.getScaleXY(), labelImage.getScaleZ());
            ++i;
        }
        return res;
    }
    
    
    public static TreeMap<Integer, BoundingBox> getBounds(ImageInteger labelImage) {
        HashMap<Integer, BoundingBox> bounds = new HashMap<Integer, BoundingBox>();
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
        return new TreeMap<Integer, BoundingBox>(bounds);
    }
    
    public static Object3D[] getObjectsImage(ImageInteger labelImage, boolean ensureContinuousLabels) {
        return getObjectsImage(labelImage, null, ensureContinuousLabels);
    }
    
    public static Object3D[] getObjectsImage(ImageInteger labelImage, TreeMap<Integer, BoundingBox> bounds,  boolean ensureContinuousLabels) {
        if (bounds==null) bounds = getBounds(labelImage);
        Object3D[] res = new Object3D[bounds.size()];
        int i = 0;
        
        for (Entry<Integer, BoundingBox> e : bounds.entrySet()) {
            ImageByte label = labelImage.cropLabel(e.getKey(), e.getValue());
            res[i] = new Object3D(label, ensureContinuousLabels?(i + 1):e.getKey());
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
                for (int z = b.zMin; z<=b.zMax; ++z) {
                    for (int y = b.yMin; y<=b.yMax; ++y) {
                        for (int x = b.xMin; x<=b.xMax; ++x) {
                            if (labelImage.getPixelInt(x, y, z)==currentLabel) labelImage.setPixel(x, y, z, newLabel);
                        }
                    }
                }
            }
            ++newLabel;
        }
    }
    
}
