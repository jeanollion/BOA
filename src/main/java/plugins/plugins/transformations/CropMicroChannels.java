/*
 * Copyright (C) 2016 jollion
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

import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import dataStructure.containers.InputImages;
import static dataStructure.containers.InputImages.getAverageFrame;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import plugins.Cropper;
import static plugins.Plugin.logger;
import plugins.Transformation;
import utils.ArrayUtil;

/**
 *
 * @author jollion
 */
public abstract class CropMicroChannels implements Transformation, Cropper {
    public static boolean debug = false;
    protected ArrayList<Integer> configurationData=new ArrayList<Integer>(4); // xMin/xMax/yMin/yMax
    protected NumberParameter xStart = new BoundedNumberParameter("X start", 0, 0, 0, null);
    protected NumberParameter xStop = new BoundedNumberParameter("X stop (0 for image width)", 0, 0, 0, null);
    protected NumberParameter yStart = new BoundedNumberParameter("Y start", 0, 0, 0, null);
    protected NumberParameter yStop = new BoundedNumberParameter("Y stop (0 for image heigth)", 0, 0, 0, null);
    protected NumberParameter margin = new BoundedNumberParameter("X-Margin", 0, 0, 0, null);
    protected NumberParameter channelHeight = new BoundedNumberParameter("Channel Height", 0, 330, 0, null);
    protected NumberParameter cropMargin = new BoundedNumberParameter("Crop Margin", 0, 45, 0, null);
    NumberParameter number = new BoundedNumberParameter("Number of Frames", 0, 20, 1, null);
    
    public CropMicroChannels setNumberOfFrames(int nb) {
        this.number.setValue(nb);
        return this;
    }
    @Override
    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        if (channelIdx<0) throw new IllegalArgumentException("Channel no configured");
        Image image = inputImages.getImage(channelIdx, inputImages.getDefaultTimePoint());
        // check configuration validity
        if (xStop.getValue().intValue()==0 || xStop.getValue().intValue()>=image.getSizeX()) xStop.setValue(image.getSizeX()-1);
        if (xStart.getValue().intValue()>=xStop.getValue().intValue()) {
            logger.warn("CropMicroChannels2D: illegal configuration: xStart>=xStop, set to default values");
            xStart.setValue(0);
            xStop.setValue(image.getSizeX()-1);
        }
        if (yStop.getValue().intValue()==0 || yStop.getValue().intValue()>=image.getSizeY()) yStop.setValue(image.getSizeY()-1);
        if (yStart.getValue().intValue()>=yStop.getValue().intValue()) {
            logger.warn("CropMicroChannels2D: illegal configuration: yStart>=yStop, set to default values");
            yStart.setValue(0);
            yStop.setValue(image.getSizeY()-1);
        }
        
        if (channelHeight.getValue().intValue()>image.getSizeY()) throw new IllegalArgumentException("channel height > image height");
        BoundingBox b=null;
        int numb = Math.min(number.getValue().intValue(), inputImages.getFrameNumber()-2);
        if (numb>1) {
            List<Integer> frames = InputImages.chooseNImagesWithSignal(inputImages, channelIdx, numb);
            for (int f : frames) {
                image = inputImages.getImage(channelIdx, f);
                BoundingBox bb = getBoundingBox(image);
                if (bb==null) continue;
                if (b==null) b = bb;
                else b.expand(bb);
                if (debug) logger.debug("CropMicroChannels: time: {}, bounds: {}, max bounds: {}", f, bb, b);
            }
        } else b = getBoundingBox(image);
        if (b==null) {
            b=image.getBoundingBox();
            logger.error("No Microchannels found");
        } else logger.debug("Crop Microchannel: image: {} timepoint: {} boundingBox: {}", image.getName(), inputImages.getDefaultTimePoint(), b);
        configurationData=new ArrayList<Integer>(4);
        configurationData.add(b.getxMin());
        configurationData.add(b.getxMax());
        configurationData.add(b.getyMin());
        configurationData.add(b.getyMax());
    }
    
    @Override
    public BoundingBox getCropBoundginBox(int channelIdx, InputImages inputImages) {
        if (!isConfigured(inputImages.getChannelNumber(), inputImages.getFrameNumber())) {
            computeConfigurationData(channelIdx, inputImages);
        }
        return new BoundingBox(configurationData.get(0), configurationData.get(1), configurationData.get(2), configurationData.get(3), 0, inputImages.getSizeZ(channelIdx)-1);
    }
    
    @Override
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return configurationData!=null && configurationData.size()==4;
    }
    
    @Override
    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.ALL;
    }
    protected abstract BoundingBox getBoundingBox(Image image);
    
    @Override
    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        BoundingBox bounds = new BoundingBox(configurationData.get(0), configurationData.get(1), configurationData.get(2), configurationData.get(3), 0, image.getSizeZ()-1);
        return image.crop(bounds);
    }

    @Override
    public ArrayList getConfigurationData() {
        return configurationData;
    }
    
    public static class Result {
        public final int[] xMax;
        public final int[] xMin;
        public final int[] yMinShift;
        public int yMin, yMax;

        public Result(List<int[]> sortedMinMaxYShiftList, int yMin, int yMax) {
            this.yMin = yMin;
            this.yMax=yMax;
            this.xMax= new int[sortedMinMaxYShiftList.size()];
            this.xMin=new int[sortedMinMaxYShiftList.size()];
            this.yMinShift= new int[sortedMinMaxYShiftList.size()];
            int idx = 0;
            for (int[] minMax : sortedMinMaxYShiftList) {
                xMin[idx] = minMax[0];
                xMax[idx] = minMax[1];
                yMinShift[idx++] = minMax[2];
            }
        }
        public int getXMin() {
            return xMin[0];
        }
        public int getXMax() {
            return xMax[xMax.length-1];
        }
        public int getXWidth(int idx) {
            return xMax[idx]-xMin[idx];
        }
        public double getXMean(int idx) {
            return (xMax[idx]+xMin[idx]) / 2d ;
        }
        public int getYMin() {
            return yMin+ArrayUtil.min(yMinShift);
        }
        public int getYMax() {
            return yMax;
        }
        public int size() {
            return xMin.length;
        }
        public BoundingBox getBounds(int idx, boolean includeYMinShift) {
            return new BoundingBox(xMin[idx], xMax[idx], yMin+(includeYMinShift?yMinShift[idx]:0), yMax, 0, 0);
        }
        public Object3D getObject3D(int idx, float scaleXY, float scaleZ, boolean includeYMinShift) {
            return new Object3D(new BlankMask("mask of:" + idx+1, getBounds(idx, includeYMinShift).getImageProperties(scaleXY, scaleZ)), idx+1);
        }
        public ObjectPopulation getObjectPopulation(ImageProperties im, boolean includeYMinShift) {
            List<Object3D> l = new ArrayList<>(xMin.length);
            for (int i = 0; i<xMin.length; ++i) l.add(getObject3D(i, im.getScaleXY(), im.getScaleZ(), includeYMinShift));
            return new ObjectPopulation(l, im);
        }
    }
    boolean testMode;
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}
}
