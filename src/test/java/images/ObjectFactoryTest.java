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
import image.ImageByte;
import image.ObjectFactory;
import static org.junit.Assert.fail;
import org.junit.Test;

/**
 *
 * @author jollion
 */
public class ObjectFactoryTest {
    @Test
    public void testGetObjectsVoxels() {
        ImageByte im = new ImageByte("", 3, 4, 5);
        im.setPixel(0, 0, 0, 1);
        im.setPixel(1, 0, 0, 1);
        
        im.setPixel(2, 1, 1, 3);
        im.setPixel(3, 1, 1, 3);
        
        Object3D[] obs = ObjectFactory.getObjectsVoxels(im, false);
        
        
        
        fail("");
    }
    
    @Test
    public void testGetObjectsBounds() {
        fail("");
    }
    
    @Test
    public void testGetObjectsImages() {
        fail("");
    }
    
    @Test
    public void testRelabelImage() {
        fail("");
    }
}
