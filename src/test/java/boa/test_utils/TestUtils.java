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
package boa.test_utils;

import boa.data_structure.ProcessingTest;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import boa.image.BlankMask;
import boa.image.IJImageWrapper;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageFloat;
import boa.image.ImageShort;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author nasique
 */
public class TestUtils {
    public static final Logger logger = LoggerFactory.getLogger(ProcessingTest.class);
    public static void showImageIJ(Image image) {
        if (IJ.getInstance()==null) new ImageJ();
        ImagePlus ip = IJImageWrapper.getImagePlus(image);
        double[] minAndMax = image.getMinAndMax(null);
        ip.setDisplayRange(minAndMax[0], minAndMax[1]);
        ip.show();
    }
    public static <T extends Image> void assertImage(T expected, T actual, float precision) {
        assertImage("", expected, actual, precision);
    }
    public static <T extends Image> void assertImage(String message, T expected, T actual, float precision) {
        assertEquals(message+" image comparison: sizeZ", expected.getSizeZ(), actual.getSizeZ());
        if (expected instanceof ImageByte) assertImageByte(message, (ImageByte)expected, (ImageByte)actual);
        else if (expected instanceof ImageShort) assertImageShort(message, (ImageShort)expected, (ImageShort)actual);
        else if (expected instanceof ImageFloat) assertImageFloat(message, (ImageFloat)expected, (ImageFloat)actual, precision);
        else fail("wrong image type");
    }
    
    private static void assertImageByte(String message, ImageByte expected, ImageByte actual) {
        for (int z = 0; z < expected.getSizeZ(); z++) {
            assertArrayEquals(message+" image comparison " + expected.getName() + " plane: " + z, expected.getPixelArray()[z], actual.getPixelArray()[z]);
        }
    }
    private static void assertImageShort(String message, ImageShort expected, ImageShort actual) {
        for (int z = 0; z < expected.getSizeZ(); z++) {
            assertArrayEquals(message+" image comparison " + expected.getName() + " plane: " + z, expected.getPixelArray()[z], actual.getPixelArray()[z]);
        }
    }
    private static void assertImageFloat(String message, ImageFloat expected, ImageFloat actual, float precision) {
        for (int z = 0; z < expected.getSizeZ(); z++) {
            assertArrayEquals(message+" image comparison " + expected.getName() + " plane: " + z, expected.getPixelArray()[z], actual.getPixelArray()[z], precision);
        }
    }
    
    public static <T extends Image> T generateRandomImage(int sizeX, int sizeY, int sizeZ, T outputType) {
        T res = Image.createEmptyImage("", outputType, new BlankMask("", sizeX, sizeY, sizeZ));
        int maxValue=res instanceof ImageByte? 255: 65535;
        for (int z = 0; z < sizeZ; ++z) {
            for (int xy = 0; xy < sizeY*sizeX; ++xy) {
                    res.setPixel(xy, z, xy*(z+1)%maxValue);
            }
        }
        return res;
    }
    
}
