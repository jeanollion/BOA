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
import java.util.Comparator;
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
        Filter filter = maxLocal?new LocalMax():new LocalMin();
        return applyFilter(image, res, filter, neighborhood);
    }
        /**
     * ATTENTION: bug en dimension 1 !!
     * @param image input image
     * @param output image to store the result in. if {@param output} is null or {@param output}=={@param image} or {@param output} does not have the same dimensions as {@param image}, a new image of the type of {@param output} will be created
     * @param maxLocal if true the local maximum transform of the image is return, if false the local minimum of the image is returned
     * @param threshold supplemental condition: to be a local extrema, the pixel value must be superior to {@param threshold} if {@param maxLocal}==true, otherwise inferior to {@param threshold}
     * @param neighborhood 2D/3D neighborhood in which the local extrema is computed
     * @return an image of same type as output (that can be output). Each pixel value is 0 if the current pixel is not an extrema, or has the value of the original image if it is an extrema
     */
    public static ImageByte localExtrema(Image image, ImageByte output, boolean maxLocal, double threshold, Neighborhood neighborhood) {
        ImageByte res;
        String name = maxLocal?"MaxLocal of: "+image.getName():"MinLocal of: "+image.getName();
        if (output==null || !output.sameSize(image) || output==image) res = new ImageByte(name, image);
        else res = (ImageByte)output.setName(name);
        Filter filter = maxLocal?new LocalMaxThreshold(threshold):new LocalMinThreshold(threshold);
        return applyFilter(image, res, filter, neighborhood);
    }
    
    protected static <T extends Image, F extends Filter> T applyFilter(Image image, T output, F filter, Neighborhood neighborhood) {
        if (filter==null) throw new IllegalArgumentException("Apply Filter Error: Filter cannot be null");
        if (neighborhood==null) throw new IllegalArgumentException("Apply Filter ("+filter.getClass().getSimpleName()+") Error: Neighborhood cannot be null");
        T res;
        String name = filter.getClass().getSimpleName()+" of: "+image.getName();
        if (output==null) res = (T)new ImageFloat(name, image);
                else if (!output.sameSize(image) || output==image) res = Image.createEmptyImage(name, output, image);
        else res = (T)output.setName(name);
        float round=output instanceof ImageFloat ? 0: 0.5f;
        filter.setUp(image, neighborhood);
        for (int z = 0; z < image.getSizeZ(); ++z) {
            for (int y = 0; y < image.getSizeY(); ++y) {
                for (int x = 0; x < image.getSizeX(); ++x) {
                    neighborhood.setPixels(x, y, z, image);
                    res.setPixel(x, y, z, filter.applyFilter(x, y, z)+round);
                }
            }
        }
        return res;
    }
    
    private static abstract class Filter {
        Image image;
        Neighborhood neighborhood;
        public void setUp(Image image, Neighborhood neighborhood) {this.image=image; this.neighborhood=neighborhood;}
        public abstract float applyFilter(int x, int y, int z);
    }
    private static class Mean extends Filter {
        @Override public float applyFilter(int x, int y, int z) {
            if (neighborhood.getValueCount()==0) return 0;
            double mean = 0;
            for (int i = 0; i<neighborhood.getValueCount(); ++i) mean+=neighborhood.getPixelValues()[i];
            return (float)(mean/neighborhood.getValueCount());
        }
    }
    private static class Median extends Filter { // TODO: selection algorithm:  http://blog.teamleadnet.com/2012/07/quick-select-algorithm-find-kth-element.html
        @Override public float applyFilter(int x, int y, int z) {
            if (neighborhood.getValueCount()==0) return 0;
            Arrays.sort(neighborhood.getPixelValues(), 0, neighborhood.getValueCount());
            if (neighborhood.getValueCount()%2==0) return (neighborhood.getPixelValues()[neighborhood.getValueCount()/2-1]+neighborhood.getPixelValues()[neighborhood.getValueCount()/2])/2f;
            else return neighborhood.getPixelValues()[neighborhood.getValueCount()/2];
        }
    }
    /*private static class GaussianMedian extends Filter {
        double[] gaussKernel;
        double[] gaussedValues;
        Integer[] indicies;
        Comparator<Integer> comp = new Comparator<Integer>() {
            @Override public int compare(Integer arg0, Integer arg1) {
                return Double.compare(gaussedValues[arg0], gaussedValues[arg1]);
            }
        };
        @Override public void setUp(Image image, Neighborhood neighborhood) {
            super.setUp(image, neighborhood);
            gaussKernel = new double[neighborhood.getSize()];
            gaussedValues = new double[neighborhood.getSize()];
            indicies = new Integer[gaussedValues.length];
            float[] d = neighborhood.getDistancesToCenter();
            double s = neighborhood.getRadiusXY();
            double expCoeff = -1 / (2 * s* s);
            for (int i = 0; i<gaussKernel.length; ++i) gaussKernel[i] = Math.exp(d[i]*d[i] * expCoeff);
        }
        public void resetOrders() {for (int i = 0; i < indicies.length; i++) indicies[i]=i;}
        @Override public float applyFilter(int x, int y, int z) {
            if (neighborhood.getValueCount()==0) return 0;
            resetOrders();
            float[] values = neighborhood.getPixelValues();
            for (int i = 0; i<values.length; ++i) gaussedValues[i] = values[i]*gaussKernel[i];
            Arrays.sort(indicies, 0, neighborhood.getValueCount(), comp);
            if (neighborhood.getValueCount()%2==0) return (values[indicies[neighborhood.getValueCount()/2-1]]+values[indicies[neighborhood.getValueCount()/2]])/2f;
            else return values[indicies[neighborhood.getValueCount()/2]];
        }
    }*/
    private static class Max extends Filter {
        @Override public float applyFilter(int x, int y, int z) {
            if (neighborhood.getValueCount()==0) return 0;
            float max = neighborhood.getPixelValues()[0];
            for (int i = 1; i<neighborhood.getValueCount(); ++i) if (neighborhood.getPixelValues()[i]>max) max=neighborhood.getPixelValues()[i];
            return max;
        }
    }
    private static class LocalMax extends Filter {
        @Override public float applyFilter(int x, int y, int z) {
            if (neighborhood.getValueCount()==0) return 0;
            float max = neighborhood.getPixelValues()[0];
            for (int i = 1; i<neighborhood.getValueCount(); ++i) if (neighborhood.getPixelValues()[i]>max) max=neighborhood.getPixelValues()[i];
            return max==image.getPixel(x, y, z)?1:0;
        }
    }
    private static class LocalMaxThreshold extends Filter {
        double threshold;
        public LocalMaxThreshold(double threshold) {
            this.threshold=threshold;
        }
        @Override public float applyFilter(int x, int y, int z) {
            if (neighborhood.getValueCount()==0) return 0;
            float max = neighborhood.getPixelValues()[0];
            if (max<threshold) return 0;
            for (int i = 1; i<neighborhood.getValueCount(); ++i) if (neighborhood.getPixelValues()[i]>max) return 0;
            return 1;
        }
    }
    private static class LocalMin extends Filter {
        @Override public float applyFilter(int x, int y, int z) {
            if (neighborhood.getValueCount()==0) return 0;
            float min = neighborhood.getPixelValues()[0];
            for (int i = 1; i<neighborhood.getValueCount(); ++i) if (neighborhood.getPixelValues()[i]<min) return 0;
            return 1;
        }
    }
    private static class LocalMinThreshold extends Filter {
        double threshold;
        public LocalMinThreshold(double threshold) {
            this.threshold=threshold;
        }
        @Override public float applyFilter(int x, int y, int z) {
            if (neighborhood.getValueCount()==0) return 0;
            float min = neighborhood.getPixelValues()[0];
            if (min>threshold) return 0;
            for (int i = 1; i<neighborhood.getValueCount(); ++i) if (neighborhood.getPixelValues()[i]<min) return 0;
            return 1;
        }
    }
    private static class Min extends Filter {
        @Override public float applyFilter(int x, int y, int z) {
            if (neighborhood.getValueCount()==0) return 0;
            float min = neighborhood.getPixelValues()[0];
            for (int i = 1; i<neighborhood.getValueCount(); ++i) if (neighborhood.getPixelValues()[i]<min) return 0;
            return 1;
        }
    }
    //(low + high) >>> 1 <=> (low + high) / 2
}
