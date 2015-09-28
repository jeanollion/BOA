package processing.mergeRegions;

import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.Voxel2D;
import dataStructure.objects.Voxel3D;
import image.BoundingBox;
import image.Image;
import image.ImageFloat;
import image.ImageInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;
import processing.ImageFeatures;

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

public class RegionCollection {
    HashMap<Integer, Region> regions;
    ImageInteger labelMap;
    Image inputGray;
    InterfaceCollection interfaces;
    boolean setInterfaces;
    boolean verbose;
    int nCPUs;
    public RegionCollection(ImageInteger labelMap, Image intensityMap, boolean verbose, int nCPUs) {
        this.verbose=verbose;
        this.nCPUs=nCPUs;
        this.labelMap=labelMap;
        this.inputGray=intensityMap;
        if (inputGray==null) this.setInterfaces=false;
        createRegions();
    }
    
    public void shiftIndicies(boolean updateRegionMap) {
        TreeMap<Integer, Region> sortedReg = new TreeMap<Integer, Region>(regions);
        HashMap<Integer, Region> newRegions = null;
        if (updateRegionMap) newRegions = new HashMap<Integer, Region> (regions.size());
        int curIdx=1;
        for (Region r : sortedReg.values()) {
            if (r.label==0) {
                if (updateRegionMap) newRegions.put(0, r);
                continue;
            }
            r.setVoxelLabel(curIdx);
            r.label=curIdx;
            if (updateRegionMap) newRegions.put(curIdx, r);
            curIdx++;
        }
        if (updateRegionMap) regions=newRegions;
    }
    
    public void initInterfaces() {
        interfaces = new InterfaceCollection(this, verbose);
        interfaces.getInterfaces();
        interfaces.initializeRegionInterfaces();
        if (verbose) interfaces.drawInterfaces();
    }
    
    public void initInterfacesLight() {
        interfaces = new InterfaceCollection(this, verbose);
        interfaces.getInterfacesLight();
        interfaces.initializeRegionInterfaces();
    }
    
    public static ObjectPopulation mergeAllConnected(ImageInteger labelMap) {
        RegionCollection r = new RegionCollection(labelMap, null, false, 1);
        InterfaceCollection.mergeAllConnected(r);
        return r.getObjectPopulation();
    }
    
    public static Object3D mergeAll(ImageInteger labelMap) {
        BoundingBox b = new BoundingBox();
        for (int z = 0; z<labelMap.getSizeZ(); ++z) {
            for (int y = 0; y<labelMap.getSizeY(); ++y) {
                for (int x = 0; x<labelMap.getSizeX(); ++x) {
                    if (labelMap.insideMask(x, y, z)) {
                        labelMap.setPixel(x, y, z, 1);
                        b.expand(x, y, z);
                    }
                }
            }
        } 
        if (b.getSizeX()==labelMap.getSizeX() && b.getSizeY()==labelMap.getSizeY() && b.getSizeZ()==labelMap.getSizeZ()) return new Object3D(labelMap, 1);
        else {
            ImageInteger crop = labelMap.crop(b).addOffset(labelMap);
            return new Object3D(crop, 1);
        }
    }
    
    public ObjectPopulation getObjectPopulation() {
        
        ArrayList<Object3D> al = new ArrayList<Object3D>(regions.size());
        for (Region r : regions.values()) al.add(r.toObject3D());
        return new ObjectPopulation(al, labelMap);
    }
    
    public void mergeSortHessianCond(double hessianRadius, boolean useScale, double erode) {
        if (interfaces==null) initInterfaces();
        float scaleZ = labelMap.getScaleZ();
        float scaleXY = labelMap.getScaleXY();
        if (!useScale) inputGray.setCalibration(scaleXY, scaleXY);
        ImageFloat hess=ImageFeatures.getHessian(this.inputGray, hessianRadius)[0];
        if (!useScale) {
            hess.setCalibration(scaleXY, scaleZ);
            inputGray.setCalibration(scaleXY, scaleZ);
        }
        for (Region r : regions.values()) if (!r.interfaces.isEmpty()) r.mergeCriterionValue=Region.getHessianMeanValue(new ArrayList[]{r.voxels}, hess, erode, nCPUs);
        interfaces.mergeSortHessian(hess, erode);
    }
    
    public void mergeSortCorrelation() {
        if (interfaces==null) initInterfaces();
        for (Region r : regions.values()) if (!r.interfaces.isEmpty()) r.mergeCriterionValue = Region.getRho(new ArrayList[]{r.voxels}, inputGray, nCPUs);
        interfaces.mergeSortCorrelation();
    }
    
    public Region get(int label) {
        return regions.get(label);
    }
    
    protected void createRegions() {
        regions=new HashMap<Integer, Region>();
        regions.put(0, new Region(0, null, this)); // background
        if (labelMap.getSizeZ()>1) {
            for (int z = 0; z<labelMap.getSizeZ(); z++) {
                for (int y = 0; y<labelMap.getSizeY(); y++) {
                    for (int x = 0; x<labelMap.getSizeX(); x++) {
                        int label = labelMap.getPixelInt(x,y, z);
                        if (label!=0) {
                            Region r = regions.get(label);
                            if (r==null) regions.put(label, new Region(label, new Voxel3D(x, y, z), this));
                            else r.voxels.add(new Voxel3D(x, y, z));
                        }
                    }
                }
            }
        } else {
            // 2D case 
            for (int y = 0; y<labelMap.getSizeY(); y++) {
                for (int x = 0; x<labelMap.getSizeX(); x++) {
                    int label = labelMap.getPixelInt(x,y, 0);
                    if (label!=0) {
                        Region r = regions.get(label);
                        if (r==null) regions.put(label, new Region(label, new Voxel2D(x, y), this));
                        else r.voxels.add(new Voxel2D(x, y));
                    }
                }
            }
        }
        if (verbose) ij.IJ.log("Region collection: nb of spots:"+regions.size());
    }
    
    public void fusion(Region r1, Region r2, double newCriterion) {
        regions.remove(r1.fusion(r2, newCriterion).label);
    }
}
