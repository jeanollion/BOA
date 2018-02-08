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
package boa.plugins.plugins.segmenters;

import boa.configuration.parameters.Parameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.RegionPopulation.ContactBorder;
import boa.data_structure.StructureObjectProcessing;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.ImageMask;
import boa.image.processing.Filters;
import boa.image.processing.ImageFeatures;
import boa.image.processing.WatershedTransform;
import boa.image.processing.clustering.RegionCluster;
import boa.image.processing.localthickness.LocalThickness;
import boa.image.processing.split_merge.SplitAndMerge;
import boa.image.processing.split_merge.SplitAndMergeBacteriaShape;
import boa.image.processing.split_merge.SplitAndMergeEdge;
import boa.image.processing.split_merge.SplitAndMergeHessian;
import boa.plugins.ObjectSplitter;
import boa.plugins.SegmenterSplitAndMerge;
import boa.plugins.plugins.thresholders.ConstantValue;
import boa.plugins.plugins.thresholders.IJAutoThresholder;
import boa.plugins.plugins.trackers.ObjectIdxTracker;
import ij.process.AutoThresholder;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 *
 * @author jollion
 */
public class BacteriaShape implements SegmenterSplitAndMerge, ObjectSplitter {
    public boolean testMode = false;
    @Override
    public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        if (testMode) ImageWindowManagerFactory.showImage(input);
        Image smoothed = Filters.median(input, null, Filters.getNeighborhood(3, input));
        Image sigma = Filters.applyFilter(smoothed, new ImageFloat("", input), new Filters.Sigma(), Filters.getNeighborhood(3, input)); // lower than 4
        if (testMode) ImageWindowManagerFactory.showImage(sigma);
        RegionPopulation pop = partitionImage(sigma, parent.getMask());
        pop.smoothRegions(2, true, parent.getMask()); // regularize borders
        if (testMode) {
            ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, input).setName("after partition"));
            ImageWindowManagerFactory.showImage(pop.getLabelMap().duplicate("labels after partition"));
        }
        
        EdgeDetector seg = new EdgeDetector().setThrehsoldingMethod(1);
        //seg.setThresholder(new ConstantValue(0.2)).filterRegions(pop, input, parent.getMask());
        // merge according to quantile value of sigma @ border between regions
        SplitAndMergeEdge sam = new SplitAndMergeEdge(sigma, input, 0.06, 0.25); // without norm 0.25 -> 0.03 sur-seg, 0.04 merge  head of cell with background when low intensity | with normalization: 0.06-0.08 
        sam.compareMethod = sam.compareByMedianIntensity(input, true);
        //sam.setTestMode(testMode);
        sam.merge(pop, 0);
        if (testMode) {
            ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, input).setName("after merge"));
        }
        pop.filter(new ContactBorder(input.getSizeY()/2, input, ContactBorder.Border.Xl)); 
        pop.filter(new ContactBorder(input.getSizeY()/2, input, ContactBorder.Border.Xr)); 
        seg.setThresholder(new ConstantValue(0.4)).filterRegions(pop, input, parent.getMask());
        //seg.setIsDarkBackground(false).setThresholder(new ConstantValue(0.05)).filterRegions(pop, sigma, parent.getMask());
        
        sam = new SplitAndMergeEdge(sigma, input, 0.09, 0.25);
        //sam.setTestMode(testMode);
        sam.merge(pop, 0);
        
        if (testMode) {
            ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, input).setName("after delete"));
            ImageWindowManagerFactory.showImage(pop.getLabelMap().duplicate("labels before merge by shape"));
        }
        // merge 
        SplitAndMergeBacteriaShape samShape = new SplitAndMergeBacteriaShape();
        samShape.curvaturePerCluster = false;
        samShape.useThicknessCriterion=false;
        samShape.thresholdCurvSides=-0.03;
        samShape.curvatureScale=6;
        samShape.thresholdCurvMean=Double.NEGATIVE_INFINITY;
        samShape.compareMethod = samShape.compareBySize(true);
        //samShape.setTestMode(testMode);
        samShape.merge(pop, 0);
        if (testMode) ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, input).setName("after merge by shape"));
        
        pop.filter(new RegionPopulation.Size().setMin(100));
        pop.filter(new RegionPopulation.LocalThickness(10));
        seg.setThresholder(new ConstantValue(0.5)).filterRegions(pop, input, parent.getMask());
        pop.localThreshold(smoothed, 2.5, true, false);
        
        if (testMode) ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, input).setName("after delete by size and local thld"));
        
        samShape = new SplitAndMergeBacteriaShape();
        samShape.setTestMode(testMode);
        samShape.splitAndMerge(pop.getLabelMap(), 5, 0);
        pop.filter(new RegionPopulation.Size().setMin(100));
        //pop.filter(new RegionPopulation.LocalThickness(10));
        pop.sortBySpatialOrder(ObjectIdxTracker.IndexingOrder.YXZ);
        if (testMode) ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, input).setName("after split & merge"));
        
        // TODO add criterion on thickness at interface using local thickness ? -> fix it before
        return pop;
    }
    private static RegionPopulation partitionImage(Image wsMap, ImageMask mask) {
        int minSizePropagation = 5;
        ImageInteger seeds = Filters.localExtrema(wsMap, null, false, mask, Filters.getNeighborhood(1, 1, wsMap)); 
        // add  seeds on sides to partition image properly
        int[] xs = new int[]{0, wsMap.getSizeX()-1};
        for (int y = 0; y<wsMap.getSizeY(); ++y) {
            for (int x : xs) {
                if (mask.insideMask(x, y, 0)) seeds.setPixel(x, y, 0, 1);
            }
        }
        WatershedTransform.SizeFusionCriterion sfc = minSizePropagation>1 ? new WatershedTransform.SizeFusionCriterion(minSizePropagation) : null;
        WatershedTransform wt = new WatershedTransform(wsMap, mask, Arrays.asList(ImageLabeller.labelImage(seeds)), false, null, sfc);
        wt.setLowConnectivity(false);
        wt.run();
        return wt.getObjectPopulation();
    }
    
    @Override
    public double split(Image input, Region o, List<Region> result) {
        SplitAndMergeBacteriaShape sam = new SplitAndMergeBacteriaShape();
        sam.ignoreEndOfChannelRegionWhenMerginSmallRegions = false;
        sam.testMode=testMode;
        RegionPopulation pop = sam.splitAndMerge(o.getMask(), 50, 2);
        if (pop.getObjects().size()!=2) return Double.POSITIVE_INFINITY;
        result.addAll(pop.getObjects());
        RegionCluster<SplitAndMergeBacteriaShape.InterfaceLocalShape> c = new RegionCluster(pop, false, true, sam.getFactory());
        SplitAndMergeBacteriaShape.InterfaceLocalShape i = c.getAllInterfaces().iterator().next();
        i.updateInterface();
        return getCost(i.getCurvatureValue(), sam.thresholdCurvMean, false);
    }

    @Override
    public double computeMergeCost(Image input, List<Region> objects) {
        SplitAndMergeBacteriaShape sam = new SplitAndMergeBacteriaShape();
        sam.ignoreEndOfChannelRegionWhenMerginSmallRegions = false;
        sam.testMode=testMode;
        RegionPopulation mergePop = new RegionPopulation(objects, input, false);
        RegionCluster<SplitAndMergeBacteriaShape.InterfaceLocalShape> c = new RegionCluster(mergePop, false, true, sam.getFactory());
        List<Set<Region>> clusters = c.getClusters();
        if (clusters.size()>1) return Double.POSITIVE_INFINITY;
        if (sam.curvaturePerCluster) sam.updateCurvature(c.getClusters(), mergePop.getLabelMap());
        Set<SplitAndMergeBacteriaShape.InterfaceLocalShape> allInterfaces = c.getInterfaces(clusters.get(0));
        double minCurv = Double.POSITIVE_INFINITY;
        for (SplitAndMergeBacteriaShape.InterfaceLocalShape i : allInterfaces) { // get the min curvature value = worst case
            i.updateInterface();
            if (i.getCurvatureValue()<minCurv) minCurv = i.getCurvatureValue();
        }
        if (minCurv==Double.POSITIVE_INFINITY || minCurv==Double.NaN) return Double.POSITIVE_INFINITY;
        return getCost(minCurv, sam.thresholdCurvMean, true);
    }
    
    public static double getCost(double value, double threshold, boolean valueShouldBeBelowThresholdForAPositiveCost)  {
        if (valueShouldBeBelowThresholdForAPositiveCost) {
            if (value>=threshold) return 0;
            else return (threshold-value);
        } else {
            if (value<=threshold) return 0;
            else return (value-threshold);
        }
    }

    @Override
    public RegionPopulation splitObject(Image input, Region object) {
        SplitAndMergeBacteriaShape sam = new SplitAndMergeBacteriaShape();
        sam.testMode=testMode;
        sam.ignoreEndOfChannelRegionWhenMerginSmallRegions = false;
        RegionPopulation pop = sam.splitAndMerge(object.getMask(), 50, 2);
        pop.translate(object.getBounds(), true);
        return pop;
    }
    
    @Override
    public Parameter[] getParameters() {
        return new Parameter[0];
    }

    @Override
    public void setSplitVerboseMode(boolean verbose) {
        this.testMode=verbose;
    }
    
}
