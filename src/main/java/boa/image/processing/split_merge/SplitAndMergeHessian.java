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
package boa.image.processing.split_merge;

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.Voxel;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import boa.image.processing.ImageFeatures;
import boa.image.processing.WatershedTransform;
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
public class SplitAndMergeHessian extends SplitAndMerge<SplitAndMergeHessian.Interface> {
    Image hessian;
    final Image rawIntensityMap;
    Image intensityMap;
    Image normalizedHessian;
    public ImageByte tempSplitMask;
    public final double splitThresholdValue, hessianScale;
    Function<Set<Voxel>, Double> interfaceValue;

    public SplitAndMergeHessian(Image input, double splitThreshold, double hessianScale) {
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
    public SplitAndMergeHessian setInterfaceValue(Function<Set<Voxel>, Double> interfaceValue) {
        this.interfaceValue=interfaceValue;
        return this;
    }

    
    public SplitAndMergeHessian setWatershedMap(Image hessian) {
        this.hessian = hessian;
        return this;
    }
    public Image getHessian() {
        if (hessian ==null) {
            synchronized(this) {
                if (hessian==null) hessian=ImageFeatures.getHessian(rawIntensityMap, hessianScale, false)[0].setName("hessian");
            }
        }
        return hessian;
    }
    @Override public Image getWatershedMap() {
        return getHessian();
    }

    public ImageByte getSplitMask() {
        if (tempSplitMask==null) tempSplitMask = new ImageByte("split mask", rawIntensityMap);
        return tempSplitMask;
    }
    
    @Override
    protected ClusterCollection.InterfaceFactory<Region, Interface> createFactory() {
        return (Region e1, Region e2, Comparator<? super Region> elementComparator) -> new Interface(e1, e2);
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
