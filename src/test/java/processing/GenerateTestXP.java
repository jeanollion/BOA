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
package processing;

import dataStructure.configuration.ChannelImage;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.Structure;
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


/**
 *
 * @author jollion
 */
public class GenerateTestXP {
    public static void main(String[] args) {
        String dbName = "fluo151130_OutputNewScaling";
        TestProcessBacteria t = new TestProcessBacteria();
        t.setUpXp(true, "/home/jollion/Documents/LJP/DataLJP/Fluo151130/OutputNewScaling/");
        //t.setUpXp(true, "/data/Images/Fluo/films1511/151130/Output");
        //t.setUpXp(true, "/data/Images/Fluo/films1511/151130/Output_champ01_sub88-118");
        //t.setUpXp(true, "/data/Images/Fluo/films1510/Output");
        //t.setUpXp(true, "/home/jollion/Documents/LJP/DataLJP/TestOutput60");
        //t.testImport("/data/Images/Fluo/testsub595-630");
        //t.testImport("/data/Images/Fluo/films1511/151130/champ01_sub88-118");
        //t.testImport("/data/Images/Fluo/films1510/63me120r-14102015-LR62R1-lbiptg100x_1");
        //t.testImport("/home/jollion/Documents/LJP/DataLJP/test");
        t.saveXP(dbName);
        //t.process(dbName, true);
    }
    
    public static Experiment generateXP(String outputDir, boolean setUpPreProcessing) {
        PluginFactory.findPlugins("plugins.plugins");
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
            xp.getPreProcessingTemplate().addTransformation(1, null, new ScaleHistogramSignalExclusion(106, 7.8, 0, 50)); // to remove blinking
            xp.getPreProcessingTemplate().addTransformation(1, null, new Median(1, 0)).setActivated(true); // to remove salt and pepper noise
            xp.getPreProcessingTemplate().addTransformation(0, null, new IJSubtractBackground(20, true, false, true, false));
            xp.getPreProcessingTemplate().addTransformation(0, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXVAR, 0));
            xp.getPreProcessingTemplate().addTransformation(0, null, new Flip(ImageTransformation.Axis.Y)).setActivated(true);
            xp.getPreProcessingTemplate().addTransformation(0, null, new CropMicroChannels2D());
            xp.getPreProcessingTemplate().addTransformation(0, null, new SelectBestFocusPlane(3)).setActivated(false); // faster after crop, but previous transformation might be aftected if the first plane is really out of focus
            xp.getPreProcessingTemplate().addTransformation(0, null, new ImageStabilizerXY());
        }
        return xp;
    }
}
