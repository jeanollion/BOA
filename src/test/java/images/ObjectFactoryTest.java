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

import dataStructure.objects.Object3D;
import dataStructure.objects.Voxel3D;
import image.BoundingBox;
import image.ImageByte;
import image.ObjectFactory;
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
public class ObjectFactoryTest {
    ImageByte im;
    BoundingBox bound1, bound3;
    Object3D o1, o3;
    @Before
    public void setUp() {
        im = new ImageByte("", 4, 4, 5);
        im.setPixel(0, 0, 0, 1);
        im.setPixel(1, 0, 0, 1);
        im.setPixel(2, 1, 1, 3);
        im.setPixel(3, 1, 1, 3);
        
        
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
        assertTrue("object1 vox1:", new Voxel3D(0, 0, 0).equals(obs[0].getVoxels().get(0)));
        assertTrue("object1 vox2:", new Voxel3D(1, 0, 0).equals(obs[0].getVoxels().get(1)));
        assertEquals("object3 vox number:", 2, obs[1].getVoxels().size());
        assertTrue("object3 vox1:", new Voxel3D(2, 1, 1).equals(obs[1].getVoxels().get(0)));
        System.out.println("voxel 1 o3:"+obs[1].getVoxels().get(1));
        assertTrue("object3 vox2:", new Voxel3D(3, 1, 1).equals(obs[1].getVoxels().get(1)));
        for (int i = 0; i<obs[0].getVoxels().size(); ++i) assertTrue("o1 vox:"+i, obs[0].getVoxels().get(i).equals(o1.getVoxels().get(i)));
        for (int i = 0; i<obs[1].getVoxels().size(); ++i) assertTrue("o3 vox:"+i, obs[1].getVoxels().get(i).equals(o3.getVoxels().get(i)));
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
        ImageIOTest.assertImageByte((ImageByte)obs[0].getMask(), (ImageByte)o1.getMask());
        ImageIOTest.assertImageByte((ImageByte)obs[1].getMask(), (ImageByte)o3.getMask());
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
        ImageIOTest.assertImageByte(imtest, imtest2);
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
        ImageIOTest.assertImageByte(imtest, imtest2);
    }
    
    @Test
    public void testRelabelImage() {
        ImageByte imRelabel = new ImageByte("", 4, 4, 5);
        imRelabel.setPixel(0, 0, 0, 1);
        imRelabel.setPixel(1, 0, 0, 1);
        imRelabel.setPixel(2, 1, 1, 2);
        imRelabel.setPixel(3, 1, 1, 2);
        
        ImageByte im2 = im.duplicate("");
        ObjectFactory.relabelImage(im2, null);
        ImageIOTest.assertImageByte(imRelabel, im2);
    }
}
