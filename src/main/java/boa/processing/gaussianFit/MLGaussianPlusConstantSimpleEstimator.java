/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package boa.processing.gaussianFit;

import static boa.core.Processor.logger;
import net.imglib2.Localizable;
import net.imglib2.algorithm.localization.MLGaussianEstimator;
import net.imglib2.algorithm.localization.Observation;

/**
 *
 * @author jollion
 */
public class MLGaussianPlusConstantSimpleEstimator extends MLGaussianEstimator {
    final protected double sigma;
    final protected int nDims;
    public MLGaussianPlusConstantSimpleEstimator(double typicalSigma, double lowerBound, double upperBound, int nDims) {
        super(typicalSigma, nDims);
        this.sigma= typicalSigma;
        this.nDims=nDims;
    }

    @Override
    public double[] initializeFit(Localizable point, Observation data) {
        
        final double[] start_param = new double[nDims+3];
        for (int j = 0; j < nDims; j++) {
                start_param[j] = point.getLongPosition(j);
        }
        double centerValue = getValue(point, data);
        start_param[nDims + 1] = this.sigma;//b
        double[] meanAndMin = getMeanAndMinValue(point, data, 3 * sigma);
        start_param[nDims + 2] = (meanAndMin[0]+meanAndMin[1])/2; //C
        start_param[nDims] = centerValue - start_param[nDims + 2]; //A
        logger.debug("startpoint estimation: data: {}, {}", data.I.length, start_param);
        return start_param;
    }
    
    private static double getValue(Localizable point, Observation data) {
        double d = Double.MAX_VALUE;
        double res = Double.NaN;
        for (int i =0 ; i<data.I.length; ++i) {
            double temp = distSq(data.X[i], point);
            if (temp<d) {
                d=temp;
                res = data.I[i];
            }
        }
        return res;
    }
    
    private static double[] getMeanAndMinValue(Localizable point, Observation data, double distMax) {
        double res = 0;
        double count = 0;
        distMax = distMax * distMax;
        double min = Double.POSITIVE_INFINITY;
        for (int i =0 ; i<data.I.length; ++i) {
            double d = distSq(data.X[i], point);
            if (d<distMax) {
                count++;
                res += data.I[i];
                if (data.I[i]<min) min = data.I[i];
            }
        }
        return new double[]{res/count, min};
    }
    
    private static double distSq(double[] p1, Localizable point) {
        double  d = 0;
        for (int i = 0; i<p1.length; ++i) d+=Math.pow(p1[i]-point.getDoublePosition(i), 2);
        return d;
    }
    
    @Override
    public String toString() {
        return "Simple estimator for gaussian peaks";
    }
}
