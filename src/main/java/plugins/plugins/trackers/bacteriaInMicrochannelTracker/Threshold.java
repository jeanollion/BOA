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
import image.Image;
import image.ImageByte;
import image.ImageOperations;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import static plugins.Plugin.logger;
import plugins.plugins.thresholders.IJAutoThresholder;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.adaptativeThresholdHalfWindow;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.debug;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.debugCorr;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class Threshold {

    double thresholdValue;
    final double saturateValue;
    final int saturateValue256;
    double minAndMax[];
    final boolean byteHisto;
    double[] thresholdF;
    BiFunction<Integer, Integer, Double> getTimeYThreshold;
    final List<int[]> histos;
    final List<Image> planes;
    int[] frameRange;
    public double getThreshold(int frame) {
        if (thresholdF==null) return thresholdValue;
        else return thresholdF[frame];
    }
    public double getThreshold(int frame, int y) {
        if (getTimeYThreshold==null) return getThreshold(frame);
        return getTimeYThreshold.apply(frame, y);
    }
    
    
    public Threshold(List<Image> planes) {
        this.planes=planes;
        this.frameRange=new int[]{0, planes.size()-1};
        byteHisto = planes.get(0) instanceof ImageByte;
        long t0 = System.currentTimeMillis();
        minAndMax = new double[2];
        histos = ImageOperations.getHisto256AsList(planes, minAndMax);
        long t1 = System.currentTimeMillis();
        int[] histoAll = new int[256];
        for (int[] h : histos) ImageOperations.addHisto(h, histoAll, true);
        // saturate histogram to remove device aberations 
        saturateValue256 = (int)IJAutoThresholder.runThresholder(AutoThresholder.Method.MaxEntropy, histoAll, minAndMax, true); // byteImage=true to get a 8-bit value
        saturateValue =  byteHisto ? saturateValue256 : IJAutoThresholder.convertHisto256Threshold(saturateValue256, minAndMax);
        for (int i = saturateValue256; i<256; ++i) histoAll[i]=0;
        thresholdValue = IJAutoThresholder.runThresholder(AutoThresholder.Method.Otsu, histoAll, minAndMax, byteHisto);
        long t2 = System.currentTimeMillis();
        if (debug || debugCorr) logger.debug("Threshold Value over all time: {}, minAndMax: {}, byte?{}, saturate value: {} ()", thresholdValue, minAndMax, byteHisto, saturateValue256, saturateValue);
        if (debug || debugCorr) logger.debug("getHistos: {}ms, compute 1st thld: {}ms", t1-t0, t2-t1);
    }
    public void setFrameRange(int[] frameRange) {
        if (frameRange==null) return;
        this.frameRange=frameRange;
        if (saturateValue256<255 || frameRange[0]>0 || frameRange[1]<planes.size()-1) { // update min and max if necessary
            List<Image> planesSub = planes.subList(frameRange[0], frameRange[1]+1);
            double[] minAndMaxNew = ImageOperations.getMinAndMax(planesSub);
            minAndMaxNew[1] = saturateValue;
            if (minAndMaxNew[0]!=minAndMax[0] || minAndMaxNew[1]!=minAndMax[1]) {
                minAndMax = minAndMaxNew;
                List<int[]> histosSub = ImageOperations.getHisto256AsList(planesSub, minAndMax);
                for (int i = 0; i<histosSub.size(); ++i) {
                    saturateHistogram(histosSub.get(i));
                    histos.set(i+frameRange[0], histosSub.get(i)); // replace new histograms
                }
                int[] histoAll = new int[256];
                for (int[] h : histosSub) ImageOperations.addHisto(h, histoAll, true);
                thresholdValue = IJAutoThresholder.runThresholder(AutoThresholder.Method.Otsu, histoAll, minAndMax, byteHisto);
                if (debug || debugCorr) logger.debug("new threshold value: {}", thresholdValue);
            }
        }
    }
    public void setAdaptativeThreshold(double adaptativeCoefficient, int adaptativeThresholdHalfWindow) {
        //SigmaMuThresholder.getStatistics(planes, 20, thresholdValue, true);
        //if (true) return;
        
        long t4 = System.currentTimeMillis();
        // adaptative threhsold on sliding window histo mean
        if (adaptativeThresholdHalfWindow*2 < frameRange[1] - frameRange[0] ) {
            thresholdF = new double[planes.size()];
            int fMin = frameRange[0]; 
            int fMax = fMin+2*adaptativeThresholdHalfWindow;
            int[] histo = new int[256];
            for (int f = fMin; f<=fMax; ++f) ImageOperations.addHisto(histos.get(f), histo, true);
            //for (int i = higthThldLimit256; i<256; ++i) histo[i]=0; // saturate histogram to remove device aberations 
            double t = (1-adaptativeCoefficient) * thresholdValue + adaptativeCoefficient * IJAutoThresholder.runThresholder(AutoThresholder.Method.Otsu, histo, minAndMax, planes.get(0) instanceof ImageByte);
            for (int f = fMin; f<=fMin+adaptativeThresholdHalfWindow; ++f) thresholdF[f] = t; // this histo is valid until fMin + window
            while (fMax<frameRange[1]) {
                ++fMax;
                ImageOperations.addHisto(histos.get(fMax), histo, true); 
                ImageOperations.addHisto(histos.get(fMin), histo, false);
                //for (int i = higthThldLimit256; i<256; ++i) histo[i]=0; // saturate histogram to remove device aberations 
                ++fMin;
                thresholdF[fMin+adaptativeThresholdHalfWindow] = IJAutoThresholder.runThresholder(AutoThresholder.Method.Otsu, histo, minAndMax, planes.get(0) instanceof ImageByte);
                thresholdF[fMin+adaptativeThresholdHalfWindow] = adaptativeCoefficient * thresholdF[fMin+adaptativeThresholdHalfWindow] + (1-adaptativeCoefficient) * thresholdValue;
            }
            for (int f = fMin+adaptativeThresholdHalfWindow+1; f<=frameRange[1]; ++f) thresholdF[f] = thresholdF[fMin+adaptativeThresholdHalfWindow]; // this histo is valid until last frame
            long t5 = System.currentTimeMillis();
            if (debug || debugCorr) {
                logger.debug("framewindow: {}", frameRange);
                Utils.plotProfile("Thresholds", thresholdF);
                logger.debug("compute threshold window: {}ms", t5-t4);
            }
        } 
    }
    
    private void saturateHistogram(int[] histo256) {
        if (byteHisto) for (int i = saturateValue256; i<256; ++i) histo256[i] = 0;
        else histo256[255] = 0; // assumes has been computed with maxValue = saturateValue
    }
    
    /*public void setAdaptativeByY(int yHalfWindow) {
        
    }*/
    
}
