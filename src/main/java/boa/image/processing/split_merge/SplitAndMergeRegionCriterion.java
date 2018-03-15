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
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * Split & Merge Class with interface value function based on value in whole region
 * @author jollion
 */
public class SplitAndMergeRegionCriterion extends SplitAndMerge<SplitAndMergeRegionCriterion.Interface> {
    final Image wsMap;
    public final double splitThresholdValue;
    Function<Interface, Double> interfaceValue;

    
    public static enum InterfaceValue {MEAN_INTENSITY_IN_REGIONS, DIFF_INTENSITY_BTWN_REGIONS, DIFF_MEDIAN_BTWN_REGIONS};
    public SplitAndMergeRegionCriterion(Image edgeMap, Image intensityMap, double splitThreshold, InterfaceValue method) {
        super(intensityMap);
        this.wsMap = edgeMap;
        splitThresholdValue=splitThreshold;
        switch (method) {
            case MEAN_INTENSITY_IN_REGIONS:
            default:
                interfaceValue = i-> -Stream.concat(i.getE1().getVoxels().stream(), i.getE2().getVoxels().stream()).mapToDouble(v->intensityMap.getPixel(v.x, v.y, v.z)).average().getAsDouble(); // maximal intensity first
                break;
            case DIFF_INTENSITY_BTWN_REGIONS:
                interfaceValue = i -> {
                    double m1 = i.getE1().getVoxels().stream().mapToDouble(v->intensityMap.getPixel(v.x, v.y, v.z)).average().getAsDouble();
                    double m2 = i.getE2().getVoxels().stream().mapToDouble(v->intensityMap.getPixel(v.x, v.y, v.z)).average().getAsDouble();
                    return Math.abs(m1-m2); // minimal difference first
                };
                break;
            case DIFF_MEDIAN_BTWN_REGIONS:
                interfaceValue = i -> Math.abs(getMedianValues().getAndCreateIfNecessarySync(i.getE1())-getMedianValues().getAndCreateIfNecessarySync(i.getE2()));
                break;
        }
        
    }

    public BiFunction<? super Interface, ? super Interface, Integer> compareMethod=null;
    
    public SplitAndMergeRegionCriterion setInterfaceValue(Function<Interface, Double> interfaceValue) {
        this.interfaceValue=interfaceValue;
        return this;
    }
    @Override public Image getWatershedMap() {
        return wsMap;
    }
    @Override
    public Image getSeedCreationMap() {
        return wsMap;
    }

    @Override
    protected ClusterCollection.InterfaceFactory<Region, Interface> createFactory() {
        return (Region e1, Region e2) -> new Interface(e1, e2);
    }
    
    public class Interface extends InterfaceRegionImpl<Interface> {
        public double value;
        public Interface(Region e1, Region e2) {
            super(e1, e2);
        }
        @Override public void performFusion() {
            SplitAndMergeRegionCriterion.this.regionChanged(e1);
            SplitAndMergeRegionCriterion.this.regionChanged(e2);
            super.performFusion();
        }
        @Override public void updateInterface() {
            value = interfaceValue.apply(this);
        }

        @Override 
        public void fusionInterface(Interface otherInterface, Comparator<? super Region> elementComparator) {
            //fusionInterfaceSetElements(otherInterface, elementComparator);
            value = Double.NaN;// updateSortValue will be called afterwards
        }

        @Override
        public boolean checkFusion() {
            if (testMode) logger.debug("check fusion: {}+{}, value: {}, threhsold: {}, fusion: {}", e1.getLabel(), e2.getLabel(), value, splitThresholdValue, value<splitThresholdValue);
            return value<splitThresholdValue;
        }

        @Override
        public void addPair(Voxel v1, Voxel v2) {
        }

        @Override
        public int compareTo(Interface t) {
            int c = compareMethod!=null ? compareMethod.apply(this, t) : Double.compare(value, t.value); // small edges first
            if (c==0) return super.compareElements(t, RegionCluster.regionComparator); // consitency with equals method
            else return c;
        }

        @Override
        public String toString() {
            return "Interface: " + e1.getLabel()+"+"+e2.getLabel()+ " sortValue: "+value;
        } 
    }
}
