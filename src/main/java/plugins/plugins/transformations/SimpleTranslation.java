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
import dataStructure.containers.InputImages;
import dataStructure.objects.StructureObjectPreProcessing;
import image.Image;
import image.TypeConverter;
import plugins.TransformationTimeIndependent;
import processing.ImageTransformation;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class SimpleTranslation implements TransformationTimeIndependent {
    NumberParameter X = new NumberParameter("X", 3, 0);
    NumberParameter Y = new NumberParameter("Y", 3, 0);
    NumberParameter Z = new NumberParameter("Z", 3, 0);
    ChoiceParameter interpolation = new ChoiceParameter("Interpolation", Utils.toStringArray(ImageTransformation.InterpolationScheme.values()), ImageTransformation.InterpolationScheme.LINEAR.toString(), false);
    Parameter[] parameters = new Parameter[]{X, Y, Z, interpolation};

    public SimpleTranslation() {}
    
    public SimpleTranslation(float dX, float dY, float dZ) {
        X.setValue(dX);
        Y.setValue(dY);
        Z.setValue(dZ);
    }

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        if (!X.hasIntegerValue() || !Y.hasIntegerValue() || !Z.hasIntegerValue()) image = TypeConverter.toFloat(image, null);
        return ImageTransformation.translate(image, X.getValue().doubleValue(), Y.getValue().doubleValue(), Z.getValue().doubleValue(), ImageTransformation.InterpolationScheme.valueOf(interpolation.getSelectedItem()));
    }

    public boolean isTimeDependent() {
        return false;
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
    public Parameter[] getConfigurationData() {
        return null;
    }

    public boolean does3D() {
        return true;
    }

    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.MULTIPLE;
    }

    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        
    }
    
}
