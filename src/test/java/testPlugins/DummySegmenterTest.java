/*
 * Copyright (C) 2015 nasique
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
package testPlugins;

import TestUtils.Utils;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import image.ImageByte;
import image.ImageFormat;
import image.ImageInteger;
import image.ImageLabeller;
import image.ImageWriter;
import images.ImageIOTest;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import testPlugins.dummyPlugins.DummySegmenter;

/**
 *
 * @author nasique
 */
public class DummySegmenterTest {
    @Test
    public void testDummySegmenter() {
        DummySegmenter s = new DummySegmenter(true, 2);
        ImageByte in = new ImageByte("", 50, 50, 2);
        ObjectPopulation pop = s.runSegmenter(in, 0, null);
        assertEquals("number of objects", 2, pop.getObjects().size());
        ImageInteger image = pop.getLabelImage();
        Object3D[] obs = ImageLabeller.labelImage(image);
        assertEquals("number of objects from image", 2, obs.length);
        
        // reconstruction de l'image
        ImageInteger res2 = ImageInteger.mergeBinary(in, obs[0].getMask(), obs[1].getMask());
        Utils.assertImage((ImageByte)res2, (ImageByte)image, 0);
        
    }
}
