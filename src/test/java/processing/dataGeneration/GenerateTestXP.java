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
package processing.dataGeneration;

import core.Processor;
import dataStructure.configuration.ChannelImage;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.Structure;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.MorphiumMasterDAO;
import java.io.File;
import plugins.PluginFactory;
import plugins.plugins.measurements.BacteriaFluoMeasurements;
import plugins.plugins.measurements.BacteriaLineageIndex;
import plugins.plugins.measurements.BacteriaMeasurementsWoleMC;
import plugins.plugins.measurements.MeasurementObject;
import plugins.plugins.measurements.MutationMeasurements;
import plugins.plugins.measurements.objectFeatures.SNR;
import plugins.plugins.preFilter.IJSubtractBackground;
import plugins.plugins.preFilter.Median;
import plugins.plugins.processingScheme.SegmentAndTrack;
import plugins.plugins.processingScheme.SegmentOnly;
import plugins.plugins.processingScheme.SegmentThenTrack;
import plugins.plugins.segmenters.BacteriaFluo;
import plugins.plugins.segmenters.MicroChannelPhase2D;
import plugins.plugins.segmenters.MutationSegmenter;
import plugins.plugins.segmenters.MutationSegmenterScaleSpace;
import plugins.plugins.trackers.BacteriaClosedMicrochannelTrackerLocalCorrections;
import plugins.plugins.trackers.MicrochannelProcessor;
import plugins.plugins.trackers.ObjectIdxTracker;
import plugins.plugins.transformations.AutoRotationXY;
import plugins.plugins.transformations.CropMicroChannelBF2D;
import plugins.plugins.transformations.CropMicroChannelFluo2D;
import plugins.plugins.transformations.CropMicroChannels2D;
import plugins.plugins.transformations.Flip;
import plugins.plugins.transformations.ImageStabilizerXY;
import plugins.plugins.transformations.SaturateHistogram;
import plugins.plugins.transformations.ScaleHistogramSignalExclusion;
import plugins.plugins.transformations.ScaleHistogramSignalExclusionY;
import plugins.plugins.transformations.SelectBestFocusPlane;
import plugins.plugins.transformations.SuppressCentralHorizontalLine;
import processing.ImageTransformation;


/**
 *
 * @author jollion
 */
public class GenerateTestXP {
    public static void main(String[] args) {
        /*String dbName = "dummyXP";
        String inputDir = "!!not a directory";
        String outputDir = "!!not a directory";
        boolean flip = true;
        boolean fluo = true;
        */
        //////// FLUO
        // Ordi LJP
        /*String dbName = "fluo151130_OutputNewScaling";
        String outputDir = "/data/Images/Fluo/films1511/151130/OutputNewScaling";
        String inputDir = "/data/Images/Fluo/films1511/151130/ME120R63-30112015-lr62r1";
        boolean flip = true;
        boolean fluo = true;
        */
        
        /*String dbName = "fluo151127";
        String outputDir = "/data/Images/Fluo/films1511/151127/Output";
        String inputDir = "/data/Images/Fluo/films1511/151127/ME121R-27112015-laser";
        boolean flip = true; 
        boolean fluo = true;
        */
        
        /*String dbName = "fluo160218";
        String inputDir = "/data/Images/Fluo/film160218/ME120R63-18022016-LR62r";
        String outputDir = "/data/Images/Fluo/film160218/Output";
        boolean flip = false;
        boolean fluo = true;
        */
        
        /*
        String dbName = "fluo160217";
        String inputDir = "/data/Images/Fluo/film160217/ME120R63-17022016-LR62r";
        String outputDir = "/data/Images/Fluo/film160217/Output";
        boolean flip = false;
        boolean fluo = true;
        // ATTENTION 10 champs (12->21) pour lesquels 115TP pour le fichier mutation et 114TP pour le fichier REF dans un dossier séparé
        */
        
        
        /*String dbName = "fluo160212";
        String inputDir = "/data/Images/Fluo/film160212/ImagesSubset0-120/";
        String outputDir = "/data/Images/Fluo/film160212/Output";
        boolean flip = true;
        boolean fluo = true;
        */
        
        /*String dbName = "fluo160311";
        String inputDir = "/data/Images/Fluo/film160311/ME121R-11032016-lr62r2/";
        String outputDir = "/data/Images/Fluo/film160311/Output/";
        boolean flip = true;
        boolean fluo = true;*/
        
        /*String dbName = "fluo160407";
        String inputDir = "/data/Images/Fluo/film160407/me121r-lr62r2-07042016";
        String outputDir = "/data/Images/Fluo/film160407/Output/";
        boolean flip = false;
        boolean fluo = true;*/
        
        /*String dbName = "fluo160408";
        String inputDir = "/data/Images/Fluo/film160408/me121r-laser1-08042016/";
        String outputDir = "/data/Images/Fluo/film160408/Output/";
        boolean flip = true;
        boolean fluo = true;
        */
        
        /*String dbName = "fluo160428";
        String inputDir = "/data/Images/Fluo/film160428/63121r-laser1-28042016/";
        String outputDir = "/data/Images/Fluo/film160428/Output/";
        boolean flip = true;
        boolean fluo = true;
        */
        String dbName = "fluo160501";
        String inputDir = "/data/Images/Fluo/film160501/me121r-lr62rep2-01052016/";
        String outputDir = "/data/Images/Fluo/film160501/Output";
        boolean flip = true;
        boolean fluo = true;
        
        //////////// Trans
        /*String dbName = "testBF";
        String inputDir = "/data/Images/Lydia/testJeanFilm";
        String outputDir = "/data/Images/Lydia/Output";
        boolean flip = true;
        boolean fluo = false;
        */
        /*
        // Ordi Portable
        String dbName = "testSub";
        String outputDir = "/home/jollion/Documents/LJP/DataLJP/TestOutput";
        String inputDir = "/home/jollion/Documents/LJP/DataLJP/testsub"; 
        boolean flip = true;
        boolean fluo = true;
        */     
        
        /*String dbName = "fluo151127";
        String outputDir = "/home/jollion/Documents/LJP/DataLJP/Fluo151127/Output";
        String inputDir = "/media/jollion/4336E5641DA22135/LJP/films1511/151127/ME121R-27112015-laser";
        boolean flip = true;
        boolean fluo = true;
        */
        
        boolean performProcessing = false;
        
        MasterDAO mDAO = new MorphiumMasterDAO(dbName);
        mDAO.reset();
        Experiment xp = fluo ? generateXPFluo(outputDir, true, flip) : generateXPBF(outputDir, true, flip); 
        mDAO.setExperiment(xp);
        
        Processor.importFiles(xp, true, inputDir);
        
        if (performProcessing) {
            Processor.preProcessImages(mDAO, true);
            Processor.processAndTrackStructures(mDAO, true, 0);
        }
        mDAO.updateExperiment(); // save changes
    }
    
    
    public static Experiment generateXPFluo(String outputDir, boolean setUpPreProcessing, boolean flip) {
        
        Experiment xp = new Experiment("testXP");
        xp.setImportImageMethod(Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD);
        //xp.setImportImageMethod(Experiment.ImportImageMethod.SINGLE_FILE);
        xp.getChannelImages().insert(new ChannelImage("RFP", "_REF"), new ChannelImage("YFP", ""));
        xp.setOutputImageDirectory(outputDir);
        File f =  new File(outputDir); f.mkdirs(); //deleteDirectory(f);
        Structure mc = new Structure("MicroChannel", -1, 0);
        Structure bacteria = new Structure("Bacteria", 0, 0);
        Structure mutation = new Structure("Mutation", 0, 1); // parent structure 1 segParentStructure 0
        xp.getStructures().insert(mc, bacteria, mutation);
        
        mc.setProcessingScheme(new SegmentAndTrack(new MicrochannelProcessor()));
        bacteria.setProcessingScheme(new SegmentAndTrack(new BacteriaClosedMicrochannelTrackerLocalCorrections(new BacteriaFluo(), 0.9, 1.1, 1.7, 1, 5)));
        mutation.setProcessingScheme(new SegmentOnly(new MutationSegmenterScaleSpace().setThresholdSeeds(2)));
        //mutation.setManualSegmenter();
        xp.addMeasurement(new BacteriaLineageIndex(1, "BacteriaLineage"));
        xp.addMeasurement(new BacteriaFluoMeasurements(1, 2));
        xp.addMeasurement(new MutationMeasurements(1, 2));
        xp.addMeasurement(new MeasurementObject(2).addFeature(new SNR().setBackgroundObjectStructureIdx(1).setIntensityStructure(2), "MutationSNR"));
        xp.addMeasurement(new BacteriaMeasurementsWoleMC(1, 2));
        if (setUpPreProcessing) {// preProcessing 
            //xp.getPreProcessingTemplate().addTransformation(0, null, new SuppressCentralHorizontalLine(6)).setActivated(false);
            xp.getPreProcessingTemplate().addTransformation(0, null, new SaturateHistogram(350, 450));
            xp.getPreProcessingTemplate().addTransformation(1, null, new Median(1, 0)).setActivated(true); // to remove salt and pepper noise
            xp.getPreProcessingTemplate().addTransformation(0, null, new IJSubtractBackground(20, true, false, true, false));
            xp.getPreProcessingTemplate().addTransformation(0, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXVAR, 0));
            xp.getPreProcessingTemplate().addTransformation(0, null, new Flip(ImageTransformation.Axis.Y)).setActivated(flip);
            xp.getPreProcessingTemplate().addTransformation(0, null, new CropMicroChannelFluo2D(30, 45, 200, 0.6, 5));
            xp.getPreProcessingTemplate().addTransformation(1, null, new ScaleHistogramSignalExclusionY().setExclusionChannel(0)); // to remove blinking / homogenize on Y direction
            xp.getPreProcessingTemplate().addTransformation(0, null, new SelectBestFocusPlane(3)).setActivated(false); // faster after crop, but previous transformation might be aftected if the first plane is really out of focus
            xp.getPreProcessingTemplate().addTransformation(0, null, new ImageStabilizerXY(0, 1000, 5e-8, 20).setAdditionalTranslation(1, -0.477, -0.362)); // additional translation to correct chromatic shift
        }
        return xp;
    }
    
    public static Experiment generateXPBF(String outputDir, boolean setUpPreProcessing, boolean flip) {
        
        Experiment xp = new Experiment("testXP");
        xp.setImportImageMethod(Experiment.ImportImageMethod.SINGLE_FILE);
        xp.getChannelImages().insert(new ChannelImage("BF"));
        xp.setOutputImageDirectory(outputDir);
        File f =  new File(outputDir); f.mkdirs(); //deleteDirectory(f);
        Structure mc = new Structure("MicroChannel", -1, 0);
        Structure bacteria = new Structure("Bacteria", 0, 0);
        xp.getStructures().insert(mc, bacteria);
        
        mc.setProcessingScheme(new SegmentThenTrack(
                new MicroChannelPhase2D(), 
                new ObjectIdxTracker()
        ));
        bacteria.setProcessingScheme(new SegmentAndTrack(
                new BacteriaClosedMicrochannelTrackerLocalCorrections(
                        new BacteriaFluo().setOpenRadius(3), 0.9, 1.1, 1.7, 1, 5))
        );
        
        //xp.addMeasurement(new BacteriaLineageIndex(1));
        //xp.addMeasurement(new BacteriaMeasurements(1, 2));
        xp.addMeasurement(new BacteriaMeasurementsWoleMC(1, 2));
        if (setUpPreProcessing) {// preProcessing 
            xp.getPreProcessingTemplate().addTransformation(0, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXARTEFACT, 0));
            xp.getPreProcessingTemplate().addTransformation(0, null, new Flip(ImageTransformation.Axis.Y)).setActivated(flip);
            xp.getPreProcessingTemplate().addTransformation(0, null, new CropMicroChannelBF2D());
        }
        return xp;
    }

}
