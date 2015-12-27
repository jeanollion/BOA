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
import boa.gui.objects.DBConfiguration;
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
import org.junit.Before;
import org.junit.Test;
import testPlugins.dummyPlugins.DummySegmenter;
import plugins.PluginFactory;
import plugins.plugins.processingScheme.SegmentThenTrack;
import plugins.plugins.trackers.ObjectIdxTracker;

/**
 *
 * @author jollion
 */
public class ConfigurationTest {
    Experiment xp;
    Structure s0, s1, s2, s3, s4, s5, s6;
    @Before
    public void setUp() {
        s0 = new Structure("StructureIdx0", -1, 0);
        s1 = new Structure("StructureIdx1", 0, 0);
        s2 = new Structure("StructureIdx2", 0, 0);
        s3 = new Structure("StructureIdx3", 1, 0);
        s4 = new Structure("StructureIdx4", -1, 0);
        s5 = new Structure("StructureIdx5", 3, 0);
        s6 = new Structure("StructureIdx6", 3, 0);
        xp = new Experiment("test XP", s0, s1, s2, s3, s4, s5, s6);
        /*
        root
        -s0
        --s1
        ---s3
        ----s5
        ----s6
        --s2
        -s4
        */
    }
    
    @Test
    public void testHierarchicalStructureOrder() {
        assertEquals("Structure 2", s2, xp.getStructure(2));
        
        assertEquals("Hierarchical order s0:", 0, xp.getHierachicalOrder(0));
        assertEquals("Hierarchical order s1:", 1, xp.getHierachicalOrder(1));
        assertEquals("Hierarchical order s2:", 1, xp.getHierachicalOrder(2));
        assertEquals("Hierarchical order s3:", 2, xp.getHierachicalOrder(3));
        assertEquals("Hierarchical order s4:", 0, xp.getHierachicalOrder(4));
        assertEquals("Hierarchical order s5:", 3, xp.getHierachicalOrder(5));
        assertEquals("Hierarchical order s5:", 3, xp.getHierachicalOrder(6));
        
        int[][] orders = xp.getStructuresInHierarchicalOrder();
        assertArrayEquals("orders 0:", new int[]{0, 4}, orders[0]);
        assertArrayEquals("orders 1:", new int[]{1, 2}, orders[1]);
        assertArrayEquals("orders 2:", new int[]{3}, orders[2]);
        assertArrayEquals("orders 3:", new int[]{5,6}, orders[3]);
    }
    
    @Test
    public void testPathToStructure() {
        assertArrayEquals("path to structure 0->6", new int[]{1, 3, 6}, xp.getPathToStructure(0, 6));
        assertArrayEquals("path to structure 2->6", new int[]{}, xp.getPathToStructure(2, 6)); // 6 is not an indirect child of 2
        assertArrayEquals("path to root 6", new int[]{0, 1, 3, 6}, xp.getPathToRoot(6));
    }
    
    @Test
    public void testGetChildren() {
        assertArrayEquals("getChildren 3", new int[]{5, 6}, xp.getChildStructures(3));
        assertArrayEquals("getChildren 2", new int[]{}, xp.getChildStructures(2));
        assertArrayEquals("getAllChildren 0", new int[]{1, 2, 3, 5, 6}, xp.getAllChildStructures(0));
    }
    
    @Test
    public void testStroreSimpleXPMorphium() {
        DBConfiguration db = new DBConfiguration("testdb");
        db.clearObjectsInDB();
        Experiment xp = new Experiment("test xp");
        int idx = xp.getStructureCount();
        xp.getStructures().insert(xp.getStructures().createChildInstance("structureTest"));
        db.getXpDAO().store(xp);
        db.getXpDAO().clearCache();
        xp = db.getXpDAO().getExperiment();
        assertEquals("structure nb", idx+1, xp.getStructureCount());
        assertEquals("structure name", "structureTest", xp.getStructure(idx).getName());
        assertTrue("xp init postLoad", xp.getChildCount()>0);

    }
    
    @Test
    public void testStroreCompleteXPMorphium() {
        DBConfiguration db = new DBConfiguration("testdb");
        db.clearObjectsInDB();

        // set-up experiment structure
        Experiment xp = new Experiment("test");
        ChannelImage image = xp.getChannelImages().createChildInstance();
        xp.getChannelImages().insert(image);
        Structure microChannel = xp.getStructures().createChildInstance("MicroChannel");
        Structure bacteries = xp.getStructures().createChildInstance("Bacteries");
        xp.getStructures().insert(microChannel);
        bacteries.setParentStructure(0);
        int idx = xp.getStructureCount();

        // set-up processing & tracking 
        PluginFactory.findPlugins("plugin.dummyPlugins");
        microChannel.setProcessingScheme(new SegmentThenTrack(new DummySegmenter(true, 2), new ObjectIdxTracker()));
        bacteries.setProcessingScheme(new SegmentThenTrack(new DummySegmenter(true, 3), new ObjectIdxTracker()));
        

        db.getXpDAO().store(xp);
        db.getXpDAO().clearCache();
        xp = db.getXpDAO().getExperiment();

        assertEquals("structure nb", idx, xp.getStructureCount());
        assertTrue("xp init postLoad", xp.getChildCount()>0);
    }
    
    
}
