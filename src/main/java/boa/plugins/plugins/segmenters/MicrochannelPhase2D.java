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
package boa.plugins.plugins.segmenters;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectProcessing;
import boa.data_structure.Voxel;
import ij.process.AutoThresholder;
import boa.image.BlankMask;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.processing.ImageFeatures;
import boa.image.processing.ImageOperations;
import static boa.image.processing.ImageOperations.threshold;
import boa.image.processing.RegionFactory;
import static boa.plugins.Plugin.logger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import boa.plugins.Segmenter;
import boa.plugins.plugins.transformations.CropMicroChannelBF2D;
import static boa.plugins.plugins.transformations.CropMicroChannelBF2D.betterPeakRelativeThreshold;
import boa.utils.ArrayUtil;
import boa.utils.Utils;
import static boa.utils.Utils.plotProfile;
import java.util.Comparator;
import java.util.function.Predicate;

/**
 *
 * @author jollion
 */
public class MicrochannelPhase2D implements MicrochannelSegmenter {
    
    NumberParameter channelWidth = new BoundedNumberParameter("MicroChannel Typical Width (pixels)", 0, 20, 5, null);
    NumberParameter channelWidthMin = new BoundedNumberParameter("MicroChannel Width Min(pixels)", 0, 15, 5, null);
    NumberParameter channelWidthMax = new BoundedNumberParameter("MicroChannel Width Max(pixels)", 0, 28, 5, null);
    NumberParameter yStartAdjustWindow = new BoundedNumberParameter("Y-Start Adjust Window (pixels)", 0, 5, 0, null).setToolTipText("Window (in pixels) within which y-coordinate of start of microchannel will be refined, by searching for the first local maximum of the Y-derivate.");
    NumberParameter localDerExtremaThld = new BoundedNumberParameter("X-Derivative Threshold (absolute value)", 3, 10, 0, null).setToolTipText("Threshold for Microchannel border detection (peaks of 1st derivative in X-axis)");
    //NumberParameter sigmaThreshold = new BoundedNumberParameter("Border Sigma Threshold", 3, 0.75, 0, 1).setToolTipText("<html>Fine adjustement of X bounds: eliminate lines with no signals <br />After segmentation, standart deviation along y-axis of each line is compared to the one of the center of the microchannel. <br />When the ratio is inferior to this threhsold, the line is eliminated. <br />0 = no adjustement</html>");
    Parameter[] parameters = new Parameter[]{channelWidth, channelWidthMin, channelWidthMax, localDerExtremaThld}; //sigmaThreshold
    public static boolean debug = false;

    public MicrochannelPhase2D() {
    }

    public MicrochannelPhase2D(int channelWidth) {
        this.channelWidth.setValue(channelWidth);
    }

    public MicrochannelPhase2D setyStartAdjustWindow(int yStartAdjustWindow) {
        this.yStartAdjustWindow.setValue(yStartAdjustWindow);
        return this;
    }
    @Override
    public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        Result r = segment(input);
        if (r==null) return null;
        ArrayList<Region> objects = new ArrayList<>(r.size());
        for (int idx = 0; idx<r.xMax.length; ++idx) {
            objects.add(new Region(new BlankMask(r.getBounds(idx, true).getImageProperties(input.getScaleXY(), input.getScaleZ())), idx+1, true));
            logger.debug("mc: {}: bds: {}", idx, objects.get(objects.size()-1).getBounds());
        }
        return new RegionPopulation(objects, input);
    }
    
    @Override
    public Result segment(Image input) {
        Result r =  segmentMicroChannels(input, false, 0, yStartAdjustWindow.getValue().intValue(), 0, channelWidth.getValue().intValue(), channelWidthMin.getValue().intValue(), channelWidthMax.getValue().intValue(), localDerExtremaThld.getValue().doubleValue(), debug);
        if (r==null) return null;
        double thld = 0;
        //double thld = sigmaThreshold.getValue().doubleValue();
        if (thld>0) { // refine borders: compare Y-variance from sides to center and remove if too low
            for (int idx = 0; idx<r.xMax.length; ++idx) {
                BoundingBox bds = r.getBounds(idx, true);
                int yShift = bds.getSizeX()/2;
                int xSizeInner = bds.getSizeX()/4;
                int maxCropX = Math.min(xSizeInner, 2);
                if (xSizeInner==0) continue;
                BoundingBox inner = new BoundingBox(bds.getxMin()+xSizeInner, bds.getxMax()-xSizeInner, bds.getyMin()+yShift, bds.getyMax()-yShift, bds.getzMin(), bds.getzMax());
                float[] yProjInner = ImageOperations.meanProjection(input, ImageOperations.Axis.Y, inner);
                double[] sm = ArrayUtil.meanSigma(yProjInner, 0, yProjInner.length, null);
                double sigmaInner = sm[1];
                for (int x = bds.getxMin(); x<bds.getxMin()+maxCropX; ++x) { // adjust left
                    double s = getSigmaLine(input, x, inner.getyMin(), (int)inner.getZMean(), yProjInner);
                    //logger.debug("x: {}, sig: {}, ref: {},sig/ref:{}", x, s, sigmaInner, s/sigmaInner);
                    if (s/sigmaInner<thld) r.xMin[idx]=x;
                    else break;
                }
                for (int x = bds.getxMax(); x>=bds.getxMax()-maxCropX; --x) { // adjust right
                    double s = getSigmaLine(input, x, inner.getyMin(), (int)inner.getZMean(), yProjInner);
                    //logger.debug("x: {}, sig: {}, ref: {},sig/ref:{}", x, s, sigmaInner, s/sigmaInner);
                    if (s/sigmaInner<thld) r.xMax[idx]=x;
                    else break;
                }
            }
        }
        return r;
    }
    private static double getSigmaLine(Image image, int x, int yStart, int z, float[] array) {
        for (int y = 0; y<array.length; ++y ) array[y] = image.getPixel(x, y+yStart, z);
        double[] sm = ArrayUtil.meanSigma(array, 0, array.length, null);
        return sm[1];
    }
    
    @Override public Parameter[] getParameters() {
        return parameters;
    }
    
    /**
      1) search for optical aberation + crop
      2) search for y-start of MC using Y-proj of d/dy image global max (projection from yE[0; y-aberation]
      3) search of xpositions of microchannels using X-projection (yE[y-start; y-aberration]) of d/dx & peak detection (detection of positive peak & negative peak @ distance of channel weight) 
        
     * @param image
     * @param opticalAberration
     * @param margin
     * @param yStartAdjustWindow
     * @param yMarginEndChannel
     * @param channelWidth
     * @param widthMin
     * @param widthMax
     * @param localExtremaThld
     * @param testMode
     * @return 
     */
    public static Result segmentMicroChannels(Image image, boolean opticalAberration, int margin, int yStartAdjustWindow, int yMarginEndChannel, int channelWidth, int widthMin, int widthMax, double localExtremaThld, boolean testMode) {
        
        double derScale = 2;
        int xErode = Math.max(1, (int)(derScale/2d));
        xErode = 0;
        if (testMode) logger.debug("xErode: {}", xErode);
        
        int aberrationStart = opticalAberration ? searchYLimWithOpticalAberration(image, 0.25, yMarginEndChannel, testMode) : image.getSizeY()-1;
        if (aberrationStart<=0) return null;
        Image imCrop = image.crop(new BoundingBox(0, image.getSizeX()-1, 0, aberrationStart, 0, image.getSizeZ()-1));
        
        Image imDerY = ImageFeatures.getDerivative(imCrop, derScale, 0, 1, 0, true);
        float[] yProj = ImageOperations.meanProjection(imDerY, ImageOperations.Axis.Y, null);
        int channelStartIdx = ArrayUtil.max(yProj, 0, (int)(yProj.length*0.75)); // limit to channel start

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
        if (testMode) {
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
        
        if (testMode) {
            logger.debug("{} max found, {} min found", localMax.size(), localMin.size());
            logger.debug("max: {}", localMax);
            logger.debug("min: {}", localMin);
        }
        
        List<int[]> peaks = new ArrayList<>();
        int lastMinIdx = 0;
        int maxIdx = 0;
        while (maxIdx<localMax.size()) {
            if (testMode) logger.debug("VALID MAX: {}", localMax.get(maxIdx));
            int minIdx = getNextMinIdx(derMap, localMin, localMax, maxIdx, lastMinIdx, widthMin,widthMax, segmentScoreComparator, testMode);
            if (minIdx>=0 ) {
                // check all valid max between current max and min
                int nextMaxIdx = maxIdx+1;
                while (nextMaxIdx<localMax.size() && localMax.get(nextMaxIdx)<localMin.get(minIdx)) {
                    if (Math.abs(derMap[localMax.get(maxIdx)])*betterPeakRelativeThreshold<Math.abs(derMap[localMax.get(nextMaxIdx)])) {
                        int nextMinIdx = getNextMinIdx(derMap, localMin, localMax, nextMaxIdx, lastMinIdx, widthMin,widthMax, segmentScoreComparator, testMode);
                        if (nextMinIdx>=0) {
                            int comp = segmentScoreComparator.compare(new int[]{maxIdx, minIdx}, new int[]{nextMaxIdx, nextMinIdx});
                            if (comp>0) {
                                maxIdx = nextMaxIdx;
                                minIdx = nextMinIdx;
                                if (testMode) logger.debug("BETTER VALID MAX: {}, d: {}", localMax.get(maxIdx), localMin.get(minIdx) - localMax.get(maxIdx));
                            }
                        }
                    }
                    ++nextMaxIdx;
                }
                if (testMode) {
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
                BoundingBox win = new BoundingBox(peak[0], peak[1], Math.max(0, channelStartIdx-yStartAdjustWindow), Math.min(imDerY.getSizeY()-1, channelStartIdx+yStartAdjustWindow), 0, 0);
                float[] proj = ImageOperations.meanProjection(imDerY, ImageOperations.Axis.Y, win);
                List<Integer> localMaxY = ArrayUtil.getRegionalExtrema(proj, 2, true);
                //peak[2] = ArrayUtil.max(proj)-yStartAdjustWindow;
                if (localMaxY.isEmpty()) continue;
                peak[2] = localMaxY.get(0)-yStartAdjustWindow;
                //if (debug) plotProfile("yProjDerAdjust", proj);
            }
        }
        Result r= new Result(peaks, channelStartIdx, aberrationStart);
        // adjust Y: remove derScale from left 
        int xLeftAdjust = (int)(derScale/2.0+0.5);
        for (int i = 0; i<r.size(); ++i) r.xMax[i]-=xLeftAdjust;
        if (testMode) for (int i = 0; i<r.size(); ++i) logger.debug("mc: {} -> {}", i, r.getBounds(i, true));
        return r;
    }
    
    private static int getNextMinIdx(final float[] derMap, final List<Integer> localMin, final List<Integer> localMax, final int maxIdx, int lastMinIdx, final double widthMin, final double widthMax, Comparator<int[]> segmentScoreComparator, boolean testMode) {
        int minIdx = lastMinIdx;
        while(minIdx<localMin.size()) {
            int d = localMin.get(minIdx) - localMax.get(maxIdx);
            //if (debug) logger.debug("Test MIN: {}, d: {}", localMin.get(minIdx), d);
            if (d>=widthMin && d<=widthMax) {
                if (testMode) logger.debug("VALID MIN: {}, d: {}", localMin.get(minIdx), d);
                // see if next mins yield to better segmentation
                boolean better=true;
                while(better) {
                    better=false;
                    if (minIdx+1<localMin.size() && Math.abs(derMap[localMin.get(minIdx+1)])>Math.abs(derMap[localMin.get(minIdx)])*betterPeakRelativeThreshold) {
                        int d2 = localMin.get(minIdx+1) - localMax.get(maxIdx);
                        if (testMode) logger.debug("Test BETTER VALID MIN: {}, d: {}", localMin.get(minIdx), d2);
                        if (d2>=widthMin && d2<=widthMax) {
                            if (segmentScoreComparator.compare(new int[]{maxIdx, minIdx}, new int[]{maxIdx, minIdx+1})>0) {
                                ++minIdx;
                                better = true;
                                if (testMode) logger.debug("BETTER VALID MIN: {}, d: {}", localMin.get(minIdx), d2);
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
       
    public static int searchYLimWithOpticalAberration(Image image, double peakProportion, int margin, boolean testMode) {
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
        
        if (testMode) {
            new IJImageDisplayer().showImage(image);
            Utils.plotProfile("yProj", yProj);
            //Utils.plotProfile("Sliding sigma", slidingSigma);
            logger.debug("Optical Aberration detection: minIdx: {}, maxIdx: {}, peakHeightThld: {}, enfOfPeak: {}, low limit of Mc: {}", minIdx, maxIdx, thld, endOfPeakIdx, startOfMicroChannel);
        }
        return startOfMicroChannel;
    }
}
