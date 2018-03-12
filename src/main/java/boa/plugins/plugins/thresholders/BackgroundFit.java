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
import boa.data_structure.StructureObjectProcessing;
import boa.image.BlankMask;
import boa.image.Histogram;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageFloat;
import boa.image.ImageMask;
import boa.plugins.SimpleThresholder;
import boa.plugins.Thresholder;
import boa.image.processing.ImageFeatures;
import boa.utils.ArrayUtil;
import boa.utils.Utils;

/**
 * @author jollion
 */
public class BackgroundFit implements SimpleThresholder, Thresholder {
    public static boolean debug;
    
    NumberParameter sigmaFactor = new BoundedNumberParameter("Sigma factor", 2, 3, 0.01, null);
    
    @Override
    public double runSimpleThresholder(Image input, ImageMask mask) {
        return backgroundFitHalf(input, mask, sigmaFactor.getValue().doubleValue(), null);
    }
    @Override
    public double runThresholder(Image input, StructureObjectProcessing structureObject) {
        return runSimpleThresholder(input, structureObject.getMask() );
    }
    public static float[] smooth(int[] data, double scale) {
        ImageFloat image = new ImageFloat("", data.length, 1, 1);
        for (int i = 0; i<data.length; ++i) image.setPixel(i, 0, 0, data[i]);
        image = ImageFeatures.gaussianSmooth(image, scale, scale, true);
        return image.getPixelArray()[0];
    }
    public static void fillZeros(int[] data) {
        for (int i = 0; i<data.length; ++i) {
            if (data[i]==0) {
                int lowerV = i>0? data[i-1] : 0;
                int lowerB = i;
                while(i<data.length-1 && data[i]==0) ++i;
                int fillV;
                if (lowerV>0 && data[i]>0) fillV = (lowerV+data[i])/2;
                else if (data[lowerB]>0) fillV = lowerV;
                else fillV = data[i+1];
                for (int j = lowerB; j<i; ++j) data[j]=fillV;
            }
        }
    }
    public static double backgroundFitHalf(Image input, ImageMask mask, double sigmaFactor, double[] meanSigma) {
        if (mask==null) mask = new BlankMask(input);
        return backgroundFitHalf(input.getHisto256(mask), sigmaFactor, meanSigma);
    }
    public static double backgroundFitHalf(Histogram histo, double sigmaFactor, double[] meanSigma) {
        if (meanSigma!=null && meanSigma.length<2) throw new IllegalArgumentException("Argument Mean Sigma should be null or of size 2 to recieve mean and sigma values");
        //fillZeros(histo);
        float[] histoSmooth = smooth(histo.data, 3); //2 ou 3
        
        // fit on whole histogram
        double[] fit = ArrayUtil.gaussianFit(histoSmooth, 0);
        int mode = (int)(fit[0]+0.5);
        if (debug) logger.debug("first fit: {}", fit);
        //int mode = ArrayUtil.max(histo);
        double[] subset = new double[mode+1];
        for (int i = 0;i<=mode;++i) subset[i]=histoSmooth[i];
        if (debug) {
            Utils.plotProfile("gauss fit: histo", histo.data);
            Utils.plotProfile("gauss fit: histo smooth", histoSmooth);
            Utils.plotProfile("gauss fit: histo smooth sub", subset);
        }
        fit = ArrayUtil.gaussianFit(subset, 2);
        if (debug) logger.debug("second fit: {}", fit);
        double threshold = mode + sigmaFactor * fit[1];
        
        
        double binSize = histo.getBinSize();
        double min =histo.getHistoMinBreak();
        threshold = threshold * binSize + min;
        if (meanSigma!=null) {
            meanSigma[0] = fit[0] * binSize + min;
            meanSigma[1] = fit[1] * binSize;
            if (meanSigma.length>2) meanSigma[2] = threshold;
            if (meanSigma.length>3) meanSigma[3] = fit[0] - sigmaFactor * fit[1];
        }
        //logger.debug("gaussian fit histo: modal value: {}, sigma: {}, threshold: {}", meanSigma[0], meanSigma[1], threshold);
        return threshold;
    }
    
    public static double backgroundFit(Image input, ImageMask mask, double sigmaFactor, double[] meanSigma) {
        if (meanSigma!=null && meanSigma.length<2) throw new IllegalArgumentException("Argument Mean Sigma should be null or of size 2 to recieve mean and sigma values");
        if (mask==null) mask = new BlankMask(input);
        Histogram histo = input.getHisto256(mask);
        //fillZeros(histo);
        float[] histoSmooth = smooth(histo.data, 3); //2 ou 3
        
        // fit on whole histogram
        double[] fit = ArrayUtil.gaussianFit(histoSmooth, 0);
        int mode = (int)(fit[0]+0.5);
        if (debug) logger.debug("first fit: {}", fit);
        //int mode = ArrayUtil.max(histo);
        if (debug) {
            Utils.plotProfile("gauss fit: histo", histo.data);
            Utils.plotProfile("gauss fit: histo smooth", histoSmooth);
        }
        double threshold = mode + sigmaFactor * fit[1];
        
        double[] mm = input.getMinAndMax(mask, null);
        double binSize = (input instanceof ImageByte) ? 1 : (mm[1] - mm[0]) / 256.0;
        double min = (input instanceof ImageByte) ? 0 : mm[0];
        threshold = threshold * binSize + min;
        if (meanSigma!=null) {
            meanSigma[0] = fit[0] * binSize + min;
            meanSigma[1] = fit[1] * binSize;
            if (meanSigma.length>2) meanSigma[2] = threshold;
            if (meanSigma.length>3) meanSigma[3] = fit[0] - sigmaFactor * fit[1];
        }
        //logger.debug("gaussian fit histo: modal value: {}, sigma: {}, threshold: {}", meanSigma[0], meanSigma[1], threshold);
        return threshold;
    }
    
    
    public Parameter[] getParameters() {
        return new Parameter[]{sigmaFactor};
    }

    public boolean does3D() {
        return true;
    }
    
}
