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
package boa.misc;

import boa.core.Processor;
import boa.core.Task;
import boa.configuration.experiment.ChannelImage;
import boa.configuration.experiment.Experiment;
import boa.configuration.experiment.Structure;
import boa.data_structure.dao.MasterDAO;
import boa.plugins.PluginFactory;
import boa.plugins.plugins.measurements.ChromaticShiftBeads;
import boa.plugins.plugins.measurements.objectFeatures.object_feature.Size;
import boa.plugins.plugins.post_filters.FeatureFilter;
import boa.plugins.plugins.pre_filters.Median;
import boa.plugins.plugins.processing_pipeline.SegmentOnly;
import boa.plugins.plugins.segmenters.SimpleThresholder;

/**
 *
 * @author jollion
 */
public class MeasureChromaticShift {
    public static void main(String[] args) {
        PluginFactory.findPlugins("boa.plugins.plugins");
        Experiment xp = generateXP(new double[]{125, 600}, "/data/Images/ChromaticShift/billes2", "/data/Images/ChromaticShift/billesOutput");
        
        MasterDAO db = new Task("chromaticShift").getDB();
        MasterDAO.deleteObjectsAndSelectionAndXP(db);
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
            struc.setProcessingPipeline(new SegmentOnly(new SimpleThresholder(thresholds[s])).addPreFilters(new Median(2, 1)).addPostFilters(new FeatureFilter(new Size().setScaled(false), 5, true, true)));
        }
        // sets measurements
        for (int s = 0; s<thresholds.length-1; ++s) { 
            for (int s2 = s+1; s2<thresholds.length; ++s2) { 
                xp.addMeasurement(new ChromaticShiftBeads(s, s2));
            }
        }
        xp.setImportImageMethod(Experiment.IMPORT_METHOD.SINGLE_FILE);
        xp.setOutputDirectory(outputDirectory);
        Processor.importFiles(xp, true, null, inputDirectory);
        
        return xp;
    }
}
