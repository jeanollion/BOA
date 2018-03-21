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
import boa.image.BoundingBox;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import boa.image.SimpleBoundingBox;
import boa.image.processing.ImageFeatures;
import boa.image.processing.ImageOperations;
import static boa.plugins.plugins.segmenters.MicrochannelPhase2D.segmentMicroChannels;
import boa.plugins.MicrochannelSegmenter.Result;
import boa.plugins.plugins.pre_filters.IJSubtractBackground;
import static boa.plugins.plugins.segmenters.MicrochannelPhase2D.searchYLimWithOpticalAberration;
import boa.utils.ArrayUtil;
import java.util.Arrays;

/**
 *
 * @author jollion
 */
public class CropMicrochannelsPhase2D extends CropMicroChannels {
    public static boolean debug = false;
    NumberParameter yEndMargin = new BoundedNumberParameter("Distance between end of channel and optical aberration", 0, 30, 0, null).setToolTipText("Additional margin added between open-end of microchannels and optical aberration in Y direction");
    Parameter[] parameters = new Parameter[]{channelHeight, cropMarginY, yEndMargin, boundGroup};
    
    public CropMicrochannelsPhase2D(int cropMarginY) {
        this();
        this.cropMarginY.setValue(cropMarginY);
    }
    public CropMicrochannelsPhase2D() {
        this.referencePoint.setSelectedIndex(1);
        this.frameNumber.setValue(0);
    }
    
    @Override public MutableBoundingBox getBoundingBox(Image image) {
        return getBoundingBox(image, cropMarginY.getValue().intValue(), channelHeight.getValue().intValue(),xStart.getValue().intValue(), xStop.getValue().intValue(), yStart.getValue().intValue(), yStop.getValue().intValue(), yEndMargin.getValue().intValue());
    }
    
    public MutableBoundingBox getBoundingBox(Image image, int cropMargin, int channelHeight,  int xStart, int xStop, int yStart, int yStop, int yMarginEndChannel) {
        if (debug) testMode = true;
        int yMax =  searchYLimWithOpticalAberration(image, 0.25, yMarginEndChannel, testMode) ;
        if (yMax<0) throw new RuntimeException("No optical aberration found");
        // in case image was rotated and 0 were added, search for xMin & xMax so that no 0's are in the image
        BoundingBox nonNullBound = getNonNullBound(image, yMax);
        if (testMode) logger.debug("non null bounds: {}", nonNullBound);
        Image imCrop = image.crop(nonNullBound);
        Image imDerY = ImageFeatures.getDerivative(imCrop, 2, 0, 1, 0, true);
        float[] yProj = ImageOperations.meanProjection(imDerY, ImageOperations.Axis.Y, null);
        int yMin = ArrayUtil.max(yProj, 0, yProj.length-1-channelHeight/2) + nonNullBound.yMin();
        //if (yMax<=0) yMax = yMin + channelHeight;
        
        if (yStop==0) yStop = image.sizeY()-1;
        if (xStop==0) xStop = image.sizeX()-1;
        //yMax = Math.min(yMin+channelHeight, yMax);
        yMin = Math.max(yStart,yMin);
        yStop = Math.min(yStop, yMax);
        yStart = Math.max(yMin-cropMargin, yStart);
        
        xStart = Math.max(nonNullBound.xMin(), xStart);
        xStop = Math.min(xStop, nonNullBound.xMax());
        return new MutableBoundingBox(xStart, xStop, yStart, yStop, 0, image.sizeZ()-1);
        
    }
    private static BoundingBox getNonNullBound(Image image, int yMax) {
        int[] xMinMaxDown = getXMinAndMax(image, yMax);
        if (xMinMaxDown[0]==0 && xMinMaxDown[1]==image.sizeX()-1) return  image.getBoundingBox().setyMax(yMax); // no null values 
        int[] yMinMaxLeft = getYMinAndMax(image, xMinMaxDown[0]);
        int[] yMinMaxRigth = getYMinAndMax(image, xMinMaxDown[1]);
        int yMin = Math.min(yMinMaxLeft[0], yMinMaxRigth[1]);
        int[] xMinMaxUp = getXMinAndMax(image, yMin);
        return new SimpleBoundingBox(Math.max(xMinMaxDown[0], xMinMaxUp[0]), Math.min(xMinMaxDown[1], xMinMaxUp[1]), yMin, yMax, image.zMin(), image.zMax());
    }
    
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }

}
