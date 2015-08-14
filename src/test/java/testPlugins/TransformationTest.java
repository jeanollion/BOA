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
import image.ImageByte;
import images.ImageIOTest;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import processing.ImageTransformation;

/**
 *
 * @author nasique
 */
public class TransformationTest {
    @Test
    public void filpTest() {
        ImageByte test = new ImageByte("", 5, 4, 3);
        test.setPixel(0, 0, 0, 1);
        test.setPixel(1, 1, 1, 1);
        ImageByte test2=test.duplicate("");
        ImageTransformation.filp(test2, ImageTransformation.Axis.X);
        assertEquals("filp-X", 1, test2.getPixelInt(test.getSizeX()-1, 0, 0));
        ImageTransformation.filp(test2, ImageTransformation.Axis.X);
        Utils.assertImageByte(test, test2);
        
        ImageTransformation.filp(test2, ImageTransformation.Axis.Y);
        assertEquals("filp-Y", 1, test2.getPixelInt(0, test.getSizeY()-1, 0));
        ImageTransformation.filp(test2, ImageTransformation.Axis.Y);
        ImageTransformation.filp(test2, ImageTransformation.Axis.Z);
        assertEquals("filp-Z", 1, test2.getPixelInt(0, 0, test.getSizeZ()-1));
    }
}
