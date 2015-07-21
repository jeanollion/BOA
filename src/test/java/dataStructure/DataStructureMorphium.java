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
package dataStructure;

import dataStructure.configuration.Experiment;
import dataStructure.configuration.ExperimentDAO;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author jollion
 */
public class DataStructureMorphium {
    @Test
    public void testConfiguration() {
        try {
            MorphiumConfig cfg = new MorphiumConfig();
            cfg.setDatabase("testdb");
            cfg.addHost("localhost", 27017);
            Morphium m=new Morphium(cfg);
            m.clearCollection(Experiment.class);
            
            Experiment xp = new Experiment("test xp");
            int idx = xp.getStructureNB();
            xp.getStructures().insert(xp.getStructures().createChildInstance("structureTest"));
            m.store(xp);
            
            m=new Morphium(cfg);
            ExperimentDAO dao = new ExperimentDAO(m);
            xp = dao.getExperiment();
            
            assertEquals("structure nb", idx+1, xp.getStructureNB());
            assertEquals("structure name", "structureTest", xp.getStructure(idx).getName());
            assertTrue("xp init postLoad", xp.getChildCount()>0);
            
        } catch (UnknownHostException ex) {
            Logger.getLogger(DataStructureMorphium.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
