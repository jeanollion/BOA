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
package boa.plugins.plugins.segmenters;

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.ConditionalParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.PreFilterSequence;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectProcessing;
import boa.data_structure.StructureObjectUtils;
import boa.data_structure.Voxel;
import ij.process.AutoThresholder;
import boa.image.BlankMask;
import boa.image.BoundingBox;
import boa.image.Histogram;
import boa.image.HistogramFactory;
import boa.image.HistogramFactory.BIN_SIZE_METHOD;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.image.SimpleBoundingBox;
import boa.image.processing.ImageOperations;
import boa.image.processing.RegionFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import boa.plugins.ManualSegmenter;
import boa.plugins.ObjectSplitter;
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
import boa.image.processing.split_merge.SplitAndMergeEdge;
import boa.image.processing.split_merge.SplitAndMergeRegionCriterion;
import boa.measurement.BasicMeasurements;
import static boa.plugins.Plugin.logger;
import boa.plugins.SimpleThresholder;
import boa.plugins.TestableProcessingPlugin;
import boa.plugins.Thresholder;
import boa.plugins.ThresholderHisto;
import boa.plugins.ToolTip;
import boa.plugins.TrackParametrizable;
import boa.plugins.plugins.pre_filters.Median;
import boa.plugins.plugins.pre_filters.Sigma;
import static boa.plugins.plugins.segmenters.EdgeDetector.valueFunction;
import boa.plugins.plugins.thresholders.BackgroundFit;
import boa.plugins.plugins.thresholders.CompareThresholds;
import boa.plugins.plugins.thresholders.ParentThresholder;
import boa.plugins.plugins.trackers.ObjectIdxTracker;
import boa.utils.ArrayUtil;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 *
 * @author jollion
 */
public class BacteriaIntensity  implements TrackParametrizable<BacteriaIntensityPhase>, SegmenterSplitAndMerge, ManualSegmenter, ObjectSplitter, ToolTip, TestableProcessingPlugin {
    public static boolean verbose = false;
    protected double threshold=Double.NaN;
    protected double minThld = Double.NaN;
    // configuration-related attributes
    PluginParameter<SimpleThresholder> localThrehsolder = new PluginParameter<>("Local Threshold", SimpleThresholder.class, new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu), false);
    PluginParameter<ThresholderHisto> globalThrehsolder = new PluginParameter<>("Global Threshold", ThresholderHisto.class, new BackgroundFit(15), false);
    PreFilterSequence watershedMap = new PreFilterSequence("Watershed Map").add(new ImageFeature().setFeature(ImageFeature.Feature.GAUSS).setScale(2), new Sigma(2).setMedianRadius(0)).setToolTipText("Filters used to define edge map used in first watershed step. <br />Max eigen value of Structure tensore is a good option<br />Median/Gaussian + Sigma is more suitable for noisy images (involve less derivation)<br />Gradient magnitude is another option");  // min scale = 1 (noisy signal:1.5), max = 2 min smooth scale = 1.5 (noisy / out of focus: 2) //new ImageFeature().setFeature(ImageFeature.Feature.StructureMax).setScale(1.5).setSmoothScale(2)
    NumberParameter splitThreshold = new BoundedNumberParameter("Split Threshold", 4, 0.3, 0, null).setToolTipText("Lower value splits more. At step 2) regions are merge if sum(hessian)|interface / sum(raw intensity)|interface < (this parameter)"); // TODO was 0.12 before change of scale (hess *= sqrt(2pi)-> *2.5 
    NumberParameter localThresholdFactor = new BoundedNumberParameter("Local Threshold Factor", 2, 1.25, 0, null).setToolTipText("Factor defining the local threshold.  Lower value of this factor will yield in smaller cells. T = median value - (inter-quartile) * (this factor).");
    NumberParameter minSize = new BoundedNumberParameter("Minimum size", 0, 100, 50, null).setToolTipText("Minimum Object Size in voxels");
    NumberParameter minSizePropagation = new BoundedNumberParameter("Minimum size (propagation)", 0, 50, 1, null).setToolTipText("Minimal size of region at watershed partitioning @ step 2)");
    NumberParameter smoothScale = new BoundedNumberParameter("Smooth scale", 1, 2, 0, 5).setToolTipText("Scale (pixels) for gaussian filtering for the local thresholding step");
    NumberParameter hessianScale = new BoundedNumberParameter("Hessian scale", 1, 4, 1, 6).setToolTipText("In pixels. Used in step 2). Lower value -> finner split, more sentitive to noise. Influences the value of split threshold parameter");
    ChoiceParameter thresholdMethod=  new ChoiceParameter("Threshold Method", new String[]{"Local Threshold", "Global Threshold"}, "Global Threshold", false);
    ConditionalParameter thldCond = new ConditionalParameter(thresholdMethod).setActionParameters("Local Threshold", localThrehsolder).setActionParameters("Global Threshold", globalThrehsolder);
    NumberParameter sigmaThldForVoidMC = new BoundedNumberParameter("Sigma/Mu thld for void channels", 3, 0.085, 0, null).setToolTipText("Parameter to look for void microchannels, at track pre-filter step: <br /> To assess if whole microchannel track is void: sigma / mu of raw images is computed on whole track, in the central line of each microchannel (1/3 of the width). If sigma / mu < this value, the whole track is considered to be void. <br />If the track is not void, a global otsu threshold is computed on the prefiltered signal. A channel is considered as void if its value of sigma / mu of raw signal is inferior to this threshold and is its mean value of prefiltered signal is superior to the global threshold");
    
    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{watershedMap, splitThreshold, localThresholdFactor, minSize, smoothScale, hessianScale, thldCond, sigmaThldForVoidMC};
    }
    private final String toolTip = "<b>Intensity-based 2D segmentation:</b>"
            + "<ol><li>Foreground is detected using the plugin EdgeDetector using Median 3 + Sigma 3 as watershed map & the method secondary map using hessian max as secondary map</li>"
            + "<li>Forground region is split by applying a watershed transform on the maximal hessian eigen value, regions are then merged, using a criterion described in \"Split Threshold\" parameter</li>"
            + "<li>A local threshold is applied to each region. Mostly because inter-forground regions may be segmented in step 1). Threshold is set as described in \"Local Threshold Factor\" parameter. <br /> "
            + "Propagating from contour voxels, all voxels with value on the smoothed image (\"Smooth scale\" parameter) under the local threshold is removed</li></ol>";
    
    @Override
    public String getToolTipText() {return toolTip;}
    
    //segmentation-related attributes (kept for split and merge methods)
    EdgeDetector edgeDetector;
    SplitAndMergeHessian splitAndMerge;
    StructureObjectProcessing currentParent; 
    
    public BacteriaIntensity() {

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
        return "Bacteria Intensity: " + Utils.toStringArray(getParameters());
    }   
    protected SplitAndMergeHessian initializeSplitAndMerge(StructureObjectProcessing parent, int structureIdx, ImageMask foregroundMask) {
        //if (edgeDetector==null) edgeDetector = initEdgeDetector(parent, structureIdx); //call from split/merge/manualseg
        SplitAndMergeHessian res= new SplitAndMergeHessian(parent.getPreFilteredImage(structureIdx), splitThreshold.getValue().doubleValue(), hessianScale.getValue().doubleValue(), globalBackgroundLevel);
        //res.setWatershedMap(parent.getPreFilteredImage(structureIdx), false).setSeedCreationMap(parent.getPreFilteredImage(structureIdx), true);  // better results when using hessian as watershed map -> takes into 
        if (stores!=null) res.setTestMode(TestableProcessingPlugin.getAddTestImageConsumer(stores, (StructureObject)parent));
        return res;
    }
    /**
     * Initialize the EdgeDetector that will create a parittion of the image and filter the regions
     * @param parent
     * @param structureIdx
     * @return 
     */
    protected EdgeDetector initEdgeDetector(StructureObjectProcessing parent, int structureIdx) {
        EdgeDetector seg = new EdgeDetector().setIsDarkBackground(true); // keep defaults parameters ? 
        seg.minSizePropagation.setValue(0);
        //if (stores!=null) seg.setTestMode(TestableProcessingPlugin.getAddTestImageConsumer(stores, (StructureObject)parent));
        //seg.setPreFilters(new ImageFeature().setFeature(ImageFeature.Feature.GRAD).setScale(2)); // min = 1.5
        seg.setPreFilters(watershedMap.get());
        //if (Double.isNaN(threshold)) seg.setThrehsoldingMethod(EdgeDetector.THLD_METHOD.VALUE_MAP).setThresholder(Double.isNaN(minThld) ? localThrehsolder.instanciatePlugin(): new CompareThresholds(localThrehsolder.instanciatePlugin(), new ConstantValue(minThld), true)); //local threshold + honors min value
        //else seg.setThrehsoldingMethod(EdgeDetector.THLD_METHOD.VALUE_MAP).setThresholder(new ConstantValue(Double.isNaN(minThld) ? threshold : Math.max(threshold, minThld))); // global threshold + honors min value
        seg.setThrehsoldingMethod(EdgeDetector.THLD_METHOD.NO_THRESHOLDING);
        return seg;
    }
    @Override public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        if (isVoid) return null;
        Consumer<Image> imageDisp = TestableProcessingPlugin.getAddTestImageConsumer(stores, (StructureObject)parent);
        edgeDetector = initEdgeDetector(parent, structureIdx);
        RegionPopulation splitPop = edgeDetector.runSegmenter(input, structureIdx, parent);
        splitPop.smoothRegions(1, true, parent.getMask());
        if (stores!=null) imageDisp.accept(edgeDetector.getWsMap(parent.getPreFilteredImage(structureIdx), parent.getMask()).setName("EdgeDetector EdgeMap"));
        if (stores!=null) imageDisp.accept(EdgeDetector.generateRegionValueMap(splitPop, parent.getPreFilteredImage(structureIdx)).setName("Values After EdgeDetector"));
        splitPop = filterRegionsAfterEdgeDetector(parent, structureIdx, splitPop);
        if (stores!=null) imageDisp.accept(EdgeDetector.generateRegionValueMap(splitPop, parent.getPreFilteredImage(structureIdx)).setName("Values After Filtering"));
        if (splitAndMerge==null || !parent.equals(currentParent)) {
            currentParent = parent;
            splitAndMerge = initializeSplitAndMerge(parent, structureIdx, parent.getMask());
        }
        RegionPopulation split = splitAndMerge.split(splitPop.getLabelMap(), minSizePropagation.getValue().intValue());
        if (stores!=null) {
            imageDisp.accept(splitAndMerge.getHessian());
            imageDisp.accept(EdgeDetector.generateRegionValueMap(split, input).setName("Values after split by hessian"));
        }
        split = filterRegionAfterSplitByHessian(parent, structureIdx, split);
        if (stores!=null)  imageDisp.accept(split.getLabelMap().duplicate("Labels before merge by Hessian"));
        RegionPopulation res = splitAndMerge.merge(split, 0);
        res = localThreshold(input, res, parent, structureIdx, false);
        if (stores!=null)  imageDisp.accept(res.getLabelMap().duplicate("After local threshold"));
        res.filter(new RegionPopulation.Thickness().setX(2).setY(2)); // remove thin objects
        res.filter(new RegionPopulation.Size().setMin(minSize.getValue().intValue())); // remove small objects
        
        res.sortBySpatialOrder(ObjectIdxTracker.IndexingOrder.YXZ);
        return res;
    }
    protected RegionPopulation filterRegionsAfterEdgeDetector(StructureObjectProcessing parent, int structureIdx, RegionPopulation pop) {
        // perform hysteresis thresholding : define foreground & backgroun + merge indeterminded regions to the closest neighbor in therms of intensity
        Map<Region, Double> values = pop.getRegions().stream().collect(Collectors.toMap(o->o, valueFunction(parent.getPreFilteredImage(structureIdx))));
        Consumer<Image> imageDisp = TestableProcessingPlugin.getAddTestImageConsumer(stores, (StructureObject)parent);
        if (Double.isNaN(threshold) || this.thresholdMethod.getSelectedIndex()==0) {
            threshold = this.localThrehsolder.instanciatePlugin().runSimpleThresholder(parent.getPreFilteredImage(structureIdx), parent.getMask());
        }
        double foreThld = threshold;
        //if (Double.isNaN(minThld) || (Double.isNaN(foreThld))) return pop;
        
        Set<Region> backgroundL = pop.getRegions().stream().filter(r->values.get(r)<=minThld).collect(Collectors.toSet());
        if (foreThld==minThld) { // simple thresholding
            pop.getRegions().removeAll(backgroundL);
            pop.relabel(true);
            return pop;
        }
        Set<Region> foregroundL = pop.getRegions().stream().filter(r->values.get(r)>=foreThld).collect(Collectors.toSet());
        if (foregroundL.isEmpty()) {
            pop.getRegions().clear();
            pop.relabel(true);
            return pop;
        }
        if (stores!=null) logger.debug("min thld: {} max thld: {}, background: {}, foreground: {}, unknown: {}", minThld, foreThld, backgroundL.size(), foregroundL.size(), pop.getRegions().size()-backgroundL.size()-foregroundL.size());
        if (pop.getRegions().size()>foregroundL.size()+backgroundL.size()) { // merge indetermined regions with either background or foreground
            pop.getRegions().removeAll(backgroundL);
            pop.getRegions().removeAll(foregroundL);
            pop.getRegions().addAll(0, backgroundL); // so that background region keep same instance when merged with indetermined region
            pop.getRegions().addAll(backgroundL.size(), foregroundL);
            pop.relabel(false);
            if (stores!=null) imageDisp.accept(pop.getLabelMap().duplicate("Labels Before Hysteresis"));
            
            SplitAndMergeEdge sm = new SplitAndMergeEdge(edgeDetector.getWsMap(parent.getPreFilteredImage(structureIdx), parent.getMask()), parent.getPreFilteredImage(structureIdx), Double.POSITIVE_INFINITY, false);
            sm.setInterfaceValue(i-> {
                if (i.getVoxels().isEmpty() || sm.isFusionForbidden(i)) {
                    return Double.NaN;
                } else {
                    int size = i.getVoxels().size()+i.getDuplicatedVoxels().size();
                    double val= ArrayUtil.quantile(Stream.concat(i.getVoxels().stream(), i.getDuplicatedVoxels().stream()).mapToDouble(v->sm.getWatershedMap().getPixel(v.x, v.y, v.z)).sorted(), size, 0.1);
                    double mean1 = BasicMeasurements.getMeanValue(i.getE1(), sm.getIntensityMap());
                    double mean2 = BasicMeasurements.getMeanValue(i.getE2(), sm.getIntensityMap());
                    double mean = (mean1 + mean2) / 2d;
                    //double sum = BasicMeasurements.getSum(i.getE1(), sm.getIntensityMap())+BasicMeasurements.getSum(i.getE2(), sm.getIntensityMap());
                    //double mean = sum /(double)(i.getE1().size()+i.getE2().size());
                    val= val/(mean-globalBackgroundLevel);
                    return val;
                }
            });
            sm.setTestMode(imageDisp);
            //SplitAndMergeRegionCriterion sm = new SplitAndMergeRegionCriterion(null, parent.getPreFilteredImage(structureIdx), -Double.MIN_VALUE, SplitAndMergeRegionCriterion.InterfaceValue.ABSOLUTE_DIFF_MEDIAN_BTWN_REGIONS);
            sm.addForbidFusion(i->{
                return backgroundL.contains(i.getE1()) && (foregroundL.contains(i.getE2()) || backgroundL.contains(i.getE2()))
                || foregroundL.contains(i.getE1()) && foregroundL.contains(i.getE2());
                    });
            sm.merge(pop, foregroundL.size()+backgroundL.size());
            if (stores!=null) imageDisp.accept(EdgeDetector.generateRegionValueMap(pop, parent.getPreFilteredImage(structureIdx)).setName("Values After Hysteresis"));
        }
        pop.getRegions().removeAll(backgroundL);
        pop.relabel(true);
        return pop;
    }
    // for extension purpose
    protected RegionPopulation filterRegionAfterSplitByHessian(StructureObjectProcessing parent, int structureIdx, RegionPopulation pop) {
        return pop;
    }
    
    protected RegionPopulation localThreshold(Image input, RegionPopulation pop, StructureObjectProcessing parent, int structureIdx, boolean callFromSplit) {
        Image smooth = smoothScale.getValue().doubleValue()>=1 ? ImageFeatures.gaussianSmooth(input, smoothScale.getValue().doubleValue(), false):input;
        pop.localThreshold(smooth, localThresholdFactor.getValue().doubleValue(), true, true);
        return pop;
    }
    
    
    

    public boolean does3D() {
        return true;
    }
    // segmenter split and merge interface
    @Override public double split(StructureObject parent, int structureIdx, Region o, List<Region> result) {
        result.clear();
        RegionPopulation pop =  splitObject(parent, structureIdx, o); // after this step pop is in same landmark as o
        if (pop.getRegions().size()<=1) return Double.POSITIVE_INFINITY;
        else {
            if (tempSplitMask==null) tempSplitMask = new ImageByte("split mask", parent.getMask());
            result.addAll(pop.getRegions());
            if (pop.getRegions().size()>2) return 0; //   objects could not be merged during step process means no contact (effect of local threshold)
            SplitAndMergeHessian.Interface inter = getInterface(result.get(0), result.get(1));
            //logger.debug("split @ {}-{}, inter size: {} value: {}/{}", parent, o.getLabel(), inter.getVoxels().size(), inter.value, splitAndMerge.splitThresholdValue);
            if (inter.getVoxels().size()<=1) return 0;
            double cost = getCost(inter.value, splitAndMerge.splitThresholdValue, true);
            return cost;
        }
        
    }

    @Override public double computeMergeCost(StructureObject parent, int structureIdx, List<Region> objects) {
        if (objects.isEmpty() || objects.size()==1) return 0;
        Image input = parent.getPreFilteredImage(structureIdx);
        RegionPopulation mergePop = new RegionPopulation(objects, objects.get(0).isAbsoluteLandMark() ? input : new BlankMask(input).resetOffset());
        mergePop.relabel(false); // ensure distinct labels , if not cluster cannot be found
        if (splitAndMerge==null || !parent.equals(currentParent)) {
            currentParent = parent;
            splitAndMerge = initializeSplitAndMerge(parent, structureIdx, parent.getMask());
        }
        RegionCluster c = new RegionCluster(mergePop, true, splitAndMerge.getFactory());
        List<Set<Region>> clusters = c.getClusters();
        if (clusters.size()>1) { // merge impossible : presence of disconnected objects
            if (stores!=null) logger.debug("merge impossible: {} disconnected clusters detected", clusters.size());
            return Double.POSITIVE_INFINITY;
        } 
        double maxCost = Double.NEGATIVE_INFINITY;
        Set<SplitAndMergeHessian.Interface> allInterfaces = c.getInterfaces(clusters.get(0));
        for (SplitAndMergeHessian.Interface i : allInterfaces) {
            i.updateInterface();
            if (i.value>maxCost) maxCost = i.value;
        }

        if (Double.isInfinite(maxCost)) return Double.POSITIVE_INFINITY;
        return getCost(maxCost, splitAndMerge.splitThresholdValue, false);
        
    }
    
    protected ImageByte tempSplitMask;
    private SplitAndMergeHessian.Interface getInterface(Region o1, Region o2) {
        o1.draw(tempSplitMask, o1.getLabel());
        o2.draw(tempSplitMask, o2.getLabel());
        SplitAndMergeHessian.Interface inter = RegionCluster.getInteface(o1, o2, tempSplitMask, splitAndMerge.getFactory());
        inter.updateInterface();
        o1.draw(tempSplitMask, 0);
        o2.draw(tempSplitMask, 0);
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
     * @return split objects in same landmark as {@param object}
     */
    @Override public RegionPopulation splitObject(StructureObject parent, int structureIdx, Region object) {
        Image input = parent.getPreFilteredImage(structureIdx);
        if (input==null) throw new IllegalArgumentException("No prefiltered image set");
        ImageInteger mask = object.isAbsoluteLandMark() ? object.getMaskAsImageInteger().cropWithOffset(input.getBoundingBox()) :object.getMaskAsImageInteger().cropWithOffset(input.getBoundingBox().resetOffset()); // extend mask to get the same size as the image
        if (splitAndMerge==null || !parent.equals(currentParent)) {
            currentParent = parent;
            splitAndMerge = initializeSplitAndMerge(parent, structureIdx,parent.getMask());
        }
        if (stores!=null) splitAndMerge.setTestMode(TestableProcessingPlugin.getAddTestImageConsumer(stores, (StructureObject)parent));
        RegionPopulation res = splitAndMerge.splitAndMerge(mask, minSizePropagation.getValue().intValue(), 2);
        //res =  localThreshold(input, res, parent, structureIdx, true); 
        if (object.isAbsoluteLandMark()) res.translate(parent.getBounds(), true);
        if (res.getRegions().size()>2) RegionCluster.mergeUntil(res, 2, 0); // merge most connected until 2 objects remain
        return res;
    }

    
    // manual correction implementations
    private boolean verboseManualSeg;
    @Override public void setManualSegmentationVerboseMode(boolean verbose) {
        this.verboseManualSeg=verbose;
    }

    @Override public RegionPopulation manualSegment(Image input, StructureObject parent, ImageMask segmentationMask, int structureIdx, List<int[]> seedsXYZ) {
        
        List<Region> seedObjects = RegionFactory.createSeedObjectsFromSeeds(seedsXYZ, input.sizeZ()==1, input.getScaleXY(), input.getScaleZ());
        EdgeDetector seg = initEdgeDetector(parent, structureIdx);
        RegionPopulation pop = seg.run(input, segmentationMask);
        SplitAndMergeHessian splitAndMerge=initializeSplitAndMerge(parent, structureIdx, pop.getLabelMap());
        pop = splitAndMerge.merge(pop, 0);
        pop.filter(o->{
            for(Region so : seedObjects ) if (o.intersect(so)) return true;
            return false;
        });
        localThreshold(input, pop, parent, structureIdx, false);
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
    
    
    // apply to segmenter from whole track information (will be set prior to call any other methods)
    
    boolean isVoid = false;
    double globalThreshold = Double.NaN, localThreshold = Double.NaN, globalBackgroundLevel=0;
    @Override
    public TrackParametrizable.TrackParametrizer<BacteriaIntensity> run(int structureIdx, List<StructureObject> parentTrack) {
        double[] backgroundMean = new double[1];
        Set<StructureObject> voidMC = getVoidMicrochannels(structureIdx, parentTrack, backgroundMean);
        
        double[] minAndGlobalThld = getGlobalMinAndGlobalThld(parentTrack, structureIdx, voidMC);
        return (p, s) -> {
            if (voidMC.contains(p)) s.isVoid=true; 
            s.minThld=minAndGlobalThld[0];
            s.globalBackgroundLevel = backgroundMean[0];
            s.globalThreshold = minAndGlobalThld[1];
            if (thresholdMethod.getSelectedIndex()>=1) s.threshold = minAndGlobalThld[1]; // global threshold
        };
        
    }
    /**
     * Detected whether all microchannels are void, only part of it or none
     * All microchannels are void if sigma/mu of raw sigal is inferior to the corresponding parameter value (~0.08)
     * If not global otsu threshold is computed on all prefiltered images, if mean prefiltered value < thld microchannel is considered void
     * @param structureIdx
     * @param parentTrack
     * @param backgroundMeanStore to stores global background level
     * @return void microchannels
     */
    protected Set<StructureObject> getVoidMicrochannels(int structureIdx, List<StructureObject> parentTrack, double[] backgroundMeanStore) {
        double globalVoidThldSigmaMu = sigmaThldForVoidMC.getValue().doubleValue();
        // get sigma in the middle line of each MC
        double[] globalSum = new double[4];
        globalSum[3] = Double.POSITIVE_INFINITY;
        Function<StructureObject, float[]> compute = p->{
            Image imR = p.getRawImage(structureIdx);
            Image im = p.getPreFilteredImage(structureIdx);
            if (im==null) throw new RuntimeException("no prefiltered image for : "+p + " ("+p.getId()+ ") at structure: "+structureIdx);
            int xMargin = im.sizeX()/3;
            MutableBoundingBox bb= new MutableBoundingBox(im).resetOffset().extend(new SimpleBoundingBox(xMargin, -xMargin, im.sizeX(), -im.sizeY()/6, 0, 0)); // only central line to avoid border effects + remove open end -> sometimes optical aberration
            double[] sum = new double[3];
            double[] sumR = new double[4];
            sumR[3] = Double.POSITIVE_INFINITY;
            BoundingBox.loop(bb, (x, y, z)-> {
                if (p.getMask().insideMask(x, y, z)) {
                    double v = im.getPixel(x, y, z);
                    sum[0]+=v;
                    sum[1]+=v*v;
                    sum[2]++;
                    v = imR.getPixel(x, y, z);
                    sumR[0]+=v;
                    sumR[1]+=v*v;
                    sumR[2]++;
                    if (sumR[3]>v) sumR[3] = v;
                }
            });
            synchronized(globalSum) {
                globalSum[0]+=sumR[0];
                globalSum[1]+=sumR[1];
                globalSum[2]+=sumR[2];
                if (globalSum[3]>sumR[3]) globalSum[3] = sumR[3];
            }
            double meanR = sumR[0]/sumR[2];
            double meanR2 = sumR[1]/sumR[2];
            return new float[]{(float)(sum[0]/sum[2]), (float)meanR, (float)Math.sqrt(meanR2 - meanR * meanR), (float)sumR[3]};
        };
        List<float[]> meanF_meanR_sigmaR = parentTrack.stream().parallel().map(p->compute.apply(p)).collect(Collectors.toList());
        // 1) criterion for void microchannel track
        double globalMean = globalSum[0]/globalSum[2];
        double globalMean2 = globalSum[1]/globalSum[2];
        double globalSigma = Math.sqrt(globalMean2 - globalMean * globalMean);
        if (globalSigma/(globalMean-globalSum[3])<globalVoidThldSigmaMu) {
            logger.debug("parent: {} sigma: {}/{}={} all channels considered void: {}", parentTrack.get(0), globalSigma, globalMean, globalSigma/globalMean);
            return new HashSet<>(parentTrack);
        }
        // 2) criterion for void microchannels : low intensity value
        // intensity criterion based on global otsu threshold
        double[] globalThld = getGlobalThreshold(parentTrack.stream(), structureIdx);
        if (backgroundMeanStore!=null) backgroundMeanStore[0] = globalThld[0];
        Set<StructureObject> outputVoidMicrochannels = IntStream.range(0, parentTrack.size())
                .filter(idx -> meanF_meanR_sigmaR.get(idx)[0]<globalThld[1])  // test on mean value is because when mc is very full, sigma can be low enough
                .filter(idx -> meanF_meanR_sigmaR.get(idx)[2]/(meanF_meanR_sigmaR.get(idx)[1]-meanF_meanR_sigmaR.get(idx)[3])<globalVoidThldSigmaMu) // test on sigma/mu value because when mc is nearly void, mean can be low engough
                .mapToObj(idx -> parentTrack.get(idx))
                .collect(Collectors.toSet());
        logger.debug("parent: {} global sigma: {}/{}={} global thld: {} void mc {}/{}", parentTrack.get(0), globalSigma,globalMean, globalSigma/globalMean, globalThld,outputVoidMicrochannels.size(), parentTrack.size() );
        //logger.debug("s/mu : {}", Utils.toStringList(meanF_meanR_sigmaR.subList(10, 15), f->"mu="+f[0]+" muR="+f[1]+"sR="+f[2]+ " sR/muR="+f[2]/f[1]));
        return outputVoidMicrochannels;
    }
    protected double[] getGlobalMinAndGlobalThld(List<StructureObject> parentTrack, int structureIdx, Set<StructureObject> voidMC) {
        if (voidMC.size()==parentTrack.size()) return new double[]{Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        Map<Image, ImageMask> imageMapMask = parentTrack.stream().filter(p->!voidMC.contains(p)).collect(Collectors.toMap(p->p.getPreFilteredImage(structureIdx), p->p.getMask() )); 
        if (this.thresholdMethod.getSelectedIndex()==1) { // global threshold on this track
            Histogram histo = HistogramFactory.getHistogram(()->Image.stream(imageMapMask, true).parallel(), globalThrehsolder.instanciatePlugin() instanceof IJAutoThresholder ? HistogramFactory.BIN_SIZE_METHOD.NBINS_256: HistogramFactory.BIN_SIZE_METHOD.AUTO_WITH_LIMITS);
            double minThreshold = BackgroundFit.backgroundFit(histo, 5, null);
            double globalThld = globalThrehsolder.instanciatePlugin().runThresholderHisto(histo);
            //double minThreshold = BackgroundThresholder.runThresholder(histo, 3, 3, 2, null);
            logger.debug("parent: {} global threshold on images with forground: [{};{}]", parentTrack.get(0), minThreshold, globalThld);
            return new double[]{minThreshold, globalThld}; 
        } else {
            Histogram histo = HistogramFactory.getHistogram(()->Image.stream(imageMapMask, true).parallel(), HistogramFactory.BIN_SIZE_METHOD.AUTO_WITH_LIMITS);
            double minThreshold = BackgroundFit.backgroundFit(histo, 5, null);
            
            return new double[]{minThreshold, Double.NaN}; 
        }
        
    }
    protected static double[] getGlobalThreshold(Stream<StructureObject> parent, int structureIdx) { // TODO : remplacer par background fit. attention si image normalisée -> bin pas 1, sinon bin = 1. Calcult auto des bin ? ou en deux temps ? 
        Map<Image, ImageMask> imageMapMask = parent.collect(Collectors.toMap(p->p.getPreFilteredImage(structureIdx), p->p.getMask() )); 
        //Histogram histo = HistogramFactory.getHistogram(()->Image.stream(imageMapMask, true).parallel(), 256);
        //return IJAutoThresholder.runThresholder(AutoThresholder.Method.Otsu, histo);
        Histogram histo = HistogramFactory.getHistogram(()->Image.stream(imageMapMask, true).parallel(), BIN_SIZE_METHOD.AUTO_WITH_LIMITS);
        double[] meanAndSigma = new double[2];
        double thld = BackgroundFit.backgroundFit(histo, 10, meanAndSigma);
        return new double[]{meanAndSigma[0], thld};
    }

    // testable processing plugin
    Map<StructureObject, TestDataStore> stores;
    @Override public void setTestDataStore(Map<StructureObject, TestDataStore> stores) {
        this.stores=  stores;
    }
}
