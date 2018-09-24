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
package boa.image.processing;

import boa.configuration.experiment.Position;
import boa.core.Task;
import boa.data_structure.StructureObject;
import boa.data_structure.dao.MasterDAO;
import boa.plugins.PluginFactory;
import boa.plugins.plugins.measurements.FluorescenceFit;
import ij.ImageJ;

/**
 *
 * @author Jean Ollion
 */
public class TestFluorescenceFitting {
    public static void main(String[] args) {
        PluginFactory.findPlugins("boa.plugins.plugins");
        new ImageJ();
        //String dbName = "170919_thomas";
        String dbName = "preproc_example";
        int postition= 0, frame=0, mc=1, b=0, m = 0;
        
        MasterDAO mDAO = new Task(dbName).getDB();
        
        //testAllObjects(mDAO, structureIdx, 26);
        
        int parentStructure = mDAO.getExperiment().getStructure(1).getParentStructure();
        Position f = mDAO.getExperiment().getPosition(postition);
        StructureObject root = mDAO.getDao(f.getName()).getRoots().get(frame);
        StructureObject bact = root.getChildren(parentStructure).stream().filter(o->o.getTrackHead().getIdx()==mc).findAny().get().getChildren(1).get(b);
        FluorescenceFit fluo = new FluorescenceFit().setVerbose(true).setObjectClasses(1, 2);
        
        fluo.performMeasurement(bact);
        
    }
}
