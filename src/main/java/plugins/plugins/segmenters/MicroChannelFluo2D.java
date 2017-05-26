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
import plugins.Segmenter;
import plugins.Thresholder;
import plugins.plugins.thresholders.BackgroundThresholder;
import plugins.plugins.thresholders.IJAutoThresholder;
import plugins.plugins.trackers.ObjectIdxTracker;
import static plugins.plugins.trackers.ObjectIdxTracker.getComparatorObject3D;
import plugins.plugins.transformations.CropMicroChannelFluo2D;
import static plugins.plugins.transformations.CropMicroChannelFluo2D.segmentMicroChannels;
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
public class MicroChannelFluo2D implements MicrochannelSegmenter {

    NumberParameter channelHeight = new BoundedNumberParameter("Microchannel Height (pixels)", 0, 350, 5, null);
    NumberParameter channelWidth = new BoundedNumberParameter("Microchannel Width (pixels)", 0, 40, 5, null);
    NumberParameter yMargin = new BoundedNumberParameter("y-margin", 0, 20, 0, null);
    PluginParameter<Thresholder> threshold= new PluginParameter<>("Threshold", Thresholder.class, new BackgroundThresholder(2.5, 3.5, 3), false);
    NumberParameter fillingProportion = new BoundedNumberParameter("Microchannel filling proportion", 2, 0.5, 0.05, 1);
    NumberParameter minObjectSize = new BoundedNumberParameter("Min. Object Size", 0, 100, 1, null);
    Parameter[] parameters = new Parameter[]{channelHeight, channelWidth, yMargin, threshold, fillingProportion, minObjectSize};
    public static boolean debug = false;

    public MicroChannelFluo2D() {
    }

    public MicroChannelFluo2D(int channelHeight, int channelWidth, int yMargin, double fillingProportion, int minObjectSize) {
        this.channelHeight.setValue(channelHeight);
        this.channelWidth.setValue(channelWidth);
        this.yMargin.setValue(yMargin);
        this.fillingProportion.setValue(fillingProportion);
        this.minObjectSize.setValue(minObjectSize);
    }

    @Override
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        double thld = this.threshold.instanciatePlugin().runThresholder(input, parent);
        ObjectPopulation objects = run(input, channelHeight.getValue().intValue(), channelWidth.getValue().intValue(), yMargin.getValue().intValue(), thld, fillingProportion.getValue().doubleValue(), minObjectSize.getValue().intValue());
        return objects;
    }

    public static ObjectPopulation run(Image image, int channelHeight, int channelWidth, int yMargin, double threshold, double fillingProportion, int minObjectSize) {
        CropMicroChannelFluo2D.debug=debug;
        Result r = segmentMicroChannels(image, 0, channelHeight, fillingProportion, minObjectSize, threshold);
        if (r==null) return null;
        ArrayList<Object3D> res = new ArrayList<Object3D>(r.xMax.length);
        int yMin = Math.max(r.yMin - yMargin, 0);
        for (int i = 0; i < r.xMax.length; ++i) {
            int xMin = Math.max((int) (r.getXMean(i) - channelWidth / 2.0), 0);
            res.add(new Object3D(new BlankMask("mask of microchannel:" + (i + 1), channelWidth, Math.min(channelHeight, image.getSizeY()-yMin), image.getSizeZ(), xMin, yMin, 0, image.getScaleXY(), image.getScaleZ()), i + 1));
        }
        return new ObjectPopulation(res, image);
    }
    
    @Override
    public CropMicroChannels.Result segment(Image input) {
        double thld = this.threshold.instanciatePlugin().runThresholder(input, null);
        Result r = segmentMicroChannels(input, 0, channelHeight.getValue().intValue(), fillingProportion.getValue().doubleValue(), minObjectSize.getValue().intValue(), thld);
        r.yMin = Math.max(r.yMin - yMargin.getValue().intValue(), 0);
        return r;
    }
    
    public static ObjectPopulation run2(Image image, int channelHeight, int channelWidth, int yMargin) {
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
            res.add(new Object3D(new BlankMask("mask of microchannel:" + (i + 1), channelWidth, Math.min(channelHeight, image.getSizeY()-yStart), image.getSizeZ(), xMin, yStart, 0, image.getScaleXY(), image.getScaleZ()), i + 1));
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
    

}
