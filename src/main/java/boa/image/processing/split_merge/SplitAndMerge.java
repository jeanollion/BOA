/*
 * Copyright (C) 2018 jollion
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

import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.Voxel;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.image.Image;
import boa.image.ImageInteger;
import boa.image.processing.Filters;
import boa.image.processing.WatershedTransform;
import boa.image.processing.clustering.ClusterCollection;
import boa.image.processing.clustering.InterfaceRegionImpl;
import boa.image.processing.clustering.RegionCluster;
import boa.image.processing.neighborhood.Neighborhood;
import static boa.plugins.Plugin.logger;
import boa.plugins.plugins.trackers.ObjectIdxTracker;
import boa.utils.HashMapGetCreate;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 * @author jollion
 */
public abstract class SplitAndMerge<I extends InterfaceRegionImpl<I> & RegionCluster.InterfaceVoxels<I>> {
    public boolean testMode;
    protected ClusterCollection.InterfaceFactory<Region, I> factory;
    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }
    public abstract Image getWatershedMap();
    protected abstract ClusterCollection.InterfaceFactory<Region, I> createFactory();
    public ClusterCollection.InterfaceFactory<Region, I> getFactory() {
        if (factory == null) factory = createFactory();
        return factory;
    }
     /**
     * 
     * @param popWS population to merge according to criterion on hessian value @ interface / value @ interfacepopWS.filterAndMergeWithConnected(new RegionPopulation.Size().setMin(minSize));
     * @param objectMergeLimit 
     * @return 
     */
    public RegionPopulation merge(RegionPopulation popWS, int objectMergeLimit) {
        RegionCluster.verbose=testMode;
        RegionCluster.mergeSort(popWS,  getFactory(), objectMergeLimit<=1, 0, objectMergeLimit);
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
    public RegionPopulation splitAndMerge(ImageInteger segmentationMask, int minSizePropagation, int objectMergeLimit) {
        WatershedTransform.SizeFusionCriterion sfc = minSizePropagation>1 ? new WatershedTransform.SizeFusionCriterion(minSizePropagation) : null;
        RegionPopulation popWS = WatershedTransform.watershed(getWatershedMap(), segmentationMask, false, null, sfc, false);
        if (testMode) {
            popWS.sortBySpatialOrder(ObjectIdxTracker.IndexingOrder.YXZ);
            ImageWindowManagerFactory.showImage(getWatershedMap());
            ImageWindowManagerFactory.showImage(popWS.getLabelMap().duplicate("seg map before merge"));
        }
        return merge(popWS, objectMergeLimit);
    }
    public RegionCluster<I> getInterfaces(RegionPopulation population, boolean lowConnectivity) {
        return new RegionCluster<>(population, false, lowConnectivity, getFactory());
    }
    
}
