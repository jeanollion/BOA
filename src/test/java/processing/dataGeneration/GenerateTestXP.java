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
import plugins.plugins.measurements.BacteriaMeasurementsWoleMC;
import plugins.plugins.preFilter.IJSubtractBackground;
import plugins.plugins.preFilter.Median;
import plugins.plugins.processingScheme.SegmentAndTrack;
import plugins.plugins.processingScheme.SegmentOnly;
import plugins.plugins.segmenters.BacteriaFluo;
import plugins.plugins.segmenters.MutationSegmenter;
import plugins.plugins.trackers.BacteriaClosedMicrochannelTrackerLocalCorrections;
import plugins.plugins.trackers.MicrochannelProcessor;
import plugins.plugins.transformations.AutoRotationXY;
import plugins.plugins.transformations.CropMicroChannels2D;
import plugins.plugins.transformations.Flip;
import plugins.plugins.transformations.ImageStabilizerXY;
import plugins.plugins.transformations.ScaleHistogramSignalExclusion;
import plugins.plugins.transformations.SelectBestFocusPlane;
import plugins.plugins.transformations.SuppressCentralHorizontalLine;
import processing.ImageTransformation;


/**
 *
 * @author jollion
 */
public class GenerateTestXP {
    public static void main(String[] args) {
        //String dbName = "fluo151130_OutputNewScaling";
        //String outputDir = "/home/jollion/Documents/LJP/DataLJP/Fluo151130/OutputNewScaling/";
        String dbName = "testSub";
        String outputDir = "/home/jollion/Documents/LJP/DataLJP/TestOutput";
        String inputDir = "/home/jollion/Documents/LJP/DataLJP/testsub";        
        boolean performProcessing = false;
        
        MasterDAO mDAO = new MorphiumMasterDAO(dbName);
        mDAO.reset();
        Experiment xp = generateXP(outputDir, true); 
        mDAO.setExperiment(xp);
        
        Processor.importFiles(xp, inputDir);
        if (performProcessing) {
            Processor.preProcessImages(mDAO, true);
            Processor.processAndTrackStructures(mDAO, true, 0);
        }
        mDAO.updateExperiment(); // save changes
    }
    
    
    public static Experiment generateXP(String outputDir, boolean setUpPreProcessing) {
        
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
        mutation.setProcessingScheme(new SegmentOnly(new MutationSegmenter()));
        
        //xp.addMeasurement(new BacteriaLineageIndex(1));
        //xp.addMeasurement(new BacteriaMeasurements(1, 2));
        xp.addMeasurement(new BacteriaMeasurementsWoleMC(1, 2));
        if (setUpPreProcessing) {// preProcessing 
            xp.getPreProcessingTemplate().addTransformation(0, null, new SuppressCentralHorizontalLine(6)).setActivated(false);
            xp.getPreProcessingTemplate().addTransformation(1, null, new Median(1, 0)).setActivated(true); // to remove salt and pepper noise
            xp.getPreProcessingTemplate().addTransformation(0, null, new IJSubtractBackground(20, true, false, true, false));
            xp.getPreProcessingTemplate().addTransformation(0, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXVAR, 0));
            xp.getPreProcessingTemplate().addTransformation(0, null, new Flip(ImageTransformation.Axis.Y)).setActivated(true);
            xp.getPreProcessingTemplate().addTransformation(0, null, new CropMicroChannels2D());
            xp.getPreProcessingTemplate().addTransformation(1, null, new ScaleHistogramSignalExclusion(100, 5, 0, 50, true)); // to remove blinking
            xp.getPreProcessingTemplate().addTransformation(0, null, new SelectBestFocusPlane(3)).setActivated(false); // faster after crop, but previous transformation might be aftected if the first plane is really out of focus
            xp.getPreProcessingTemplate().addTransformation(0, null, new ImageStabilizerXY());
        }
        return xp;
    }

}
