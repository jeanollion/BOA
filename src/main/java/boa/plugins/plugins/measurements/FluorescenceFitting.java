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
package boa.plugins.plugins.measurements;

import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.ObjectClassParameter;
import boa.data_structure.StructureObject;
import boa.measurement.MeasurementKey;
import boa.plugins.Measurement;
import java.util.Arrays;
import java.util.List;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.IntStream;

/**
 *
 * @author Jean Ollion
 */
public class FluorescenceFitting implements Measurement{
    ObjectClassParameter bacteria = new ObjectClassParameter("Bacteria");
    ObjectClassParameter mc = new ObjectClassParameter("Microchannel");
    
    @Override
    public int getCallStructure() {
        return bacteria.getSelectedClassIdx();
    }

    // this method asks if the measurement should be applied on each object of the call structure track or only on the first element (ie trackhead)
    // in this case we apply the measurement to each object   
    @Override
    public boolean callOnlyOnTrackHeads() {
        return false;
    }
    
    // name and object class of output values. 
    // in our case all fitted values are returned and associated to bacteria
    @Override
    public List<MeasurementKey> getMeasurementKeys() {
        
    }

    @Override
    public void performMeasurement(StructureObject object) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }

    
    
    
    /**
     * Fits fluorescence profile {@param c_i} with a Lorenzian distribution using expectation maximization procedure as described in Kaiser, Matthias, Florian Jug, Thomas Julou, Siddharth Deshpande, Thomas Pfohl, Olin K. Silander, Gene Myers, and Erik van Nimwegen. 2018. “Monitoring Single-Cell Gene Regulation Under Dynamically Controllable Conditions with Integrated Microfluidics and Software.” Nature Communications 9 (1):212. https://doi.org/10.1038/s41467-017-02505-0.
     * ci = noise + B + A / ( (1 + i - i_mid) / w )^2
     * @param c_i fluorescence profile
     * @param wStart starting value for width of distribution
     * @param iMidStart starting value for center of peak
     * @return array with fitted values of  A, B, i_mid, w
     */
    public static double[] fitFluo(double[] c_i, double iMidStart, double wStart) {
        // step 1
        double cMax = Arrays.stream(c_i).max().getAsDouble();
        double cMin = Arrays.stream(c_i).min().getAsDouble();
        // step 2
        double BStart = cMin;
        double AStart = cMax - cMin;
        // step 3
        double[] ro_i = IntStream.range(0, c_i.length).mapToDouble(i -> 1/(1+Math.pow((i-iMidStart)/wStart, 2))).toArray();
        double ro = Arrays.stream(ro_i).sum();
        // step 4
        double B =  1d/c_i.length * IntStream.range(0, c_i.length).mapToDouble(i -> BStart * c_i[i] / (BStart + AStart * ro_i[i]) ).sum();
        // step 5
        double A =  1d/ro * IntStream.range(0, c_i.length).mapToDouble(i -> AStart * c_i[i] * ro_i[i] / (BStart + AStart * ro_i[i]) ).sum();
        // step 6
        // update ro_i array ?? 
        // find iMid using root of derivative function
        DoubleUnaryOperator d_i_mid = i_mid -> IntStream.range(0, c_i.length).mapToDouble(i-> (i-i_mid)*ro_i[i]*ro_i[i] * (-1 + c_i[i] / (B + A * ro_i[i]) ) ).sum();
        double iMid = getRootBisection(d_i_mid, 100, 1e-2, iMidStart - wStart/2, iMidStart + wStart/2);
        
        DoubleBinaryOperator ro_i_f = (i, w) -> 1/(1+Math.pow((i-iMid)/w, 2));
        DoubleUnaryOperator d_w = w -> IntStream.range(0, c_i.length).mapToDouble(i-> {
            double ro_i_ = ro_i_f.applyAsDouble(i, w);
            return ro_i_ * (1 - ro_i_) * (-1 + c_i[i] / (B + A * ro_i[i]) );
        }).sum();
        double w = getRootBisection(d_i_mid, 100, 1e-2, iMidStart - wStart/2, iMidStart + wStart/2);
        return new double[] {A, B, iMid, w};
    }
    
    
    // 2 methods to find roots
    /**
     * Finds a root of {@param function} within range [{@param leftBound} ; {@param rightBound}] using bisection method
     * @param function
     * @param maxIteration maximum number of iteration
     * @param precision precision of the search 
     * @param leftBound
     * @param rightBound 
     * @return a root of {@param function}
     */
    public static double getRootBisection(DoubleUnaryOperator function, int maxIteration, double precision, double leftBound, double rightBound) {
        double x = 0, xLeft = leftBound, xRight = rightBound;
        double error = xRight-xLeft;
        int iter = 0;
        double fx = function.applyAsDouble(x);
        double fLeft = function.applyAsDouble(xLeft);
        while (Math.abs(error) > precision && iter<maxIteration && fx!=0 ) {
            x = ((xLeft+xRight)/2);
            fx = function.applyAsDouble(x);
            if ((fLeft*fx) < 0) {
                xRight  = x;
                error = xRight-xLeft;
            } else {
                xLeft = x;
                error = xRight-xLeft;
                fLeft = fx;
            }
            ++iter;
        }
        return x;
    }
    /**
     * Finds a root of {@param function} within range [{@param leftBound} ; {@param rightBound}] using secant method
     * @param function
     * @param maxIteration maximum number of iteration
     * @param precision precision of the search 
     * @param leftBound
     * @param rightBound 
     * @return a root of {@param function}
     */
    public static double getRootSecant(DoubleUnaryOperator function, int maxIteration, double precision, double start, double leftBound, double rightBound) {
        if (start<leftBound || start>rightBound) throw new IllegalArgumentException("X not withing provided bounds");
        int iter = 0;
        double x = start;
        double x1 = leftBound;
        double x2 = rightBound;
        double dx=rightBound - leftBound;
        double fX1 = function.applyAsDouble(x1);
        double fX2 = function.applyAsDouble(x2);
        double fX = function.applyAsDouble(x);
        while ((Math.abs(dx)>precision) && (iter<maxIteration) && fX2!=0) {
            double d = fX1-fX;
            x2 = x1-fX1*(x1-x)/d;
            x  = x1;
            fX=fX1;
            fX1 = fX2;
            fX2 = function.applyAsDouble(x2);
            x1 = x2;
            dx = x1-x;
            ++iter;
        }
        return x1;
    }

}
