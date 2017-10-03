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
package core.generateXP;

import boa.gui.DBUtil;
import boa.gui.GUI;
import configuration.parameters.TransformationPluginParameter;
import core.Processor;
import core.Processor;
import dataStructure.configuration.ChannelImage;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.Experiment.ImportImageMethod;
import dataStructure.configuration.PreProcessingChain;
import dataStructure.configuration.Structure;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.MasterDAOFactory;
import ij.process.AutoThresholder;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import plugins.ObjectFeature;
import plugins.PluginFactory;
import plugins.Transformation;
import plugins.plugins.measurements.BacteriaFluoMeasurements;
import plugins.plugins.measurements.BacteriaLineageMeasurements;
import plugins.plugins.measurements.BacteriaMeasurementsWoleMC;
import plugins.plugins.measurements.BacteriaTransMeasurements;
import plugins.plugins.measurements.GrowthRate;
import plugins.plugins.measurements.ObjectFeatures;
import plugins.plugins.measurements.MutationMeasurements;
import plugins.plugins.measurements.MutationTrackMeasurements;
import plugins.plugins.measurements.ObjectInclusionCount;
import plugins.plugins.measurements.RelativePosition;
import plugins.plugins.measurements.TrackLength;
import plugins.plugins.measurements.objectFeatures.FeretMax;
import plugins.plugins.measurements.objectFeatures.Mean;
import plugins.plugins.measurements.objectFeatures.Quality;
import plugins.plugins.measurements.objectFeatures.SNR;
import plugins.plugins.measurements.objectFeatures.Size;
import plugins.plugins.postFilters.FeatureFilter;
import plugins.plugins.preFilter.BandPass;
import plugins.plugins.preFilter.IJSubtractBackground;
import plugins.plugins.preFilter.Median;
import plugins.plugins.processingScheme.SegmentAndTrack;
import plugins.plugins.processingScheme.SegmentOnly;
import plugins.plugins.processingScheme.SegmentThenTrack;
import plugins.plugins.segmenters.BacteriaFluo;
import plugins.plugins.segmenters.BacteriaTrans;
import plugins.plugins.segmenters.MicroChannelFluo2D;
import plugins.plugins.segmenters.MicrochannelPhase2D;
import plugins.plugins.segmenters.MutationSegmenter;
import plugins.plugins.segmenters.MutationSegmenterScaleSpace;
import plugins.plugins.thresholders.IJAutoThresholder;
import plugins.plugins.trackPostFilter.RemoveTracksStartingAfterFrame;
import plugins.plugins.trackPostFilter.TrackLengthFilter;
import plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections;
import plugins.plugins.trackers.LAPTracker;
import plugins.plugins.trackers.MicrochannelTracker;
import plugins.plugins.trackers.ObjectIdxTracker;
import plugins.plugins.transformations.AutoRotationXY;
import plugins.plugins.transformations.CropMicroChannelBF2D;
import plugins.plugins.transformations.CropMicroChannelFluo2D;
import plugins.plugins.transformations.CropMicroChannels;
import plugins.plugins.transformations.CropMicroChannels2D;
import plugins.plugins.transformations.Flip;
import plugins.plugins.transformations.ImageStabilizerXY;
import plugins.plugins.transformations.RemoveStripesSignalExclusion;
import plugins.plugins.transformations.SaturateHistogram;
import plugins.plugins.transformations.SaturateHistogramHyperfluoBacteria;
import plugins.plugins.transformations.ScaleHistogramSignalExclusion;
import plugins.plugins.transformations.ScaleHistogramSignalExclusionY;
import plugins.plugins.transformations.SelectBestFocusPlane;
import plugins.plugins.transformations.SimpleCrop;
import plugins.plugins.transformations.SimpleTranslation;
import plugins.plugins.transformations.SuppressCentralHorizontalLine;
import processing.ImageTransformation;


/**
 *
 * @author jollion
 */
public class GenerateXP {
    public static final Logger logger = LoggerFactory.getLogger(GenerateXP.class);
    static boolean subTransPre = true;
    static Experiment.ImportImageMethod importMethod;
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
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
        if (MasterDAOFactory.getCurrentType().equals(MasterDAOFactory.DAOType.DBMap)) DBUtil.removePrefix(dbName, GUI.DBprefix);
        MasterDAO mDAO = MasterDAOFactory.createDAO(dbName, configDir);
        MasterDAO.deleteObjectsAndSelectionAndXP(mDAO);
        Experiment xp = fluo ? generateXPFluo(DBUtil.removePrefix(dbName, GUI.DBprefix), outputDir, true, flip, trimStart, trimEnd, scaleXY, cropXYdXdY) : generateXPTrans(DBUtil.removePrefix(dbName, GUI.DBprefix), outputDir, true, flip, trimStart, trimEnd, scaleXY); 
        mDAO.setExperiment(xp);
        
        Processor.importFiles(xp, true, null, inputDir);
        setFlip(xp, flipArray);
        deletePositions(xp, deletePositions);
        if (performProcessing) {
            try {
                Processor.preProcessImages(mDAO, true);
            } catch (Exception ex) {
                logger.error("Error while preprocessing", ex);
            }
            Processor.processAndTrackStructures(mDAO, true, 0);
        }
        mDAO.updateExperiment(); // save changes
    }
    
    
    public static Experiment generateXPFluo(String name, String outputDir, boolean setUpPreProcessing, boolean flip, int trimFramesStart, int trimFramesEnd, double scaleXY, int[] crop) {
        
        Experiment xp = new Experiment(name);
        if (importMethod==null) xp.setImportImageMethod(ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD);
        else xp.setImportImageMethod(importMethod);
        xp.getChannelImages().insert(new ChannelImage("RFP", "_REF"), new ChannelImage("YFP", ""));
        xp.setOutputDirectory(outputDir);
        Structure mc = new Structure("Microchannel", -1, 0);
        Structure bacteria = new Structure("Bacteria", 0, 0).setAllowSplit(true);
        Structure mutation = new Structure("Mutation", 0, 1, 1);
        xp.getStructures().insert(mc, bacteria, mutation);
        setParametersFluo(xp, true, true);
        
        if (setUpPreProcessing) setPreprocessingFluo(xp.getPreProcessingTemplate(), flip, trimFramesStart, trimFramesEnd, scaleXY, crop);
        return xp;
    }
    public static void setPreprocessingFluo(PreProcessingChain ps, boolean flip, int trimFramesStart, int trimFramesEnd, double scaleXY, int[] crop) {
        ps.setFrameDuration(120);
        ps.removeAllTransformations();
            ps.setFrameDuration(120);
            if (!Double.isNaN(scaleXY)) ps.setCustomScale(scaleXY, 1);
            if (crop!=null) ps.addTransformation(0, null, new SimpleCrop(crop));
            ps.setTrimFrames(trimFramesStart, trimFramesEnd);
            ps.addTransformation(0, null, new RemoveStripesSignalExclusion(0).setAddGlobalMean(false));
            ps.addTransformation(1, null, new RemoveStripesSignalExclusion(0));
            ps.addTransformation(0, null, new SaturateHistogramHyperfluoBacteria());
            ps.addTransformation(0, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXVAR));
            ps.addTransformation(1, new int[]{1}, new SimpleTranslation(1, 1, 0).setInterpolationScheme(ImageTransformation.InterpolationScheme.NEAREST)).setActivated(true); // nearest -> translation entiers
            ps.addTransformation(0, null, new Flip(ImageTransformation.Axis.Y)).setActivated(flip);
            CropMicroChannels cropper = new CropMicroChannelFluo2D(0, 45, 200, 0.5, 10);
            ps.addTransformation(0, null, cropper).setActivated(true);
            ps.addTransformation(0, null, new ImageStabilizerXY(1, 1000, 1e-8, 20).setAdditionalTranslation(1, 1, flip?-1:1).setCropper(cropper)).setActivated(false); // additional translation to correct chromatic shift
    }
    public static void setParametersFluo(Experiment xp, boolean processing, boolean measurements) {
        Structure mc = xp.getStructure(0).setBrightObject(true);
        Structure bacteria = xp.getStructure(1).setBrightObject(true).setAllowSplit(true);
        Structure mutation = xp.getStructure(2).setBrightObject(true);
        mutation.setSegmentationParentStructure(1);
        if (processing) {
            mc.setProcessingScheme(new SegmentAndTrack(
                    new MicrochannelTracker().setSegmenter(
                            new MicroChannelFluo2D()
                    ).setTrackingParameters(40, 0.5).setYShiftQuantile(0.05)
                    ).addTrackPostFilters(new TrackLengthFilter().setMinSize(100), 
                            new RemoveTracksStartingAfterFrame())
            );
            bacteria.setProcessingScheme(new SegmentAndTrack(new BacteriaClosedMicrochannelTrackerLocalCorrections().setSegmenter(new BacteriaFluo()).setCostParameters(0.1, 0.5)));
            //mutation.setProcessingScheme(new SegmentAndTrack(new LAPTracker().setCompartimentStructure(1)));
            mutation.setProcessingScheme(new SegmentAndTrack(
                    new LAPTracker().setCompartimentStructure(1).setSegmenter(
                        new MutationSegmenter(0.9, 0.75, 0.9).setScale(2)  // was 1.5, 1, 1.25
                ).setSpotQualityThreshold(2) // was 2
                            .setLinkingMaxDistance(0.8, 0.82).setGapParameters(0.8, 0.15, 3).setTrackLength(8, 14)
            ).addPreFilters(new BandPass(0, 8, 0, 5) 
            ).addPostFilters(new FeatureFilter(new Quality(), 0.8, true, true))); // was 1.5
        }
        if (measurements) {
            xp.clearMeasurements();
            xp.addMeasurement(new BacteriaLineageMeasurements(1, "BacteriaLineage"));
            xp.addMeasurement(new ObjectFeatures(1).addFeatures(new Size().setScale(true), new FeretMax().setScale(true), new Mean().setIntensityStructure(1)));
            xp.addMeasurement(new MutationTrackMeasurements(1, 2));
            xp.addMeasurement(new ObjectInclusionCount(1, 2, 10).setMeasurementName("MutationNumber"));
            xp.addMeasurement(new ObjectFeatures(2).addFeatures(new Quality()));
            xp.addMeasurement(new RelativePosition(1, 0, 1, 2));
        }
    }
    
    public static Experiment generateXPTrans(String name, String outputDir, boolean setUpPreProcessing, boolean flip, int trimFramesStart, int trimFramesEnd, double scaleXY) {
        
        Experiment xp = new Experiment(name);
        xp.setImportImageMethod(importMethod==null ? Experiment.ImportImageMethod.SINGLE_FILE : importMethod);
        xp.getChannelImages().insert(new ChannelImage("BF", ""));
        xp.setOutputDirectory(outputDir);
        Structure mc = new Structure("Microchannel", -1, 0);
        Structure bacteria = new Structure("Bacteria", 0, 0).setAllowSplit(true);
        xp.getStructures().insert(mc, bacteria);
        setParametersTrans(xp, true, true);
        if (setUpPreProcessing) { // preProcessing 
            xp.getPreProcessingTemplate().setFrameDuration(4);
            if (!Double.isNaN(scaleXY)) xp.getPreProcessingTemplate().setCustomScale(scaleXY, 1);
            xp.getPreProcessingTemplate().addTransformation(0, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXARTEFACT).setPrefilters(new IJSubtractBackground(0.3, true, false, true, true)));
            xp.getPreProcessingTemplate().addTransformation(0, null, new Flip(ImageTransformation.Axis.Y)).setActivated(flip);
            xp.getPreProcessingTemplate().addTransformation(0, null, new CropMicroChannelBF2D());
            if (subTransPre) xp.getPreProcessingTemplate().addTransformation(0, null, new IJSubtractBackground(0.3, true, false, true, false)); // subtract after crop because subtract alter optical aberation detection. Optimization: paraboloid = true / range=03-05 best = 0.3 
            xp.getPreProcessingTemplate().addTransformation(0, null, new BandPass(0, 20, 0, 0)).setActivated(false); // TODO optimize low bound [10-50]
            xp.getPreProcessingTemplate().setTrimFrames(trimFramesStart, trimFramesEnd);
        }
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
    public static void setParametersTrans(Experiment xp, boolean processing, boolean measurements) {
        Structure mc = xp.getStructure(0);
        Structure bacteria = xp.getStructure(1);
        if (processing) {
            if (!subTransPre) mc.setProcessingScheme(new SegmentAndTrack(new MicrochannelTracker().setSegmenter(new MicrochannelPhase2D())).addPreFilters(new IJSubtractBackground(0.3, true, false, true, false)));
            else mc.setProcessingScheme(new SegmentAndTrack(new MicrochannelTracker().setSegmenter(new MicrochannelPhase2D()))
                    .addTrackPostFilters(new TrackLengthFilter().setMinSize(100),  new RemoveTracksStartingAfterFrame()));
            bacteria.setProcessingScheme(
                    new SegmentAndTrack(
                            new BacteriaClosedMicrochannelTrackerLocalCorrections()
                            .setSegmenter(new BacteriaTrans())
                            .setCostParameters(1.5, 3).setThresholdParameters(2, 1, 25, 15)
                    )
            );
            bacteria.setManualSegmenter(new BacteriaTrans()
                    .setThreshold(new IJAutoThresholder().setMethod(AutoThresholder.Method.MaxEntropy))
                    .setObjectParameters(50, 1)
            );
        }
        if (measurements) {
            xp.clearMeasurements();
            xp.addMeasurement(new BacteriaLineageMeasurements(1));
            xp.addMeasurement(new BacteriaTransMeasurements(1));
            xp.addMeasurement(new TrackLength(0));
            xp.addMeasurement(new GrowthRate().setFeature(new Size()).setSuffix("Area"));
            xp.addMeasurement(new GrowthRate().setFeature(new FeretMax()).setSuffix("Length"));
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
