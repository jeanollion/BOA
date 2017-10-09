/*
 * Copyright (C) 2017 jollion
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
package TestUtils;

import static TestUtils.Utils.logger;
import core.Task;
import dataStructure.configuration.MicroscopyField;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.StructureObject;

/**
 *
 * @author jollion
 */
public class DirectBytBufferMemoryLeak {
    public static void main(String[] args) {
        String dbName = "MutH_150324";
        Task t = new Task(dbName);
        MasterDAO db = t.getDB();
        MicroscopyField m =db.getExperiment().getPosition(1);
        for (int l = 0; l<1000; ++l) {
            logger.debug("loop: {}", l);
            long t0 = System.currentTimeMillis();
            for (int i = 0; i<m.getTimePointNumber(false); ++i) m.getInputImages().getImage(0, i);
            long t1 = System.currentTimeMillis();
            logger.debug("perImage time: {}", (t1-t0)/m.getTimePointNumber(false));
            //for (StructureObject o) db.getDao(m.getName()).getRoots();
            m.flushImages(true, true);
            System.gc();
            t.publishMemoryUsage("after flush images: "+l);
        }
    }
}
