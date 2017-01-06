/*
 * Copyright (C) 2016 jollion
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
package plugins.plugins.trackers.bacteriaInMicrochannelTracker;

import ij.process.AutoThresholder;
import image.BoundingBox;
import image.BoundingBox.LoopFunction;
import image.Image;
import image.ImageByte;
import image.ImageOperations;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.apache.commons.lang.ArrayUtils;
import static plugins.Plugin.logger;
import plugins.plugins.thresholders.IJAutoThresholder;
import plugins.plugins.thresholders.Percentage;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.adaptativeThresholdHalfWindow;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.debug;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.debugCorr;
import utils.ArrayUtil;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class ThresholdHisto extends Threshold {

    double thresholdValue;
    final double saturateValue;
    int saturateValue256;
    double minAndMax[];
    final boolean byteHisto;
    double[] thresholdF;
    double[] thldCoeffY;
    final List<int[]> histos;
    int[] frameRange;
    final static AutoThresholder.Method thldMethod = AutoThresholder.Method.Otsu;
    final static AutoThresholder.Method saturateMethod = AutoThresholder.Method.Shanbhag; // Avec sub: shanbhag. Pas de sub: : MaxEntropy / Triangle
    final static double maxSaturationProportion = 0.03;
    @Override public double getThreshold() {
        return thresholdValue;
    }
    @Override public double getThreshold(int frame) {
        if (thresholdF==null) return thresholdValue;
        else return thresholdF[frame];
    }
    @Override public double getThreshold(int frame, int y) {
        if (thldCoeffY==null) return getThreshold(frame);
        return getThreshold(frame) * thldCoeffY[y];
    }
    
    public ThresholdHisto(List<Image> planes) {
        super(planes);
        this.frameRange=new int[]{0, planes.size()-1};
        byteHisto = planes.get(0) instanceof ImageByte;
        long t0 = System.currentTimeMillis();
        minAndMax = new double[2];
        histos = ImageOperations.getHisto256AsList(planes, minAndMax);
        long t1 = System.currentTimeMillis();
        int[] histoAll = new int[256];
        for (int[] h : histos) ImageOperations.addHisto(h, histoAll, false);
        // saturate histogram to remove device aberations 
        saturateValue256 = (int)IJAutoThresholder.runThresholder(saturateMethod, histoAll, minAndMax, true); // byteImage=true to get a 8-bit value
        // limit to saturagePercentage
        int satPer = Percentage.getBinAtPercentage(histoAll, maxSaturationProportion);
        if (satPer>saturateValue256) saturateValue256 = satPer;
        saturateValue =  byteHisto ? saturateValue256 : IJAutoThresholder.convertHisto256Threshold(saturateValue256, minAndMax);
        for (int i = saturateValue256; i<256; ++i) histoAll[i]=0;
        thresholdValue = IJAutoThresholder.runThresholder(thldMethod, histoAll, minAndMax, byteHisto);
        long t2 = System.currentTimeMillis();
        if (debug || debugCorr) logger.debug("Threshold Value over all time: {}, minAndMax: {}, byte?{}, saturate value: {} ({})", thresholdValue, minAndMax, byteHisto, saturateValue256, saturateValue);
        if (debug || debugCorr) logger.debug("getHistos: {}ms, compute 1st thld: {}ms", t1-t0, t2-t1);
    }
    @Override public void setFrameRange(int[] frameRange) { // will recompute threshold value, min&max, saturateValue
        if (frameRange==null) return;
        this.frameRange=frameRange;
        if (debug || debugCorr) logger.debug("set frame range: sat: {}, fr: {}", saturateValue256, frameRange);
        if (saturateValue256<255 || frameRange[0]>0 || frameRange[1]<planes.size()-1) { // update min and max if necessary
            List<Image> planesSub = planes.subList(frameRange[0], frameRange[1]+1);
            double[] minAndMaxNew = ImageOperations.getMinAndMax(planesSub);
            if (saturateValue<minAndMaxNew[1]) minAndMaxNew[1] = saturateValue;
            if (debug || debugCorr) logger.debug("set frame range, old mm: {}, new mm: {}", minAndMax, minAndMaxNew);
            if (minAndMaxNew[0]!=minAndMax[0] || minAndMaxNew[1]!=minAndMax[1]) {
                minAndMax = minAndMaxNew;
                List<int[]> histosSub = ImageOperations.getHisto256AsList(planesSub, minAndMax);
                for (int i = 0; i<histosSub.size(); ++i) {
                    saturateHistogram(histosSub.get(i));
                    histos.set(i+frameRange[0], histosSub.get(i)); // replace new histograms
                }
                int[] histoAll = new int[256];
                for (int[] h : histosSub) ImageOperations.addHisto(h, histoAll, true);
                thresholdValue = IJAutoThresholder.runThresholder(thldMethod, histoAll, minAndMax, byteHisto);
                if (debug || debugCorr) logger.debug("new threshold value: {}", thresholdValue);
            }
        }
    }
    @Override public void setAdaptativeThreshold(double adaptativeCoefficient, int adaptativeThresholdHalfWindow) {
        long t4 = System.currentTimeMillis();
        // adaptative threhsold on sliding window histo mean
        if (adaptativeThresholdHalfWindow*2 < frameRange[1] - frameRange[0] ) {
            List<Double> slide = Threshold.slide(histos.subList(frameRange[0], frameRange[1]+1), adaptativeThresholdHalfWindow, new SlidingOperator<int[], int[], Double>() {
                @Override
                public int[] instanciateAccumulator() {
                    return new int[256];
                }
                @Override
                public void slide(int[] removeElement, int[] addElement, int[] accumulator) {
                    if (removeElement!=null) ImageOperations.addHisto(removeElement, accumulator, true);
                    if (addElement!=null) ImageOperations.addHisto(addElement, accumulator, false);
                }
                @Override
                public Double compute(int[] accumulator) {
                    double thld = IJAutoThresholder.runThresholder(thldMethod, accumulator, minAndMax, byteHisto);
                    return adaptativeCoefficient * thld + (1-adaptativeCoefficient) * thresholdValue;
                }
            });
            if (frameRange[0]>0) {  // slide indices to initial range
                Double[] start = new Double[frameRange[0]];
                Arrays.fill(start, slide.get(0));
                slide.addAll(0, Arrays.asList(start));
            }
            thresholdF = ArrayUtil.toPrimitive(slide);
            
            long t5 = System.currentTimeMillis();
            if (debug || debugCorr) {
                logger.debug("framewindow: {}, subList: {}, thlds length: {}", frameRange, histos.subList(frameRange[0], frameRange[1]+1).size(), slide.size());
                Utils.plotProfile("Thresholds", thresholdF);
                logger.debug("compute threshold window: {}ms", t5-t4);
            }
        } 
    }
    
    private void saturateHistogram(int[] histo256) {
        if (byteHisto) for (int i = saturateValue256; i<256; ++i) histo256[i] = 0;
        else histo256[255] = 0; // assumes has been computed with maxValue = saturateValue
    }
    
    @Override public void setAdaptativeByY(int yHalfWindow) {
        long t1 = System.currentTimeMillis();
        double yMean = 0;
        int yMax = 0;
        for (Image i : planes) {
            yMean+=i.getSizeY();
            if (i.getSizeY()>yMax) yMax = i.getSizeY();
        }
        yMean/=planes.size();
        double[] coeffs = new double[(int)(yMean / yHalfWindow)+1];
        for (int i = 1; i<coeffs.length-1; ++i) coeffs[i] = getThld((i-1) * yHalfWindow, (i+1)*yHalfWindow) / thresholdValue;
        coeffs[0] = coeffs[1];
        coeffs[coeffs.length-1]=getThld((coeffs.length-2)*yHalfWindow, yMax) / thresholdValue;
        thldCoeffY = interpolate(coeffs, yHalfWindow, yMax);
        long t2 = System.currentTimeMillis();
        if (debug || debugCorr) {
            Utils.plotProfile("Threshold Coeff Y", thldCoeffY);
            logger.debug("compute threshold coeff by Y: {}ms", t2-t1);
        }
    }
    private double getThld(int yMin, int yMax) {
        int[] histo = new int[256];
        for (Image i : planes.subList(frameRange[0], frameRange[1]+1)) {
            int[] h = i.getHisto256(minAndMax[0], minAndMax[1], null, new BoundingBox(0, i.getSizeX()-1, yMin, Math.min(i.getSizeY()-1, yMax), 0, i.getSizeZ()-1));
            saturateHistogram(h);
            for (int j = 0; j < 256; ++j) histo[j] += h[j];   
        }
        return IJAutoThresholder.runThresholder(thldMethod, histo, minAndMax, byteHisto);
    }

    @Override
    public int[] getFrameRange() {
        return this.frameRange;
    }

    @Override
    public void freeMemory() {
        
    }
    
    
    
}
