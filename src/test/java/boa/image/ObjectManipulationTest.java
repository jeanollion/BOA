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
package boa.image;

import static boa.test_utils.GenerateSyntheticData.generateImages;
import boa.test_utils.TestUtils;
import boa.configuration.experiment.Structure;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.Voxel;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageLabeller;
import boa.image.processing.RegionFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;
import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author jollion
 */
public class ObjectManipulationTest {
    ImageByte im, imRelabel;
    BoundingBox bound1, bound3;
    Region o1;
    Region o3;
    
    @Before
    public void setUp() {
        im = new ImageByte("", 4, 4, 5);
        im.setPixel(0, 0, 0, 1);
        im.setPixel(1, 0, 0, 1);
        im.setPixel(2, 1, 1, 3);
        im.setPixel(3, 1, 1, 3);
        imRelabel = new ImageByte("", 4, 4, 5);
        imRelabel.setPixel(0, 0, 0, 1);
        imRelabel.setPixel(1, 0, 0, 1);
        imRelabel.setPixel(2, 1, 1, 2);
        imRelabel.setPixel(3, 1, 1, 2);
        
        bound1=new BoundingBox(0, 1, 0, 0,0,0);
        bound3 = new BoundingBox(2, 3, 1,1,1,1);
        o1 = new Region(im.cropLabel(1, bound1), 1, false);
        o3 = new Region(im.cropLabel(3, bound3), 3, false);
        
    }
    @Test
    public void testGetObjectsVoxels() {
        Region[] obs = RegionFactory.getRegions(im, false);
        assertEquals("object number", 2, obs.length);
        assertEquals("object1 vox number:", 2, obs[0].getVoxels().size());
        assertTrue("object1 vox1:", new Voxel(0, 0, 0).equals(obs[0].getVoxels().get(0)));
        assertTrue("object1 vox2:", new Voxel(1, 0, 0).equals(obs[0].getVoxels().get(1)));
        assertEquals("object3 vox number:", 2, obs[1].getVoxels().size());
        assertTrue("object3 vox1:", new Voxel(2, 1, 1).equals(obs[1].getVoxels().get(0)));
        assertTrue("object3 vox2:", new Voxel(3, 1, 1).equals(obs[1].getVoxels().get(1)));
        assertObject3DVoxels(o1, obs[0]);
        assertObject3DVoxels(o3, obs[1]);
    }
    
    @Test
    public void testGetObjectsBounds() {
        TreeMap<Integer, BoundingBox> bds = RegionFactory.getBounds(im);
        assertEquals("object number", 2, bds.size());
        assertTrue("bound1", bound1.equals(bds.get(1)));
        assertTrue("bound3", bound3.equals(bds.get(3)));
    }
    
    @Test
    public void testGetObjectsImages() {
        Region[] obs = RegionFactory.getObjectsImage(im, null, false);
        assertEquals("object number", 2, obs.length);
        TestUtils.assertImage((ImageByte)obs[0].getMask(), (ImageByte)o1.getMask(), 0);
        TestUtils.assertImage((ImageByte)obs[1].getMask(), (ImageByte)o3.getMask(), 0);
    }
    
    @Test 
    public void testConversionVoxelMask() {
        Region[] obs = RegionFactory.getRegions(im, false);
        ImageByte imtest = new ImageByte("", im);
        int label=1;
        for (Region o : obs) o.draw(imtest, label++);
        ImageByte imtest2 = new ImageByte("", im);
        label = 1;
        for (Region o : obs) imtest2.appendBinaryMasks(label++, o.getMask());
        TestUtils.assertImage(imtest, imtest2, 0);
    }
    
    @Test 
    public void testConversionMaskVoxels() {
        Region[] obs = RegionFactory.getObjectsImage(im, null, false);
        ImageByte imtest = new ImageByte("", im);
        int label=1;
        for (Region o : obs) {
            o.getVoxels(); // get voxels to ensure draw with voxels over draw with mask
            o.draw(imtest, label++);
        }
        ImageByte imtest2 = new ImageByte("", im);
        label = 1;
        for (Region o : obs) imtest2.appendBinaryMasks(label++, o.getMask());
        TestUtils.assertImage(imtest, imtest2, 0);
    }
    
    @Test
    public void testRelabelImage() {
        ImageByte im2 = im.duplicate("");
        RegionFactory.relabelImage(im2, null);
        TestUtils.assertImage(imRelabel, im2, 0);
    }
    
    @Test 
    public void testObjectPopulation() {
        RegionPopulation popObj = new RegionPopulation(new ArrayList<Region>(Arrays.asList(new Region[]{o1, o3})), im);
        TestUtils.assertImage(im, (ImageByte)popObj.getLabelMap(), 0);
        popObj.relabel();
        TestUtils.assertImage(imRelabel, (ImageByte)popObj.getLabelMap(), 0);
        popObj = new RegionPopulation(new ArrayList<Region>(Arrays.asList(new Region[]{o1, o3})), im);
        popObj.relabel();
        TestUtils.assertImage(imRelabel, (ImageByte)popObj.getLabelMap(), 0);
        
        RegionPopulation popIm = new RegionPopulation(im, true);
        assertEquals("number of objects", 2, popIm.getRegions().size());
        assertObject3DVoxels(o1, popIm.getRegions().get(0));
        assertObject3DVoxels(o3, popIm.getRegions().get(1));
    }
    
    public static void assertObject3DVoxels(Region expected, Region actual) {
        assertEquals("object voxel number", expected.getVoxels().size(), actual.getVoxels().size());
        for (int i = 0; i<expected.getVoxels().size(); ++i) {
            TestUtils.logger.trace("assert voxel: {} expected: {} actual: {}", i, expected.getVoxels().get(i), actual.getVoxels().get(i));
            assertTrue("voxel: "+i, expected.getVoxels().get(i).equals(actual.getVoxels().get(i)));
        }
    }
    
    
}
