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
package boa.plugins.legacy;

import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.RegionPopulation.Border;
import boa.data_structure.RegionPopulation.ContactBorder;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectProcessing;
import boa.data_structure.Voxel;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.image.BlankMask;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.ImageMask;
import boa.image.ImageProperties;
import boa.image.ThresholdMask;
import boa.image.processing.FillHoles2D;
import boa.image.processing.Filters;
import boa.image.processing.ImageFeatures;
import boa.image.processing.ImageOperations;
import boa.image.processing.WatershedTransform;
import boa.image.processing.clustering.RegionCluster;
import boa.image.processing.localthickness.LocalThickness;
import boa.image.processing.split_merge.SplitAndMerge;
import boa.image.processing.split_merge.SplitAndMergeBacteriaShape;
import boa.image.processing.split_merge.SplitAndMergeEdge;
import boa.image.processing.split_merge.SplitAndMergeHessian;
import boa.measurement.BasicMeasurements;
import boa.measurement.GeometricalMeasurements;
import boa.plugins.ObjectSplitter;
import boa.plugins.SegmenterSplitAndMerge;
import boa.plugins.plugins.thresholders.ConstantValue;
import boa.plugins.plugins.thresholders.IJAutoThresholder;
import boa.utils.ArrayUtil;
import boa.utils.HashMapGetCreate;
import ij.process.AutoThresholder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import boa.plugins.SimpleThresholder;
import boa.plugins.Thresholder;
import boa.plugins.plugins.segmenters.EdgeDetector;
import boa.plugins.plugins.thresholders.LocalContrastThresholder;
import boa.plugins.plugins.trackers.ObjectIdxTracker;
import java.util.Set;

/**
 *
 * @author jollion
 */
public class BacteriaShape implements SegmenterSplitAndMerge, ObjectSplitter {
    public boolean testMode = false;
    PluginParameter<SimpleThresholder> thresholder = new PluginParameter<>("Threshold", SimpleThresholder.class, new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu) , false).setToolTipText("Main Intensity threshold. Threshold is computed on the partitionned image filled with median value at ecah region"); 
    protected NumberParameter medianRadius = new BoundedNumberParameter("Median Radius", 1, 3, 1, null).setToolTipText("Radius for median filtering in pixels: edge-preserving high-frequency noise removal");
    protected NumberParameter sigmaRadius = new BoundedNumberParameter("Sigma Radius", 1, 3, 1, null).setToolTipText("Radius for Sigma filtering: used to generate edge map for watershed partitioning");
    protected NumberParameter thresholdBackground = new BoundedNumberParameter("Background Threshold", 3, 0.2, 0, 0.75).setToolTipText("Parameter defining background threshold. Under this value region are considered as background. Background threshold is defined as the ponderated mean between : mean under main threshold AND main threshold");
    protected NumberParameter thresholdForeground = new BoundedNumberParameter("Foreground Threshold", 3, 0.5, 0.25, 1).setToolTipText("Parameter defining foreground threshold. Over this value region are considered as foreground. Foreground is defined the same way as background is, except the mean is over main threshold");
    protected NumberParameter thresholdDifference = new BoundedNumberParameter("Difference Threshold", 3, 0.8, 0.3, 1).setToolTipText("When merging partitionned map, the difference between the median value of 2 region is limited. The threshold is defined as this parameter * (foreground threshold - background threshold) ");
    Parameter[] parameters = new Parameter[]{thresholder, medianRadius, sigmaRadius, thresholdBackground, thresholdForeground, thresholdDifference};
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
        Image medianValueMap = EdgeDetector.generateRegionValueMap(pop, input);
        if (testMode) ImageWindowManagerFactory.showImage(medianValueMap.setName("after partition"));
        
        // thresholds
        if (Double.isNaN(threshold)) threshold = this.thresholder.instanciatePlugin().runSimpleThresholder(medianValueMap, parent.getMask());
        List<Region> sideBackObjects = sideBackground? getSideBackgroundRegions(pop):Collections.EMPTY_LIST;
        if (!sideBackObjects.isEmpty()) { //check that thld is higher thant side objects
            for (Region r : sideBackObjects) {
                Voxel v = r.getVoxels().iterator().next();
                double med = medianValueMap.getPixel(v.x, v.y, v.z);
                if (threshold<=med) threshold = med*1.1;
            }
        }
        double thld = threshold;
        double pB=thresholdBackground.getValue().doubleValue();
        double pF=thresholdForeground.getValue().doubleValue();
        double backgroundThld = (pB)*thld+(1-pB)*ImageOperations.getMeanAndSigma(medianValueMap, parent.getMask(), v->v<=thld)[0];
        double foregroundThld = (1-pF)*thld+(pF)*ImageOperations.getMeanAndSigma(medianValueMap, parent.getMask(), v->v>=thld)[0];
        double thldDiff = thresholdDifference.getValue().doubleValue()*(foregroundThld-backgroundThld);
        if (testMode) logger.debug("thld: {} back: {} fore: {}, diff: {}", thld, backgroundThld, foregroundThld, thldDiff);
        double thldS = IJAutoThresholder.runThresholder(sigma, parent.getMask(), null, AutoThresholder.Method.Otsu, 0);
        double mergeThld1 = thldS/foregroundThld;
        double mergeThld2 = thldS/thld;
        if (testMode) logger.debug("thldS: {}, thld1: {}, thld2: {}", thldS, mergeThld1, mergeThld2);

        /*if (testMode) {
            SplitAndMergeEdge sam = new SplitAndMergeEdge(sigma, input, mergeThld1).setNormedEdgeValueFunction(0.5, thldDiff);
            Image interMap = sam.drawInterfaceValues(pop);
            logger.debug("thld on interfaces: {}", IJAutoThresholder.runThresholder(interMap, new ThresholdMask(interMap, Double.MIN_VALUE, true, true), null, AutoThresholder.Method.Otsu, 0));
        }*/
        
        // merge background and forground values respectively. remain intermediate region either background or foreground
        FilterSideBackground removeBackgroundRegions = new FilterSideBackground(0.2, 0.75, input); // was 0.33
        HashMapGetCreate<Region, Double> median = new HashMapGetCreate<>(r->BasicMeasurements.getQuantileValue(r, input, 0.5)[0]);
        pop.mergeWithConnectedWithinSubset(o->median.getAndCreateIfNecessary(o)<=backgroundThld);
        pop.mergeWithConnectedWithinSubset(o->median.getAndCreateIfNecessary(o)>=foregroundThld && removeBackgroundRegions.keepObject(o));
        median.clear();
        if (testMode) {
            ImageWindowManagerFactory.showImage(pop.getLabelMap().duplicate("labels after merge back and fore"));
            ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, input).setName("after merge back and fore"));
            
        }
        
        // merge according to quantile value of sigma @ border between regions // also limit by difference of median values // also forbid fusion between foreground ans background regions
        SplitAndMergeEdge sam = new SplitAndMergeEdge(sigma, input, mergeThld2, true);
        sam.compareMethod = sam.compareByMedianIntensity(true);
        
        sam.setTestMode(testMode);
        
        //if (testMode) ImageWindowManagerFactory.showImage(pop.duplicate().filter(removeBackgroundRegions).getLabelMap().setName("remove back objects"));
        sam.forbidFusionForegroundBackground(r-> !removeBackgroundRegions.keepObject(r), r->true); // was r->sam.getMedianValues().getAndCreateIfNecessary(r)>=thld
        //sam.addForbidByMedianDifference(thldDiff);
        if (sam.testMode) ImageWindowManagerFactory.showImage(sam.drawInterfaceValues(pop));
        sam.merge(pop, 0);
        if (sam.testMode) ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, input).setName("after merge by edge"));
        
        if (sideBackground) {
            // erase objects on side (forced during partitioning -> context of microchannels)
            pop.filter(new ContactBorder(input.sizeY()/2, input, Border.Xl)); 
            pop.filter(new ContactBorder(input.sizeY()/2, input, Border.Xr)); 
        }
        pop.filter(removeBackgroundRegions);
        
        EdgeDetector seg = new EdgeDetector().setThrehsoldingMethod(1);
        seg.setThresholder(new ConstantValue(backgroundThld)).filterRegions(pop, input, parent.getMask());
        //seg.setIsDarkBackground(false).setThresholder(new ConstantValue(0.05)).filterRegions(pop, sigma, parent.getMask());
        
        if (testMode) {
            ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, input).setName("before merge by shape"));
            ImageWindowManagerFactory.showImage(pop.getLabelMap().duplicate("labels before merge by shape"));
        }
        // merge by shape: elminate remaining background region that are not aligned with foreground. low curvature at both edges of interfaces -> in order to merge regions of low intensity within cells. Problem: merge with background regions possible at this stage
        SplitAndMergeBacteriaShape samShape = new SplitAndMergeBacteriaShape(input);
        samShape.curvaturePerCluster = false; // curvature is computed at each interface, not by cluster because they may contain background regions
        samShape.useThicknessCriterion=false;
        samShape.curvCriterionOnBothSides=true;
        samShape.thresholdCurvSides=-0.03;
        samShape.curvatureScale=5;
        samShape.thresholdCurvMean=Double.NEGATIVE_INFINITY;
        samShape.compareMethod = samShape.compareBySize(true);
        //samShape.compareMethod = samShape.compareByMedianIntensity(true);
        //samShape.setTestMode(testMode);
        samShape.merge(pop, 0);
        if (testMode) ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, input).setName("after merge by shape"));
        
        // erase small or thin or low intensity regions 
        pop.filter(new RegionPopulation.Size().setMin(100));
        pop.filter(new RegionPopulation.LocalThickness(10));
        seg.setThresholder(new ConstantValue(backgroundThld)).filterRegions(pop, input, parent.getMask()); // was 0.5
        FillHoles2D.fillHoles(pop);
        if (testMode) ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, input).setName("after delete by size and local thld"));
        
        // re-split & merge by shape (curvature & thickness criterion)
        samShape = new SplitAndMergeBacteriaShape(input);
        samShape.setTestMode(testMode);
        pop=samShape.splitAndMerge(pop.getLabelMap(), 20, 0);
        
        // local threshold
        pop.localThreshold(smoothed, 2, true, false);
        pop.filter(new RegionPopulation.Size().setMin(100)); // remove small objects created by localthreshold
        
        if (testMode) ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, input).setName("after split & merge"));
        pop.sortBySpatialOrder(ObjectIdxTracker.IndexingOrder.YXZ);
        return pop;
    }
    private static RegionPopulation partitionImage(Image wsMap, ImageMask mask, boolean sideBackground) {
        int minSizePropagation = 5;
        ImageInteger seeds = Filters.localExtrema(wsMap, null, false, mask, Filters.getNeighborhood(sideBackground?1.5:1, wsMap)); 
        if (sideBackground) {// add  seeds on sides to partition image properly // todo necessary ? parameter?
            int[] xs = new int[]{0, wsMap.sizeX()-1};
            for (int y = 0; y<wsMap.sizeY(); ++y) {
                for (int x : xs) {
                    if (mask.insideMask(x, y, 0)) seeds.setPixel(x, y, 0, 1);
                }
            }
        }
        WatershedTransform.SizeFusionCriterion sfc = minSizePropagation>1 ? new WatershedTransform.SizeFusionCriterion(minSizePropagation) : null;
        WatershedTransform wt = new WatershedTransform(wsMap, mask, Arrays.asList(ImageLabeller.labelImage(seeds)), false, null, sfc);
        wt.setLowConnectivity(false); // was false
        wt.run();
        return wt.getRegionPopulation();
    }
    
    public static List<Region> getSideBackgroundRegions(RegionPopulation pop) {
        ContactBorder left =  new ContactBorder(0, pop.getImageProperties(), Border.Xl);
        ContactBorder right =  new ContactBorder(0, pop.getImageProperties(), Border.Xr);
        double thld = pop.getImageProperties().sizeY()/2d;
        return pop.getRegions().stream().filter(r-> left.getContact(r)>thld||right.getContact(r)>thld).collect(Collectors.toList());
    }

   


    
    public static class FilterSideBackground implements RegionPopulation.SimpleFilter {
        RegionPopulation.ContactBorder left, right;
        int sizeX, sizeY;
        double sizeXProportion, contactYProportion;
        public FilterSideBackground(double sizeXProportion, double contactYProportion, ImageProperties image) {
            this.sizeXProportion=sizeXProportion;
            this.contactYProportion=contactYProportion;
            left =  new ContactBorder(0, image, Border.Xl);
            right =  new ContactBorder(0, image, Border.Xr);
            sizeX = image.sizeX();
            sizeY=image.sizeY();
        }
        @Override
        public boolean keepObject(Region object) {
            double contactL = left.getContact(object);
            double contactR = right.getContact(object);
            double contact = Math.max(contactL, contactR);
            if (contact>sizeY/2) return false;
            double thickX = GeometricalMeasurements.meanThicknessX(object);
            if (thickX>=sizeXProportion*sizeX) return true;
            double thickY = GeometricalMeasurements.meanThicknessY(object);
            return contact<= thickY * contactYProportion;
        }
        
    }
    
    
    @Override
    public double split(StructureObject parent, int structureIdx, Region o, List<Region> result) {
        Image input = parent.getPreFilteredImage(structureIdx);
        SplitAndMergeBacteriaShape sam = new SplitAndMergeBacteriaShape(input);
        sam.ignoreEndOfChannelRegionWhenMerginSmallRegions = false;
        sam.testMode=testMode;
        RegionPopulation pop = sam.splitAndMerge(o.getMaskAsImageInteger(), 50, 2);
        if (pop.getRegions().size()!=2) return Double.POSITIVE_INFINITY;
        result.addAll(pop.getRegions());
        RegionCluster<SplitAndMergeBacteriaShape.InterfaceLocalShape> c = new RegionCluster(pop, false, true, sam.getFactory());
        SplitAndMergeBacteriaShape.InterfaceLocalShape i = c.getAllInterfaces().iterator().next();
        i.updateInterface();
        return getCost(i.getCurvatureValue(), sam.thresholdCurvMean, false);
    }

    @Override
    public double computeMergeCost(StructureObject parent, int structureIdx, List<Region> objects) {
        Image input = parent.getPreFilteredImage(structureIdx);
        SplitAndMergeBacteriaShape sam = new SplitAndMergeBacteriaShape(input);
        sam.ignoreEndOfChannelRegionWhenMerginSmallRegions = false;
        sam.testMode=testMode;
        RegionPopulation mergePop = new RegionPopulation(objects, new BlankMask(input).resetOffset());
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
    public RegionPopulation splitObject(StructureObject parent, int structureIdx, Region object) {
        Image input = parent.getPreFilteredImage(structureIdx);
        SplitAndMergeBacteriaShape sam = new SplitAndMergeBacteriaShape(input);
        sam.testMode=testMode;
        sam.ignoreEndOfChannelRegionWhenMerginSmallRegions = false;
        RegionPopulation pop = sam.splitAndMerge(object.getMaskAsImageInteger(), 50, 2);
        pop.translate(object.getBounds(), true);
        return pop;
    }
    
    @Override
    public void setSplitVerboseMode(boolean verbose) {
        testMode=verbose;
    }
    
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    // OVerridable Threshold interface
    double threshold = Double.NaN;

    public void setThresholdValue(double threshold) {
        this.threshold = threshold;
    }


    public Image getImageForThresholdComputation(Image input, int structureIdx, StructureObjectProcessing parent) {
        return input; // should partition image and return the median value map?
    }
    
}
