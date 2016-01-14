package processing.mergeRegions;

import boa.gui.imageInteraction.IJImageDisplayer;
import static core.Processor.logger;
import dataStructure.objects.Object3D;
import dataStructure.objects.Voxel;
import ij.IJ;
import image.Image;
import image.ImageFloat;
import image.ImageInteger;
import image.ImageLabeller;
import image.ImageProperties;
import image.ImageShort;
import java.util.*;
import processing.neighborhood.EllipsoidalNeighborhood;

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
    ImageProperties imageProperties;
    double erode;
    boolean verbose;
    Image hessian;
    double fusionThreshold;
    
    public InterfaceCollection(RegionCollection regions, boolean verbose) {
        this.regions = regions;
        this.verbose=verbose;
        imageProperties = regions.labelMap;
        
        
    }
    
    
    protected void getInterfaces(Image intensityMap) {
        HashMap<RegionPair, Interface> interfaceMap = new HashMap<RegionPair, Interface>();
        ImageInteger inputLabels = regions.labelMap;
        Voxel n;
        int otherLabel;
        int[][] neigh = inputLabels.getSizeZ()>1 ? ImageLabeller.neigh3DHalf : ImageLabeller.neigh2DHalf;
        for (Region r : regions.regions.values()) {
            for (Voxel vox : r.voxels) {
                vox = vox.duplicate(); // to avoid having the same instance of voxel as in the region. //TODO why?
                for (int i = 0; i<neigh.length; ++i) {
                    n = new Voxel(vox.x+neigh[i][0], vox.y+neigh[i][1], vox.z+neigh[i][2]); // en avant pour les interactions avec d'autre spots / 0
                    if (inputLabels.contains(n.x, n.y, n.z)) { 
                        otherLabel = inputLabels.getPixelInt(n.x, n.y, n.z);   
                        if (otherLabel!=r.label) {
                            if (otherLabel!=0) addPair(interfaceMap, r.label, vox, otherLabel, n);
                            else addPairBackground(r, vox, n);
                        }
                    }
                    n = new Voxel(vox.x-neigh[i][0], vox.y-neigh[i][1], vox.z-neigh[i][2]);// eventuellement aussi en arriere juste pour interaction avec 0
                    if (inputLabels.contains(n.x, n.y, n.z)) {
                        otherLabel = inputLabels.getPixelInt(n.x, n.y, n.z);  
                        if (otherLabel==0) addPairBackground(r, vox, n);
                    }
                }
            }
            
            interfaces = new HashSet<Interface>(interfaceMap.values());
            if (intensityMap!=null) setVoxelIntensity(intensityMap);
        }
        if (verbose) logger.debug("Interface collection: nb of interfaces:"+interfaces.size());
    }
    
    public static ArrayList<Voxel> getInteface(Object3D o1, Object3D o2, ImageInteger labelImage) {
        EllipsoidalNeighborhood neigh = labelImage.getSizeZ()>1 ? new EllipsoidalNeighborhood(1, 1, true) : new EllipsoidalNeighborhood(1, true);
        Object3D min;
        int otherLabel;
        if (o1.getVoxels().size()<=o2.getVoxels().size()) {
            min=o1;
            otherLabel = o2.getLabel();
        } else {
            min = o2;
            otherLabel = o1.getLabel();
        }
        int xx, yy, zz;
        ArrayList<Voxel> inter = new ArrayList<Voxel>();
        for (Voxel v : min.getVoxels()) {
            for (int i = 0; i<neigh.dx.length; ++i) {
                xx=v.x+neigh.dx[i];
                yy=v.y+neigh.dy[i];
                zz=v.z+neigh.dz[i];
                if (labelImage.contains(xx, yy, zz) && labelImage.getPixelInt(xx, yy, zz)==otherLabel) inter.add(v);
            }
        }
        return inter;
    }
    
    public static void mergeAllConnected(RegionCollection regions) {
        ImageInteger inputLabels = regions.labelMap;
        int otherLabel;
        int[][] neigh = inputLabels.getSizeZ()>1 ? ImageLabeller.neigh3DHalf : ImageLabeller.neigh2DHalf;
        Voxel n;
        for (int z = 0; z<inputLabels.getSizeZ(); z++) {
            for (int y = 0; y<inputLabels.getSizeY(); y++) {
                for (int x = 0; x<inputLabels.getSizeX(); x++) {
                    int label = inputLabels.getPixelInt(x, y, z);
                    if (label==0) continue;
                    Region currentRegion = regions.get(label);
                    for (int i = 0; i<neigh.length; ++i) {
                        n = new Voxel(x+neigh[i][0], y+neigh[i][1], z+neigh[i][2]);
                        if (inputLabels.contains(n.x, n.y, n.z)) { 
                            otherLabel = inputLabels.getPixelInt(n.x, n.y, n.z);   
                            if (otherLabel>0 && otherLabel!=label) {
                                Region otherRegion = regions.get(otherLabel);
                                regions.fusion(currentRegion, otherRegion, null);
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
                logger.debug("region: {}" , r);
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
        if (label1==label2) throw new IllegalArgumentException("Cannot add interface of Region with itself: "+label1);
        RegionPair pair = new RegionPair(label1, label2);
        //if (regions.get(pair.r1).label != pair.r1) logger.debug("1region label not consistent: label: {} region: {}", pair.r1, regions.get(pair.r1));
        //if (regions.get(pair.r2).label != pair.r2) logger.debug("2region label not consistent: label: {} region: {}", pair.r2, regions.get(pair.r2));
        Interface inter = interfaces.get(pair);
        if (inter==null) {
            inter = new InterfaceVoxels(regions.get(pair.r1), regions.get(pair.r2), this); // enfonction de la methode...
            interfaces.put(pair, inter);
        }
        ((InterfaceVoxels)inter).addPair(vox1, vox2);
    }
    
    protected void addPairBackground(Region r, Voxel vox1, Voxel vox2) {
        if (r.interfaceBackground==null) {
            r.interfaceBackground = new InterfaceVoxels(regions.get(0), r, this); // enfonction de la methode...
        }
        ((InterfaceVoxels)r.interfaceBackground).addPair(vox1, vox2);
    }
    
    protected void setVoxelIntensity(Image image) {
        if (image!=null) {
            for (Interface i : interfaces) {
                InterfaceVoxels ivs = (InterfaceVoxels)i;
                for (Voxel v : ivs.r1Voxels) v.value=image.getPixel(v.x, v.y, v.z);
                for (Voxel v : ivs.r2Voxels) v.value=image.getPixel(v.x, v.y, v.z);
            }
        }
    }
    
    protected void drawInterfaces() {
        ImageShort im = new ImageShort("Iterfaces", imageProperties.getSizeX(), imageProperties.getSizeY(), imageProperties.getSizeZ());
        for (Interface i : interfaces) {
            for (Voxel v : ((InterfaceVoxels)i).r1Voxels) {
                im.setPixel(v.x, v.y, v.z, i.r2.label);
            }
            for (Voxel v : ((InterfaceVoxels)i).r2Voxels) {
                im.setPixel(v.x, v.y, v.z, i.r1.label);
            }
        }
        new IJImageDisplayer().showImage(im);
    }
    
    protected void drawInterfacesStrength() {
        ImageFloat im = new ImageFloat("Iterface Strength", imageProperties.getSizeX(), imageProperties.getSizeY(), imageProperties.getSizeZ());
        for (Interface i : interfaces) {
            if (i.r1.label==0) continue;
            for (Voxel v : ((InterfaceVoxels)i).r1Voxels) {
                im.setPixel(v.x, v.y, v.z, (float)i.strength);
            }
            for (Voxel v : ((InterfaceVoxels)i).r2Voxels) {
                im.setPixel(v.x, v.y, v.z, (float)i.strength);
            }
        }
        new IJImageDisplayer().showImage(im);
    }
    
    public boolean fusion(Interface i, boolean remove, double[] newCriterion) {
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
                    if (idx>=0) { // if interface is already present, simply add the new voxels to the interface
                        Interface existingInterface = i.r1.interfaces.get(idx);
                        interfaces.remove(existingInterface);
                        existingInterface.mergeInterface(otherInterface);
                        interfaces.add(existingInterface);
                        otherRegion.interfaces.remove(otherInterface);
                    } else { // if not add a new interface
                        otherInterface.switchRegion(i.r2, i.r1);
                        i.r1.interfaces.add(otherInterface);
                        interfaces.add(otherInterface);
                    }
                }
            }
        }
        regions.fusion(i.r1, i.r2, newCriterion);
        //if (change) logger.debug("fusioned region: {}", i.r1);
        return change;
    }
    
    protected void mergeSortHessianSpots(ImageFloat hess, double erode) {
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
    
    protected void mergeSortHessianBacteria(Image hess, double fusionThreshold) {
        this.sortMethod=4; //mean of hessian value @ interface / ascending order
        this.fusionMethod=2; // comparison of values inside region with value @ interface
        this.hessian=hess;
        this.fusionThreshold=fusionThreshold;
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
            ImageShort im = new ImageShort("Clusters", imageProperties.getSizeX(), imageProperties.getSizeY(), imageProperties.getSizeZ());
            int currentLabel = 1; 
            for (HashSet<Interface> c : clusters) {
                for (Interface i : c) {
                    for (Voxel v : i.r1.voxels) im.setPixel(v.x, v.y, v.z, currentLabel);
                    for (Voxel v : i.r2.voxels) im.setPixel(v.x, v.y, v.z, currentLabel);
                }
                ++currentLabel;
            }
            new IJImageDisplayer().showImage(im);
        }
        int clusterLabel = 0;
        int size = 0;
        for (HashSet<Interface> c : clusters) {
            if (verbose) logger.debug("mergeSort cluster: "+ ++clusterLabel);
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
        Iterator<Interface> it = interfaces.iterator(); // descending
        double[] mergedCriteria;
        while (it.hasNext()) {
            Interface i = it.next();
            if (verbose) logger.debug("Interface:"+i);
            mergedCriteria = i.checkFusionCriteria();
            if (mergedCriteria!=null) {
                it.remove();
                if (fusion(i, false, mergedCriteria)) it=interfaces.iterator();
            } else if (i.hasNoInteractants()) it.remove();
        }
    }
}
