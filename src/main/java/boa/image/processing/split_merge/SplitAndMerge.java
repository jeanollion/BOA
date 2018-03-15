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

import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.Voxel;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.image.processing.Filters;
import boa.image.processing.watershed.WatershedTransform;
import boa.image.processing.clustering.ClusterCollection;
import boa.image.processing.clustering.InterfaceRegionImpl;
import boa.image.processing.clustering.RegionCluster;
import boa.image.processing.neighborhood.Neighborhood;
import boa.measurement.BasicMeasurements;
import static boa.plugins.Plugin.logger;
import boa.plugins.plugins.trackers.ObjectIdxTracker;
import boa.utils.HashMapGetCreate;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 *
 * @author jollion
 * @param <I>
 */
public abstract class SplitAndMerge<I extends InterfaceRegionImpl<I> > { //& RegionCluster.InterfaceVoxels<I>
    public boolean testMode;
    protected ClusterCollection.InterfaceFactory<Region, I> factory;
    protected HashMapGetCreate<Region, Double> medianValues;
    protected Image intensityMap;
    boolean wsMapIsEdgeMap = true, localMinOnSeedMap=true;
    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }
    public SplitAndMerge(Image intensityMap) {
        this.intensityMap=intensityMap;
        medianValues= new HashMapGetCreate<>(r -> BasicMeasurements.getQuantileValue(r, intensityMap, 0.5)[0]);
    }
    /**
     * 
     * @return A map containing median values of a given region within the map {@param intensityMap}, updated when a fusion occurs
     */
    public HashMapGetCreate<Region, Double> getMedianValues() {
        return medianValues;
    }

    public Image getIntensityMap() {
        return intensityMap;
    }
    public abstract Image getSeedCreationMap();
    public abstract Image getWatershedMap();
    protected abstract ClusterCollection.InterfaceFactory<Region, I> createFactory();
    public ClusterCollection.InterfaceFactory<Region, I> getFactory() {
        if (factory == null) factory = createFactory();
        return factory;
    }

    public void addForbidFusion(Predicate<I> forbidFusion) {
        if (forbidFusion==null) return;
        if (this.forbidFusion!=null) this.forbidFusion=this.forbidFusion.or(forbidFusion);
        else this.forbidFusion = forbidFusion;
    }
    public void addForbidFusionForegroundBackground(Predicate<Region> isBackground, Predicate<Region> isForeground) {
        this.addForbidFusion(i->{
            if (isBackground.test(i.getE1()) && isForeground.test(i.getE2())) return true;
            if (isForeground.test(i.getE1()) && isBackground.test(i.getE2())) return true;
            return false;
        });
        
    }
    /**
     * Fusion is forbidden if difference of median values within regions is superior to {@param thldDiff}
     * @param thldDiff 
     */
    public void addForbidByMedianDifference(double thldDiff) {
        this.addForbidFusion(i->{
            double med1 = medianValues.getAndCreateIfNecessary(i.getE1());
            double med2 = medianValues.getAndCreateIfNecessary(i.getE2());
            return Math.abs(med1-med2)>thldDiff;
        });
    }
    
    protected Predicate<I> forbidFusion;
     /**
     * 
     * @param popWS population to merge according to criterion on hessian value @ interface / value @ interfacepopWS.filterAndMergeWithConnected(new RegionPopulation.Size().setMin(minSize));
     * @param numberOfObjectsToKeep 
     * @return 
     */
    public RegionPopulation merge(RegionPopulation popWS, int numberOfObjectsToKeep) {
        RegionCluster.verbose=testMode;
        RegionCluster<I> c = new RegionCluster<>(popWS, false, true, getFactory());
        c.addForbidFusionPredicate(forbidFusion);
        c.mergeSort(numberOfObjectsToKeep<=1, 0, numberOfObjectsToKeep);
        //if (testMode) disp.showImage(popWS.getLabelMap().duplicate("seg map after merge"));
        popWS.sortBySpatialOrder(ObjectIdxTracker.IndexingOrder.YXZ);
        if (testMode) ImageWindowManagerFactory.showImage(popWS.getLabelMap().duplicate("seg map"));
        return popWS;
    }
    /**
     * 
     * @param segmentationMask
     * @param minSizePropagation
     * @param objectMergeLimit
     * @return 
     */
    public RegionPopulation splitAndMerge(ImageMask segmentationMask, int minSizePropagation, int objectMergeLimit) {
        RegionPopulation popWS = split(segmentationMask, minSizePropagation);
        if (testMode) {
            ImageWindowManagerFactory.showImage(getWatershedMap());
            ImageWindowManagerFactory.showImage(popWS.getLabelMap().duplicate("seg map after split by hessian before merge"));
        }
        return merge(popWS, objectMergeLimit);
    }
    public RegionPopulation split(ImageMask segmentationMask, int minSizePropagation) {
        ImageByte seeds = Filters.localExtrema(getSeedCreationMap(), null, !localMinOnSeedMap, segmentationMask, Filters.getNeighborhood(1.5, 1.5, getSeedCreationMap()));
        WatershedTransform.WatershedConfiguration config = new WatershedTransform.WatershedConfiguration().decreasingPropagation(!wsMapIsEdgeMap);
        if (minSizePropagation>1) config.fusionCriterion(new WatershedTransform.SizeFusionCriterion(minSizePropagation));
        RegionPopulation popWS = WatershedTransform.watershed(getWatershedMap(), segmentationMask, seeds, config);
        if (testMode) popWS.sortBySpatialOrder(ObjectIdxTracker.IndexingOrder.YXZ);
        return popWS;
    }
    public RegionCluster<I> getInterfaces(RegionPopulation population, boolean lowConnectivity) {
        return new RegionCluster<>(population, false, lowConnectivity, getFactory());
    }
    public final BiFunction<? super I, ? super I, Integer> compareBySize(boolean largerFirst) {
        return (i1, i2) -> {
            int[] maxMin1 = i1.getE1().size()>i1.getE2().size() ? new int[]{i1.getE1().size(), i1.getE2().size()} : new int[]{i1.getE2().size(), i1.getE1().size()};
            int[] maxMin2 = i2.getE1().size()>i2.getE2().size() ? new int[]{i2.getE1().size(), i2.getE2().size()} : new int[]{i2.getE2().size(), i2.getE1().size()};
            if (largerFirst) {
                int c = Integer.compare(maxMin1[0], maxMin2[0]);
                if (c!=0) return -c;
                return Integer.compare(maxMin1[1], maxMin2[1]); // smaller first
            } else {
                int c = Integer.compare(maxMin1[1], maxMin2[1]);
                if (c!=0) return c;
                return Integer.compare(maxMin1[0], maxMin2[0]);
            }  
        };
        /*return (i1, i2) -> {
            int s1 = i1.getE1().getSize() + i1.getE2().getSize();
            int s2 = i2.getE1().getSize() + i2.getE2().getSize();
            return largerFirst ? Integer.compare(s2, s1) : Integer.compare(s1, s2);
        };*/
    }
    protected void regionChanged(Region r) {
        if (medianValues!=null) medianValues.remove(r);
    }
    
    public BiFunction<? super I, ? super I, Integer> compareByMedianIntensity(boolean highIntensityFisrt) {
        return (i1, i2) -> {
            double i11  = medianValues.getAndCreateIfNecessary(i1.getE1());
            double i12  = medianValues.getAndCreateIfNecessary(i1.getE2());
            double i21  = medianValues.getAndCreateIfNecessary(i2.getE1());
            double i22  = medianValues.getAndCreateIfNecessary(i2.getE2());
            
            if (highIntensityFisrt) {
                double min1 = Math.min(i11, i12);
                double min2 = Math.min(i21, i22);
                int c = Double.compare(min1, min2);
                if (c!=0) return -c; // max of mins first
                double max1 = Math.max(i11, i12);
                double max2 = Math.max(i21, i22);
                return -Double.compare(max1, max2); // max of maxs first
            } else {
                double max1 = Math.max(i11, i12);
                double max2 = Math.max(i21, i22);
                int c = Double.compare(max1, max2);
                if (c!=0) return c;
                double min1 = Math.min(i11, i12);
                double min2 = Math.min(i21, i22);
                return Double.compare(min1, min2);
            }
        };
    }
}
