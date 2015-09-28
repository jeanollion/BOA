package processing.mergeRegions;

import dataStructure.objects.Object3D;
import dataStructure.objects.Voxel;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageFloat;
import image.ImageInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import processing.EDT;

/**
 *
 **
 * /**
 * Copyright (C) 2012 Jean Ollion
 *
 *
 *
 * This file is part of tango
 *
 * tango is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Jean Ollion
 */

public class Region {
        ArrayList<Voxel> voxels;
        ArrayList<Interface> interfaces;
        Interface interfaceBackground;
        int label;
        RegionCollection col;
        double mergeCriterionValue;
        
        public Region(int label, Voxel vox, RegionCollection col) {
            this.label=label;
            this.voxels=new ArrayList<Voxel>();
            if (vox!=null) voxels.add(vox);
            this.col=col;
        }
        
        public void setLabel(int label) {
            col.regions.remove(this.label);
            this.label=label;
            setVoxelLabel(label);
            col.regions.put(label, this);
        }
        
        public void setVoxelLabel(int l) {
            for (Voxel v : voxels) col.labelMap.setPixel(v.x, v.y, v.getZ(), l);
        }

        public Region fusion(Region region, double newCriterion) {
            if (region.label<label) return region.fusion(this, newCriterion);
            if (col.verbose) ij.IJ.log("fusion:"+label+ "+"+region.label);
            region.setVoxelLabel(label);
            this.voxels.addAll(region.voxels);
            this.mergeCriterionValue=newCriterion;
            //if (this.interactants!=null) interactants.addAll(region.interactants);
            //spots.remove(region.label);
            return region;
        }
        
        /*public double getArea() {
            ImageInt inputLabels = col.labelMap;
            double area=0;
            for (Vox3D vox: voxels) {
                if (vox.x<cal.limX && (inputLabels.getPixelInt(vox.xy+1, vox.z))!=label) {
                    area+=cal.aXZ;
                }
                if (vox.x>0 && (inputLabels.getPixelInt(vox.xy-1, vox.z))!=label) {
                    area+=cal.aXZ;
                }
                if (vox.y<cal.limY && (inputLabels.getPixelInt(vox.xy+cal.sizeX, vox.z))!=label) {
                    area+=cal.aXZ;
                }
                if (vox.y>0 && (inputLabels.getPixelInt(vox.xy-cal.sizeX, vox.z))!=label) {
                    area+=cal.aXZ;
                }
                if (vox.z<cal.limZ && (inputLabels.getPixelInt(vox.xy, vox.z+1))!=label) {
                    area+=cal.aXY;
                }
                if (vox.z>0 && (inputLabels.getPixelInt(vox.xy, vox.z-1))!=label) {
                    area+=cal.aXY;
                }
            }
            return area;
        }*/
        
        public boolean hasNoInteractant() {
            return interfaces==null || interfaces.isEmpty() || (interfaces.size()==1 && interfaces.get(0).r1.label==0);
        }
        
        public Object3D toObject3D() {
            ArrayList<Voxel> al = new ArrayList<Voxel>(voxels.size());
            return new Object3D(al, label);
        }
        
        @Override 
        public boolean equals(Object o) {
            if (o instanceof Region) {
                return ((Region)o).label==label;
            } else if (o instanceof Integer) return label==(Integer)o;
            else return false;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 71 * hash + this.label;
            return hash;
        }
        
        @Override 
        public String toString() {
            return "Region:"+label;
        }
        
    public static double getRho(ArrayList<Voxel>[] voxIt, Image intensityMap, int nbCPUs) {
        return 1;
        /*BoundingBox bb= getBoundingBox(voxIt);
        if (bb==null) return 0;
        ImageInteger map = createSegImageMini(1, null, bb, voxIt );
        ImageFloat dm = EDT.transform(map, true, intensityMap.getScaleXY(), intensityMap.getScaleZ(), 1);
        
        int size = 0; for (HashSet<Vox3D> h : voxIt) size+=h.size();
        float[] distances = new float[size];
        float[] values = new float[distances.length];
        int idx=0;
        for (HashSet<Vox3D> h : voxIt) {
            for (Vox3D v : h) {
                distances[idx] = dm.getPixel(v.x-bb[0], v.y-bb[1], v.z-bb[2]);
                values[idx++] = intensityMap.getPixel(v.xy, v.z);
            }   
        }
        return SpearmanPairWiseCorrelationTest.computeRho(distances, values);*/
    }
    
    public static double getHessianMeanValue(ArrayList<Voxel>[] voxIt, ImageFloat hessian, double erode, int nbCPUs) {
        return 1;
        /*int[] bb= getBoundingBox(voxIt);
        if (bb==null) return 0;
        ImageInt map = createSegImageMini(1, null, bb, voxIt );
        ImageFloat dm = map.getDistanceMapInsideMask(nbCPUs);
        // set voxel value to distanceMap
        int size = 0;
        for (HashSet<Vox3D> h : voxIt) {
            size+=h.size();
            for (Vox3D v : h) v.value=dm.getPixel(v.x-bb[0], v.y-bb[1], v.z-bb[2]);
        }
        ArrayList<Vox3D> al;
        if (voxIt.length==1) al = new ArrayList(voxIt[0]);
        else {
            al = new ArrayList<Vox3D>(size);
            for (HashSet<Vox3D> h : voxIt) al.addAll(h);
        }
        Collections.sort(al);
        int idx = (int)((size-1) * erode + 0.5);
        
        double mean = 0;
        for (int i = idx; i<size; i++) {
            Vox3D v = al.get(i);
            mean+=hessian.pixels[v.z][v.xy];
        }
        if (idx<size) mean/=(double)(size-idx);
        //IJ.log("getHessMeanVal Sort: 0:"+al.get(0).value+" "+(size-1)+":"+al.get(size-1).value + "idx: "+idx+ " : "+al.get(idx).value + " mean value: "+mean);
        return mean;*/
    }
        
    protected static BoundingBox getBoundingBox(ArrayList<Voxel>[] voxIt) {
        BoundingBox b= new BoundingBox();
        for (ArrayList<Voxel> al : voxIt) {
            Object3D o = new Object3D(al, 1);
            b.expand(o.getBounds());
        }
        return b;
    }
        
    public static ImageInteger createSegImageMini(int val, int[] border, BoundingBox boundingBox, ArrayList<Voxel>[] voxIt) {
        if (boundingBox==null) boundingBox= getBoundingBox(voxIt);
        if (border==null) border = new int[3];
        int xm = boundingBox.getxMin() - border[0];
        if (xm < -border[0]) {
            xm = -border[0];
        }
        int ym = boundingBox.getyMin() - border[1];
        if (ym < -border[1]) {
            ym = -border[1];
        }
        int zm = boundingBox.getzMin() - border[2];
        if (zm < -border[2]) {
            zm = -border[2];
        }

        int w = boundingBox.getxMax() - xm + 1 + border[0];
        int h = boundingBox.getyMax() - ym + 1 + border[1];
        int d = boundingBox.getzMax() - zm + 1 + border[2];
        ImageByte miniLabelImage = new ImageByte("Object_" + val, w, h, d);
        int xx, yy, zz;
        for (ArrayList<Voxel> it : voxIt) {
            for (Voxel vox : it) {
                xx = vox.x - xm;
                yy = vox.y - ym;
                zz = vox.getZ() - zm;
                if (miniLabelImage.contains(xx, yy, zz)) {
                    miniLabelImage.setPixel(xx, yy, zz, val);
                }
            }
        }
        //miniLabelImage.show();
        // set the offsets
        miniLabelImage.addOffset(xm, ym, zm);
        //miniLabelImage.setScale((float) this.getResXY(), (float) this.getResZ(), this.getUnits());
        //miniLabelImage.show("obj:"+this);
        return miniLabelImage;
    }
    
}
