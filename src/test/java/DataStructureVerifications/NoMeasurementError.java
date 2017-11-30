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
package DataStructureVerifications;

import static TestUtils.TestUtils.logger;
import core.Task;
import dataStructure.configuration.MicroscopyField;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;
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
        MicroscopyField f = db.getExperiment().getPosition(fIdx);
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
