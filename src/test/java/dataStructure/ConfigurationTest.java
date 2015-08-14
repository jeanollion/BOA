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

import TestUtils.Utils;
import dataStructure.configuration.ChannelImage;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.ExperimentDAO;
import dataStructure.configuration.Structure;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import java.net.UnknownHostException;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import testPlugins.dummyPlugins.DummySegmenter;
import plugins.PluginFactory;
import plugins.plugins.trackers.TrackerObjectIdx;

/**
 *
 * @author jollion
 */
public class ConfigurationTest {
    
    
    @Test
    public void testHierarchicalStructureOrder() {
        
        Structure s0 = new Structure("StructureIdx0", -1, 0);
        Structure s1 = new Structure("StructureIdx1", 0, 0);
        Structure s2 = new Structure("StructureIdx2", 0, 0);
        Structure s3 = new Structure("StructureIdx3", 1, 0);
        Structure s4 = new Structure("StructureIdx4", -1, 0);
        Experiment xp = new Experiment("test XP", s0, s1, s2, s3, s4);
        assertEquals("Structure 2", s2, xp.getStructure(2));
        
        assertEquals("Hierarchical order s0:", 0, xp.getHierachicalOrder(0));
        assertEquals("Hierarchical order s1:", 1, xp.getHierachicalOrder(1));
        assertEquals("Hierarchical order s2:", 1, xp.getHierachicalOrder(2));
        assertEquals("Hierarchical order s3:", 2, xp.getHierachicalOrder(3));
        assertEquals("Hierarchical order s4:", 0, xp.getHierachicalOrder(4));
        
        int[][] orders = xp.getStructuresInHierarchicalOrder();
        assertArrayEquals("orders 0:", new int[]{0, 4}, orders[0]);
        assertArrayEquals("orders 1:", new int[]{1, 2}, orders[1]);
        assertArrayEquals("orders 2:", new int[]{3}, orders[2]);
        
    }
    
    @Test
    public void testStroreSimpleXPMorphium() {
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
            Utils.logger.error("couldnot connect to db", ex);
        }
    }
    
    @Test
    public void testStroreCompleteXPMorphium() {
        try {
            MorphiumConfig cfg = new MorphiumConfig();
            cfg.setDatabase("testdb");
            cfg.addHost("localhost", 27017);
            Morphium m=new Morphium(cfg);
            m.clearCollection(Experiment.class);
            
            // set-up experiment structure
            Experiment xp = new Experiment("test");
            ChannelImage image = xp.getChannelImages().createChildInstance();
            xp.getChannelImages().insert(image);
            Structure microChannel = xp.getStructures().createChildInstance("MicroChannel");
            Structure bacteries = xp.getStructures().createChildInstance("Bacteries");
            xp.getStructures().insert(microChannel);
            bacteries.setParentStructure(0);
            int idx = xp.getStructureNB();
            
            // set-up processing chain
            PluginFactory.findPlugins("plugin.dummyPlugins");
            microChannel.getProcessingChain().setSegmenter(new DummySegmenter(true, 2));
            bacteries.getProcessingChain().setSegmenter(new DummySegmenter(false, 3));
            
            // set-up traking
            PluginFactory.findPlugins("plugins.plugins.trackers");
            microChannel.setTracker(new TrackerObjectIdx());
            bacteries.setTracker(new TrackerObjectIdx());
            
            m.store(xp);
            m=new Morphium(cfg);
            ExperimentDAO dao = new ExperimentDAO(m);
            xp = dao.getExperiment();
            
            assertEquals("structure nb", idx, xp.getStructureNB());
            assertTrue("xp init postLoad", xp.getChildCount()>0);
            
        } catch (UnknownHostException ex) {
            Utils.logger.error("couldnot connect to db", ex);
        }
    }
}
