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
package boa.plugins.plugins.transformations;

import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.FilterSequence;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.input_image.InputImage;
import boa.data_structure.input_image.InputImages;
import boa.gui.image_interaction.IJImageDisplayer;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.image.BoundingBox;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import boa.image.SimpleBoundingBox;
import boa.image.processing.ImageFeatures;
import boa.image.processing.ImageOperations;
import boa.plugins.Plugin;
import boa.plugins.ToolTip;
import boa.utils.ArrayUtil;
import boa.utils.Pair;
import boa.utils.Utils;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jollion
 */
public class CropMicrochannelsPhase2D extends CropMicroChannels implements ToolTip {
    private final static Logger logger = LoggerFactory.getLogger(CropMicrochannelsPhase2D.class);
    public static boolean debug = false;
    protected String toolTip = "<b>Microchannel Detection in phase-contrast images</b><br />"
            + "Based on detection of optical aberration (shadow of opened-end of microchannels in phase contrast imaging) as the max of Y mean profile. End of peak is determined using the <em>peak proportion</em> parameter and height using the <em>channel length</em> parameter. <br />Supposes optical aberration is the highest peak. Refer to plot <em>Peak Detection</em><br />"
            + "Closed-end of microchannels is detected as the highest peak of dI/dy (refer to graph <em>Closed-end detection</em>) after excluding the optical aberration.<br />"
            + "If a previous rotation has added null values to the image corners, the final bounding box is ensured to exclude them";
    NumberParameter abberationPeakProp = new BoundedNumberParameter("Optical aberration peak proportion", 3, 0.25, 0.1, 1).setEmphasized(true).setToolTipText("The end of optical aberration is determined as the first y index (in y-mean projection values) towards closed-end of microchanel that reach: this value * peak height<br />Depending on phase-contrast setup, the optical aberration can different tail profile. <br /> A lower value will keep more tail, a higher value will remove tail. A too low value can lead to unstable results over frames.<br />Refer to plot <em>Peak Detection</em>");
    NumberParameter yEndMargin = new BoundedNumberParameter("Distance between end of channel and optical aberration", 0, 60, 0, null).setToolTipText("Additional margin added between open-end of microchannels and optical aberration in Y direction");
    Parameter[] parameters = new Parameter[]{abberationPeakProp, yEndMargin, cropMarginY, boundGroup};
    @Override public String getToolTipText() {return toolTip;}
    public CropMicrochannelsPhase2D(int cropMarginY) {
        this();
        this.cropMarginY.setValue(cropMarginY);
    }
    public CropMicrochannelsPhase2D() {
        this.referencePoint.setSelectedIndex(1);
        this.frameNumber.setValue(0);
    }
    
    @Override public MutableBoundingBox getBoundingBox(Image image) {
        return getBoundingBox(image, cropMarginY.getValue().intValue(),xStart.getValue().intValue(), xStop.getValue().intValue(), yStart.getValue().intValue(), yStop.getValue().intValue(), yEndMargin.getValue().intValue());
    }
    
    public MutableBoundingBox getBoundingBox(Image image, int cropMargin,  int xStart, int xStop, int yStart, int yStop, int yMarginEndChannel) {
        if (debug) testMode = true;
        int yMax =  searchYLimWithOpticalAberration(image, abberationPeakProp.getValue().doubleValue(), yMarginEndChannel, testMode) ;
        if (yMax<0) throw new RuntimeException("No optical aberration found");
        // in case image was rotated and 0 were added, search for xMin & xMax so that no 0's are in the image
        BoundingBox nonNullBound = getNonNullBound(image, yMax);
        if (testMode) logger.debug("non null bounds: {}", nonNullBound);
        Image imCrop = image.crop(nonNullBound);
        Image imDerY = ImageFeatures.getDerivative(imCrop, 2, 0, 1, 0, true);
        float[] yProj = ImageOperations.meanProjection(imDerY, ImageOperations.Axis.Y, null);
        if (testMode) Utils.plotProfile("Closed-end detection", yProj, nonNullBound.yMin(), "y", "dI/dy");
        // when optical aberration is very extended, actual length of micro-channels can be way smaller than the parameter -> no check
        //if (yProj.length-1<channelHeight/10) throw new RuntimeException("No microchannels found in image. Out-of-Focus image ?");
        int yMin = ArrayUtil.max(yProj, 0, yProj.length-1) + nonNullBound.yMin();
        //if (yMax<=0) yMax = yMin + channelHeight;
        
        if (yStop==0) yStop = image.sizeY()-1;
        if (xStop==0) xStop = image.sizeX()-1;
        //yMax = Math.min(yMin+channelHeight, yMax);
        yMin = Math.max(yStart,yMin);
        yStop = Math.min(yStop, yMax);
        yStart = Math.max(yMin-cropMargin, Math.max(yStart, nonNullBound.yMin()));
        
        xStart = Math.max(nonNullBound.xMin(), xStart);
        xStop = Math.min(xStop, nonNullBound.xMax());
        return new MutableBoundingBox(xStart, xStop, yStart, yStop, 0, image.sizeZ()-1);
        
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
        float[] yProj = ImageOperations.meanProjection(image, ImageOperations.Axis.Y, null, (double v) -> v > 0); // when image was rotated by a high angle zeros are introduced
        ArrayUtil.gaussianSmooth(yProj, 10);
        int start = getFirstNonNanIdx(yProj, true);
        int end = getFirstNonNanIdx(yProj, false);
        int peakIdx = ArrayUtil.max(yProj, start, end + 1);
        double median = ArrayUtil.median(Arrays.copyOfRange(yProj, start, end + 1 - start));
        double peakHeight = yProj[peakIdx] - median;
        float thld = (float) (peakHeight * peakProportion + median);
        int endOfPeakYIdx = ArrayUtil.getFirstOccurence(yProj, peakIdx, start, thld, true, true);
        // TODO -> get peak width using the half width on the side opposed to microchannels -> remove 2 x half width
        int startOfMicroChannel = endOfPeakYIdx - margin;
        if (testMode) {
            ImageWindowManagerFactory.showImage(image.setName("Peak detection Input Image"));
            Utils.plotProfile("Peak Detection: detected at y = "+peakIdx+" peak end:"+endOfPeakYIdx+" end of microchannels:"+startOfMicroChannel, yProj, "Y", "Mean Intensity projection along X");
            //Utils.plotProfile("Sliding sigma", slidingSigma);
            Plugin.logger.debug("Optical Aberration detection: start mc / end peak/ peak: idx: [{};{};{}], values: [{};{};{}]", startOfMicroChannel, endOfPeakYIdx, peakIdx, median, thld, yProj[peakIdx]);
        }
        return startOfMicroChannel;
    }
    private static int getFirstNonNanIdx(float[] array, boolean fromStart) {
        if (fromStart) {
            int start = 0;
            while (start<array.length && Float.isNaN(array[start])) ++start;
            return start;
        } else {
            int end = array.length-1;
            while (end>0 && Float.isNaN(array[end])) --end;
            return end;
        }
    }
    
    private static BoundingBox getNonNullBound(Image image, int yMax) {
        int[] xMinMaxDown = getXMinAndMax(image, yMax);
        if (xMinMaxDown[0]==0 && xMinMaxDown[1]==image.sizeX()-1) return  image.getBoundingBox().setyMax(yMax); // no null values 
        int[] yMinMaxLeft = getYMinAndMax(image, xMinMaxDown[0]);
        int[] yMinMaxRigth = getYMinAndMax(image, xMinMaxDown[1]);
        int yMin = Math.min(yMinMaxLeft[0], yMinMaxRigth[0]);
        int[] xMinMaxUp = getXMinAndMax(image, yMin);
        return new SimpleBoundingBox(Math.max(xMinMaxDown[0], xMinMaxUp[0]), Math.min(xMinMaxDown[1], xMinMaxUp[1]), yMin, yMax, image.zMin(), image.zMax());
    }
    
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }

    @Override
    protected void uniformizeBoundingBoxes(Map<Integer, MutableBoundingBox> allBounds, InputImages inputImages, int channelIdx) {
        
        int maxSizeY = allBounds.values().stream().mapToInt(b->b.sizeY()).max().getAsInt();
        int sY = allBounds.entrySet().stream().mapToInt(b-> {
            int yMinNull = getYmin(inputImages.getImage(channelIdx, b.getKey()), b.getValue().xMin(), b.getValue().xMax()); // limit sizeY so that no null pixels (due to rotation) is present in the image & not out-of-bounds
            return b.getValue().yMax() - Math.max(b.getValue().yMax()-(maxSizeY-1), yMinNull)+1;
        }).min().getAsInt();
        //logger.info("max size Y: {} uniformized sizeY: {}", maxSizeY, sY);
        //logger.info("all bounds: {}", allBounds.entrySet().stream().filter(e->e.getKey()%100==0).sorted((e1, e2)->Integer.compare(e1.getKey(), e2.getKey())).map(e->new Pair(e.getKey(), e.getValue())).collect(Collectors.toList()));
        allBounds.values().stream().filter(bb->bb.sizeY()!=sY).forEach(bb-> bb.setyMin(bb.yMax()-(sY-1)));
        //logger.info("all bounds after uniformize Y: {}", allBounds.entrySet().stream().filter(e->e.getKey()%100==0).sorted((e1, e2)->Integer.compare(e1.getKey(), e2.getKey())).map(e->new Pair(e.getKey(), e.getValue())).collect(Collectors.toList()));
        uniformizeX(allBounds);
        
    }

}
