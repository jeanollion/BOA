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
import boa.image.HistogramFactory;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.image.ImageMask2D;
import boa.plugins.SimpleThresholder;
import boa.plugins.Thresholder;
import boa.image.processing.ImageFeatures;
import boa.image.processing.ImageOperations;
import boa.plugins.MultiThreaded;
import boa.plugins.ThresholderHisto;
import boa.plugins.ToolTip;
import boa.utils.ArrayUtil;
import boa.utils.Pair;
import boa.utils.Utils;
import ij.gui.Plot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.math3.analysis.ParametricUnivariateFunction;
import org.apache.commons.math3.analysis.function.Gaussian;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.NotStrictlyPositiveException;
import org.apache.commons.math3.exception.NullArgumentException;
import org.apache.commons.math3.fitting.AbstractCurveFitter;
import org.apache.commons.math3.fitting.GaussianCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoint;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem;
import org.apache.commons.math3.linear.DiagonalMatrix;

/**
 * @author jollion
 */
public class BackgroundFit implements ThresholderHisto, SimpleThresholder, MultiThreaded, Thresholder, ToolTip {
    public static boolean debug;
    NumberParameter sigmaFactor = new BoundedNumberParameter("Sigma factor", 3, 10, 0, null);
    
    public BackgroundFit() {
        
    }
    public BackgroundFit(double sigmaFactor) {
        this.sigmaFactor.setValue(sigmaFactor);
    }
    @Override
    public String getToolTipText() {
        return "Fits a gaussian on the lower half of the mode's peak of the histogram to extract its parameters: Mean & Std. <br />Resulting Threshold = Mean + <em>Sigma Factor</em> * Std<br /> Supposes that the mode corresponds to the background values and that the lower half of the background peak is not too far from a gaussian distribution";
    }
    
    
    boolean multithread;
    @Override
    public void setMultithread(boolean multithread) {
        this.multithread=multithread;
    }
    
    @Override
    public double runThresholderHisto(Histogram histogram) {
        return backgroundFit(histogram, sigmaFactor.getValue().doubleValue(), null);
    }
    
    @Override
    public double runSimpleThresholder(Image input, ImageMask mask) {
        return runThresholderHisto(HistogramFactory.getHistogram(()->Utils.parallele(input.stream(mask, true), multithread), HistogramFactory.BIN_SIZE_METHOD.AUTO_WITH_LIMITS) );
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
    public static void smoothInPlace(int[] data, double scale) {
        float[] smoothed = smooth(data, scale);
        for (int i = 0; i<smoothed.length; ++i)  data[i] = Math.round(smoothed[i]);
    }
    public static void fillZeros(int[] data) {
        for (int i = 1; i<data.length-1; ++i) {
            if (data[i]==0) {
                int lowerV = data[i-1];
                int lowerB = i;
                while(i<data.length-2 && data[i]==0) ++i;
                int fillV = (lowerV+data[i])/2;
                for (int j = lowerB; j<i; ++j) data[j]=fillV;
            }
        }
    }
    public static double backgroundFit(Histogram histo, double sigmaFactor, double[] meanSigma) {
        return backgroundFit(histo, sigmaFactor, meanSigma, false); 
    }
    private static double backgroundFit(Histogram histo, double sigmaFactor, double[] meanSigma, boolean smooth) {
        long t0 = System.currentTimeMillis();
        long t1 = System.currentTimeMillis();
        // get mode -> background
        fillZeros(histo.data);
        if (smooth) smoothInPlace(histo.data, 3);
        int mode = ArrayUtil.max(histo.data);
        double halfWidthIdx = getHalfWidthIdx(histo, mode, true);
        if (Double.isNaN(halfWidthIdx)) halfWidthIdx = getHalfWidthIdx(histo, mode, false);
        long t2 = System.currentTimeMillis();
        double modeFit;
        int halfHalf = Math.max(2, (int)(halfWidthIdx/2d));
        int startT = mode - halfHalf;
        if (startT>=0) {
            // gaussian fit on trimmed data to get more precise mean value
            WeightedObservedPoints obsT = new WeightedObservedPoints();
            for (int i = startT; i<=2 * mode - halfHalf; ++i) obsT.add( i, histo.data[i]-histo.data[startT]);
            double sigma = (mode - halfWidthIdx) / Math.sqrt(2*Math.log(2));
            try { 
                double[] coeffsT = GaussianCurveFitter.create().withStartPoint(new double[]{histo.data[mode]-histo.data[startT], mode, sigma/2.0}).fit(obsT.toList());
                modeFit = coeffsT[1];
                double stdBckT = coeffsT[2];
                //logger.debug("mean (T): {}, std (T): {}", modeFit, stdBckT);
            } catch (Throwable t) {
                if (!smooth) return backgroundFit(histo.duplicate(), sigmaFactor, meanSigma, true);
                throw t;
            }
        } else modeFit = mode;
        halfWidthIdx = getHalfWidthIdx(histo, modeFit, true);
        if (Double.isNaN(halfWidthIdx)) halfWidthIdx = getHalfWidthIdx(histo, mode, false);
        double sigma = (histo.getValueFromIdx(modeFit) - histo.getValueFromIdx(halfWidthIdx)) / Math.sqrt(2*Math.log(2)); // real values
        int start = Double.isNaN(halfWidthIdx) ? getMinMonoBefore(histo.data, mode) : Math.max(0, (int) (modeFit - 6 * (modeFit-halfWidthIdx)));
        // use gaussian fit on lowest half of data 
        long t3 = System.currentTimeMillis();
        WeightedObservedPoints obs = new WeightedObservedPoints();
        for (int i = start; i<=mode; ++i) obs.add(histo.getValueFromIdx(i), histo.data[i]);
        obs.add(modeFit, histo.getCountLinearApprox(modeFit));
        for (double i  = modeFit; i<=2 * modeFit - start; ++i) obs.add(histo.getValueFromIdx(i), histo.getCountLinearApprox(2*modeFit-i));  
        try {
            double[] coeffs = GaussianCurveFitter.create().withStartPoint(new double[]{histo.data[mode], histo.getValueFromIdx(mode), sigma}).fit(obs.toList());
            double meanBck = coeffs[1];
            double stdBck = coeffs[2];
            if (meanSigma!=null) {
                meanSigma[0] = meanBck;
                meanSigma[1] = stdBck;
            }
            double thld = meanBck + sigmaFactor * stdBck;
            return thld;
        } catch(Throwable t) {
            if (!smooth) return backgroundFit(histo.duplicate(), sigmaFactor, meanSigma, true);
            histo.plotIJ1("mode: "+modeFit+ " sigma: "+sigma, debug);
            throw t;
        }
        
        //long t4 = System.currentTimeMillis();
        //logger.debug("mean: {} sigma: {} (from half width: {}), thld: {}, get histo: {} & {}, fit: {} & {}", meanBck, stdBck, sigma, thld, t1-t0, t2-t1, t3-t2, t4-t3);
        
    }
    
    private static double getHalfWidthIdx(Histogram histo, double mode, boolean before) {
        double halfH = histo.getCountLinearApprox(mode) / 2d;
        int half = (int)mode;
        if (before) {
            while(half>0 && histo.data[half-1]>halfH) --half;
            if (half<=0) return Double.NaN;
            // linear approx between half & half -1
            return half-1 + (halfH - histo.data[half-1]) / (double)(histo.data[half]-histo.data[half-1]) ;
        } else {
            while(half<histo.data.length-1 && histo.data[half+1]>halfH) ++half;
            if (half==histo.data.length-1) return Double.NaN;
            // linear approx between half & half -1
            return half + (halfH - histo.data[half]) / (double)(histo.data[half]-histo.data[half+1]) ;
        }
    }
    
    private static int getMinMonoBefore(int[] array, int start) {
        while(start>0 && array[start]>array[start-1]) --start;
        return start;
    }
    
    
    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{sigmaFactor};
    }

    
    
}
