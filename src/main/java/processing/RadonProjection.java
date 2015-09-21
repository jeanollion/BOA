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

import image.Image;
import image.ImageFloat;
import processing.neighborhood.EllipsoidalNeighborhood;

/**
 *
 * @author jollion
 */
public class RadonProjection {

    public static void radonProject(Image image, int z, double angle, float[] proj) {

        boolean fast = false;

        double val;
        int x, y;
        double sintab = Math.sin((double) angle * Math.PI / 180 - Math.PI / 2);
        double costab = Math.cos((double) angle * Math.PI / 180 - Math.PI / 2);

        int projIdx = 0;

        // Project each pixel in the image
        int Xcenter = image.getSizeX() / 2;
        int Ycenter = image.getSizeY() / 2;
        
        //if no. scans is greater than the image width, then scale will be <1
        double scale = image.getSizeX() * 1.42 / proj.length; 

        int N = 0;
        val = 0;
        double weight = 0;
        double sang = Math.sqrt(2) / 2;

        double a = -costab / sintab;
        double aa = 1 / a;
        if (Math.abs(sintab) > sang) {
            for (projIdx = 0; projIdx < proj.length; projIdx++) {
                N = projIdx - proj.length / 2; //System.out.print("N="+N+" ");
                double b = (N - costab - sintab) / sintab;
                b *= scale;
                for (x = -Xcenter; x < Xcenter; x++) {
                    if (fast) {
                        //just use nearest neighbour interpolation
                        y = (int) Math.round(a * x + b);
                        if (y >= -Xcenter && y < Xcenter) {
                            val += image.getPixel(x+Xcenter, y + Ycenter, z);
                        }
                    } else {
                        //linear interpolation
                        y = (int) Math.round(a * x + b);
                        weight = Math.abs((a * x + b) - Math.ceil(a * x + b));

                        if (y >= -Xcenter && y + 1 < Xcenter) {
                            val += (1 - weight) * image.getPixel(x+Xcenter, y + Ycenter, z)
                                    + weight * image.getPixel(x+Xcenter, y + Ycenter + 1, z);
                        }

                    }
                }
                proj[projIdx] = (float) (val / Math.abs(sintab));
                val = 0;

            }
        }
        if (Math.abs(sintab) <= sang) {
            for (projIdx = 0; projIdx < proj.length; projIdx++) {
                N = projIdx - proj.length / 2;
                double bb = (N - costab - sintab) / costab;
                bb = bb * scale;
                //IJ.write("bb="+bb+" ");
                for (y = -Ycenter; y < Ycenter; y++) {
                    if (fast == true) {
                        x = (int) Math.round(aa * y + bb);
                        if (x >= -Xcenter && x < Xcenter) {
                            val += image.getPixel(x+Xcenter, y + Ycenter, z);
                        }
                    } else {
                        x = (int) Math.round(aa * y + bb);
                        weight = Math.abs((aa * y + bb) - Math.ceil(aa * y + bb));

                        if (x >= -Xcenter && x + 1 < Xcenter) {
                            val += (1 - weight) * image.getPixel(x+Xcenter, y + Ycenter, z)
                                    + weight * image.getPixel(x+Xcenter + 1, y + Ycenter, z);
                        }

                    }
                }
                proj[projIdx] = (float) (val / Math.abs(costab));
                val = 0;

            }

        }
    }
    
    public static ImageFloat getSinogram(Image image, double angleMin, double angleMax, double stepSize, int projSize) {
        double[] angles = getAngleArray(angleMin, angleMax, stepSize);
        ImageFloat res = new ImageFloat("sinogram", angles.length, projSize, 1);
        float[] proj = new float[projSize];
        for (int i = 0; i<angles.length; ++i) {
            radonProject(image, 0, angles[i], proj);
            for (int j = 0; j<projSize; ++j) res.setPixel(i, j, 0, proj[j]);
        }
        return res;
    }
    
    
    
    public static double[] getAngleArray(double ang1, double ang2, double stepsize) {
        if (ang1>ang2) {double temp = ang1; ang1=ang2; ang2=temp;}
        double[] angles = new double [(int)(Math.abs(ang2-ang1) / stepsize + 0.5d)]; 
        angles[0]=ang1;
        for (int i=1; i<angles.length; ++i) angles[i]=(angles[i-1]+stepsize)%360;
        return angles;
    }
    
    
    
    
    
    private static void paste(float[] source, ImageFloat dest, int x) {
        float[] pixDest = dest.getPixelArray()[0];
        for (int i = 0; i<source.length; ++i) pixDest[x + i*dest.getSizeX()] = source[i];
    }
    
    private static void filter(double scale, float[] data) {
        ImageFloat im = new ImageFloat("", data.length, new float[][]{data});
        im = Filters.median(im, im, new EllipsoidalNeighborhood(3, false));
        im = Filters.tophat(im, im, new EllipsoidalNeighborhood(scale, false));
        float[] data2 = im.getPixelArray()[0];
        for (int i = 0; i<data.length; ++i) data[i] = data2[i];
    }
    
    
}
