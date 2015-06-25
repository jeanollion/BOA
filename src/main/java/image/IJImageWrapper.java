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

import ij.ImagePlus;
import ij.ImageStack;

/**
 *
 * @author jollion
 */
public class IJImageWrapper {
    
    public static Image wrap(ImagePlus img) {
        switch(img.getBitDepth()) {
            case 8:
                byte[][] pix8 = new byte[img.getNSlices()][];
                if (img.getImageStack() != null) {
                    for (int i = 0; i < pix8.length; ++i) {
                        pix8[i] = (byte[]) img.getImageStack().getPixels(i + 1);
                    }
                } else {
                    pix8[0] = (byte[]) img.getProcessor().getPixels();
                }
                return new ImageByte(img.getTitle(), img.getWidth(), pix8);
            case 16:
                short[][] pix16 = new short[img.getNSlices()][];
                if (img.getImageStack() != null) {
                    for (int i = 0; i < pix16.length; ++i) {
                        pix16[i] = (short[]) img.getImageStack().getPixels(i + 1);
                    }
                } else {
                    pix16[0] = (short[]) img.getProcessor().getPixels();
                }
                return new ImageShort(img.getTitle(), img.getWidth(), pix16);
            case 32:
                float[][] pix32 = new float[img.getNSlices()][];
                if (img.getImageStack() != null) {
                    for (int i = 0; i < pix32.length; ++i) {
                        pix32[i] = (float[]) img.getImageStack().getPixels(i + 1);
                    }
                } else {
                    pix32[0] = (float[]) img.getProcessor().getPixels();
                }
                return new ImageFloat(img.getTitle(), img.getWidth(), pix32);
            default:
                throw new IllegalArgumentException("Image should be of thype byte, short or float");
        }
    }
    /**
     * Generate ImageJ's ImagePlus object from {@param image}, if the type is byte, short or float, the pixel array is backed in the ImagePlus object, if not a conversion occurs and there is no more link between pixels arrays in ImagePlus object and Image object.
     * @param image input image
     * @return ImageJ's ImagePlus object, containing and ImageStack 
     */
    public static ImagePlus getImagePlus(Image image) {
        ImageStack st = new ImageStack(image.getSizeX(), image.getSizeY(), image.getSizeZ());
        if (image instanceof ImageByte) {
            byte[][] pixels = ((ImageByte)image).getPixelArray();
            for (int z = 0; z < image.getSizeZ(); ++z) {
                st.setPixels(pixels[z], z + 1);
            }
        } else if (image instanceof ImageShort) {
            short[][] pixels = ((ImageShort)image).getPixelArray();
            for (int z = 0; z < image.getSizeZ(); ++z) {
                st.setPixels(pixels[z], z + 1);
            }
        } else if (image instanceof ImageFloat) {
            float[][] pixels = ((ImageFloat)image).getPixelArray();
            for (int z = 0; z < image.getSizeZ(); ++z) {
                st.setPixels(pixels[z], z + 1);
            }
        } else if (image instanceof ImageInt) {
            return getImagePlus(TypeConverter.toFloat(image));
        } else if (image instanceof ImageMask) {
            return getImagePlus(TypeConverter.toByte((ImageMask)image));
        }
        return new ImagePlus(image.getName(), st);
    }
}
