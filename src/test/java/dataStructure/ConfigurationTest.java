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
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();
    
    @Test
    public void testHierarchicalStructureOrder() {
        Experiment xp = new Experiment("test XP");
        Structure s2 = new Structure("StructureIdx2", 0);
        xp.getStructures().insert(s2); // idx=2
        Structure s3 = new Structure("StructureIdx3", 1);
        xp.getStructures().insert(s3); // idx=3
        Structure s4 = new Structure("StructureIdx4", -1);
        xp.getStructures().insert(s4); // idx=4
        
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
    public void importFieldTest() {
        // creation de l'image de test
        String title = "imageTestMultiple";
        ImageFormat format = ImageFormat.OMETIF;
        File folder = testFolder.newFolder("TestImages");
        int timePoint = 3;
        int channel = 2;
        ImageByte[][] images = new ImageByte[timePoint][channel];
        for (int t = 0; t<timePoint; t++) {
            for (int c = 0; c<channel;c++) {
                images[t][c] = new ImageByte(title+"t"+t+"c"+c, 6, 5, 4);
                images[t][c].setPixel(t, c, c, 1);
            }
        }
        ImageWriter.writeToFile(images, folder.getAbsolutePath(), title, format);
        File folder2 = new File(folder.getAbsolutePath()+File.separator+"subFolder");
        folder2.mkdir();
        ImageWriter.writeToFile(images, folder2.getAbsolutePath(), title, format);
        ImageWriter.writeToFile(images, folder2.getAbsolutePath(), title+"2", format);
        
        Experiment xp = new Experiment("testXP");
        xp.getChannelImages().insert(new ChannelImage("channel2"));
        
        String[] files = new String[]{folder.getAbsolutePath()};
        Processor.importFiles(files, xp);
        assertEquals("number of fields detected", 2, xp.getMicroscopyFields().getChildCount());
        MultipleImageContainer c = xp.getMicroscopyField(0).getImages();
        ImageReader reader = new ImageReader(c.getPath());
        assertEquals("extension:", ImageFormat.OMETIF.getExtension(), reader.getExtension().getExtension());
        ImageByte im00 = (ImageByte)reader.openImage(c.getImageIOCoordinates(0, 0));
        ImageIOTest.assertImageByte(images[0][0], im00);
    }
}
