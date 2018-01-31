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
package boa.plugins.plugins.segmenters;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectProcessing;
import boa.data_structure.Voxel;
import ij.process.AutoThresholder;
import boa.image.BlankMask;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.processing.ImageOperations;
import static boa.image.processing.ImageOperations.threshold;
import boa.image.processing.RegionFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import boa.plugins.Segmenter;
import boa.plugins.plugins.transformations.CropMicroChannelBF2D;
import boa.plugins.plugins.transformations.CropMicroChannels.Result;
import boa.image.processing.Filters;
import boa.image.processing.ImageFeatures;
import boa.image.processing.WatershedTransform;
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;
import boa.utils.ArrayUtil;
import static boa.utils.Utils.plotProfile;

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
    NumberParameter sigmaThreshold = new BoundedNumberParameter("Border Sigma Threshold", 3, 0.75, 0, 1).setToolTipText("<html>Fine adjustement of X bounds: eliminate lines with no signals <br />After segmentation, standart deviation along y-axis of each line is compared to the one of the center of the microchannel. <br />When the ratio is inferior to this threhsold, the line is eliminated. <br />0 = no adjustement</html>");
    Parameter[] parameters = new Parameter[]{channelWidth, channelWidthMin, channelWidthMax, localDerExtremaThld, sigmaThreshold};
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
    public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        Result r = segment(input);
        if (r==null) return null;
        ArrayList<Region> objects = new ArrayList<>(r.size());
        for (int idx = 0; idx<r.xMax.length; ++idx) {
            objects.add(new Region(new BlankMask("mask of microchannel:" + idx+1, r.getBounds(idx, true).getImageProperties(input.getScaleXY(), input.getScaleZ())), idx+1, true));
            logger.debug("object!: {}", objects.get(objects.size()-1).getBounds());
        }
        return new RegionPopulation(objects, input);
    }
    
    @Override
    public Result segment(Image input) {
        CropMicroChannelBF2D cropper = new CropMicroChannelBF2D().setChannelWidth(channelWidth.getValue().intValue(), channelWidthMin.getValue().intValue(), channelWidthMax.getValue().intValue()).setLocalDerivateXThld(localDerExtremaThld.getValue().doubleValue());
        cropper.setTestMode(debug);
        Result r =  cropper.segmentMicroChannels(input, false, 0, yStartAdjustWindow.getValue().intValue(), 0);
        if (r==null) return null;
        double thld = sigmaThreshold.getValue().doubleValue();
        if (thld>0) { // refine borders: compare Y-variance from sides to center and remove if too low
            for (int idx = 0; idx<r.xMax.length; ++idx) {
                BoundingBox bds = r.getBounds(idx, true);
                int yShift = bds.getSizeX()/2;
                int xSizeInner = bds.getSizeX()/4;
                int maxCropX = Math.min(xSizeInner, 2);
                if (xSizeInner==0) continue;
                BoundingBox inner = new BoundingBox(bds.getxMin()+xSizeInner, bds.getxMax()-xSizeInner, bds.getyMin()+yShift, bds.getyMax()-yShift, bds.getzMin(), bds.getzMax());
                float[] yProjInner = ImageOperations.meanProjection(input, ImageOperations.Axis.Y, inner);
                double[] sm = ArrayUtil.meanSigma(yProjInner, 0, yProjInner.length, null);
                double sigmaInner = sm[1];
                for (int x = bds.getxMin(); x<bds.getxMin()+maxCropX; ++x) { // adjust left
                    double s = getSigmaLine(input, x, inner.getyMin(), (int)inner.getZMean(), yProjInner);
                    //logger.debug("x: {}, sig: {}, ref: {},sig/ref:{}", x, s, sigmaInner, s/sigmaInner);
                    if (s/sigmaInner<thld) r.xMin[idx]=x;
                    else break;
                }
                for (int x = bds.getxMax(); x>=bds.getxMax()-maxCropX; --x) { // adjust right
                    double s = getSigmaLine(input, x, inner.getyMin(), (int)inner.getZMean(), yProjInner);
                    //logger.debug("x: {}, sig: {}, ref: {},sig/ref:{}", x, s, sigmaInner, s/sigmaInner);
                    if (s/sigmaInner<thld) r.xMax[idx]=x;
                    else break;
                }
            }
        }
        return r;
    }
    private static double getSigmaLine(Image image, int x, int yStart, int z, float[] array) {
        for (int y = 0; y<array.length; ++y ) array[y] = image.getPixel(x, y+yStart, z);
        double[] sm = ArrayUtil.meanSigma(array, 0, array.length, null);
        return sm[1];
    }
    
    @Override public Parameter[] getParameters() {
        return parameters;
    }
    

}
