/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.image;

import boa.image.processing.ImageOperations;
import static boa.image.Image.logger;
import static boa.utils.Utils.parallele;
import ij.gui.Plot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 *
 * @author Jean Ollion
 */
public class Histogram {

    public final int[] data;
    public final double  min;
    public final double binSize;
    public Histogram(int[] data, double[] minAndMax) {
        this.data = data;
        this.min = minAndMax[0];
        binSize = ( minAndMax[1] - minAndMax[0]) / (double)data.length;
    }
    public Histogram(int[] data, double binSize, double min) {
        this.data = data;
        this.binSize = binSize;
        this.min=min;
    }
    public Histogram duplicate() {
        return duplicate(0, data.length);
    }
    public double getMaxValue() {
        return getValueFromIdx(getMaxNonNullIdx()+binSize/2);
    }
    public Histogram duplicate(int fromIdxIncluded, int toIdxExcluded) {
        int[] dup = new int[data.length];
        System.arraycopy(data, fromIdxIncluded, dup, fromIdxIncluded, toIdxExcluded-fromIdxIncluded);
        return new Histogram(dup, binSize, min);
    }
    
    public void add(Histogram other) {
        for (int i = 0; i < data.length; ++i) data[i]+=other.data[i];
    }
    public void remove(Histogram other) {
        for (int i = 0; i < data.length; ++i) data[i]-=other.data[i];
    }
    
    public double getValueFromIdx(double idx) {
        return idx * binSize + min;
    }
    public double getIdxFromValue(double value) {
        if (value<=min) return 0;
        int idx = (int) Math.round((value - min) / binSize );
        if (idx>=data.length) return data.length-1;
        return idx;
    }
    public double getMeanIdx(int fromIncluded, int toExcluded) {
        double count = 0;
        double meanIdx = 0;
        for (int i = fromIncluded; i<toExcluded; ++i) {
            meanIdx+=data[i] * i;
            count+=data[i];
        }
        return meanIdx/count;
    }
    public int count(int fromIncluded, int toExcluded) {
        int count = 0;
        for (int i = fromIncluded; i<toExcluded; ++i) count+=data[i];
        return count;
    }
    public int count() {
        int sum = 0;
        for (int i : data) sum+=i;
        return sum;
    }
    public int getMaxNonNullIdx() {
        int i = data.length-1;
        if (data[i]==0) while(i>0 && data[i-1]==0) --i;
        return i;
    }
    public int getMinNonNullIdx() {
        int i = 0;
        if (data[i]==0) while(i<data.length-1 && data[i+1]==0) ++i;
        return i;
    }
    public void removeSaturatingValue(double countThlFactor, boolean highValues) {
        if (highValues) {
            int i = getMaxNonNullIdx();
            if (i>0) {
                //logger.debug("remove saturating value: {} (prev: {}, i: {})", data[i], data[i-1], i);
                if (data[i]>data[i-1]*countThlFactor) data[i]=0;
            }
        } else {
            int i = getMinNonNullIdx();
            if (i<data.length-1) {
                //logger.debug("remove saturating value: {} (prev: {}, i: {})", data[i], data[i-1], i);
                if (data[i]>data[i+1]*countThlFactor) data[i]=0;
            }
        }
    }
    public double[] getQuantiles(double... quantile) {
        int gcount = 0;
        for (int i : data) gcount += i;
        double[] res = new double[quantile.length];
        for (int i = 0; i<res.length; ++i) {
            int count = gcount;
            double limit = count * (1-quantile[i]); // 1- ?
            if (limit >= count) {
                res[i] = min;
                continue;
            }
            count = data[data.length-1];
            int idx = data.length-1;
            while (count < limit && idx > 0) {
                idx--;
                count += data[idx];
            }
            double idxInc = (data[idx] != 0) ? (count - limit) / (data[idx]) : 0; //lin approx
            res[i] = getValueFromIdx(idx + idxInc);
        }
        return res;
    }
    public double getCountLinearApprox(double histoIdx) {
        if (histoIdx<=0) return data[0];
        if (histoIdx>=data.length-1) return data[data.length-1];
        int idx = (int)histoIdx;
        return data[idx] + (data[idx+1]-data[idx]) * (histoIdx-idx);
    }
    public void eraseRange(int fromIncluded, int toExcluded) {
        Arrays.fill(data, fromIncluded, toExcluded, 0);
    }
    /**
     * 
     * @param title
     * @param xValues if false: histogram index will be displayed in x axis
     */
    public void plotIJ1(String title, boolean xValues) {
        float[] values = new float[data.length];
        float[] x = new float[values.length];
        for (int i = 0; i<values.length; ++i) {
            values[i] = data[i];
            x[i] = xValues ? (float)getValueFromIdx(i) : i;
        }
        new Plot(title, "value", "count", x, values).show();
    }
}
