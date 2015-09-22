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

import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.containers.InputImages;
import dataStructure.objects.Object3D;
import ij.process.AutoThresholder;
import image.BoundingBox;
import image.Image;
import image.ImageFloat;
import image.ImageInteger;
import image.ImageLabeller;
import plugins.TransformationTimeIndependent;
import plugins.plugins.thresholders.IJAutoThresholder;
import processing.RadonProjection;
import static utils.Utils.plotProfile;

/**
 *
 * @author jollion
 */
public class CropMicroChannels2D implements TransformationTimeIndependent {
    
    Integer[] configurationData; // xMin/xMax/yMin/yMax
    NumberParameter xStart = new NumberParameter("X start", 0, 0);
    NumberParameter xStop = new BoundedNumberParameter("X stop (0 for image width)", 0, 0, 0, null);
    NumberParameter yStart = new BoundedNumberParameter("Y start", 0, 0, 0, null);
    NumberParameter yStop = new BoundedNumberParameter("Y stop (0 for image heigth)", 0, 0, 0, null);
    NumberParameter margin = new BoundedNumberParameter("Margin", 0, 15, 0, null);
    NumberParameter channelHeight = new BoundedNumberParameter("Channel Height", 0, 350, 0, null);
    NumberParameter channelWidth = new BoundedNumberParameter("Channel Width", 0, 10, 0, null);
    Parameter[] parameters = new Parameter[]{channelHeight, channelWidth, margin, xStart, xStop, yStart, yStop};
    private static final int cropMargin=20;
    
    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.ALL;
    }

    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
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
        if (channelWidth.getValue().intValue()>image.getSizeX()) throw new IllegalArgumentException("channel width > image width");
        
        int z = image.getSizeZ()/2;
        BoundingBox b = getBoundingBox(image, z, margin.getValue().intValue(), channelHeight.getValue().intValue(), xStart.getValue().intValue(), xStop.getValue().intValue(), yStart.getValue().intValue(), yStop.getValue().intValue());
        // si b==null -> faire sur d'autres temps? 
        logger.debug("Crop Microp Channel: image: {} timepoint: {} boundingBox: {}", image.getName(), inputImages.getDefaultTimePoint(), b);
        configurationData = new Integer[]{b.getxMin(), b.getxMax(), b.getyMin(), b.getyMax()};
    }
    
    public static BoundingBox getBoundingBox(Image image, int z, int margin, int channelHeight, int xStart, int xStop, int yStart, int yStop) {
        //get projections along X and Y axis
        ImageFloat imProjX = new ImageFloat("proj(X)", image.getSizeX(), 1, 1);
        for (int x = 0; x<image.getSizeX(); ++x) {
            double sumX=0;
            for (int y = 0; y<image.getSizeY(); ++y) sumX+=image.getPixel(x, y, z);
            imProjX.setPixel(x, 0, 0, sumX/image.getSizeY());
        }
        ImageFloat imProjY = new ImageFloat("proj(Y)", image.getSizeY(), 1, 1);
        for (int y = 0; y<image.getSizeY(); ++y) {
            double sumY=0;
            for (int x = 0; x<image.getSizeX(); ++x) sumY+=image.getPixel(x, y, z);
            imProjY.setPixel(y, 0, 0, sumY/image.getSizeX());
        }
        //plotProfile(imProjX, 0, 0, true);
        //plotProfile(imProjY, 0, 0, true);
        // get Y coords
        ImageInteger heightMask = imProjY.threshold(IJAutoThresholder.runThresholder(imProjY, null, AutoThresholder.Method.Triangle), true, false);
        //plotProfile(heightMask, 0, 0, true);
        Object3D[] objHeight = ImageLabeller.labelImage(heightMask);
        if (objHeight.length==0) return null;
        else if (objHeight.length==1) {
            yStart = objHeight[0].getBounds().getxMin();
        } else { // get object with maximum height
            int idxMax = 0;
            for (int i = 1; i<objHeight.length;++i) if (objHeight[i].getBounds().getSizeX()>=objHeight[idxMax].getBounds().getSizeX()) idxMax=i;
            yStart = Math.max(objHeight[idxMax].getBounds().getxMin()-cropMargin, 0);
            //logger.trace("crop microchannels: yStart: {} idx of margin object: {}", yStart, idxMax);
        }
        yStop = Math.min(yStop, yStart+channelHeight + cropMargin*2);
        // get X coords
        ImageInteger widthMask = imProjX.threshold(IJAutoThresholder.runThresholder(imProjX, null, AutoThresholder.Method.Otsu), true, false);
        //plotProfile(widthMask, 0, 0, true);
        Object3D[] objWidth = ImageLabeller.labelImage(widthMask);
        if (objWidth.length==0) return null;
        else { 
            // get first object after xStart & margin
            int i;
            int xStartTemp=xStart;
            for (i = 0; i<objWidth.length;++i) {
                int curX = objWidth[i].getBounds().getxMin();
                if (curX>=margin && curX>=xStart) {
                    xStartTemp=curX;
                    break;
                }
            }
            // get last object before xStop & margin
            int j;
            int xStopTemp=xStop;
            for (j = objWidth.length-1; j>=i;--j) {
                int curX = objWidth[j].getBounds().getxMax();
                if (curX<=(image.getSizeX()-margin) && curX<=xStop) {
                    xStopTemp=curX;
                    break;
                }
            }
            xStart=Math.max(0, xStartTemp-cropMargin);
            xStop=Math.min(xStop, xStopTemp+cropMargin);
            //logger.trace("crop microchannels: xStart: {} idx of margin object: {}", xStart, i);
            //logger.trace("crop microchannels: xStop: {} idx of margin object: {}", xStop, j);
        }
        return new BoundingBox(xStart, xStop, yStart, yStop, 0, image.getSizeZ()-1);
    }
    

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        BoundingBox bounds = new BoundingBox(configurationData[0], configurationData[1], configurationData[2], configurationData[3], 0, image.getSizeZ()-1);
        return image.crop(bounds);
    }

    public Object[] getConfigurationData() {
        return configurationData;
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    
}
