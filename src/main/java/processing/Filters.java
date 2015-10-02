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
package processing;

import dataStructure.objects.Voxel;
import image.Image;
import image.ImageByte;
import image.ImageFloat;
import java.util.Arrays;
import processing.neighborhood.EllipsoidalNeighborhood;
import processing.neighborhood.Neighborhood;

/**
 *
 * @author jollion
 */
public class Filters {
    public static Neighborhood getNeighborhood(double scaleXY, double scaleZ, Image image) {return image.getSizeZ()>1 ? new EllipsoidalNeighborhood(scaleXY, scaleZ, false) : new EllipsoidalNeighborhood(scaleXY, false);}
    
    
    public static <T extends Image> T mean(Image image, T output, Neighborhood neighborhood) {
        return applyFilter(image, output, new Mean(), neighborhood);
    }
    
    public static <T extends Image> T median(Image image, T output, Neighborhood neighborhood) {
        return applyFilter(image, output, new Median(), neighborhood);
    }
    
    public static <T extends Image> T max(Image image, T output, Neighborhood neighborhood) {
        return applyFilter(image, output, new Max(), neighborhood);
    }
    
    public static <T extends Image> T min(Image image, T output, Neighborhood neighborhood) {
        return applyFilter(image, output, new Min(), neighborhood);
    }
    
    public static <T extends Image> T open(Image image, T output, Neighborhood neighborhood) {
        ImageFloat min = applyFilter(image, new ImageFloat("", 0, 0, 0), new Min(), neighborhood);
        return applyFilter(min, output, new Max(), neighborhood);
    }
    
    public static <T extends Image> T close(Image image, T output, Neighborhood neighborhood) {
        ImageFloat max = applyFilter(image, new ImageFloat("", 0, 0, 0), new Max(), neighborhood);
        return applyFilter(max, output, new Min(), neighborhood);
    }
    
    public static <T extends Image> T tophat(Image image, T output, Neighborhood neighborhood) {
        ImageFloat open =open(image, new ImageFloat("", 0, 0, 0), neighborhood);
        T res = image.sameSize(output)?output:Image.createEmptyImage("Tophat of: "+image.getName(), output, image);
        float round=output instanceof ImageFloat ? 0: 0.5f;
        int sizeX=image.getSizeX();
        float[][] openPix = open.getPixelArray();
        //1-open
        for (int z = 0; z < image.getSizeZ(); ++z) { 
            for (int y = 0; y < image.getSizeY(); ++y) {
                for (int x = 0; x < sizeX; ++x) {
                    res.setPixel(x, y, z, image.getPixel(x, y, z)-openPix[z][x+y*sizeX]+round);
                }
            }
        }
        return res;
    }
    
    public static <T extends Image> T tophatInv(Image image, T output, Neighborhood neighborhood) {
        ImageFloat close =close(image, new ImageFloat("", 0, 0, 0), neighborhood);
        T res = image.sameSize(output)?output:Image.createEmptyImage("Tophat of: "+image.getName(), output, image);
        float round=output instanceof ImageFloat ? 0: 0.5f;
        int sizeX=image.getSizeX();
        float[][] closePix = close.getPixelArray();
        //1-open
        for (int z = 0; z < image.getSizeZ(); ++z) { 
            for (int y = 0; y < image.getSizeY(); ++y) {
                for (int x = 0; x < sizeX; ++x) {
                    res.setPixel(x, y, z, image.getPixel(x, y, z)-closePix[z][x+y*sizeX]+round);
                }
            }
        }
        return res;
    }
    /**
     * ATTENTION: bug en dimension 1 !!
     * @param image input image
     * @param output image to store the result in. if {@param output} is null or {@param output}=={@param image} or {@param output} does not have the same dimensions as {@param image}, a new image of the type of {@param output} will be created
     * @param maxLocal if true the local maximum transform of the image is return, if false the local minimum of the image is returned
     * @param neighborhood 2D/3D neighborhood in which the local extrema is computed
     * @return an image of same type as output (that can be output). Each pixel value is 0 if the current pixel is not an extrema, or has the value of the original image if it is an extrema
     */
    public static ImageByte localExtrema(Image image, ImageByte output, boolean maxLocal, Neighborhood neighborhood) {
        ImageByte res;
        String name = maxLocal?"MaxLocal of: "+image.getName():"MinLocal of: "+image.getName();
        if (output==null || !output.sameSize(image) || output==image) res = new ImageByte(name, image);
        else res = (ImageByte)output.setName(name);
        Filter filter = maxLocal?new Max():new Min();
        for (int z = 0; z < image.getSizeZ(); ++z) {
            for (int y = 0; y < image.getSizeY(); ++y) {
                for (int x = 0; x < image.getSizeX(); ++x) {
                    neighborhood.setPixels(x, y, z, image);
                    float value = image.getPixel(x, y, z);
                    res.setPixel(x, y, z, value==filter.applyFilter(neighborhood.getPixelValues(), neighborhood.getValueNumber(), neighborhood)?1:0); //+round
                }
            }
        }
        return res;
    }
    
    protected static <T extends Image, F extends Filter> T applyFilter(Image image, T output, F filter, Neighborhood neighborhood) {
        T res;
        String name = filter.getClass().getSimpleName()+" of: "+image.getName();
        if (!output.sameSize(image) || output==image) res = Image.createEmptyImage(name, output, image);
        else res = (T)output.setName(name);
        float round=output instanceof ImageFloat ? 0: 0.5f;
        for (int z = 0; z < image.getSizeZ(); ++z) {
            for (int y = 0; y < image.getSizeY(); ++y) {
                for (int x = 0; x < image.getSizeX(); ++x) {
                    neighborhood.setPixels(x, y, z, image);
                    res.setPixel(x, y, z, filter.applyFilter(neighborhood.getPixelValues(), neighborhood.getValueNumber(), neighborhood)+round); //+round
                }
            }
        }
        return res;
    }
    
    private static interface Filter {
        public float applyFilter(float[] values, int valueNumber, Neighborhood neighborhood);
    }
    private static class Mean implements Filter {
        @Override public float applyFilter(float[] values, int valueNumber, Neighborhood neighborhood) {
            if (valueNumber==0) return 0;
            double mean = 0;
            for (int i = 0; i<valueNumber; ++i) mean+=values[i];
            return (float)(mean/valueNumber);
        }
    }
    private static class Median implements Filter {
        @Override public float applyFilter(float[] values, int valueNumber, Neighborhood neighborhood) {
            if (valueNumber==0) return 0;
            Arrays.sort(values, 0, valueNumber);
            if (valueNumber%2==0) return (values[valueNumber/2-1]+values[valueNumber/2])/2;
            else return values[valueNumber/2];
        }
    }
    private static class Max implements Filter {
        @Override public float applyFilter(float[] values, int valueNumber, Neighborhood neighborhood) {
            if (valueNumber==0) return 0;
            float max = values[0];
            for (int i = 1; i<valueNumber; ++i) if (values[i]>max) max=values[i];
            return max;
        }
    }
    private static class Min implements Filter {
        @Override public float applyFilter(float[] values, int valueNumber, Neighborhood neighborhood) {
            if (valueNumber==0) return 0;
            float min = values[0];
            for (int i = 1; i<valueNumber; ++i) if (values[i]<min) min=values[i];
            return min;
        }
    }
    //(low + high) >>> 1 <=> (low + high) / 2
}
