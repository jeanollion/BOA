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
package plugins.plugins.transformations;

import boa.gui.imageInteraction.IJImageDisplayer;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import dataStructure.containers.InputImages;
import dataStructure.objects.Object3D;
import ij.process.AutoThresholder;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageFloat;
import image.ImageInteger;
import image.ImageLabeller;
import image.ImageOperations;
import static image.ImageOperations.threshold;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import plugins.Thresholder;
import plugins.TransformationTimeIndependent;
import plugins.plugins.thresholders.ConstantValue;
import plugins.plugins.thresholders.IJAutoThresholder;
import plugins.plugins.trackers.ObjectIdxTracker;
import plugins.plugins.trackers.ObjectIdxTracker.IndexingOrder;
import static plugins.plugins.trackers.ObjectIdxTracker.getComparatorObject3D;
import processing.Filters;
import processing.ImageFeatures;
import processing.RadonProjection;
import processing.neighborhood.EllipsoidalNeighborhood;
import utils.ArrayUtil;
import utils.HashMapGetCreate;
import utils.Utils;
import static utils.Utils.plotProfile;

/**
 *
 * @author jollion
 */
public class CropMicroChannelFluo2D extends CropMicroChannels {
    
    NumberParameter minObjectSize = new BoundedNumberParameter("Object Size Filter", 0, 200, 1, null);
    NumberParameter fillingProportion = new BoundedNumberParameter("Filling proportion of Microchannel", 2, 0.6, 0.05, 1);
    NumberParameter threshold = new BoundedNumberParameter("Intensity Threshold", 2, 50, 0, null);
    //PluginParameter<Thresholder> threshold = new PluginParameter<Thresholder>("Intensity Threshold", Thresholder.class, new ConstantValue(50), false);
    Parameter[] parameters = new Parameter[]{channelHeight, cropMargin, margin, minObjectSize, threshold, fillingProportion, xStart, xStop, yStart, yStop, number};
    
    
    public CropMicroChannelFluo2D(int margin, int cropMargin, int minObjectSize, double fillingProportion, int timePointNumber) {
        this.margin.setValue(margin);
        this.cropMargin.setValue(cropMargin);
        this.minObjectSize.setValue(minObjectSize);
        this.fillingProportion.setValue(fillingProportion);
        this.number.setValue(timePointNumber);
    }
    
    public CropMicroChannelFluo2D() {
        
    }
    
    public CropMicroChannelFluo2D setTimePointNumber(int timePointNumber) {
        this.number.setValue(timePointNumber);
        return this;
    }

    protected BoundingBox getBoundingBox(Image image) {
        return getBoundingBox(image, cropMargin.getValue().intValue(), margin.getValue().intValue(), channelHeight.getValue().intValue(), threshold.getValue().doubleValue(), fillingProportion.getValue().doubleValue(), minObjectSize.getValue().intValue(), xStart.getValue().intValue(), xStop.getValue().intValue(), yStart.getValue().intValue(), yStop.getValue().intValue());
    }
    
    public static BoundingBox getBoundingBox(Image image, int cropMargin, int margin, int channelHeight, double threshold, double fillingProportion, int minObjectSize, int xStart, int xStop, int yStart, int yStop) {
        Result r = segmentMicroChannels(image, margin, channelHeight, fillingProportion, minObjectSize, threshold);
        if (r == null) return null;
        int yMin = Math.max(yStart, r.yMin);
        yStop = Math.min(yStop, yMin+channelHeight);
        yStart = Math.max(yMin-cropMargin, yStart);
        
        xStart = Math.max(xStart, r.getXMin()-cropMargin);
        xStop = Math.min(xStop, r.getXMax() + cropMargin);
        if (debug) logger.debug("Xmin: {}, Xmax: {}", r.getXMin(), r.getXMax());
        return new BoundingBox(xStart, xStop, yStart, yStop, 0, image.getSizeZ()-1);
        
    }
    public static Result segmentMicroChannels(Image image, int margin, int channelHeight, double fillingProportion, int minObjectSize) {
        return segmentMicroChannels(image, margin, channelHeight, fillingProportion, minObjectSize, Double.NaN);
    }
    public static Result segmentMicroChannels(Image image, int margin, int channelHeight, double fillingProportion, int minObjectSize, AutoThresholder.Method thresholdingMethod) {
        double thld = IJAutoThresholder.runThresholder(image, null, thresholdingMethod);
        return segmentMicroChannels(image, margin, channelHeight, fillingProportion, minObjectSize, thld);
    }
    public static Result segmentMicroChannels(Image image, int margin, int channelHeight, double fillingProportion, int minObjectSize, double thld) {
        double thldX = channelHeight * fillingProportion; // only take into account roughly filled channels
        thldX /= (double) (image.getSizeY() * image.getSizeZ() ); // mean X projection
        /*
        1) rough segmentation of cells with autothreshold
        2) selection of filled channels using X-projection & threshold on length
        3) computation of Y start using the minimal Y of objects within the selected channels from step 2 (median value of yMins)
        */
        
        if (Double.isNaN(thld)) thld = IJAutoThresholder.runThresholder(image, null, AutoThresholder.Method.Triangle); // OTSU / TRIANGLE / YEN 
        ImageByte mask = ImageOperations.threshold(image, thld, true, true);
        //mask = Filters.binaryClose(mask, new ImageByte("segmentation mask::closed", mask), Filters.getNeighborhood(4, 4, mask));
        float[] xProj = ImageOperations.meanProjection(mask, ImageOperations.Axis.X, null);
        ImageFloat imProjX = new ImageFloat("proj(X)", mask.getSizeX(), new float[][]{xProj});
        ImageByte projXThlded = ImageOperations.threshold(imProjX, thldX, true, false).setName("proj(X) thlded: "+thldX);
        if (debug) {
            new IJImageDisplayer().showImage(mask);
            Utils.plotProfile(imProjX, 0, 0, true);
            Utils.plotProfile(projXThlded, 0, 0, true);
        }
        List<Object3D> xObjectList = new ArrayList<Object3D>(ImageLabeller.labelImageList(projXThlded));
        Iterator<Object3D> it = xObjectList.iterator();
        int rightLimit = image.getSizeX() - margin;
        while(it.hasNext()) {
            BoundingBox b = it.next().getBounds();
            if (b.getxMin()<margin || b.getxMax()>rightLimit) it.remove();
        }
        Object3D[] xObjects = xObjectList.toArray(new Object3D[xObjectList.size()]);
        if (xObjects.length==0) return null;
        Object3D[] objects = ImageLabeller.labelImage(mask);
        if (objects.length==0) return null;
        int[] yMins = new int[xObjects.length];
        Arrays.fill(yMins, Integer.MAX_VALUE);
        for (Object3D o : objects) {
            BoundingBox b = o.getBounds();
            if (o.getSize()<minObjectSize) continue;
            X_SEARCH : for (int i = 0; i<xObjects.length; ++i) {
                BoundingBox inter = b.getIntersection(xObjects[i].getBounds());
                if (inter.getSizeX() >= xObjects[i].getBounds().getSizeX() / 2 ) {
                    if (b.getyMin()<yMins[i]) yMins[i] = b.getyMin();
                    break X_SEARCH;
                }
            }
        }
        // get median value of yMins
        List<Integer> yMinsList = new ArrayList<Integer>(yMins.length);
        for (int yMin : yMins) if (yMin!=Integer.MAX_VALUE) yMinsList.add(yMin);
        if (yMinsList.isEmpty()) return null;
        Collections.sort(yMinsList);
        int s = yMinsList.size();
        int yMin =  (s%2 == 0) ? (int) (0.5d + (double)(yMinsList.get(s/2-1)+yMinsList.get(s/2)) /2d) : yMinsList.get(s/2);
        if (debug) logger.debug("Ymin: {}, among: {} values : {}", yMin, yMinsList.size(), yMins);
        
        return new Result(xObjects, yMin, yMin+channelHeight);
        
    }

    @Override public Parameter[] getParameters() {
        return parameters;
    }
    
}
