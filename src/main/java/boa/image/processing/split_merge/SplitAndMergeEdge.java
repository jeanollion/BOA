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
import boa.utils.ArrayUtil;
import java.util.function.BiFunction;

/**
 *
 * @author jollion
 */
public class SplitAndMergeEdge extends SplitAndMerge<SplitAndMergeEdge.Interface> {
    final Image edge;
    Image intensityMap;
    public ImageByte tempSplitMask;
    public final double splitThresholdValue;
    Function<Interface, Double> interfaceValue;

    public SplitAndMergeEdge(Image edgeMap, Image input, double splitThreshold, double quantile) {
        this.edge = edgeMap;
        splitThresholdValue=splitThreshold;
        if (quantile>=0 && quantile<=1) {
            interfaceValue = i->{
                Collection<Voxel> voxels = i.getVoxels();
                if (voxels.isEmpty()) {
                    return Double.NaN;
                } else {
                    float[] values = new float[voxels.size()];
                    int idx = 0;
                    for (Voxel v : voxels)  values[idx++]=edge.getPixel(v.x, v.y, v.z);
                    double val= ArrayUtil.quantile(values, quantile);
                    // normalize by intensity
                    float[] nValues = new float[i.getE1().getVoxels().size()+i.getE2().getVoxels().size()];
                    idx = 0;
                    for (Voxel v : i.getE1().getVoxels()) nValues[idx++]=input.getPixel(v.x, v.y, v.z);
                    for (Voxel v : i.getE2().getVoxels()) nValues[idx++]=input.getPixel(v.x, v.y, v.z);
                    val /=ArrayUtil.quantile(nValues, 0.5);
                    return val;
                }
            };
        } else throw new IllegalArgumentException("Quantile should be >=0 & <=1");
        /*else {
            interfaceValue = voxels->{
                if (voxels.isEmpty()) {
                    return Double.NaN;
                } else {
                    double edgeSum = 0;
                    for (Voxel v : voxels)  edgeSum+=edge.getPixel(v.x, v.y, v.z);
                    return edgeSum;
                }
            };
        }*/
    }
    public BiFunction<? super Interface, ? super Interface, Integer> compareMethod=null;
    public SplitAndMergeEdge setInterfaceValue(Function<Interface, Double> interfaceValue) {
        this.interfaceValue=interfaceValue;
        return this;
    }
    @Override public Image getWatershedMap() {
        return edge;
    }

    public ImageByte getSplitMask() {
        if (tempSplitMask==null) tempSplitMask = new ImageByte("split mask", edge);
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
        @Override public void performFusion() {
            SplitAndMergeEdge.this.regionChanged(e1);
            SplitAndMergeEdge.this.regionChanged(e2);
            super.performFusion();
        }
        @Override public void updateInterface() {
            value = interfaceValue.apply(this);
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
            if (voxels.size()<=5) return false;
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
            int c = compareMethod!=null ? compareMethod.apply(this, t) : Double.compare(value, t.value); // small edges first
            if (c==0) return super.compareElements(t, RegionCluster.regionComparator); // consitency with equals method
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
