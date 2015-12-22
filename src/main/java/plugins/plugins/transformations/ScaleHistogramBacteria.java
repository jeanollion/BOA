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
import configuration.parameters.ChannelImageParameter;
import configuration.parameters.Parameter;
import dataStructure.containers.InputImages;
import image.BlankMask;
import image.Image;
import image.ImageFloat;
import image.ImageInteger;
import image.ImageMask;
import image.ImageOperations;
import java.util.ArrayList;
import plugins.Transformation;
import plugins.plugins.thresholders.BackgroundFit;
import plugins.plugins.thresholders.KappaSigma;
import utils.ThreadRunner;

/**
 *
 * @author jollion
 */
public class ScaleHistogramBacteria implements Transformation {
    BoundedNumberParameter sigmaTh= new BoundedNumberParameter("Theorical Sigma", 2, 7.83, 1, null);
    BoundedNumberParameter muTh= new BoundedNumberParameter("Theorical Mean", 2, 106, 1, null);
    ChannelImageParameter signalExclusion = new ChannelImageParameter("Channel for Signal Exclusion");
    Parameter[] parameters = new Parameter[]{sigmaTh, muTh, signalExclusion};
    ArrayList<double[]> meanSigmaT;
    public ScaleHistogramBacteria() {}
    
    public ScaleHistogramBacteria(double sigmaTh, double muTh, int signalExclusion) {
        this.sigmaTh.setValue(sigmaTh);
        this.muTh.setValue(muTh);
        if (signalExclusion>=0) this.signalExclusion.setSelectedIndex(signalExclusion);
    }
    
    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        ImageMask exclusionMask;
        int chExcl = signalExclusion.getSelectedIndex();
        if (chExcl>=0) {
            Image bacteria = inputImages.getImage(chExcl, channelIdx);
        }
        ThreadRunner tr = new ThreadRunner(0, inputImages.getTimePointNumber());
        
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
    public static double[] computeMeanSigma(Image image, Image exclusionSignal, double exclusionThreshold, ImageInteger exclusionMask) {
        if (exclusionMask!=null) ImageOperations.threshold(exclusionSignal, exclusionThreshold, true, false, true, exclusionMask);
        else exclusionMask = new BlankMask(image);
        return ImageOperations.getMeanAndSigma(image, exclusionMask);
    }
    
    private static Image scaleGF(Image image, double scaleFactor) {
        double[] meanSigma = new double[2];
        BackgroundFit.backgroundFit(image, null, 1, meanSigma);
        double scale = scaleFactor / meanSigma[0];
        return ImageOperations.affineOperation(image, null, scale, 0);
    }
    
    private static Image scaleKS(Image image, double scaleFactor, double sigmaFactorKS, int iterationsKS) {
        double[] meanSigma = new double[2];
        KappaSigma.kappaSigmaThreshold(image, null, sigmaFactorKS, iterationsKS, meanSigma);
        double scale = scaleFactor / meanSigma[0];
        return ImageOperations.affineOperation(image, null, scale, 0);
    }
    
    private static double getMean(Image image) {
        double[] meanSigma = new double[2];
        KappaSigma.kappaSigmaThreshold(image, null, 3, 1, meanSigma);
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
        if (meanSigmaT==null || meanSigmaT.isEmpty() || meanSigmaT.size()<=timePoint) throw new Error("ScaleHistogram transformation not configured");
        double[] muSig = this.meanSigmaT.get(timePoint);
        double alpha = muSig[1] / this.sigmaTh.getValue().doubleValue();
        double beta = muSig[0] - alpha * this.muTh.getValue().doubleValue();
        return ImageOperations.affineOperation(image, image instanceof ImageFloat? image: null, 1d/alpha, -beta/alpha);
    }

    public ArrayList getConfigurationData() {
        return meanSigmaT;
    }

    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.SAME;
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
}
