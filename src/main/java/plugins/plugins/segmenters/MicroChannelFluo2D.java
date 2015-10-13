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
import image.Image;
import image.ImageFloat;
import image.ImageInteger;
import image.ImageLabeller;
import image.ImageOperations;
import static image.ImageOperations.threshold;
import java.util.ArrayList;
import plugins.Segmenter;
import plugins.plugins.thresholders.IJAutoThresholder;
import processing.Filters;
import processing.ImageFeatures;
import processing.neighborhood.EllipsoidalNeighborhood;
import utils.ArrayUtil;
import static utils.Utils.plotProfile;

/**
 *
 * @author jollion
 */
public class MicroChannelFluo2D implements Segmenter {
    NumberParameter channelHeight = new BoundedNumberParameter("MicroChannel Height (pixels)", 0, 350, 5, null);
    NumberParameter channelWidth = new BoundedNumberParameter("MicroChannel Width (pixels)", 0, 30, 5, null);
    NumberParameter yMargin = new BoundedNumberParameter("y-margin", 0, 5, 0, null);
    Parameter[] parameters = new Parameter[]{channelHeight, channelWidth, yMargin};
    
    public MicroChannelFluo2D(){}
    
    public MicroChannelFluo2D(int channelHeight, int channelWidth, int yMargin){
        this.channelHeight.setValue(channelHeight);
        this.channelWidth.setValue(channelWidth);
        this.yMargin.setValue(yMargin);
    }
    
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        // TODO: sum sur tous les temps ou un subset des temps pour une meilleure precision?
        
        // faire le calcul que pour le premier temps et copier les objets pour les temps suivants 
        int refTimePoint = 0;
        ArrayList<Object3D> objects;
        if (parent.getTimePoint()==refTimePoint) { // pour le moment  true || 
            objects= getObjects(input, channelHeight.getValue().intValue(), channelWidth.getValue().intValue(), yMargin.getValue().intValue());
            logger.debug("MicroChannelFluo2D: current timepoint: {} segmented objects: {}, channelHeight: {}, channel width: {}, yMargin: {}", parent.getTimePoint(), objects.size(), channelHeight.getValue().intValue(), channelWidth.getValue().intValue(), yMargin.getValue().intValue());
        }
        else {
            StructureObjectProcessing ref = parent;
            while(ref.getTimePoint()>0 && ref.getTimePoint()!=refTimePoint) ref=(StructureObjectProcessing)ref.getPrevious();
            if (ref.getTimePoint()!=refTimePoint) {
                objects = new ArrayList<Object3D>(0);
                logger.debug("MicroChannelFluo2D: current timepoint: {}, reference timepoint: {} no objects found", parent.getTimePoint(), refTimePoint);
            }
            else {
                ArrayList<? extends StructureObject> oos = ((StructureObject)ref).getChildObjects(structureIdx);
                objects = new ArrayList<Object3D>(oos.size());
                for (StructureObject o : oos) objects.add(o.getObject());
                logger.debug("MicroChannelFluo2D: current timepoint: {}, reference timepoint: {} copied objects: {}", parent.getTimePoint(), refTimePoint, objects.size());
            }
        }
        return new ObjectPopulation(objects, input);
    }
    
    public static ArrayList<Object3D> getObjects(Image image, int channelHeight, int channelWidth, int yMargin) {
        // get yStart
        float[] yProj = ImageOperations.meanProjection(image, ImageOperations.Axis.Y, null);
        ImageFloat imProjY = new ImageFloat("proj(Y)", image.getSizeY(), new float[][]{yProj});
        ImageInteger heightMask = threshold(imProjY, IJAutoThresholder.runThresholder(imProjY, null, AutoThresholder.Method.Triangle), true, false);
        Object3D[] objHeight = ImageLabeller.labelImage(heightMask);
        int yStart;
        if (objHeight.length==0) yStart=0;
        else if (objHeight.length==1) {
            yStart = objHeight[0].getBounds().getxMin();
        } else { // get object with maximum height
            int idxMax = 0;
            for (int i = 1; i<objHeight.length;++i) if (objHeight[i].getBounds().getSizeX()>=objHeight[idxMax].getBounds().getSizeX()) idxMax=i;
            yStart = objHeight[idxMax].getBounds().getxMin();
            //logger.trace("crop microchannels: yStart: {} idx of margin object: {}", yStart, idxMax);
        }
        // refine by searching max of derivative near yStart
        ImageFloat median = Filters.median(imProjY, new ImageFloat("", 0, 0, 0), new EllipsoidalNeighborhood(3, true));
        ImageFloat projDer = ImageFeatures.getDerivative(median, 1, 1, 0, 0, false); 
        yStart = ArrayUtil.max(projDer.getPixelArray()[0], yStart-10, yStart+10);
        logger.trace("MicroChannelFluo: Y search: max of 1st derivate:{}", yStart);
        //refine by searching 2nd derivate maximum
        projDer = ImageFeatures.getDerivative(median, 2, 2, 0, 0, true); 
        //plotProfile(projDer, 0, 0, true);
        yStart = ArrayUtil.max(projDer.getPixelArray()[0], yStart-10, yStart);
        logger.trace("MicroChannelFluo: Y search: max of 2st derivate:{}", yStart);
        yStart = Math.max(0, yStart-yMargin);
        
        // get all XCenters
        float[] xProj = ImageOperations.meanProjection(image, ImageOperations.Axis.X, null);
        ImageFloat imProjX = new ImageFloat("proj(X)", image.getSizeX(), new float[][]{xProj});
        ImageInteger widthMask = threshold(imProjX, IJAutoThresholder.runThresholder(imProjX, null, AutoThresholder.Method.Otsu), true, false);
        Object3D[] objWidth = ImageLabeller.labelImage(widthMask);
        
        ArrayList<Object3D> res = new ArrayList<Object3D>(objWidth.length);
        for (int i = 0; i<objWidth.length; ++i) {
            int xMin = Math.max((int)(objWidth[i].getBounds().getXMean()-channelWidth/2.0), 0);
            res.add(new Object3D(new BlankMask("mask of microchannel:"+(i+1), channelWidth, channelHeight, image.getSizeZ(), xMin, yStart, 0, image.getScaleXY(), image.getScaleZ()), i+1));
        }
        return res;
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    
}
