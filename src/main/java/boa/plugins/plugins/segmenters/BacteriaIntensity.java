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

import boa.plugins.legacy.BacteriaTrans;
import boa.configuration.parameters.BooleanParameter;
import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageDisplayer;
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

/**
 *
 * @author jollion
 */
public class BacteriaIntensity implements SegmenterSplitAndMerge, OverridableThreshold, ManualSegmenter, ObjectSplitter, ParameterSetup, ToolTip {
    public static boolean verbose = false;
    public boolean testMode = false;
    
    // configuration-related attributes
    PluginParameter<SimpleThresholder> threhsolder = new PluginParameter<>("Local Threshold", SimpleThresholder.class, new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu), false);
    NumberParameter splitThreshold = new BoundedNumberParameter("Split Threshold", 4, 0.3, 0, 1).setToolTipText("Lower value splits more. At step 2) regions are merge if sum(hessian)|interface / sum(raw intensity)|interface < (this parameter)"); // TODO was 0.12 before change of scale (hess *= sqrt(2pi)-> *2.5 
    NumberParameter localThresholdFactor = new BoundedNumberParameter("Local Threshold Factor", 2, 1.25, 1, null).setToolTipText("Factor defining the local threshold. T = median value - (inter-quartile) * (this factor). Lower value of this factor will yield in smaller cells");
    NumberParameter minSize = new BoundedNumberParameter("Minimum size", 0, 100, 50, null).setToolTipText("Minimum Object Size in voxels");
    NumberParameter minSizePropagation = new BoundedNumberParameter("Minimum size (propagation)", 0, 50, 1, null).setToolTipText("Minimal size of region at watershed partitioning @ step 2)");
    NumberParameter smoothScale = new BoundedNumberParameter("Smooth scale", 1, 2, 0, 5).setToolTipText("Scale (pixels) for gaussian filtering for the local thresholding step");
    NumberParameter hessianScale = new BoundedNumberParameter("Hessian scale", 1, 4, 1, 6);
    Parameter[] parameters = new Parameter[]{splitThreshold, localThresholdFactor, minSize, smoothScale, hessianScale};
    private final String toolTip = "<html>Intensity-based 2D segmentation <br />"
            + "1) Foreground is detected using the plugin EdgeDetector using Median 3 + Sigma 3 as watershed map & the method secondary map using hessian max as secondary map <br />"
            + "2) Forground region is split by applying a watershed transform on the maximal hessian eigen value, regions are then merged, using a criterion described in \"Split Threshold\" parameter<br />"
            + "3) A local threshold is applied to each region. Mostly because inter-forground regions may be segmented in step 1). Threshold is set as described in \"Local Threshold Factor\" parameter. Propagating from contour voxels, all voxels with value on the smoothed image (\"Smooth scale\" parameter) under the local threshold is removed </html>";
    
    @Override
    public String getToolTipText() {return toolTip;}
    
    //segmentation-related attributes (kept for split and merge methods)
    SplitAndMergeHessian splitAndMerge;
    Image smoothed;
    double threshold = Double.NaN;
    
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
    protected SplitAndMergeHessian initializeSplitAndMerge(Image input) {
        SplitAndMergeHessian res= new SplitAndMergeHessian(input, splitThreshold.getValue().doubleValue(), hessianScale.getValue().doubleValue());
        res.setTestMode(testMode);
        return res;
    }
    protected Image getSmoothed(Image input) {
        if (smoothed==null) {
            if (smoothScale.getValue().doubleValue()>=1) smoothed = ImageFeatures.gaussianSmooth(input, smoothScale.getValue().doubleValue(), false);
            // median?
        }
        return smoothed;
    }
    
    protected EdgeDetector initEdgeDetector() {
        EdgeDetector seg = new EdgeDetector().setIsDarkBackground(true); // keep defaults parameters ? 
        seg.setTestMode(testMode);
        //seg.setPreFilters(new ImageFeature().setFeature(ImageFeature.Feature.GRAD).setScale(2)); // min = 1.5
        seg.setPreFilters(new ImageFeature().setFeature(ImageFeature.Feature.StructureMax).setScale(1.5).setSmoothScale(2)); // min scale = 1 (noisy signal:1.5), max = 2 min smooth scale = 1.5 (noisy / out of focus: 2)
        //seg.setPreFilters(new Sigma(3).setMedianRadius(3));
        //seg.setSecondaryThresholdMap(splitAndMerge.getHessian()); // not efficient when hyperfluo cells but not saturated..
        if (Double.isNaN(threshold)) seg.setThrehsoldingMethod(1).setThresholder(threhsolder.instanciatePlugin());
        else seg.setThrehsoldingMethod(1).setThresholder(new ConstantValue(threshold));
        
        //seg.setThrehsoldingMethod(0).setThresholder(new BackgroundThresholder(3, 3, 2).setStartingValue(new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu))); // useless if secondary map
        //seg.setWsPriorityMap(getSmoothed(input));
        return seg;
    }
    @Override public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        splitAndMerge = initializeSplitAndMerge(input);
        EdgeDetector seg = initEdgeDetector();
        RegionPopulation splitPop = seg.runSegmenter(input, structureIdx, parent);
        RegionPopulation res = splitAndMerge.splitAndMerge(splitPop.getLabelMap(), minSizePropagation.getValue().intValue(), 0);
        res.localThreshold(getSmoothed(input), localThresholdFactor.getValue().doubleValue(), true, true);
        if (testMode) {
            ImageWindowManagerFactory.showImage(getSmoothed(input).setName("smoothed map for local threshold"));
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
        return res;
    }
    
    
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }

    @Override public double split(Image input, Region o, List<Region> result) {
        RegionPopulation pop =  splitObject(input, o); // init processing variables
        pop.translate(o.getBounds().duplicate().reverseOffset(), false);
        if (pop.getObjects().size()<=1) return Double.POSITIVE_INFINITY;
        else {
            if (pop.getObjects().size()>2) pop.mergeWithConnected(pop.getObjects().subList(2, pop.getObjects().size()));
            Region o1 = pop.getObjects().get(0);
            Region o2 = pop.getObjects().get(1);
            result.add(o1);
            result.add(o2);
            SplitAndMergeHessian.Interface inter = getInterface(o1, o2);
            double cost = BacteriaTrans.getCost(inter.value, splitAndMerge.splitThresholdValue, true);
            pop.translate(o.getBounds(), true);
            return cost;
        }
        
    }

    @Override public double computeMergeCost(Image input, List<Region> objects) {
        if (objects.isEmpty() || objects.size()==1) return 0;
        RegionPopulation mergePop = new RegionPopulation(objects, input, false);
        splitAndMerge = this.initializeSplitAndMerge(input);
        RegionCluster c = new RegionCluster(mergePop, false, true, splitAndMerge.getFactory());
        List<Set<Region>> clusters = c.getClusters();
        double maxCost = Double.NEGATIVE_INFINITY;
        //logger.debug("compute merge cost: {} objects in {} clusters", objects.size(), clusters.size());
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
        return BacteriaTrans.getCost(maxCost, splitAndMerge.splitThresholdValue, false);
        
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
    
    
    // object splitter interface
    boolean splitVerbose;
    @Override public void setSplitVerboseMode(boolean verbose) {
        this.splitVerbose=verbose;
    }
    
    @Override public RegionPopulation splitObject(Image input, Region object) {
        if (!input.sameSize(object.getMask())) {
            input = input.crop(object.getBounds());
            //mask = mask.crop(input.getBoundingBox()); // problem with crop & offsets when bb is larger & has an offset
        }
        // avoid border effects: dilate image
        int ext = (int)this.hessianScale.getValue().doubleValue()+1;
        BoundingBox extent = new BoundingBox(-ext, ext, -ext, ext, 0, 0);
       
        Image inExt = input.extend(extent);
        ImageInteger maskExt = object.getMask().extend(extent);
        splitAndMerge = initializeSplitAndMerge(inExt);
        splitAndMerge.setTestMode(splitVerbose);
        // ici -> bug avec offsets? 
        //logger.debug("in off: {}, object off: {}, inExt off: {}, maskExt off: {}", input.getBoundingBox(), object.getMask().getBoundingBox(), inExt.getBoundingBox(), maskExt.getBoundingBox());
        RegionPopulation res = splitAndMerge.splitAndMerge(maskExt, minSizePropagation.getValue().intValue(), 2);
        extent = new BoundingBox(ext, -ext, ext, -ext, 0, 0);
        ImageInteger labels = res.getLabelMap().extend(extent);
        RegionPopulation pop= new RegionPopulation(labels, true);
        pop.translate(object.getBounds(), true);
        return pop;
    }

    
    // manual correction implementations
    private boolean verboseManualSeg;
    @Override public void setManualSegmentationVerboseMode(boolean verbose) {
        this.verboseManualSeg=verbose;
    }

    @Override public RegionPopulation manualSegment(Image input, StructureObject parent, ImageMask segmentationMask, int structureIdx, List<int[]> seedsXYZ) {
        SplitAndMergeHessian splitAndMerge=initializeSplitAndMerge(input);
        List<Region> seedObjects = RegionFactory.createSeedObjectsFromSeeds(seedsXYZ, input.getSizeZ()==1, input.getScaleXY(), input.getScaleZ());
        EdgeDetector seg = initEdgeDetector();
        RegionPopulation pop = seg.run(input, segmentationMask);
        pop = splitAndMerge.merge(pop, 0);
        pop.filter(o->{
            for(Region so : seedObjects ) if (o.intersect(so)) return true;
            return false;
        });
        pop.localThreshold(getSmoothed(input), localThresholdFactor.getValue().doubleValue(), true, true);
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
