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

import boa.core.Task;
import boa.data_structure.dao.MasterDAO;
import boa.plugins.ManualSegmenter;
import boa.plugins.PluginFactory;

/**
 *
 * @author Jean Ollion
 */
public class TestManualSegmentation {
    public static void main(String[] args) {
        PluginFactory.findPlugins("boa.plugins.plugins");
        String dbName= "fluo151127";
        int field = 0;
        testManualSegmentation(dbName);
    }
    public static void testManualSegmentation(String dbName) {
        MasterDAO db = new Task(dbName).getDB();
        ManualSegmenter m = db.getExperiment().getStructure(2).getManualSegmenter();
        m.manualSegment(null, null, null, 2, null);
    }
}
