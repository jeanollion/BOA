/*
 * Copyright (C) 2015 jollion
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

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectProcessing;
import boa.data_structure.StructureObjectUtils;
import boa.data_structure.Voxel;
import ij.process.AutoThresholder;
import boa.image.BlankMask;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.image.processing.ImageOperations;
import boa.image.processing.RegionFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import boa.plugins.ManualSegmenter;
import boa.plugins.ObjectSplitter;
import boa.plugins.ParameterSetup;
import boa.plugins.Segmenter;
import boa.plugins.SegmenterSplitAndMerge;
import boa.plugins.plugins.pre_filters.ImageFeature;
import boa.plugins.plugins.thresholders.BackgroundThresholder;
import boa.plugins.plugins.thresholders.ConstantValue;
import boa.plugins.plugins.thresholders.IJAutoThresholder;
import boa.image.processing.ImageFeatures;
import boa.image.processing.split_merge.SplitAndMergeHessian;
import boa.utils.Utils;
import boa.image.processing.clustering.RegionCluster;
import boa.plugins.OverridableThreshold;
import boa.plugins.SimpleThresholder;
import boa.plugins.Thresholder;
import boa.plugins.ToolTip;
import boa.plugins.plugins.pre_filters.Median;
import boa.plugins.plugins.pre_filters.Sigma;
import boa.plugins.plugins.thresholders.CompareThresholds;
import boa.plugins.plugins.trackers.ObjectIdxTracker;

/**
 *
 * @author jollion
 */
public class BacteriaIntensity implements SegmenterSplitAndMerge, OverridableThreshold, ManualSegmenter, ObjectSplitter, ParameterSetup, ToolTip {
    public static boolean verbose = false;
    public boolean testMode = false;
    protected double threshold=Double.NaN;
    protected double minThld = 0;
    // configuration-related attributes
    PluginParameter<SimpleThresholder> threhsolder = new PluginParameter<>("Local Threshold", SimpleThresholder.class, new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu), false);
    NumberParameter splitThreshold = new BoundedNumberParameter("Split Threshold", 4, 0.3, 0, null).setToolTipText("Lower value splits more. At step 2) regions are merge if sum(hessian)|interface / sum(raw intensity)|interface < (this parameter)"); // TODO was 0.12 before change of scale (hess *= sqrt(2pi)-> *2.5 
    NumberParameter localThresholdFactor = new BoundedNumberParameter("Local Threshold Factor", 2, 1.25, 0, null).setToolTipText("Factor defining the local threshold.  Lower value of this factor will yield in smaller cells. T = median value - (inter-quartile) * (this factor).");
    NumberParameter minSize = new BoundedNumberParameter("Minimum size", 0, 100, 50, null).setToolTipText("Minimum Object Size in voxels");
    NumberParameter minSizePropagation = new BoundedNumberParameter("Minimum size (propagation)", 0, 50, 1, null).setToolTipText("Minimal size of region at watershed partitioning @ step 2)");
    NumberParameter smoothScale = new BoundedNumberParameter("Smooth scale", 1, 2, 0, 5).setToolTipText("Scale (pixels) for gaussian filtering for the local thresholding step");
    NumberParameter hessianScale = new BoundedNumberParameter("Hessian scale", 1, 4, 1, 6).setToolTipText("In pixels. Used in step 2). Lower value -> finner split, more sentitive to noise. Influences the value of split threshold parameter");
    Parameter[] parameters = new Parameter[]{splitThreshold, localThresholdFactor, minSize, smoothScale, hessianScale};
    private final String toolTip = "<html>Intensity-based 2D segmentation <br />"
            + "1) Foreground is detected using the plugin EdgeDetector using Median 3 + Sigma 3 as watershed map & the method secondary map using hessian max as secondary map <br />"
            + "2) Forground region is split by applying a watershed transform on the maximal hessian eigen value, regions are then merged, using a criterion described in \"Split Threshold\" parameter<br />"
            + "3) A local threshold is applied to each region. Mostly because inter-forground regions may be segmented in step 1). Threshold is set as described in \"Local Threshold Factor\" parameter. Propagating from contour voxels, all voxels with value on the smoothed image (\"Smooth scale\" parameter) under the local threshold is removed </html>";
    
    @Override
    public String getToolTipText() {return toolTip;}
    
    //segmentation-related attributes (kept for split and merge methods)
    SplitAndMergeHessian splitAndMerge;
    
    public BacteriaIntensity() {
        testMode = verbose;
    }
    public BacteriaIntensity setSplitThreshold(double splitThreshold) {
        this.splitThreshold.setValue(splitThreshold);
        return this;
    }
    public BacteriaIntensity setMinSize(int minSize) {
        this.minSize.setValue(minSize);
        return this;
    }
    public BacteriaIntensity setSmoothScale(double smoothScale) {
        this.smoothScale.setValue(smoothScale);
        return this;
    }
    public BacteriaIntensity setHessianScale(double hessianScale) {
        this.hessianScale.setValue(hessianScale);
        return this;
    }
    public BacteriaIntensity setLocalThresholdFactor(double localThresholdFactor) {
        this.localThresholdFactor.setValue(localThresholdFactor);
        return this;
    }
    @Override
    public String toString() {
        return "Bacteria Intensity: " + Utils.toStringArray(parameters);
    }   
    protected SplitAndMergeHessian initializeSplitAndMerge(Image input, ImageMask foregroundMask) {
        SplitAndMergeHessian res= new SplitAndMergeHessian(input, splitThreshold.getValue().doubleValue(), hessianScale.getValue().doubleValue());
        res.setTestMode(testMode);
        return res;
    }
    
    protected EdgeDetector initEdgeDetector() {
        EdgeDetector seg = new EdgeDetector().setIsDarkBackground(true); // keep defaults parameters ? 
        seg.setTestMode(testMode);
        //seg.setPreFilters(new ImageFeature().setFeature(ImageFeature.Feature.GRAD).setScale(2)); // min = 1.5
        seg.setPreFilters(new ImageFeature().setFeature(ImageFeature.Feature.StructureMax).setScale(1.5).setSmoothScale(2)); // min scale = 1 (noisy signal:1.5), max = 2 min smooth scale = 1.5 (noisy / out of focus: 2)
        //seg.setPreFilters(new Sigma(3).setMedianRadius(3));
        //seg.setSecondaryThresholdMap(splitAndMerge.getHessian()); // not efficient when hyperfluo cells but not saturated..
        if (Double.isNaN(threshold)) seg.setThrehsoldingMethod(1).setThresholder(Double.isNaN(minThld) ? threhsolder.instanciatePlugin(): new CompareThresholds(threhsolder.instanciatePlugin(), new ConstantValue(minThld), true)); // honors min value
        else seg.setThrehsoldingMethod(1).setThresholder(new ConstantValue(Math.max(threshold, minThld)));
        
        //seg.setThrehsoldingMethod(0).setThresholder(new BackgroundThresholder(3, 3, 2).setStartingValue(new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu))); // useless if secondary map
        //seg.setWsPriorityMap(getSmoothed(input));
        return seg;
    }
    @Override public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        EdgeDetector seg = initEdgeDetector();
        RegionPopulation splitPop = seg.runSegmenter(input, structureIdx, parent);
        splitPop.smoothRegions(1, true, parent.getMask());
        splitAndMerge = initializeSplitAndMerge(input, splitPop.getLabelMap());
        RegionPopulation res = splitAndMerge.splitAndMerge(splitPop.getLabelMap(), minSizePropagation.getValue().intValue(), 0);
        res = localThreshold(input, res, parent, structureIdx);
        if (testMode) {
            ImageWindowManagerFactory.showImage(res.getLabelMap().duplicate("After local threshold"));
        }
        res.filter(new RegionPopulation.Thickness().setX(2).setY(2)); // remove thin objects
        res.filter(new RegionPopulation.Size().setMin(minSize.getValue().intValue())); // remove small objects
        
        if (testParameter!=null) {
            logger.debug("testParameter: {}", testParameter);
            if (splitThreshold.getName().equals(testParameter)) {
                Image hess = splitAndMerge.getHessian().duplicate("Split map");
                hess = ImageOperations.divide(hess, input, null);
                ImageWindowManagerFactory.showImage(res.getLabelMap().setName("Segmentation with splitThreshold: "+splitThreshold.getValue().doubleValue()));
                ImageOperations.trim(hess, res.getLabelMap(), hess);
                ImageWindowManagerFactory.showImage(hess);
            }
        }
        res.sortBySpatialOrder(ObjectIdxTracker.IndexingOrder.YXZ);
        return res;
    }
    
    protected RegionPopulation localThreshold(Image input, RegionPopulation pop, StructureObjectProcessing parent, int structureIdx) {
        Image smooth = smoothScale.getValue().doubleValue()>=1 ? ImageFeatures.gaussianSmooth(input, smoothScale.getValue().doubleValue(), false):input;
        pop.localThreshold(smooth, localThresholdFactor.getValue().doubleValue(), true, true);
        return pop;
    }
    
    
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    // segmenter split and merge interface
    @Override public double split(StructureObject parent, int structureIdx, Region o, List<Region> result) {
        RegionPopulation pop =  splitObject(parent, structureIdx, o); // after this step pop is in same landmark as o
        if (pop.getRegions().size()<=1) return Double.POSITIVE_INFINITY;
        else {
            if (pop.getRegions().size()>2) pop.mergeWithConnected(pop.getRegions().subList(2, pop.getRegions().size()));
            Region o1 = pop.getRegions().get(0);
            Region o2 = pop.getRegions().get(1);
            result.add(o1);
            result.add(o2);
            SplitAndMergeHessian.Interface inter = getInterface(o1, o2);
            double cost = getCost(inter.value, splitAndMerge.splitThresholdValue, true);
            return cost;
        }
        
    }

    @Override public double computeMergeCost(StructureObject parent, int structureIdx, List<Region> objects) {
        if (objects.isEmpty() || objects.size()==1) return 0;
        Image input = parent.getPreFilteredImage(structureIdx);
        RegionPopulation mergePop = new RegionPopulation(objects, objects.get(0).isAbsoluteLandMark() ? input : new BlankMask(input).resetOffset());
        splitAndMerge = this.initializeSplitAndMerge(input, mergePop.getLabelMap());
        RegionCluster c = new RegionCluster(mergePop, false, true, splitAndMerge.getFactory());
        List<Set<Region>> clusters = c.getClusters();
        double maxCost = Double.NEGATIVE_INFINITY;
        if (clusters.size()>1) { // merge impossible : presence of disconnected objects
            if (testMode) logger.debug("merge impossible: {} disconnected clusters detected", clusters.size());
            return Double.POSITIVE_INFINITY;
        } 
        Set<SplitAndMergeHessian.Interface> allInterfaces = c.getInterfaces(clusters.get(0));
        for (SplitAndMergeHessian.Interface i : allInterfaces) {
            i.updateInterface();
            if (i.value>maxCost) maxCost = i.value;
        }

        if (maxCost==Double.MIN_VALUE) return Double.POSITIVE_INFINITY;
        return getCost(maxCost, splitAndMerge.splitThresholdValue, false);
        
    }
    
    private SplitAndMergeHessian.Interface getInterface(Region o1, Region o2) {
        o1.draw(splitAndMerge.getSplitMask(), o1.getLabel());
        o2.draw(splitAndMerge.getSplitMask(), o2.getLabel());
        SplitAndMergeHessian.Interface inter = RegionCluster.getInteface(o1, o2, splitAndMerge.tempSplitMask, splitAndMerge.getFactory());
        inter.updateInterface();
        o1.draw(splitAndMerge.getSplitMask(), 0);
        o2.draw(splitAndMerge.getSplitMask(), 0);
        return inter;
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
    
    // object splitter interface
    boolean splitVerbose;
    @Override public void setSplitVerboseMode(boolean verbose) {
        this.splitVerbose=verbose;
    }
    /**
     * Splits objects
     * @param parent
     * @param structureIdx
     * @param object
     * @return splitted objects in same landmark as {@param object}
     */
    @Override public RegionPopulation splitObject(StructureObject parent, int structureIdx, Region object) {
        Image input = parent.getPreFilteredImage(structureIdx);
        ImageInteger mask = object.isAbsoluteLandMark() ? object.getMask().cropWithOffset(input.getBoundingBox()) :object.getMask().cropWithOffset(input.getBoundingBox().translateToOrigin()); // extend mask to get the same size as the image
        splitAndMerge = initializeSplitAndMerge(input, mask);
        splitAndMerge.setTestMode(splitVerbose);
        RegionPopulation res = splitAndMerge.splitAndMerge(mask, minSizePropagation.getValue().intValue(), 2);
        res =  localThreshold(input, res, parent, structureIdx); 
        if (object.isAbsoluteLandMark()) res.translate(parent.getBounds(), true);
        return res;
    }

    
    // manual correction implementations
    private boolean verboseManualSeg;
    @Override public void setManualSegmentationVerboseMode(boolean verbose) {
        this.verboseManualSeg=verbose;
    }

    @Override public RegionPopulation manualSegment(Image input, StructureObject parent, ImageMask segmentationMask, int structureIdx, List<int[]> seedsXYZ) {
        
        List<Region> seedObjects = RegionFactory.createSeedObjectsFromSeeds(seedsXYZ, input.getSizeZ()==1, input.getScaleXY(), input.getScaleZ());
        EdgeDetector seg = initEdgeDetector();
        RegionPopulation pop = seg.run(input, segmentationMask);
        SplitAndMergeHessian splitAndMerge=initializeSplitAndMerge(input, pop.getLabelMap());
        pop = splitAndMerge.merge(pop, 0);
        pop.filter(o->{
            for(Region so : seedObjects ) if (o.intersect(so)) return true;
            return false;
        });
        localThreshold(input, pop, parent, structureIdx);
        //RegionPopulation pop =  WatershedTransform.watershed(splitAndMerge.getHessian(), segmentationMask, seedObjects, false, new WatershedTransform.ThresholdPropagation(getNormalizedHessian(input), this.manualSegPropagationHessianThreshold.getValue().doubleValue(), false), new WatershedTransform.SizeFusionCriterion(this.minSize.getValue().intValue()), false);
        
        if (verboseManualSeg) {
            Image seedMap = new ImageByte("seeds from: "+input.getName(), input);
            for (int[] seed : seedsXYZ) seedMap.setPixel(seed[0], seed[1], seed[2], 1);
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(seedMap);
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(splitAndMerge.getHessian());
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(pop.getLabelMap().setName("segmented from: "+input.getName()));
        }
        
        return pop;
    }
    
    // ParameterSetup Implementation
    
    @Override public boolean canBeTested(String p) {
        List canBeTested = new ArrayList(){{add(splitThreshold); }};
        return canBeTested.contains(p);
    }
    String testParameter;
    @Override public void setTestParameter(String p) {
        this.testParameter=p;
    }

    @Override
    public void setThresholdValue(double threshold) {
        this.threshold=threshold;
    }

    @Override
    public Image getImageForThresholdComputation(Image input, int structureIdx, StructureObjectProcessing parent) {
        return input;
    }

}
