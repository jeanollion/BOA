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
import plugins.Segmenter;
import plugins.plugins.transformations.CropMicroChannelBF2D;
import processing.Filters;
import processing.ImageFeatures;
import processing.neighborhood.EllipsoidalNeighborhood;
import utils.ArrayUtil;
import static utils.Utils.plotProfile;

/**
 *
 * @author jollion
 */
public class MicroChannelPhase2D implements Segmenter {

    NumberParameter channelWidth = new BoundedNumberParameter("MicroChannel Width (pixels)", 0, 24, 5, null);
    NumberParameter microChannelWidthError = new BoundedNumberParameter("Microchannel Width error proportion", 2, 0.25, 0, 1);
    Parameter[] parameters = new Parameter[]{channelWidth, microChannelWidthError};
    public static boolean debug = false;

    public MicroChannelPhase2D() {
    }

    public MicroChannelPhase2D(int channelWidth) {
        this.channelWidth.setValue(channelWidth);
    }

    @Override
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        ObjectPopulation objects = run(input, channelWidth.getValue().intValue(), microChannelWidthError.getValue().doubleValue(), 2, 3, 3);
        return objects;
    }

    public static ObjectPopulation run(Image image, int channelWidth, double channelWIdthError, double gradientScale, int erodeSize, int dilateSize) {
        CropMicroChannelBF2D.debug=debug;
        CropMicroChannelBF2D.Result r = CropMicroChannelBF2D.segmentMicroChannels(image, false, 0, channelWidth, channelWIdthError);
        if (r==null) return null;
        ArrayList<Object3D> objects = new ArrayList<Object3D>(r.xMax.length);
        for (int i = 0; i < r.xMax.length; ++i) objects.add(new Object3D(new BlankMask("mask of microchannel:" + (i + 1), r.xMax[i]-r.xMin[i], r.yMax-r.yMin, image.getSizeZ(), r.xMin[i], r.yMin, 0, image.getScaleXY(), image.getScaleZ()), i + 1));
        return new ObjectPopulation(objects, image);
        /*erodeSize = 2 * erodeSize;
        dilateSize = 2 * dilateSize;
        ArrayList<Object3D> seedObjects = new ArrayList<Object3D>(r.xMax.length);
        int ySize = r.yMax-r.yMin;
        int yOff= r.yMin;
        if (ySize <= erodeSize+3) {
            ySize= 3;
            yOff += (ySize - (r.yMax-r.yMin))/2;
        } else {
            ySize-=erodeSize;
            yOff += erodeSize/2;
        }
        for (int i = 0; i < r.xMax.length; ++i) {
            int xSize = r.xMax[i]-r.xMin[i];
            int xOff= r.xMin[i];
            if (xSize<=erodeSize+3) {
                xSize= 3;
                xOff += (xSize - (r.xMax[i]-r.xMin[i]))/2;
            } else {
                xSize-=erodeSize;
                xOff += erodeSize/2;
            }
            seedObjects.add(new Object3D(new BlankMask("mask of microchannel:" + (i + 1), xSize, ySize, image.getSizeZ(), xOff, yOff, 0, image.getScaleXY(), image.getScaleZ()), i + 1));
        }
        ObjectPopulation pop = new ObjectPopulation(seedObjects, image);
        
        // getMask for watershed propagation
        ArrayList<Object3D> maskObjects = new ArrayList<Object3D>(r.xMax.length);
        ySize = r.yMax-r.yMin;
        yOff= r.yMin;
        if (yOff < dilateSize) {
            yOff = 0;
            ySize = r.yMax+dilateSize/2;
        } else {
            yOff -= dilateSize/2;
            ySize +=dilateSize;
        }
        if (ySize+yOff>=image.getSizeY()) ySize-= ySize+yOff - image.getSizeY()+1;
        for (int i = 0; i < r.xMax.length; ++i) {
            int xSize = r.xMax[i]-r.xMin[i];
            int xOff= r.xMin[i];
            if (xSize<dilateSize) {
                xOff =0;
                xSize = r.xMax[i]+dilateSize/2;
            } else {
                xOff -= dilateSize/2;
                xSize = xSize+dilateSize;
            }
            if (xSize+xOff>=image.getSizeX()) xSize-= xSize+xOff - image.getSizeX()+1;
            maskObjects.add(new Object3D(new BlankMask("mask of microchannel:" + (i + 1), xSize, ySize, image.getSizeZ(), xOff, yOff, 0, image.getScaleXY(), image.getScaleZ()), i + 1));
        }
        ObjectPopulation maskPop = new ObjectPopulation(maskObjects, image);
        ImageInteger mask = maskPop.getLabelImage().setName("Dilated objects");
        Image gradient = ImageFeatures.getGradientMagnitude(image, gradientScale, false);
        if (debug) {
            new IJImageDisplayer().showImage(mask);
            new IJImageDisplayer().showImage(pop.getLabelImage().setName("erodedObjects"));
            
            new IJImageDisplayer().showImage(pop2.getLabelImage().setName("objects"));
            new IJImageDisplayer().showImage(gradient);
        }
        
        //pop.fitToEdges(gradient, mask);
        //modifier fitToEdges : les seeds comptées comme foreground ont une intensité comparable aux pixels dans l'objet érodé, les autres sont background
        return pop;
                */
    }
    
    
    
    public Parameter[] getParameters() {
        return parameters;
    }
    

}
