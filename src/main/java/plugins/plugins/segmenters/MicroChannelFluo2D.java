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
package plugins.plugins.segmenters;

import boa.gui.imageInteraction.IJImageDisplayer;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectProcessing;
import ij.process.AutoThresholder;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageFloat;
import image.ImageInteger;
import image.ImageLabeller;
import image.ImageOperations;
import static image.ImageOperations.threshold;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import plugins.ParameterSetup;
import plugins.Segmenter;
import plugins.Thresholder;
import plugins.OverridableThreshold;
import plugins.OverridableThresholdWithSimpleThresholder;
import plugins.plugins.thresholders.BackgroundThresholder;
import plugins.plugins.thresholders.IJAutoThresholder;
import plugins.plugins.transformations.CropMicroChannelFluo2D;
import plugins.plugins.transformations.CropMicroChannels;
import plugins.plugins.transformations.CropMicroChannels.Result;
import processing.Filters;
import processing.ImageFeatures;
import processing.neighborhood.EllipsoidalNeighborhood;
import utils.ArrayUtil;
import static utils.Utils.plotProfile;

/**
 *
 * @author jollion
 */
public class MicroChannelFluo2D implements MicrochannelSegmenter , OverridableThresholdWithSimpleThresholder {

    NumberParameter channelHeight = new BoundedNumberParameter("Microchannel Height (pixels)", 0, 430, 5, null).setToolTipText("Height of microchannel in pixels");
    NumberParameter channelWidth = new BoundedNumberParameter("Microchannel Width (pixels)", 0, 40, 5, null);
    NumberParameter yShift = new BoundedNumberParameter("y-shift (start of microchannel)", 0, 20, 0, null).setToolTipText("Translation of the microchannel in upper direction");
    PluginParameter<plugins.SimpleThresholder> threshold= new PluginParameter<>("Threshold", plugins.SimpleThresholder.class, new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu), false); //new BackgroundThresholder(3, 6, 3) when background is removed and images saved in 16b, half of background is trimmed -> higher values
    NumberParameter fillingProportion = new BoundedNumberParameter("Microchannel filling proportion", 2, 0.3, 0.05, 1).setToolTipText("Fill proportion = y-length of bacteria / height of microchannel. If proportion is under this value, the object won't be segmented. Allows to avoid segmenting islated bacteria in central channel");
    NumberParameter minObjectSize = new BoundedNumberParameter("Min. Object Size", 0, 200, 1, null).setToolTipText("To detect microchannel a rough semgentation of bacteria is performed by simple threshold. Object undier this size in pixels are removed, to avoid taking into account objects that are not bacteria");
    Parameter[] parameters = new Parameter[]{channelHeight, channelWidth, yShift, threshold, fillingProportion, minObjectSize};
    public static boolean debug = false;

    public MicroChannelFluo2D() {
    }

    public MicroChannelFluo2D(int channelHeight, int channelWidth, int yMargin, double fillingProportion, int minObjectSize) {
        this.channelHeight.setValue(channelHeight);
        this.channelWidth.setValue(channelWidth);
        this.yShift.setValue(yMargin);
        this.fillingProportion.setValue(fillingProportion);
        this.minObjectSize.setValue(minObjectSize);
    }

    @Override
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        double thld = Double.isNaN(thresholdValue) ? this.threshold.instanciatePlugin().runThresholder(input, parent) : thresholdValue;
        logger.debug("thresholder: {} : {}", threshold.getPluginName(), threshold.getParameters());
        CropMicroChannelFluo2D cropper = new CropMicroChannelFluo2D().setChannelDim(this.channelHeight.getValue().intValue(), fillingProportion.getValue().doubleValue()).setParameters(this.minObjectSize.getValue().intValue());
        Result r = cropper.segmentMicroChannels(input, thresholdedImage, 0, yShift.getValue().intValue(), channelWidth.getValue().intValue(), thld);
        if (r==null) return null;
        else return r.getObjectPopulation(input, true);
    }
    
    @Override
    public Result segment(Image input) {
        double thld = Double.isNaN(thresholdValue) ? this.threshold.instanciatePlugin().runSimpleThresholder(input, null) : thresholdValue;
        CropMicroChannelFluo2D cropper = new CropMicroChannelFluo2D().setChannelDim(this.channelHeight.getValue().intValue(), fillingProportion.getValue().doubleValue()).setParameters(this.minObjectSize.getValue().intValue());
        Result r = cropper.segmentMicroChannels(input, thresholdedImage, 0, yShift.getValue().intValue(), channelWidth.getValue().intValue(), thld);
        return r;
    }
    
    private static ObjectPopulation run2(Image image, int channelHeight, int channelWidth, int yMargin) {
        // get yStart
        float[] yProj = ImageOperations.meanProjection(image, ImageOperations.Axis.Y, null);
        ImageFloat imProjY = new ImageFloat("proj(Y)", image.getSizeY(), new float[][]{yProj});
        double thldY =  IJAutoThresholder.runThresholder(imProjY, null, AutoThresholder.Method.Triangle); // ISODATA ou Triangle?
        ImageInteger heightMask = threshold(imProjY, thldY, true, false); 
        Object3D[] objHeight = ImageLabeller.labelImage(heightMask);
        int yStart;
        if (objHeight.length == 0) {
            yStart = 0;
        } else if (objHeight.length == 1) {
            yStart = objHeight[0].getBounds().getxMin();
        } else { // get object with maximum height tq sizeY-yStart < channelHeight
            int idxMax = 0;
            for (int i = 1; i < objHeight.length; ++i) {
                if (objHeight[i].getBounds().getSizeX() >= objHeight[idxMax].getBounds().getSizeX()) {
                    if (yProj.length - objHeight[i].getBounds().getxMin() < channelHeight) {
                        idxMax = i-1; 
                        break;
                    } else idxMax = i;
                }
            }
            
            yStart = objHeight[idxMax].getBounds().getxMin();
            logger.trace("crop microchannels: yStart: {} idx of margin object: {}", yStart, idxMax);
        }
        int yStart0 = yStart;
        // refine by searching max of derivative near yStart
        ImageFloat median = Filters.median(imProjY, new ImageFloat("", 0, 0, 0), new EllipsoidalNeighborhood(3, true));
        ImageFloat projDer = ImageFeatures.getDerivative(median, 1, 1, 0, 0, false).setName("Y proj, derivative");
        yStart = ArrayUtil.max(projDer.getPixelArray()[0], yStart - 10, yStart + 10);
        logger.trace("MicroChannelFluo: Y search: max of 1st derivate:{}", yStart);
        //refine by searching 2nd derivate maximum
        projDer = ImageFeatures.getDerivative(median, 2, 2, 0, 0, true);
        //plotProfile(projDer, 0, 0, true);
        yStart = ArrayUtil.max(projDer.getPixelArray()[0], yStart - 10, yStart);
        logger.trace("MicroChannelFluo: Y search: max of 2st derivate:{}", yStart);
        yStart = Math.max(0, yStart - yMargin);

        if (debug) {
            logger.debug("Y search: thld: {}, yStart: {}, yStart2: {}", thldY, yStart0, yStart);
            plotProfile("Y proj", yProj);
            plotProfile(projDer, 0, 0, true);
        }
        
        // get all XCenters
        float[] xProj = ImageOperations.meanProjection(image, ImageOperations.Axis.X, null);
        ImageFloat imProjX = new ImageFloat("proj(X)", image.getSizeX(), new float[][]{xProj});
        ImageInteger widthMask = threshold(imProjX, IJAutoThresholder.runThresholder(imProjX, null, AutoThresholder.Method.Triangle), true, false);
        Object3D[] objWidth = ImageLabeller.labelImage(widthMask);

        ArrayList<Object3D> res = new ArrayList<Object3D>(objWidth.length);
        for (int i = 0; i < objWidth.length; ++i) {
            int xMin = Math.max((int) (objWidth[i].getBounds().getXMean() - channelWidth / 2.0), 0);
            res.add(new Object3D(new BlankMask("mask of microchannel:" + (i + 1), channelWidth, Math.min(channelHeight, image.getSizeY()-yStart), image.getSizeZ(), xMin, yStart, 0, image.getScaleXY(), image.getScaleZ()), i + 1, true));
        }
        if (debug) {
            IJImageDisplayer disp = new IJImageDisplayer();
            disp.showImage(imProjY.setName("imm proj Y"));
            disp.showImage(imProjX.setName("imm proj X"));
        }
        return new ObjectPopulation(res, image);
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }

    // use threshold implementation
    protected double thresholdValue = Double.NaN;
    ImageInteger thresholdedImage = null;
    @Override
    public plugins.SimpleThresholder getThresholder() {
        return this.threshold.instanciatePlugin();
    }

    @Override
    public void setThresholdValue(double threhsold) {
        this.thresholdValue=threhsold;
    }

    @Override
    public Image getThresholdImage(Image input, int structureIdx, StructureObjectProcessing parent) {
        return input;
    }

    @Override
    public void setThresholdedImage(ImageInteger thresholdedImage) {
        this.thresholdedImage= thresholdedImage;
    }

}
