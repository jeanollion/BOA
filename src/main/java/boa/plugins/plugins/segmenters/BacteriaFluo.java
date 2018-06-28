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
import boa.ui.GUI;
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
import boa.plugins.plugins.segmenters.BacteriaFluo.FOREGROUND_SELECTION_METHOD;
import boa.plugins.plugins.segmenters.BacteriaFluo.THRESHOLD_COMPUTATION;
import static boa.plugins.plugins.segmenters.EdgeDetector.valueFunction;
import boa.plugins.plugins.thresholders.BackgroundFit;
import boa.plugins.plugins.trackers.ObjectIdxTracker;
import boa.utils.ArrayUtil;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author jollion
 */
public class BacteriaFluo extends BacteriaIntensitySegmenter<BacteriaFluo> {
    public static boolean verbose = false;
    public enum FOREGROUND_SELECTION_METHOD {SIMPLE_THRESHOLDING, HYSTERESIS_THRESHOLDING, EDGE_FUSION};
    public enum THRESHOLD_COMPUTATION {CURRENT_FRAME, PARENT_TRACK, ROOT_TRACK};
    private enum BACKGROUND_REMOVAL {BORDER_CONTACT, THRESHOLDING, BORDER_CONTACT_AND_THRESHOLDING};
    // configuration-related attributes
    
    PluginParameter<SimpleThresholder> bckThresholderFrame = new PluginParameter<>("Method", SimpleThresholder.class, new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu), false).setToolTipText("Threshold for foreground region selection after watershed partitioning on edge map. All regions with median value under this value are considered background").setEmphasized(true);
    PluginParameter<ThresholderHisto> bckThresholder = new PluginParameter<>("Method", ThresholderHisto.class, new BackgroundFit(10), false).setToolTipText("Threshold for foreground region selection after watershed partitioning on edge map. All regions with median value under this value are considered background. Computed on the whole parent track/ root track.").setEmphasized(true);
    ChoiceParameter bckThresholdMethod=  new ChoiceParameter("Background Threshold", Utils.toStringArray(THRESHOLD_COMPUTATION.values()), THRESHOLD_COMPUTATION.ROOT_TRACK.toString(), false);
    ConditionalParameter bckThldCond = new ConditionalParameter(bckThresholdMethod).setActionParameters(THRESHOLD_COMPUTATION.CURRENT_FRAME.toString(), bckThresholderFrame).setActionParameters(THRESHOLD_COMPUTATION.ROOT_TRACK.toString(), bckThresholder).setActionParameters(THRESHOLD_COMPUTATION.PARENT_TRACK.toString(), bckThresholder).setToolTipText("Threshold for background region filtering after watershed partitioning on edge map. All regions with median value under this value are considered background. <br />If <em>CURRENT_FRAME</em> is selected, threshold will be computed at each frame. If <em>PARENT_BRANCH</em> is selected, threshold will be computed on the whole parent track. If <em>ROOT_TRACK</em> is selected, threshold will be computed on the whole root track on raw images (no prefilters). <br />Configuration Hint: value is displayed on right click menu: <em>display thresholds</em> command. Tune the value using intermediate image <em>Region Values after partitioning</em>, only background partitions should be under this value");
    
    PluginParameter<SimpleThresholder> foreThresholderFrame = new PluginParameter<>("Method", SimpleThresholder.class, new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu), false).setEmphasized(true);
    PluginParameter<ThresholderHisto> foreThresholder = new PluginParameter<>("Method", ThresholderHisto.class, new BackgroundFit(20), false).setEmphasized(true).setToolTipText("Threshold for foreground region selection, use depend on the method. Computed on the whole parent-track/root track.");
    ChoiceParameter foreThresholdMethod=  new ChoiceParameter("Foreground Threshold", Utils.toStringArray(THRESHOLD_COMPUTATION.values()), THRESHOLD_COMPUTATION.ROOT_TRACK.toString(), false);
    ConditionalParameter foreThldCond = new ConditionalParameter(foreThresholdMethod).setActionParameters(THRESHOLD_COMPUTATION.CURRENT_FRAME.toString(), foreThresholderFrame).setActionParameters(THRESHOLD_COMPUTATION.ROOT_TRACK.toString(), foreThresholder).setActionParameters(THRESHOLD_COMPUTATION.PARENT_TRACK.toString(), foreThresholder).setToolTipText("Threshold for foreground region selection after watershed partitioning on edge map. All regions with median value over this value are considered foreground. <br />If <em>CURRENT_FRAME</em> is selected, threshold will be computed at each frame. If <em>PARENT_BRANCH</em> is selected, threshold will be computed on the whole parent track. If <em>ROOT_TRACK</em> is selected, threshold will be computed on the whole root track, on raw images (no prefilters).<br />Configuration Hint: value is displayed on right click menu: <em>display thresholds</em> command. Tune the value using intermediate image <em>Region Values after partitioning</em>, only foreground partitions should be over this value");
    
    NumberParameter backgroundEdgeFusionThld = new BoundedNumberParameter("Background Edge Fusion Threshold", 4, 2, 0, null).setEmphasized(true).setToolTipText("Threshold for background edge partition fusion. 2 adjacent partitions are merged if the 10% quantile edge value @ interface / background sigma &lt; this value.<br />Sensible to sharpness of edges (decrease smoothing in edge map to increase sharpness)<br />Configuration Hint: Tune the value using intermediate image <em>Interface Values for Background Fusion</em>, no interface between foreground and background partitions should have a value under this threshold");
    NumberParameter foregroundEdgeFusionThld = new BoundedNumberParameter("Foreground Edge Fusion Threshold", 4, 0.2, 0, null).setToolTipText("Threshold for foreground edge partition fusion. Allow to merge foreground region with low value in order to avoid removing them at thresholding<br />2 adjacent partitions are merged if the mean edge value @ interface / mean value @ regions &lt; this value<br />Sensible to sharpness of edges (decrease smoothing in edge map to increase sharpness).<br />Configuration Hint: Tune the value using intermediate image <em>Interface Values for Foreground Fusion</em>, no interface between foreground and background partitions should have a value under this threshold");
    ChoiceParameter backgroundSel=  new ChoiceParameter("Background Removal", Utils.toStringArray(BACKGROUND_REMOVAL.values()), BACKGROUND_REMOVAL.BORDER_CONTACT.toString(), false);
    ConditionalParameter backgroundSelCond = new ConditionalParameter(backgroundSel).setActionParameters(BACKGROUND_REMOVAL.BORDER_CONTACT_AND_THRESHOLDING.toString(), foregroundEdgeFusionThld, bckThldCond).setActionParameters(BACKGROUND_REMOVAL.THRESHOLDING.toString(), foregroundEdgeFusionThld, bckThldCond).setToolTipText("Method to remove background partition after merging edges.<br/><ol><li>"+BACKGROUND_REMOVAL.BORDER_CONTACT.toString()+": Removes all partition directly in contact with upper, left and right borders. Length & with of microchannels should be adjusted so that bacteria going out of microchannels are no within the segmented regions of microchannel otherwise they may touch the left/right sides of microchannels are be erased</li><li>"+BACKGROUND_REMOVAL.THRESHOLDING.toString()+": Removes regions with median value under <em>Background Threshold</em></li><li>"+BACKGROUND_REMOVAL.BORDER_CONTACT_AND_THRESHOLDING.toString()+": Combination of the two previous methods</li></ol>");
    ChoiceParameter foregroundSelectionMethod=  new ChoiceParameter("Foreground Selection Method", Utils.toStringArray(FOREGROUND_SELECTION_METHOD.values()), FOREGROUND_SELECTION_METHOD.EDGE_FUSION.toString(), false); 
    ConditionalParameter foregroundSelectionCond = new ConditionalParameter(foregroundSelectionMethod).setActionParameters(FOREGROUND_SELECTION_METHOD.SIMPLE_THRESHOLDING.toString(), bckThldCond).setActionParameters(FOREGROUND_SELECTION_METHOD.HYSTERESIS_THRESHOLDING.toString(), bckThldCond, foreThldCond).setActionParameters(FOREGROUND_SELECTION_METHOD.EDGE_FUSION.toString(), backgroundEdgeFusionThld,backgroundSelCond).setEmphasized(true).setToolTipText("Foreground selection after watershed partitioning on <em>Edge Map</em><br /><ol><li>"+FOREGROUND_SELECTION_METHOD.SIMPLE_THRESHOLDING.toString()+": All regions with median value inferior to threshold defined in <em>Background Threshold</em> are erased. No suitable when fluorescence signal is highly variable among bacteria</li><li>"+FOREGROUND_SELECTION_METHOD.HYSTERESIS_THRESHOLDING.toString()+": Regions with median value under <em>Background Threshold</em> are condidered as background, all regions over threshold defined in <em>Foreground Threshold</em> are considered as foreground. Other regions are fused to the adjacent region that has lower edge value at interface, until only Background and Foreground region remain. Then background regions are removed. This method is suitable if a foreground threshold can be defined verifying the following conditions : <ol><li>each bacteria has at least one partition with a value superior to this threshold</li><li>No foreground partition (especially close to highly fluorescent bacteria) has a value over this threshold</li></ol> </li> This method is suitable when fluorescence signal is highly variable, but requieres to be tuned according to fluorescence signal (that can vary between different strands/gorwth conditions)<li>"+FOREGROUND_SELECTION_METHOD.EDGE_FUSION.toString()+": </li>Foreground selection is performed in 3 steps:<ol><li>All adjacent regions that verify condition defined in <em>Background Edge Fusion Threshold</em> are merged. This mainly merges background regions </li><li>All adjacent regions that verify condition defined in <em>Foreground Edge Fusion Threshold</em> are merged. This mainly merges foreground regions </li><li>All regions with median value inferior to threshold defined in <em>Background Threshold</em> are erased</li></ol>This method is more suitable when foreground fluorescence levels are higly variable, but might lead in false negative when edges are not sharp enough</ol>");
    
    // attributes parametrized during track parametrization
    protected double bckThld = Double.NaN, foreThld = Double.NaN;
    private double globalBackgroundSigma=1;
    
    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{vcThldForVoidMC, edgeMap, foregroundSelectionCond, hessianScale, splitThreshold, smoothScale, localThresholdFactor};
    }
    @Override
    public String getToolTipText() {
        return "<b>Intensity-based 2D segmentation:</b>"
            +"<li>Void microchannels are detected prior to segmentation step using information on the whole microchannel track. See <em>Variation coefficient threshold</em> parameter"
            + "<ol><li>Foreground detection: image is partitioned using by watershed on the edge map. Foreground partition are the selected depending on the method chosen in <em>Foreground Selection Method</em></li>"
            + "<li>Forground is split by applying a watershed transform on the maximal hessian eigen value, regions are then merged, using a criterion described in <em>Split Threshold</em> parameter</li>"
            + "<li>A local threshold is applied to each region. Mostly because inter-forground regions may be segmented in step 1). Threshold is set as described in <em>Local Threshold Factor</em> parameter. <br /> "
            + "Propagating from contour voxels, all voxels with value on the smoothed image (<em>Smooth scale</em> parameter) under the local threshold is removed</li></ol>";
    }
    
    
    public BacteriaFluo() {
        localThresholdFactor.setToolTipText("Factor defining the local threshold.  Lower value of this factor will yield in smaller cells. T = median value - (inter-quartile) * (this factor).");
    }
    
    @Override
    public String toString() {
        return "Bacteria Intensity: " + Utils.toStringArray(getParameters());
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
    @Override
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
        BACKGROUND_REMOVAL remMeth = BACKGROUND_REMOVAL.valueOf(backgroundSel.getSelectedItem());
        boolean remContact = remMeth == BACKGROUND_REMOVAL.BORDER_CONTACT || remMeth == BACKGROUND_REMOVAL.BORDER_CONTACT_AND_THRESHOLDING;
        boolean remThld = remMeth == BACKGROUND_REMOVAL.THRESHOLDING || remMeth == BACKGROUND_REMOVAL.BORDER_CONTACT_AND_THRESHOLDING;
        if (remThld) { // merge foreground regions: normalized values
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
        }
        if (remThld) ensureThresholds(parent, structureIdx, true, false);
        RegionPopulation.ContactBorderMask contact = new RegionPopulation.ContactBorderMask(2, parent.getMask(), RegionPopulation.Border.XYup);
        pop.getRegions().removeIf(r->(remContact ? !contact.keepObject(r):false) || (remThld ? valueFunction(parent.getPreFilteredImage(structureIdx)).apply(r)<=bckThld : false)); // remove regions with low intensity
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
    private RegionPopulation filterRegionsAfterEdgeDetectorHysteresis(StructureObjectProcessing parent, int structureIdx, RegionPopulation pop) {
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
    @Override 
    protected RegionPopulation filterRegionsAfterSplitByHessian(StructureObjectProcessing parent, int structureIdx, RegionPopulation pop) {
        return pop;
    }
    @Override
    protected RegionPopulation filterRegionsAfterMergeByHessian(StructureObjectProcessing parent, int structureIdx, RegionPopulation pop) {
        return pop;
    }
    @Override
    protected RegionPopulation localThreshold(Image input, RegionPopulation pop, StructureObjectProcessing parent, int structureIdx, boolean callFromSplit) {
        Image smooth = smoothScale.getValue().doubleValue()>=1 ? ImageFeatures.gaussianSmooth(input, smoothScale.getValue().doubleValue(), false):input;
        pop.localThreshold(smooth, localThresholdFactor.getValue().doubleValue(), true, true);
        return pop;
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
        RegionPopulation res = splitAndMerge.splitAndMerge(mask, MIN_SIZE_PROPAGATION, splitAndMerge.objectNumberLimitCondition(2));
        res.sortBySpatialOrder(ObjectIdxTracker.IndexingOrder.YXZ);
        //res =  localThreshold(input, res, parent, structureIdx, true); 
        if (object.isAbsoluteLandMark()) res.translate(parent.getBounds(), true);
        if (res.getRegions().size()>2) RegionCluster.mergeUntil(res, 2, 0); // merge most connected until 2 objects remain
        return res;
    }

    // apply to segmenter from whole track information (will be set prior to call any other methods)
    
    @Override
    public TrackParametrizable.TrackParametrizer<BacteriaFluo> run(int structureIdx, List<StructureObject> parentTrack) {
        if (parentTrack.get(0).getRawImage(structureIdx)==parentTrack.get(0).getPreFilteredImage(structureIdx)) { // no prefilter -> perform on root
            logger.debug("no prefilters detected: global mean & sigma on root track");
            double[] ms = getRootBckMeanAndSigma(parentTrack, structureIdx, null);
            this.globalBackgroundLevel = ms[0];
            this.globalBackgroundSigma = ms[1];
        } else { // prefilters -> perform on parent track
            logger.debug("prefilters detected: global mean & sigma on parent track");
            Map<Image, ImageMask> imageMapMask = parentTrack.stream().collect(Collectors.toMap(p->p.getPreFilteredImage(structureIdx), p->p.getMask() )); 
            Histogram histo = HistogramFactory.getHistogram(()->Image.stream(imageMapMask, true).parallel(), HistogramFactory.BIN_SIZE_METHOD.AUTO_WITH_LIMITS);
            double[] ms = new double[2];
            BackgroundFit.backgroundFit(histo, 5, ms);
            this.globalBackgroundLevel = ms[0];
            this.globalBackgroundSigma = ms[1];
        } 
        
        Set<StructureObject> voidMC = getVoidMicrochannels(structureIdx, parentTrack);
        double[] thlds = getTrackThresholds(parentTrack, structureIdx, voidMC);
        return (p, s) -> {
            if (voidMC.contains(p)) s.isVoid=true; 
            s.bckThld=thlds[0];
            s.foreThld = thlds[1];
            s.globalBackgroundLevel = globalBackgroundLevel;
            s.globalBackgroundSigma = globalBackgroundSigma;
        };
    }
    
    protected double[] getTrackThresholds(List<StructureObject> parentTrack, int structureIdx, Set<StructureObject> voidMC) {
        if (voidMC.size()==parentTrack.size()) return new double[]{Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        Histogram[] histoRoot=new Histogram[1], histoParent=new Histogram[1];
        Supplier<Histogram> getHistoParent = () -> {
            if (histoParent[0]==null) { 
                Map<Image, ImageMask> imageMapMask = parentTrack.stream().filter(p->!voidMC.contains(p)).collect(Collectors.toMap(p->p.getPreFilteredImage(structureIdx), p->p.getMask() )); 
                histoParent[0] = HistogramFactory.getHistogram(()->Image.stream(imageMapMask, true).parallel(), HistogramFactory.BIN_SIZE_METHOD.AUTO_WITH_LIMITS);
            }
            return histoParent[0];
        };
        boolean needToComputeGlobalMin = this.bckThresholdMethod.getSelectedIndex()>0 && (foregroundSelectionMethod.getSelectedIndex()!=2 || backgroundSel.getSelectedIndex()>0);
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
    
    @Override
    protected double getGlobalThreshold(List<StructureObject> parent, int structureIdx) {
        return globalBackgroundLevel + 5 * globalBackgroundSigma;
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
            logger.debug("found on root {} mean {}, sigma: {}", parents.get(0), parents.get(0).getRoot().getAttribute(meanK, 0d),parents.get(0).getRoot().getAttribute(stdK, 1d));
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
    @Override
    protected void displayAttributes() {
        GUI.log("Background Threshold: "+this.bckThld);
        logger.info("Background Threshold: "+this.bckThld);
        GUI.log("Foreground Threshold: "+this.foreThld);
        logger.info("Foreground Threshold: {}", foreThld);
        GUI.log("Background Mean: "+this.globalBackgroundLevel+" Sigma: "+globalBackgroundSigma);
        logger.info("Background Mean: {} Sigma: {}", globalBackgroundLevel, globalBackgroundSigma);
    }
}
