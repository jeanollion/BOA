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
    
    /**
     * 
     * @param image input image to be converted
     * @return a new ImageFloat values casted as float
     */
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
    
    /**
     * 
     * @param image input image to be converted
     * @return a mask represented as an ImageByte, each non-zero voxel of {@param image} has a value of 1
     */
    public static ImageByte toByteMask(ImageMask image) {
        ImageByte res = new ImageByte(image.getName(), image);
        byte[][] newPixels = res.getPixelArray();
        for (int z = 0; z<image.getSizeZ(); ++z) {
            for (int xy = 0; xy<image.getSizeXY(); ++xy) {
                if (image.insideMask(xy, z)) newPixels[z][xy] = 1;
            }
        }
        return res;
    }
    /**
     * 
     * @param image input image
     * @return an image of type ImageByte, ImageShort or ImageFloat. If {@param image} is of type ImageByte, ImageShort or ImageFloat {@Return}={@param image}. If {@param image} is of type ImageInt, it is cast as a FloatImage {@link TypeConverter#toFloat(image.Image) }. If {@param image} is a mask if will be converted to a mask: {@link TypeConverter#toByteMask(image.ImageMask) }
     */
    public static Image toCommonImageType(Image image) {
        if (image instanceof ImageByte || image instanceof ImageShort || image instanceof ImageFloat) return image;
        else if (image instanceof ImageInt) return toFloat(image);
        else if (image instanceof ImageMask) return toByteMask((ImageMask)image);
        else return toFloat(image);
    }
}
