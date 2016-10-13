/*
 * Copyright (C) 2016 jollion
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
package utils.clustering;

import boa.gui.imageInteraction.IJImageDisplayer;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.Voxel;
import image.Image;
import image.ImageFloat;
import image.ImageInteger;
import image.ImageLabeller;
import image.ImageShort;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import processing.neighborhood.EllipsoidalNeighborhood;

/**
 *
 * @author jollion
 */
public class Object3DCluster<I extends InterfaceObject3D<I>> extends ClusterCollection<Object3D, I> {
    ObjectPopulation population;
    public final static Comparator<Object3D> object3DComparator = new Comparator<Object3D>() {
        public int compare(Object3D o1, Object3D o2) {
            return Integer.compare(o1.getLabel(), o2.getLabel());
        };
    };
    
    public Object3DCluster(ObjectPopulation population, boolean background, boolean lowConnectivity, InterfaceFactory<Object3D, I> interfaceFactory) {
        super(population.getObjects(), object3DComparator, interfaceFactory);
        this.population=population;
        setInterfaces(background, lowConnectivity);
    }
    
    
    protected void setInterfaces(boolean background, boolean lowConnectivity) {
        Map<Integer, Object3D> objects = new HashMap<Integer, Object3D>();
        for (Object3D o : population.getObjects()) objects.put(o.getLabel(), o);
        if (background) objects.put(0, new Object3D(new ArrayList<Voxel>(), 0, population.getImageProperties().getBoundingBox(), population.getImageProperties().getScaleXY(), population.getImageProperties().getScaleZ()));
        ImageInteger inputLabels = population.getLabelMap();
        Voxel n;
        int otherLabel;
        int[][] neigh = inputLabels.getSizeZ()>1 ? (lowConnectivity ? ImageLabeller.neigh3DLowHalf : ImageLabeller.neigh3DHalf) : (lowConnectivity ? ImageLabeller.neigh2D4Half : ImageLabeller.neigh2D8Half);
        for (Object3D o : population.getObjects()) {
            for (Voxel vox : o.getVoxels()) {
                vox = vox.duplicate(); // to avoid having the same instance of voxel as in the region, because voxel value could be different
                for (int i = 0; i<neigh.length; ++i) {
                    n = new Voxel(vox.x+neigh[i][0], vox.y+neigh[i][1], vox.z+neigh[i][2]); // en avant pour les interactions avec d'autre spots / 0
                    if (inputLabels.contains(n.x, n.y, n.z)) { 
                        otherLabel = inputLabels.getPixelInt(n.x, n.y, n.z);   
                        if (otherLabel!=o.getLabel()) {
                            if (background || otherLabel!=0) {
                                InterfaceObject3D inter = getInterface(o, objects.get(otherLabel), true);
                                if (otherLabel>o.getLabel()) inter.addPair(vox, n);
                                else inter.addPair(n, vox);
                            }
                        }
                    }
                    n = new Voxel(vox.x-neigh[i][0], vox.y-neigh[i][1], vox.z-neigh[i][2]);// eventuellement aussi en arriere juste pour interaction avec 0 = background
                    if (inputLabels.contains(n.x, n.y, n.z)) {
                        otherLabel = inputLabels.getPixelInt(n.x, n.y, n.z);  
                        if (background && otherLabel==0) {
                            InterfaceObject3D inter = getInterface(o, objects.get(otherLabel), true); 
                            inter.addPair(n, vox);
                        }
                    }
                }
            } 
        }
        if (verbose) logger.debug("Interface collection: nb of interfaces:"+interfaces.size());
    }
    
    /*public static InterfaceVoxelSet getInteface(Object3D o1, Object3D o2, ImageInteger labelImage) {
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
        Set<Voxel> inter = new HashSet<Voxel>();
        for (Voxel v : min.getVoxels()) {
            for (int i = 0; i<neigh.dx.length; ++i) {
                xx=v.x+neigh.dx[i];
                yy=v.y+neigh.dy[i];
                zz=v.z+neigh.dz[i];
                if (labelImage.contains(xx, yy, zz) && labelImage.getPixelInt(xx, yy, zz)==otherLabel) {
                    inter.add(v);
                    inter.add(new Voxel(xx, yy, zz));
                }
            }
        }
        return new InterfaceVoxelSet(o1, o2, inter, object3DComparator);
    }*/
    public static <I extends InterfaceObject3D<I>> I getInteface(Object3D o1, Object3D o2, ImageInteger labelImage, InterfaceFactory<Object3D, I> interfaceFactory) {
        EllipsoidalNeighborhood neigh = labelImage.getSizeZ()>1 ? new EllipsoidalNeighborhood(1, 1, true) : new EllipsoidalNeighborhood(1, true);
        Object3D min;
        int otherLabel;
        I inter =  interfaceFactory.create(o1, o2, object3DComparator);
        if (o1.getVoxels().size()<=o2.getVoxels().size()) {
            min=o1;
            otherLabel = o2.getLabel();
        } else {
            min = o2;
            otherLabel = o1.getLabel();
        }
        int xx, yy, zz;
        for (Voxel v : min.getVoxels()) {
            for (int i = 0; i<neigh.dx.length; ++i) {
                xx=v.x+neigh.dx[i];
                yy=v.y+neigh.dy[i];
                zz=v.z+neigh.dz[i];
                if (labelImage.contains(xx, yy, zz) && labelImage.getPixelInt(xx, yy, zz)==otherLabel) inter.addPair(v, new Voxel(xx, yy, zz));
            }
        }
        return inter;
    }
    
    /*public void setVoxelIntensities(Image image, boolean objects, boolean interfaces) {
        if (objects) for (Object3D o : this.allElements) for (Voxel v : o.getVoxels()) v.value=image.getPixel(v.x, v.y, v.z);
        if (interfaces) for (I i : this.interfaces) {
            if (i.data instanceof Collection) {
                try {
                    Collection<Voxel> c = (Collection<Voxel>)i.data;
                    for (Voxel v : c) v.value=image.getPixel(v.x, v.y, v.z);
                } catch (Error e) {}
            }
        }
    }*/ 
    
    /*public static void mergeSort(ObjectPopulation population, Image voxelIntensityImage, FusionCriterion<Set<Voxel>> criterion, InterfaceSortMethod<Object3D, Set<Voxel>> interfaceSortMethod) {
        Object3DCluster<Set<Voxel>> c = new Object3DCluster<Set<Voxel>>(population, false, interfaceVoxSetFactory);
        if (voxelIntensityImage!=null) c.setVoxelIntensities(voxelIntensityImage, true, true);
        c.mergeSort(criterion, new InterfaceDataFusionCollection<Set<Voxel>, Voxel>(), interfaceSortMethod);
    }*/
    public static <I extends InterfaceObject3D<I>> void mergeSort(ObjectPopulation population, InterfaceFactory<Object3D, I> interfaceFactory, boolean checkCriterion, int numberOfInterfacesToKeep, int numberOfObjecsToKeep) {
        Object3DCluster<I> c = new Object3DCluster<>(population, false, true, interfaceFactory);
        c.mergeSort(checkCriterion, numberOfInterfacesToKeep, numberOfObjecsToKeep);
    }
    
    public static <I extends InterfaceObject3D<I>> void mergeSort(ObjectPopulation population, InterfaceFactory<Object3D, I> interfaceFactory) {
        mergeSort(population, interfaceFactory, true, 0, 0);
    }
    
    @Override public List<Object3D> mergeSort(boolean checkCriterion, int numberOfInterfacesToKeep, int numberOfObjecsToKeep) {
        int nInit = population.getObjects().size();
        super.mergeSort(checkCriterion, numberOfInterfacesToKeep, numberOfObjecsToKeep);
        if (nInit > population.getObjects().size()) population.relabel(true);
        return population.getObjects();
    }
    
    public static <I extends InterfaceVoxels<I>> ImageShort drawInterfaces(Object3DCluster<I> cluster) {
        ImageShort im = new ImageShort("Iterfaces", cluster.population.getImageProperties());
        for (I i : cluster.interfaces) {
            logger.debug("Interface: {}+{}, size: {}", i.getE1().getLabel(), i.getE2().getLabel(), i.getVoxels().size());
            for (Voxel v : i.getVoxels()) {
                if (cluster.population.getLabelMap().getPixelInt(v.x, v.y, v.z)==i.getE1().getLabel()) im.setPixel(v.x, v.y, v.z, i.getE2().getLabel());
                else im.setPixel(v.x, v.y, v.z, i.getE1().getLabel());
            }
        }
        return im;
    }
    
    public static interface InterfaceVoxels<T extends Interface<Object3D, T>> extends InterfaceObject3D<T> {
        public Collection<Voxel> getVoxels();
    } 
}
