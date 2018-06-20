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

import boa.gui.image_interaction.ImageWindowManagerFactory;
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
import boa.gui.GUI;
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
import boa.measurement.GeometricalMeasurements;
import static boa.plugins.Plugin.logger;
import boa.plugins.SimpleThresholder;
import boa.plugins.TestableProcessingPlugin;
import boa.plugins.Thresholder;
import boa.plugins.ThresholderHisto;
import boa.plugins.ToolTip;
import boa.plugins.TrackParametrizable;
import boa.plugins.plugins.pre_filters.Median;
import boa.plugins.plugins.pre_filters.Sigma;
import boa.plugins.plugins.segmenters.BacteriaIntensity.FOREGROUND_SELECTION_METHOD;
import boa.plugins.plugins.segmenters.BacteriaIntensity.THRESHOLD_COMPUTATION;
import static boa.plugins.plugins.segmenters.EdgeDetector.valueFunction;
import boa.plugins.plugins.thresholders.BackgroundFit;
import boa.plugins.plugins.thresholders.CompareThresholds;
import boa.plugins.plugins.thresholders.ParentThresholder;
import boa.plugins.plugins.trackers.ObjectIdxTracker;
import boa.utils.ArrayUtil;
import boa.utils.HashMapGetCreate;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 *
 * @author jollion
 */
public class BacteriaIntensity implements TrackParametrizable<BacteriaIntensityPhase>, SegmenterSplitAndMerge, ManualSegmenter, ObjectSplitter, ToolTip, TestableProcessingPlugin {
    public static boolean verbose = false;
    public enum FOREGROUND_SELECTION_METHOD {SIMPLE_THRESHOLDING, HYSTERESIS_THRESHOLDING, EDGE_FUSION};
    public enum THRESHOLD_COMPUTATION {CURRENT_FRAME, PARENT_BRANCH, ROOT_BRANCH};
    
    // configuration-related attributes
    PreFilterSequence edgeMap = new PreFilterSequence("Edge Map").add(new ImageFeature().setFeature(ImageFeature.Feature.GAUSS).setScale(1.5), new Sigma(2).setMedianRadius(0)).setToolTipText("Filters used to define edge map used in first watershed step. <br />Max eigen value of Structure tensor is a good option<br />Median/Gaussian + Sigma is more suitable for noisy images (involve less derivation)<br />Gradient magnitude is another option. <br />Configuration Hint: tune this value using the intermediate images <em>Edge Map for Partitioning</em> and <em>Region Values after partitioning</em>");  // min scale = 1 (noisy signal:1.5), max = 2 min smooth scale = 1.5 (noisy / out of focus: 2) //new ImageFeature().setFeature(ImageFeature.Feature.StructureMax).setScale(1.5).setSmoothScale(2)
    
    PluginParameter<SimpleThresholder> bckThresholderFrame = new PluginParameter<>("Method", SimpleThresholder.class, new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu), false).setToolTipText("Threshold for foreground region selection after watershed partitioning on edge map. All regions with median value under this value are considered background").setEmphasized(true);
    PluginParameter<ThresholderHisto> bckThresholder = new PluginParameter<>("Method", ThresholderHisto.class, new BackgroundFit(10), false).setToolTipText("Threshold for foreground region selection after watershed partitioning on edge map. All regions with median value under this value are considered background. Computed on the whole parent track/ root track.").setEmphasized(true);
    ChoiceParameter bckThresholdMethod=  new ChoiceParameter("Background Threshold", Utils.toStringArray(THRESHOLD_COMPUTATION.values()), THRESHOLD_COMPUTATION.ROOT_BRANCH.toString(), false);
    ConditionalParameter bckThldCond = new ConditionalParameter(bckThresholdMethod).setActionParameters(THRESHOLD_COMPUTATION.CURRENT_FRAME.toString(), bckThresholderFrame).setActionParameters(THRESHOLD_COMPUTATION.ROOT_BRANCH.toString(), bckThresholder).setActionParameters(THRESHOLD_COMPUTATION.PARENT_BRANCH.toString(), bckThresholder).setToolTipText("Threshold for background region filtering after watershed partitioning on edge map. All regions with median value under this value are considered background. <br />If <em>CURRENT_FRAME</em> is selected, threshold will be computed at each frame. If <em>PARENT_BRANCH</em> is selected, threshold will be computed on the whole parent branch. If <em>ROOT_BRANCH</em> is selected, threshold will be computed on the whole root branch on raw images (no prefilters). <br />Configuration Hint: value is displayed on right click menu: <em>display thresholds</em> command. Tune the value using intermediate image <em>Region Values after partitioning</em>, only background partitions should be under this value");
    
    PluginParameter<SimpleThresholder> foreThresholderFrame = new PluginParameter<>("Method", SimpleThresholder.class, new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu), false).setEmphasized(true);
    PluginParameter<ThresholderHisto> foreThresholder = new PluginParameter<>("Method", ThresholderHisto.class, new BackgroundFit(20), false).setEmphasized(true).setToolTipText("Threshold for foreground region selection, use depend on the method. Computed on the whole parent-track/root track.");
    ChoiceParameter foreThresholdMethod=  new ChoiceParameter("Foreground Threshold", Utils.toStringArray(THRESHOLD_COMPUTATION.values()), THRESHOLD_COMPUTATION.ROOT_BRANCH.toString(), false);
    ConditionalParameter foreThldCond = new ConditionalParameter(foreThresholdMethod).setActionParameters(THRESHOLD_COMPUTATION.CURRENT_FRAME.toString(), foreThresholderFrame).setActionParameters(THRESHOLD_COMPUTATION.ROOT_BRANCH.toString(), foreThresholder).setActionParameters(THRESHOLD_COMPUTATION.PARENT_BRANCH.toString(), foreThresholder).setToolTipText("Threshold for foreground region selection after watershed partitioning on edge map. All regions with median value over this value are considered foreground. <br />If <em>CURRENT_FRAME</em> is selected, threshold will be computed at each frame. If <em>PARENT_BRANCH</em> is selected, threshold will be computed on the whole parent branch. If <em>ROOT_BRANCH</em> is selected, threshold will be computed on the whole root branch, on raw images (no prefilters).<br />Configuration Hint: value is displayed on right click menu: <em>display thresholds</em> command. Tune the value using intermediate image <em>Region Values after partitioning</em>, only foreground partitions should be over this value");
    
    NumberParameter backgroundEdgeFusionThld = new BoundedNumberParameter("Background Edge Fusion Threshold", 4, 2, 0, null).setEmphasized(true).setToolTipText("Threshold for background edge partition fusion. 2 adjacent partitions are merged if the 10% quantile edge value @ interface / background sigma < this value.<br />Sensible to sharpness of edges (decrease smoothing in edge map to increase sharpness)<br />Configuration Hint: Tune the value using intermediate image <em>Interface Values for Background Fusion</em>, no interface between foreground and background partitions should have a value under this threshold");
    NumberParameter foregroundEdgeFusionThld = new BoundedNumberParameter("Foreground Edge Fusion Threshold", 4, 0.2, 0, null).setEmphasized(true).setToolTipText("Threshold for foreground edge partition fusion. 2 adjacent partitions are merged if the mean edge value @ interface / mean value @ regions < this value<br />Sensible to sharpness of edges (decrease smoothing in edge map to increase sharpness).<br />Configuration Hint: Tune the value using intermediate image <em>Interface Values for Foreground Fusion</em>, no interface between foreground and background partitions should have a value under this threshold");
    ChoiceParameter foregroundSelectionMethod=  new ChoiceParameter("Foreground Selection Method", Utils.toStringArray(FOREGROUND_SELECTION_METHOD.values()), FOREGROUND_SELECTION_METHOD.EDGE_FUSION.toString(), false); //new String[]{FOREGROUND_SELECTION_METHOD.SIMPLE_THRESHOLDING.toString(), FOREGROUND_SELECTION_METHOD.HYSTERESIS_THRESHOLDING.toString()}
    ConditionalParameter foregroundSelectionCond = new ConditionalParameter(foregroundSelectionMethod).setActionParameters(FOREGROUND_SELECTION_METHOD.SIMPLE_THRESHOLDING.toString(), bckThldCond).setActionParameters(FOREGROUND_SELECTION_METHOD.HYSTERESIS_THRESHOLDING.toString(), bckThldCond, foreThldCond).setActionParameters(FOREGROUND_SELECTION_METHOD.EDGE_FUSION.toString(), backgroundEdgeFusionThld,foregroundEdgeFusionThld, bckThldCond).setEmphasized(true).setToolTipText("Foreground selection after watershed partitioning on <em>Edge Map</em><br /><ol><li>"+FOREGROUND_SELECTION_METHOD.SIMPLE_THRESHOLDING.toString()+": All regions with median value inferior to threshold defined in <em>Background Threshold</em> are erased. No suitable when fluorescence signal is highly variable among bacteria</li><li>"+FOREGROUND_SELECTION_METHOD.HYSTERESIS_THRESHOLDING.toString()+": Regions with median value under <em>Background Threshold</em> are condidered as background, all regions over threshold defined in <em>Foreground Threshold</em> are considered as foreground. Other regions are fused to the adjacent region that has lower edge value at interface, until only Background and Foreground region remain. Then background regions are removed. This method is suitable if a foreground threshold can be defined verifying the following conditions : <ol><li>each bacteria has at least one partition with a value superior to this threshold</li><li>No foreground partition (especially close to highly fluorescent bacteria) has a value over this threshold</li></ol> </li> This method is suitable when fluorescence signal is highly variable, but requieres to be tuned according to fluorescence signal (that can vary between different strands/gorwth conditions)<li>"+FOREGROUND_SELECTION_METHOD.EDGE_FUSION.toString()+": </li>Foreground selection is performed in 3 steps:<ol><li>All adjacent regions that verify condition defined in <em>Background Edge Fusion Threshold</em> are merged. This mainly merges background regions </li><li>All adjacent regions that verify condition defined in <em>Foreground Edge Fusion Threshold</em> are merged. This mainly merges foreground regions </li><li>All regions with median value inferior to threshold defined in <em>Background Threshold</em> are erased</li></ol>This method is more suitable when foreground fluorescence levels are higly variable, but might lead in false negative when edges are not sharp enough</ol>");
    
    NumberParameter splitThreshold = new BoundedNumberParameter("Split Threshold", 4, 0.3, 0, null).setEmphasized(true).setToolTipText("At step 2) regions are merge if sum(hessian)|interface / sum(raw intensity)|interface < (this parameter). <br />Lower value splits more.  <br />Configuration Hint: Tune the value using intermediate image <em>Interface Values before merge by Hessian</em>, interface with a value over this threshold will not be merged");
    NumberParameter localThresholdFactor = new BoundedNumberParameter("Local Threshold Factor", 2, 1.25, 0, null).setToolTipText("Factor defining the local threshold.  Lower value of this factor will yield in smaller cells. T = median value - (inter-quartile) * (this factor).");
    NumberParameter minSize = new BoundedNumberParameter("Minimum Region Size", 0, 100, 50, null).setToolTipText("Minimum Object Size in voxels");
    NumberParameter minSizePropagation = new BoundedNumberParameter("Minimum Size (propagation)", 0, 50, 1, null).setToolTipText("Minimal size of region at watershed partitioning. Only used for split objects during tracking or manual edition");
    NumberParameter smoothScale = new BoundedNumberParameter("Smooth scale", 1, 2, 0, 5).setToolTipText("Scale (pixels) for gaussian filtering for the local thresholding step");
    NumberParameter hessianScale = new BoundedNumberParameter("Hessian scale", 1, 4, 1, 6).setToolTipText("In pixels. Used in step 2). Lower value -> finner split, more sentitive to noise. Influences the value of split threshold parameter. <br />Configuration Hint: tune this value using the intermediate image <em>Hessian</em>");
    NumberParameter sigmaThldForVoidMC = new BoundedNumberParameter("Sigma/Mu thld for void channels", 3, 0.085, 0, null).setToolTipText("Parameter to look for void microchannels, at track pre-filter step: <br /> To assess if whole microchannel track is void: sigma / mu of raw images is computed on whole track, in the central line of each microchannel (1/3 of the width). If sigma / mu < this value, the whole track is considered to be void. <br />If the track is not void, a global otsu threshold is computed on the prefiltered signal. A channel is considered as void if its value of sigma / mu of raw signal is inferior to this threshold and is its mean value of prefiltered signal is superior to the global threshold");
    
    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{sigmaThldForVoidMC, edgeMap, foregroundSelectionCond, hessianScale, splitThreshold, smoothScale, localThresholdFactor, minSize};
    }
    private final String toolTip = "<b>Intensity-based 2D segmentation:</b>"
            + "<ol><li>Foreground detection: image is partitioned using by watershed on the edge map. Foreground partition are the selected depending on the method chosen in <em>Foreground Selection Method</em></li>"
            + "<li>Forground is split by applying a watershed transform on the maximal hessian eigen value, regions are then merged, using a criterion described in <em>Split Threshold</em> parameter</li>"
            + "<li>A local threshold is applied to each region. Mostly because inter-forground regions may be segmented in step 1). Threshold is set as described in <em>Local Threshold Factor</em> parameter. <br /> "
            + "Propagating from contour voxels, all voxels with value on the smoothed image (<em>Smooth scale</em> parameter) under the local threshold is removed</li></ol>";
    
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
        //if (stores!=null) res.setTestMode(TestableProcessingPlugin.getAddTestImageConsumer(stores, (StructureObject)parent));
        return res;
    }
    /**
     * Initialize the EdgeDetector that will create a parittion of the image and filter the regions
     * @param parent
     * @param structureIdx
     * @return 
     */
    protected EdgeDetector initEdgeDetector(StructureObjectProcessing parent, int structureIdx) {
        EdgeDetector seg = new EdgeDetector().setIsDarkBackground(true);
        seg.minSizePropagation.setValue(0);
        seg.setPreFilters(edgeMap.get());
        seg.setThrehsoldingMethod(EdgeDetector.THLD_METHOD.NO_THRESHOLDING);
        //seg.setTestMode( TestableProcessingPlugin.getAddTestImageConsumer(stores, (StructureObject)parent));
        return seg;
    }
    @Override public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        if (isVoid) return null;
        
        Consumer<Image> imageDisp = TestableProcessingPlugin.getAddTestImageConsumer(stores, (StructureObject)parent);
        edgeDetector = initEdgeDetector(parent, structureIdx);
        RegionPopulation splitPop = edgeDetector.runSegmenter(input, structureIdx, parent);
        splitPop.smoothRegions(1, true, parent.getMask());
        if (stores!=null) imageDisp.accept(edgeDetector.getWsMap(parent.getPreFilteredImage(structureIdx), parent.getMask()).setName("Edge Map for Partitioning"));
        if (stores!=null) imageDisp.accept(EdgeDetector.generateRegionValueMap(splitPop, parent.getPreFilteredImage(structureIdx)).setName("Region Values After Partitioning"));
        splitPop = filterRegionsAfterEdgeDetector(parent, structureIdx, splitPop);
        if (stores!=null) imageDisp.accept(EdgeDetector.generateRegionValueMap(splitPop, parent.getPreFilteredImage(structureIdx)).setName("Region Values After Filtering of Paritions"));
        if (splitAndMerge==null || !parent.equals(currentParent)) {
            currentParent = parent;
            splitAndMerge = initializeSplitAndMerge(parent, structureIdx, parent.getMask());
        }
        RegionPopulation split = splitAndMerge.split(splitPop.getLabelMap(), minSizePropagation.getValue().intValue());
        if (stores!=null) {
            imageDisp.accept(splitAndMerge.getHessian().setName("Hessian"));
            
        }
        split = filterRegionAfterSplitByHessian(parent, structureIdx, split);
        if (stores!=null)  {
            imageDisp.accept(EdgeDetector.generateRegionValueMap(split, input).setName("Region Values before merge by Hessian"));
            imageDisp.accept(splitAndMerge.drawInterfaceValues(split).setName("Interface values before merge by Hessian"));
        }
        RegionPopulation res = splitAndMerge.merge(split, null);
        res = localThreshold(input, res, parent, structureIdx, false);
        if (stores!=null)  imageDisp.accept(res.getLabelMap().duplicate("Region Labels after local threshold"));
        res.filter(new RegionPopulation.Thickness().setX(2).setY(2)); // remove thin objects
        res.filter(new RegionPopulation.Size().setMin(minSize.getValue().intValue())); // remove small objects
        
        res.sortBySpatialOrder(ObjectIdxTracker.IndexingOrder.YXZ);
        if (stores!=null) {
            int pSIdx = ((StructureObject)parent).getExperiment().getStructure(structureIdx).getParentStructure();
            stores.get(parent).addMisc("Display Thresholds", l->{
                if (l.stream().map(o->o.getParent(pSIdx)).anyMatch(o->o==parent)) {
                    GUI.log("Background Threshold: "+this.bckThld);
                    logger.info("Background Threshold: "+this.bckThld);
                    GUI.log("Foreground Threshold: "+this.foreThld);
                    logger.info("Foreground Threshold: {}", foreThld);
                    GUI.log("Background Mean: "+this.globalBackgroundLevel+" Sigma: "+globalBackgroundSigma);
                    logger.info("Background Mean: {} Sigma: {}", globalBackgroundLevel, globalBackgroundSigma);
                }
            });
        }
        return res;
    }
    private void ensureThresholds(StructureObjectProcessing parent, int structureIdx, boolean bck, boolean fore) {
        if (bck && Double.isNaN(bckThld) && bckThresholdMethod.getSelectedIndex()==0) {
            bckThld = bckThresholderFrame.instanciatePlugin().runSimpleThresholder(parent.getPreFilteredImage(structureIdx), parent.getMask());
        } 
        if (bck && Double.isNaN(bckThld)) throw new RuntimeException("Bck Threshold not computed");
        if (fore && Double.isNaN(foreThld) && foreThresholdMethod.getSelectedIndex()==0) {
            foreThld = foreThresholderFrame.instanciatePlugin().runSimpleThresholder(parent.getPreFilteredImage(structureIdx), parent.getMask());
        } 
        if (fore && Double.isNaN(foreThld)) throw new RuntimeException("Fore Threshold not computed");        
    }
    protected RegionPopulation filterRegionsAfterEdgeDetector(StructureObjectProcessing parent, int structureIdx, RegionPopulation pop) {
        switch(FOREGROUND_SELECTION_METHOD.valueOf(foregroundSelectionMethod.getValue())) {
            case SIMPLE_THRESHOLDING:
                ensureThresholds(parent, structureIdx, true, false);
                pop.getRegions().removeIf(r->valueFunction(parent.getPreFilteredImage(structureIdx)).apply(r)<=bckThld);
                return pop;
            case HYSTERESIS_THRESHOLDING:
                return filterRegionsAfterEdgeDetectorHysteresis(parent, structureIdx, pop);
            case EDGE_FUSION:
            default:
                return filterRegionsAfterEdgeDetectorEdgeFusion(parent, structureIdx, pop);
        }
    }
    protected RegionPopulation filterRegionsAfterEdgeDetectorEdgeFusion(StructureObjectProcessing parent, int structureIdx, RegionPopulation pop) {
        Consumer<Image> imageDisp = TestableProcessingPlugin.getAddTestImageConsumer(stores, (StructureObject)parent);
        SplitAndMergeEdge sm = new SplitAndMergeEdge(edgeDetector.getWsMap(parent.getPreFilteredImage(structureIdx), parent.getMask()), parent.getPreFilteredImage(structureIdx), 0.1, false);
        
        // merge background regions: value is proportional to background sigma
        sm.setInterfaceValue(i-> {
            if (i.getVoxels().isEmpty() || sm.isFusionForbidden(i)) {
                return Double.NaN;
            } else {
                int size = i.getVoxels().size()+i.getDuplicatedVoxels().size();
                //double val= Stream.concat(i.getVoxels().stream(), i.getDuplicatedVoxels().stream()).mapToDouble(v->sm.getWatershedMap().getPixel(v.x, v.y, v.z)).average().getAsDouble();
                double val= ArrayUtil.quantile(Stream.concat(i.getVoxels().stream(), i.getDuplicatedVoxels().stream()).mapToDouble(v->sm.getWatershedMap().getPixel(v.x, v.y, v.z)).sorted(), size, 0.1);
                val /= this.globalBackgroundSigma;
                return val;
            }
        }).setThresholdValue(this.backgroundEdgeFusionThld.getValue().doubleValue());
        if (stores!=null) imageDisp.accept(sm.drawInterfaceValues(pop).setName("Interface Values for Background Partition Fusion"));
        sm.merge(pop, null);
        if (stores!=null) imageDisp.accept(EdgeDetector.generateRegionValueMap(pop, parent.getPreFilteredImage(structureIdx)).setName("Region Values After Background Parition Fusion"));
        //if (stores!=null) imageDisp.accept(sm.drawInterfaceValues(pop).setName("Interface Values after Bck Partition Fusion"));
        
        // merge foreground regions: normalized values
        sm.setInterfaceValue(i-> {
            if (i.getVoxels().isEmpty() || sm.isFusionForbidden(i)) {
                return Double.NaN;
            } else {
                int size = i.getVoxels().size()+i.getDuplicatedVoxels().size();
                double val= Stream.concat(i.getVoxels().stream(), i.getDuplicatedVoxels().stream()).mapToDouble(v->sm.getWatershedMap().getPixel(v.x, v.y, v.z)).average().getAsDouble();
                //double val= ArrayUtil.quantile(Stream.concat(i.getVoxels().stream(), i.getDuplicatedVoxels().stream()).mapToDouble(v->sm.getWatershedMap().getPixel(v.x, v.y, v.z)).sorted(), size, 0.2);
                double mean1 = BasicMeasurements.getMeanValue(i.getE1(), sm.getIntensityMap());
                double mean2 = BasicMeasurements.getMeanValue(i.getE2(), sm.getIntensityMap());
                double mean = (mean1 + mean2) / 2d;
                //double mean= Stream.concat(i.getE1().getVoxels().stream(), i.getE2().getVoxels().stream()).mapToDouble(v->sm.getIntensityMap().getPixel(v.x, v.y, v.z)).average().getAsDouble(); // not suitable for background regions directly adjecent to highly fluo cells
                //double mean= Stream.concat(i.getVoxels().stream(), i.getDuplicatedVoxels().stream()).mapToDouble(v->sm.getIntensityMap().getPixel(v.x, v.y, v.z)).average().getAsDouble(); // leak easily
                val= val/(mean-globalBackgroundLevel);
                return val;
            }
        }).setThresholdValue(this.foregroundEdgeFusionThld.getValue().doubleValue());
        if (stores!=null) imageDisp.accept(sm.drawInterfaceValues(pop).setName("Interface Values for Foreground Partition Fusion"));
        sm.merge(pop, null);
        if (stores!=null) imageDisp.accept(EdgeDetector.generateRegionValueMap(pop, parent.getPreFilteredImage(structureIdx)).setName("Region Values After Foreground Parition Fusion"));
        ensureThresholds(parent, structureIdx, true, false);
        pop.getRegions().removeIf(r->valueFunction(parent.getPreFilteredImage(structureIdx)).apply(r)<=bckThld); // remove regions with low intensity

        pop.relabel(true);
        return pop;
    }
    private static Predicate<Region> touchSides(ImageMask parentMask) {
        RegionPopulation.ContactBorderMask contactLeft = new RegionPopulation.ContactBorderMask(1, parentMask, RegionPopulation.Border.Xl);
        RegionPopulation.ContactBorderMask contactRight = new RegionPopulation.ContactBorderMask(1, parentMask, RegionPopulation.Border.Xr);
        return r-> {
            //double l = GeometricalMeasurements.maxThicknessY(r);
            int cLeft = contactLeft.getContact(r);
            if (cLeft>0) return true;
            int cRight = contactRight.getContact(r);
            return cRight>0;
        };
    }
    
    // hysteresis thresholding
    protected RegionPopulation filterRegionsAfterEdgeDetectorHysteresis(StructureObjectProcessing parent, int structureIdx, RegionPopulation pop) {
        // perform hysteresis thresholding : define foreground & backgroun + merge indeterminded regions to the closest neighbor in therms of intensity
        Consumer<Image> imageDisp = TestableProcessingPlugin.getAddTestImageConsumer(stores, (StructureObject)parent);
        ensureThresholds(parent, structureIdx, true, true);
        Map<Region, Double> values = pop.getRegions().stream().collect(Collectors.toMap(o->o, valueFunction(parent.getPreFilteredImage(structureIdx))));
        Predicate<Region> touchSides = touchSides(parent.getMask());
        Set<Region> backgroundL = pop.getRegions().stream().filter(r->values.get(r)<=bckThld || touchSides.test(r) ).collect(Collectors.toSet());
        if (foreThld==bckThld) { // simple thresholding
            pop.getRegions().removeAll(backgroundL);
            pop.relabel(true);
            return pop;
        }
        Set<Region> foregroundL = pop.getRegions().stream().filter(r->!backgroundL.contains(r) && values.get(r)>=foreThld).collect(Collectors.toSet());
        if (stores!=null) logger.debug("min thld: {} max thld: {}, background: {}, foreground: {}, unknown: {}", bckThld, foreThld, backgroundL.size(), foregroundL.size(), pop.getRegions().size()-backgroundL.size()-foregroundL.size());
        if (pop.getRegions().size()>foregroundL.size()+backgroundL.size()) { // merge indetermined regions with either background or foreground
            pop.getRegions().removeAll(backgroundL);
            pop.getRegions().removeAll(foregroundL);
            pop.getRegions().addAll(0, backgroundL); // so that background region keep same instance when merged with indetermined region
            pop.getRegions().addAll(backgroundL.size(), foregroundL);
            pop.relabel(false);
            SplitAndMergeEdge sm = new SplitAndMergeEdge(edgeDetector.getWsMap(parent.getPreFilteredImage(structureIdx), parent.getMask()), parent.getPreFilteredImage(structureIdx), 1, false); // TODO : parameter
            sm.setInterfaceValue(i-> {
                if (i.getVoxels().isEmpty() || sm.isFusionForbidden(i)) {
                    return Double.NaN;
                } else {
                    int size = i.getVoxels().size()+i.getDuplicatedVoxels().size();
                    double val= ArrayUtil.quantile(Stream.concat(i.getVoxels().stream(), i.getDuplicatedVoxels().stream()).mapToDouble(v->sm.getWatershedMap().getPixel(v.x, v.y, v.z)).sorted(), size, 0.1);
                    return val;
                }
            });
            if (stores!=null) imageDisp.accept(sm.drawInterfaceValues(pop).setName("Interface Values for Hysteresis"));
            //sm.setTestMode(imageDisp);
            //SplitAndMergeRegionCriterion sm = new SplitAndMergeRegionCriterion(null, parent.getPreFilteredImage(structureIdx), -Double.MIN_VALUE, SplitAndMergeRegionCriterion.InterfaceValue.ABSOLUTE_DIFF_MEDIAN_BTWN_REGIONS);
            sm.addForbidFusionForegroundBackground(r->backgroundL.contains(r), r->foregroundL.contains(r));
            /*sm.addForbidFusion(i->{
                int r1 = backgroundL.contains(i.getE1()) ? -1 : (foregroundL.contains(i.getE1()) ? 1 : 0);
                int r2 = backgroundL.contains(i.getE2()) ? -1 : (foregroundL.contains(i.getE2()) ? 1 : 0);
                return r1*r2!=0; // forbid merge if both are foreground or background
            });*/
            
            sm.merge(pop, sm.objectNumberLimitCondition(backgroundL.size()+foregroundL.size()));
            if (stores!=null) {
                imageDisp.accept(EdgeDetector.generateRegionValueMap(pop, parent.getPreFilteredImage(structureIdx)).setName("Values After Hysteresis"));
            }
            pop.getRegions().removeAll(backgroundL);
        } else pop.getRegions().removeAll(backgroundL);
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
        RegionPopulation res = splitAndMerge.splitAndMerge(mask, minSizePropagation.getValue().intValue(), splitAndMerge.objectNumberLimitCondition(2));
        res.sortBySpatialOrder(ObjectIdxTracker.IndexingOrder.YXZ);
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
        pop = splitAndMerge.merge(pop, null);
        pop.filter(o->{
            for(Region so : seedObjects ) if (o.intersect(so)) return true;
            return false;
        });
        pop.sortBySpatialOrder(ObjectIdxTracker.IndexingOrder.YXZ);
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
    protected double bckThld = Double.NaN, foreThld = Double.NaN;
    private double globalBackgroundLevel=0, globalBackgroundSigma=1;
    @Override
    public TrackParametrizable.TrackParametrizer<BacteriaIntensity> run(int structureIdx, List<StructureObject> parentTrack) {
        double[] backgroundMeanAndSigma = new double[2];
        Set<StructureObject> voidMC = getVoidMicrochannels(structureIdx, parentTrack, backgroundMeanAndSigma);
        
        double[] thlds = getBranchThresholds(parentTrack, structureIdx, voidMC);
        return (p, s) -> {
            if (voidMC.contains(p)) s.isVoid=true; 
            s.bckThld=thlds[0];
            s.foreThld = thlds[1];
            s.globalBackgroundLevel = backgroundMeanAndSigma[0];
            s.globalBackgroundSigma = backgroundMeanAndSigma[1];
        };
        
    }
    /**
     * Detected whether all microchannels are void, only part of it or none
     * All microchannels are void if sigma/mu of raw sigal is inferior to the corresponding parameter value (~0.08)
     * If not global otsu threshold is computed on all prefiltered images, if mean prefiltered value < thld microchannel is considered void
     * @param structureIdx
     * @param parentTrack
     * @param backgroundMeanAndSigmaStore to stores global background level
     * @return void microchannels
     */
    protected Set<StructureObject> getVoidMicrochannels(int structureIdx, List<StructureObject> parentTrack, double[] backgroundMeanAndSigmaStore) {
        double globalVoidThldSigmaMu = sigmaThldForVoidMC.getValue().doubleValue();
        // get sigma in the middle line of each MC
        double[] globalSum = new double[4];
        globalSum[3] = Double.POSITIVE_INFINITY;
        Function<StructureObject, float[]> compute = p->{
            Image imR = p.getRawImage(structureIdx);
            Image im = p.getPreFilteredImage(structureIdx);
            if (im==null) throw new RuntimeException("no prefiltered image");
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
        // intensity criterion based on global threshold (otsu for phase, backgroundFit(10) for fluo
        double globalThld = getGlobalThreshold(parentTrack, structureIdx, null, backgroundMeanAndSigmaStore);

        Set<StructureObject> outputVoidMicrochannels = IntStream.range(0, parentTrack.size())
                .filter(idx -> meanF_meanR_sigmaR.get(idx)[0]<globalThld)  // test on mean value is because when mc is very full, sigma can be low enough
                .filter(idx -> meanF_meanR_sigmaR.get(idx)[2]/(meanF_meanR_sigmaR.get(idx)[1]-meanF_meanR_sigmaR.get(idx)[3])<globalVoidThldSigmaMu) // test on sigma/mu value because when mc is nearly void, mean can be low engough
                .mapToObj(idx -> parentTrack.get(idx))
                .collect(Collectors.toSet());
        logger.debug("parent: {} global sigma: {}/{}={} global thld: {} void mc {}/{}", parentTrack.get(0), globalSigma,globalMean, globalSigma/globalMean, globalThld,outputVoidMicrochannels.size(), parentTrack.size() );
        //logger.debug("s/mu : {}", Utils.toStringList(meanF_meanR_sigmaR.subList(10, 15), f->"mu="+f[0]+" muR="+f[1]+"sR="+f[2]+ " sR/muR="+f[2]/f[1]));
        return outputVoidMicrochannels;
    }
    protected double[] getBranchThresholds(List<StructureObject> parentTrack, int structureIdx, Set<StructureObject> voidMC) {
        if (voidMC.size()==parentTrack.size()) return new double[]{Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        Histogram[] histoRoot=new Histogram[1], histoParent=new Histogram[1];
        Supplier<Histogram> getHistoParent = () -> {
            if (histoParent[0]==null) { 
                Map<Image, ImageMask> imageMapMask = parentTrack.stream().filter(p->!voidMC.contains(p)).collect(Collectors.toMap(p->p.getPreFilteredImage(structureIdx), p->p.getMask() )); 
                histoParent[0] = HistogramFactory.getHistogram(()->Image.stream(imageMapMask, true).parallel(), HistogramFactory.BIN_SIZE_METHOD.AUTO_WITH_LIMITS);
            }
            return histoParent[0];
        };
        boolean needToComputeGlobalMin = this.bckThresholdMethod.getSelectedIndex()>0;
        boolean needToComputeGlobalMax = this.foregroundSelectionMethod.getSelectedIndex()==1 && this.foreThresholdMethod.getSelectedIndex()>0;
        if (!needToComputeGlobalMin && !needToComputeGlobalMin) return new double[]{Double.NaN, Double.NaN};
        double bckThld = Double.NaN, foreThld = Double.NaN;
        if (needToComputeGlobalMin) {
            ThresholderHisto thlder = bckThresholder.instanciatePlugin();
            if (bckThresholdMethod.getSelectedIndex()==2) { // root threshold
                if (thlder instanceof BackgroundFit) {
                    double[] ms = getRootBckMeanAndSigma(parentTrack, structureIdx, histoRoot);
                    bckThld = ms[0] + ((BackgroundFit)thlder).getSigmaFactor() * ms[1];
                } else bckThld = getRootThreshold(parentTrack, structureIdx, histoRoot, true);
            } else bckThld = thlder.runThresholderHisto(getHistoParent.get());  // parent threshold
        }
        if (needToComputeGlobalMax) { // global threshold on this track
            ThresholderHisto thlder = foreThresholder.instanciatePlugin();
            if (foreThresholdMethod.getSelectedIndex()==2) { // root threshold
                if (thlder instanceof BackgroundFit) {
                    double[] ms = getRootBckMeanAndSigma(parentTrack, structureIdx, histoRoot);
                    foreThld = ms[0] + ((BackgroundFit)thlder).getSigmaFactor() * ms[1];
                } else foreThld = getRootThreshold(parentTrack, structureIdx, histoRoot, false);
            } else foreThld = thlder.runThresholderHisto(getHistoParent.get());  // parent threshold
        } 
        logger.debug("parent: {} global threshold on images with forground: [{};{}]", parentTrack.get(0), bckThld, foreThld);
        return new double[]{bckThld, foreThld}; 
    }
    // IF THERE ARE PRE-FILTERS -> VALUES WILL NOT BE COHERENT WITH ROOT MEAN & SIGMA
    protected double getGlobalThreshold(List<StructureObject> parent, int structureIdx, Histogram[] histoStore, double[] meanAndSigmaStore) {
        double sigmaFactor = 5; // was 10
        if (parent.get(0).getRawImage(structureIdx)==parent.get(0).getPreFilteredImage(structureIdx)) { // no prefilter -> perform on root
            logger.debug("no prefilters detected: global mean & sigma on root branch");
            double[] ms = getRootBckMeanAndSigma(parent, structureIdx, histoStore);
            if (meanAndSigmaStore!=null) {
                meanAndSigmaStore[0] = ms[0];
                meanAndSigmaStore[1] = ms[1];
            }
            return ms[0] + sigmaFactor * ms[1];
        } else { // prefilters -> perform on parent branch
            logger.debug("prefilters detected: global mean & sigma on parent branch");
            Map<Image, ImageMask> imageMapMask = parent.stream().collect(Collectors.toMap(p->p.getPreFilteredImage(structureIdx), p->p.getMask() )); 
            Histogram histo = HistogramFactory.getHistogram(()->Image.stream(imageMapMask, true).parallel(), HistogramFactory.BIN_SIZE_METHOD.AUTO_WITH_LIMITS);
            return BackgroundFit.backgroundFit(histo, 5, meanAndSigmaStore);
        } 
    }
    private double getRootThreshold(List<StructureObject> parents, int structureIdx, Histogram[] histoStore, boolean min) {
        // cas particulier si BackgroundFit -> call 
        String key = (min ? bckThresholder.toJSONEntry().toJSONString() : foreThresholder.toJSONEntry().toJSONString())+"_"+structureIdx;
        if (parents.get(0).getRoot().getAttributes().containsKey(key)) {
            return parents.get(0).getRoot().getAttribute(key, Double.NaN);
        } else {
            synchronized(parents.get(0).getRoot()) {
                if (parents.get(0).getRoot().getAttributes().containsKey(key)) {
                    return parents.get(0).getRoot().getAttribute(key, Double.NaN);
                } else {
                    List<Image> im = parents.stream().map(p->p.getRoot()).map(p->p.getRawImage(structureIdx)).collect(Collectors.toList());
                    ThresholderHisto thlder = (min ? bckThresholder:foreThresholder).instanciatePlugin();
                    Histogram histo;
                    if (histoStore!=null && histoStore[0]!=null ) histo = histoStore[0];
                    else {
                        histo = HistogramFactory.getHistogram(()->Image.stream(im).parallel(), BIN_SIZE_METHOD.AUTO_WITH_LIMITS) ;
                        if (histoStore!=null) histoStore[0] = histo;
                    }
                    double thld = thlder.runThresholderHisto(histo);
                    parents.get(0).getRoot().setAttribute(key, thld);
                    logger.debug("computing thld: {} on root: {} -> {}", key, parents.get(0).getRoot(), thld);
                    return thld;
                }
            }
        }
    }
    
    private static double[] getRootBckMeanAndSigma(List<StructureObject> parents, int structureIdx, Histogram[] histoStore) {
        String meanK = "backgroundMean_"+structureIdx;
        String stdK = "backgroundStd_"+structureIdx;
        if (parents.get(0).getRoot().getAttributes().containsKey(meanK)) {
            return new double[]{parents.get(0).getRoot().getAttribute(meanK, 0d), parents.get(0).getRoot().getAttribute(stdK, 1d)};
        } else {
            synchronized(parents.get(0).getRoot()) {
                if (parents.get(0).getRoot().getAttributes().containsKey(meanK)) {
                    return new double[]{parents.get(0).getRoot().getAttribute(meanK, 0d), parents.get(0).getRoot().getAttribute(stdK, 1d)};
                } else {
                    Histogram histo;
                    if (histoStore!=null && histoStore[0]!=null) histo = histoStore[0];
                    else {
                        List<Image> im = parents.stream().map(p->p.getRoot()).map(p->p.getRawImage(structureIdx)).collect(Collectors.toList());
                        histo = HistogramFactory.getHistogram(()->Image.stream(im).parallel(), BIN_SIZE_METHOD.AUTO_WITH_LIMITS);
                        if (histoStore!=null) histoStore[0] = histo;
                    }
                    double[] ms = new double[2];
                    BackgroundFit.backgroundFit(histo, 10, ms);
                    parents.get(0).getRoot().setAttribute(meanK, ms[0]);
                    parents.get(0).getRoot().setAttribute(stdK, ms[1]);
                    logger.debug("compute root {} mean {}, sigma: {}", parents.get(0), ms[0], ms[1]);
                    return ms;
                }
            }
        }
    }
    // testable processing plugin
    Map<StructureObject, TestDataStore> stores;
    @Override public void setTestDataStore(Map<StructureObject, TestDataStore> stores) {
        this.stores=  stores;
    }
}
