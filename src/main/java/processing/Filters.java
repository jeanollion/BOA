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

import dataStructure.objects.Voxel3D;
import image.Image;
import image.ImageFloat;
import java.util.Arrays;
import processing.neighborhood.EllipsoidalNeighborhood;
import processing.neighborhood.Neighborhood;

/**
 *
 * @author jollion
 */
public class Filters {
    public static <T extends Image> T mean(Image image, T outputType, Neighborhood neighborhood) {
        return applyFilter(image, outputType, new Mean(), neighborhood);
    }
    
    public static <T extends Image> T median(Image image, T outputType, Neighborhood neighborhood) {
        return applyFilter(image, outputType, new Median(), neighborhood);
    }
    
    public static <T extends Image> T max(Image image, T outputType, Neighborhood neighborhood) {
        return applyFilter(image, outputType, new Max(), neighborhood);
    }
    
    public static <T extends Image> T min(Image image, T outputType, Neighborhood neighborhood) {
        return applyFilter(image, outputType, new Min(), neighborhood);
    }
    
    public static <T extends Image> T open(Image image, T outputType, Neighborhood neighborhood) {
        ImageFloat min = applyFilter(image, new ImageFloat("", 0, 0, 0), new Min(), neighborhood);
        return applyFilter(min, outputType, new Max(), neighborhood);
    }
    
    public static <T extends Image> T close(Image image, T outputType, Neighborhood neighborhood) {
        ImageFloat max = applyFilter(image, new ImageFloat("", 0, 0, 0), new Max(), neighborhood);
        return applyFilter(max, outputType, new Min(), neighborhood);
    }
    
    public static <T extends Image> T tophat(Image image, T outputType, Neighborhood neighborhood) {
        ImageFloat open =open(image, new ImageFloat("", 0, 0, 0), neighborhood);
        T res = Image.createEmptyImage("Tophat of: "+image.getName(), outputType, image);
        float round=image instanceof ImageFloat ? 0: 0.5f;
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
    
    public static <T extends Image> T tophatInv(Image image, T outputType, Neighborhood neighborhood) {
        ImageFloat close =close(image, new ImageFloat("", 0, 0, 0), neighborhood);
        T res = Image.createEmptyImage("Tophat of: "+image.getName(), outputType, image);
        float round=image instanceof ImageFloat ? 0: 0.5f;
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
    
    protected static <T extends Image, F extends Filter> T applyFilter(Image image, T outType, F filter, Neighborhood neighborhood) {
        T res = Image.createEmptyImage(filter.getClass().getSimpleName()+" of: "+image.getName(), outType, image);
        float round=image instanceof ImageFloat ? 0: 0.5f;
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
            for (int i = 1; i<valueNumber; ++i) if (values[i]>max) values[i]=max;
            return max;
        }
    }
    private static class Min implements Filter {
        @Override public float applyFilter(float[] values, int valueNumber, Neighborhood neighborhood) {
            if (valueNumber==0) return 0;
            float min = values[0];
            for (int i = 1; i<valueNumber; ++i) if (values[i]<min) values[i]=min;
            return min;
        }
    }
}
