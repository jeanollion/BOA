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
package GaussianFitTest;

import static TestUtils.Utils.logger;
import boa.gui.imageInteraction.IJImageDisplayer;
import core.Task;
import dataStructure.configuration.MicroscopyField;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import plugins.PluginFactory;
import processing.dataGeneration.TestProcessMutations;

/**
 *
 * @author jollion
 */
public class GaussianFitTestOnMutations {
    MasterDAO db;
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        //String dbName = "testSub60";
        //final String dbName = "boa_fluo151127";
        final String dbName = "boa_fluo160428";
        int fIdx = 1;
        int mcIdx =0;
        //String dbName = "fluo151130_Output";
        GaussianFitTestOnMutations t = new GaussianFitTestOnMutations();
        t.init(dbName);
        t.testGaussFit(fIdx, mcIdx, 3);
    }
    public void init(String dbName) {
        db = new Task(dbName).getDB();
        logger.info("Experiment: {} retrieved from db: {}", db.getExperiment().getName(), dbName);
    }
    public void testGaussFit(int fieldIdx, int mcIdx, int tp) {
        MicroscopyField f = db.getExperiment().getPosition(fieldIdx);
        StructureObject root = db.getDao(f.getName()).getRoot(tp);
        //logger.debug("field name: {}, root==null? {}", f.getName(), root==null);
        StructureObject mc = root.getChildren(0).get(mcIdx);
        ObjectPopulation pop = mc.getObjectPopulation(2);
        ObjectPopulation.GaussianFit.disp=true;
        new IJImageDisplayer().showImage(mc.getRawImage(2).duplicate("source"));
        pop.filter(new ObjectPopulation.GaussianFit(mc.getRawImage(2), 4, 2, 6, 0.5, 1, 5));
    }
}
