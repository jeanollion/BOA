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
import boa.gui.imageInteraction.ImageDisplayer;
import core.Task;
import dataStructure.configuration.MicroscopyField;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import ij.ImageJ;
import image.Image;
import plugins.PluginFactory;
import plugins.ProcessingScheme;
import plugins.Segmenter;
import plugins.plugins.segmenters.MicrochannelPhase2D;

/**
 *
 * @author jollion
 */
public class TestProcessMicrochannelsPhase {
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        new ImageJ();
        int time =534;
        int field = 0;
        //String dbName = "boa_phase150616wt";
        //String dbName = "boa_phase141129wt";
        String dbName = "ProblemeTracking";
        testSegMicrochannelsFromXP(dbName, field, time);
    }
    
    public static void testSegMicrochannelsFromXP(String dbName, int fieldNumber, int timePoint) {
        MasterDAO mDAO =new Task(dbName).getDB();
        MicroscopyField f = mDAO.getExperiment().getPosition(fieldNumber);
        StructureObject root = mDAO.getDao(f.getName()).getRoot(timePoint);
        if (root==null) root = f.createRootObjects(mDAO.getDao(f.getName())).get(timePoint);
        logger.debug("field name: {}, root==null? {}", f.getName(), root==null);
        Image input = root.getRawImage(0);
        MicrochannelPhase2D.debug=true;
        //MicroChannelPhase2D seg = new MicroChannelPhase2D().setyStartAdjustWindow(5);
        Segmenter s = mDAO.getExperiment().getStructure(0).getProcessingScheme().getSegmenter();
        ObjectPopulation pop = s.runSegmenter(input, 0, root);
        //ObjectPopulation pop = MicroChannelFluo2D.run2(input, 355, 40, 20);
        ImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(input);
        disp.showImage(pop.getLabelMap());
        logger.debug("{} objects found", pop.getObjects().size());
        // test split
        //ObjectPopulation popSplit = testObjectSplitter(intensityMap, pop.getChildren().get(0));
        //disp.showImage(popSplit.getLabelImage());
    }
    
}
