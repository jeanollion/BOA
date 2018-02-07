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
package boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker;

import boa.data_structure.StructureObject;
import ij.process.AutoThresholder;
import boa.image.BoundingBox;
import boa.image.Histogram;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.processing.ImageOperations;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.apache.commons.lang.ArrayUtils;
import static boa.plugins.Plugin.logger;
import boa.plugins.Segmenter;
import boa.plugins.plugins.thresholders.IJAutoThresholder;
import static boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker.BacteriaClosedMicrochannelTrackerLocalCorrections.debug;
import static boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker.BacteriaClosedMicrochannelTrackerLocalCorrections.debugCorr;
import boa.utils.ArrayUtil;
import boa.utils.SlidingOperator;
import static boa.utils.SlidingOperator.performSlide;
import boa.utils.Utils;
import boa.plugins.OverridableThresholdMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 *
 * @author jollion
 */
public class ThresholdHisto extends Threshold {

    double thresholdValue;
    final double saturateValue;
    int saturateValue256;
    double[] thresholdF;
    double[] thldCoeffY;
    final Map<Image, Histogram> histos;
    Histogram histoAll;
    final AutoThresholder.Method thldMethod;
    //final static AutoThresholder.Method saturateMethod = AutoThresholder.Method.Shanbhag; // Avec sub: shanbhag. Pas de sub: : MaxEntropy / Triangle
    final static double maxSaturationProportion = 0.03;
    @Override public double getThreshold() {
        return thresholdValue;
    }
    @Override public double getThreshold(int frame) {
        if (thresholdF==null) return thresholdValue;
        else return thresholdF[frame-offsetFrame];
    }
    @Override public double getThreshold(int frame, int y) {
        if (thldCoeffY==null) return getThreshold(frame);
        return getThreshold(frame) * thldCoeffY[y];
    }
    
    public ThresholdHisto(TreeMap<StructureObject, Image> planes, int offsetFrame, AutoThresholder.Method method, AutoThresholder.Method saturateMethod) {
        super(planes, offsetFrame);
        thldMethod = method;
        this.frameRange=new int[]{0, planes.size()-1};
        long t0 = System.currentTimeMillis();
        double[] minAndMax = new double[2];
        histos = Histogram.getHistoAll256(maskMap, minAndMax);
        long t1 = System.currentTimeMillis();
        Iterator<Histogram> it = histos.values().iterator();
        histoAll = it.next().duplicate();
        while(it.hasNext()) histoAll.add(it.next());
        if (saturateMethod!=null) { // saturate histogram to remove device aberations 
            double sv = IJAutoThresholder.runThresholder(saturateMethod, histoAll); // byteImage=true to get a 8-bit value
            saturateValue256 = (int)histoAll.getIdxFromValue(sv);
            // limit to saturagePercentage
            double sat = histoAll.getQuantiles(1-maxSaturationProportion)[0];
            double satPer = histoAll.getIdxFromValue(sat);
            if (satPer>saturateValue256) saturateValue256 = (int)satPer;
            saturateValue =  histoAll.getValueFromIdx(saturateValue256);
            for (int i = saturateValue256; i<256; ++i) histoAll.data[i]=0;
            histoAll.minAndMax[1] = saturateValue;
        } else {
            saturateValue = Double.NaN;
            saturateValue256=255;
        }
        thresholdValue = IJAutoThresholder.runThresholder(thldMethod, histoAll);
        long t2 = System.currentTimeMillis();
        if (debug || debugCorr) logger.debug("Threshold Value over all time: {}, minAndMax: {}, byte?{}, saturate value: {} ({})", thresholdValue, histoAll.minAndMax, histoAll.byteHisto, saturateValue256, saturateValue);
        if (debug || debugCorr) logger.debug("getHistos: {}ms, compute 1st thld: {}ms", t1-t0, t2-t1);
    }
    @Override public void setFrameRange(int[] fr) { // will recompute threshold value, min&max, saturateValue
        if (fr==null) return;
        frameRange=new int[]{fr[0]-offsetFrame, fr[1]-offsetFrame};
        if (debug || debugCorr) logger.debug("set frame range: sat: {}, fr: {}", saturateValue256, frameRange);
        if (saturateValue256<255 || frameRange[0]>0 || frameRange[1]<planes.size()-1) { // update min and max if necessary
            Map<StructureObject, Image> planesSub = planes.entrySet().stream().filter(e->e.getKey().getFrame()<fr[0]||e.getKey().getFrame()>fr[1]).collect(Collectors.toMap(e->e.getKey(), e->e.getValue()));
            double[] minAndMaxNew = ImageOperations.getMinAndMax(planesSub.values());
            if (!Double.isNaN(saturateValue) && saturateValue<minAndMaxNew[1]) minAndMaxNew[1] = saturateValue;
            if (debug || debugCorr) logger.debug("set frame range, old mm: {}, new mm: {}", histoAll.minAndMax, minAndMaxNew);
            if (minAndMaxNew[0]!=histoAll.minAndMax[0] || minAndMaxNew[1]!=histoAll.minAndMax[1]) {
                
                List<Histogram> histosSub = Histogram.getHisto256AsList(planesSub, minAndMaxNew);
                for (int i = 0; i<histosSub.size(); ++i) {
                    if (!Double.isNaN(saturateValue)) saturateHistogram(histosSub.get(i));
                    histos.set(i+frameRange[0], histosSub.get(i)); // replace new histograms
                }
                Iterator<Histogram> it = histosSub.iterator();
                histoAll = it.next().duplicate();
                while(it.hasNext()) histoAll.add(it.next());
                thresholdValue = IJAutoThresholder.runThresholder(thldMethod, histoAll);
                if (debug || debugCorr) logger.debug("new threshold value: {}", thresholdValue);
            }
        }
    }
    @Override public void setAdaptativeThreshold(double adaptativeCoefficient, int adaptativeThresholdHalfWindow) {
        long t4 = System.currentTimeMillis();
        // adaptative threhsold on sliding window histo mean
        if (adaptativeThresholdHalfWindow*2 < frameRange[1] - frameRange[0] ) {
            List<Double> slide = performSlide(histos.subList(frameRange[0], frameRange[1]+1), adaptativeThresholdHalfWindow, new SlidingOperator<Histogram, Histogram, Double>() {
                @Override
                public Histogram instanciateAccumulator() {
                    return new Histogram(new int[256], histoAll.byteHisto, histoAll.minAndMax);
                }
                @Override
                public void slide(Histogram removeElement, Histogram addElement, Histogram accumulator) {
                    if (removeElement!=null) accumulator.remove(removeElement);
                    if (addElement!=null) accumulator.add(addElement);
                }
                @Override
                public Double compute(Histogram accumulator) {
                    double thld = IJAutoThresholder.runThresholder(thldMethod, accumulator);
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
    public void unsetAdaptativeByF() {
        this.thresholdF=null;
    }
    private void saturateHistogram(Histogram histo256) {
        if (histo256.byteHisto) for (int i = saturateValue256; i<256; ++i) histo256.data[i] = 0;
        else histo256.data[255] = 0; // assumes has been computed with maxValue = saturateValue
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
        Histogram histo=null;
        for (Image i : planes.subList(frameRange[0], frameRange[1]+1)) {
            Histogram h = i.getHisto256(histoAll.minAndMax[0], histoAll.minAndMax[1], null, new BoundingBox(0, i.getSizeX()-1, yMin, Math.min(i.getSizeY()-1, yMax), 0, i.getSizeZ()-1));
            saturateHistogram(h);
            if (histo==null) histo = h;
            else histo.add(h);
        }
        return IJAutoThresholder.runThresholder(thldMethod, histo);
    }

    @Override
    public void freeMemory() {
        histos.clear();
    }

    @Override
    public boolean hasAdaptativeByY() {
        return this.thldCoeffY!=null;
    }

    @Override
    public void apply(StructureObject o, Segmenter s) {
        if (!(s instanceof OverridableThresholdMap)) return;
        if (hasAdaptativeByY()) ((OverridableThresholdMap)s).setThresholdedImage(getThresholdedPlane(o.getFrame(), false));
        else ((OverridableThresholdMap)s).setThresholdValue(getThreshold(o.getFrame()));
    }
    
}
