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
package boa.core.generateXP;

import boa.ui.logger.ExperimentSearchUtils;
import boa.ui.GUI;
import boa.configuration.parameters.TransformationPluginParameter;
import boa.core.Processor;
import boa.core.Processor;
import boa.configuration.experiment.ChannelImage;
import boa.configuration.experiment.Experiment;
import boa.configuration.experiment.Experiment.IMPORT_METHOD;
import boa.configuration.experiment.PreProcessingChain;
import boa.configuration.experiment.Structure;
import boa.configuration.parameters.PreFilterSequence;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.MasterDAOFactory;
import ij.process.AutoThresholder;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import boa.plugins.ObjectFeature;
import boa.plugins.PluginFactory;
import boa.plugins.Transformation;
import boa.plugins.plugins.measurements.BacteriaLineageMeasurements;
import boa.plugins.plugins.measurements.BacteriaTrackingAttributes;
import boa.plugins.plugins.measurements.GrowthRate;
import boa.plugins.plugins.measurements.ObjectFeatures;
import boa.plugins.plugins.measurements.ObjectInclusionCount;
import boa.plugins.plugins.measurements.RelativePosition;
import boa.plugins.plugins.measurements.objectFeatures.object_feature.FeretMax;
import boa.plugins.plugins.measurements.objectFeatures.object_feature.Mean;
import boa.plugins.plugins.measurements.objectFeatures.object_feature.Quality;
import boa.plugins.plugins.measurements.objectFeatures.object_feature.SNR;
import boa.plugins.plugins.measurements.objectFeatures.object_feature.Size;
import boa.plugins.plugins.post_filters.FeatureFilter;
import boa.plugins.plugins.pre_filters.BandPass;
import boa.plugins.plugins.pre_filters.IJSubtractBackground;
import boa.plugins.plugins.processing_pipeline.SegmentAndTrack;
import boa.plugins.plugins.processing_pipeline.SegmentOnly;
import boa.plugins.plugins.processing_pipeline.SegmentThenTrack;
import boa.plugins.plugins.segmenters.BacteriaFluo;
import boa.plugins.plugins.segmenters.MicrochannelFluo2D;
import boa.plugins.plugins.segmenters.MicrochannelPhase2D;
import boa.plugins.plugins.segmenters.SpotSegmenter;
import boa.plugins.plugins.thresholders.IJAutoThresholder;
import boa.plugins.plugins.track_post_filter.RemoveTracksStartingAfterFrame;
import boa.plugins.plugins.track_post_filter.TrackLengthFilter;
import boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker.BacteriaClosedMicrochannelTrackerLocalCorrections;
import boa.plugins.plugins.trackers.MicrochannelTracker;
import boa.plugins.plugins.trackers.ObjectIdxTracker;
import boa.plugins.plugins.transformations.AutoRotationXY;
import boa.plugins.plugins.transformations.CropMicrochannelsPhase2D;
import boa.plugins.plugins.transformations.CropMicrochannelsFluo2D;
import boa.plugins.plugins.transformations.CropMicroChannels;
import boa.plugins.plugins.transformations.Flip;
import boa.plugins.plugins.transformations.ImageStabilizerXY;
import boa.plugins.plugins.transformations.RemoveStripesSignalExclusion;
import boa.plugins.plugins.transformations.SaturateHistogram;
import boa.plugins.plugins.transformations.SaturateHistogramHyperfluoBacteria;
import boa.plugins.plugins.transformations.SimpleCrop;
import boa.plugins.plugins.measurements.Focus;
import boa.plugins.plugins.measurements.objectFeatures.object_feature.MeanAtBorder;
import boa.plugins.plugins.post_filters.RemoveEndofChannelBacteria;
import boa.plugins.plugins.pre_filters.ImageFeature;
import boa.plugins.plugins.track_post_filter.RemoveTrackByFeature;
import boa.plugins.plugins.transformations.AutoFlipY;
import boa.plugins.plugins.transformations.RemoveDeadPixels;
import boa.image.processing.ImageTransformation;
import boa.image.processing.ImageTransformation.MainAxis;
import boa.plugins.plugins.measurements.ContainerObject;
import boa.plugins.plugins.measurements.SimpleTrackMeasurements;
import boa.plugins.plugins.measurements.SpineCoordinates;
import boa.plugins.plugins.measurements.objectFeatures.object_feature.Max;
import boa.plugins.plugins.measurements.objectFeatures.object_feature.Min;
import boa.plugins.plugins.measurements.objectFeatures.object_feature.SpineLength;
import boa.plugins.plugins.measurements.objectFeatures.object_feature.Thickness;
import boa.plugins.plugins.measurements.objectFeatures.object_feature.ThicknessAxis;
import boa.plugins.plugins.post_filters.BinaryClose;
import boa.plugins.plugins.post_filters.FillHoles2D;
import boa.plugins.plugins.segmenters.BacteriaPhaseContrast;
import boa.plugins.plugins.thresholders.BackgroundFit;
import boa.plugins.plugins.track_post_filter.FitRegionsToEdges;
import boa.plugins.plugins.track_post_filter.PostFilter;
import boa.plugins.plugins.track_pre_filters.NormalizeTrack;
import boa.plugins.plugins.track_pre_filters.SubtractBackgroundMicrochannels;
import boa.plugins.plugins.trackers.NestedSpotTracker;
import boa.plugins.plugins.transformations.TypeConverter;


/**
 *
 * @author jollion
 */
public class GenerateXP {
    public static final Logger logger = LoggerFactory.getLogger(GenerateXP.class);
    static Experiment.IMPORT_METHOD importMethod;
    
    
    public static Experiment generateXPFluo(String name, String outputDir, boolean setUpPreProcessing, boolean mutationHighBck, int trimFramesStart, int trimFramesEnd, double scaleXY, int[] crop) {
        
        Experiment xp = new Experiment(name);
        if (importMethod==null) xp.setImportImageMethod(IMPORT_METHOD.ONE_FILE_PER_CHANNEL_POSITION);
        else xp.setImportImageMethod(importMethod);
        xp.getChannelImages().insert(new ChannelImage("RFP", "_REF"), new ChannelImage("YFP", ""));
        xp.setOutputDirectory(outputDir);
        Structure mc = new Structure("Microchannels", -1, 0);
        Structure bacteria = new Structure("Bacteria", 0, 0).setAllowSplit(true);
        Structure mutation = new Structure("Spots", 0, 1, 1);
        xp.getStructures().insert(mc, bacteria, mutation);
        setParametersFluo(xp, true, mutationHighBck, true);
        
        if (setUpPreProcessing) setPreprocessingFluo(xp.getPreProcessingTemplate(), trimFramesStart, trimFramesEnd, scaleXY, crop);
        return xp;
    }
    public static void setPreprocessingFluo(PreProcessingChain ps, int trimFramesStart, int trimFramesEnd, double scaleXY, int[] crop) {
        ps.removeAllTransformations();
        ps.setFrameDuration(120);
        if (!Double.isNaN(scaleXY)) ps.setCustomScale(scaleXY, 1);
        if (crop!=null) ps.addTransformation(0, null, new SimpleCrop(crop));
        ps.setTrimFrames(trimFramesStart, trimFramesEnd);
        ps.addTransformation(1, null, new RemoveDeadPixels(20, 4)); // for reminiscent pixels
        ps.addTransformation(1, null, new RemoveDeadPixels(35, 1)); // for non-reminiscent pixels
        ps.addTransformation(0, null, new RemoveStripesSignalExclusion(0));
        ps.addTransformation(1, null, new RemoveStripesSignalExclusion(0).setSecondSignalExclusion(1, new BackgroundFit(10))); // add secondary mask in case of non-bacteria "contaminents" -> high fluo in mutation and no fluo in bacteria
        ps.addTransformation(0, null, new SaturateHistogramHyperfluoBacteria());
        ps.addTransformation(0, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXVAR).setRemoveIncompleteRowsAndColumns(false).setMaintainMaximum(true));
        ps.addTransformation(0, null, new AutoFlipY().setMethod(AutoFlipY.AutoFlipMethod.FLUO_HALF_IMAGE));
        //ps.addTransformation(1, new int[]{1}, new SimpleTranslation(1, flip?-1:1, 0).setInterpolationScheme(ImageTransformation.InterpolationScheme.NEAREST)).setActivated(true); // nearest -> translation entiers
        //ps.addTransformation(0, null, new Flip(ImageTransformation.Axis.Y)).setActivated(flip);
        CropMicroChannels cropper = new CropMicrochannelsFluo2D(350, 45, 200, 0.5, 10);
        ps.addTransformation(0, null, cropper).setActivated(true);
        ps.addTransformation(-1, null, new TypeConverter().setLimitTo16((short)1000));
        
        //ps.addTransformation(0, null, new ImageStabilizerXY(1, 1000, 1e-8, 20).setAdditionalTranslation(1, 1, 0).setCropper(cropper)).setActivated(false); // additional translation to correct chromatic shift
    }
    public static void setPreprocessingPhase(PreProcessingChain ps, int trimFramesStart, int trimFramesEnd, double scaleXY) {
        ps.setFrameDuration(4);
        ps.setTrimFrames(trimFramesStart, trimFramesEnd);
        ps.removeAllTransformations();
        if (!Double.isNaN(scaleXY)) ps.setCustomScale(scaleXY, 1);
        ps.addTransformation(0, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXARTEFACT).setPrefilters(new IJSubtractBackground(10, true, false, true, true)).setRemoveIncompleteRowsAndColumns(false).setMaintainMaximum(true));
        ps.addTransformation(0, null, new AutoFlipY().setMethod(AutoFlipY.AutoFlipMethod.PHASE));
        ps.addTransformation(0, null, new CropMicrochannelsPhase2D());
        ps.addTransformation(-1, null, new TypeConverter().setLimitTo16((short)0));
    }
    public static void setPreprocessingTransAndMut(PreProcessingChain ps, int trimFramesStart, int trimFramesEnd, double scaleXY) {
        ps.removeAllTransformations();
        setPreprocessingPhase(ps, trimFramesStart, trimFramesEnd, scaleXY );
        ps.addTransformation(1, null, new RemoveStripesSignalExclusion(-1));
        //ps.addTransformation(1, new int[]{1}, new SimpleTranslation(1, 0, 0).setInterpolationScheme(ImageTransformation.InterpolationScheme.NEAREST)).setActivated(false); // nearest -> translation entiers //flip?-1:1 depends on FLIP !!!
    }
    public static void setParametersFluo(Experiment xp, boolean processing, boolean mutationHighBck, boolean measurements) {
        Structure mc = xp.getStructure(0);
        Structure bacteria = xp.getStructure(1).setAllowSplit(true);
        Structure mutation = xp.getStructure(2);
        mutation.setSegmentationParentStructure(1);
        if (processing) {
            mc.setProcessingPipeline(new SegmentAndTrack(
                    new MicrochannelTracker().setSegmenter(new MicrochannelFluo2D()
                    ).setTrackingParameters(40, 0.5).setYShiftQuantile(0.05).setWidthQuantile(0.9)
                    ).addTrackPostFilters(
                            //new RemoveMicrochannelsWithOverexpression(99, 5).setTrim(true),
                            new TrackLengthFilter().setMinSize(5), 
                            new RemoveTracksStartingAfterFrame())
            );
            bacteria.setProcessingPipeline(new SegmentAndTrack(
                            new BacteriaClosedMicrochannelTrackerLocalCorrections().setSegmenter(new BacteriaFluo()).setCostParameters(0.25, 1.25)
                    ).addTrackPostFilters(
                            new PostFilter(new RemoveEndofChannelBacteria().setContactSidesProportion(0.5)).setDeleteMethod(2).setMergePolicy(PostFilter.MERGE_POLICY.MERGE_TRACKS_BACT_SIZE_COND), 
                            new RemoveTrackByFeature().setMergePolicy(PostFilter.MERGE_POLICY.MERGE_TRACKS_BACT_SIZE_COND).setFeature(new Size().setScaled(false), 150, true).setQuantileValue(0.25)
                    )
            );
            // modification of scaling: lap * 2.5, gauss * scale (=2) quality * 2.23
            mutation.setProcessingPipeline(new SegmentAndTrack(
                    new NestedSpotTracker().setCompartimentStructure(1).setSegmenter(new SpotSegmenter(!mutationHighBck ? 2.75 : 3, !mutationHighBck ? 2 : 2.75, !mutationHighBck ? 1.6 : 2).setScale(2.5)  // was 0.9, 0.65, 0.9, scale was 2 for mutH
                ).setSpotQualityThreshold(3.122) // 4.46 for mutH ? 
                            .setLinkingMaxDistance(0.4, 0.77).setGapParameters(0.75, 0.15, 1)
            ).addPreFilters(new BandPass(0, 7, 0, 0) // was 10
            ).addPostFilters(new FeatureFilter(new Quality(), !mutationHighBck ?2:2.2, true, true)));
        }
        if (measurements) {
            xp.clearMeasurements();
            xp.addMeasurement(new BacteriaLineageMeasurements(1));
            xp.addMeasurement(new SimpleTrackMeasurements(1));
            xp.addMeasurement(new ObjectFeatures(1).addFeatures(new Mean().setIntensityStructure(1)));
            //xp.addMeasurement(new ObjectFeatures(1).addFeature(new MeanAtBorder().setIntensityStructure(1), "IntensityBorderMeanGrad").addPreFilter(new ImageFeature().setFeature(ImageFeature.Feature.GRAD).setScale(2.5)));
            xp.addMeasurement(new ContainerObject(2, 1));
            xp.addMeasurement(new SimpleTrackMeasurements(2));
            xp.addMeasurement(new SimpleTrackMeasurements(0));
            //xp.addMeasurement(new SpineCoordinates(2, 1));
            xp.addMeasurement(new ObjectFeatures(2).addFeatures(new Mean(), new Max(), new Min()));
            xp.addMeasurement(new ObjectInclusionCount(1, 2, 10).setMeasurementName("MutationNumber"));
            xp.addMeasurement(new ObjectFeatures(2).addFeatures(new Quality()));
            xp.addMeasurement(new RelativePosition(1, 0, RelativePosition.REF_POINT.GEOM_CENTER, RelativePosition.REF_POINT.UPPER_LEFT_CORNER).setMeasurementName("Center"));
            xp.addMeasurement(new GrowthRate().saveSizeAtDivision(true).setFeature(new Size().setScaled(true), true).setSuffix("Area"));
            xp.addMeasurement(new GrowthRate().saveSizeAtDivision(true).setFeature(new FeretMax().setScaled(true), true).setSuffix("Length"));
            //xp.addMeasurement(new GrowthRate().saveSizeAtDivision(true).setFeature(new SpineLength().setScaled(true), true).setSuffix("SpineLength"));
        }
    }
    
    public static Experiment generateXPPhase(String name, String outputDir, boolean setUpPreProcessing, int trimFramesStart, int trimFramesEnd, double scaleXY) {
        Experiment xp = new Experiment(name);
        xp.setImportImageMethod(importMethod==null ? Experiment.IMPORT_METHOD.SINGLE_FILE : importMethod); // Was single file
        xp.getChannelImages().insert(new ChannelImage("PhaseContrast", ""));
        xp.setOutputDirectory(outputDir);
        Structure mc = new Structure("Microchannels", -1, 0);
        Structure bacteria = new Structure("Bacteria", 0, 0).setAllowSplit(true);
        xp.getStructures().insert(mc, bacteria);
        setParametersPhase(xp, true, true);
        if (setUpPreProcessing) setPreprocessingPhase(xp.getPreProcessingTemplate(), trimFramesStart, trimFramesEnd, scaleXY);
        return xp;
    }
    public static void deletePositions(Experiment xp, boolean[] deletePositions) {
        if (deletePositions!=null) {
            if (deletePositions.length!=xp.getPositionCount()) logger.error("Delete array has {} elements and dataset has: {} imported positions", deletePositions.length, xp.getPositionCount());
            else {
                List<String> toDelete = new ArrayList<>();
                String[] names = xp.getPositionsAsString();
                for (int i = 0; i<deletePositions.length; ++i) if (deletePositions[i]) toDelete.add(names[i]);
                for (String p : toDelete) {
                    xp.getPosition(p).eraseData(); // deletes images - 
                    xp.getPosition(p).removeFromParent(); // remove from parent
                }
            }
        }
    }
    public static void setFlip(Experiment xp, boolean[] flipArray) {
        if (flipArray!=null) {
            if (flipArray.length!=xp.getPositionCount()) logger.error("Flip array has {} elements and dataset has: {} imported positions", flipArray.length, xp.getPositionCount());
            else {
                for (int i = 0; i<flipArray.length; ++i) {
                    List<TransformationPluginParameter<Transformation>> transfo = xp.getPosition(i).getPreProcessingChain().getTransformations(false);
                    for (TransformationPluginParameter<Transformation> tp : transfo) if (tp.instanciatePlugin().getClass()==Flip.class) {
                        tp.setActivated(flipArray[i]);
                        //logger.debug("{} flip: {}", i, flipArray[i]);
                    }
                }
            }
        }
    }
    public static void setParametersPhase(Experiment xp, boolean processing, boolean measurements) {
        Structure mc = xp.getStructure(0);
        Structure bacteria = xp.getStructure(1);
        if (processing) {
            SegmentAndTrack mcpc = new SegmentAndTrack(new MicrochannelTracker().setSegmenter(new MicrochannelPhase2D()).setAllowGaps(false));
            mcpc.addPreFilters(new IJSubtractBackground(10, true, false, true, false));
            mcpc.addTrackPostFilters(
                new TrackLengthFilter().setMinSize(10),  
                new RemoveTracksStartingAfterFrame(),
                new FitRegionsToEdges()
                //new AverageMask()
            );
            
            mc.setProcessingPipeline(mcpc);
            bacteria.setProcessingPipeline(new SegmentAndTrack(
                            new BacteriaClosedMicrochannelTrackerLocalCorrections()
                            .setSegmenter(new BacteriaPhaseContrast())
                            .setCostParameters(0.2, 2)
                            .setSizeFeature(0)
                    ).addTrackPreFilters(
                        //new PreFilter(new BandPass(0, 150, 2, 1)),
                        new SubtractBackgroundMicrochannels(),
                        new NormalizeTrack(1, true)
                    ).addPostFilters(
                            new FeatureFilter(new ThicknessAxis().setAxis(MainAxis.X), 6, true, true), 
                            new FeatureFilter(new Size().setScaled(false), 200, true, true), 
                            new BinaryClose(5),
                            new FillHoles2D()
                    ).addTrackPostFilters(
                            new PostFilter(new RemoveEndofChannelBacteria()).setDeleteMethod(2).setMergePolicy(PostFilter.MERGE_POLICY.MERGE_TRACKS_BACT_SIZE_COND), 
                            new RemoveTrackByFeature().setMergePolicy(PostFilter.MERGE_POLICY.MERGE_TRACKS_BACT_SIZE_COND).setFeature(new Size().setScaled(false), 10, true).setStatistics(2)
                    )
            );
            /*bacteria.setProcessingScheme(
                    new SegmentAndTrack(
                            new BacteriaClosedMicrochannelTrackerLocalCorrections()
                            .setSegmenter(new BacteriaTrans())
                            .setCostParameters(1.5, 3).setThresholdParameters(0, 1, 25, 15) // was 2
                    )) // was not
            );*/
            
        }
        if (measurements) {
            xp.clearMeasurements();
            xp.addMeasurement(new BacteriaLineageMeasurements(1));
            xp.addMeasurement(new BacteriaTrackingAttributes(1));
            xp.addMeasurement(new SimpleTrackMeasurements(1));
            xp.addMeasurement(new SimpleTrackMeasurements(0));
            xp.addMeasurement(new RelativePosition(1, 0, RelativePosition.REF_POINT.GEOM_CENTER, RelativePosition.REF_POINT.UPPER_LEFT_CORNER).setMeasurementName("BacteriaCenter"));
            xp.addMeasurement(new ObjectFeatures(1).addFeatures(new Thickness()));
            xp.addMeasurement(new GrowthRate().saveSizeAtDivision(true).setFeature(new Size().setScaled(true), true).setSuffix("Area"));
            xp.addMeasurement(new GrowthRate().saveSizeAtDivision(true).setFeature(new FeretMax().setScaled(true), true).setSuffix("Length"));
            //xp.addMeasurement(new GrowthRate().saveSizeAtDivision(true).setFeature(new SpineLength().setScaled(true), true).setSuffix("SpineLength"));
        }
    }
    
    public static boolean[] getBooleanArray(int N, boolean defaultValue) {
        boolean[] res= new boolean[N];
        if (defaultValue) Arrays.fill(res, true);
        return res;
    }
    public static boolean[] fillRange(boolean[] array, int idxMinIncluded, int idxMaxIncluded, boolean value) {
        for (int i = idxMinIncluded; i<=idxMaxIncluded; ++i) array[i] = value;
        return array;
    }
    public static boolean[] setValues(boolean[] array, boolean value, int... indices) {
        for (int i : indices) array[i]=value;
        return array;
    }
}
