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

import image.Image;
import image.ImageByte;
import java.util.Arrays;
import java.util.List;
import plugins.plugins.thresholders.SigmaMuThresholder;
import utils.ArrayUtil;

/**
 *
 * @author jollion
 */
public class ThresholdSigmaMu extends Threshold {
    ThresholdHisto thld;
    double[] thresholdF;
    double[] thldCoeffY;
    
    
    public ThresholdSigmaMu(List<Image> planes) {
        super(planes);
        this.thld = new ThresholdHisto(planes);
    }
    @Override
    public void setFrameRange(int[] frameRange) {
        
    }

    @Override public double getThreshold() {
        return thld.getThreshold();
    }
    @Override public double getThreshold(int frame) {
        if (thresholdF==null) return thld.getThreshold();
        else return thresholdF[frame];
    }
    @Override public double getThreshold(int frame, int y) {
        if (thldCoeffY==null) return getThreshold(frame);
        return getThreshold(frame) * thldCoeffY[y];
    }

    @Override
    public void setAdaptativeByY(int yHalfWindow) {
        List<double[]> stats = Arrays.asList(SigmaMuThresholder.getStatistics(planes, true, thld.getThreshold(), false, true));
        thldCoeffY = getSlidingMean(stats, yHalfWindow);
        for (int i = 0; i<thldCoeffY.length; ++i) thldCoeffY[i]/=getThreshold();
    }

    @Override
    public void setAdaptativeThreshold(double adaptativeCoefficient, int adaptativeThresholdHalfWindow) {
        List<double[]> stats = Arrays.asList(SigmaMuThresholder.getStatistics(planes, false, thld.getThreshold(), false, true));
        thresholdF = getSlidingMean(stats, adaptativeThresholdHalfWindow);
        if (adaptativeCoefficient<1) for (int i = 0; i<thldCoeffY.length; ++i) thldCoeffY[i] = thldCoeffY[i] * adaptativeCoefficient + getThreshold() * (1-adaptativeCoefficient);
    }
    private static double[] getSlidingMean(List<double[]> stats, int halfWindow) {
        List<Double> res = slide(stats, halfWindow, new SlidingOperator<double[], double[], Double>() {
            @Override
            public double[] instanciateAccumulator() {
                return new double[2];
            }
            @Override
            public void slide(double[] removeElement, double[] addElement, double[] accumulator) {
                if (removeElement!=null) {
                    accumulator[0]-=removeElement[0];
                    accumulator[1]-=removeElement[2];
                }
                if (addElement!=null) {
                    accumulator[0]+=addElement[0];
                    accumulator[1]+=addElement[2];
                }
            }

            @Override
            public Double compute(double[] accumulator) {
                return accumulator[0] / accumulator[1];
            }
        });
        return ArrayUtil.toPrimitive(res);
    }
    
    
}
