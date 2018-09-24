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
package boa.gaussian_fit;

import static boa.test_utils.TestUtils.logger;
import boa.gui.image_interaction.IJImageDisplayer;
import boa.core.Task;
import boa.configuration.experiment.Position;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.plugins.PluginFactory;
import boa.processing.test.TestProcessMutations;

/**
 *
 * @author Jean Ollion
 */
public class GaussianFitTestOnMutations {
    MasterDAO db;
    public static void main(String[] args) {
        PluginFactory.findPlugins("boa.plugins.plugins");
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
        Position f = db.getExperiment().getPosition(fieldIdx);
        StructureObject root = db.getDao(f.getName()).getRoot(tp);
        //logger.debug("field name: {}, root==null? {}", f.getName(), root==null);
        StructureObject mc = root.getChildren(0).get(mcIdx);
        RegionPopulation pop = mc.getChildRegionPopulation(2);
        RegionPopulation.GaussianFit.disp=true;
        new IJImageDisplayer().showImage(mc.getRawImage(2).duplicate("source"));
        pop.filter(new RegionPopulation.GaussianFit(mc.getRawImage(2), 4, 2, 6, 0.5, 1, 5));
    }
}
