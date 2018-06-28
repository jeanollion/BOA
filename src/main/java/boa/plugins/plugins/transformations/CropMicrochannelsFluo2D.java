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

import boa.gui.image_interaction.IJImageDisplayer;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.data_structure.input_image.InputImages;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectProcessing;
import boa.image.Histogram;
import boa.image.HistogramFactory;
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
import boa.plugins.plugins.thresholders.BackgroundThresholder;
import boa.plugins.plugins.segmenters.MicrochannelFluo2D;
import boa.plugins.MicrochannelSegmenter.Result;
import boa.plugins.plugins.thresholders.BackgroundFit;
import static boa.image.processing.ImageOperations.threshold;
import boa.measurement.BasicMeasurements;
import boa.plugins.ThresholderHisto;
import boa.plugins.ToolTip;
import static boa.plugins.plugins.segmenters.MicrochannelFluo2D.FILL_TOOL_TIP;
import static boa.plugins.plugins.segmenters.MicrochannelFluo2D.SIZE_TOOL_TIP;
import static boa.plugins.plugins.segmenters.MicrochannelFluo2D.THLD_TOOL_TIP;
import static boa.plugins.plugins.segmenters.MicrochannelFluo2D.TOOL_TIP;
import boa.plugins.plugins.thresholders.IJAutoThresholder;
import boa.utils.ArrayUtil;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 *
 * @author jollion
 */
public class CropMicrochannelsFluo2D extends CropMicroChannels implements ToolTip {
    protected NumberParameter channelHeight = new BoundedNumberParameter("Channel Height", 0, 410, 0, null);
    NumberParameter minObjectSize = new BoundedNumberParameter("Object Size Filter", 0, 200, 1, null).setToolTipText(SIZE_TOOL_TIP);
    NumberParameter fillingProportion = new BoundedNumberParameter("Filling proportion of Microchannel", 2, 0.5, 0.05, 1).setToolTipText(FILL_TOOL_TIP);
    PluginParameter<ThresholderHisto> thresholder = new PluginParameter<>("Threshold", ThresholderHisto.class, new BackgroundFit(10), false).setToolTipText(THLD_TOOL_TIP);   //new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu)
    
    Parameter[] parameters = new Parameter[]{channelHeight, cropMarginY, minObjectSize, thresholder, fillingProportion, boundGroup};
    double threshold = Double.NaN;
    public CropMicrochannelsFluo2D(int channelHeight, int cropMargin, int minObjectSize, double fillingProportion, int FrameNumber) {
        this.channelHeight.setValue(channelHeight);
        this.cropMarginY.setValue(cropMargin);
        this.minObjectSize.setValue(minObjectSize);
        this.fillingProportion.setValue(fillingProportion);
        this.referencePoint.setSelectedIndex(0);
        this.frameNumber.setValue(0);
        //frameNumber.setValue(FrameNumber);
    }
    
    @Override
    public String getToolTipText() {
        return "Crop input image around microchannels. Supposes that microchannels are aligned with Y-axis and that closed-end is located at the top of the image. Microchannels are detected as follow:<br />"+TOOL_TIP;
    }
    
    public CropMicrochannelsFluo2D() {
        //this.margin.setValue(30);
    }
    public CropMicrochannelsFluo2D setThresholder(ThresholderHisto instance) {
        this.thresholder.setPlugin(instance);
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
    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        // compute one threshold for all images
        List<Image> allImages = IntStream.range(0, inputImages.getFrameNumber()).mapToObj(f->inputImages.getImage(channelIdx, f)).collect(Collectors.toList());
        ThresholderHisto thlder = thresholder.instanciatePlugin();
        Histogram histo = HistogramFactory.getHistogram(()->Image.stream(allImages), thlder instanceof IJAutoThresholder ? HistogramFactory.BIN_SIZE_METHOD.NBINS_256 : HistogramFactory.BIN_SIZE_METHOD.AUTO_WITH_LIMITS);
        threshold = thlder.runThresholderHisto(histo);
        super.computeConfigurationData(channelIdx, inputImages);
    }
            
    @Override
    public MutableBoundingBox getBoundingBox(Image image) {
        double thld = Double.isNaN(threshold)? 
                thresholder.instanciatePlugin().runThresholderHisto(HistogramFactory.getHistogram(()->image.stream(), thresholder.instanciatePlugin() instanceof IJAutoThresholder ? HistogramFactory.BIN_SIZE_METHOD.NBINS_256 : HistogramFactory.BIN_SIZE_METHOD.AUTO_WITH_LIMITS)) 
                : threshold;
        return getBoundingBox(image, null , thld);
    }
    
    public MutableBoundingBox getBoundingBox(Image image, ImageInteger thresholdedImage, double threshold) {
        if (debug) testMode = true;
        Consumer<Image> dispImage = testMode ? i->ImageWindowManagerFactory.showImage(i) : null;
        BiConsumer<String, Consumer<List<StructureObject>>> miscDisp = testMode ? (s, c)->c.accept(Collections.EMPTY_LIST) : null;
        Result r = MicrochannelFluo2D.segmentMicroChannels(image, thresholdedImage, 0, 0, this.channelHeight.getValue().intValue(), this.fillingProportion.getValue().doubleValue(), threshold, this.minObjectSize.getValue().intValue(), dispImage, miscDisp);
        if (r == null) return null;
        int cropMargin = this.cropMarginY.getValue().intValue();
        int xStart = this.xStart.getValue().intValue();
        int xStop = this.xStop.getValue().intValue();
        int yStart = this.yStart.getValue().intValue();
        int yStop = this.yStop.getValue().intValue();
        int yMin = Math.max(yStart, r.yMin);
        if (yStop==0) yStop = image.sizeY()-1;
        if (xStop==0) xStop = image.sizeX()-1;
        yStart = Math.max(yMin-cropMargin, yStart);
        yStop = Math.min(yStop, yMin+channelHeight.getValue().intValue()-1);
        
        //xStart = Math.max(xStart, r.getXMin()-cropMargin);
        //xStop = Math.min(xStop, r.getXMax() + cropMargin);
        
        return new MutableBoundingBox(xStart, xStop, yStart, yStop, 0, image.sizeZ()-1);
        
    }
    @Override
    protected void uniformizeBoundingBoxes(Map<Integer, MutableBoundingBox> allBounds, InputImages inputImages, int channelIdx) {
        // reference point = top -> all y start are conserved
        int imageSizeY = inputImages.getImage(channelIdx, inputImages.getDefaultTimePoint()).sizeY();
        int maxSizeY = allBounds.values().stream().mapToInt(b->b.sizeY()).max().getAsInt();
        // TODO : parameter to allow out of bound in order to preserve size ? 
        int sY = allBounds.values().stream().filter(b->b.sizeY()!=maxSizeY).mapToInt(b-> { // get maximal sizeY so that all channels fit within range
            int yMin = b.yMin();
            int yMax = yMin + maxSizeY-1;
            if (yMax>=imageSizeY) {
                yMax = imageSizeY-1;
                yMin = Math.max(0, yMax - maxSizeY+1);
            }
            return yMax - yMin +1;
        }).min().orElse(-1);
        if (sY>0) allBounds.values().stream().filter(b->b.sizeY()!=sY).forEach(b-> b.setyMax(b.yMin() + sY -1)); // keep yMin and set yMax
        //int sizeY = (int)Math.round(ArrayUtil.quantile(allBounds.values().stream().mapToDouble(b->b.sizeY()).sorted(), allBounds.size(), 0.5));
        uniformizeX(allBounds);
    }

    @Override public Parameter[] getParameters() {
        return parameters;
    }

    
    
    
}
