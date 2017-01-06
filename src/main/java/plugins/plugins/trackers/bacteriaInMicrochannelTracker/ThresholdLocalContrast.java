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
import image.ImageOperations;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import plugins.plugins.thresholders.LocalContrastThresholder;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.debug;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.debugCorr;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.logger;
import utils.ArrayUtil;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class ThresholdLocalContrast extends Threshold {
    double[] thresholdF;
    double[] thldCoeffY;
    List<double[]> thldFY;
    int[] frameRange;
    final double localContrastThreshold;
    List<Image> lcImages;
    double thld;
    public ThresholdLocalContrast(List<Image> planes, double localContrastThreshold) {
        super(planes);
        this.frameRange=new int[]{0, planes.size()-1};
        this.localContrastThreshold=localContrastThreshold;
        long t0 = System.currentTimeMillis();
        this.lcImages = new ArrayList<>(planes.size());
        for (Image i : planes) lcImages.add(LocalContrastThresholder.getLocalContrast(i, 2));
        long t1 = System.currentTimeMillis();
        final double[] stats = new double[2];
        for (int idx = 0; idx<planes.size(); ++idx) {
            Image i = planes.get(idx);
            Image lc = lcImages.get(idx);
            lc.getBoundingBox().translateToOrigin().loop((int x, int y, int z) -> {
                if (lc.getPixel(x, y, z)>localContrastThreshold) {
                    stats[0] +=i.getPixel(x, y, z);
                    ++stats[1];
                }
            });
        }
        thld = stats[1]>0 ? stats[0] / stats[1] : Double.NEGATIVE_INFINITY;
        long t2 = System.currentTimeMillis();
        if (debug || debugCorr) logger.debug("Threshold Value over all time: {}, computes images: {}ms, compute thld: {}ms", thld, t1-t0, t2-t1);
    }
    @Override
    public void setFrameRange(int[] frameRange) {
        this.frameRange=frameRange;
    }

    @Override public double getThreshold() {
        return thld;
    }
    @Override public double getThreshold(int frame) {
        if (thresholdF==null) return thld;
        else return thresholdF[frame];
    }
    @Override public double getThreshold(int frame, int y) {
        if (thldFY!=null) return thldFY.get(frame)[y];
        if (thldCoeffY==null) return getThreshold(frame);
        return getThreshold(frame) * thldCoeffY[y];
    }
    public void setAdaptativeByFY(int adaptativeThresholdHalfWindow, int yHalfWindow) {
        List<double[][]> stats = Arrays.asList(getStatisticsLocalContrast(planes, lcImages, localContrastThreshold));
        int ySize = stats.get(0)[0].length;
        long t0 = System.currentTimeMillis();
        List<double[][]> slideByF = slide(stats, adaptativeThresholdHalfWindow, new SlidingOperator<double[][], double[][], double[][]>() {
            @Override
            public double[][] instanciateAccumulator() {
                return new double[ySize][2];
            }
            @Override
            public void slide(double[][] removeElement, double[][] addElement, double[][] accumulator) {
                if (removeElement!=null) {
                    for (int y = 0; y<ySize; ++y) {
                        accumulator[y][0]-=removeElement[y][0];
                        accumulator[y][1]-=removeElement[y][1];
                    }
                }
                if (addElement!=null) {
                    for (int y = 0; y<ySize; ++y) {
                        accumulator[y][0]+=addElement[y][0];
                        accumulator[y][1]+=addElement[y][1];
                    }
                }
            }

            @Override
            public double[][] compute(double[][] accumulator) { // simple copy
                double[][] res = new double[ySize][2];
                for (int y = 0; y<ySize; ++y) {
                    res[y][0] = accumulator[y][0];
                    res[y][1] = accumulator[y][1];
                }
                return res;
            }
        });
        long t1 = System.currentTimeMillis();
        List<double[]> res = new ArrayList<>(slideByF.size());
        for (double[][] s: slideByF) {
            res.add(getSlidingMean(Arrays.asList(s), yHalfWindow));
        }
        long t2 = System.currentTimeMillis();
        if (debug || debugCorr) logger.debug("slide by Frames: {}ms, slide by y: {}ms", t1-t0, t2-t1);
    }
    @Override
    public void setAdaptativeByY(int yHalfWindow) {
        List<double[]> stats = Arrays.asList(getStatisticsLocalContrast(planes, lcImages, true, localContrastThreshold, true));
        thldCoeffY = getSlidingMean(stats, yHalfWindow);
        for (int i = 0; i<thldCoeffY.length; ++i) thldCoeffY[i]/=getThreshold();
        if (debug || debugCorr) {
            Utils.plotProfile("Threshold Coeff Y", thldCoeffY);
            //logger.debug("compute threshold coeff by Y: {}ms", t2-t1);
        }
    }

    @Override
    public void setAdaptativeThreshold(double adaptativeCoefficient, int adaptativeThresholdHalfWindow) {
        List<double[]> stats = Arrays.asList(getStatisticsLocalContrast(planes, lcImages, false, localContrastThreshold, true));
        thresholdF = getSlidingMean(stats, adaptativeThresholdHalfWindow);
        if (adaptativeCoefficient<1) for (int i = 0; i<thldCoeffY.length; ++i) thldCoeffY[i] = thldCoeffY[i] * adaptativeCoefficient + getThreshold() * (1-adaptativeCoefficient);
        if (debug || debugCorr) {
            Utils.plotProfile("Thresholdby Frame", thresholdF);
            //logger.debug("compute threshold coeff by Y: {}ms", t2-t1);
        }
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
                    accumulator[1]-=removeElement[1];
                }
                if (addElement!=null) {
                    accumulator[0]+=addElement[0];
                    accumulator[1]+=addElement[1];
                }
            }

            @Override
            public Double compute(double[] accumulator) {
                return accumulator[1]>0 ? accumulator[0] / accumulator[1] : Double.NEGATIVE_INFINITY;
            }
        });
        return ArrayUtil.toPrimitive(res);
    }
    @Override
    public int[] getFrameRange() {
        return this.frameRange;
    }
    public static double[][] getStatisticsLocalContrast(List<Image> images, List<Image> lcImages, boolean alongY, final double localContrastThreshold, boolean raw) {
        int dimLength = 0;
        if (alongY) {
            for (Image i : images) {
                if (i.getSizeY()>dimLength) dimLength = i.getSizeY();
            }
        }
        else dimLength = images.size();
        double[][] stats = new double[dimLength][2]; // 0 sum, 2 count
        for (int imIdx = 0; imIdx<images.size(); ++imIdx) {
            Image i = images.get(imIdx);
            Image lc = lcImages.get(imIdx);
            final int ii = imIdx;
            lc.getBoundingBox().translateToOrigin().loop((int x, int y, int z) -> {
                int idx = alongY ? y : ii;
                if (lc.getPixel(x, y, z)>localContrastThreshold) {
                    stats[idx][0] +=i.getPixel(x, y, z);
                    ++stats[idx][1];
                }
            });
        }
        if (!raw) {
            for (int i = 0; i<stats.length; ++i) {
                stats[i][0]/=stats[i][1];
            }
            double[] yMean = new double[dimLength];
            double[] yCount  = new double[dimLength];
            for (int i = 0; i<dimLength; ++i) {
                yMean[i] = stats[i][0];
                yCount[i] = stats[i][2];
            }
            Utils.plotProfile("Y background profile", yMean);
            Utils.plotProfile("Y count profile", yCount);
        }
        return stats;
    }
    public static double[][][] getStatisticsLocalContrast(List<Image> images, List<Image> lcImages, final double localContrastThreshold) {
         
        int yMax = 0;
        for (Image i : images) {
            if (i.getSizeY()>yMax) yMax = i.getSizeY();
        }
        
        double[][][] stats = new double[images.size()][yMax][2]; // 0 sum, 2 count
        for (int imIdx = 0; imIdx<images.size(); ++imIdx) {
            Image i = images.get(imIdx);
            Image lc = lcImages.get(imIdx);
            final int ii = imIdx;
            lc.getBoundingBox().translateToOrigin().loop((int x, int y, int z) -> {
                if (lc.getPixel(x, y, z)>localContrastThreshold) {
                    stats[ii][y][0] +=i.getPixel(x, y, z);
                    ++stats[ii][y][1];
                }
            });
        }
        return stats;
    }
    /**
     *
     * @param images
     * @param binSize
     * @param threshold
     * @param backgroundUnderThreshold
     * @param raw 
     * @return matrix dim 1 = alongY : y sinon plane, dim 2 = stats value: raw sum, sum2 & count, sinon mu sigma & count 
     */
    public static double[][] getStatistics(List<Image> images, boolean alongY, double threshold, boolean backgroundUnderThreshold, boolean raw) { // return mean, sigma, count
        // 1  get max y 
        int dimLength = 0;
        if (alongY) for (Image i : images) if (i.getSizeY()>dimLength) dimLength = i.getSizeY();
        else dimLength = images.size();
        double[][] stats = new double[dimLength][3]; // 0 sum, 1 sum2, 3 count
        for (int imIdx = 0; imIdx<images.size(); ++imIdx) {
            Image i = images.get(imIdx);
            final int ii = imIdx;
            double currentThreshold = ImageOperations.getMeanAndSigma(i, null, v->v>threshold==backgroundUnderThreshold)[0]; // mean within cells : avoid area within cell but no at borders of cell
            i.getBoundingBox().translateToOrigin().loop((int x, int y, int z) -> {
                int idx = alongY ? y : ii;
                double v = i.getPixel(x, y, z);
                if (v>currentThreshold == backgroundUnderThreshold) {
                    stats[idx][0] +=v;
                    stats[idx][1] +=v*v;
                    ++stats[idx][2];
                }
            });
        }
        if (!raw) {
            for (int i = 0; i<stats.length; ++i) {
                stats[i][0]/=stats[i][2];
                stats[i][1]/=stats[i][2];
                stats[i][1] = Math.sqrt(stats[i][1] - stats[i][0] * stats[i][0]);
            }
            double[] yMean = new double[dimLength];
            double[] ySd = new double[dimLength];
            double[] yCount  = new double[dimLength];
            for (int i = 0; i<dimLength; ++i) {
                yMean[i] = stats[i][0];
                ySd[i] = stats[i][1];
                yCount[i] = stats[i][2];
            }
            Utils.plotProfile("Y background profile", yMean);
            Utils.plotProfile("Y sd profile", ySd);
            Utils.plotProfile("Y count profile", yCount);
        }
        return stats;
    }

    @Override
    public void freeMemory() {
        this.lcImages=null;
    }
}
