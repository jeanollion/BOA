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
package plugins.plugins.thresholders;

import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.StructureObjectProcessing;
import image.BlankMask;
import image.Image;
import image.ImageMask;
import plugins.SimpleThresholder;
import plugins.Thresholder;

/**
 * Implementation of Kappa Sigma Clipping algorithm by Gaëtan Lehmann, http://www.insight-journal.org/browse/publication/132. 
 * Finds the mean and sigma of the background, and use this two properties to select the pixels significantly different of the background. 
 * Mean and sigma are first computed on the entire image, and a threshold is computed as mean + f * sigma. 
 * This threshold is then used to select the background, and recompute a new threshold with only pixels in the background. 
 * This algorithm shouldn’t converge to a value, so the number of iterations must be provided. 
 * In general, two iterations are used.
 *
 * @author jollion
 */
public class KappaSigma implements SimpleThresholder, Thresholder {
    NumberParameter sigmaFactor = new BoundedNumberParameter("Sigma factor", 2, 3, 0.01, null);
    NumberParameter iterations = new BoundedNumberParameter("Iteration number", 0, 2, 1, null);
    Parameter[] parameters = new Parameter[]{sigmaFactor, iterations};
    
    @Override 
    public double runThresholder(Image input) {
        return runThresholder(input, null);
    }
    @Override
    public double runThresholder(Image input, StructureObjectProcessing structureObject) {
        return kappaSigmaThreshold(input, structureObject!=null?structureObject.getMask():null, sigmaFactor.getValue().doubleValue(), iterations.getValue().intValue(), null);
    }

    public static double kappaSigmaThreshold(Image input, ImageMask mask, double sigmaFactor, int iterations, double[] meanSigma) {
        if (meanSigma!=null && meanSigma.length<2) throw new IllegalArgumentException("Argument Mean Sigma should be null or of size 2 to recieve mean and sigma values");
        if (mask==null) mask = new BlankMask(input);
        double lastThreshold = Double.MAX_VALUE;
        double count, mean, mean2, sigma;
        if (iterations<=0) iterations=1;
        for (int i = 0; i<iterations; i++) {
            count=0;
            mean=0;
            mean2=0;
            sigma=0;
            for (int z = 0; z<input.getSizeZ(); z++) {
                for (int xy = 0; xy<input.getSizeXY(); xy++) {
                    if (mask.insideMask(xy, z)) {
                        double val = input.getPixel(xy, z);
                        if (val<lastThreshold) {
                            mean+=val;
                            mean2+=val*val;
                            count++;
                        }
                    }
                }
            }
            if (count>0) {
                mean/=count;
                sigma = Math.sqrt(mean2/count - mean*mean);
                if (meanSigma!=null) {
                    meanSigma[0]=mean;
                    meanSigma[1]=sigma;
                }
            }
            double newThreshold = mean + sigmaFactor * sigma;
            //logger.trace("Kappa Sigma Thresholder: Iteration:"+ i+" Mean Background Value: "+mean+ " Sigma: "+sigma+ " threshold: "+newThreshold);
            if (newThreshold == lastThreshold) return lastThreshold;
            else lastThreshold = newThreshold;
        }
        return lastThreshold;
    }
    
    
    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    
}
