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
import boa.image.processing.localthickness.LocalThickness;
import boa.image.processing.split_merge.SplitAndMerge;
import boa.image.processing.split_merge.SplitAndMergeBacteriaShape;
import boa.image.processing.split_merge.SplitAndMergeEdge;
import boa.image.processing.split_merge.SplitAndMergeHessian;
import boa.plugins.SegmenterSplitAndMerge;
import boa.plugins.plugins.thresholders.ConstantValue;
import boa.plugins.plugins.thresholders.IJAutoThresholder;
import ij.process.AutoThresholder;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author jollion
 */
public class BacteriaShape implements SegmenterSplitAndMerge {
    public boolean testMode = false;
    @Override
    public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        if (testMode) ImageWindowManagerFactory.showImage(input);
        Image smoothed = Filters.median(input, null, Filters.getNeighborhood(3, input));
        Image sigma = Filters.applyFilter(smoothed, new ImageFloat("", input), new Filters.Sigma(), Filters.getNeighborhood(3, input)); // lower than 4
        if (testMode) ImageWindowManagerFactory.showImage(sigma);
        RegionPopulation pop = partitionImage(sigma, parent.getMask());
        pop.smoothRegions(2, true, parent.getMask()); // regularize borders
        if (testMode) ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, input).setName("after partition"));
        
        EdgeDetector seg = new EdgeDetector().setThrehsoldingMethod(1);
        //seg.setThresholder(new ConstantValue(0.2)).filterRegions(pop, input, parent.getMask());
        
        SplitAndMergeEdge sam = new SplitAndMergeEdge(sigma, 0.03, 0.25); // merge according to quantile value of sigma @ border between regions
        //sam.setTestMode(testMode);
        sam.merge(pop, 0);
        if (testMode) {
            ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, input).setName("after merge"));
            ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, sigma).setName("sigma after merge"));
        }
        pop.filter(new ContactBorder(input.getSizeY()/2, input, ContactBorder.Border.Xl)); 
        pop.filter(new ContactBorder(input.getSizeY()/2, input, ContactBorder.Border.Xr)); 
        seg.setThresholder(new ConstantValue(0.3)).filterRegions(pop, input, parent.getMask());
        //seg.setIsDarkBackground(false).setThresholder(new ConstantValue(0.05)).filterRegions(pop, sigma, parent.getMask());
        
        if (testMode) {
            ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, input).setName("after delete"));
            ImageWindowManagerFactory.showImage(pop.getLabelMap().duplicate("labels before merge by shape"));
            
        }
        // merge 
        SplitAndMergeBacteriaShape samShape = new SplitAndMergeBacteriaShape();
        samShape.curvaturePerCluster = false;
        samShape.useThicknessCriterion=false;
        samShape.thresholdCurvSides=-0.02;
        samShape.thresholdCurvMean=Double.NEGATIVE_INFINITY;
        samShape.compareMethod = SplitAndMergeBacteriaShape.compareBySize(true);
        samShape.setTestMode(testMode);
        samShape.merge(pop, 0);
        if (testMode) ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, input).setName("after merge by shape"));
        
        pop.filter(new RegionPopulation.Size().setMin(100));
        pop.filter(new RegionPopulation.LocalThickness(10));
        pop.localThreshold(smoothed, 2.5, true, false);
        
        if (testMode) ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, input).setName("after delete by size and local thld"));
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
        //SplitAndMergeBacteriaShape sam = new SplitAndMergeBacteriaShape();
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public double computeMergeCost(Image input, List<Region> objects) {
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[0];
    }
    
}
