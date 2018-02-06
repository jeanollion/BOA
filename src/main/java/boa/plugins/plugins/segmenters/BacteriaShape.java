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
import boa.data_structure.Voxel;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.ImageMask;
import boa.image.ThresholdMask;
import boa.image.processing.Filters;
import boa.image.processing.ImageFeatures;
import boa.image.processing.ImageOperations;
import boa.image.processing.WatershedTransform;
import boa.image.processing.localthickness.LocalThickness;
import boa.image.processing.split_merge.SplitAndMerge;
import boa.image.processing.split_merge.SplitAndMergeBacteriaShape;
import boa.image.processing.split_merge.SplitAndMergeEdge;
import boa.image.processing.split_merge.SplitAndMergeHessian;
import boa.measurement.BasicMeasurements;
import boa.plugins.SegmenterSplitAndMerge;
import boa.plugins.plugins.thresholders.ConstantValue;
import boa.plugins.plugins.thresholders.IJAutoThresholder;
import boa.utils.ArrayUtil;
import boa.utils.HashMapGetCreate;
import ij.process.AutoThresholder;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 *
 * @author jollion
 */
public class BacteriaShape implements SegmenterSplitAndMerge {
    public boolean testMode = false;
    boolean sideBackground=true;
    @Override
    public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        // computing edge map
        if (testMode) ImageWindowManagerFactory.showImage(input);
        Image smoothed = Filters.median(input, null, Filters.getNeighborhood(3, input));
        Image sigma = Filters.applyFilter(smoothed, new ImageFloat("", input), new Filters.Sigma(), Filters.getNeighborhood(3, input)); // lower than 4
        if (testMode) ImageWindowManagerFactory.showImage(sigma);
        
        // image partition
        RegionPopulation pop = partitionImage(sigma, parent.getMask(), sideBackground);
        pop.smoothRegions(2, true, parent.getMask()); // regularize borders
        
        // thresholds
        Image medianValueMap = EdgeDetector.generateRegionValueMap(pop, input);
        double thld = IJAutoThresholder.runThresholder(medianValueMap, parent.getMask(), null, AutoThresholder.Method.Otsu, 0);
        double p=0.75;
        double backgroundThld = (1-p)*thld+(p)*ImageOperations.getMeanAndSigma(medianValueMap, parent.getMask(), v->v<=thld)[0];
        double foregroundThld = (1-p)*thld+(p)*ImageOperations.getMeanAndSigma(medianValueMap, parent.getMask(), v->v>=thld)[0];
        // TODO traitement particulier pour les 1 ou 2 side bck objects -> si au dessu de la valeur du seuil, soit mettre le seuil plus haut, soit ne pas les supprimer
        double thldDiff = (foregroundThld-backgroundThld)/2d;
        if (testMode) logger.debug("thld: {} back: {} fore: {}, diff: {}", thld, backgroundThld, foregroundThld, thldDiff);
        double thldS = IJAutoThresholder.runThresholder(sigma, parent.getMask(), null, AutoThresholder.Method.Otsu, 0);
        double mergeThld1 = thldS/foregroundThld;
        double mergeThld2 = thldS/thld;
        if (testMode) logger.debug("thldS: {}, thld1: {}, thld2: {}", thldS, mergeThld1, mergeThld2);
        if (testMode) ImageWindowManagerFactory.showImage(medianValueMap.setName("after partition"));
        
        
        if (testMode) {
            SplitAndMergeEdge sam = new SplitAndMergeEdge(sigma, input, mergeThld1).setNormedEdgeValueFunction(0.5, thldDiff);
            Image interMap = sam.drawInterfaceValues(pop);
            logger.debug("thld on interfaces: {}", IJAutoThresholder.runThresholder(interMap, new ThresholdMask(interMap, Double.MIN_VALUE, true, true), null, AutoThresholder.Method.Otsu, 0));
        }
        
        // merge background and forground values respectively. remain intermediate region either background or foreground
        HashMapGetCreate<Region, Double> median = new HashMapGetCreate<>(r->BasicMeasurements.getQuantileValue(r, input, false, 0.5)[0]);
        pop.mergeWithConnectedWithinSubset(o->median.getAndCreateIfNecessary(o)<=backgroundThld);
        pop.mergeWithConnectedWithinSubset(o->median.getAndCreateIfNecessary(o)>=foregroundThld);
        median.clear();
        if (testMode) {
            ImageWindowManagerFactory.showImage(pop.getLabelMap().duplicate("labels after merge back and fore"));
            ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, input).setName("after merge back and fore"));
            
        }
        
        // merge according to quantile value of sigma @ border between regions // also limit by difference of value
        SplitAndMergeEdge sam = new SplitAndMergeEdge(sigma, input, mergeThld1).setNormedEdgeValueFunction(0.5,  thldDiff);
        sam.compareMethod = sam.compareByMedianIntensity(true);
        sam.setTestMode(testMode);
        if (sam.testMode) ImageWindowManagerFactory.showImage(sam.drawInterfaceValues(pop));
        sam.merge(pop, 0);
        if (sam.testMode) ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, input).setName("after merge by edge"));
        
        if (sideBackground) {
            // erase objects on side (forced during partitioning -> context of microchannels)
            pop.filter(new ContactBorder(input.getSizeY()/2, input, ContactBorder.Border.Xl)); 
            pop.filter(new ContactBorder(input.getSizeY()/2, input, ContactBorder.Border.Xr)); 
        }
        EdgeDetector seg = new EdgeDetector().setThrehsoldingMethod(1);
        seg.setThresholder(new ConstantValue(backgroundThld)).filterRegions(pop, input, parent.getMask());
        //seg.setIsDarkBackground(false).setThresholder(new ConstantValue(0.05)).filterRegions(pop, sigma, parent.getMask());
        
        // merge by edge with higher threshold
        //sam =  new SplitAndMergeEdge(sigma, mergeThld2).setEdgeValueFunction(0.5, input, thldDiff);
        //sam.setTestMode(testMode);
        //sam.merge(pop, 0);
        
        if (false && testMode) {
            ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, input).setName("before merge by shape"));
            ImageWindowManagerFactory.showImage(pop.getLabelMap().duplicate("labels before merge by shape"));
        }
        // merge by shape: low curvature at both edges of interfaces -> in order to merge regions of low intensity within cells. Problem: merge with background regions possible at this stage
        SplitAndMergeBacteriaShape samShape = new SplitAndMergeBacteriaShape();
        samShape.curvaturePerCluster = false;
        samShape.useThicknessCriterion=false;
        samShape.curvCriterionOnBothSides=true;
        samShape.thresholdCurvSides=-0.03;
        samShape.curvatureScale=6;
        samShape.thresholdCurvMean=Double.NEGATIVE_INFINITY;
        samShape.compareMethod = samShape.compareBySize(true);
        //samShape.setTestMode(testMode);
        samShape.merge(pop, 0);
        if (testMode) ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, input).setName("after merge by shape"));
        
        // erase small or thin or low intensity regions 
        pop.filter(new RegionPopulation.Size().setMin(100));
        pop.filter(new RegionPopulation.LocalThickness(10));
        seg.setThresholder(new ConstantValue(thld)).filterRegions(pop, input, parent.getMask()); // was 0.5
        
        if (testMode) ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, input).setName("after delete by size and local thld"));
        
        // re-split & merge by shape (curvature & thickness criterion)
        samShape = new SplitAndMergeBacteriaShape();
        //samShape.setTestMode(testMode);
        pop=samShape.splitAndMerge(pop.getLabelMap(), 20, 0);

        // local threshold
        pop.localThreshold(smoothed, 2, true, true);
        
        if (testMode) ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, input).setName("after split & merge"));
        
        return pop;
    }
    private static RegionPopulation partitionImage(Image wsMap, ImageMask mask, boolean sideBackground) {
        int minSizePropagation = 5;
        ImageInteger seeds = Filters.localExtrema(wsMap, null, false, mask, Filters.getNeighborhood(1.5, 1.5, wsMap)); 
        if (sideBackground) {// add  seeds on sides to partition image properly // todo necessary ? parameter?
            int[] xs = new int[]{0, wsMap.getSizeX()-1};
            for (int y = 0; y<wsMap.getSizeY(); ++y) {
                for (int x : xs) {
                    if (mask.insideMask(x, y, 0)) seeds.setPixel(x, y, 0, 1);
                }
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
