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
package plugins.plugins.transformations;

import configuration.parameters.BooleanParameter;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.Parameter;
import dataStructure.containers.InputImages;
import image.Image;
import image.ImageFloat;
import image.ImageOperations;
import java.util.ArrayList;
import plugins.Transformation;
import plugins.plugins.thresholders.BackgroundFit;
import plugins.plugins.thresholders.BackgroundThresholder;

/**
 *
 * @author jollion
 */
public class ScaleHistogram implements Transformation {
    BoundedNumberParameter scaleFactor= new BoundedNumberParameter("Scale Factor", 1, 100, 1, null);
    BooleanParameter method = new BooleanParameter("Mean estimation method", "Gaussian Fit", "Kappa-Sigma", false);
    Parameter[] parameters = new Parameter[]{scaleFactor, method};
    
    public ScaleHistogram() {}
    
    public ScaleHistogram(double scaleFactor, boolean gaussianFit) {
        this.scaleFactor.setValue(scaleFactor);
        method.setSelected(gaussianFit);
    }
    
    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        /*double scaleFactor = this.scaleFactor.getValue().doubleValue();
        double[] means = new double[inputImages.getTimePointNumber()]; 
        double[] meansGF = new double[inputImages.getTimePointNumber()]; 
        double[] meansKS3 = new double[inputImages.getTimePointNumber()];
        double[] meansKS4 = new double[inputImages.getTimePointNumber()];
        for (int t = 0; t<inputImages.getTimePointNumber(); ++t) {
            Image i = inputImages.getImage(channelIdx, t);
            means[t] = getMean(i);
            meansGF[t] = getMean(scaleGF(i, scaleFactor));
            meansKS3[t] = getMean(scaleKS(i, scaleFactor, 3, 2));
            meansKS4[t] = getMean(scaleKS(i, scaleFactor, 4, 2));
        }
        normal: 114 / 6.8
        result: GF: 105 / 0.75
        KS3 : 100 / 0.03
        KS4: 100 / 0.02
        logger.debug("ScaleHistogram: no correction: {}, gaussian fit: {}, ks 3: {}, ks 4: {}", getMeanSD(means), getMeanSD(meansGF), getMeanSD(meansKS3), getMeanSD(meansKS4));
        */
    
    }
    
    
    
    private static Image scaleGF(Image image, double scaleFactor) {
        double[] meanSigma = new double[2];
        BackgroundFit.backgroundFitHalf(image, null, 1, meanSigma);
        double scale = scaleFactor / meanSigma[0];
        return ImageOperations.affineOperation(image, null, scale, 0);
    }
    
    private static Image scaleKS(Image image, double scaleFactor, double sigmaFactorKS, int iterationsKS) {
        double[] meanSigma = new double[2];
        BackgroundThresholder.run(image, null, sigmaFactorKS, sigmaFactorKS, iterationsKS, meanSigma);
        double scale = scaleFactor / meanSigma[0];
        return ImageOperations.affineOperation(image, null, scale, 0);
    }
    
    private static double getMean(Image image) {
        double[] meanSigma = new double[2];
        BackgroundThresholder.run(image, null, 3, 3, 1, meanSigma);
        return meanSigma[0];
    }
    
    private static double[] getMeanSD(double[] values) {
        double mean=0;
        double mean2=0;
        for (double val : values) {
            mean+=val;
            mean2+=val*val;
        }
        mean/=(double)values.length;
        return new double[]{mean, Math.sqrt(mean2/(double)values.length - mean*mean)};
    }
    
    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        double[] meanSigma = new double[2];
        if (method.getSelected()) BackgroundFit.backgroundFitHalf(image, null, 1, meanSigma);
        else BackgroundThresholder.run(image, null, 3, 3, 2, meanSigma);
        double scale = scaleFactor.getValue().doubleValue() / meanSigma[0];
        logger.debug("timePoint: {} estimated background : {}, scale value: {}, method: {}", timePoint, meanSigma[0], scale, method.getSelectedItem());
        return ImageOperations.affineOperation(image, image instanceof ImageFloat? image: null, scale, 0);
    }

    public ArrayList getConfigurationData() {
        return null;
    }
    
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return true;
    }

    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.SAME;
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
}
