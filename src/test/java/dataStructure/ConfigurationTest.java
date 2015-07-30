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

import core.Processor;
import dataStructure.configuration.ChannelImage;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.Structure;
import dataStructure.containers.MultipleImageContainer;
import dataStructure.containers.MultipleImageContainerSingleFile;
import image.ImageByte;
import image.ImageReader;
import image.ImageWriter;
import image.ImageFormat;
import images.ImageIOTest;
import java.io.File;
import java.util.ArrayList;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 *
 * @author jollion
 */
public class ConfigurationTest {
    
    
    @Test
    public void testHierarchicalStructureOrder() {
        
        Structure s0 = new Structure("StructureIdx0", -1);
        Structure s1 = new Structure("StructureIdx1", 0);
        Structure s2 = new Structure("StructureIdx2", 0);
        Structure s3 = new Structure("StructureIdx3", 1);
        Structure s4 = new Structure("StructureIdx4", -1);
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
    
    
}
