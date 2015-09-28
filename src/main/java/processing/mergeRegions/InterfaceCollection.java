package processing.mergeRegions;

import boa.gui.imageInteraction.IJImageDisplayer;
import dataStructure.objects.Voxel;
import dataStructure.objects.Voxel2D;
import dataStructure.objects.Voxel3D;
import ij.IJ;
import image.Image;
import image.ImageFloat;
import image.ImageInteger;
import image.ImageLabeller;
import image.ImageShort;
import java.util.*;

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

public class InterfaceCollection {
    int fusionMethod, sortMethod;
    RegionCollection regions;
    Set<Interface> interfaces;
    Image intensityMap;
    double erode;
    boolean verbose;
    ImageFloat hessian;
    
    public InterfaceCollection(RegionCollection regions, boolean verbose) {
        this.regions = regions;
        this.verbose=verbose;
        intensityMap = regions.inputGray;
        
        
    }
    
    private static Voxel createVoxel(Voxel vox, int dx, int dy, int dz) {
        if (vox instanceof Voxel3D) return new Voxel3D(vox.x+dx, vox.y+dy, vox.getZ()+dz);
        else return new Voxel2D(vox.x+dx, vox.y+dy);
    }
    
    protected void getInterfaces() {
        HashMap<RegionPair, Interface> interfaceMap = new HashMap<RegionPair, Interface>();
        ImageInteger inputLabels = regions.labelMap;
        Voxel n;
        int otherLabel;
        int[][] neigh = inputLabels.getSizeZ()>1 ? ImageLabeller.neigh3D : ImageLabeller.neigh2D;
        for (Region r : regions.regions.values()) {
            for (Voxel vox : r.voxels) {
                vox = vox.copy(); // to avoid having the same instance of voxel as in the region.
                for (int i = 0; i<neigh.length; ++i) {
                    n = createVoxel(vox, neigh[i][0], neigh[i][1], neigh[i][2]);// en avant pour les interactions avec d'autre spots / 0
                    if (inputLabels.contains(n.x, n.y, n.getZ())) { 
                        otherLabel = inputLabels.getPixelInt(n.x, n.y, n.getZ());   
                        if (otherLabel!=r.label) {
                            if (otherLabel!=0) addPair(interfaceMap, r.label, vox, otherLabel, n);
                            else addPairBackground(r, vox, n);
                        }
                    }
                    n = createVoxel(vox, -neigh[i][0], -neigh[i][1], -neigh[i][2]);// eventuellement aussi en arriere juste pour interaction avec 0
                    if (inputLabels.contains(n.x, n.y, n.getZ())) {
                        otherLabel = inputLabels.getPixelInt(n.x, n.y, n.getZ());  
                        if (otherLabel==0) addPairBackground(r, vox, n);
                    }
                }
            }
            
            interfaces = new HashSet<Interface>(interfaceMap.values());
            setVoxelIntensity();
        }
        if (verbose) ij.IJ.log("Interface collection: nb of interfaces:"+interfaces.size());
    }
    
    protected void getInterfacesLight() {
        HashMap<RegionPair, Interface> interfaceMap = new HashMap<RegionPair, Interface>();
        ImageInteger inputLabels = regions.labelMap;
        int otherLabel;
        int[][] neigh = inputLabels.getSizeZ()>1 ? ImageLabeller.neigh3D : ImageLabeller.neigh2D;
        Voxel n;
        for (Region r : regions.regions.values()) {
            for (Voxel vox : r.voxels) {
                    vox=vox.copy();
                    for (int i = 0; i<neigh.length; ++i) {
                        n = createVoxel(vox, neigh[i][0], neigh[i][1], neigh[i][2]);
                        if (inputLabels.contains(n.x, n.y, n.getZ())) { 
                            otherLabel = inputLabels.getPixelInt(n.x, n.y, n.getZ());   
                            if (otherLabel>0 && otherLabel!=r.label && !interfaceMap.containsKey(new RegionPair(r.label, otherLabel))) {
                                addPair(interfaceMap, r.label, vox, otherLabel, n);
                            }
                        }
                    }
            }
            interfaces = new HashSet<Interface>(interfaceMap.values());
            setVoxelIntensity();
        }
        if (verbose) ij.IJ.log("Interface collection: nb of interfaces:"+interfaces.size());
    }
    
    public static void mergeAllConnected(RegionCollection regions) {
        ImageInteger inputLabels = regions.labelMap;
        int otherLabel;
        int[][] neigh = inputLabels.getSizeZ()>1 ? ImageLabeller.neigh3D : ImageLabeller.neigh2D;
        Voxel n;
        for (int z = 0; z<inputLabels.getSizeZ(); z++) {
            for (int y = 0; y<inputLabels.getSizeY(); y++) {
                for (int x = 0; x<inputLabels.getSizeX(); x++) {
                    int label = inputLabels.getPixelInt(x, y, z);
                    if (label==0) continue;
                    Region currentRegion = regions.get(label);
                    for (int i = 0; i<neigh.length; ++i) {
                        n = new Voxel3D(x+neigh[i][0], y+neigh[i][1], z+neigh[i][2]);
                        if (inputLabels.contains(n.x, n.y, n.getZ())) { 
                            otherLabel = inputLabels.getPixelInt(n.x, n.y, n.getZ());   
                            if (otherLabel>0 && otherLabel!=label) {
                                Region otherRegion = regions.get(otherLabel);
                                regions.fusion(currentRegion, otherRegion, 0);
                                if (label>otherLabel) {
                                    currentRegion=otherRegion;
                                    label=otherLabel;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    protected void initializeRegionInterfaces() {
        for (Region r : regions.regions.values()) r.interfaces=new ArrayList<Interface>(5);
        for (Interface i : interfaces) {
            i.r1.interfaces.add(i);
            i.r2.interfaces.add(i);
        }
        if (verbose) {
            for (Region r : regions.regions.values()) {
                String in = "Region:"+r.label+" Interfaces: ";
                for (int i = 0; i<r.interfaces.size(); i++) in+=r.interfaces.get(i).getOther(r).label+", ";
                System.out.println(in);
            }
        }
    }
    
    /*public ImageStats getInterfaceHistogram() {
        int size=0;
        for (Interface i : interfaces) {
            InterfaceVoxSet ivs = (InterfaceVoxSet)i;
            size+=ivs.r1Voxels.size();
            size+=ivs.r2Voxels.size();
        }
        ImageFloat f = new ImageFloat("Interfaces", size, 1, 1);
        int offset = 0;
        for (Interface i : interfaces) {
            InterfaceVoxSet ivs = (InterfaceVoxSet)i;
            for (Vox3D v : ivs.r1Voxels) f.pixels[0][offset++]=v.value;
            for (Vox3D v : ivs.r2Voxels) f.pixels[0][offset++]=v.value;
        }
        f.getHistogram();
        return f.getImageStats(null);
    }*/
    
    /*protected void addPair(int label1, Vox3D vox1, int label2, Vox3D vox2) {
        RegionPair pair = new RegionPair(label1, label2);
        int idx = interfaces.indexOf(pair);
        if (idx<0) {
            interfaces.add(new Interface(regions.get(pair.r1), regions.get(pair.r2)));
        }
    }
    * 
    */
    
    protected void addPair(HashMap<RegionPair, Interface> interfaces, int label1, Voxel vox1, int label2, Voxel vox2) {
        RegionPair pair = new RegionPair(label1, label2);
        Interface inter = interfaces.get(pair);
        if (inter==null) {
            inter = new InterfaceVoxSet(regions.get(pair.r1), regions.get(pair.r2), this); // enfonction de la methode...
            interfaces.put(pair, inter);
        }
        ((InterfaceVoxSet)inter).addPair(vox1, vox2);
    }
    
    protected void addPairBackground(Region r, Voxel vox1, Voxel vox2) {
        if (r.interfaceBackground==null) {
            r.interfaceBackground = new InterfaceVoxSet(regions.get(0), r, this); // enfonction de la methode...
        }
        ((InterfaceVoxSet)r.interfaceBackground).addPair(vox1, vox2);
    }
    
    protected void setVoxelIntensity() {
        if (intensityMap==null) return;
        for (Interface i : interfaces) {
            InterfaceVoxSet ivs = (InterfaceVoxSet)i;
            for (Voxel v : ivs.r1Voxels) v.value=intensityMap.getPixel(v.x, v.y, v.getZ());
            for (Voxel v : ivs.r2Voxels) v.value=intensityMap.getPixel(v.x, v.y, v.getZ());
        }
    }
    
    protected void drawInterfaces() {
        ImageShort im = new ImageShort("Iterfaces", intensityMap.getSizeX(), intensityMap.getSizeY(), intensityMap.getSizeZ());
        for (Interface i : interfaces) {
            for (Voxel v : ((InterfaceVoxSet)i).r1Voxels) {
                im.setPixel(v.x, v.y, v.getZ(), i.r2.label);
            }
            for (Voxel v : ((InterfaceVoxSet)i).r2Voxels) {
                im.setPixel(v.x, v.y, v.getZ(), i.r1.label);
            }
        }
        new IJImageDisplayer().showImage(im);
    }
    
    protected void drawInterfacesStrength() {
        ImageFloat im = new ImageFloat("Iterface Strength", intensityMap.getSizeX(), intensityMap.getSizeY(), intensityMap.getSizeZ());
        for (Interface i : interfaces) {
            if (i.r1.label==0) continue;
            for (Voxel v : ((InterfaceVoxSet)i).r1Voxels) {
                im.setPixel(v.x, v.y, v.getZ(), (float)i.strength);
            }
            for (Voxel v : ((InterfaceVoxSet)i).r2Voxels) {
                im.setPixel(v.x, v.y, v.getZ(), (float)i.strength);
            }
        }
        new IJImageDisplayer().showImage(im);
    }
    
    public boolean fusion(Interface i, boolean remove, double newCriterion) {
        if (remove) interfaces.remove(i);
        if (i.r1.interfaces!=null) i.r1.interfaces.remove(i);
        boolean change = false;
        if (i.r2.interfaces!=null) {
            for (Interface otherInterface : i.r2.interfaces) { // appends interfaces of deleted region to new region
                if (!otherInterface.equals(i)) {
                    change=true;
                    interfaces.remove(otherInterface);
                    Region otherRegion = otherInterface.getOther(i.r2);
                    int idx = i.r1.interfaces.indexOf(new RegionPair(i.r1, otherRegion));
                    if (idx>=0) {
                        Interface existingInterface = i.r1.interfaces.get(idx);
                        interfaces.remove(existingInterface);
                        existingInterface.mergeInterface(otherInterface);
                        interfaces.add(existingInterface);
                    } else {
                        otherInterface.switchRegion(i.r2, i.r1);
                        i.r1.interfaces.add(otherInterface);
                        interfaces.add(otherInterface);
                    }
                }
            }
        }
        regions.fusion(i.r1, i.r2, newCriterion);
        return change;
    }
    
    protected void mergeSortHessian(ImageFloat hess, double erode) {
        this.sortMethod=0;
        this.fusionMethod=1;
        this.erode = erode;
        this.hessian=hess;
        mergeSortCluster();
    }
    
    protected void mergeSortCorrelation() {
        this.sortMethod=0;
        this.fusionMethod=0;
        mergeSortCluster();
    }
    
    protected void mergeSortCluster() {
        ArrayList<HashSet<Interface>> clusters = new ArrayList<HashSet<Interface>>();
        HashSet<Interface> currentCluster;
        for (Interface i : interfaces) {
            currentCluster = null;
            if (clusters.isEmpty()) {
                currentCluster = new HashSet<Interface>(i.r1.interfaces.size()+ i.r2.interfaces.size()-1);
                currentCluster.addAll(i.r1.interfaces);
                currentCluster.addAll(i.r2.interfaces);
                clusters.add(currentCluster);
            } else {
                Iterator<HashSet<Interface>> it = clusters.iterator();
                while(it.hasNext()) {
                    HashSet<Interface> cluster = it.next();
                    if (cluster.contains(i)) {
                        cluster.addAll(i.r1.interfaces);
                        cluster.addAll(i.r2.interfaces);
                        if (currentCluster!=null) { // fusion des clusters
                            currentCluster.addAll(cluster);
                            it.remove();
                        } else currentCluster=cluster;
                    }
                }
                if (currentCluster==null) {
                    currentCluster = new HashSet<Interface>(i.r1.interfaces.size()+ i.r2.interfaces.size()-1);
                    currentCluster.addAll(i.r1.interfaces);
                    currentCluster.addAll(i.r2.interfaces);
                    clusters.add(currentCluster);
                }
            }
        } 
        if (verbose) { // draw clusters
            ImageShort im = new ImageShort("Clusters", intensityMap.getSizeX(), intensityMap.getSizeZ(), intensityMap.getSizeZ());
            int currentLabel = 1; 
            for (HashSet<Interface> c : clusters) {
                for (Interface i : c) {
                    for (Voxel v : i.r1.voxels) im.setPixel(v.x, v.y, v.getZ(), currentLabel);
                    for (Voxel v : i.r2.voxels) im.setPixel(v.x, v.y, v.getZ(), currentLabel);
                }
                ++currentLabel;
            }
            new IJImageDisplayer().showImage(im);
        }
        int clusterLabel = 0;
        int size = 0;
        for (HashSet<Interface> c : clusters) {
            if (verbose) IJ.log("mergeSort cluster: "+ ++clusterLabel);
            interfaces = c;
            mergeSort();
            size+=interfaces.size();
        }
        interfaces = new HashSet<Interface>(size);
        for (HashSet<Interface> c : clusters) interfaces.addAll(c);
    }
    
    private void mergeSort() {
        for (Interface i : interfaces) i.computeStrength();
        if (verbose) drawInterfacesStrength();
        interfaces = new TreeSet<Interface>(interfaces);
        Iterator<Interface> it = interfaces.iterator(); // descending??
        Double mergedCriteria;
        while (it.hasNext()) {
            Interface i = it.next();
            if (verbose) System.out.println("Interface:"+i);
            mergedCriteria = i.checkFusionCriteria();
            if (mergedCriteria!=null) {
                it.remove();
                if (fusion(i, false, mergedCriteria)) it=interfaces.iterator();
            } else if (i.hasNoInteractants()) it.remove();
        }
    }
    
    /*
    protected void mergeSort() {
        //compute strength
        if (Core.debug) ij.IJ.log("Merge Regions: nb interactions:"+interfaces.size());
        for (Interface i : interfaces) i.computeStrength();
        Collections.sort(interfaces);
        int idx = 0;
        while (idx<interfaces.size()) {
            if (interfaces.get(idx).checkFusionCriteria()) {
                if (fusion(interfaces.remove(idx), false)) idx=0; //fusion > sort > RAZ
            } else if (interfaces.get(idx).hasNoInteractants()) interfaces.remove(idx);
            else idx++;
        }
    }
    * 
    */
}
