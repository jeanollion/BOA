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
        RegionPopulation popWS = WatershedTransform.watershed(getWatershedMap(), segmentationMask, false, null, sfc, false);
        if (testMode) {
            popWS.sortBySpatialOrder(ObjectIdxTracker.IndexingOrder.YXZ);
            ImageWindowManagerFactory.showImage(getWatershedMap());
            ImageWindowManagerFactory.showImage(popWS.getLabelMap().duplicate("seg map before merge"));
        }
        return merge(popWS, minSize, objectMergeLimit);
    }
    public RegionCluster<I> getInterfaces(RegionPopulation population, boolean lowConnectivity) {
        return new RegionCluster<>(population, false, lowConnectivity, getFactory());
    }
    public static void smoothRegions(RegionPopulation pop, boolean lowConnectivity, boolean eraseVoxelsIfConnectedToBackground) {
        Neighborhood n = Filters.getNeighborhood(lowConnectivity?1:1.5, lowConnectivity?1:1.5, pop.getImageProperties());
        HashMapGetCreate<Integer, int[]> count = new HashMapGetCreate<>(9, i->new int[1]);
        Map<Integer, Region> regionByLabel = pop.getObjects().stream().collect(Collectors.toMap(r->r.getLabel(), r->r));
        Iterator<Region> rIt = pop.getObjects().iterator();
        while(rIt.hasNext()) {
            Region r = rIt.next();
            Iterator<Voxel> it = r.getVoxels().iterator();
            while(it.hasNext()) {
                Voxel v = it.next();
                n.setPixels(v, pop.getLabelMap(), null);
                for (int i = 0; i<n.getValueCount(); ++i) count.getAndCreateIfNecessary((int)n.getPixelValues()[i])[0]++;
                if (!eraseVoxelsIfConnectedToBackground) count.remove(0);
                int maxLabel = Collections.max(count.entrySet(), (e1, e2)->Integer.compare(e1.getValue()[0], e2.getValue()[0])).getKey();
                if (maxLabel!=r.getLabel() && count.get(maxLabel)[0]>count.get(r.getLabel())[0]) {
                    it.remove();
                    if (maxLabel>0) regionByLabel.get(maxLabel).getVoxels().add(v);
                    pop.getLabelMap().setPixel(v.x, v.y, v.z, maxLabel);
                }
                count.clear();
            }
            if (r.getVoxels().isEmpty()) rIt.remove();
        }
    }
}
