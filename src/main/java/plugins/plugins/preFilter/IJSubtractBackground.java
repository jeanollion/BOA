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
package plugins.plugins.preFilter;

import configuration.parameters.BooleanParameter;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.ChoiceParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.containers.InputImages;
import dataStructure.objects.StructureObjectPreProcessing;
import ij.ImagePlus;
import ij.ImageStack;
import image.IJImageWrapper;
import image.Image;
import java.util.ArrayList;
import plugins.PreFilter;
import plugins.TransformationTimeIndependent;

/**
 *
 * @author jollion
 */
public class IJSubtractBackground implements PreFilter, TransformationTimeIndependent {
    BooleanParameter method = new BooleanParameter("Method", "Rolling Ball", "Sliding Paraboloid", true);
    BooleanParameter imageType = new BooleanParameter("Image Background", "Dark", "Light", true);
    BooleanParameter smooth = new BooleanParameter("Perform Smoothing", true);
    BooleanParameter corners = new BooleanParameter("Correct corners", true);
    NumberParameter radius = new BoundedNumberParameter("Radius", 1, 20, 1, null);
    Parameter[] parameters = new Parameter[]{radius, method, imageType, smooth, corners};
    
    public IJSubtractBackground(double radius, boolean doSlidingParaboloid, boolean lightBackground, boolean smooth, boolean corners) {
        this.radius.setValue(radius);
        method.setSelected(!doSlidingParaboloid);
        this.imageType.setSelected(!lightBackground);
        this.smooth.setSelected(smooth);
        this.corners.setSelected(corners);
    }
    
    public IJSubtractBackground(){}
    
    public Image runPreFilter(Image input, StructureObjectPreProcessing structureObject) {
        
        filter(input, radius.getValue().doubleValue(), !method.getSelected(), !imageType.getSelected(), smooth.getSelected(), corners.getSelected());
        return input;
    }
    
    public static void filter(Image input, double radius, boolean doSlidingParaboloid, boolean lightBackground, boolean smooth, boolean corners) {
        ImageStack ip = IJImageWrapper.getImagePlus(input).getImageStack();
        for (int z = 0; z<input.getSizeZ(); ++z) new ij.plugin.filter.BackgroundSubtracter().rollingBallBackground(ip.getProcessor(z+1), radius, false, lightBackground, doSlidingParaboloid, smooth, corners);
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
        filter(image, radius.getValue().doubleValue(), !method.getSelected(), !imageType.getSelected(), smooth.getSelected(), corners.getSelected());
        return image;
    }

    public ArrayList getConfigurationData() {
        return null;
    }
    
}
