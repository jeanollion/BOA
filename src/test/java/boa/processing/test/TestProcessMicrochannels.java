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
import boa.gui.image_interaction.IJImageDisplayer;
import boa.gui.image_interaction.IJImageWindowManager;
import boa.gui.image_interaction.ImageDisplayer;
import boa.gui.image_interaction.InteractiveImage;
import boa.configuration.parameters.PostFilterSequence;
import boa.configuration.parameters.PreFilterSequence;
import boa.core.Task;
import boa.configuration.experiment.Position;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import ij.ImageJ;
import boa.image.Image;
import boa.image.ImageMask;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import boa.plugins.PluginFactory;
import boa.plugins.Segmenter;
import boa.plugins.plugins.segmenters.BacteriaFluo;
import boa.plugins.plugins.segmenters.MicrochannelFluo2D;
import boa.plugins.plugins.transformations.CropMicrochannelsFluo2D;
/**
 *
 * @author jollion
 */
public class TestProcessMicrochannels {
    public static void main(String[] args) {
        PluginFactory.findPlugins("boa.plugins.plugins");
        new ImageJ();
        int time =1;
        int field = 294;
        //String dbName = "TestThomasRawStacks";
        String dbName = "fluo171204_WT_750ms";
        testSegMicrochannelsFromXP(dbName, field, time);
        //testSegAndTrackMicrochannelsFromXP(dbName, field, 0, 700);
    }
    
    public static void testSegMicrochannelsFromXP(String dbName, int fieldNumber, int timePoint) {
        MasterDAO mDAO = new Task(dbName).getDB();
        Position f = mDAO.getExperiment().getPosition(fieldNumber);
        StructureObject root = mDAO.getDao(f.getName()).getRoot(timePoint);
        logger.debug("field name: {}, root==null? {} frame: {}", f.getName(), root==null, root.getFrame());
        Image input = root.getRawImage(0);
        MicrochannelFluo2D.debug=true;
        CropMicrochannelsFluo2D.debug=true;
        //ObjectPopulation pop = MicroChannelFluo2D.run(input, 355, 40, 20, 50, 0.6d, 100);
        //ObjectPopulation pop = MicroChannelFluo2D.run2(input, 355, 40, 20);
        Segmenter s = mDAO.getExperiment().getStructure(0).getProcessingScheme().getSegmenter();

        RegionPopulation pop=s.runSegmenter(input, 0, root);
        logger.debug("object count: {}", pop.getRegions().size());
        ImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(input);
        disp.showImage(pop.getLabelMap());
        
        // test split
        //ObjectPopulation popSplit = testObjectSplitter(intensityMap, pop.getChildren().get(0));
        //disp.showImage(popSplit.getLabelImage());
    }

}
