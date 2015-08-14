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

import ij.ImagePlus;
import ij.measure.Calibration;
import image.Image;
import image.ImageFloat;
import image.ImagescienceWrapper;
import imagescience.feature.Differentiator;
import imagescience.feature.Hessian;
import imagescience.feature.Structure;
import imagescience.image.FloatImage;
import imagescience.segment.Thresholder;
import java.util.Vector;
import utils.ThreadRunner;

/**
 *
 * @author jollion
 */
public class ImageFeatures {
    public static void hysteresis(Image image, double lowval, double highval) {
        final imagescience.image.Image is = ImagescienceWrapper.getImagescience(image);
        final Thresholder thres = new Thresholder();
        thres.hysteresis(is, lowval, highval);
    }
    
    public static ImageFloat[] structureTransform(Image image, double smoothScale, double integrationScale) {
        ImageFloat[] res = new ImageFloat[image.getScaleZ()==1?2:3];
        
        imagescience.image.Image is = ImagescienceWrapper.getImagescience(image);
        double sscale = smoothScale;
        double iscale = integrationScale;
        sscale *= image.getScaleXY();
        iscale *= image.getScaleXY();
        
        Vector vector = (new Structure()).run(is, sscale, iscale);
        for (int i=0;i<res.length;i++) res[i] = (ImageFloat)ImagescienceWrapper.wrap((imagescience.image.Image)vector.get(i));
        
        for (int i = 0; i < res.length; i++) {
            res[i].setName(image.getName() + ":structure:" + (i + 1));
            res[i].setCalibration(image);
            res[i].resetOffset().addOffset(image);
        }
        return res;
    }
    
    public static ImageFloat[] getGradient(Image image, double scale) {
        final int dims = image.getSizeZ()==1?2:3;
        final ImageFloat[] res = new ImageFloat[dims];
        final imagescience.image.Image is = ImagescienceWrapper.getImagescience(image);
        scale *= (double) image.getScaleXY(); // FIXME scaleZ?
        final Differentiator differentiator = new Differentiator();
        for (int i =0;i<dims; i++) {
            res[i] = (ImageFloat)ImagescienceWrapper.wrap(differentiator.run(is.duplicate(), scale, i==0?1:0, i==1?1:0, i==2?1:0));
            res[i].setCalibration(image);
            res[i].resetOffset().addOffset(image);
        }
        return res;
    }
    
    public static ImageFloat getGradientMagnitude(Image image, double scale) {
        ImageFloat[] grad = getGradient(image, scale);
        ImageFloat res = new ImageFloat(image.getName() + ":gradientMagnitude", image);
        final float[][] pixels = res.getPixelArray();
        if (grad.length == 3) {
            final int sizeX = image.getSizeX();
            final float[][] grad0 = grad[0].getPixelArray();
            final float[][] grad1 = grad[1].getPixelArray();
            final float[][] grad2 = grad[2].getPixelArray();
            int offY, off;
            for (int z = 0; z< image.getSizeZ(); ++z) {
                for (int y = 0; y< image.getSizeY(); ++y) {
                    offY = y * sizeX;
                    for (int x = 0; x< sizeX; ++x) {
                        off = x + offY;
                        pixels[z][off] = (float) Math.sqrt(grad0[z][off] * grad0[z][off] + grad1[z][off] * grad1[z][off] + grad2[z][off] * grad2[z][off]);
                    }
                }
            }
        } else {
            final int sizeX = image.getSizeX();
            final float[][] grad0 = grad[0].getPixelArray();
            final float[][] grad1 = grad[1].getPixelArray();
            int offY, off;
            for (int y = 0; y< image.getSizeY(); ++y) {
                offY = y * sizeX;
                for (int x = 0; x< sizeX; ++x) {
                    off = x + offY;
                    pixels[0][off] = (float) Math.sqrt(grad0[0][off] * grad0[0][off] + grad1[0][off] * grad1[0][off]);
                }
            }
        }
        
        
        /*if (grad.length == 3) {
            final ThreadRunner tr = new ThreadRunner(0, image.getSizeY(), nbCPUs);
            final int sizeZ = image.getSizeZ();
            final int sizeX = image.getSizeX();
            final float[][] grad0 = grad[0].getPixelArray();
            final float[][] grad1 = grad[1].getPixelArray();
            final float[][] grad2 = grad[2].getPixelArray();
            for (int i = 0; i < tr.threads.length; i++) {
                tr.threads[i] = new Thread(
                        new Runnable() {
                            public void run() {
                                int offY, off;
                                for (int y = tr.ai.getAndIncrement(); y < tr.end; y = tr.ai.getAndIncrement()) {
                                    offY = y * sizeX;                                    
                                    for (int x = 0; x < sizeX; ++x) {
                                        off = x + offY;
                                        for (int z = 0; z< sizeZ; ++z) pixels[z][off] = (float) Math.sqrt(grad0[z][off] * grad0[z][off] + grad1[z][off] * grad1[z][off] + grad2[z][off] * grad2[z][off]);
                                    }

                                }
                            }
                        });
            }
            tr.startAndJoin();
        } else {
            final ThreadRunner tr = new ThreadRunner(0, image.getSizeY(), nbCPUs);
            final int sizeX = image.getSizeX();
            final float[][] grad0 = grad[0].getPixelArray();
            final float[][] grad1 = grad[1].getPixelArray();
            for (int i = 0; i < tr.threads.length; i++) {
                tr.threads[i] = new Thread(
                        new Runnable() {
                            public void run() {
                                int offY, off;
                                for (int y = tr.ai.getAndIncrement(); y < tr.end; y = tr.ai.getAndIncrement()) {
                                    offY = y * sizeX;                                    
                                    for (int x = 0; x < sizeX; ++x) {
                                        off = x + offY;
                                        pixels[0][off] = (float) Math.sqrt(grad0[0][off] * grad0[0][off] + grad1[0][off] * grad1[0][off]);
                                    }

                                }
                            }
                        });
            }
            tr.startAndJoin();
        }*/
        return res;
    }
    
    public static ImageFloat[] getHessian(Image image, double scale) {
        ImageFloat[] res = new ImageFloat[image.getSizeZ()==1?2:3];
        final imagescience.image.Image is = ImagescienceWrapper.getImagescience(image);
        scale *= image.getScaleXY();
        Vector vector = new Hessian().run(new FloatImage(is), scale, false);
        for (int i=0;i<res.length;i++) {
            res[i] = (ImageFloat)ImagescienceWrapper.wrap((imagescience.image.Image) vector.get(i));
            res[i].setCalibration(image);
            res[i].resetOffset().addOffset(image);
            res[i].setName(image.getName() + ":hessian" + (i + 1));
        }
        return res;
    }
    
    public static ImageFloat gaussianSmooth(Image image, float scaleXY, float scaleZ) {
        float old_scaleXY = image.getScaleXY();
        float old_scaleZ = image.getScaleZ();
        image.setCalibration(1, (float)(scaleXY / scaleZ));
        Differentiator differentiator = new Differentiator();
        ImageFloat res = (ImageFloat)ImagescienceWrapper.wrap(differentiator.run(ImagescienceWrapper.getImagescience(image), scaleXY, 0, 0, 0));
        image.setCalibration(old_scaleXY, old_scaleZ);
        res.setCalibration(old_scaleXY, old_scaleZ);
        res.resetOffset().addOffset(image);
        return res;
    }
}
