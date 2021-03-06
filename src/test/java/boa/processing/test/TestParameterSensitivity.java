/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.processing.test;

import static boa.test_utils.TestUtils.logger;
import boa.core.Task;
import boa.configuration.experiment.Structure;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import boa.plugins.PluginFactory;
import boa.plugins.plugins.measurements.objectFeatures.object_feature.Quality;
import boa.plugins.plugins.post_filters.FeatureFilter;
import boa.plugins.plugins.pre_filters.BandPass;
import boa.plugins.plugins.processing_pipeline.SegmentAndTrack;
import boa.plugins.plugins.segmenters.SpotSegmenter;
import boa.plugins.plugins.trackers.NestedSpotTracker;
import boa.utils.Utils;
import boa.plugins.ProcessingPipeline;

/**
 *
 * @author Jean Ollion
 */
public class TestParameterSensitivity {
    public static void main(String[] args) {
        PluginFactory.findPlugins("boa.plugins.plugins");
        String dbName = "fluo160501_uncorr_TestParam";
        String dir  = "/data/Images/Fluo/fluo160501_uncorr_TestParam";
        
        List<double[]> allParams = new ArrayList<double[]>(){{
            //add(new double[]{0.8, 1.5}); 
            //add(new double[]{0.8, 2});
            //add(new double[]{0.8, 3});
            //add(new double[]{0.8, 5});
            //add(new double[]{0.8, 10});
            //add(new double[]{1, 1.5});
            //add(new double[]{1, 2});
            //add(new double[]{1, 3});
            //add(new double[]{1, 5});
            //add(new double[]{1, 10});
            //add(new double[]{1.5, 1.5});
            //add(new double[]{1.5, 2});
            //add(new double[]{1.5, 3});
            //add(new double[]{1.5, 5});
            //add(new double[]{1.5, 10});
            //add(new double[]{2, 2});
            //add(new double[]{2, 3});
            //add(new double[]{2, 5});
            //add(new double[]{2, 10});
            //add(new double[]{3, 3});
            //add(new double[]{3, 5});
            //add(new double[]{3, 10});
            //add(new double[]{5, 5});
            //add(new double[]{5, 10});
            //add(new double[]{1.1, 2.5});
            //add(new double[]{1.2, 2.5});
            //add(new double[]{1.3, 2.5});
            //add(new double[]{1.1, 3});
            add(new double[]{1.2, 3});
            //add(new double[]{1.3, 3});
        }};
        for (double[] p : allParams) runTest(p, dbName, dir);
        //runTest(allParams.get(0), dbName, dir);
    }
    public static void runTest(double[] params, String xp, String dir) {
        Task t = new Task(xp, dir);
        int[] pos = new int[]{0, 1, 3};
        //int[] pos = new int[]{1};
        t.setPositions(pos).setActions(false, true, true, true).setStructures(2);
        
        Structure mutation = t.getDB().getExperiment().getStructure(2);
        mutation.setProcessingPipeline(new SegmentAndTrack(
                    new NestedSpotTracker().setCompartimentStructure(1).setSegmenter(new SpotSegmenter(0.9, 0.75, 0.9).setScale(2)
                ).setSpotQualityThreshold(params[1])
                            .setLinkingMaxDistance(0.8, 0.82).setGapParameters(0.8, 0.15, 1)
            ).addPreFilters(new BandPass(0, 8, 0, 5) 
            ).addPostFilters(new FeatureFilter(new Quality(), params[0], true, true)));
        t.getDB().updateExperiment();
        logger.debug("Test: {}",t.toString());
        t.runTask();
        t = new Task(xp, dir).setPositions(pos).addExtractMeasurementDir(null, 2);
        t.runTask();
        // rename files
        logger.debug("rename from: {}, to : {}", dir+File.separator+xp+"_2.csv", dir+File.separator+xp+"_"+params[0]+"_"+params[1]+"_2.csv");
        new File(dir+File.separator+xp+"_2.csv").renameTo(new File(dir+File.separator+xp+"_"+params[0]+"_"+params[1]+"_2.csv"));
    }
}
