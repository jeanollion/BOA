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
import boa.gui.image_interaction.IJImageDisplayer;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.ConditionalParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectProcessing;
import boa.data_structure.Voxel;
import ij.process.AutoThresholder;
import boa.image.BlankMask;
import boa.image.Histogram;
import boa.image.HistogramFactory;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.ImageMask;
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
import boa.plugins.TestableProcessingPlugin;
import boa.plugins.ToolTip;
import boa.plugins.TrackParametrizable;
import boa.plugins.plugins.segmenters.MicrochannelPhase2D.X_DER_METHOD;
import boa.plugins.plugins.thresholders.BackgroundFit;
import boa.plugins.plugins.thresholders.IJAutoThresholder;
import boa.plugins.plugins.transformations.CropMicrochannelsPhase2D;
import boa.utils.ArrayUtil;
import boa.utils.Utils;
import static boa.utils.Utils.plotProfile;
import ij.gui.Plot;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 *
 * @author jollion
 */
public class MicrochannelPhase2D implements MicrochannelSegmenter, TestableProcessingPlugin, ToolTip, TrackParametrizable<MicrochannelPhase2D> {

    
    public enum X_DER_METHOD {CONSTANT, RELATIVE_TO_INTENSITY_RANGE}
    NumberParameter channelWidth = new BoundedNumberParameter("Typical Width", 0, 20, 5, null).setToolTipText("Typical width of microchannels, in pixels");
    NumberParameter channelWidthMin = new BoundedNumberParameter("Min Width", 0, 15, 5, null).setToolTipText("Minimal width of microchannels, in pixels");
    NumberParameter channelWidthMax = new BoundedNumberParameter("Max Width", 0, 28, 5, null).setToolTipText("Maximal width of microchannels, in pixels");
    NumberParameter closedEndYAdjustWindow = new BoundedNumberParameter("Closed-end Y Adjust Window", 0, 5, 0, null).setToolTipText("Window (in pixels) within which y-coordinate of the closed-end of microchannel will be refined, by searching for the first local maximum of the Y-derivate within the window: [y-this value; y+this value]");
    ChoiceParameter xDerPeakThldMethod = new ChoiceParameter("X-Derivative Threshold Method", Utils.toStringArray(X_DER_METHOD.values()), X_DER_METHOD.RELATIVE_TO_INTENSITY_RANGE.toString(), false);
    NumberParameter localDerExtremaThld = new BoundedNumberParameter("X-Derivative Threshold", 3, 10, 0, null).setToolTipText("Threshold for Microchannel side detection (peaks of 1st derivative in X-axis). <br />This parameter will depend on the intensity of the image and should be adjusted if microchannels are poorly detected. <br />Configuration Hint: Refer to side detection plot (displayed through right-click menu) to display peak heights.<br />A higher value if too many channels are detected and a lower value in the contrary.");
    NumberParameter relativeDerThld = new BoundedNumberParameter("X-Derivative Ratio", 3, 40, 1, null).setToolTipText("To compute x-derivative threshold for peaks, the signal range is computed range = the median signal value - mean backgroud value. X-derivative threshold = signal range / this ratio.<br />Configuration Hint: Refer to side detection plot (displayed through right-click menu) to display peak heights. <br />Decrease this value if too many microchannels are detected.");
    ConditionalParameter xDerPeakThldCond = new ConditionalParameter(xDerPeakThldMethod).setActionParameters(X_DER_METHOD.CONSTANT.toString(), localDerExtremaThld).setActionParameters(X_DER_METHOD.RELATIVE_TO_INTENSITY_RANGE.toString(), relativeDerThld).setEmphasized(true).setToolTipText("Side detection: peak selection method. <ol><li>"+X_DER_METHOD.CONSTANT.toString()+": Constant threshold</li><li>"+X_DER_METHOD.RELATIVE_TO_INTENSITY_RANGE.toString()+": Relative to signal Range. This method is more adapted when signal range can vary from one experiment to another</li></ol> <br/ >");
    Parameter[] parameters = new Parameter[]{channelWidth, channelWidthMin, channelWidthMax, xDerPeakThldCond}; //sigmaThreshold
    public final static double PEAK_RELATIVE_THLD = 0.6;
    public static boolean debug = false;
    public static int debugIdx = -1;
    protected String toolTip = "<b>Microchannel Segmentation in phase-contrast images:</b>"
            + "<ol><li>Search for global closed-end y-coordinate of Microchannels: global max of the Y-proj of d/dy (requires no optical aberration is present on the image)</li>"
            + "<li>Search of x-positions of microchannels using X-projection of d/dx image & peak detection: <br />"
            + "(detection of positive peask & negative peaks with amplitude over <em>X-derivative Threshold</em> separated by a distance as close as possible to <em>Typical Width</em> and in the range [widthMin; widthMax]</li>"
            + "<li>Adjust yStart for each channel: first local max of d/dy image in the range [yEnd-  AdjustWindow ; yEnd+ AdjustWindow]</li></ol>";

    public MicrochannelPhase2D() {}

    // testable
    Map<StructureObject, TestDataStore> stores;
    @Override public void setTestDataStore(Map<StructureObject, TestDataStore> stores) {
        this.stores=  stores;
    }
    
    public MicrochannelPhase2D setyStartAdjustWindow(int yStartAdjustWindow) {
        this.closedEndYAdjustWindow.setValue(yStartAdjustWindow);
        return this;
    }
    @Override
    public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        Result r = segment(input, structureIdx, parent);
        if (r==null) return null;
        ArrayList<Region> objects = new ArrayList<>(r.size());
        for (int idx = 0; idx<r.xMax.length; ++idx) {
            objects.add(new Region(new BlankMask(r.getBounds(idx, true), input.getScaleXY(), input.getScaleZ()), idx+1, true));
            //logger.debug("mc: {}: bds: {}", idx, objects.get(objects.size()-1).getBounds());
        }
        return new RegionPopulation(objects, input);
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
     * @return Result object containing bounding boxes of segmented microchannels
     */
    @Override
    public Result segment(Image image, int structureIdx, StructureObjectProcessing parent) {
        int closedEndYAdjustWindow = this.closedEndYAdjustWindow.getValue().intValue();
        int channelWidth = this.channelWidth.getValue().intValue();
        int channelWidthMin = this.channelWidthMin.getValue().intValue();
        int channelWidthMax = this.channelWidthMax.getValue().intValue();
        double localDerExtremaThld;
        switch(X_DER_METHOD.valueOf(xDerPeakThldMethod.getSelectedItem())) {
            case CONSTANT:
                localDerExtremaThld = this.localDerExtremaThld.getValue().doubleValue();
                break;
            case RELATIVE_TO_INTENSITY_RANGE:
            default:
                localDerExtremaThld = this.globalLocalDerThld;
                if (Double.isNaN(globalLocalDerThld)) throw new RuntimeException("Global X-Der threshold not set");
        }
        
        double derScale = 2;
        // get aberration
        int[] yStartStop = new int[]{0, image.sizeY()-1};
        Image imCrop = (image instanceof ImageFloat ? image.duplicate() : image);
        
        // get global closed-end Y coordinate
        Image imDerY = ImageFeatures.getDerivative(imCrop, derScale, 0, 1, 0, true);
        float[] yProj = ImageOperations.meanProjection(imDerY, ImageOperations.Axis.Y, null);
        int closedEndY = ArrayUtil.max(yProj, 0, yProj.length) + yStartStop[0]; 

        // get X coordinates of each microchannel
        imCrop = image.crop(new MutableBoundingBox(0, image.sizeX()-1, closedEndY, image.sizeY()-1, 0, image.sizeZ()-1));
        float[] xProj = ImageOperations.meanProjection(imCrop, ImageOperations.Axis.X, null);
        // check for null values @ start & end that could be introduces by rotation and replace by first non-null value
        int start = 0;
        while (start<xProj.length && xProj[start]==0) ++start;
        if (start>0) Arrays.fill(xProj, 0, start, xProj[start]);
        int end = xProj.length-1;
        while (end>0 && xProj[end]==0) --end;
        if (end<xProj.length-1) Arrays.fill(xProj, end+1, xProj.length, xProj[end]);
        //derivate
        ArrayUtil.gaussianSmooth(xProj, 1); // derScale
        Image imDerX = ImageFeatures.getDerivative(imCrop, derScale, 1, 0, 0, true);
        float[] xProjDer = ImageOperations.meanProjection(imDerX, ImageOperations.Axis.X, null);
        
        if (stores!=null) {
            stores.get(parent).addMisc("Show test data", l->{
                ImageWindowManagerFactory.showImage(imDerY.setName("Closed-end detection image (dI/dy)"));
                ImageWindowManagerFactory.showImage(imDerX.setName("Side detection image (dI/dx)"));
                plotProfile("Closed-end detection (mean projection)", yProj, "y", "dI/dy");
                plotProfile("Side detection (mean projection of dI/dx) Threshold: "+localDerExtremaThld+(this.xDerPeakThldMethod.getSelectedIndex()==1? " Signal Range: "+(relativeDerThld.getValue().doubleValue()*localDerExtremaThld) : ""), xProjDer, "x", "dI/dx");
                //plotProfile("Side dectection (mean projection of I)", xProj);
            });
        }
        
        final float[] derMap = xProjDer;
        List<Integer> localMax = ArrayUtil.getRegionalExtrema(xProjDer, (int)(derScale+0.5), true);
        List<Integer> localMin = ArrayUtil.getRegionalExtrema(xProjDer, (int)(derScale+0.5), false);
        final Predicate<Integer> rem = i -> Math.abs(derMap[i])<localDerExtremaThld ;
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
        
        if (stores!=null) {
            logger.debug("{} max found, {} min found", localMax.size(), localMin.size());
            logger.debug("max: {}", localMax);
            logger.debug("min: {}", localMin);
        }
        
        List<int[]> peaks = new ArrayList<>();
        int lastMinIdx = 0;
        int maxIdx = 0;
        while (maxIdx<localMax.size()) {
            if (stores!=null) logger.debug("VALID MAX: {}", localMax.get(maxIdx));
            int minIdx = getNextMinIdx(derMap, localMin, localMax, maxIdx, lastMinIdx, channelWidthMin,channelWidthMax, segmentScoreComparator, stores!=null);
            if (minIdx>=0 ) {
                // check all valid max between current max and min
                int nextMaxIdx = maxIdx+1;
                while (nextMaxIdx<localMax.size() && localMax.get(nextMaxIdx)<localMin.get(minIdx)) {
                    if (Math.abs(derMap[localMax.get(maxIdx)])*PEAK_RELATIVE_THLD<Math.abs(derMap[localMax.get(nextMaxIdx)])) {
                        int nextMinIdx = getNextMinIdx(derMap, localMin, localMax, nextMaxIdx, lastMinIdx, channelWidthMin,channelWidthMax, segmentScoreComparator, stores!=null);
                        if (nextMinIdx>=0) {
                            int comp = segmentScoreComparator.compare(new int[]{maxIdx, minIdx}, new int[]{nextMaxIdx, nextMinIdx});
                            if (comp>0) {
                                maxIdx = nextMaxIdx;
                                minIdx = nextMinIdx;
                                if (stores!=null) logger.debug("BETTER VALID MAX: {}, d: {}", localMax.get(maxIdx), localMin.get(minIdx) - localMax.get(maxIdx));
                            }
                        }
                    }
                    ++nextMaxIdx;
                }
                if (stores!=null) {
                    int x1 = localMax.get(maxIdx);
                    int x2 = localMin.get(minIdx);
                    logger.debug("Peak found X: [{};{}], distance: {}, value: [{};{}], normedValue: [{};{}]", x1, x2, localMin.get(minIdx) - localMax.get(maxIdx), xProjDer[x1], xProjDer[x2], xProjDer[x1]/xProj[x1], xProjDer[x2]/xProj[x2]);
                }
                peaks.add(new int[]{localMax.get(maxIdx), localMin.get(minIdx), 0});
                lastMinIdx = minIdx;
                maxIdx = nextMaxIdx; // first max after min
            } else ++maxIdx;
        }
        
        // refine Y-coordinate of closed-end for each microchannel. As MC shape is generally ellipsoidal @ close-end, only get the profile in the 1/3-X center part
        if (closedEndYAdjustWindow>0) {
            for (int idx = 0; idx<peaks.size();++idx) {
                int[] peak = peaks.get(idx);
                double sizeX = peak[1]-peak[0]+1;
                MutableBoundingBox win = new MutableBoundingBox((int)(peak[0]+sizeX/3+0.5), (int)(peak[1]-sizeX/3+0.5), Math.max(0, closedEndY-closedEndYAdjustWindow), Math.min(imDerY.sizeY()-1, closedEndY+closedEndYAdjustWindow), 0, 0);
                float[] proj = ImageOperations.meanProjection(imDerY, ImageOperations.Axis.Y, win);
                List<Integer> localMaxY = ArrayUtil.getRegionalExtrema(proj, 2, true);
                //peak[2] = ArrayUtil.max(proj)-yStartAdjustWindow;
                if (localMaxY.isEmpty()) continue;
                peak[2] = localMaxY.get(0)- (closedEndY>=closedEndYAdjustWindow ? closedEndYAdjustWindow : 0);
                if (stores!=null) {
                    int ii = idx;
                    stores.get(parent).addMisc("Display closed-end adjument", l -> {
                        Set<Integer> idxes = l.stream().map(o -> o.getIdx()).collect(Collectors.toSet());
                        if (idxes.contains(ii)) Utils.plotProfile("Closed-end y-adjustment: first local max @ y=:"+(localMaxY.get(0)+win.yMin()), proj, win.yMin(), "y", "dI/dy");
                    });
                    
                }
            }
        }
        Result r= new Result(peaks, closedEndY, image.sizeY()-1);
         
        int xLeftErrode = (int)(derScale/2.0+0.5); // adjust Y: remove derScale from left 
        for (int i = 0; i<r.size(); ++i) r.xMax[i]-=xLeftErrode;
        if (stores!=null) for (int i = 0; i<r.size(); ++i) logger.debug("mc: {} -> {}", i, r.getBounds(i, true));
        return r;
    }
    double globalLocalDerThld = Double.NaN;
    // track parametrizable interface
    @Override
    public TrackParametrizer<MicrochannelPhase2D> run(int structureIdx, List<StructureObject> parentTrack) {
        switch(X_DER_METHOD.valueOf(xDerPeakThldMethod.getSelectedItem())) {
            case CONSTANT:
                return null;
            case RELATIVE_TO_INTENSITY_RANGE:
            default:
                // compute signal range on all images
                //logger.debug("parent track: {}",parentTrack.stream().map(p->p.getPreFilteredImage(structureIdx)).collect(Collectors.toList()) );
                Map<Image, ImageMask> maskMap = parentTrack.stream().collect(Collectors.toMap(p->p.getPreFilteredImage(structureIdx), p->p.getMask()));
                Histogram histo = HistogramFactory.getHistogram(()->Image.stream(maskMap, true).parallel(), HistogramFactory.BIN_SIZE_METHOD.AUTO_WITH_LIMITS);
                double thld = IJAutoThresholder.runThresholder(AutoThresholder.Method.Otsu, histo);
                int thldIdx = (int)histo.getIdxFromValue(thld);
                double foreground = histo.duplicate(thldIdx, histo.data.length).getQuantiles(0.5)[0];
                double background = histo.getValueFromIdx(histo.getMeanIdx(0, thldIdx-1));
                double range =foreground - background;
                double xDerThld = range / this.relativeDerThld.getValue().doubleValue(); // divide by ratio and set to segmenter
                return (p, s) -> s.globalLocalDerThld = xDerThld;
        }
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
    // tooltip interface
    @Override
    public String getToolTipText() {
        return toolTip;
    }
    
}
