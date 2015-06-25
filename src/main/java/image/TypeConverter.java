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
package image;

/**
 *
 * @author jollion
 */
public class TypeConverter {
    public static ImageFloat toFloat(Image image) {
        ImageFloat res = new ImageFloat(image.getName(), image);
        float[][] newPixels = res.getPixelArray();
        for (int z = 0; z<image.getSizeZ(); ++z) {
            for (int xy = 0; xy<image.getSizeXY(); ++xy) {
                newPixels[z][xy]=image.getPixel(xy, z);
            }
        }
        return res;
    }
    public static ImageByte toByte(ImageMask image) {
        ImageByte res = new ImageByte(image.getName(), image);
        byte[][] newPixels = res.getPixelArray();
        for (int z = 0; z<image.getSizeZ(); ++z) {
            for (int xy = 0; xy<image.getSizeXY(); ++xy) {
                if (image.insideMask(xy, z)) newPixels[z][xy] = 1;
            }
        }
        return res;
    }
    
    public static Image toCommonImageType(Image image) {
        if (image instanceof ImageByte || image instanceof ImageShort || image instanceof ImageFloat) return image;
        else if (image instanceof ImageInt) return toFloat(image);
        else if (image instanceof ImageMask) return toByte((ImageMask)image);
        else return toFloat(image);
    }
}
