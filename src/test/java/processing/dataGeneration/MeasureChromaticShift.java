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
import core.Task;
import dataStructure.configuration.ChannelImage;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.Structure;
import dataStructure.objects.MasterDAO;
import plugins.PluginFactory;
import plugins.plugins.measurements.ChromaticShiftBeads;
import plugins.plugins.postFilters.SizeFilter;
import plugins.plugins.preFilter.Median;
import plugins.plugins.processingScheme.SegmentOnly;
import plugins.plugins.segmenters.ProcessingChain;
import plugins.plugins.segmenters.SimpleThresholder;

/**
 *
 * @author jollion
 */
public class MeasureChromaticShift {
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        Experiment xp = generateXP(new double[]{125, 600}, "/data/Images/ChromaticShift/billes2", "/data/Images/ChromaticShift/billesOutput");
        
        MasterDAO db = new Task("chromaticShift").getDB();
        db.reset();
        db.setExperiment(xp);
        Task t = new Task("chromaticShift").setAllActions().addExtractMeasurementDir("/home/jollion/Documents/LJP/Analyse/ChromaticShift", 0);
        t.runTask();
    }
    private static Experiment generateXP(final double[] thresholds, String inputDirectory, String outputDirectory) {
        Experiment xp = new Experiment("Chromatic Shift");
        xp.getPreProcessingTemplate().setCustomScale(0.06474, 0.1);
        for (int s = 0; s<thresholds.length; ++s) {
            xp.getChannelImages().insert(new ChannelImage("channel:"+s));
            Structure struc = new Structure("structure:"+s, -1, s);
            xp.getStructures().insert(struc);
            struc.setProcessingScheme(new SegmentOnly(new ProcessingChain(new SimpleThresholder(thresholds[s])).addPrefilters(new Median(2, 1)).addPostfilters(new SizeFilter(5, 0))));
        }
        // sets measurements
        for (int s = 0; s<thresholds.length-1; ++s) { 
            for (int s2 = s+1; s2<thresholds.length; ++s2) { 
                xp.addMeasurement(new ChromaticShiftBeads(s, s2));
            }
        }
        xp.setImportImageMethod(Experiment.ImportImageMethod.SINGLE_FILE);
        xp.setOutputDirectory(outputDirectory);
        Processor.importFiles(xp, true, inputDirectory);
        
        return xp;
    }
}
