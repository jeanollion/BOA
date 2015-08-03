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
package plugins.plugins.transformations;

import configuration.parameters.ChoiceParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.containers.InputImage;
import dataStructure.containers.InputImages;
import dataStructure.objects.StructureObjectPreProcessing;
import image.Image;
import plugins.TransformationTimeIndependent;
import processing.ImageTransformation;
import processing.RadonProjection;

/**
 *
 * @author jollion
 */
public class AutoRotationXY implements TransformationTimeIndependent {
    NumberParameter minAngle = new NumberParameter("Minimal Angle for search", 2, -10);
    NumberParameter maxAngle = new NumberParameter("Maximal Angle for search", 2, 10);
    NumberParameter precision1 = new NumberParameter("Angular Precision of first seach", 2, 1);
    NumberParameter precision2 = new NumberParameter("Angular Precision", 0, 0.1);
    ChoiceParameter interpolation = new ChoiceParameter("Interpolation", ImageTransformation.InterpolationScheme.getValues(), ImageTransformation.InterpolationScheme.LINEAR.toString(), false);
    Parameter[] parameters = new Parameter[]{minAngle, maxAngle, precision1, precision2, interpolation};
    Float[] internalParams;

    public boolean isTimeDependent() {
        return false;
    }

    public Object[] getConfigurationData() {
        return internalParams;
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }

    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.ALL;
    }

    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        Image image = inputImages.getImage(channelIdx, inputImages.getDefaultTimePoint());
        float angle = (float)RadonProjection.computeRotationAngleXY(image, (int)(image.getSizeZ()/2), minAngle.getValue().doubleValue()+90, maxAngle.getValue().doubleValue()+90, precision1.getValue().doubleValue(), precision2.getValue().doubleValue());
        internalParams = new Float[]{-angle+90};
    }

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        return ImageTransformation.rotateXY(image, internalParams[0], ImageTransformation.InterpolationScheme.valueOf(interpolation.getSelectedItem()));
    }
    
}
