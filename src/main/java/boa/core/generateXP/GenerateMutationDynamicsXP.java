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

import boa.ui.DBUtil;
import boa.gui.GUI;
import boa.configuration.parameters.TransformationPluginParameter;
import boa.core.Processor;
import static boa.core.generateXP.GenerateXP.fillRange;
import static boa.core.generateXP.GenerateXP.generateXPFluo;
import static boa.core.generateXP.GenerateXP.generateXPTrans;
import static boa.core.generateXP.GenerateXP.getBooleanArray;
import static boa.core.generateXP.GenerateXP.logger;
import boa.configuration.experiment.ChannelImage;
import boa.configuration.experiment.Experiment;
import boa.configuration.experiment.MicroscopyField;
import boa.configuration.experiment.Structure;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.MasterDAOFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import boa.plugins.ObjectFeature;
import boa.plugins.PluginFactory;
import boa.plugins.Transformation;
import boa.plugins.plugins.measurements.BacteriaLineageMeasurements;
import boa.plugins.plugins.measurements.GetAttribute;
import boa.plugins.plugins.measurements.SimpleTrackMeasurements;
import boa.plugins.plugins.measurements.InclusionObjectIdx;
import boa.plugins.plugins.measurements.MutationTrackMeasurements;
import boa.plugins.plugins.measurements.ObjectFeatures;
import boa.plugins.plugins.measurements.ObjectInclusionCount;
import boa.plugins.plugins.measurements.RelativePosition;
import boa.plugins.plugins.measurements.SimpleIntensityMeasurement;
import boa.plugins.plugins.measurements.SimpleIntensityMeasurementStructureExclusion;
import boa.plugins.plugins.measurements.objectFeatures.LocalSNR;
import boa.plugins.plugins.measurements.objectFeatures.Quality;
import boa.plugins.plugins.measurements.objectFeatures.SNR;
import boa.plugins.plugins.post_filters.FeatureFilter;
import boa.plugins.plugins.pre_filters.BandPass;
import boa.plugins.plugins.pre_filters.IJSubtractBackground;
import boa.plugins.plugins.pre_filters.Median;
import boa.plugins.plugins.processing_scheme.SegmentAndTrack;
import boa.plugins.plugins.processing_scheme.SegmentThenTrack;
import boa.plugins.plugins.segmenters.BacteriaIntensity;
import boa.plugins.plugins.segmenters.MicrochannelFluo2D;
import boa.plugins.plugins.segmenters.MutationSegmenter;
import boa.plugins.plugins.trackers.MutationTracker;
import boa.plugins.plugins.trackers.MicrochannelTracker;
import boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker.BacteriaClosedMicrochannelTrackerLocalCorrections;
import boa.plugins.plugins.transformations.AutoRotationXY;
import boa.plugins.plugins.transformations.CropMicrochannelsFluo2D;
import boa.plugins.plugins.transformations.Flip;
import boa.plugins.plugins.transformations.ImageStabilizerXY;
import boa.plugins.plugins.transformations.SaturateHistogram;
import boa.plugins.plugins.transformations.SaturateHistogramHyperfluoBacteria;
import boa.plugins.plugins.transformations.SimpleCrop;
import boa.plugins.plugins.transformations.SimpleTranslation;
import boa.image.processing.ImageTransformation;

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
        PluginFactory.findPlugins("boa.plugins.plugins");
        MasterDAOFactory.setCurrentType(MasterDAOFactory.DAOType.DBMap);
        
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
        flipArray = fillRange(getBooleanArray(117, true), 0, 35, false);
        invertChannels = false;
        mutThld = 90;
        
        boolean onlyUpdateMeasurements = true;
        boolean performProcessing = false;
        
        String configDir = MasterDAOFactory.getCurrentType().equals(MasterDAOFactory.DAOType.DBMap) ? new File(outputDir).getParent() : "localhost";
        if (MasterDAOFactory.getCurrentType().equals(MasterDAOFactory.DAOType.DBMap)) DBUtil.removePrefix(dbName, GUI.DBprefix);
        MasterDAO mDAO = MasterDAOFactory.createDAO(dbName, configDir);
        
        if (onlyUpdateMeasurements) {
            setMeasurements(mDAO.getExperiment());
        } else {
            MasterDAO.deleteObjectsAndSelectionAndXP(mDAO);
            Experiment xp = generateXPFluo(DBUtil.removePrefix(dbName, GUI.DBprefix), outputDir, true, trimStart, trimEnd, cropXYdXdY);
            mDAO.setExperiment(xp);
            Processor.importFiles(xp, true, null, inputDir);
            for (MicroscopyField f : xp.getPositions()) f.setDefaultFrame(0);
            GenerateXP.setFlip(xp, flipArray);
            if (performProcessing) {
                try {
                    Processor.preProcessImages(mDAO);
                } catch (Exception ex) {
                    logger.debug("Error while preprocessing", ex);
                }
                Processor.processAndTrackStructures(mDAO, true, 0);
            }
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
        xp.setOutputDirectory(outputDir);
        File f =  new File(outputDir); f.mkdirs(); //deleteDirectory(f);
        Structure mc = new Structure("Microchannel", -1, bactChan);
        Structure bacteria = new Structure("Bacteria", 0, bactChan).setAllowSplit(true);
        Structure mutation = new Structure("Mutation", 0, mutChan); // parent structure 1 segParentStructure 0
        xp.getStructures().insert(mc, bacteria, mutation);
        
        mc.setProcessingScheme(new SegmentAndTrack(new MicrochannelTracker().setSegmenter(new MicrochannelFluo2D())));
        //bacteria.setProcessingScheme(new SegmentAndTrack(new BacteriaClosedMicrochannelTrackerLocalCorrections(new BacteriaFluo()).setCostParameters(0.1, 0.5)));
        bacteria.setProcessingScheme(new SegmentThenTrack(new BacteriaIntensity(), new BacteriaClosedMicrochannelTrackerLocalCorrections().setCostParameters(0.1, 0.5)));
        mutation.setProcessingScheme(new SegmentAndTrack(
                new MutationTracker().setCompartimentStructure(1).setSegmenter(
                        new MutationSegmenter(0.65, 0.5, 0.55).setScale(2.5) 
                ).setSpotQualityThreshold(1).setLinkingMaxDistance(0.4, 0.41).setGapParameters(0.4, 0.1, 3)
        ).addPreFilters(new BandPass(0, 8, 0, 5) // was 10
        ).addPostFilters(new FeatureFilter(new Quality(), 0.6, true, true))); 
        
        setMeasurements(xp);
        
        if (setUpPreProcessing) {// preProcessing 
            //xp.getPreProcessingTemplate().addTransformation(0, null, new SuppressCentralHorizontalLine(6)).setActivated(false);
            if (!Double.isNaN(scaleXY)) xp.getPreProcessingTemplate().setCustomScale(scaleXY, 1);
            if (crop!=null) xp.getPreProcessingTemplate().addTransformation(0, null, new SimpleCrop(crop));
            xp.getPreProcessingTemplate().setTrimFrames(trimFramesStart, trimFramesEnd);
            xp.getPreProcessingTemplate().addTransformation(bactChan, null, new SaturateHistogramHyperfluoBacteria());
            xp.getPreProcessingTemplate().addTransformation(mutChan, null, new BandPass(0, 40, 1, 0)); // remove horizontal lines
            xp.getPreProcessingTemplate().addTransformation(bactChan, null, new IJSubtractBackground(20, true, false, true, false));
            xp.getPreProcessingTemplate().addTransformation(bactChan, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXVAR));
            xp.getPreProcessingTemplate().addTransformation(bactChan, null, new Flip(ImageTransformation.Axis.Y)).setActivated(flip);
            xp.getPreProcessingTemplate().addTransformation(bactChan, null, new CropMicrochannelsFluo2D(410, 45, 200, 0.6, 5));
            if (!stabilizer) xp.getPreProcessingTemplate().addTransformation(bactChan, new int[]{bactChan}, new SimpleTranslation(-1, -1, 0)); // 0.19 microns en Z
            if (stabilizer) xp.getPreProcessingTemplate().addTransformation(bactChan, null, new ImageStabilizerXY(4, 2000, 1e-12, 5).setAdditionalTranslation(bactChan, -1, -1)); // additional translation to correct chromatic shift
        }
        return xp;
    }
    
    protected static void setMeasurements(Experiment xp) {
        xp.clearMeasurements();
        xp.addMeasurement(new SimpleTrackMeasurements(1));
        xp.addMeasurement(new SimpleTrackMeasurements(2));
        xp.addMeasurement(new InclusionObjectIdx(2, 1).setMeasurementName("BacteriaIdx"));
        xp.addMeasurement(new ObjectInclusionCount(1, 2, 10).setMeasurementName("MutationCount"));
        xp.addMeasurement(new GetAttribute(2).addAttributes("Quality"));
        xp.addMeasurement(new SimpleIntensityMeasurementStructureExclusion(0, 2, 1).setPrefix("YfpBactExcl").setRadii(2, 0));
        xp.addMeasurement(new SimpleIntensityMeasurement(1, 2).setPrefix("Yfp"));
        xp.addMeasurement(new SimpleIntensityMeasurement(1, 1).setPrefix("Rfp"));
        xp.addMeasurement(new SimpleIntensityMeasurementStructureExclusion(1, 2, 2).setPrefix("YfpMutExcl").setRadii(2, 2));
        xp.addMeasurement(new SimpleIntensityMeasurement(2, 2).setPrefix("Yfp"));
        xp.addMeasurement(new ObjectFeatures(2).addFeature(new SNR(1), "Snr").addFeature(new LocalSNR(1), "LocalSnr"));
        xp.addMeasurement(new RelativePosition(2, 1, 0, 0).setMeasurementName("CoordMassToBacteriaMass"));
        xp.addMeasurement(new RelativePosition(2, 1, 0, 1).setMeasurementName("CoordMassToBacteriaGeom"));
        xp.addMeasurement(new RelativePosition(2, 1, 2, 1).setMeasurementName("CoordToBacteriaGeom"));
        xp.addMeasurement(new RelativePosition(2, 0, 0, 2).setMeasurementName("CoordMassToMC"));
        xp.addMeasurement(new RelativePosition(2, 0, 1, 2).setMeasurementName("CoordGeomToMC"));
        xp.addMeasurement(new RelativePosition(2, 0, 2, 2).setMeasurementName("CoordToMC"));
        xp.addMeasurement(new RelativePosition(2, -1, 0, 2).setMeasurementName("CoordMass"));
        xp.addMeasurement(new RelativePosition(2, -1, 1, 2).setMeasurementName("CoordGeom"));
        xp.addMeasurement(new RelativePosition(1, -1, 1, 2).setMeasurementName("CoordGeom"));
        xp.addMeasurement(new RelativePosition(1, -1, 0, 2).setMeasurementName("CoordMass"));
        xp.addMeasurement(new RelativePosition(1, 0, 0, 2).setMeasurementName("CoordMassToMC"));
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
