/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins.plugins.thresholders;

import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.data_structure.StructureObjectProcessing;
import boa.image.BlankMask;
import boa.image.Histogram;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageMask;
import boa.plugins.SimpleThresholder;

/**
 * Adapted from Implementation of Kappa Sigma Clipping algorithm by Gaëtan Lehmann, http://www.insight-journal.org/browse/publication/132. 
 * Finds the mean and sigma of the background, and use this two properties to select the pixels significantly different of the background. 
 * Mean and sigma are first computed on the entire image, and a threshold is computed as mean + f * sigma. 
 * This threshold is then used to select the background, and recompute a new threshold with only pixels in the background. 
 * This algorithm shouldn’t converge to a value, so the number of iterations must be provided. 
 * In general, two iterations are used.
 * Variation : added final sigma for final threshold computation
 * @author jollion
 */
public class BackgroundThresholder implements SimpleThresholder {
    NumberParameter sigmaFactor = new BoundedNumberParameter("Sigma factor", 2, 2.5, 0.01, null);
    NumberParameter finalSigmaFactor = new BoundedNumberParameter("Final Sigma factor", 2, 4, 0.01, null);
    NumberParameter iterations = new BoundedNumberParameter("Iteration number", 0, 2, 1, null);
    PluginParameter<SimpleThresholder> startingPoint = new PluginParameter<>("Starting value", SimpleThresholder.class, true);
    Parameter[] parameters = new Parameter[]{sigmaFactor, finalSigmaFactor, iterations, startingPoint};
    
    public BackgroundThresholder() {}
    
    public BackgroundThresholder(double sigmaFactor, double finalSigmaFactor, int iterations) {
        this.sigmaFactor.setValue(sigmaFactor);
        this.finalSigmaFactor.setValue(finalSigmaFactor);
        this.iterations.setValue(iterations);
    }
    public BackgroundThresholder setStartingValue(SimpleThresholder thlder) {
        this.startingPoint.setPlugin(thlder);
        return this;
    }
    @Override 
    public double runSimpleThresholder(Image input, ImageMask mask) {
        double firstValue = Double.MAX_VALUE;
        if (this.startingPoint.isOnePluginSet()) {
            firstValue = startingPoint.instanciatePlugin().runSimpleThresholder(input, mask);
        }
        return runThresholder(input, mask, sigmaFactor.getValue().doubleValue(), finalSigmaFactor.getValue().doubleValue(), iterations.getValue().intValue(), firstValue, null);
    }
    @Override
    public double runThresholder(Image input, StructureObjectProcessing structureObject) {
        ImageMask mask = structureObject!=null?structureObject.getMask():null;
        //return BackgroundThresholder.runSimpleThresholder(input, mask, sigmaFactor.getValue().doubleValue(), finalSigmaFactor.getValue().doubleValue(), iterations.getValue().intValue(), null);
        return runSimpleThresholder(input , mask);
    }
    
    public static double runThresholder(Image input, ImageMask mask, double sigmaFactor, double lastSigmaFactor, int iterations, double firstValue) {
        return runThresholder(input,mask, sigmaFactor, lastSigmaFactor, iterations, firstValue, null);
    }
    public static double runThresholder(Image input, ImageMask mask, double sigmaFactor, double lastSigmaFactor, int iterations, double firstValue, double[] meanSigma) {
        if (meanSigma!=null && meanSigma.length<2) throw new IllegalArgumentException("Argument Mean Sigma should be null or of size 2 to recieve mean and sigma values");
        if (mask==null) mask = new BlankMask(input);
        if (firstValue==Double.NaN) firstValue = Double.MAX_VALUE;
        double lastThreshold = firstValue;
        double count, mean, mean2, sigma;
        if (iterations<=0) iterations=1;
        for (int i = 0; i<iterations; i++) {
            count=0;
            mean=0;
            mean2=0;
            sigma=0;
            for (int z = 0; z<input.sizeZ(); z++) {
                for (int xy = 0; xy<input.sizeXY(); xy++) {
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
                    if (meanSigma.length>2) meanSigma[2] = count;
                }
            }
            double newThreshold = i==iterations-1 ? mean + lastSigmaFactor * sigma : mean + sigmaFactor * sigma;
            if (Double.isFinite(firstValue)) newThreshold = Math.min(firstValue, newThreshold);
            //logger.debug("Kappa Sigma Thresholder: Iteration:"+ i+" Mean Background Value: "+mean+ " Sigma: "+sigma+ " threshold: "+newThreshold);
            if (newThreshold == lastThreshold) return lastThreshold;
            else lastThreshold = newThreshold;
        }
        //logger.debug("background thlder: {} ms: {}, first value: {}", Math.min(firstValue, lastThreshold), meanSigma, firstValue);
        return Math.min(firstValue, lastThreshold);
    }
    /*
    public static double runThresholderHisto(Image input, ImageMask mask, double sigmaFactor, double lastSigmaFactor, int iterations, double[] meanSigma) {
        Histogram histo = input.getHisto256(mask, null);
        return BackgroundThresholder.runThresholder(histo, sigmaFactor, lastSigmaFactor, iterations, meanSigma);
    }
    
    public static double runThresholder(Histogram histo, double sigmaFactor, double lastSigmaFactor, int iterations, double[] meanSigma) {
        double thld = getHistogramIdx(histo.data, sigmaFactor, lastSigmaFactor, iterations, meanSigma);
        return histo.getValueFromIdx(thld);
    }
    // TODO TEST !!! 
    private static double getHistogramIdx(int[] histogram, double sigmaFactor, double lastSigmaFactor, int iterations, double[] meanSigma) {
        if (meanSigma!=null && meanSigma.length<2) throw new IllegalArgumentException("Argument Mean Sigma should be null or of size 2 to recieve mean and sigma values");
        int lastThreshold = 255;
        double mean=0, sigma=0;
        double count=0;
        if (iterations<=0) iterations=1;
        for (int i = 0; i<iterations; i++) { //TODO for more precision: linear approx : add a part of next histogram break proportional to thld-(int)thld
            count=0;
            sigma=0;
            mean=0;
            for (int idx = 0; idx<=lastThreshold; idx++) {
                mean+=idx*histogram[idx];
                count+=histogram[idx];
            }
            if (count>0) {
                mean = mean/count;
                for (int idx = 0; idx<=lastThreshold; idx++) sigma+=Math.pow(mean-idx, 2)*histogram[idx];
                sigma= Math.sqrt(sigma/count);
                if (meanSigma!=null) {
                    meanSigma[0]=mean;
                    meanSigma[1]=sigma;
                }
            }
            int newThreshold = i==iterations-1 ? (int)(mean + lastSigmaFactor * sigma+0.5) : (int)(mean + sigmaFactor * sigma+0.5);
            //logger.debug("Kappa Sigma Thresholder HISTO: Iteration:"+ i+" Mean Background Value: "+mean+ " Sigma: "+sigma+ " threshold: "+newThreshold);
            if (newThreshold == lastThreshold) break;
            else lastThreshold = newThreshold;
        }
        return mean + lastSigmaFactor * sigma;
    }*/
    
    
    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    
}
