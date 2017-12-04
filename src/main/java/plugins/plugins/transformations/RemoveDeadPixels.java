/*
 * Copyright (C) 2017 jollion
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
import image.Image;
import java.util.List;
import plugins.Transformation;
import processing.Filters;
import processing.ImageFeatures;

/**
 *
 * @author jollion
 */
public class RemoveDeadPixels implements Transformation {
    NumberParameter threshold = new BoundedNumberParameter("Local Threshold", 5, 40, 0, null).setToolTipText("Difference between pixels and median transform (radius 1 pix.) is computed. If difference is higer than this threshold pixel is considered as dead and will be replaced by the median value");
    
    public RemoveDeadPixels(){}
    public RemoveDeadPixels(double threshold) {
        this.threshold.setValue(threshold);
    }
    
    @Override
    public void computeConfigurationData(int channelIdx, InputImages inputImages) throws Exception { }

    @Override
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return true;
    }

    @Override
    public Image applyTransformation(int channelIdx, int timePoint, Image image) throws Exception {
        double thld= threshold.getValue().doubleValue();
        Image median = Filters.median(image, null, Filters.getNeighborhood(1.5, 1, image));
        //Image blurred = ImageFeatures.gaussianSmooth(image, 5, 5, false);
        median.getBoundingBox().translateToOrigin().loop((x, y, z)->{
            float med = median.getPixel(x, y, z);
            if (image.getPixel(x, y, z)-med>= thld) {
                image.setPixel(x, y, z, med);
                if (testMode) logger.debug("pixel @ [{};{};{}] f={}, value: {}, median: {}, diff: {}", timePoint, x, y, z, image.getPixel(x, y, z), med, image.getPixel(x, y, z)-med );
            }
        });
        return image;
    }

    @Override
    public List getConfigurationData() {
        return null;
    }

    @Override
    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.SAME;
    }
    boolean testMode;
    @Override
    public void setTestMode(boolean testMode) {
        this.testMode=testMode;
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{threshold};
    }
    
}
