/*
 * Copyright (C) 2017 jollion
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
import static core.generateXP.GenerateXP.fillRange;
import static core.generateXP.GenerateXP.generateXPFluo;
import static core.generateXP.GenerateXP.generateXPTrans;
import static core.generateXP.GenerateXP.getBooleanArray;
import static core.generateXP.GenerateXP.logger;
import dataStructure.configuration.ChannelImage;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.MicroscopyField;
import dataStructure.configuration.Structure;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.MorphiumMasterDAO;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import plugins.PluginFactory;
import plugins.Transformation;
import plugins.plugins.measurements.BacteriaLineageIndex;
import plugins.plugins.measurements.SimpleTrackMeasurements;
import plugins.plugins.measurements.InclusionObjectIdx;
import plugins.plugins.measurements.MutationTrackMeasurements;
import plugins.plugins.measurements.ObjectInclusionCount;
import plugins.plugins.measurements.RelativePosition;
import plugins.plugins.measurements.SimpleIntensityMeasurement;
import plugins.plugins.preFilter.BandPass;
import plugins.plugins.preFilter.IJSubtractBackground;
import plugins.plugins.preFilter.Median;
import plugins.plugins.processingScheme.SegmentAndTrack;
import plugins.plugins.processingScheme.SegmentThenTrack;
import plugins.plugins.segmenters.BacteriaFluo;
import plugins.plugins.segmenters.MicroChannelFluo2D;
import plugins.plugins.segmenters.MutationSegmenter;
import plugins.plugins.segmenters.MutationSegmenterScaleSpace;
import plugins.plugins.trackers.FixedObjectsTracker;
import plugins.plugins.trackers.LAPTracker;
import plugins.plugins.trackers.MicrochannelProcessor;
import plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections;
import plugins.plugins.transformations.AutoRotationXY;
import plugins.plugins.transformations.CropMicroChannelFluo2D;
import plugins.plugins.transformations.Flip;
import plugins.plugins.transformations.ImageStabilizerXY;
import plugins.plugins.transformations.SaturateHistogram;
import plugins.plugins.transformations.SaturateHistogramAuto;
import plugins.plugins.transformations.ScaleHistogramSignalExclusionY;
import plugins.plugins.transformations.SelectBestFocusPlane;
import plugins.plugins.transformations.SimpleCrop;
import plugins.plugins.transformations.SimpleTranslation;
import processing.ImageTransformation;

/**
 *
 * @author jollion
 */
public class GenerateMutationDynamicsXP {
    static double scaleXY = Double.NaN;
    static Experiment.ImportImageMethod importMethod = Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD;
    static boolean flip = false;
    static boolean singleChannel = false;
    static boolean invertChannels = false;
    static double mutThld = 115;
    static boolean stabilizer = false;
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");

        int trimStart=0, trimEnd=0;
        int[] cropXYdXdY=null;
        boolean[] flipArray;
        /*String dbName = "boa_170111MutationDynamics";
        String inputDir = "/data/Images/MutationDynamics/170111/input";
        String outputDir = "/data/Images/MutationDynamics/170111/output";
        */
        
        /*String dbName = "boa_fluo170117_GammeMutTrack";
        String inputDir = "/data/Images/MutationDynamics/170117GammeMutTrack/input";
        String outputDir = "/data/Images/MutationDynamics/170117GammeMutTrack/output";
        importMethod = Experiment.ImportImageMethod.SINGLE_FILE;
        invertChannels = true;
        mutThld = 90;*/
        
        /*String dbName = "boa_fluo170117_GammeMutTrackStab";
        String inputDir = "/data/Images/MutationDynamics/170117GammeMutTrack/input";
        String outputDir = "/data/Images/MutationDynamics/170117GammeMutTrack/outputStab";
        importMethod = Experiment.ImportImageMethod.SINGLE_FILE;
        invertChannels = true;
        mutThld = 90;
        stabilizer = true;
        */
        
        String dbName = "boa_fluo170207_150ms";
        String inputDir = "/data/Images/MutationDynamics/170207/150ms_2scan";
        String outputDir = "/data/Images/MutationDynamics/170207/150ms_2scan_output";
        importMethod = Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD;
        flipArray = fillRange(getBooleanArray(117, false), 0, 35, true);
        invertChannels = false;
        mutThld = 90;
        
        boolean performProcessing = false;
        
        MasterDAO mDAO = new MorphiumMasterDAO(dbName);
        mDAO.reset();
        Experiment xp = generateXPFluo(DBUtil.removePrefix(dbName, GUI.DBprefix), outputDir, true, trimStart, trimEnd, cropXYdXdY);
        GenerateXP.setFlip(xp, flipArray);
        mDAO.setExperiment(xp);
        Processor.importFiles(xp, true, inputDir);
        for (MicroscopyField f : xp.getPositions()) f.setDefaultFrame(0);
        if (performProcessing) {
            Processor.preProcessImages(mDAO, true);
            Processor.processAndTrackStructures(mDAO, true, 0);
        }
        mDAO.updateExperiment(); // save changes
    }
    public static Experiment generateXPFluo(String name, String outputDir, boolean setUpPreProcessing, int trimFramesStart, int trimFramesEnd, int[] crop) {
        
        Experiment xp = new Experiment(name);
        xp.setImportImageMethod(importMethod);
        int bactChan = 0;
        int mutChan = 1;
        if (singleChannel) {
            mutChan = 0;
            xp.getChannelImages().insert(new ChannelImage("YFP", ""));
        } else if (invertChannels) {
            bactChan = 1;
            mutChan = 0;
            xp.getChannelImages().insert(new ChannelImage("YFP", ""), new ChannelImage("RFP", "_REF"));
        } else  xp.getChannelImages().insert(new ChannelImage("RFP", "_REF"), new ChannelImage("YFP", ""));
        xp.setOutputImageDirectory(outputDir);
        File f =  new File(outputDir); f.mkdirs(); //deleteDirectory(f);
        Structure mc = new Structure("Microchannel", -1, bactChan);
        Structure bacteria = new Structure("Bacteria", 0, bactChan).setAllowSplit(true);
        Structure mutation = new Structure("Mutation", 0, mutChan); // parent structure 1 segParentStructure 0
        xp.getStructures().insert(mc, bacteria, mutation);
        
        mc.setProcessingScheme(new SegmentAndTrack(new MicrochannelProcessor()));
        //bacteria.setProcessingScheme(new SegmentAndTrack(new BacteriaClosedMicrochannelTrackerLocalCorrections(new BacteriaFluo()).setCostParameters(0.1, 0.5)));
        bacteria.setProcessingScheme(new SegmentThenTrack(new BacteriaFluo(), new BacteriaClosedMicrochannelTrackerLocalCorrections().setCostParameters(0.1, 0.5)));
        mutation.setProcessingScheme(new SegmentAndTrack(new LAPTracker().setCompartimentStructure(1).setSegmenter(new MutationSegmenter()).setSpotQualityThreshold(3.5).setLinkingMaxDistance(0.75, 3).setTrackLength(10, 0)).addPreFilters(new BandPass(0, 10)));
        
        xp.addMeasurement(new SimpleTrackMeasurements(1));
        xp.addMeasurement(new SimpleTrackMeasurements(2));
        xp.addMeasurement(new InclusionObjectIdx(2, 1).setMeasurementName("BacteriaIdx"));
        xp.addMeasurement(new ObjectInclusionCount(1, 2, 10).setMeasurementName("MutationCount"));
        xp.addMeasurement(new SimpleIntensityMeasurement(1, 2));
        xp.addMeasurement(new SimpleIntensityMeasurement(2, 2));
        xp.addMeasurement(new RelativePosition(2, 1, true, 0).setMeasurementName("CoordMassToBacteriaMass"));
        xp.addMeasurement(new RelativePosition(2, 1, true, 1).setMeasurementName("CoordMassToBacteriaGeom"));
        xp.addMeasurement(new RelativePosition(2, 0, true, 2).setMeasurementName("CoordMassToMC"));
        xp.addMeasurement(new RelativePosition(2, 0, false, 2).setMeasurementName("CoordGeomToMC"));
        xp.addMeasurement(new RelativePosition(2, -1, true, 2).setMeasurementName("CoordMass"));
        xp.addMeasurement(new RelativePosition(2, -1, false, 2).setMeasurementName("CoordGeom"));
        xp.addMeasurement(new RelativePosition(1, -1, false, 2).setMeasurementName("CoordGeom"));
        xp.addMeasurement(new RelativePosition(1, -1, true, 2).setMeasurementName("CoordMass"));
        xp.addMeasurement(new RelativePosition(1, 0, true, 2).setMeasurementName("CoordMassToMC"));
        
        if (setUpPreProcessing) {// preProcessing 
            //xp.getPreProcessingTemplate().addTransformation(0, null, new SuppressCentralHorizontalLine(6)).setActivated(false);
            if (!Double.isNaN(scaleXY)) xp.getPreProcessingTemplate().setCustomScale(scaleXY, 1);
            if (crop!=null) xp.getPreProcessingTemplate().addTransformation(0, null, new SimpleCrop(crop));
            xp.getPreProcessingTemplate().setTrimFrames(trimFramesStart, trimFramesEnd);
            xp.getPreProcessingTemplate().addTransformation(bactChan, null, new SaturateHistogramAuto().setSigmas(1, 2));
            xp.getPreProcessingTemplate().addTransformation(mutChan, null, new BandPass(0, 40, 1)); // remove horizontal lines
            xp.getPreProcessingTemplate().addTransformation(bactChan, null, new IJSubtractBackground(20, true, false, true, false));
            xp.getPreProcessingTemplate().addTransformation(bactChan, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXVAR));
            xp.getPreProcessingTemplate().addTransformation(bactChan, null, new Flip(ImageTransformation.Axis.Y)).setActivated(flip);
            xp.getPreProcessingTemplate().addTransformation(bactChan, null, new CropMicroChannelFluo2D(30, 45, 200, 0.6, 5));
            if (!stabilizer) xp.getPreProcessingTemplate().addTransformation(bactChan, new int[]{bactChan}, new SimpleTranslation(0.477, 0.362, 0)); // 0.19 microns en Z
            if (stabilizer) xp.getPreProcessingTemplate().addTransformation(bactChan, null, new ImageStabilizerXY(4, 2000, 1e-12, 5).setAdditionalTranslation(bactChan, 0.477, 0.362)); // additional translation to correct chromatic shift
        }
        return xp;
    }
    
    /*public static Experiment generateXPSingleChannel(String name, String outputDir, boolean setUpPreProcessing, boolean flip, int trimFramesStart, int trimFramesEnd, int[] crop) {
        
        Experiment xp = new Experiment(name);
        xp.setImportImageMethod(importMethod);
        //xp.setImportImageMethod(Experiment.ImportImageMethod.SINGLE_FILE);
        xp.getChannelImages().insert(new ChannelImage("YFP", ""));
        xp.setOutputImageDirectory(outputDir);
        File f =  new File(outputDir); f.mkdirs(); //deleteDirectory(f);
        Structure mc = new Structure("Microchannel", -1, 0);
        Structure bacteria = new Structure("Bacteria", 0, 0).setAllowSplit(true);
        Structure mutation = new Structure("Mutation", 0, 1); // parent structure 1 segParentStructure 0
        xp.getStructures().insert(mc, bacteria, mutation);
        
        mc.setProcessingScheme(new SegmentAndTrack(new FixedObjectsTracker(new MicroChannelFluo2D())));
        bacteria.setProcessingScheme(new SegmentAndTrack(new FixedObjectsTracker(new BacteriaFluo())));
        mutation.setProcessingScheme(new SegmentAndTrack(new LAPTracker().setCompartimentStructure(1)));
        xp.addMeasurement(new MutationTrackMeasurements(1, 2));
        xp.addMeasurement(new ObjectInclusionCount(1, 2, 10).setMeasurementName("MutationNumber"));
        if (setUpPreProcessing) {// preProcessing 
            if (!Double.isNaN(scaleXY)) xp.getPreProcessingTemplate().setCustomScale(scaleXY, 1);
            if (crop!=null) xp.getPreProcessingTemplate().addTransformation(0, null, new SimpleCrop(crop));
            xp.getPreProcessingTemplate().setTrimFrames(trimFramesStart, trimFramesEnd);
            xp.getPreProcessingTemplate().addTransformation(0, null, new Median(1, 0)).setActivated(true); // to remove salt and pepper noise
            xp.getPreProcessingTemplate().addTransformation(0, null, new IJSubtractBackground(20, true, false, true, false));
            xp.getPreProcessingTemplate().addTransformation(0, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXVAR).setAverageReference(10));
            xp.getPreProcessingTemplate().addTransformation(0, null, new Flip(ImageTransformation.Axis.Y)).setActivated(flip);
            xp.getPreProcessingTemplate().addTransformation(0, null, new CropMicroChannelFluo2D(30, 45, 200, 0.6, 1).setAvergeFrameNb(10));
            xp.getPreProcessingTemplate().addTransformation(0, null, new ScaleHistogramSignalExclusionY().setExclusionChannel(0)).setActivated(false); // to remove blinking / homogenize on Y direction
        }
        return xp;
    }*/
}
