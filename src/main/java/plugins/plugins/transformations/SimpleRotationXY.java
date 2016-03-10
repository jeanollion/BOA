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

import configuration.parameters.BooleanParameter;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.ChoiceParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.containers.InputImages;
import dataStructure.objects.StructureObjectPreProcessing;
import image.Image;
import image.TypeConverter;
import java.util.ArrayList;
import plugins.TransformationTimeIndependent;
import processing.ImageTransformation;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class SimpleRotationXY implements TransformationTimeIndependent {
    NumberParameter angle = new BoundedNumberParameter("Angle (degree)", 4, 0, -180, 180);
    ChoiceParameter interpolation = new ChoiceParameter("Interpolation", Utils.toStringArray(ImageTransformation.InterpolationScheme.values()), ImageTransformation.InterpolationScheme.LINEAR.toString(), false);
    BooleanParameter removeIncomplete = new BooleanParameter("Remove incomplete rows and columns", false);
    Parameter[] parameters = new Parameter[]{angle, interpolation, removeIncomplete};
    
    public SimpleRotationXY() {}
    
    public SimpleRotationXY(double angle) {
        if (angle>360) angle=angle%360;
        else if (angle<-360) angle=angle%-360;
        if (angle>180) angle=-360+angle;
        else if (angle<-180) angle = 360-angle;
        this.angle.setValue(angle);
    }
    
    public void computeParameters(int structureIdx, StructureObjectPreProcessing structureObject) {
        
    }

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        return ImageTransformation.rotateXY(TypeConverter.toFloat(image, null), angle.getValue().floatValue(), ImageTransformation.InterpolationScheme.valueOf(interpolation.getSelectedItem()), removeIncomplete.getSelected());
    }

    public boolean isTimeDependent() {
        return false;
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public ArrayList getConfigurationData() {
        return null;
    }

    public boolean does3D() {
        return true;
    }

    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.ALL;
    }

    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        
    }
}
