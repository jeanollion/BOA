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
package boa.image.processing;

import boa.core.Task;
import boa.data_structure.dao.MasterDAO;
import boa.image.Histogram;
import boa.image.HistogramFactory;
import boa.image.Image;
import static boa.test_utils.TestUtils.logger;
import boa.utils.ArrayUtil;
import boa.utils.Pair;
import boa.utils.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.math3.analysis.ParametricUnivariateFunction;
import org.apache.commons.math3.analysis.function.Gaussian;
import org.apache.commons.math3.analysis.function.Gaussian.Parametric;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.NotStrictlyPositiveException;
import org.apache.commons.math3.exception.NullArgumentException;
import org.apache.commons.math3.fitting.AbstractCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoint;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem;
import org.apache.commons.math3.linear.DiagonalMatrix;

/**
 *
 * @author Jean Ollion
 */
public class GaussianMixtureModel {
    public static void main(String[] args) {
        // open histo
        String dbName = "MF1_180509";
        int postition= 0, frame=122;
        MasterDAO mDAO = new Task(dbName).getDB();
        List<Image> images = new ArrayList<>();
        for (int f = 0; f<mDAO.getExperiment().getPosition(postition).getFrameNumber(true); ++f)  images.add(mDAO.getExperiment().getPosition(postition).getInputImages().getImage(0, frame));
        Histogram histo = HistogramFactory.getHistogram(()->Image.stream(images), 1);
        
        // get estimation of peak localization -> local extrema
        long t0 = System.currentTimeMillis();
        int scale = 5;
        float[] histoValues = new float[histo.data.length];
        for (int i = 0; i<histoValues.length; ++i) histoValues[i] = histo.data[i];
        ArrayUtil.gaussianSmooth(histoValues, scale);
        
        double[] values = IntStream.range(0, histoValues.length).mapToDouble(i->histoValues[i]>1 ? Math.log(histoValues[i]) : 0).toArray();
        float[] valuesF = new float[values.length];
        for (int i =0; i<values.length; ++i) valuesF[i] = (float)values[i];
        ArrayUtil.getDerivative(valuesF, scale, 2, true);
        Utils.plotProfile("d2", valuesF);
        List<Integer> localMax = ArrayUtil.getRegionalExtrema(values, scale, true);
        Comparator<Integer> peakHeighComp = (p1, p2)->-Double.compare(values[p1], values[p2]);
        int[] mainPeaks = localMax.stream().sorted(peakHeighComp).limit(2).mapToInt(i->i).sorted().toArray();
        if (mainPeaks.length<2) throw new RuntimeException("less than 2 peaks found in histogram!");
        // look for 2nd foreground peak after first foreground: 
        int peak3 = localMax.stream().filter(i->i>mainPeaks[1]).sorted(peakHeighComp).findFirst().orElse(-1);
        if (peak3<0) {
            peak3 = localMax.stream().filter(i->i<mainPeaks[1] && i>mainPeaks[0]).sorted(peakHeighComp).findFirst().orElse(-1);
        }
        long t1 = System.currentTimeMillis();

        double[] peaks = peak3>0 ? new double[]{mainPeaks[0], mainPeaks[1], peak3} : new double[]{mainPeaks[0], mainPeaks[1]};
        logger.debug("peaks: {}", peaks);
        peaks = new double[]{8, 100, 226};
        
        // apply GMM
        // test with syntethic values
        //Gaussian.Parametric p = new Parametric();
        //List<WeightedObservedPoint> points = IntStream.range(0, histoValues.length).mapToObj(i->new WeightedObservedPoint(1, i, p.value(i, new double[]{4, 10, 3})+p.value(i, new double[]{1, 100, 10})+p.value(i, new double[]{2, 200, 20})+10)).collect(Collectors.toList());
        //peaks = new double[]{10, 110, 220};
        List<WeightedObservedPoint> points = IntStream.range(0, values.length).mapToObj(i->new WeightedObservedPoint(1, i, values[i])).collect(Collectors.toList());
        
        Pair<Double, List<double[]>> res = GMMFitter.fitGauss(peaks, points);
        logger.info("gauss found: {}, base line: {}", res.value.size(), res.key);
        for (double[] r : res.value)  logger.info("Peak: pos={}, sig={}, norm={}", r[1], r[0], r[2]);
            
        
        long t2 = System.currentTimeMillis();
        Utils.plotProfile("histo smoothed", values);
        logger.info("get peaks: {}, fit: {}", t1-t0, t2-t1);
    }
    static class GMM implements ParametricUnivariateFunction {
        Gaussian.Parametric p = new Parametric();
        final int gaussN; 
        /**
         * 
         * @param gaussianNumber Number of gaussian functions in the model
         */
        public GMM(int gaussianNumber) {
            this.gaussN= gaussianNumber;
        }
        @Override
        public double value(double x, double... parameters) {
            validateParameters(parameters);
            double sum = parameters[gaussN*3];
            for (int i = 0; i<gaussN; ++i) sum+=p.value(x, parameters[i*3], parameters[i*3+1], parameters[i*3+2]);
            return sum;
        }
        
        @Override 
        public double[] gradient(double x, double... parameters) {
            validateParameters(parameters);
            double[] res = new double[3 * gaussN+1];
            for (int i = 0; i<gaussN; ++i) System.arraycopy(p.gradient(x, parameters[3*i], parameters[1+3*i], parameters[2+3*i]), 0, res, 3*i, 3);
            res[3 * gaussN] = 1; // base line
            return res;
        }
        
        private void validateParameters(double[] param) throws NullArgumentException, DimensionMismatchException, NotStrictlyPositiveException {
            if (param == null) {
                throw new NullArgumentException();
            }
            if (param.length != 3*gaussN+1) {
                throw new DimensionMismatchException(param.length, 3*gaussN+1);
            }
            /*for (int i = 0; i<gaussN; ++i) {
                if (param[2+i*3] <= 0) throw new NotStrictlyPositiveException(param[2+i*3]);
            }*/
        }
    }
    public static class GMMFitter extends AbstractCurveFitter {
        final int gaussN;
        final double[] initPeakPos;
        public GMMFitter(int gaussianN, double[] initialGuessPeakPositions) {
            this.gaussN = gaussianN;
            if (initialGuessPeakPositions.length!=gaussN) throw new IllegalArgumentException("Invalid peak position number");
            this.initPeakPos = initialGuessPeakPositions;
        }
        @Override 
        protected LeastSquaresProblem getProblem(Collection<WeightedObservedPoint> points) {
            final int len = points.size();
            final double[] target  = new double[len];
            final double[] weights = new double[len];
            
            
            int i = 0;
            for(WeightedObservedPoint point : points) {
                target[i]  = point.getY();
                weights[i++] = point.getWeight();
            }
            
            final double[] initialGuess = new double[3 * gaussN+1];
            Arrays.fill(initialGuess, 1d);
            initialGuess[3*gaussN] = 0; // base line
            for (int g= 0; g<gaussN; ++g) initialGuess[1+3*g] = initPeakPos[g];
            
            final AbstractCurveFitter.TheoreticalValuesFunction model = new AbstractCurveFitter.TheoreticalValuesFunction(new GMM(gaussN), points);

            return new LeastSquaresBuilder().
                maxEvaluations(Integer.MAX_VALUE).
                maxIterations(Integer.MAX_VALUE).
                start(initialGuess).
                target(target).
                weight(new DiagonalMatrix(weights)).
                model(model.getModelFunction(), model.getModelFunctionJacobian()).
                build();
        }
        
        public static Pair<Double, List<double[]>> fitGauss(double[] initGuess, Collection<WeightedObservedPoint> points) {
            int gN = initGuess.length;
            while (gN>0) {
                try {
                    GMMFitter fitter = new GMMFitter(gN, Arrays.copyOf(initGuess, gN));
                    double coeffs[] = fitter.fit(points);
                    List<double[]> res = new ArrayList<>(gN);
                    for (int g = 0; g<gN; ++g) res.add(new double[]{coeffs[3*g], coeffs[3*g+1], coeffs[3*g+2]});
                    Collections.sort(res, (d1, d2)->Double.compare(d1[1], d2[1])); // sort by position
                    return new Pair(coeffs[3*gN], res);
                } catch(Throwable t) {
                    --gN;
                }
            }
            return new Pair(Double.NaN, Collections.EMPTY_LIST);
        }
    }
}
