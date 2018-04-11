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
package boa.utils.geom;

import boa.utils.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 *
 * @author Jean Ollion
 */
public class VectorSmoother {
    @FunctionalInterface  private interface GaussFunction {public double getCoeff(double distSq);}
    final GaussFunction gaussCoeff;
    final double limit, coeff0;
    double sum;
    Vector currentVector;
    public VectorSmoother(double sigma) {
        double sig2 = sigma * sigma * 2;
        double coeff = 1/Math.sqrt(sig2 * Math.PI);
        gaussCoeff = x2 -> coeff * Math.exp(-x2/sig2);
        limit = gaussCoeff.getCoeff(9*sigma*sigma);
        coeff0 = gaussCoeff.getCoeff(0);
    }
    public void init(Vector start) {
        currentVector = start.duplicate();
        currentVector.multiply(coeff0);
        sum = coeff0;
    }
    public boolean addVector(Vector v, double dist) {
        return addVectorDistSq(v, dist*dist);
    }
    public boolean addVectorDistSq(Vector v, double distSq) {
        double c = gaussCoeff.getCoeff(distSq);
        if (c<=limit) return false;
        sum+=c;
        currentVector.add(v, c);
        return true;
    }
    public Vector getSmoothedVector() {
        currentVector.multiply(1d/sum);
        return currentVector;
    }

}
