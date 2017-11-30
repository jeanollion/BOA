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
package images;

import static TestUtils.GenerateSyntheticData.generateImages;
import TestUtils.TestUtils;
import dataStructure.configuration.Structure;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.Voxel;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageLabeller;
import image.ObjectFactory;
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
    Object3D o1, o3;
    
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
        o1 = new Object3D(im.cropLabel(1, bound1), 1);
        o3 = new Object3D(im.cropLabel(3, bound3), 3);
        
    }
    @Test
    public void testGetObjectsVoxels() {
        Object3D[] obs = ObjectFactory.getObjectsVoxels(im, false);
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
        TreeMap<Integer, BoundingBox> bds = ObjectFactory.getBounds(im);
        assertEquals("object number", 2, bds.size());
        assertTrue("bound1", bound1.equals(bds.get(1)));
        assertTrue("bound3", bound3.equals(bds.get(3)));
    }
    
    @Test
    public void testGetObjectsImages() {
        Object3D[] obs = ObjectFactory.getObjectsImage(im, null, false);
        assertEquals("object number", 2, obs.length);
        TestUtils.assertImage((ImageByte)obs[0].getMask(), (ImageByte)o1.getMask(), 0);
        TestUtils.assertImage((ImageByte)obs[1].getMask(), (ImageByte)o3.getMask(), 0);
    }
    
    @Test 
    public void testConversionVoxelMask() {
        Object3D[] obs = ObjectFactory.getObjectsVoxels(im, false);
        ImageByte imtest = new ImageByte("", im);
        int label=1;
        for (Object3D o : obs) o.draw(imtest, label++);
        ImageByte imtest2 = new ImageByte("", im);
        label = 1;
        for (Object3D o : obs) imtest2.appendBinaryMasks(label++, o.getMask());
        TestUtils.assertImage(imtest, imtest2, 0);
    }
    
    @Test 
    public void testConversionMaskVoxels() {
        Object3D[] obs = ObjectFactory.getObjectsImage(im, null, false);
        ImageByte imtest = new ImageByte("", im);
        int label=1;
        for (Object3D o : obs) {
            o.getVoxels(); // get voxels to ensure draw with voxels over draw with mask
            o.draw(imtest, label++);
        }
        ImageByte imtest2 = new ImageByte("", im);
        label = 1;
        for (Object3D o : obs) imtest2.appendBinaryMasks(label++, o.getMask());
        TestUtils.assertImage(imtest, imtest2, 0);
    }
    
    @Test
    public void testRelabelImage() {
        ImageByte im2 = im.duplicate("");
        ObjectFactory.relabelImage(im2, null);
        TestUtils.assertImage(imRelabel, im2, 0);
    }
    
    @Test 
    public void testObjectPopulation() {
        ObjectPopulation popObj = new ObjectPopulation(new ArrayList<Object3D>(Arrays.asList(new Object3D[]{o1, o3})), im);
        TestUtils.assertImage(im, (ImageByte)popObj.getLabelMap(), 0);
        popObj.relabel();
        TestUtils.assertImage(imRelabel, (ImageByte)popObj.getLabelMap(), 0);
        popObj = new ObjectPopulation(new ArrayList<Object3D>(Arrays.asList(new Object3D[]{o1, o3})), im);
        popObj.relabel();
        TestUtils.assertImage(imRelabel, (ImageByte)popObj.getLabelMap(), 0);
        
        ObjectPopulation popIm = new ObjectPopulation(im, true);
        assertEquals("number of objects", 2, popIm.getObjects().size());
        assertObject3DVoxels(o1, popIm.getObjects().get(0));
        assertObject3DVoxels(o3, popIm.getObjects().get(1));
    }
    
    public static void assertObject3DVoxels(Object3D expected, Object3D actual) {
        assertEquals("object voxel number", expected.getVoxels().size(), actual.getVoxels().size());
        for (int i = 0; i<expected.getVoxels().size(); ++i) {
            TestUtils.logger.trace("assert voxel: {} expected: {} actual: {}", i, expected.getVoxels().get(i), actual.getVoxels().get(i));
            assertTrue("voxel: "+i, expected.getVoxels().get(i).equals(actual.getVoxels().get(i)));
        }
    }
    
    
}
