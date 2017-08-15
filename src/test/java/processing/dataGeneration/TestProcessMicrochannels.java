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
package processing.dataGeneration;

import static TestUtils.Utils.logger;
import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.IJImageWindowManager;
import boa.gui.imageInteraction.ImageDisplayer;
import boa.gui.imageInteraction.ImageObjectInterface;
import configuration.parameters.PostFilterSequence;
import configuration.parameters.PreFilterSequence;
import core.Task;
import dataStructure.configuration.MicroscopyField;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import ij.ImageJ;
import image.Image;
import image.ImageMask;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import plugins.PluginFactory;
import plugins.Segmenter;
import plugins.OverridableThreshold;
import plugins.plugins.segmenters.BacteriaFluo;
import plugins.plugins.segmenters.MicroChannelFluo2D;
import plugins.plugins.transformations.CropMicroChannelFluo2D;

/**
 *
 * @author jollion
 */
public class TestProcessMicrochannels {
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        new ImageJ();
        int time =0;
        int field = 0;
        String dbName = "fluo170517_MutH";
        //String dbName = "fluo160408_MutH";
        testSegMicrochannelsFromXP(dbName, field, time);
        //testSegAndTrackMicrochannelsFromXP(dbName, field, 0, 700);
    }
    
    public static void testSegMicrochannelsFromXP(String dbName, int fieldNumber, int timePoint) {
        MasterDAO mDAO = new Task(dbName).getDB();
        MicroscopyField f = mDAO.getExperiment().getPosition(fieldNumber);
        StructureObject root = mDAO.getDao(f.getName()).getRoot(timePoint);
        logger.debug("field name: {}, root==null? {} frame: {}", f.getName(), root==null, root.getFrame());
        Image input = root.getRawImage(0);
        MicroChannelFluo2D.debug=true;
        CropMicroChannelFluo2D.debug=true;
        //ObjectPopulation pop = MicroChannelFluo2D.run(input, 355, 40, 20, 50, 0.6d, 100);
        //ObjectPopulation pop = MicroChannelFluo2D.run2(input, 355, 40, 20);
        Segmenter s = mDAO.getExperiment().getStructure(0).getProcessingScheme().getSegmenter();
        ((OverridableThreshold)s).setThresholdValue(10.5);
        ObjectPopulation pop=s.runSegmenter(input, 0, root);
        logger.debug("object count: {}", pop.getObjects().size());
        ImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(input);
        disp.showImage(pop.getLabelMap());
        
        // test split
        //ObjectPopulation popSplit = testObjectSplitter(intensityMap, pop.getChildren().get(0));
        //disp.showImage(popSplit.getLabelImage());
    }

}
