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
package TestUtils;

import dataStructure.ProcessingTest;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import image.IJImageWrapper;
import image.Image;
import image.ImageByte;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author nasique
 */
public class Utils {
    public static final Logger logger = LoggerFactory.getLogger(ProcessingTest.class);
    public static void showImageIJ(Image image) {
        if (IJ.getInstance()==null) new ImageJ();
        ImagePlus ip = IJImageWrapper.getImagePlus(image);
        float[] minAndMax = image.getMinAndMax(null);
        ip.setDisplayRange(minAndMax[0], minAndMax[1]);
        ip.show();
    }

    public static void assertImageByte(ImageByte expected, ImageByte actual) {
        assertEquals("image comparison: sizeZ", expected.getSizeZ(), actual.getSizeZ());
        for (int z = 0; z < expected.getSizeZ(); z++) {
            assertArrayEquals("image comparison " + expected.getName() + " plane: " + z, expected.getPixelArray()[z], actual.getPixelArray()[z]);
        }
    }
}
