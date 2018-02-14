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
import boa.image.BlankMask;
import boa.image.BoundingBox;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.image.TypeConverter;
import boa.utils.HashMapGetCreate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

/**
 *
 * @author jollion
 */
public class RegionFactory {
    public static Region[] getRegions(ImageInteger labelImage, boolean ensureContinuousLabels) {
        HashMapGetCreate<Integer, Set<Voxel>> objects = new HashMapGetCreate<>(new HashMapGetCreate.SetFactory<>());
        int label;
        int sizeX = labelImage.getSizeX();
        for (int z = 0; z < labelImage.getSizeZ(); ++z) {
            for (int xy = 0; xy < labelImage.getSizeXY(); ++xy) {
                label = labelImage.getPixelInt(xy, z);
                if (label != 0) objects.getAndCreateIfNecessary(label).add(new Voxel(xy % sizeX, xy / sizeX, z));
            }
        }
        TreeMap<Integer, Set<Voxel>> tm = new TreeMap(objects);
        Region[] res = new Region[tm.size()];
        int i = 0;
        for (Entry<Integer, Set<Voxel>> e : tm.entrySet()) {
            res[i] = new Region(e.getValue(), ensureContinuousLabels?(i + 1):e.getKey(), labelImage.getSizeZ()==1, labelImage.getScaleXY(), labelImage.getScaleZ());
            ++i;
        }
        return res;
    }
  
    public static TreeMap<Integer, BoundingBox> getBounds(ImageInteger labelImage) {
        HashMapGetCreate<Integer, BoundingBox> bounds = new HashMapGetCreate<>(i->new BoundingBox());
        labelImage.getBoundingBox().translateToOrigin().loop((x, y, z)-> {
            int label = labelImage.getPixelInt(x, y, z);
            if (label>0) bounds.getAndCreateIfNecessary(label).expand(x, y, z);
        });
        return new TreeMap<>(bounds);
    }
    public static BoundingBox getBounds(ImageMask mask) {
        BoundingBox bounds = new BoundingBox();
        ImageMask.loop(mask, (x, y, z)->{bounds.expand(x, y, z);});
        return bounds;
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
    /**
     * 
     * @param mask
     * @return region where of voxels contained within {@param mask}
     * the returned region has the same landmask as the mask
     */
    public static Region getObjectImage(ImageMask mask) {
        if (mask instanceof BlankMask) return new Region((BlankMask)mask, 1, ((BlankMask) mask).getSizeZ()==1);
        BoundingBox bounds = getBounds(mask);
        if (bounds.equals(mask.getBoundingBox().translateToOrigin())) {
            if (mask instanceof ImageInteger) return new Region((ImageInteger)mask, 1, mask.getSizeZ()==1);
            else return new Region(TypeConverter.toImageInteger(mask, null), 1, mask.getSizeZ()==1);
        } else {
            ImageByte newMask = new ImageByte("", bounds.getImageProperties(mask.getScaleXY(), mask.getScaleZ()));
            newMask.getBoundingBox().loop((x, y, z)->{
                if (mask.insideMask(x, y, z)) newMask.setPixelWithOffset(x, y, z, 1); // bounds has for landmask mask
            });
            newMask.addOffset(mask.getBoundingBox()); 
            return new Region(newMask, 1, mask.getSizeZ()==1);
        }
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
