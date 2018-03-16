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

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.data_structure.input_image.InputImages;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObjectProcessing;
import ij.process.AutoThresholder;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.processing.ImageOperations;
import static boa.image.processing.ImageOperations.threshold;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import boa.plugins.SimpleThresholder;
import boa.plugins.Thresholder;
import boa.plugins.TransformationTimeIndependent;
import boa.plugins.plugins.thresholders.BackgroundThresholder;
import boa.plugins.plugins.segmenters.MicrochannelFluo2D;
import boa.plugins.MicrochannelSegmenter.Result;

/**
 *
 * @author jollion
 */
public class CropMicrochannelsFluo2D extends CropMicroChannels {
    
    NumberParameter minObjectSize = new BoundedNumberParameter("Object Size Filter", 0, 200, 1, null);
    NumberParameter fillingProportion = new BoundedNumberParameter("Filling proportion of Microchannel", 2, 0.5, 0.05, 1);
    PluginParameter<SimpleThresholder> threshold = new PluginParameter<>("Intensity Threshold", SimpleThresholder.class, new BackgroundThresholder(3, 6, 3), false);   //new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu)
    Parameter[] parameters = new Parameter[]{channelHeight, cropMarginY, minObjectSize, threshold, fillingProportion, xStart, xStop, yStart, yStop, number};
    
    public CropMicrochannelsFluo2D(int channelHeight, int cropMargin, int minObjectSize, double fillingProportion, int timePointNumber) {
        this.channelHeight.setValue(channelHeight);
        this.cropMarginY.setValue(cropMargin);
        this.minObjectSize.setValue(minObjectSize);
        this.fillingProportion.setValue(fillingProportion);
        this.number.setValue(timePointNumber);
    }
    
    public CropMicrochannelsFluo2D() {
        //this.margin.setValue(30);
    }
    public CropMicrochannelsFluo2D setThresholder(SimpleThresholder instance) {
        this.threshold.setPlugin(instance);
        return this;
    }
    public CropMicrochannelsFluo2D setTimePointNumber(int timePointNumber) {
        this.number.setValue(timePointNumber);
        return this;
    }
    public CropMicrochannelsFluo2D setChannelDim(int channelHeight, double fillingProportion) {
        this.channelHeight.setValue(channelHeight);
        this.fillingProportion.setValue(fillingProportion);
        return this;
    }
    public CropMicrochannelsFluo2D setParameters(int minObjectSize) {
        this.minObjectSize.setValue(minObjectSize);
        return this;
    }
    @Override
    public MutableBoundingBox getBoundingBox(Image image) {
        double thld = this.threshold.instanciatePlugin().runSimpleThresholder(image, null);
        return getBoundingBox(image, null , thld);
    }
    
    public MutableBoundingBox getBoundingBox(Image image, ImageInteger thresholdedImage, double threshold) {
        if (debug) testMode = true;
        Result r = MicrochannelFluo2D.segmentMicroChannels(image, thresholdedImage, 0, 0, this.channelHeight.getValue().intValue(), this.fillingProportion.getValue().doubleValue(), threshold, this.minObjectSize.getValue().intValue(), testMode);
        if (r == null) return null;
        int cropMargin = this.cropMarginY.getValue().intValue();
        int xStart = this.xStart.getValue().intValue();
        int xStop = this.xStop.getValue().intValue();
        int yStart = this.yStart.getValue().intValue();
        int yStop = this.yStop.getValue().intValue();
        int yMin = Math.max(yStart, r.yMin);
        if (yStop==0) yStop = image.sizeY()-1;
        if (xStop==0) xStop = image.sizeX()-1;
        yStop = Math.min(yStop, yMin+channelHeight.getValue().intValue() + cropMargin);
        
        yStart = Math.max(yMin-cropMargin, yStart);
        
        //xStart = Math.max(xStart, r.getXMin()-cropMargin);
        //xStop = Math.min(xStop, r.getXMax() + cropMargin);
        
        if (testMode) logger.debug("Xmin: {}, Xmax: {}", r.getXMin(), r.getXMax());
        return new MutableBoundingBox(xStart, xStop, yStart, yStop, 0, image.sizeZ()-1);
        
    }
    

    @Override public Parameter[] getParameters() {
        return parameters;
    }
    
    boolean testMode;
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}
}
