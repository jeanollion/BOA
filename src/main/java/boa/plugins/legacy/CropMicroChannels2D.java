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
package boa.plugins.legacy;

import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.input_image.InputImages;
import boa.data_structure.Region;
import ij.process.AutoThresholder;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.processing.ImageOperations;
import static boa.image.processing.ImageOperations.threshold;
import java.util.ArrayList;
import static boa.plugins.Plugin.logger;
import boa.plugins.Transformation.SelectionMode;
import boa.plugins.TransformationTimeIndependent;
import boa.plugins.plugins.thresholders.IJAutoThresholder;
import boa.image.processing.Filters;
import boa.image.processing.ImageFeatures;
import boa.image.processing.RadonProjection;
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;
import boa.utils.ArrayUtil;
import static boa.utils.Utils.plotProfile;

/**
 *
 * @author jollion
 */
public class CropMicroChannels2D {//implements TransformationTimeIndependent {
    public static boolean debug = false;
    ArrayList<Integer> configurationData=new ArrayList<Integer>(4); // xMin/xMax/yMin/yMax
    NumberParameter xStart = new NumberParameter("X start", 0, 0);
    NumberParameter xStop = new BoundedNumberParameter("X stop (0 for image width)", 0, 0, 0, null);
    NumberParameter yStart = new BoundedNumberParameter("Y start", 0, 0, 0, null);
    NumberParameter yStop = new BoundedNumberParameter("Y stop (0 for image heigth)", 0, 0, 0, null);
    NumberParameter margin = new BoundedNumberParameter("Margin", 0, 30, 0, null);
    NumberParameter channelHeight = new BoundedNumberParameter("Channel Height", 0, 355, 0, null);
    NumberParameter cropMargin = new BoundedNumberParameter("Crop Margin", 0, 45, 0, null);
    Parameter[] parameters = new Parameter[]{channelHeight, cropMargin, margin, xStart, xStop, yStart, yStop};
    
    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.ALL;
    }

    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        Image image = inputImages.getImage(channelIdx, inputImages.getDefaultTimePoint());
        // check configuration validity
        if (xStop.getValue().intValue()==0 || xStop.getValue().intValue()>=image.sizeX()) xStop.setValue(image.sizeX()-1);
        if (xStart.getValue().intValue()>=xStop.getValue().intValue()) {
            logger.warn("CropMicroChannels2D: illegal configuration: xStart>=xStop, set to default values");
            xStart.setValue(0);
            xStop.setValue(image.sizeX()-1);
        }
        if (yStop.getValue().intValue()==0 || yStop.getValue().intValue()>=image.sizeY()) yStop.setValue(image.sizeY()-1);
        if (yStart.getValue().intValue()>=yStop.getValue().intValue()) {
            logger.warn("CropMicroChannels2D: illegal configuration: yStart>=yStop, set to default values");
            yStart.setValue(0);
            yStop.setValue(image.sizeY()-1);
        }
        
        if (channelHeight.getValue().intValue()>image.sizeY()) throw new IllegalArgumentException("channel height > image height");
        
        int z = image.sizeZ()/2;
        MutableBoundingBox b = getBoundingBox(image, z, cropMargin.getValue().intValue(), margin.getValue().intValue(), channelHeight.getValue().intValue(), xStart.getValue().intValue(), xStop.getValue().intValue(), yStart.getValue().intValue(), yStop.getValue().intValue());
        // si b==null -> faire sur d'autres temps? 
        logger.debug("Crop Microp Channel: image: {} timepoint: {} boundingBox: {}", image.getName(), inputImages.getDefaultTimePoint(), b);
        configurationData=new ArrayList<Integer>(4);
        configurationData.add(b.xMin());
        configurationData.add(b.xMax());
        configurationData.add(b.yMin());
        configurationData.add(b.yMax());
    }
    
    public static MutableBoundingBox getBoundingBox(Image image, int z, int cropMargin, int margin, int channelHeight, int xStart, int xStop, int yStart, int yStop) {
        //get projections along X and Y axis
        float[] xProj = ImageOperations.meanProjection(image, ImageOperations.Axis.X, null);
        ImageFloat imProjX = new ImageFloat("proj(X)", image.sizeX(), new float[][]{xProj});
        float[] yProj = ImageOperations.meanProjection(image, ImageOperations.Axis.Y, null);
        ImageFloat imProjY = new ImageFloat("proj(Y)", image.sizeY(), new float[][]{yProj});
        if (debug) plotProfile(imProjX, 0, 0, true);
        if (debug) plotProfile(imProjY, 0, 0, true);
        
        // get Y coords
        ImageInteger heightMask = threshold(imProjY, IJAutoThresholder.runThresholder(imProjY, null, AutoThresholder.Method.Triangle), true, false);
        if (debug) plotProfile(heightMask, 0, 0, true);
        Region[] objHeight = ImageLabeller.labelImage(heightMask);
        if (objHeight.length==0) return null;
        else if (objHeight.length==1) {
            yStart = objHeight[0].getBounds().xMin();
        } else { // get object with maximum height
            int idxMax = 0;
            for (int i = 1; i<objHeight.length;++i) if (objHeight[i].getBounds().sizeX()>=objHeight[idxMax].getBounds().sizeX()) idxMax=i;
            yStart = objHeight[idxMax].getBounds().xMin();
            if (debug) logger.debug("crop microchannels: yStart: {} idx of margin object: {}", yStart, idxMax);
        }
        // look for derivative maximum around new yStart:
        ImageFloat median = Filters.median(imProjY, new ImageFloat("", 0, 0, 0), new EllipsoidalNeighborhood(3, true));
        ImageFloat projDer = ImageFeatures.getDerivative(median, 1, 1, 0, 0, true);
        if (debug) plotProfile(projDer, 0, 0, true);
        yStart = ArrayUtil.max(projDer.getPixelArray()[0], yStart-cropMargin, yStart+cropMargin);
        yStop = Math.min(yStop, yStart+channelHeight);
        yStart = Math.max(yStart-cropMargin, 0);
        
        // get X coords
        ImageInteger widthMask = threshold(imProjX, IJAutoThresholder.runThresholder(imProjX, null, AutoThresholder.Method.Triangle), true, false);
        if (debug) plotProfile(widthMask, 0, 0, true);
        Region[] objWidth = ImageLabeller.labelImage(widthMask);
        median = Filters.median(imProjX, new ImageFloat("", 0, 0, 0), new EllipsoidalNeighborhood(3, true));
        projDer = ImageFeatures.getDerivative(median, 1, 1, 0, 0, true);
        if (debug) plotProfile(projDer, 0, 0, true);
        if (objWidth.length==0) return null;
        else { 
            // get first object after xStart & margin
            int startObject;
            int xStartTemp=xStart;
            for (startObject = 0; startObject<objWidth.length;++startObject) {
                int curX = objWidth[startObject].getBounds().xMin();
                if (curX>=margin && curX>=xStart) {
                    xStartTemp=curX;
                    break;
                }
            }
            // get first object border: first max of derivative before max of object:
            int maxStart = ArrayUtil.max(imProjX.getPixelArray()[0], objWidth[startObject].getBounds().xMin(), objWidth[startObject].getBounds().xMax());
            int maxDerStart = ArrayUtil.max(projDer.getPixelArray()[0], xStartTemp-cropMargin, maxStart);
            // get last object before xStop & margin
            int stopObject;
            int xStopTemp=xStop;
            for (stopObject = objWidth.length-1; stopObject>=startObject;--stopObject) {
                int curX = objWidth[stopObject].getBounds().xMax();
                if (curX<=(image.sizeX()-margin) && curX<=xStop) {
                    xStopTemp=curX;
                    break;
                }
            }
            int maxStop = ArrayUtil.max(imProjX.getPixelArray()[0], objWidth[stopObject].getBounds().xMin(), objWidth[stopObject].getBounds().xMax());
            int maxDerStop = ArrayUtil.min(projDer.getPixelArray()[0], maxStop, xStopTemp+cropMargin); // pente descendente
            
            xStart=Math.max(0, maxDerStart-cropMargin);
            xStop=Math.min(xStop, maxDerStop+cropMargin);
            if (debug) logger.debug("crop microchannels: xStart: {} idx of margin object: {}, maxStart: {}, maxDerStart: {}", xStart, startObject, maxStart, maxDerStart);
            if (debug) logger.debug("crop microchannels: xStop: {} idx of margin object: {}, maxStop: {}, maxDerStop: {}", xStop, stopObject, maxStop, maxDerStop);
        }
        return new MutableBoundingBox(xStart, xStop, yStart, yStop, 0, image.sizeZ()-1);
    }
    

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        MutableBoundingBox bounds = new MutableBoundingBox(configurationData.get(0), configurationData.get(1), configurationData.get(2), configurationData.get(3), 0, image.sizeZ()-1);
        return image.crop(bounds);
    }
    
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return true;
    }

    public ArrayList getConfigurationData() {
        return configurationData;
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    
}
