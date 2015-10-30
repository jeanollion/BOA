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

import static TestUtils.Utils.logger;
import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageDisplayer;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.ExperimentDAO;
import dataStructure.configuration.MicroscopyField;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import de.caluga.morphium.Morphium;
import image.Image;
import image.ImageMask;
import plugins.PluginFactory;
import plugins.plugins.segmenters.BacteriaFluo;
import plugins.plugins.segmenters.SpotFluo2D5;
import utils.MorphiumUtils;

/**
 *
 * @author jollion
 */
public class TestProcessMutations {
    Experiment xp;
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        TestProcessMutations t = new TestProcessMutations();
        t.testSegMutationsFromXP();
    }
    public void testSegMutationsFromXP() {
        int field = 0;
        int time = 0;
        int channel = 0;
        int bacteria = 1;
        //String dbName = "testFluo";
        String dbName = "testFluo60";
        Morphium m=MorphiumUtils.createMorphium(dbName);
        ExperimentDAO xpDAO = new ExperimentDAO(m);
        xp=xpDAO.getExperiment();
        logger.info("Experiment: {} retrieved from db: {}", xp.getName(), dbName);

        ObjectDAO dao = new ObjectDAO(m, xpDAO);
        MicroscopyField f = xp.getMicroscopyField(field);
        StructureObject root = dao.getRoot(f.getName(), time);
        logger.debug("field name: {}, root==null? {}", f.getName(), root==null);
        StructureObject mc = root.getChildObjects(0, dao, false).get(channel);
        StructureObject bact = mc.getChildObjects(1, dao, false).get(bacteria);
        Image input = mc.getRawImage(2);
        ImageMask parentMask = mc.getMask();
        SpotFluo2D5.debug=true;
        ObjectPopulation pop = SpotFluo2D5.run(input, parentMask, 2, 2, 5, 6, 5);
        ImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(input);
        disp.showImage(pop.getLabelImage());
        
        // test split
        //ObjectPopulation popSplit = testObjectSplitter(input, pop.getObjects().get(0));
        //disp.showImage(popSplit.getLabelImage());
    } 
}
