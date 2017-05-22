/*
 * Copyright (C) 2017 jollion
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
package utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author jollion
 */
public interface SlidingOperator<E, A, R> {
    public A instanciateAccumulator();
    public void slide(E removeElement, E addElement, A accumulator);
    public R compute(A accumulator);
    
    public static SlidingOperator<Double, double[], Double> slidingMean(double defaultValue) {
        return new SlidingOperator<Double, double[], Double>() {

            @Override
            public double[] instanciateAccumulator() {
                return new double[2];
            }

            @Override
            public void slide(Double removeElement, Double addElement, double[] accumulator) {
                if (removeElement!=null) {
                    accumulator[0]-=removeElement;
                    --accumulator[1];
                }
                if (addElement!=null) {
                    accumulator[0]+=addElement;
                    ++accumulator[1];
                }
            }

            @Override
            public Double compute(double[] accumulator) {
                return accumulator[1]>0?accumulator[0]/accumulator[1]:defaultValue;
            }
        };
    }
    
    public static <E, A, R> List<R> performSlide(List<E> list, int halfWindow, SlidingOperator<E, A, R> operator) {
        if (list.isEmpty()) return Collections.EMPTY_LIST;
        A acc = operator.instanciateAccumulator();
        List<R> res = new ArrayList<>(list.size());
        if (list.size()<2*halfWindow+1) {
            for (int i = 0; i<list.size(); ++i) operator.slide(null, list.get(i), acc);
            R start = operator.compute(acc);
            for (int i = 0; i<list.size(); ++i) res.add(start);
            return res;
        }
        for (int i = 0; i<=2*halfWindow; ++i) operator.slide(null, list.get(i), acc);
        R start = operator.compute(acc);
        for (int i = 0; i<=halfWindow; ++i) res.add(start);
        for (int i = halfWindow+1; i<list.size()-halfWindow; ++i) {
            operator.slide(list.get(i-halfWindow-1), list.get(i+halfWindow), acc);
            res.add(operator.compute(acc));
        }
        R end = res.get(res.size()-1);
        for (int i = list.size()-halfWindow; i<list.size(); ++i) res.add(end);
        return res;
    }
    /*public static <T, A, R> R[] slideArray(T[] list, int halfWindow, SlidingOperator<T, A, R> operator) {
        R[] res = (R[])new Object[list.length];
        if (list.length==0) return res;
        if (list.length<2*halfWindow+1) halfWindow = (list.length-1)/2;
        A acc = operator.instanciateAccumulator();
        
        for (int i = 0; i<=2*halfWindow; ++i) operator.slide(null, list.get(i), acc);
        R start = operator.compute(acc);
        for (int i = 0; i<=halfWindow; ++i) res.add(start);
        for (int i = halfWindow+1; i<list.size()-halfWindow; ++i) {
            operator.slide(list.get(i-halfWindow-1), list.get(i+halfWindow), acc);
            res.add(operator.compute(acc));
        }
        R end = res.get(res.size()-1);
        for (int i = list.size()-halfWindow; i<list.size(); ++i) res.add(end);
        return res;
    }*/
}
