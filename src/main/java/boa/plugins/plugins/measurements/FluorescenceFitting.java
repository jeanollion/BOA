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

import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.ObjectClassParameter;
import boa.configuration.parameters.TextParameter;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.image.Image;
import boa.measurement.MeasurementKey;
import boa.measurement.MeasurementKeyObject;
import boa.plugins.Measurement;
import boa.utils.Utils;
import java.util.ArrayList;
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
    ObjectClassParameter bacteriaClass = new ObjectClassParameter("Bacteria");
    BoundedNumberParameter xMargin = new BoundedNumberParameter("X-Margin", 0, 50, 0, null);
    ObjectClassParameter fluo = new ObjectClassParameter("Fluorescence Channel");
    TextParameter suffix = new TextParameter("Suffix", "", false);
    Parameter[] parameters = new Parameter[]{bacteriaClass, fluo, xMargin, suffix};
    boolean verbose;
    
    // implementation of the Measurement interface
    @Override
    public int getCallStructure() {
        return bacteriaClass.getSelectedClassIdx();
    }

    // this method asks if the measurement should be applied on each object of the call structure track or only on the first element (ie trackhead)
    // in this case we apply the measurement to each object   
    @Override
    public boolean callOnlyOnTrackHeads() {
        return false;
    }
    
    // name and object class of output values. 
    // in our case all fitted values are returned and associated to bacteria object (4 measurement per bacteria)
    @Override
    public List<MeasurementKey> getMeasurementKeys() {
        String suf = suffix.getValue();
        int bIdx = bacteriaClass.getSelectedClassIdx();
        return new ArrayList<MeasurementKey>(2) {{
            add(new MeasurementKeyObject("Signal"+suf, bIdx));
            add(new MeasurementKeyObject("Background"+suf, bIdx));
        }};
    }

    @Override
    public void performMeasurement(StructureObject bacteria) {
        // find the extended microchannel object that contains the bacteria
        // first find the first common parent object class in the object class hierrachy
        /*int commonParentIdx = bacteriaClass.getFirstCommonParentObjectClassIdx(mcClass.getSelectedClassIdx());
        StructureObject parent = bacteria.getParent(commonParentIdx);
        StructureObject mcObject = StructureObjectUtils.getContainer(bacteria.getRegion(), parent.getChildren(mcClass.getSelectedClassIdx()), null);
        */
        // get observed fluo distribution
        Image fluoImage = bacteria.getRoot().getRawImage(fluo.getSelectedClassIdx());
        int yMin = bacteria.getBounds().yMin();
        int yMax = bacteria.getBounds().yMax();
        int xMin = Math.max(fluoImage.xMin(), (int)bacteria.getBounds().xMean() - xMargin.getValue().intValue());
        int xMax = Math.min(fluoImage.xMax(), (int)bacteria.getBounds().xMean() + xMargin.getValue().intValue());
        int z = bacteria.getBounds().zMin(); // assuming 2D images. if not -> include loop in z direction
        double[] observedFluo = IntStream.rangeClosed(xMin, xMax).mapToDouble(x -> {
            return IntStream.rangeClosed(yMin, yMax)
                    .mapToDouble(y -> fluoImage.getPixelWithOffset(x, y, z)).sum(); // sum of fluorescence for each column
        }).toArray();
        
        double iMidStart = bacteria.getBounds().xMean() - yMin; // middle of peak starts at middle of bacteria object. -yMin because in the fitting function the index is 0-based
        logger.debug("iMidStart: {}", iMidStart );
        double wStart = 5.5; // as in Kaiser 2018. // in a more general case, should it be a value depending on bacteria width ? 
        double[] fittedParams = fitFluo(observedFluo, iMidStart, wStart, 1);
        if (verbose) plot(observedFluo, fittedParams);
        // store fitted parameters in measurements
        bacteria.getMeasurements().setValue("Signal"+suffix.getValue(), fittedParams[0]);
        bacteria.getMeasurements().setValue("Background"+suffix.getValue(), fittedParams[1]);
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    // funtion to set parameters 
    public FluorescenceFitting setObjectClasses(int bacteria, int fluo) {
        this.bacteriaClass.setSelectedClassIdx(bacteria);
        this.fluo.setSelectedClassIdx(fluo);
        return this;
    }
    public FluorescenceFitting setXMargin(int margin) {
        this.xMargin.setValue(margin);
        return this;
    }
    public FluorescenceFitting setVerbose(boolean verbose) {
        this.verbose= verbose;
        return this;
    }
    
    // processing functions
    
    /**
     * Fits fluorescence profile {@param c_i} with a Lorenzian distribution using expectation maximization procedure as described in Kaiser, Matthias, Florian Jug, Thomas Julou, Siddharth Deshpande, Thomas Pfohl, Olin K. Silander, Gene Myers, and Erik van Nimwegen. 2018. “Monitoring Single-Cell Gene Regulation Under Dynamically Controllable Conditions with Integrated Microfluidics and Software.” Nature Communications 9 (1):212. https://doi.org/10.1038/s41467-017-02505-0.
     * ci = noise + B + A / ( (1 + i - i_mid) / w )^2
     * @param c_i fluorescence profile
     * @param wStart starting value for width of distribution
     * @param iMidStart starting value for center of peak
     * @return array with fitted values of  A, B, i_mid, w
     */
    public static double[] fitFluo(double[] c_i, double iMidStart, double wStart, int iterationNumber) {
        // step 1
        double cMax = Arrays.stream(c_i).max().getAsDouble();
        double cMin = Arrays.stream(c_i).min().getAsDouble();
        // step 2
        double[] params = new double[] {cMax - cMin, cMin, iMidStart, wStart};
        double[] ro_i = new double[c_i.length];
        for (int i = 0; i<iterationNumber; ++i) iterate(c_i, ro_i, params);
        return params;
    }
    /**
     * Updates {@param parameters} array
     * @param c_i observed values
     * @param ro_i estimated values of ro_i
     * @param parameters parameter array (A, B, iMid, w)
     */
    private static void iterate(double[] c_i, double[] ro_i, double[] parameters) {
        // update ro_i array
        IntStream.range(0, c_i.length).forEach(i -> ro_i[i] = 1/(1+Math.pow((i-parameters[2])/parameters[3], 2)));
        Utils.plotProfile("ro i", ro_i);
        // step 3
        double ro = Arrays.stream(ro_i).sum();
        // step 4
        double B =  1d/c_i.length * IntStream.range(0, c_i.length).mapToDouble(i -> parameters[1] * c_i[i] / (parameters[1] + parameters[0] * ro_i[i]) ).sum();
        // step 5
        double A =  1d/ro * IntStream.range(0, c_i.length).mapToDouble(i -> parameters[0] * c_i[i] * ro_i[i] / (parameters[1] + parameters[0] * ro_i[i]) ).sum();
        // step 6
        // Kaizer 2018 says here update ro_i array ?? ro_i does not depend on A & B
        // find iMid using root of derivative function
        DoubleUnaryOperator d_i_mid = i_mid -> IntStream.range(0, c_i.length).mapToDouble(i-> (i-i_mid)*ro_i[i]*ro_i[i] * (-1 + c_i[i] / (B + A * ro_i[i]) ) ).sum();
        double iMid = getRootBisection(d_i_mid, 100, 1e-2, parameters[2] - parameters[3]/2, parameters[2] + parameters[3]/2);
        
        DoubleBinaryOperator ro_i_f = (i, w) -> 1/(1+Math.pow((i-iMid)/w, 2));
        DoubleUnaryOperator d_w = w -> IntStream.range(0, c_i.length).mapToDouble(i-> {
            double ro_i_ = ro_i_f.applyAsDouble(i, w);
            return ro_i_ * (1 - ro_i_) * (-1 + c_i[i] / (B + A * ro_i[i]) );
        }).sum();
        // TODO: function is not monotoneous : reduce range ? other root finding method ? 
        double w = getRootSecant(d_w, 100, 1e-2, parameters[3], Math.max(parameters[3] - 10, 1), Math.min(parameters[3] + 10, c_i.length-1));
        plot(d_w, IntStream.range(0, 100).mapToDouble(i -> Math.max(parameters[3] - 10, 1)+ i * 20/100 ).toArray());
        logger.debug("fit: Bck{} Fluo: {}, iMid: {}, width: {}, ro: {}", B, A, iMid, w, ro);
        // update parameters
        parameters[0] = A;
        parameters[1] = B;
        parameters[2] = iMid;
        parameters[3] = w;
    }
    private static void plot(DoubleUnaryOperator function, double[] x) {
        double[] values = Arrays.stream(x).map(function).toArray();
        Utils.plotProfile("fit w", values, x, null, null);
    }
    private static void plot(double[] observed, double[] parameters) {
        double[] estimated_c_i = IntStream.range(0, observed.length).mapToDouble(i -> parameters[1] + parameters[0] / (1 + Math.pow((i - parameters[2])/parameters[3], 2) )).toArray();
        double[] x = IntStream.range(0, observed.length).mapToDouble(i -> i - parameters[2]).toArray(); // centered on iMid
        Utils.plotProfile("observed and estimated fluo", estimated_c_i, x, observed, x);
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
