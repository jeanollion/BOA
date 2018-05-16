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

import boa.image.Histogram;
import boa.utils.ArrayUtil;
import boa.utils.Utils;
import ij.gui.Plot;
import ij.process.AutoThresholder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Jean Ollion
 */
public class HistogramAnalyzer {
    public static final Logger logger = LoggerFactory.getLogger(HistogramAnalyzer.class);
    private final Histogram histo;
    private final int scale;
    private final float[] smooth, der2;
    private final boolean log;
    private final List<Integer> peakDer2Max, peakDer2Min;
    private final List<Range> foregroundRanges;
    private final Range backgroundRange;
    public HistogramAnalyzer(Histogram histo, int scale, boolean log) {
        this.histo= histo;
        this.scale = scale;
        this.log=log;
        smooth = getHistoValuesAsFloat();
        smooth(smooth, scale);
        if (log) log(smooth);
        der2 = ArrayUtil.getDerivative(smooth, scale, 2, false);
        for (int i =0; i<der2.length; ++i) der2[i] = -der2[i];
        peakDer2Max = ArrayUtil.getRegionalExtrema(der2, scale, true);
        peakDer2Min = ArrayUtil.getRegionalExtrema(der2, scale, false);
        
        // among 3 most intense peaks -> the first in
        int peak = this.peakDer2Max.stream().sorted(peakComparator(true)).limit(3).mapToInt(i->i).min().getAsInt();
        // first zero after the negative peak ? 

        backgroundRange = new Range(0, getNextZero(peak+1, der2.length, peak));
        peakDer2Max.removeIf(i->i<peak); // remove peaks before bck
        foregroundRanges = new ArrayList<>();
        int iPrev = 1;
        while (iPrev < peakDer2Max.size()) {
            int iCur = iPrev;
            while(iCur<peakDer2Max.size()-1 && getNextZero(peakDer2Max.get(iPrev), peakDer2Max.get(iCur+1), -1)<0) ++iCur;
            foregroundRanges.add(new Range(
                    getNextLocalMin(peakDer2Max.get(iPrev), peakDer2Max.get(iPrev-1), peakDer2Max.get(iPrev)), 
                    getNextLocalMin(peakDer2Max.get(iCur), iCur<peakDer2Max.size()-1 ? peakDer2Max.get(iCur+1): der2.length, peakDer2Max.get(iCur))
            ));
            iPrev=iCur+1;
        }
        logger.debug("peaks: {}, bck: {}, ranges: {}", peakDer2Max, backgroundRange,  foregroundRanges);
    }
    protected int getNextZero(int fromIdx, int toIdx, int defaultValue) {
        if (toIdx>=fromIdx) return IntStream.range(fromIdx, toIdx).filter(crossZero()).min().orElse(defaultValue);
        else return IntStream.iterate(fromIdx, e->e-1).limit(fromIdx-toIdx).filter(crossZero()).findFirst().orElse(defaultValue);
    }
    protected int getNextLocalMin(int fromIdx, int toIdx, int defaultValue) {
        if (toIdx>=fromIdx) return peakDer2Min.stream().filter(i->i>=fromIdx && i<=toIdx).findFirst().orElse(defaultValue);
        else return peakDer2Min.stream().filter(i->i<=fromIdx && i>=toIdx).mapToInt(i->i).max().orElse(defaultValue);
    }
    protected IntPredicate crossZero() {
        return i -> der2[i]== 0 || (i<der2.length-1 && (der2[i]*der2[i+1])<=0);
    }
    /**
     * 
     * @return array of histogram coordinate, first = peak, second = end of peak
     */
    public Range getBackgroundRange() {
        return backgroundRange;
    }
    public Range getForegroundRange() {
        return this.foregroundRanges.stream().max((r1, r2)->Double.compare(r1.count(), r2.count())).orElse(null);
    }
    public double getSaturationThreshold(double valueRatioThreshold, double maxAmountRatioThrehsold) {
        Range fore = getForegroundRange();
        if (fore ==null) return Double.NaN;
        double foreIdx = fore.meanIdx();
        double bckIdx = this.backgroundRange.meanIdx();
        double minPeakIdx =  (foreIdx - bckIdx) * valueRatioThreshold + bckIdx;
        Range satRange = this.foregroundRanges.stream().filter(r->r.max>minPeakIdx).filter(r->r.min>minPeakIdx || r.meanIdx()>minPeakIdx).max((r1, r2)->Double.compare(r1.count(), r2.count())).orElse(null);
        if (satRange==null) return Double.NaN;
        // signal amount condition1
        if (new Range(satRange.min, histo.data.length-1).count() * maxAmountRatioThrehsold > fore.count()) return Double.NaN; // sum all other foreground count ? 
        return getThldRange(fore.min, smooth.length);
    }
    public List<Range> getMainForegroundRanges(int limit) {
        return foregroundRanges.stream().sorted((r1, r2)-> -Double.compare(r1.count(), r2.count())).limit(limit).sorted((r1, r2)->Integer.compare(r1.min, r2.min)).collect(Collectors.toList());
    }
    public double getThresholdMultimodal(int modeNumber) {
        List<Range> foreRanges =  getMainForegroundRanges(modeNumber);
        if (foreRanges.size()<2) return IJAutoThresholder.runThresholder(AutoThresholder.Method.Otsu, histo);
        // get threshold between background and first foreground peak
        logger.debug("multimodal thld: non bck peaks: {}, range: [0;{}]", foreRanges, foreRanges.get(0).max);
        return getThldRange(0, foreRanges.get(0).max);
    }
    public double count(int fromIncluded, int toExcluded) {
        double s = 0;
        for (int i = fromIncluded; i<toExcluded; ++i) s+=histo.data[i];
        return s;
    }
    protected Comparator<Integer> peakComparator(boolean highestPeakFirst) {
        if (highestPeakFirst) return (p1, p2) -> -Float.compare(der2[p1], der2[p2]);
        else return (p1, p2) -> Float.compare(der2[p1], der2[p2]);
    }
    public Histogram getHistoRange(int fromIncluded, int toExcluded) {
        int[] res = new int[histo.data.length];
        System.arraycopy(histo.data, fromIncluded, res, fromIncluded, toExcluded-fromIncluded);
        return new Histogram(res, histo.byteHisto, new double[]{histo.minAndMax[0], histo.minAndMax[1]});
    }
    public double getThldRange(int fromIncluded, int toExcluded) {
        Histogram h = getHistoRange(fromIncluded,toExcluded );
        return IJAutoThresholder.runThresholder(AutoThresholder.Method.Otsu, h);
    }
    public float[] getD2() {
        return der2;
    }
    public float[] getSmoothed() {
        return smooth;
    }
    protected float[] getHistoValuesAsFloat() {
        float[] histoValues = new float[histo.data.length];
        for (int i = 0; i<histoValues.length; ++i) histoValues[i] = histo.data[i];
        return histoValues;
    }
    public static void smooth(float[] values, double scale) {
        ArrayUtil.gaussianSmooth(values, scale);
    }
    public static void log(float[] values) {
        for (int i = 0; i<values.length; ++i) values[i] = values[i]>1 ? (float)Math.log(values[i]) : 0f;
    }
    public void plot() {
        plot("smoothed histogram"+(log?" (log)":""), smooth);
        plot("d2/dx "+(log?" (log)":""), der2);
    }
    protected void plot(String title, float[] values) {
        //Utils.plotProfile(title, values);
        float[] x = new float[values.length];
        for (int i = 0; i<x.length; ++i) x[i] = (float)histo.getValueFromIdx(i);
        new Plot(title, "value", "count", x, values).show();

    }
    public class Range {
        public final int min, max;
        public Range(int min, int max) {
            if (min>max) throw new IllegalArgumentException("Invalid range");
            this.min = min;
            this.max = max;
        }
        public double count() {
            return HistogramAnalyzer.this.count(min, max+1);
        }
        public double meanIdx() {
            double sum = 0;
            double mean = 0;
            for (int i = min; i<=max; ++i) {
                mean+=histo.data[i] * i;
                sum+=histo.data[i];
            }
            return mean/sum;
        }
        @Override
        public String toString() {
            return min!=max ? "["+histo.getValueFromIdx(min)+"("+min+");"+histo.getValueFromIdx(max)+"("+max+")]" : "["+histo.getValueFromIdx(min)+"("+min+")]";
        }
    }
}
