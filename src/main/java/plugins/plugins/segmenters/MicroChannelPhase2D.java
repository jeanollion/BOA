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
import java.util.List;
import plugins.Segmenter;
import plugins.plugins.transformations.CropMicroChannelBF2D;
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
public class MicroChannelPhase2D implements Segmenter {
    
    NumberParameter channelWidth = new BoundedNumberParameter("MicroChannel Width (pixels)", 0, 24, 5, null);
    NumberParameter microChannelWidthError = new BoundedNumberParameter("Microchannel Width error proportion", 2, 0.25, 0, 1);
    NumberParameter yStartAdjustWindow = new BoundedNumberParameter("Y-Start Adjust Window (pixels)", 0, 5, 0, null);
    Parameter[] parameters = new Parameter[]{channelWidth, microChannelWidthError, yStartAdjustWindow};
    public static boolean debug = false;

    public MicroChannelPhase2D() {
    }

    public MicroChannelPhase2D(int channelWidth) {
        this.channelWidth.setValue(channelWidth);
    }

    public MicroChannelPhase2D setyStartAdjustWindow(int yStartAdjustWindow) {
        this.yStartAdjustWindow.setValue(yStartAdjustWindow);
        return this;
    }
    @Override
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        Result r = segment(input);
        if (r==null) return null;
        ArrayList<Object3D> objects = new ArrayList<Object3D>(r.size());
        for (int idx = 0; idx<r.xMax.length; ++idx) objects.add(new Object3D(new BlankMask("mask of microchannel:" + idx+1, r.getBounds(idx).getImageProperties(input.getScaleXY(), input.getScaleZ())), idx+1));
        return new ObjectPopulation(objects, input);
    }
    public Result segment(Image input) {
        return  CropMicroChannelBF2D.segmentMicroChannels(input, false, 0, channelWidth.getValue().intValue(), microChannelWidthError.getValue().doubleValue(), yStartAdjustWindow.getValue().intValue());
    }
    
    
    @Override public Parameter[] getParameters() {
        return parameters;
    }
    

}
