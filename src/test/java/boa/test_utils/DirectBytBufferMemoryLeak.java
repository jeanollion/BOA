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
package boa.test_utils;

import static boa.test_utils.TestUtils.logger;
import boa.core.Task;
import boa.configuration.experiment.Position;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.StructureObject;

/**
 *
 * @author jollion
 */
public class DirectBytBufferMemoryLeak {
    public static void main(String[] args) {
        String dbName = "MutH_150324";
        Task t = new Task(dbName);
        MasterDAO db = t.getDB();
        Position m =db.getExperiment().getPosition(1);
        for (int l = 0; l<1000; ++l) {
            logger.debug("loop: {}", l);
            long t0 = System.currentTimeMillis();
            for (int i = 0; i<m.getFrameNumber(false); ++i) m.getInputImages().getImage(0, i);
            long t1 = System.currentTimeMillis();
            logger.debug("perImage time: {}", (t1-t0)/m.getFrameNumber(false));
            //for (StructureObject o) db.getDao(m.getName()).getRoots();
            m.flushImages(true, true);
            System.gc();
            t.publishMemoryUsage("after flush images: "+l);
        }
    }
}
