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
package boa.plugins.plugins.transformations;

import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.FilterSequence;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import static boa.plugins.plugins.segmenters.MicrochannelPhase2D.segmentMicroChannels;
import boa.plugins.MicrochannelSegmenter.Result;
import boa.plugins.plugins.pre_filters.IJSubtractBackground;

/**
 *
 * @author jollion
 */
public class CropMicroChannelBF2D extends CropMicroChannels {
    public static boolean debug = false;
    NumberParameter microChannelWidth = new BoundedNumberParameter("Microchannel Typical Width (pixels)", 0, 20, 5, null);
    NumberParameter microChannelWidthMin = new BoundedNumberParameter("MicroChannel Width Min(pixels)", 0, 15, 5, null);
    NumberParameter microChannelWidthMax = new BoundedNumberParameter("MicroChannel Width Max(pixels)", 0, 28, 5, null);
    NumberParameter yEndMargin = new BoundedNumberParameter("Distance between end of channel and optical aberration", 0, 30, 0, null);
    NumberParameter localDerExtremaThld = new BoundedNumberParameter("X-Derivative Threshold (absolute value)", 3, 10, 0, null).setToolTipText("Threshold for Microchannel border detection (peaks of 1st derivative in X-axis)");;
    FilterSequence filters = new FilterSequence("Pre-Filters").add(new IJSubtractBackground(10, true, false, true, false));
    Parameter[] parameters = new Parameter[]{filters, channelHeight, cropMarginY, microChannelWidth, microChannelWidthMin, microChannelWidthMax, localDerExtremaThld, yEndMargin, xStart, xStop, yStart, yStop, number};
    
    public CropMicroChannelBF2D(int cropMarginY, int microChannelWidth, double microChannelWidthMin, int microChannelWidthMax, int timePointNumber) {
        this.cropMarginY.setValue(cropMarginY);
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
    public CropMicroChannelBF2D setChannelWidth(int microChannelWidth, double microChannelWidthMin, int microChannelWidthMax) {
        this.microChannelWidth.setValue(microChannelWidth);
        this.microChannelWidthMin.setValue(microChannelWidthMin);
        this.microChannelWidthMax.setValue(microChannelWidthMax);
        return this;
    }
    public CropMicroChannelBF2D setLocalDerivateXThld(double thld) {
        this.localDerExtremaThld.setValue(thld);
        return this;
    }
    @Override public MutableBoundingBox getBoundingBox(Image image) {
        image = filters.filter(image);
        return getBoundingBox(image, cropMarginY.getValue().intValue(), channelHeight.getValue().intValue(),xStart.getValue().intValue(), xStop.getValue().intValue(), yStart.getValue().intValue(), yStop.getValue().intValue(), 0, yEndMargin.getValue().intValue());
    }
    
    public MutableBoundingBox getBoundingBox(Image image, int cropMargin, int channelHeight,  int xStart, int xStop, int yStart, int yStop, int yStartAdjustWindow, int yMarginEndChannel) {
        if (debug) testMode = true;
        Result r = segmentMicroChannels(image, true, yStartAdjustWindow, yMarginEndChannel, microChannelWidth.getValue().intValue(), microChannelWidthMin.getValue().intValue(), microChannelWidthMax.getValue().intValue(), localDerExtremaThld.getValue().doubleValue(), debug);
        if (r==null || r.xMax.length==0) return null;
        int yMin = r.getYMin();
        int yMax = r.getYMax();
        if (yStop==0) yStop = image.sizeY()-1;
        if (xStop==0) xStop = image.sizeX()-1;
        yMax = Math.min(yMin+channelHeight, yMax);
        yMin = Math.max(yStart,yMin);
        yStop = Math.min(yStop, yMax);
        yStart = Math.max(yMin-cropMargin, yStart);
        
        //xStart = Math.max(xStart, r.getXMin()-cropMargin);
        //xStop = Math.min(xStop, r.getXMax() + cropMargin);
        if (testMode) logger.debug("Xmin: {}, Xmax: {}", r.getXMin(), r.getXMax());
        return new MutableBoundingBox(xStart, xStop, yStart, yStop, 0, image.sizeZ()-1);
        
    }
    
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    boolean testMode;
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}

}
