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
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectProcessing;
import dataStructure.objects.Voxel;
import ij.process.AutoThresholder;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageFloat;
import image.ImageInteger;
import image.ImageLabeller;
import image.ImageOperations;
import static image.ImageOperations.threshold;
import image.ObjectFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import plugins.Segmenter;
import plugins.plugins.transformations.CropMicroChannelBF2D;
import plugins.plugins.transformations.CropMicroChannels.Result;
import processing.Filters;
import processing.ImageFeatures;
import processing.WatershedTransform;
import processing.neighborhood.EllipsoidalNeighborhood;
import utils.ArrayUtil;
import static utils.Utils.plotProfile;

/**
 *
 * @author jollion
 */
public class MicrochannelPhase2D implements MicrochannelSegmenter {
    
    NumberParameter channelWidth = new BoundedNumberParameter("MicroChannel Typical Width (pixels)", 0, 20, 5, null);
    NumberParameter channelWidthMin = new BoundedNumberParameter("MicroChannel Width Min(pixels)", 0, 15, 5, null);
    NumberParameter channelWidthMax = new BoundedNumberParameter("MicroChannel Width Max(pixels)", 0, 28, 5, null);
    NumberParameter yStartAdjustWindow = new BoundedNumberParameter("Y-Start Adjust Window (pixels)", 0, 5, 0, null).setToolTipText("Window within which y-coordinate of start of microchannel will be refined (in pixels)");
    NumberParameter localDerExtremaThld = new BoundedNumberParameter("X-Derivative Threshold (absolute value)", 3, 10, 0, null).setToolTipText("Threshold for Microchannel border detection (peaks of 1st derivative in X-axis)");
    Parameter[] parameters = new Parameter[]{channelWidth, channelWidthMin, channelWidthMax, localDerExtremaThld};
    public static boolean debug = false;

    public MicrochannelPhase2D() {
    }

    public MicrochannelPhase2D(int channelWidth) {
        this.channelWidth.setValue(channelWidth);
    }

    public MicrochannelPhase2D setyStartAdjustWindow(int yStartAdjustWindow) {
        this.yStartAdjustWindow.setValue(yStartAdjustWindow);
        return this;
    }
    @Override
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        Result r = segment(input);
        if (r==null) return null;
        ArrayList<Object3D> objects = new ArrayList<Object3D>(r.size());
        for (int idx = 0; idx<r.xMax.length; ++idx) objects.add(new Object3D(new BlankMask("mask of microchannel:" + idx+1, r.getBounds(idx, true).getImageProperties(input.getScaleXY(), input.getScaleZ())), idx+1));
        return new ObjectPopulation(objects, input);
    }
    
    @Override
    public Result segment(Image input) {
        CropMicroChannelBF2D cropper = new CropMicroChannelBF2D().setChannelWidth(channelWidth.getValue().intValue(), channelWidthMin.getValue().intValue(), channelWidthMax.getValue().intValue()).setLocalDerivateXThld(localDerExtremaThld.getValue().doubleValue());
        cropper.setTestMode(debug);
        Result r =   cropper.segmentMicroChannels(input, false, 0, channelWidth.getValue().intValue(), channelWidthMin.getValue().intValue(), channelWidthMax.getValue().intValue(), yStartAdjustWindow.getValue().intValue(), localDerExtremaThld.getValue().doubleValue(), 0);
        return r;
    }
    
    @Override public Parameter[] getParameters() {
        return parameters;
    }
    

}
