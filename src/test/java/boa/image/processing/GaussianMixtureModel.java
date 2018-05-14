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

import boa.utils.ArrayUtil;
import java.util.Arrays;
import java.util.List;
import java.util.Collection;
import java.util.Collections;
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
        
    }
    class GMM implements ParametricUnivariateFunction {
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
            double sum = 0;
            for (int i = 0; i<gaussN; ++i) sum+=p.value(x, parameters[i*3], parameters[i*3+1], parameters[i*3+2]);
            return sum;
        }
        
        @Override 
        public double[] gradient(double x, double... parameters) {
            validateParameters(parameters);
            double[] res = new double[3 * gaussN];
            for (int i = 0; i<gaussN; ++i) System.arraycopy(p.gradient(x, parameters[3*i], parameters[1+3*i], parameters[2+3*i]), 0, res, 3*i, 3);
            return res;
        }
        
        private void validateParameters(double[] param) throws NullArgumentException, DimensionMismatchException, NotStrictlyPositiveException {
            if (param == null) {
                throw new NullArgumentException();
            }
            if (param.length != 3*gaussN) {
                throw new DimensionMismatchException(param.length, 3*gaussN);
            }
            /*for (int i = 0; i<gaussN; ++i) {
                if (param[2+i*3] <= 0) throw new NotStrictlyPositiveException(param[2+i*3]);
            }*/
        }
    }
    public class GMMFitter extends AbstractCurveFitter {
        final int gaussN;
        public GMMFitter(int gaussianN) {
            this.gaussN = gaussianN;
        }
        @Override 
        protected LeastSquaresProblem getProblem(Collection<WeightedObservedPoint> points) {
            final int len = points.size();
            final double[] x  = new double[len];
            final double[] target  = new double[len];
            final double[] weights = new double[len];
            
            
            int i = 0;
            for(WeightedObservedPoint point : points) {
                target[i]  = point.getY();
                weights[i] = point.getWeight();
                x[i] = point.getX();
                i += 1;
            }
            
            final double[] initialGuess = new double[3 * gaussN];
            Arrays.fill(initialGuess, 1d);
            // get estimation of peak localization -> local extrema
            int scale = Math.max(3, points.size() / 50);
            List<Integer> localMax = ArrayUtil.getRegionalExtrema(target, scale, true);
            if (localMax.size()<gaussN) {
                while (localMax.size()<gaussN && scale>1) localMax = ArrayUtil.getRegionalExtrema(target, --scale, true);
            } else if (localMax.size()>gaussN) {
                List<Integer> localMax2 = ArrayUtil.getRegionalExtrema(target, scale*2, true);
                if (localMax2.size()>=gaussN) localMax = localMax2;
            }
            
            if (localMax.size()<gaussN) {  // not enough peaks -> ensure at least 3 points
                if (localMax.isEmpty()) {
                    localMax.add(0);
                    localMax.add(len/2);
                    localMax.add(len-1);
                } else { // -> add middle points until enough
                    int minDist = points.size() / 50;
                    if (localMax.get(0)> minDist) localMax.add(0);
                    if (localMax.get(localMax.size()-1) < points.size() -minDist ) localMax.add(len-1);
                    while(localMax.size()<gaussN) {
                        // if segment is long enough -> add middle point
                    }
                }
            }
            Collections.sort(localMax, (i1, i2)->Double.compare(target[i1], target[i2])); // sort by value -> get the 3 most important local max
            for (int g = 0; g<gaussN; ++g) initialGuess[3*i + 1] = x[localMax.get(g)];
            
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
    }
}
