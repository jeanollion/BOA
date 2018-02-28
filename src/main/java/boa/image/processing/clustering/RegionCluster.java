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
 * You should have received a copyDataFrom of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package boa.image.processing.clustering;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.Voxel;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import static boa.image.BoundingBox.isIncluded;
import static boa.image.BoundingBox.isIncluded2D;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.ImageShort;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;
import java.util.function.Predicate;

/**
 *
 * @author jollion
 */
public class RegionCluster<I extends InterfaceRegion<I>> extends ClusterCollection<Region, I> {
    RegionPopulation population;
    //public boolean testMode = false;
    public final static Comparator<Region> regionComparator = (Region o1, Region o2) -> Integer.compare(o1.getLabel(), o2.getLabel());
    
    public RegionCluster(RegionPopulation population, boolean background, boolean lowConnectivity, InterfaceFactory<Region, I> interfaceFactory) {
        super(population.getRegions(), regionComparator, interfaceFactory);
        this.population=population;
        setInterfaces(background, lowConnectivity);
    }
    
    
    protected void setInterfaces(boolean background, boolean lowConnectivity) {
        Map<Integer, Region> objects = new HashMap<>();
        for (Region o : population.getRegions()) objects.put(o.getLabel(), o);
        if (background) objects.put(0, new Region(new HashSet<>(), 0, population.getImageProperties(), population.getImageProperties().sizeZ()==1, population.getImageProperties().getScaleXY(), population.getImageProperties().getScaleZ()));
        ImageInteger inputLabels = population.getLabelMap();
        Voxel n;
        int otherLabel;
        int[][] neigh = inputLabels.sizeZ()>1 ? (lowConnectivity ? ImageLabeller.neigh3DLowHalf : ImageLabeller.neigh3DHalf) : (lowConnectivity ? ImageLabeller.neigh2D4Half : ImageLabeller.neigh2D8Half);
        for (Region o : population.getRegions()) {
            for (Voxel vox : o.getVoxels()) {
                vox = vox.duplicate(); // to avoid having the same instance of voxel as in the region, because voxel can overlap & voxel can be used to store values interface-wise
                for (int i = 0; i<neigh.length; ++i) {
                    n = new Voxel(vox.x+neigh[i][0], vox.y+neigh[i][1], vox.z+neigh[i][2]); // only forward for interaction with other spots & background
                    if (inputLabels.contains(n.x, n.y, n.z)) { 
                        otherLabel = inputLabels.getPixelInt(n.x, n.y, n.z);   
                        if (otherLabel!=o.getLabel()) {
                            if (background || otherLabel!=0) {
                                InterfaceRegion inter = getInterface(o, objects.get(otherLabel), true);
                                if (otherLabel>o.getLabel()) inter.addPair(vox, n);
                                else inter.addPair(n, vox);
                            }
                        }
                    } else if (background) {
                        InterfaceRegion inter = getInterface(o, objects.get(0), true); 
                        inter.addPair(n, vox);
                    }
                    if (background) { // backward for background only
                        n = new Voxel(vox.x-neigh[i][0], vox.y-neigh[i][1], vox.z-neigh[i][2]);
                        if (inputLabels.contains(n.x, n.y, n.z)) {
                            otherLabel = inputLabels.getPixelInt(n.x, n.y, n.z);  
                            if (background && otherLabel==0) {
                                InterfaceRegion inter = getInterface(o, objects.get(otherLabel), true); 
                                inter.addPair(n, vox);
                            }
                        } else {
                            InterfaceRegion inter = getInterface(o, objects.get(0), true); 
                            inter.addPair(n, vox);
                        }
                    }
                }
            } 
        }
        if (verbose) logger.debug("Interface collection: nb of interfaces:"+interfaces.size());
    }
    
    public static <I extends InterfaceRegion<I>> I getInteface(Region o1, Region o2, ImageInteger labelImage, InterfaceFactory<Region, I> interfaceFactory) {
        EllipsoidalNeighborhood neigh = labelImage.sizeZ()>1 ? new EllipsoidalNeighborhood(1, 1, true) : new EllipsoidalNeighborhood(1, true);
        Region min;
        int otherLabel;
        I inter =  interfaceFactory.create(o1, o2);
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
    
    public static <I extends InterfaceRegion<I>> void mergeSort(RegionPopulation population, InterfaceFactory<Region, I> interfaceFactory, boolean checkCriterion, int numberOfInterfacesToKeep, int numberOfObjecsToKeep) {
        RegionCluster<I> c = new RegionCluster<>(population, false, true, interfaceFactory);
        c.mergeSort(checkCriterion, numberOfInterfacesToKeep, numberOfObjecsToKeep);
    }
    
    public static <I extends InterfaceRegion<I>> void mergeSort(RegionPopulation population, InterfaceFactory<Region, I> interfaceFactory) {
        mergeSort(population, interfaceFactory, true, 0, 0);
    }
    
    public void setUnmergeablePoints(final List<Region> points) {
        Predicate<I> f = i -> {
            Region inclPoint1 = containsPoint(i.getE1(), points);
            if (inclPoint1==null) return true;
            Region inclPoint2 = containsPoint(i.getE2(), points);
            if (inclPoint2==null || inclPoint1.equals(inclPoint2)) return true;
            else return false;
        };
        super.addForbidFusionPredicate(f);
    }
    private static Region containsPoint(Region o, List<Region> points) {
        if (o.is2D()) {
            for (Region p : points) if (isIncluded2D(p.getBounds(), o.getBounds())) return p;
        } else {
            for (Region p : points) {
                if (o.is2D()) {
                    if (isIncluded2D(p.getBounds(), o.getBounds())) return p;
                } else if (isIncluded(p.getBounds(), o.getBounds())) return p;
            }
        }
        return null;
    }
    @Override public List<Region> mergeSort(boolean checkCriterion, int numberOfInterfacesToKeep, int numberOfObjecsToKeep) {
        int nInit = population.getRegions().size();
        super.mergeSort(checkCriterion, numberOfInterfacesToKeep, numberOfObjecsToKeep);
        if (nInit > population.getRegions().size()) population.relabel(true);
        return population.getRegions();
    }
    
    public void mergeSmallObjects(double sizeLimit, int numberOfObjecsToKeep, BiFunction<Region, Set<Region>, Region> noInterfaceCase) {
        if (numberOfObjecsToKeep<0) numberOfObjecsToKeep=0;
        for (I i : interfaces) i.updateInterface();
        TreeSet<Region> queue = new TreeSet<>((e1, e2) -> Integer.compare(e1.size(), e2.size()));
        queue.addAll(allElements);
        while(queue.size()>numberOfObjecsToKeep) {
            Region s = queue.pollFirst();
            if (s.size()<sizeLimit) {
                TreeSet<I> inter = new TreeSet(getInterfaces(s));
                I strongestInterface = null;
                if (!inter.isEmpty()) strongestInterface = inter.first();
                else if (noInterfaceCase!=null && !queue.isEmpty()) {
                    Region other = noInterfaceCase.apply(s, queue);
                    if (other!=null) strongestInterface = interfaceFactory.create(s, other);
                }
                if (strongestInterface!=null) {
                    if (verbose) logger.debug("mergeSmallObjects: {}, size: {}, interface: {}, all: {}", s.getLabel(), s.size(), strongestInterface, inter);
                    strongestInterface.performFusion();
                    updateInterfacesAfterFusion(strongestInterface, interfaces);
                    allElements.remove(strongestInterface.getE2());
                    interfaces.remove(strongestInterface);
                    queue.remove(strongestInterface.getE2());
                    queue.add(strongestInterface.getE1());
                }
            } else break;
        }
    }
    
    public static <I extends InterfaceVoxels<I>> ImageShort drawInterfaces(RegionCluster<I> cluster) {
        ImageShort im = new ImageShort("Interfaces", cluster.population.getImageProperties());
        for (I i : cluster.interfaces) {
            logger.debug("Interface: {}+{}, size: {}", i.getE1().getLabel(), i.getE2().getLabel(), i.getVoxels().size());
            for (Voxel v : i.getVoxels()) {
                if (!cluster.population.getLabelMap().contains(v.x, v.y, v.z)) continue; // case of background pixels -> can be out of bound
                if (cluster.population.getLabelMap().getPixelInt(v.x, v.y, v.z)==i.getE1().getLabel()) im.setPixel(v.x, v.y, v.z, i.getE2().getLabel());
                else im.setPixel(v.x, v.y, v.z, i.getE1().getLabel());
            }
        }
        return im;
    }
    public static <I extends InterfaceVoxels<I>> ImageFloat drawInterfaceValues(RegionCluster<I> cluster, Function<I, Double> value) {
        ImageFloat im = new ImageFloat("Interface Values", cluster.population.getImageProperties());
        for (I i : cluster.interfaces) {
            double val = value.apply(i);
            for (Voxel v : i.getVoxels()) {
                if (!cluster.population.getLabelMap().contains(v.x, v.y, v.z)) continue; // case of background pixels -> can be out of bound
                if (cluster.population.getLabelMap().getPixelInt(v.x, v.y, v.z)==i.getE1().getLabel()) im.setPixel(v.x, v.y, v.z, val);
                else im.setPixel(v.x, v.y, v.z, val);
            }
        }
        return im;
    }
    
    public static interface InterfaceVoxels<T extends Interface<Region, T>> extends InterfaceRegion<T> {
        public Collection<Voxel> getVoxels();
    }
    
    public static void mergeUntil(RegionPopulation pop, int objectLimit, int interfaceLimit) {
        RegionCluster<SimpleInterfaceVoxelSet> cluster = new RegionCluster<>(pop, false, false, SimpleInterfaceVoxelSet.interfaceFactory());
        cluster.mergeSort(false, interfaceLimit, objectLimit);
    }
}
