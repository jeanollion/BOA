/*
 * Copyright (C) 2016 jollion
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
package boa.processing.test;

import static boa.test_utils.TestUtils.logger;
import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.IJImageWindowManager;
import boa.gui.imageInteraction.ImageDisplayer;
import boa.gui.imageInteraction.ImageObjectInterface;
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
import boa.plugins.plugins.segmenters.BacteriaIntensity;
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
