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
import ij.measure.Calibration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 *
 * @author jollion
 */
public class IJImageWrapper {
    
    public static Image wrap(ImagePlus img) {
        int slices = img.getNSlices() * img.getNFrames() * img.getNChannels();
        switch(img.getBitDepth()) {
            case 8:
                byte[][] pix8 = new byte[slices][];
                if (img.getImageStack() != null) {
                    for (int i = 0; i < pix8.length; ++i) {
                        pix8[i] = (byte[]) img.getImageStack().getPixels(i + 1);
                    }
                } else {
                    pix8[0] = (byte[]) img.getProcessor().getPixels();
                }
                return new ImageByte(img.getTitle(), img.getWidth(), pix8);
            case 16:
                short[][] pix16 = new short[slices][];
                if (img.getImageStack() != null) {
                    for (int i = 0; i < pix16.length; ++i) {
                        pix16[i] = (short[]) img.getImageStack().getPixels(i + 1);
                    }
                } else {
                    pix16[0] = (short[]) img.getProcessor().getPixels();
                }
                return new ImageShort(img.getTitle(), img.getWidth(), pix16);
            case 32:
                float[][] pix32 = new float[slices][];
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
            return getImagePlus(TypeConverter.toFloat(image, null));
        } else if (image instanceof ImageMask) {
            return getImagePlus(TypeConverter.toByteMask((ImageMask)image, null, 255));
        }
        ImagePlus ip= new ImagePlus(image.getName(), st);
        Calibration cal = new Calibration();
        if (image.getScaleXY()!=0) {
            cal.pixelWidth=image.getScaleXY();
            cal.pixelHeight=image.getScaleXY();
            if (image.getScaleZ()!=0) cal.pixelDepth=image.getScaleZ();
            cal.setUnit("um");
        }
        //cal.xOrigin=image.getOffsetX();
        //cal.yOrigin=image.getOffsetY();
        //cal.zOrigin=image.getOffsetZ();
        ip.setCalibration(cal);
        return ip;
    }
    /**
     * 
     * @param channel 0-based index
     * @param slice
     * @param frame
     * @param FCZCount
     * @return 
     */
    public static int getStackIndex(int channel, int slice, int frame, int[] FCZCount) {	
        if (channel<0) channel = 0;
    	if (channel>=FCZCount[1]) channel = FCZCount[1]-1;
    	if (slice<0) slice = 0;
    	if (slice>=FCZCount[2]) slice = FCZCount[2]-1;
    	if (frame<0) frame = 0;
    	if (frame>=FCZCount[0]) frame = FCZCount[0]-1;
        return frame*FCZCount[1]*FCZCount[2] + slice*FCZCount[1] + channel;
    }
    public static int[] convertIndex(int n, int[] FCZCount) {
        int[] fcz = new int[3];
        fcz[1] = ((n-1)%FCZCount[1]);
        fcz[2] = (((n-1)/FCZCount[1])%FCZCount[2]);
        fcz[0] = (((n-1)/(FCZCount[1]*FCZCount[2]))%FCZCount[0]);
        return fcz;
    }
    public static Function<int[], Integer> getStackIndexFunction(final int[] FCZCount) {
        return (idx) -> getStackIndex(idx[1], idx[2], idx[0], FCZCount);
    }
    public static Function<Integer, int[]> getStackIndexFunctionRev(final int[] FCZCount) {
        return idx -> convertIndex(idx, FCZCount);
    }
}
