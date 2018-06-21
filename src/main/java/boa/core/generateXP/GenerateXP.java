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
import boa.configuration.experiment.Experiment.ImportImageMethod;
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
import boa.plugins.plugins.measurements.BacteriaFluoMeasurements;
import boa.plugins.plugins.measurements.BacteriaLineageMeasurements;
import boa.plugins.plugins.measurements.BacteriaMeasurementsWoleMC;
import boa.plugins.plugins.measurements.BacteriaPhaseMeasurements;
import boa.plugins.plugins.measurements.GrowthRate;
import boa.plugins.plugins.measurements.ObjectFeatures;
import boa.plugins.plugins.measurements.MutationMeasurements;
import boa.plugins.plugins.measurements.MutationTrackMeasurements;
import boa.plugins.plugins.measurements.ObjectInclusionCount;
import boa.plugins.plugins.measurements.RelativePosition;
import boa.plugins.plugins.measurements.TrackLength;
import boa.plugins.plugins.measurements.objectFeatures.FeretMax;
import boa.plugins.plugins.measurements.objectFeatures.Mean;
import boa.plugins.plugins.measurements.objectFeatures.Quality;
import boa.plugins.plugins.measurements.objectFeatures.SNR;
import boa.plugins.plugins.measurements.objectFeatures.Size;
import boa.plugins.plugins.post_filters.FeatureFilter;
import boa.plugins.plugins.pre_filters.BandPass;
import boa.plugins.plugins.pre_filters.IJSubtractBackground;
import boa.plugins.plugins.processing_pipeline.SegmentAndTrack;
import boa.plugins.plugins.processing_pipeline.SegmentOnly;
import boa.plugins.plugins.processing_pipeline.SegmentThenTrack;
import boa.plugins.plugins.segmenters.BacteriaIntensity;
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
import boa.plugins.plugins.measurements.objectFeatures.MeanAtBorder;
import boa.plugins.plugins.post_filters.RemoveEndofChannelBacteria;
import boa.plugins.plugins.pre_filters.ImageFeature;
import boa.plugins.plugins.thresholders.BackgroundThresholder;
import boa.plugins.plugins.track_post_filter.RemoveMicrochannelsTouchingBackgroundOnSides;
import boa.plugins.plugins.track_post_filter.RemoveMicrochannelsWithOverexpression;
import boa.plugins.plugins.track_post_filter.RemoveSaturatedMicrochannels;
import boa.plugins.plugins.track_post_filter.RemoveTrackByFeature;
import boa.plugins.plugins.track_post_filter.SegmentationPostFilter;
import boa.plugins.plugins.transformations.AutoFlipY;
import boa.plugins.plugins.transformations.RemoveDeadPixels;
import boa.image.processing.ImageTransformation;
import boa.image.processing.ImageTransformation.MainAxis;
import boa.plugins.plugins.measurements.objectFeatures.SpineLength;
import boa.plugins.plugins.measurements.objectFeatures.ThicknessAxis;
import boa.plugins.plugins.post_filters.BinaryClose;
import boa.plugins.plugins.post_filters.FillHoles2D;
import boa.plugins.plugins.post_filters.FitMicrochannelHeadToEdges;
import boa.plugins.plugins.segmenters.BacteriaIntensityPhase;
import boa.plugins.plugins.thresholders.BackgroundFit;
import boa.plugins.plugins.track_post_filter.AverageMask;
import boa.plugins.plugins.track_post_filter.PostFilter;
import boa.plugins.plugins.track_pre_filters.NormalizeTrack;
import boa.plugins.plugins.track_pre_filters.PreFilter;
import boa.plugins.plugins.track_pre_filters.PreFilters;
import boa.plugins.plugins.track_pre_filters.Saturate;
import boa.plugins.plugins.track_pre_filters.SubtractBackgroundMicrochannels;
import boa.plugins.plugins.trackers.MutationTrackerSpine;
import boa.plugins.plugins.transformations.TypeConverter;


/**
 *
 * @author jollion
 */
public class GenerateXP {
    public static final Logger logger = LoggerFactory.getLogger(GenerateXP.class);
     static Experiment.ImportImageMethod importMethod;
    public static void main(String[] args) {
        PluginFactory.findPlugins("boa.plugins.plugins");
        MasterDAOFactory.setCurrentType(MasterDAOFactory.DAOType.DBMap);
        
        /*String dbName = "dummyXP";
        String inputDir = "!!not a directory";
        String outputDir = "!!not a directory";
        boolean flip = true;
        boolean fluo = true;
        */
        int trimStart=0, trimEnd=0;
        int[] cropXYdXdY=null;
        double scaleXY = Double.NaN;
        boolean[] flipArray= null;
        boolean[] deletePositions = null;
        //////// FLUO
        
        
        String dbName = "boa_fluo170120_wt";
        String inputDir = "/data/Images/Fluo/film170120/me120rc2-laser1-lbiptg-20012017/";
        String outputDir = "/data/Images/Fluo/film170120_test/Output";
        boolean flip = false; 
        boolean fluo = true;
        importMethod = Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD;
        
        /*String dbName = "boa_fluo170117_GammeMutTrack";
        String inputDir = "/data/Images/FastMutTrack/170117GammeMutTrack/input";
        String outputDir = "/data/Images/FastMutTrack/170117GammeMutTrack/output";
        boolean flip = false; 
        boolean fluo = true;
        */
        
        // Ordi LJP
        /*String dbName = "boa_fluo151130_OutputNewScaling";
        String outputDir = "/data/Images/Fluo/film151130/OutputNewScaling";
        String inputDir = "/data/Images/Fluo/film151130/ME120R63-30112015-lr62r1";
        boolean flip = true;
        boolean fluo = true;
        importMethod = Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD;
        */
        
        /*String dbName = "boa_fluo151127_test";
        String outputDir = "/data/Images/Fluo/film151127/OutputTest";
        String inputDir = "/data/Images/Fluo/film151127/ME121R-27112015-laser";
        boolean flip = true; 
        boolean fluo = true;
        importMethod = Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD;
        */
        // mutH. blink
        /*String dbName = "boa_fluo151127";
        String outputDir = "/data/Images/Fluo/film151127/Output";
        String inputDir = "/data/Images/Fluo/film151127/ME121R-27112015-laser";
        boolean flip = true; 
        boolean fluo = true;
        importMethod = Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD;
        */
        
        /*String dbName = "boa_fluo160218";
        String inputDir = "/data/Images/Fluo/film160218/ME120R63-18022016-LR62r";
        String outputDir = "/data/Images/Fluo/film160218/Output";
        boolean flip = false;
        boolean fluo = true;
        importMethod = Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD;
        */
        
        /*
        String dbName = "boa_fluo160217";
        String inputDir = "/data/Images/Fluo/film160217/ME120R63-17022016-LR62r";
        String outputDir = "/data/Images/Fluo/film160217/Output";
        boolean flip = false;
        boolean fluo = true;
        importMethod = Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD;
        // ATTENTION 10 champs (12->21) pour lesquels 115TP pour le fichier mutation et 114TP pour le fichier REF dans un dossier séparé
        */
        
        
        /*String dbName = "boa_fluo160212";
        String inputDir = "/data/Images/Fluo/film160212/ImagesSubset0-120/";
        String outputDir = "/data/Images/Fluo/film160212/Output";
        boolean flip = true;
        boolean fluo = true;
        importMethod = Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD;
        */
        /*
        // moitié droite des cannaux hors focus + perte de focus après 700
        String dbName = "boa_fluo160311";
        String inputDir = "/data/Images/Fluo/film160311/ME121R-11032016-lr62r2/";
        String outputDir = "/data/Images/Fluo/film160311/Output/";
        boolean flip = true;
        boolean fluo = true;
        trimEnd=800;
        cropXYdXdY=new int[]{0, 512, 512, 1024};
        importMethod = Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD;
        */
        /*
        // perte de focus seulement 300 frames
        String dbName = "boa_fluo160314";
        String inputDir = "/data/Images/Fluo/film160314/ME121R-14032016-lr62r2/";
        String outputDir = "/data/Images/Fluo/film160314/Output/";
        boolean flip = true;
        boolean fluo = true;
        importMethod = Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD;
        */
        
        /*String dbName = "boa_fluo160407";
        String inputDir = "/data/Images/Fluo/film160407/me121r-lr62r2-07042016";
        String outputDir = "/data/Images/Fluo/film160407/Output/";
        boolean flip = false;
        boolean fluo = true;
        importMethod = Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD;
        */
        /*
        // bactérie sortent des cannaux
        String dbName = "boa_fluo160408";
        String inputDir = "/data/Images/Fluo/film160408/me121r-laser1-08042016/";
        String outputDir = "/data/Images/Fluo/film160408/Output/";
        boolean flip = true;
        boolean fluo = true;
        importMethod = Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD;
        */
        
        
        /*String dbName = "boa_fluo160428";
        String inputDir = "/data/Images/Fluo/film160428/63121r-laser1-28042016/";
        String outputDir = "/data/Images/Fluo/film160428/Output/";
        boolean flip = true;
        boolean fluo = true;
        trimStart = 40;
        importMethod = Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD;
        */
        
        /*String dbName = "boa_fluo160501";
        String inputDir = "/data/Images/Fluo/film160501/me121r-lr62rep2-01052016/";
        String outputDir = "/data/Images/Fluo/film160501/Output";
        boolean flip = true;
        boolean fluo = true;
        importMethod = Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD;
        */
        //////////// Trans
       /* String dbName = "boa_testBF";
        String inputDir = "/data/Images/Lydia/testJeanFilm";
        String outputDir = "/data/Images/Lydia/Output";
        boolean flip = true;
        boolean fluo = false;
        scaleXY = 0.0646;*/
        
        /*String dbName = "boa_phase140115mutH";
        String inputDir = "/data/Images/Phase/140115_6300_mutH_LB-LR62rep/6300_mutH_LB-LR62rep-15012014_tif/";
        String outputDir = "/data/Images/Phase/140115_6300_mutH_LB-LR62rep/Output";
        deletePositions = fillRange(getBooleanArray(92, false), 54, 91, true); // a partir de la position 55 -> suppr
        boolean flip = true;
        boolean fluo = false;
        transSingleFileImport=false;
        scaleXY = 0.06289;*/
        /*
        // beaucoup de files corrompue, pas d'intervalle.. 
        String dbName = "boa_phase141113wt";
        String inputDir = "/data/Images/Phase/141113_mg6300_wt/mg6300wt-lb-lr62/";
        String outputDir = "/data/Images/Phase/141113_mg6300_wt/Output";
        boolean flip = true;
        boolean fluo = false;
        transSingleFileImport=false;
        scaleXY = 0.06289;
        */
        
        /*String dbName = "boa_phase141107wt";
        String inputDir = "/data/Images/Phase/141107_mg6300_wt/mg6300WT-lb-lr62replic1-7-11-14/";
        String outputDir = "/data/Images/Phase/141107_mg6300_wt/Output";
        trimStart = 122;
        trimEnd = 990;
        boolean flip = true;
        boolean fluo = false;
        transSingleFileImport=false;
        scaleXY = 0.06289;
        */
        
        
        /*String dbName = "boa_phase150324mutH";
        String inputDir = "/data/Images/Phase/150324_6300_mutH/6300_mutH_LB_LR62silicium-24032015-tif/";
        String outputDir = "/data/Images/Phase/150324_6300_mutH/Output";
        boolean flip = false;
        flipArray = fillRange(getBooleanArray(100, false), 63, 99, true);
        boolean fluo = false;
        transSingleFileImport=false;
        scaleXY = 0.06289;
        */
        
        /*String dbName = "boa_phase150324mutHNoSub";
        String inputDir = "/data/Images/Phase/150324_6300_mutH/6300_mutH_LB_LR62silicium-24032015-tif/";
        String outputDir = "/data/Images/Phase/150324_6300_mutH/OutputNoSub";
        boolean flip = false;
        flipArray = fillRange(getBooleanArray(100, false), 63, 99, true);
        boolean fluo = false;
        transSingleFileImport=false;
        scaleXY = 0.06289;
        subTransPre = false;
        */
        
        /*String dbName = "boa_phase150616wt";
        // cette manip contient des images hors focus
        String inputDir = "/data/Images/Phase/150616_6300_wt/6300_WT_LB_LR62silicium_16062015_tif/";
        String outputDir = "/data/Images/Phase/150616_6300_wt/Output";
        boolean flip = false;
        boolean fluo = false;
        transSingleFileImport=false;
        scaleXY = 0.06289;
        flipArray = fillRange(getBooleanArray(96, false), 61, 95, true); //pos 62 - 96 -> flip = true
        deletePositions = setValues(getBooleanArray(96, false), true, 5, 11, 14, 29, 43, 45, 47, 61, 65); // xy06 xy44 xy48 xy62  -> seulement un frame OOF, les autres beaucoup de frames
        */
        
        ////////////////////////////
        // Ordi Portable
        
        /*String dbName = "boa_fluo170120_wt";
        String inputDir = "/home/jollion/Documents/LJP/DataLJP/Fluo1701/me120rc2-laser1-lbiptg-20012017";
        String outputDir = "/home/jollion/Documents/LJP/DataLJP/Fluo1701/Output";
        boolean flip = false; 
        boolean fluo = true;
        importMethod = Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD;
        */
        /*
        String dbName = "testSub";
        String outputDir = "/home/jollion/Documents/LJP/DataLJP/TestOutput";
        String inputDir = "/home/jollion/Documents/LJP/DataLJP/testsub"; 
        boolean flip = true;
        boolean fluo = true;
        */     
        
        /*String dbName = "boa_fluo151127";
        String outputDir = "/home/jollion/Documents/LJP/DataLJP/Fluo151127/Output";
        String inputDir = "/home/jollion/Documents/LJP/DataLJP/Fluo151127/champ1_0-49";
        boolean flip = true;
        boolean fluo = true;
        */
        
        /*String dbName = "boa_testBF";
        String inputDir = "/home/jollion/Documents/LJP/DataLJP/SampleImageTrans/input/";
        String outputDir = "/home/jollion/Documents/LJP/DataLJP/SampleImageTrans/output/";
        boolean flip = true;
        boolean fluo = false;
        scaleXY = 0.0646;*/
        
        /*String dbName = "boa_mutd5_141209";
        String inputDir = "/data/Images/Phase/09122014_mutd5_lb-lr62repl/mg6300mutd5_LB_lr62replic2_oil37.nd2";
        String outputDir = "/data/Images/Phase/09122014_mutd5_lb-lr62repl/Output";
        boolean flip = true;
        boolean fluo = false;
        */
        
        /*String dbName = "boa_mutH_150120";
        String inputDir = "/media/jollion/4336E5641DA22135/LJP/phase/phase150120/6300_mutH_LB_LR62rep_20012015_nd2/mg6300mutH_LB_lr62rep_oil37.nd2";
        String outputDir = "/media/jollion/4336E5641DA22135/LJP/phase/phase150120/Output";
        boolean flip = true;
        boolean fluo = false;*/
        
        /*String dbName = "boa_mutH_140115";
        String inputDir = "/media/jollion/4336E5641DA22135/LJP/phase/phase140115/6300_mutH_LB-LR62rep-15012014_nd2/mg6300mutH_LB_lr62rep_oil37.nd2";
        String outputDir = "/media/jollion/4336E5641DA22135/LJP/phase/phase140115/Output";
        deletePositions = fillRange(getBooleanArray(95, false), 55, 94); // a partir de la position 55 -> suppr
        boolean flip = true;
        boolean fluo = false;*/
        
        /*String dbName = "boa_phase140115wt";
        String inputDir = "/media/jollion/4336E5641DA22135/LJP/phase/phase_mg6300wtlbl1/mg6300wt-lb-laser1-oil37/";
        String outputDir = "/media/jollion/4336E5641DA22135/LJP/phase/phase_mg6300wtlbl1/Output";
        boolean flip = true;
        boolean fluo = false;
        importMethod = Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_TIME_POSITION;*/
        
        /*String dbName = "boa_phase141107wt";
        String inputDir = "/media/jollion/4336E5641DA22135/LJP/phase/phase141107/mg6300WT-lb-lr62replic1-7-11-14/";
        String outputDir = "/media/jollion/4336E5641DA22135/LJP/phase/phase141107/Output";
        boolean flip = true;
        boolean fluo = false;
        importMethod = Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_TIME_POSITION;
        
        deletePositions = fillRange(getBooleanArray(991, true), 120, 990, false);
        */
        boolean performProcessing = false;
        
        String configDir = MasterDAOFactory.getCurrentType().equals(MasterDAOFactory.DAOType.DBMap) ? new File(outputDir).getParent() : "localhost";
        if (MasterDAOFactory.getCurrentType().equals(MasterDAOFactory.DAOType.DBMap)) ExperimentSearchUtils.removePrefix(dbName, GUI.DBprefix);
        MasterDAO mDAO = MasterDAOFactory.createDAO(dbName, configDir);
        MasterDAO.deleteObjectsAndSelectionAndXP(mDAO);
        Experiment xp = fluo ? generateXPFluo(ExperimentSearchUtils.removePrefix(dbName, GUI.DBprefix), outputDir, true, true, trimStart, trimEnd, scaleXY, cropXYdXdY) : generateXPPhase(ExperimentSearchUtils.removePrefix(dbName, GUI.DBprefix), outputDir, true, trimStart, trimEnd, scaleXY); 
        mDAO.setExperiment(xp);
        
        Processor.importFiles(xp, true, null, inputDir);
        setFlip(xp, flipArray);
        deletePositions(xp, deletePositions);
        if (performProcessing) {
            try {
                Processor.preProcessImages(mDAO);
            } catch (Exception ex) {
                logger.error("Error while preprocessing", ex);
            }
            Processor.processAndTrackStructures(mDAO, true, 0);
        }
        mDAO.updateExperiment(); // save changes
    }
    
    
    public static Experiment generateXPFluo(String name, String outputDir, boolean setUpPreProcessing, boolean mutationHighBck, int trimFramesStart, int trimFramesEnd, double scaleXY, int[] crop) {
        
        Experiment xp = new Experiment(name);
        if (importMethod==null) xp.setImportImageMethod(ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD);
        else xp.setImportImageMethod(importMethod);
        xp.getChannelImages().insert(new ChannelImage("RFP", "_REF"), new ChannelImage("YFP", ""));
        xp.setOutputDirectory(outputDir);
        Structure mc = new Structure("Microchannel", -1, 0);
        Structure bacteria = new Structure("Bacteria", 0, 0).setAllowSplit(true);
        Structure mutation = new Structure("Mutation", 0, 1, 1);
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
            mc.setProcessingScheme(new SegmentAndTrack(
                    new MicrochannelTracker().setSegmenter(new MicrochannelFluo2D()
                    ).setTrackingParameters(40, 0.5).setYShiftQuantile(0.05).setWidthQuantile(0.9)
                    ).addTrackPostFilters(new RemoveMicrochannelsTouchingBackgroundOnSides(2),
                            new RemoveMicrochannelsWithOverexpression(99, 5).setTrim(true),
                            new TrackLengthFilter().setMinSize(5), 
                            new RemoveTracksStartingAfterFrame())
            );
            bacteria.setProcessingScheme(new SegmentAndTrack(
                            new BacteriaClosedMicrochannelTrackerLocalCorrections().setSegmenter(new BacteriaIntensity()).setCostParameters(0.25, 1.25)
                    ).addTrackPostFilters(
                            new SegmentationPostFilter().setDeleteMethod(2).setMergePolicy(PostFilter.MERGE_POLICY.MERGE_TRACKS_SIZE_COND).addPostFilters(new RemoveEndofChannelBacteria()), 
                            new RemoveTrackByFeature().setMergePolicy(PostFilter.MERGE_POLICY.MERGE_TRACKS_SIZE_COND).setFeature(new Size(), 150, true).setQuantileValue(0.25)
                    )
            );
            // modification of scaling: lap * 2.5, gauss * scale (=2) quality * 2.23
            mutation.setProcessingScheme(new SegmentAndTrack(
                    new MutationTrackerSpine().setCompartimentStructure(1).setSegmenter(new SpotSegmenter(!mutationHighBck ? 2.75 : 3, !mutationHighBck ? 2 : 2.75, !mutationHighBck ? 1.6 : 2).setScale(2.5)  // was 0.9, 0.65, 0.9, scale was 2 for mutH
                ).setSpotQualityThreshold(3.122) // 4.46 for mutH ? 
                            .setLinkingMaxDistance(0.75, 0.77).setGapParameters(0.75, 0.15, 3)
            ).addPreFilters(new BandPass(0, 7, 0, 0) // was 10
            ).addPostFilters(new FeatureFilter(new Quality(), !mutationHighBck ?2:2.2, true, true)));
        }
        if (measurements) {
            xp.clearMeasurements();
            xp.addMeasurement(new BacteriaLineageMeasurements(1, "BacteriaLineage"));
            xp.addMeasurement(new ObjectFeatures(1).addFeatures(new Size().setScale(true), new FeretMax().setScale(true), new Mean().setIntensityStructure(1)));
            xp.addMeasurement(new ObjectFeatures(1).addFeature(new MeanAtBorder().setIntensityStructure(1), "IntensityBorderMeanGrad").addPreFilter(new ImageFeature().setFeature(ImageFeature.Feature.GRAD).setScale(2.5)));
            xp.addMeasurement(new MutationTrackMeasurements(1, 2));
            xp.addMeasurement(new ObjectInclusionCount(1, 2, 10).setMeasurementName("MutationNumber"));
            xp.addMeasurement(new ObjectFeatures(2).addFeatures(new Quality()));
            xp.addMeasurement(new RelativePosition(1, 0, RelativePosition.REF_POINT.GEOM_CENTER, RelativePosition.REF_POINT.UPPER_LEFT_CORNER).setMeasurementName("Center"));
            //xp.addMeasurement(new Focus(1));
            xp.addMeasurement(new GrowthRate().setFeature(new Size()).setSuffix("Area"));
            xp.addMeasurement(new GrowthRate().setFeature(new FeretMax()).setSuffix("Length"));
            xp.addMeasurement(new GrowthRate().setFeature(new SpineLength()).setSuffix("SpineLength"));
        }
    }
    
    public static Experiment generateXPPhase(String name, String outputDir, boolean setUpPreProcessing, int trimFramesStart, int trimFramesEnd, double scaleXY) {
        Experiment xp = new Experiment(name);
        xp.setImportImageMethod(importMethod==null ? Experiment.ImportImageMethod.SINGLE_FILE : importMethod);
        xp.getChannelImages().insert(new ChannelImage("PhaseContrast", ""));
        xp.setOutputDirectory(outputDir);
        Structure mc = new Structure("Microchannel", -1, 0);
        Structure bacteria = new Structure("Bacteria", 0, 0).setAllowSplit(true);
        xp.getStructures().insert(mc, bacteria);
        setParametersPhase(xp, true, true);
        if (setUpPreProcessing) setPreprocessingPhase(xp.getPreProcessingTemplate(), trimFramesStart, trimFramesEnd, scaleXY);
        return xp;
    }
    public static void deletePositions(Experiment xp, boolean[] deletePositions) {
        if (deletePositions!=null) {
            if (deletePositions.length!=xp.getPositionCount()) logger.error("Delete array has {} elements and xp has: {} imported positions", deletePositions.length, xp.getPositionCount());
            else {
                List<String> toDelete = new ArrayList<>();
                String[] names = xp.getPositionsAsString();
                for (int i = 0; i<deletePositions.length; ++i) if (deletePositions[i]) toDelete.add(names[i]);
                for (String p : toDelete) {
                    xp.getPosition(p).eraseData(false); // deletes images - 
                    xp.getPosition(p).removeFromParent(); // remove from parent
                }
            }
        }
    }
    public static void setFlip(Experiment xp, boolean[] flipArray) {
        if (flipArray!=null) {
            if (flipArray.length!=xp.getPositionCount()) logger.error("Flip array has {} elements and xp has: {} imported positions", flipArray.length, xp.getPositionCount());
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
                new SegmentationPostFilter().addPostFilters(new FitMicrochannelHeadToEdges().setResetBounds(true).setTrimUpperPixels(0))//,
                //new AverageMask()
            );
            
            mc.setProcessingScheme(mcpc);
            bacteria.setProcessingScheme(
                    new SegmentAndTrack(
                            new BacteriaClosedMicrochannelTrackerLocalCorrections()
                            .setSegmenter(new BacteriaIntensityPhase())
                            .setCostParameters(0.2, 2)
                            .setSizeFeature(0)
                    ).addTrackPreFilters(
                        //new PreFilter(new BandPass(0, 150, 2, 1)),
                        new SubtractBackgroundMicrochannels(),
                        new NormalizeTrack(1, true)
                    ).addPostFilters(
                            new FeatureFilter(new ThicknessAxis().setAxis(MainAxis.X), 6, true, true), 
                            new BinaryClose(5),
                            new FillHoles2D()
                    ).addTrackPostFilters(
                            new SegmentationPostFilter().setDeleteMethod(2).setMergePolicy(PostFilter.MERGE_POLICY.MERGE_TRACKS_SIZE_COND).addPostFilters(new RemoveEndofChannelBacteria().setContactSidesProportion(0)), 
                            new RemoveTrackByFeature().setMergePolicy(PostFilter.MERGE_POLICY.MERGE_TRACKS_SIZE_COND).setFeature(new Size(), 10, true).setStatistics(2)
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
            xp.addMeasurement(new BacteriaPhaseMeasurements(1));
            xp.addMeasurement(new TrackLength(0));
            xp.addMeasurement(new GrowthRate().setFeature(new Size()).setSuffix("Area"));
            xp.addMeasurement(new GrowthRate().setFeature(new FeretMax()).setSuffix("Length"));
            xp.addMeasurement(new GrowthRate().setFeature(new SpineLength()).setSuffix("SpineLength"));
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
