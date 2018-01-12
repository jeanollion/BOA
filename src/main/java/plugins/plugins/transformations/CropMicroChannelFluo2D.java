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
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import dataStructure.containers.InputImages;
import dataStructure.objects.Region;
import dataStructure.objects.RegionPopulation;
import dataStructure.objects.StructureObjectProcessing;
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
import plugins.SimpleThresholder;
import plugins.Thresholder;
import plugins.TransformationTimeIndependent;
import plugins.OverridableThreshold;
import plugins.plugins.thresholders.BackgroundThresholder;
import plugins.plugins.thresholders.ConstantValue;
import plugins.plugins.thresholders.IJAutoThresholder;
import plugins.plugins.trackers.ObjectIdxTracker;
import plugins.plugins.trackers.ObjectIdxTracker.IndexingOrder;
import static plugins.plugins.trackers.ObjectIdxTracker.getComparatorRegion;
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
    NumberParameter fillingProportion = new BoundedNumberParameter("Filling proportion of Microchannel", 2, 0.5, 0.05, 1);
    PluginParameter<SimpleThresholder> threshold = new PluginParameter<>("Intensity Threshold", SimpleThresholder.class, new BackgroundThresholder(3, 6, 3), false);   //new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu)
    Parameter[] parameters = new Parameter[]{channelHeight, cropMargin, margin, minObjectSize, threshold, fillingProportion, xStart, xStop, yStart, yStop, number};
    
    public CropMicroChannelFluo2D(int channelHeight, int Xmargin, int cropMargin, int minObjectSize, double fillingProportion, int timePointNumber) {
        this.channelHeight.setValue(channelHeight);
        this.margin.setValue(Xmargin);
        this.cropMargin.setValue(cropMargin);
        this.minObjectSize.setValue(minObjectSize);
        this.fillingProportion.setValue(fillingProportion);
        this.number.setValue(timePointNumber);
    }
    
    public CropMicroChannelFluo2D() {
        //this.margin.setValue(30);
    }
    public CropMicroChannelFluo2D setThresholder(SimpleThresholder instance) {
        this.threshold.setPlugin(instance);
        return this;
    }
    public CropMicroChannelFluo2D setTimePointNumber(int timePointNumber) {
        this.number.setValue(timePointNumber);
        return this;
    }
    public CropMicroChannelFluo2D setChannelDim(int channelHeight, double fillingProportion) {
        this.channelHeight.setValue(channelHeight);
        this.fillingProportion.setValue(fillingProportion);
        return this;
    }
    public CropMicroChannelFluo2D setParameters(int minObjectSize) {
        this.minObjectSize.setValue(minObjectSize);
        return this;
    }
    @Override
    public BoundingBox getBoundingBox(Image image) {
        double thld = this.threshold.instanciatePlugin().runSimpleThresholder(image, null);
        return getBoundingBox(image, null, cropMargin.getValue().intValue(), margin.getValue().intValue(), thld, xStart.getValue().intValue(), xStop.getValue().intValue(), yStart.getValue().intValue(), yStop.getValue().intValue());
    }
    
    public BoundingBox getBoundingBox(Image image, ImageInteger thresholdedImage, int cropMargin, int margin, double threshold, int xStart, int xStop, int yStart, int yStop) {
        if (debug) testMode = true;
        Result r = segmentMicroChannels(image, thresholdedImage, margin, 0, 0, threshold);
        if (r == null) return null;
        int yMin = Math.max(yStart, r.yMin);
        if (yStop==0) yStop = image.getSizeY()-1;
        if (xStop==0) xStop = image.getSizeX()-1;
        yStop = Math.min(yStop, yMin+channelHeight.getValue().intValue() + cropMargin);
        
        yStart = Math.max(yMin-cropMargin, yStart);
        
        //xStart = Math.max(xStart, r.getXMin()-cropMargin);
        //xStop = Math.min(xStop, r.getXMax() + cropMargin);
        
        if (testMode) logger.debug("Xmin: {}, Xmax: {}", r.getXMin(), r.getXMax());
        return new BoundingBox(xStart, xStop, yStart, yStop, 0, image.getSizeZ()-1);
        
    }
    
    public Result segmentMicroChannels(Image image, ImageInteger thresholdedImage, int Xmargin, int yShift, int channelWidth, double thld) {
        if (debug) testMode = true;
        double thldX = channelHeight.getValue().doubleValue() * fillingProportion.getValue().doubleValue(); // only take into account roughly filled channels
        thldX /= (double) (image.getSizeY() * image.getSizeZ() ); // mean X projection
        /*
        1) rough segmentation of cells with threshold
        2) selection of filled channels using X-projection & threshold on length
        3) computation of Y start using the minimal Y of objects within the selected channels from step 2 (median value of yMins)
        */
        
        if (Double.isNaN(thld) && thresholdedImage==null) thld = BackgroundThresholder.runThresholder(image, null, 3, 6, 3, Double.MAX_VALUE, null);//IJAutoThresholder.runThresholder(image, null, AutoThresholder.Method.Triangle); // OTSU / TRIANGLE / YEN 
        if (testMode) logger.debug("crop micochannels threshold : {}", thld);
        ImageInteger mask = thresholdedImage == null ? ImageOperations.threshold(image, thld, true, true) : thresholdedImage;
        Filters.binaryClose(mask, mask, Filters.getNeighborhood(1, 0, image)); // case of low intensity signal -> noisy. // remove small objects?
        List<Region> bacteria = ImageOperations.filterObjects(mask, mask, o->o.getSize()<minObjectSize.getValue().intValue());
        
        float[] xProj = ImageOperations.meanProjection(mask, ImageOperations.Axis.X, null);
        ImageFloat imProjX = new ImageFloat("proj(X)", mask.getSizeX(), new float[][]{xProj});
        ImageByte projXThlded = ImageOperations.threshold(imProjX, thldX, true, false).setName("proj(X) thlded: "+thldX);
        if (testMode) {
            ImageWindowManagerFactory.showImage(mask);
            Utils.plotProfile(imProjX, 0, 0, true);
            Utils.plotProfile(projXThlded, 0, 0, true);
        }
        List<Region> xObjectList = ImageLabeller.labelImageList(projXThlded);
        if (xObjectList.isEmpty()) return null;
        if (channelWidth<=1) channelWidth=(int)xObjectList.stream().mapToInt(o->o.getBounds().getSizeX()).average().getAsDouble();
        Xmargin = Math.max(Xmargin, channelWidth/2+1);
        if (testMode) logger.debug("channelWidth: {}, marging: {}", channelWidth, Xmargin);
        Iterator<Region> it = xObjectList.iterator();
        int rightLimit = image.getSizeX() - Xmargin;
        while(it.hasNext()) {
            BoundingBox b = it.next().getBounds();
            if (b.getXMean()<Xmargin || b.getXMean()>rightLimit) it.remove(); //if (b.getxMin()<Xmargin || b.getxMax()>rightLimit) it.remove(); //
        }
        if (xObjectList.isEmpty()) return null;
        // fusion of overlapping objects
        it = xObjectList.iterator();
        Region prev = it.next();
        while(it.hasNext()) {
            Region next = it.next();
            if (prev.getBounds().getxMax()+1>next.getBounds().getxMin()) { 
                prev.addVoxels(next.getVoxels());
                it.remove();
            } else prev= next;
        }
        
        Region[] xObjects = xObjectList.toArray(new Region[xObjectList.size()]);
        if (xObjects.length==0) return null;
        
        if (testMode) ImageWindowManagerFactory.showImage(new RegionPopulation(bacteria, mask).getLabelMap().setName("segmented bacteria"));
        if (testMode) logger.debug("mc: {}, objects: {}", Utils.toStringArray(xObjects, o->o.getBounds()), bacteria.size());
        if (bacteria.isEmpty()) return null;
        int[] yMins = new int[xObjects.length];
        Arrays.fill(yMins, Integer.MAX_VALUE);
        for (Region o : bacteria) {
            BoundingBox b = o.getBounds();
            //if (debug) logger.debug("object: {}");
            X_SEARCH : for (int i = 0; i<xObjects.length; ++i) {
                BoundingBox inter = b.getIntersection(xObjects[i].getBounds());
                if (inter.getSizeX() >= 2 ) {
                    if (b.getyMin()<yMins[i]) yMins[i] = b.getyMin();
                    break X_SEARCH;
                }
            }
        }
        // get median value of yMins
        List<Integer> yMinsList = new ArrayList<>(yMins.length);
        for (int yMin : yMins) if (yMin!=Integer.MAX_VALUE) yMinsList.add(yMin);
        if (yMinsList.isEmpty()) return null;
        //int yMin = (int)Math.round(ArrayUtil.medianInt(yMinsList));
        //if (debug) logger.debug("Ymin: {}, among: {} values : {}, shift: {}", yMin, yMinsList.size(), yMins, yShift);
        int yMin = Collections.min(yMinsList);
        
        List<int[]> sortedMinMaxYShiftList = new ArrayList<>(xObjects.length);
        
        for (int i = 0; i<xObjects.length; ++i) {
            if (yMins[i]==Integer.MAX_VALUE) continue;
            int xMin = (int) (xObjects[i].getBounds().getXMean() - channelWidth / 2.0);
            int xMax = (int) (xObjects[i].getBounds().getXMean() +  channelWidth / 2.0); // mc remains centered
            if (xMin<0 || xMax>=image.getSizeX()) continue;  // exclude outofbounds objects
            int[] minMaxYShift = new int[]{xMin, xMax, yMins[i]-yMin<yShift ? 0 : yMins[i]-yMin};
            sortedMinMaxYShiftList.add(minMaxYShift);
        }
        Collections.sort(sortedMinMaxYShiftList, (i1, i2) -> Integer.compare(i1[0], i2[0]));
        return new Result(sortedMinMaxYShiftList, Math.max(0, yMin-yShift), yMin+channelHeight.getValue().intValue()-yShift);
        //return new Result(xObjects, yMin, yMin+channelHeight);
        
    }

    @Override public Parameter[] getParameters() {
        return parameters;
    }
    
    boolean testMode;
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}
}
