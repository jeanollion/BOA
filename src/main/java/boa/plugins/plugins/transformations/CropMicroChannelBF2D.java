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

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.input_image.InputImages;
import boa.data_structure.Region;
import ij.process.AutoThresholder;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.processing.ImageOperations;
import static boa.image.processing.ImageOperations.threshold;
import java.util.ArrayList;import java.util.Comparator;
;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.commons.lang.ArrayUtils;
import boa.image.processing.ImageFeatures;
import static boa.plugins.plugins.segmenters.MicrochannelPhase2D.segmentMicroChannels;
import boa.plugins.plugins.segmenters.MicrochannelSegmenter.Result;
import boa.utils.ArrayUtil;
import boa.utils.Utils;
import static boa.utils.Utils.plotProfile;

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
    @Override public BoundingBox getBoundingBox(Image image) {
        return getBoundingBox(image, cropMargin.getValue().intValue(), margin.getValue().intValue(), channelHeight.getValue().intValue(),xStart.getValue().intValue(), xStop.getValue().intValue(), yStart.getValue().intValue(), yStop.getValue().intValue(), 0, yEndMargin.getValue().intValue());
    }
    
    public BoundingBox getBoundingBox(Image image, int cropMargin, int margin, int channelHeight,  int xStart, int xStop, int yStart, int yStop, int yStartAdjustWindow, int yMarginEndChannel) {
        if (debug) testMode = true;
        Result r = segmentMicroChannels(image, true, margin, yStartAdjustWindow, yMarginEndChannel, microChannelWidth.getValue().intValue(), microChannelWidthMin.getValue().intValue(), microChannelWidthMax.getValue().intValue(), localDerExtremaThld.getValue().doubleValue(), debug);
        if (r==null || r.xMax.length==0) return null;
        int yMin = r.getYMin();
        int yMax = r.getYMax();
        if (yStop==0) yStop = image.getSizeY()-1;
        if (xStop==0) xStop = image.getSizeX()-1;
        yMax = Math.min(yMin+channelHeight, yMax);
        yMin = Math.max(yStart,yMin);
        yStop = Math.min(yStop, yMax);
        yStart = Math.max(yMin-cropMargin, yStart);
        
        //xStart = Math.max(xStart, r.getXMin()-cropMargin);
        //xStop = Math.min(xStop, r.getXMax() + cropMargin);
        if (testMode) logger.debug("Xmin: {}, Xmax: {}", r.getXMin(), r.getXMax());
        return new BoundingBox(xStart, xStop, yStart, yStop, 0, image.getSizeZ()-1);
        
    }
    
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    boolean testMode;
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}

}
