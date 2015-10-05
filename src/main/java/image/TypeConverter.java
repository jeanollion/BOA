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
     * @param output image to cast values to. if null, a new image will be created
     * @return a new ImageFloat values casted as float
     */
    public static ImageFloat toFloat(Image image, ImageFloat output) {
        if (output==null || !output.sameSize(image)) output = new ImageFloat(image.getName(), image);
        float[][] newPixels = output.getPixelArray();
        for (int z = 0; z<image.getSizeZ(); ++z) {
            for (int xy = 0; xy<image.getSizeXY(); ++xy) {
                newPixels[z][xy]=image.getPixel(xy, z);
            }
        }
        return output;
    }
    
    /**
     * 
     * @param image input image to be converted
     * @param output image to cast values to. if null, a new image will be created
     * @return a new ImageShort values casted as short (if values exceed 65535 they will be equal to 65535) 
     */
    public static ImageShort toShort(Image image, ImageShort output) {
        if (output==null || !output.sameSize(image)) output = new ImageShort(image.getName(), image);
        for (int z = 0; z<image.getSizeZ(); ++z) {
            for (int xy = 0; xy<image.getSizeXY(); ++xy) {
                output.setPixel(xy, z, image.getPixel(xy, z)+0.5);
            }
        }
        return output;
    }
    
    /**
     * 
     * @param image input image to be converted
     * @param output image to cast values to. if null, a new image will be created
     * @return a new ImageShort values casted as short (if values exceed 65535 they will be equal to 65535) 
     */
    public static ImageByte toByte(Image image, ImageByte output) {
        if (output==null || !output.sameSize(image)) output = new ImageByte(image.getName(), image);
        for (int z = 0; z<image.getSizeZ(); ++z) {
            for (int xy = 0; xy<image.getSizeXY(); ++xy) {
                output.setPixel(xy, z, image.getPixel(xy, z)+0.5);
            }
        }
        return output;
    }
    
    
    /**
     * 
     * @param image input image to be converted
     * @param output image to cast values to. if null, a new image will be created
     * @return a mask represented as an ImageByte, each non-zero voxel of {@param image} has a value of 1
     */
    public static ImageByte toByteMask(ImageMask image, ImageByte output) {
        if (output==null || !output.sameSize(image)) output = new ImageByte(image.getName(), image);
        byte[][] newPixels = output.getPixelArray();
        for (int z = 0; z<image.getSizeZ(); ++z) {
            for (int xy = 0; xy<image.getSizeXY(); ++xy) {
                if (image.insideMask(xy, z)) newPixels[z][xy] = 1;
            }
        }
        return output;
    }
    /**
     * 
     * @param image input image
     * @return an image of type ImageByte, ImageShort or ImageFloat. If {@param image} is of type ImageByte, ImageShort or ImageFloat {@Return}={@param image}. If {@param image} is of type ImageInt, it is cast as a ShortImage {@link TypeConverter#toShort(image.Image) } if its maximum value is inferior to 65535 or a FloatImage {@link TypeConverter#toFloat(image.Image) }. If {@param image} is a mask if will be converted to a mask: {@link TypeConverter#toByteMask(image.ImageMask) }
     */
    public static Image toCommonImageType(Image image) {
        if (image instanceof ImageByte || image instanceof ImageShort || image instanceof ImageFloat) return image;
        else if (image instanceof ImageInt) {
            float[] mm = image.getMinAndMax(null);
            if (mm[1]>(65535)) return toFloat(image, null);
            else return toShort(image, null);
        }
        else if (image instanceof ImageMask) return toByteMask((ImageMask)image, null);
        else return toFloat(image, null);
    }
    
    public static <T extends Image> T cast(Image source, T output) {
        if (output instanceof ImageByte) {
            if (source instanceof ImageByte) return (T)source;
            return (T)toByte(source, (ImageByte)output);
        } else if (output instanceof ImageShort) {
            if (source instanceof ImageShort) return (T)source;
            return (T)toShort(source, (ImageShort)output);
        } else if (output instanceof ImageFloat) {
            if (source instanceof ImageFloat) return (T)source;
            return (T)toFloat(source, (ImageFloat)output);
        } else throw new IllegalArgumentException("Output should be of type byte, short, or float, but is: {}"+ output.getClass().getSimpleName());
    }
}
