/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
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
import boa.image.processing.watershed.WatershedTransform;
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
import boa.measurement.BasicMeasurements;
import boa.utils.ArrayUtil;
import boa.utils.HashMapGetCreate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 *
 * @author jollion
 */
public class SplitAndMergeEdge extends SplitAndMerge<SplitAndMergeEdge.Interface> {
    final Image edge;
    public ImageByte tempSplitMask;
    public final double splitThresholdValue;
    Function<Interface, Double> interfaceValue;

    public SplitAndMergeEdge(Image edgeMap, Image intensityMap, double splitThreshold, boolean normalizeEdgeValues) {
        super(intensityMap);
        this.edge = edgeMap;
        splitThresholdValue=splitThreshold;
        setInterfaceValue(0.5, normalizeEdgeValues);
    }

    public BiFunction<? super Interface, ? super Interface, Integer> compareMethod=null;
    public Image drawInterfaceValues(RegionPopulation pop) {
        return RegionCluster.drawInterfaceValues(new RegionCluster<>(pop, true, getFactory()), i->{i.updateInterface(); return i.value;});
    }
    public SplitAndMergeEdge setInterfaceValue(double quantile, boolean normalizeEdgeValues) {
        interfaceValue = i-> {
            if (i.getVoxels().isEmpty()) {
                return Double.NaN;
            } else {
                int size = i.voxels.size()+i.duplicatedVoxels.size();
                double val= ArrayUtil.quantile(Stream.concat(i.voxels.stream(), i.duplicatedVoxels.stream()).mapToDouble(v->edge.getPixel(v.x, v.y, v.z)).sorted(), size, quantile);
                if (normalizeEdgeValues) {// normalize by intensity (mean better than median, better than mean @ edge)
                    double sum = BasicMeasurements.getSum(i.getE1(), intensityMap)+BasicMeasurements.getSum(i.getE2(), intensityMap);
                    double mean = sum /(double)(i.getE1().size()+i.getE2().size());
                    val= val/mean;
                }
                return val;
            }
        };
        return this;
    }
    public SplitAndMergeEdge setInterfaceValue(Function<Interface, Double> interfaceValue) {
        this.interfaceValue=interfaceValue;
        return this;
    }
    @Override public Image getWatershedMap() {
        return edge;
    }
    @Override
    public Image getSeedCreationMap() {
        return edge;
    }

    public ImageByte getSplitMask() {
        if (tempSplitMask==null) tempSplitMask = new ImageByte("split mask", edge);
        return tempSplitMask;
    }
    
    @Override
    protected ClusterCollection.InterfaceFactory<Region, Interface> createFactory() {
        return (Region e1, Region e2) -> new Interface(e1, e2);
    }
    
    public class Interface extends InterfaceRegionImpl<Interface> implements RegionCluster.InterfaceVoxels<Interface> {
        public double value;
        Set<Voxel> voxels; // to allow duplicate values from background
        Set<Voxel> duplicatedVoxels;
        public Interface(Region e1, Region e2) {
            super(e1, e2);
            voxels = new HashSet<>();
            duplicatedVoxels = new HashSet<>();
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
            duplicatedVoxels.addAll(other.duplicatedVoxels);
            value = Double.NaN;// updateSortValue will be called afterwards
        }

        @Override
        public boolean checkFusion() {
            if (voxels.size()<=5) return false;
            if (addTestImage!=null) logger.debug("check fusion: {}+{}, size: {}, value: {}, threhsold: {}, fusion: {}", e1.getLabel(), e2.getLabel(), voxels.size(), value, splitThresholdValue, value<splitThresholdValue);
            return value<splitThresholdValue;
        }

        @Override
        public void addPair(Voxel v1, Voxel v2) {
            if (foregroundMask==null || !foregroundMask.contains(v1.x, v1.y, v1.z)) duplicatedVoxels.add(v2);
            else  voxels.add(v1);
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
        public Collection<Voxel> getDuplicatedVoxels() {
            return duplicatedVoxels;
        }
        @Override
        public String toString() {
            return "Interface: " + e1.getLabel()+"+"+e2.getLabel()+ " sortValue: "+value;
        } 
    }
}
