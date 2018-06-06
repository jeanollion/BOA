/*
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.core;

import boa.configuration.experiment.Experiment;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.MasterDAOFactory;
import static boa.test_utils.TestUtils.logger;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 *
 * @author Jean Ollion
 */
public class TaskTest {
    //@Rule
    //public TemporaryFolder testFolder = new TemporaryFolder();
    //MasterDAO db;
    
    private static MasterDAO getXP() {
        Experiment xp = new Experiment("xp1");
        xp.getChannelImages().insert(xp.getChannelImages().createChildInstance("ch1"));
       xp.getStructures().insert(xp.getStructures().createChildInstance("s0"));
        xp.getStructures().insert(xp.getStructures().createChildInstance("s1"));
        xp.getStructures().insert(xp.getStructures().createChildInstance("s2"));
        xp.createPosition("pos0");
        xp.createPosition("pos1");
        xp.createPosition("pos2");
        return MasterDAOFactory.createDAO("xp1", "/", MasterDAOFactory.DAOType.DBMap);
    }

    public static void main(String[] args) {
        
    
        MasterDAO db = getXP();
        List<Task> tasks = new ArrayList<>();
        tasks.add(new Task(db).setPositions(0, 1).setStructures(0).setActions(true, true, true, false));
        tasks.add(new Task(db).setPositions(0, 1).setStructures(1, 2).setActions(true, false, true, false));
        tasks.add(new Task(db).setPositions(2).setStructures(0).setActions(true, true, true, false).addExtractMeasurementDir("didir", 1, 2));
        tasks.add(new Task(db).setPositions(0, 1).setStructures(0, 1).setActions(false, false, true, true).setExportData(false, true, true, true, true));
        logger.debug("processing tasks: {}", Task.getProcessingTasksByPosition(tasks));
        logger.debug("processing tasks: {}", Task.getGlobalTasksByExperiment(tasks));
    }
}
