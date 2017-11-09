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

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import configuration.parameters.BooleanParameter;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.ChoiceParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.containers.InputImages;
import dataStructure.objects.StructureObjectPreProcessing;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.AutoThresholder;
import image.BoundingBox;
import image.IJImageWrapper;
import image.Image;
import image.ImageFloat;
import image.ImageOperations;
import image.TypeConverter;
import java.util.ArrayList;
import plugins.Filter;
import plugins.PreFilter;
import plugins.TransformationTimeIndependent;
import plugins.plugins.thresholders.IJAutoThresholder;
import processing.ImageTransformation;

/**
 *
 * @author jollion
 */
public class SubtractBackgroundMicrochannel implements PreFilter {
    BooleanParameter method = new BooleanParameter("Method", "Rolling Ball", "Sliding Paraboloid", false);
    BooleanParameter imageType = new BooleanParameter("Image Background", "Dark", "Light", false);
    BooleanParameter smooth = new BooleanParameter("Perform Smoothing", false);
    BooleanParameter corners = new BooleanParameter("Correct corners", false);
    NumberParameter radius = new BoundedNumberParameter("Radius", 2, 1000, 0.01, null);
    Parameter[] parameters = new Parameter[]{radius, method, imageType, smooth, corners};
    
    public SubtractBackgroundMicrochannel(double radius, boolean doSlidingParaboloid, boolean lightBackground, boolean smooth, boolean corners) {
        this.radius.setValue(radius);
        method.setSelected(!doSlidingParaboloid);
        this.imageType.setSelected(!lightBackground);
        this.smooth.setSelected(smooth);
        this.corners.setSelected(corners);
    }
    
    public SubtractBackgroundMicrochannel(){}
    
    public Image runPreFilter(Image input, StructureObjectPreProcessing structureObject) {
        // mirror image on both Y ends
        input = TypeConverter.toFloat(input, null);
        ImageFloat toFilter = new ImageFloat("", input.getSizeX(), 3*input.getSizeY(), input.getSizeZ());
        ImageOperations.pasteImage(input, toFilter, new BoundingBox(0, input.getSizeY(), 0));
        Image imageFlip = ImageTransformation.flip(input, ImageTransformation.Axis.Y);
        ImageOperations.pasteImage(imageFlip, toFilter, null);
        ImageOperations.pasteImage(imageFlip, toFilter, new BoundingBox(0, 2*input.getSizeY(), 0));
        //ImageWindowManagerFactory.showImage(toFilter);
        double scale = radius.getValue().doubleValue();
        scale = input.getSizeY();
        toFilter = IJSubtractBackground.filter(toFilter, scale , !method.getSelected(), !imageType.getSelected(), smooth.getSelected(), false, false);
        Image crop = toFilter.crop(new BoundingBox(0, input.getSizeX()-1, input.getSizeY(), 2*input.getSizeY()-1, 0, input.getSizeZ()-1));
        // adjust filtered image to get same center value as input image
        double medF = ImageOperations.getMeanAndSigma(crop, null)[0]; // mean is more robust when no cell
        double med = ImageOperations.getMeanAndSigma(input, null)[0];
        //double medF = ImageOperations.getPercentile(crop, null, null, 0.5)[0];
        //double med = ImageOperations.getPercentile(input, null, null, 0.5)[0];
        //logger.debug("sub back micro adjust: {} ({} & {})", med-medF, med, medF);
        if (medF!=med) {
            ImageOperations.affineOperation(crop, crop, 1, med-medF);
        } 
        crop.setCalibration(input);
        crop.resetOffset().addOffset(input);
        return crop;
    }
    
    public Parameter[] getParameters() {
        return parameters;
    }

}
