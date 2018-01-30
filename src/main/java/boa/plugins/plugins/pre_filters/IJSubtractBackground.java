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
package boa.plugins.plugins.pre_filters;

import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.input_image.InputImages;
import boa.data_structure.StructureObjectPreProcessing;
import ij.ImagePlus;
import ij.ImageStack;
import boa.image.IJImageWrapper;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.ImageMask;
import boa.image.TypeConverter;
import java.util.ArrayList;
import boa.plugins.Filter;
import boa.plugins.PreFilter;
import boa.plugins.TransformationTimeIndependent;

/**
 *
 * @author jollion
 */
public class IJSubtractBackground implements PreFilter, Filter {
    BooleanParameter method = new BooleanParameter("Method", "Rolling Ball", "Sliding Paraboloid", true);
    BooleanParameter imageType = new BooleanParameter("Image Background", "Dark", "Light", true);
    BooleanParameter smooth = new BooleanParameter("Perform Smoothing", true);
    BooleanParameter corners = new BooleanParameter("Correct corners", true);
    NumberParameter radius = new BoundedNumberParameter("Radius", 2, 20, 0.01, null);
    Parameter[] parameters = new Parameter[]{radius, method, imageType, smooth, corners};
    
    public IJSubtractBackground(double radius, boolean doSlidingParaboloid, boolean lightBackground, boolean smooth, boolean corners) {
        this.radius.setValue(radius);
        method.setSelected(!doSlidingParaboloid);
        this.imageType.setSelected(!lightBackground);
        this.smooth.setSelected(smooth);
        this.corners.setSelected(corners);
    }
    
    public IJSubtractBackground(){}
    
    public Image runPreFilter(Image input, ImageMask mask) {
        return filter(input, radius.getValue().doubleValue(), !method.getSelected(), !imageType.getSelected(), smooth.getSelected(), corners.getSelected());
    }
    /**
     * IJ's subtract background {@link ij.plugin.filter.BackgroundSubtracter#rollingBallBackground(ij.process.ImageProcessor, double, boolean, boolean, boolean, boolean, boolean) }
     * @param input input image (will not be modified)
     * @param radius
     * @param doSlidingParaboloid
     * @param lightBackground
     * @param smooth
     * @param corners
     * @return subtracted image 
     */
    public static ImageFloat filter(Image input, double radius, boolean doSlidingParaboloid, boolean lightBackground, boolean smooth, boolean corners) {
        return filter(input, radius, doSlidingParaboloid, lightBackground, smooth, corners, true);
    }
    public static ImageFloat filter(Image input, double radius, boolean doSlidingParaboloid, boolean lightBackground, boolean smooth, boolean corners, boolean duplicate) {
        if (!(input instanceof ImageFloat)) input = TypeConverter.toFloat(input, null);
        else if (duplicate) input = input.duplicate();
        ImageStack ip = IJImageWrapper.getImagePlus(input).getImageStack();
        for (int z = 0; z<input.getSizeZ(); ++z) new ij.plugin.filter.BackgroundSubtracter().rollingBallBackground(ip.getProcessor(z+1), radius, false, lightBackground, doSlidingParaboloid, smooth, corners);
        return (ImageFloat)input;
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }

    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.SAME;
    }

    public void computeConfigurationData(int channelIdx, InputImages inputImages) {}

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        return filter(image, radius.getValue().doubleValue(), !method.getSelected(), !imageType.getSelected(), smooth.getSelected(), corners.getSelected());
    }
    
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return true;
    }

    public ArrayList getConfigurationData() {
        return null;
    }
    boolean testMode;
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}
}
