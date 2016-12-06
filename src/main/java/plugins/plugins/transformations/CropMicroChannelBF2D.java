/*
 * Copyright (C) 2015 jollion
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
package plugins.plugins.transformations;

import boa.gui.imageInteraction.IJImageDisplayer;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.containers.InputImages;
import dataStructure.objects.Object3D;
import ij.process.AutoThresholder;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageFloat;
import image.ImageInteger;
import image.ImageLabeller;
import image.ImageOperations;
import static image.ImageOperations.threshold;
import java.util.ArrayList;import java.util.Comparator;
;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.commons.lang.ArrayUtils;
import processing.ImageFeatures;
import utils.ArrayUtil;
import utils.Utils;
import static utils.Utils.plotProfile;

/**
 *
 * @author jollion
 */
public class CropMicroChannelBF2D extends CropMicroChannels {
    public static boolean debug = false;
    NumberParameter microChannelWidth = new BoundedNumberParameter("Microchannel Width (pix)", 0, 20, 5, null);
    NumberParameter microChannelWidthMin = new BoundedNumberParameter("MicroChannel Width Min(pixels)", 0, 15, 5, null);
    NumberParameter microChannelWidthMax = new BoundedNumberParameter("MicroChannel Width Max(pixels)", 0, 28, 5, null);
    NumberParameter yEndMargin = new BoundedNumberParameter("Distance between end of channel and optical aberration", 0, 30, 0, null);
    NumberParameter localDerExtremaThld = new BoundedNumberParameter("X-Derivative Threshold (absolute value)", 1, 10, 0, null);
    Parameter[] parameters = new Parameter[]{channelHeight, cropMargin, margin, microChannelWidth, microChannelWidthMin, microChannelWidthMax, localDerExtremaThld, yEndMargin, xStart, xStop, yStart, yStop, number};
    public final static double betterPeakRelativeThreshold = 0.6;
    public CropMicroChannelBF2D(int margin, int cropMargin, int microChannelWidth, double microChannelWidthMin, int microChannelWidthMax, int timePointNumber) {
        this.margin.setValue(margin);
        this.cropMargin.setValue(cropMargin);
        this.microChannelWidth.setValue(microChannelWidth);
        this.microChannelWidthMin.setValue(microChannelWidthMin);
        this.microChannelWidthMax.setValue(microChannelWidthMax);
        
        this.number.setValue(timePointNumber);
    }
    public CropMicroChannelBF2D() {
        
    }
    
    public CropMicroChannelBF2D setTimePointNumber(int timePointNumber) {
        this.number.setValue(timePointNumber);
        return this;
    }
    
    @Override public BoundingBox getBoundingBox(Image image) {
        return getBoundingBox(image, cropMargin.getValue().intValue(), margin.getValue().intValue(), channelHeight.getValue().intValue(), microChannelWidth.getValue().intValue(), microChannelWidthMin.getValue().intValue(), microChannelWidthMax.getValue().intValue(), xStart.getValue().intValue(), xStop.getValue().intValue(), yStart.getValue().intValue(), yStop.getValue().intValue(), 0, localDerExtremaThld.getValue().doubleValue(), yEndMargin.getValue().intValue());
    }
    
    public static BoundingBox getBoundingBox(Image image, int cropMargin, int margin, int channelHeight, int channelWidth, int widthMin, int widthMax, int xStart, int xStop, int yStart, int yStop, int yStartAdjustWindow, double localExtremaThld, int yMarginEndChannel) {
        Result r = segmentMicroChannels(image, true, margin, channelWidth, widthMin, widthMax, yStartAdjustWindow, localExtremaThld, yMarginEndChannel);
        if (r==null || r.xMax.length==0) return null;
        int yMin = r.getYMin();
        int yMax = r.getYMax();
        yMax = Math.min(yMin+channelHeight, yMax);
        yMin = Math.max(yStart,yMin);
        yStop = Math.min(yStop, yMax);
        yStart = Math.max(yMin-cropMargin, yStart);
        
        xStart = Math.max(xStart, r.getXMin()-cropMargin);
        xStop = Math.min(xStop, r.getXMax() + cropMargin);
        if (debug) logger.debug("Xmin: {}, Xmax: {}", r.getXMin(), r.getXMax());
        return new BoundingBox(xStart, xStop, yStart, yStop, 0, image.getSizeZ()-1);
        
    }
    public static Result segmentMicroChannels(Image image, boolean opticalAberration, int margin, int channelWidth, int widthMin, int widthMax, int yStartAdjustWindow, double localExtremaThld, int yMarginEndChannel) {
        double derScale = 2;
        int xErode = Math.max(1, (int)(derScale/2d + 0.5+Double.MIN_VALUE));
        
        /*
        1) search for optical aberation + crop
        2) search for y-start of MC using Y-proj of d/dy image global max (projection from yE[0; y-aberation]
        3) search of xpositions of microchannels using X-projection (yE[y-start; y-aberration]) of d/dx & peak detection (detection of positive peak & negative peak @ distance of channel weight) 
        */
        
        int aberrationStart = opticalAberration ? searchYLimWithOpticalAberration(image, 0.25, yMarginEndChannel) : image.getSizeY()-1;
        if (aberrationStart<=0) return null;
        Image imCrop = image.crop(new BoundingBox(0, image.getSizeX()-1, 0, aberrationStart, 0, image.getSizeZ()-1));
        
        Image imDerY = ImageFeatures.getDerivative(imCrop, derScale, 0, 1, 0, true);
        float[] yProj = ImageOperations.meanProjection(imDerY, ImageOperations.Axis.Y, null);
        int channelStartIdx = ArrayUtil.max(yProj, 0, (int)(yProj.length*0.75)); // limit to 

        imCrop = image.crop(new BoundingBox(0, image.getSizeX()-1, channelStartIdx, aberrationStart, 0, image.getSizeZ()-1));
        float[] xProj = ImageOperations.meanProjection(imCrop, ImageOperations.Axis.X, null); 
        ArrayUtil.gaussianSmooth(xProj, 1); // derScale
        Image imDerX = ImageFeatures.getDerivative(imCrop, derScale, 1, 0, 0, true);
        float[] xProjDer = ImageOperations.meanProjection(imDerX, ImageOperations.Axis.X, null);
        int xShift = true ? 0 : (int)derScale ; // get a symetric profil between local max & min 
        //for (int i = 0; i<xShift; ++i) xProjDer=ArrayUtils.remove(xProjDer, 0); // shift
        /*float[] xProjDerNorm = new float[xProjDer.length];
        for (int i = 0; i<xProjDerNorm.length; ++i) {
            if (xProjDer[i]>0 && i>xShift && i<xProjDerNorm.length-xShift) xProjDerNorm[i] = xProjDer[i] / xProj[i-xShift];
            else xProjDerNorm[i] = xProjDer[i] / xProj[i];
        }*/
        if (debug) {
            //plotProfile("XProjDer", xProjDer);
            //plotProfile("XProj smoothed", xProj);
            new IJImageDisplayer().showImage(imDerY);
            new IJImageDisplayer().showImage(imDerX);
            plotProfile("yProjCrop", yProj);
            plotProfile("xProjDer", xProjDer);
            plotProfile("xProj", xProj);
            //plotProfile("xProjDerNorm", xProjDerNorm);
            
        }
        //xProjDerNorm = xProjDer;
        final float[] derMap = xProjDer;
        List<Integer> localMax = ArrayUtil.getRegionalExtrema(xProjDer, (int)(derScale+0.5), true);
        List<Integer> localMin = ArrayUtil.getRegionalExtrema(xProjDer, (int)(derScale+0.5), false);
        int leftMargin = image.getSizeX()-margin;
        final Predicate<Integer> rem = i -> Math.abs(derMap[i])<localExtremaThld || i<=margin || i>=leftMargin;
        localMax.removeIf(rem);
        localMin.removeIf(rem);
        
        Comparator<int[]> segmentScoreComparator = (int[] o1, int[] o2) -> { // >0 -> o2 better that o1
            double score1 = Math.abs(derMap[localMax.get(o1[0])]) + Math.abs(derMap[localMin.get(o1[1])]);
            double score2 = Math.abs(derMap[localMax.get(o2[0])]) + Math.abs(derMap[localMin.get(o2[1])]);
            int comp = -Double.compare(score1, score2);
            if (comp ==0) {
                int d1 = localMin.get(o1[1]) - localMax.get(o1[0]);
                int d2 = localMin.get(o2[1]) - localMax.get(o2[0]);
                comp =  Integer.compare(Math.abs(d1-channelWidth), Math.abs(d2-channelWidth));
            }
            return comp;
        };
        
        if (debug) {
            logger.debug("{} max found, {} min found", localMax.size(), localMin.size());
            logger.debug("max: {}", localMax);
            logger.debug("min: {}", localMin);
        }
        
        List<int[]> peaks = new ArrayList<>();
        int lastMinIdx = 0;
        int maxIdx = 0;
        while (maxIdx<localMax.size()) {
            if (debug) logger.debug("VALID MAX: {}", localMax.get(maxIdx));
            int minIdx = getNextMinIdx(derMap, localMin, localMax, maxIdx, lastMinIdx, widthMin,widthMax, segmentScoreComparator);
            if (minIdx>=0 ) {
                // check all valid max between current max and min
                int nextMaxIdx = maxIdx+1;
                while (nextMaxIdx<localMax.size() && localMax.get(nextMaxIdx)<localMin.get(minIdx)) {
                    if (Math.abs(derMap[localMax.get(maxIdx)])*betterPeakRelativeThreshold<Math.abs(derMap[localMax.get(nextMaxIdx)])) {
                        int nextMinIdx = getNextMinIdx(derMap, localMin, localMax, nextMaxIdx, lastMinIdx, widthMin,widthMax, segmentScoreComparator);
                        if (nextMinIdx>=0) {
                            int comp = segmentScoreComparator.compare(new int[]{maxIdx, minIdx}, new int[]{nextMaxIdx, nextMinIdx});
                            if (comp>0) {
                                maxIdx = nextMaxIdx;
                                minIdx = nextMinIdx;
                                if (debug) logger.debug("BETTER VALID MAX: {}, d: {}", localMax.get(maxIdx), localMin.get(minIdx) - localMax.get(maxIdx));
                            }
                        }
                    }
                    ++nextMaxIdx;
                }
                if (debug) {
                    int x1 = localMax.get(maxIdx);
                    int x2 = localMin.get(minIdx);
                    logger.debug("Peak found X: [{};{}], distance: {}, value: [{};{}], normedValue: [{};{}]", x1, x2, localMin.get(minIdx) - localMax.get(maxIdx), xProjDer[x1], xProjDer[x2], xProjDer[x1]/xProj[x1], xProjDer[x2]/xProj[x2]);
                }
                peaks.add(new int[]{localMax.get(maxIdx)+xErode, localMin.get(minIdx)-xErode, 0});
                lastMinIdx = minIdx;
                maxIdx = nextMaxIdx; // first max after min
            } else ++maxIdx;
        }
        // precise Y-value within shift around channelStartIdx
        if (yStartAdjustWindow>0) {
            for (int[] peak : peaks) {
                BoundingBox win = new BoundingBox(peak[0], peak[1], channelStartIdx-yStartAdjustWindow, channelStartIdx+yStartAdjustWindow, 0, 0);
                float[] proj = ImageOperations.meanProjection(imDerY, ImageOperations.Axis.Y, win);
                peak[2] = ArrayUtil.max(proj)-yStartAdjustWindow;
                //if (debug) plotProfile("yProjDerAdjust", proj);
            }
        }
        return new Result(peaks, channelStartIdx, aberrationStart);
        
    }
    
    private static int getNextMinIdx(final float[] derMap, final List<Integer> localMin, final List<Integer> localMax, final int maxIdx, int lastMinIdx, final double widthMin, final double widthMax, Comparator<int[]> segmentScoreComparator) {
        int minIdx = lastMinIdx;
        while(minIdx<localMin.size()) {
            int d = localMin.get(minIdx) - localMax.get(maxIdx);
            //if (debug) logger.debug("Test MIN: {}, d: {}", localMin.get(minIdx), d);
            if (d>=widthMin && d<=widthMax) {
                if (debug) logger.debug("VALID MIN: {}, d: {}", localMin.get(minIdx), d);
                // see if next mins yield to better segmentation
                boolean better=true;
                while(better) {
                    better=false;
                    if (minIdx+1<localMin.size() && Math.abs(derMap[localMin.get(minIdx+1)])>Math.abs(derMap[localMin.get(minIdx)])*betterPeakRelativeThreshold) {
                        int d2 = localMin.get(minIdx+1) - localMax.get(maxIdx);
                        if (debug) logger.debug("Test BETTER VALID MIN: {}, d: {}", localMin.get(minIdx), d2);
                        if (d2>=widthMin && d2<=widthMax) {
                            if (segmentScoreComparator.compare(new int[]{maxIdx, minIdx}, new int[]{maxIdx, minIdx+1})>0) {
                                ++minIdx;
                                better = true;
                                if (debug) logger.debug("BETTER VALID MIN: {}, d: {}", localMin.get(minIdx), d2);
                            } 
                        }
                    }
                }
                return minIdx;
            } else if (d>widthMax) return -1;
            
            minIdx++;
        }
        return -1;
    }
       
    
    public static int searchYLimWithOpticalAberration(Image image, double peakProportion, int margin) {
        //int slidingSigmaWindow = 10;
        // aberation is @ higher Y coord that microchannels
        /*
        1) Search for global max : yMax
        2) seach for  min after yMax -> get peak hight = h = I(yMax) - I(yMin)
        3) search for first y | y<yMax & I(y) < peakProportion * h & sliding variance < threshold
        */
        float[] yProj = ImageOperations.meanProjection(image, ImageOperations.Axis.Y, null);
        int maxIdx = ArrayUtil.max(yProj);
        int minIdx = ArrayUtil.min(yProj); //, maxIdx+1, yProj.length-1 // in case peak is touching edge of image no min after aberration: seach for min in whole y axis
        double peakHeight = yProj[maxIdx] - yProj[minIdx];
        float thld = (float)(peakHeight * peakProportion + yProj[minIdx] );
        int endOfPeakIdx = ArrayUtil.getFirstOccurence(yProj, maxIdx, 0, thld, true, true);
        
        /*float[] slidingSigma = new float[debug ? yProj.length : endOfPeakIdx]; // endOfPeakIdx
        double[] meanSigma = new double[2];
        for (int i = slidingSigmaWindow; i<slidingSigma.length; ++i) {
            ArrayUtil.meanSigma(yProj, i-slidingSigmaWindow, i, meanSigma);
            slidingSigma[i-1] = (float)(meanSigma[1] / meanSigma[0] );
        }*/
        // première valeur de sigma : 
        //int startOfMicroChannel = ArrayUtil.getFirstOccurence(slidingSigma, endOfPeakIdx-1, slidingSigmaWindow-1, (float)sigmaThreshold, true, true);
        // autre strategie: premier peak de sigma après
        //List<Integer> peaks = ArrayUtil.getRegionalExtrema(slidingSigma, 3, true);
        //peaks.removeIf(i -> i>endOfPeakIdx-slidingSigmaWindow);
        //int startOfMicroChannel = peaks.get(peaks.size()-1);
        // autre stratégie: valeur constante
        int startOfMicroChannel = endOfPeakIdx - margin;
        
        if (debug) {
            new IJImageDisplayer().showImage(image);
            Utils.plotProfile("yProj", yProj);
            //Utils.plotProfile("Sliding sigma", slidingSigma);
            logger.debug("Optical Aberration detection: minIdx: {}, maxIdx: {}, peakHeightThld: {}, enfOfPeak: {}, low limit of Mc: {}", minIdx, maxIdx, thld, endOfPeakIdx, startOfMicroChannel);
        }
        return startOfMicroChannel;
    }

    public Parameter[] getParameters() {
        return parameters;
    }
}
