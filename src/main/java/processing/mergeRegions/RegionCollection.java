package processing.mergeRegions;

import static core.Processor.logger;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.Voxel;
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
 * This file is part of 
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
    boolean verbose;
    int nCPUs;
    public RegionCollection(ImageInteger labelMap, Image intensityMap, boolean verbose, int nCPUs) {
        this.verbose=verbose;
        this.nCPUs=nCPUs;
        this.labelMap=labelMap;
        this.inputGray=intensityMap;
        createRegions();
    }
    
    public RegionCollection(ObjectPopulation pop, Image intensityMap, boolean verbose, int nCPUs) {
        this.verbose=verbose;
        this.nCPUs=nCPUs;
        this.labelMap=pop.getLabelImage();
        this.inputGray=intensityMap;
        regions=new HashMap<Integer, Region>();
        regions.put(0, new Region(0, null, this)); // background
        for (Object3D o : pop.getObjects()) {
            regions.put(o.getLabel(), new Region(o, this));
        }
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
    
    public void initInterfaces(Image intensityMap) {
        interfaces = new InterfaceCollection(this, verbose);
        interfaces.getInterfaces(intensityMap);
        interfaces.initializeRegionInterfaces();
        if (verbose) interfaces.drawInterfaces();
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
    
    public static ObjectPopulation mergeHessianBacteria(ObjectPopulation pop, Image intensities, Image hessian, double fusionThreshold) {
        RegionCollection r = new RegionCollection(pop, intensities, false, 1);
        r.verbose = logger.isDebugEnabled();
        r.initInterfaces(hessian);
        r.mergeSortHessianCondBacteria(hessian, fusionThreshold);
        return r.getObjectPopulation();
    }
    
    public ObjectPopulation getObjectPopulation() {
        
        ArrayList<Object3D> al = new ArrayList<Object3D>(regions.size());
        for (Region r : regions.values()) {
            if (r.label!=0) al.add(r.toObject3D()); // do not add background object!
        }
        ObjectPopulation res=  new ObjectPopulation(al, labelMap);
        res.relabel();
        return res;
    }
    
    public void mergeSortHessianCondSpots(double hessianRadius, boolean useScale, double erode) {
        if (interfaces==null) initInterfaces(inputGray);
        float scaleZ = labelMap.getScaleZ();
        float scaleXY = labelMap.getScaleXY();
        if (!useScale) inputGray.setCalibration(scaleXY, scaleXY);
        ImageFloat hess=ImageFeatures.getHessian(this.inputGray, hessianRadius, false)[0];
        if (!useScale) {
            hess.setCalibration(scaleXY, scaleZ);
            inputGray.setCalibration(scaleXY, scaleZ);
        }
        for (Region r : regions.values()) if (!r.interfaces.isEmpty()) r.mergeCriterionValue=new double[]{Region.getHessianMeanValue(new ArrayList[]{r.voxels}, hess, erode, nCPUs)};
        interfaces.mergeSortHessianSpots(hess, erode);
    }
    
    public void mergeSortHessianCondBacteria(Image hessian, double fustionThreshold) {
        if (interfaces==null) initInterfaces(hessian);
        for (Region r : regions.values()) {
            /*r.setVoxelValues(hessian);
            // voxel interface with background -> value set to NaN
            if (r.interfaceBackground!=null) {
                for (Voxel v : ((InterfaceVoxels)r.interfaceBackground).r2Voxels) {
                    int i = r.voxels.indexOf(v);
                    if (i>=0) r.voxels.get(i).value=Float.NaN;
                }
            }
            r.mergeCriterionValue=r.getSums();*/
        }
        interfaces.mergeSortHessianBacteria(hessian, fustionThreshold);
    }
    
    public void mergeSortCorrelation() {
        if (interfaces==null) initInterfaces(inputGray);
        for (Region r : regions.values()) if (!r.interfaces.isEmpty()) r.mergeCriterionValue = new double[]{Region.getRho(new ArrayList[]{r.voxels}, inputGray, nCPUs)};
        interfaces.mergeSortCorrelation();
    }
    
    public Region get(int label) {
        return regions.get(label);
    }
    
    protected void createRegions() {
        regions=new HashMap<Integer, Region>();
        regions.put(0, new Region(0, null, this)); // background
        for (int z = 0; z<labelMap.getSizeZ(); z++) {
            for (int y = 0; y<labelMap.getSizeY(); y++) {
                for (int x = 0; x<labelMap.getSizeX(); x++) {
                    int label = labelMap.getPixelInt(x,y, z);
                    if (label!=0) {
                        Region r = regions.get(label);
                        if (r==null) regions.put(label, new Region(label, new Voxel(x, y, z), this));
                        else r.voxels.add(new Voxel(x, y, z));
                    }
                }
            }
        }
        if (verbose) ij.IJ.log("Region collection: nb of spots:"+regions.size());
    }
    
    public void fusion(Region r1, Region r2, double[] newCriterion) {
        regions.remove(r1.fusion(r2, newCriterion).label);
    }
}
