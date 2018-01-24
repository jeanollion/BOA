/*
 * Copyright (C) 2017 jollion
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

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.Voxel;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import static boa.plugins.Plugin.logger;
import boa.plugins.plugins.trackers.ObjectIdxTracker;
import boa.image.processing.clustering.ClusterCollection;
import boa.image.processing.clustering.InterfaceRegionImpl;
import boa.image.processing.clustering.RegionCluster;

/**
 *
 * @author jollion
 */
public class SplitAndMerge {
    Image hessian;
    final Image rawIntensityMap;
    Image intensityMap;
    Image normalizedHessian;
    public ImageByte tempSplitMask;
    public final double splitThresholdValue, hessianScale;
    ClusterCollection.InterfaceFactory<Region, Interface> factory;
    boolean testMode;
    final Function<Set<Voxel>, Double> interfaceValue;
    public SplitAndMerge(Image input, double splitThreshold, double hessianScale, Function<Set<Voxel>, Double> interfaceValue) {
        rawIntensityMap=input;
        splitThresholdValue=splitThreshold;
        this.hessianScale=hessianScale;
        this.interfaceValue=interfaceValue;
    }
    public SplitAndMerge(Image input, double splitThreshold, double hessianScale) {
        rawIntensityMap=input;
        splitThresholdValue=splitThreshold;
        this.hessianScale=hessianScale;
        interfaceValue = voxels->{
            if (voxels.isEmpty()) {
                return Double.NaN;
            } else {
                double hessSum = 0, intensitySum = 0;
                getHessian();
                for (Voxel v : voxels) {
                    hessSum+=hessian.getPixel(v.x, v.y, v.z);
                    intensitySum += rawIntensityMap.getPixel(v.x, v.y, v.z);
                }
                return hessSum / intensitySum;
            }
        };
    }

    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }
     
    public Image getHessian() {
        if (hessian ==null) hessian=ImageFeatures.getHessian(rawIntensityMap, hessianScale, false)[0].setName("hessian");
        return hessian;
    }

    public ImageByte getSplitMask() {
        if (tempSplitMask==null) tempSplitMask = new ImageByte("split mask", rawIntensityMap);
        return tempSplitMask;
    }
    
    /**
     * 
     * @param segmentationMask
     * @param minSizePropagation
     * @param minSize
     * @param objectMergeLimit
     * @return 
     */
    public RegionPopulation splitAndMerge(ImageInteger segmentationMask, int minSizePropagation, int minSize, int objectMergeLimit) {
        WatershedTransform.SizeFusionCriterion sfc = minSizePropagation>1 ? new WatershedTransform.SizeFusionCriterion(minSizePropagation) : null;
        RegionPopulation popWS = WatershedTransform.watershed(getHessian(), segmentationMask, false, null, sfc, false);
        if (testMode) {
            popWS.sortBySpatialOrder(ObjectIdxTracker.IndexingOrder.YXZ);
            ImageWindowManagerFactory.showImage(getHessian());
            ImageWindowManagerFactory.showImage(popWS.getLabelMap().duplicate("seg map before merge"));
        }
        return merge(popWS, minSize, objectMergeLimit);
    }
    /**
     * 
     * @param popWS population to merge according to criterion on hessian value @ interface / value @ interface
     * @param minSize after merge, objects smaller than this size will be erased
     * @param objectMergeLimit 
     * @return 
     */
    public RegionPopulation merge(RegionPopulation popWS, int minSize, int objectMergeLimit) {
        RegionCluster.verbose=testMode;
        RegionCluster.mergeSort(popWS,  getFactory(), objectMergeLimit<=1, 0, objectMergeLimit);
        //if (testMode) disp.showImage(popWS.getLabelMap().duplicate("seg map after merge"));
        popWS.filterAndMergeWithConnected(new RegionPopulation.Thickness().setX(2).setY(2)); // remove thin objects
        popWS.filterAndMergeWithConnected(new RegionPopulation.Size().setMin(minSize)); // remove small objects
        popWS.sortBySpatialOrder(ObjectIdxTracker.IndexingOrder.YXZ);
        if (testMode) ImageWindowManagerFactory.showImage(popWS.getLabelMap().duplicate("seg map"));
        return popWS;
    }
    public RegionCluster<Interface> getInterfaces(RegionPopulation population, boolean lowConnectivity) {
        return new RegionCluster<>(population, false, lowConnectivity, getFactory());
    }
    

    public ClusterCollection.InterfaceFactory<Region, Interface> getFactory() {
        if (factory==null) factory = (Region e1, Region e2, Comparator<? super Region> elementComparator) -> new Interface(e1, e2);
        return factory;
    }
    
    
    
    public class Interface extends InterfaceRegionImpl<Interface> implements RegionCluster.InterfaceVoxels<Interface> {
        public double value;
        Set<Voxel> voxels;
        public Interface(Region e1, Region e2) {
            super(e1, e2);
            voxels = new HashSet<>();
        }

        @Override public void updateSortValue() {
            value = interfaceValue.apply(voxels);
        }

        @Override 
        public void fusionInterface(Interface otherInterface, Comparator<? super Region> elementComparator) {
            //fusionInterfaceSetElements(otherInterface, elementComparator);
            Interface other = otherInterface;
            voxels.addAll(other.voxels); 
            value = Double.NaN;// updateSortValue will be called afterwards
        }

        @Override
        public boolean checkFusion() {
            // criterion = - hessian @ border / intensity @ border < threshold
            if (testMode) logger.debug("check fusion: {}+{}, size: {}, value: {}, threhsold: {}, fusion: {}", e1.getLabel(), e2.getLabel(), voxels.size(), value, splitThresholdValue, value<splitThresholdValue);
            return value<splitThresholdValue;
        }

        @Override
        public void addPair(Voxel v1, Voxel v2) {
           voxels.add(v1);
           voxels.add(v2);
        }

        @Override
        public int compareTo(Interface t) {
            int c = Double.compare(value, t.value); // increasing values
            if (c==0) return super.compareElements(t, RegionCluster.regionComparator);
            else return c;
        }
        @Override
        public Collection<Voxel> getVoxels() {
            return voxels;
        }

        @Override
        public String toString() {
            return "Interface: " + e1.getLabel()+"+"+e2.getLabel()+ " sortValue: "+value;
        } 
    }
}
