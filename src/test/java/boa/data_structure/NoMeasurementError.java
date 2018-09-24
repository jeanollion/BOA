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
package boa.data_structure;

import static boa.test_utils.TestUtils.logger;
import boa.core.Task;
import boa.configuration.experiment.Position;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.ObjectDAO;
import boa.data_structure.StructureObject;
import java.util.List;

/**
 *
 * @author jollion
 */
public class NoMeasurementError {
    public static void main(String[] args) {
        String dbName = "boa_fluo160428";
        int fIdx = 0;
        int structureIdx = 2;
        MasterDAO db = new Task(dbName).getDB();
        Position f = db.getExperiment().getPosition(fIdx);
        ObjectDAO dao = db.getDao(f.getName());
        checkMeasurements(dao, structureIdx);
    }
    public static void checkMeasurements(ObjectDAO dao, int sIdx) {
        int count = 0;
        List<StructureObject> roots=  dao.getRoots();
        for (StructureObject r : roots) {
            for (StructureObject o : r.getChildren(sIdx)) {
                if (o.getMeasurements().getValues().isEmpty()) {
                    logger.debug("no measurement for: {}", o);
                    ++count;
                }
            }
        }
        logger.debug("total: {}", count);
    }
}
