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
package boa.plugins.plugins.segmenters;

import boa.plugins.MicrochannelSegmenter;
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
import boa.plugins.ToolTip;
import boa.utils.ArrayUtil;
import boa.utils.Utils;
import static boa.utils.Utils.plotProfile;
import java.util.Comparator;
import java.util.function.Predicate;

/**
 *
 * @author jollion
 */
public class MicrochannelPhase2D implements MicrochannelSegmenter, ToolTip {
    
    NumberParameter channelWidth = new BoundedNumberParameter("MicroChannel Typical Width (pixels)", 0, 20, 5, null);
    NumberParameter channelWidthMin = new BoundedNumberParameter("MicroChannel Width Min(pixels)", 0, 15, 5, null);
    NumberParameter channelWidthMax = new BoundedNumberParameter("MicroChannel Width Max(pixels)", 0, 28, 5, null);
    NumberParameter closedEndYAdjustWindow = new BoundedNumberParameter("Closed-end Y Adjust Window (pixels)", 0, 5, 0, null).setToolTipText("Window (in pixels) within which y-coordinate of the closed-end of microchannel will be refined, by searching for the first local maximum of the Y-derivate within the window: [y-this value; y+this value]");
    NumberParameter localDerExtremaThld = new BoundedNumberParameter("X-Derivative Threshold (absolute value)", 3, 10, 0, null).setToolTipText("<html>Threshold for Microchannel border detection (peaks of 1st derivative in X-axis). <br />This parameter will depend on the intensity of the image and should be adjusted if microchannels are poorly detected. <br />A higher value if too many channels are detected and a lower value in the contrary</html>");
    Parameter[] parameters = new Parameter[]{channelWidth, channelWidthMin, channelWidthMax, localDerExtremaThld}; //sigmaThreshold
    public final static double PEAK_RELATIVE_THLD = 0.6;
    public static boolean debug = false;
    protected String toolTip = "<html><b>Microchannel Segmentation in phase-contrast images:</b>"
            + "<ol><li>Search for optical aberration  y coordinate -> yAberration</li>"
            + "<li>Search for global closed-end y-coordinate of Microchannels: global max of the Y-proj of d/dy -> yEnd</li>"
            + "<li>Search of x-positions of microchannels using X-projection (y in [ yEnd; yAberration]) of d/dx image & peak detection: <br />"
            + "(detection of positive peask & negative peaks over \"X-derivative Threshold\" separated by a distance closest to channelWidth and in the range [widthMin; widthMax]</li>"
            + "<li>Adjust yStart for each channel: first local max of d/dy image in the range [yEnd-  AdjustWindow ; yEnd+ AdjustWindow]</li></ol></html>";

    public MicrochannelPhase2D() {}

    public MicrochannelPhase2D setyStartAdjustWindow(int yStartAdjustWindow) {
        this.closedEndYAdjustWindow.setValue(yStartAdjustWindow);
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
        Result r =  segmentMicroChannels(input, false, closedEndYAdjustWindow.getValue().intValue(), 0, channelWidth.getValue().intValue(), channelWidthMin.getValue().intValue(), channelWidthMax.getValue().intValue(), localDerExtremaThld.getValue().doubleValue(), debug);
        if (r==null) return null;
        //double thld = sigmaThreshold.getValue().doubleValue();
        /*if (thld>0) { // refine borders: compare Y-variance from sides to center and remove if too low
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
        }*/
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
      1) if {@param opticalAberration}: search for optical aberration and crop image to remove it see {@link #searchYLimWithOpticalAberration(Image, double, int, boolean) searchYLimWithOpticalAberration}
      2) Search for global closed-end y-coordinate of Microchannels: global max of the Y-proj of d/dy -> yEnd
      3) search of x-positions of microchannels using X-projection (y in [ yEnd; yAberration]) of d/dx image & peak detection (detection of positive peak & negative peak over {@param localExtremaThld} separated by a distance closest of {@param channelWidth} and in the range [{@param widthMin} ; {@param widthMax}]
      4) Adjust yStart for each channel: first local max of d/dy image in the range [yEnd-{@param yStartAdjustWindow}; yEnd+{@param yStartAdjustWindow}]
     * @param image
     * @param opticalAberration whether the image contains the optical aberration (procduced by shadow of the microfluidic device) 
     * @param yClosedEndAdjustWindow defines the window for yStart 
     * @param yMarginEndChannel
     * @param channelWidth
     * @param widthMin
     * @param widthMax
     * @param localExtremaThld
     * @param testMode
     * @return Result object containing bounding boxes of segmented microchannels
     */
    public static Result segmentMicroChannels(Image image, boolean opticalAberration, int yClosedEndAdjustWindow, int yMarginEndChannel, int channelWidth, int widthMin, int widthMax, double localExtremaThld, boolean testMode) {
        
        double derScale = 2;
        // get aberration
        int aberrationStart = opticalAberration ? searchYLimWithOpticalAberration(image, 0.25, yMarginEndChannel, testMode) : image.getSizeY()-1;
        if (aberrationStart<=0) return null;
        Image imCrop = image.crop(new BoundingBox(0, image.getSizeX()-1, 0, aberrationStart, 0, image.getSizeZ()-1));
        
        // get global closed-end Y coordinate
        Image imDerY = ImageFeatures.getDerivative(imCrop, derScale, 0, 1, 0, true);
        float[] yProj = ImageOperations.meanProjection(imDerY, ImageOperations.Axis.Y, null);
        int closedEndY = ArrayUtil.max(yProj, 0, (int)(yProj.length*0.75)); 

        // get X coordinates of each microchannel
        imCrop = image.crop(new BoundingBox(0, image.getSizeX()-1, closedEndY, aberrationStart, 0, image.getSizeZ()-1));
        float[] xProj = ImageOperations.meanProjection(imCrop, ImageOperations.Axis.X, null); 
        ArrayUtil.gaussianSmooth(xProj, 1); // derScale
        Image imDerX = ImageFeatures.getDerivative(imCrop, derScale, 1, 0, 0, true);
        float[] xProjDer = ImageOperations.meanProjection(imDerX, ImageOperations.Axis.X, null);
        
        if (testMode) {
            //plotProfile("XProjDer", xProjDer);
            //plotProfile("XProj smoothed", xProj);
            ImageWindowManagerFactory.showImage(imDerY);
            ImageWindowManagerFactory.showImage(imDerX);
            plotProfile("yProjCrop", yProj);
            plotProfile("xProjDer", xProjDer);
            plotProfile("xProj", xProj);
        }
        
        final float[] derMap = xProjDer;
        List<Integer> localMax = ArrayUtil.getRegionalExtrema(xProjDer, (int)(derScale+0.5), true);
        List<Integer> localMin = ArrayUtil.getRegionalExtrema(xProjDer, (int)(derScale+0.5), false);
        final Predicate<Integer> rem = i -> Math.abs(derMap[i])<localExtremaThld ;
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
                    if (Math.abs(derMap[localMax.get(maxIdx)])*PEAK_RELATIVE_THLD<Math.abs(derMap[localMax.get(nextMaxIdx)])) {
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
                peaks.add(new int[]{localMax.get(maxIdx), localMin.get(minIdx), 0});
                lastMinIdx = minIdx;
                maxIdx = nextMaxIdx; // first max after min
            } else ++maxIdx;
        }
        
        // refine Y-coordinate of closed-end for each microchannel
        if (yClosedEndAdjustWindow>0) {
            for (int[] peak : peaks) {
                BoundingBox win = new BoundingBox(peak[0], peak[1], Math.max(0, closedEndY-yClosedEndAdjustWindow), Math.min(imDerY.getSizeY()-1, closedEndY+yClosedEndAdjustWindow), 0, 0);
                float[] proj = ImageOperations.meanProjection(imDerY, ImageOperations.Axis.Y, win);
                List<Integer> localMaxY = ArrayUtil.getRegionalExtrema(proj, 2, true);
                //peak[2] = ArrayUtil.max(proj)-yStartAdjustWindow;
                if (localMaxY.isEmpty()) continue;
                peak[2] = localMaxY.get(0)-yClosedEndAdjustWindow;
                //if (debug) plotProfile("yProjDerAdjust", proj);
            }
        }
        Result r= new Result(peaks, closedEndY, aberrationStart);
         
        int xLeftErrode = (int)(derScale/2.0+0.5); // adjust Y: remove derScale from left 
        for (int i = 0; i<r.size(); ++i) r.xMax[i]-=xLeftErrode;
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
                    if (minIdx+1<localMin.size() && Math.abs(derMap[localMin.get(minIdx+1)])>Math.abs(derMap[localMin.get(minIdx)])*PEAK_RELATIVE_THLD) {
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
    /**
     * Search of Optical Aberration (shadow produced by the microfluidic device at the opened-end of microchannels
     * The closed-end should be towards top of image
     * All the following steps are performed on the mean projection of {@param image} along Y axis
     * 1) search for global max yMax
     * 2) search for min value after yMax (yMin>yMax) -> define aberration peak height: h = I(yMax) - I(yMin)
     * 3) search for first occurrence of the value h * {@param peakProportion} before yMax -> endOfPeakYIdx<yMax
     * @param image
     * @param peakProportion
     * @param margin removed to the endOfPeakYIdx value in order to remove long range over-illumination 
     * @param testMode
     * @return the y coordinate over the optical aberration
     */
    public static int searchYLimWithOpticalAberration(Image image, double peakProportion, int margin, boolean testMode) {

        float[] yProj = ImageOperations.meanProjection(image, ImageOperations.Axis.Y, null);
        int maxIdx = ArrayUtil.max(yProj);
        int minIdx = ArrayUtil.min(yProj); //, maxIdx+1, yProj.length-1 // in case peak is touching edge of image no min after aberration: seach for min in whole y axis
        double peakHeight = yProj[maxIdx] - yProj[minIdx];
        float thld = (float)(peakHeight * peakProportion + yProj[minIdx] );
        int endOfPeakYIdx = ArrayUtil.getFirstOccurence(yProj, maxIdx, 0, thld, true, true);
        
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
        int startOfMicroChannel = endOfPeakYIdx - margin;
        
        if (testMode) {
            new IJImageDisplayer().showImage(image);
            Utils.plotProfile("yProj", yProj);
            //Utils.plotProfile("Sliding sigma", slidingSigma);
            logger.debug("Optical Aberration detection: minIdx: {}, maxIdx: {}, peakHeightThld: {}, enfOfPeak: {}, low limit of Mc: {}", minIdx, maxIdx, thld, endOfPeakYIdx, startOfMicroChannel);
        }
        return startOfMicroChannel;
    }
    // tooltip interface
    @Override
    public String getToolTipText() {
        return toolTip;
    }
}
