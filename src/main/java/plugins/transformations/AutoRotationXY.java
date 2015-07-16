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
package plugins.transformations;

import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.StructureObjectPreProcessing;
import image.Image;
import plugins.Transformation;
import processing.ImageTransformation;
import processing.RadonProjection;

/**
 *
 * @author jollion
 */
public class AutoRotationXY implements Transformation {
    NumberParameter minAngle = new NumberParameter("Minimal Angle for search", 2, -10);
    NumberParameter maxAngle = new NumberParameter("Maximal Angle for search", 2, 10);
    NumberParameter precision1 = new NumberParameter("Angular Precision of first seach", 2, 1);
    NumberParameter precision2 = new NumberParameter("Angular Precision", 0, 0.1);
    Parameter[] parameters = new Parameter[]{minAngle, maxAngle, precision1, precision2};
    Float[] internalParams;
    
    public void computeParameters(int structureIdx, StructureObjectPreProcessing structureObject) {
        Image image = structureObject.getRawImage(structureIdx);
        float angle = (float)RadonProjection.computeRotationAngleXY(image, (int)(image.getSizeZ()/2), minAngle.getValue().doubleValue()+90, maxAngle.getValue().doubleValue()+90, precision1.getValue().doubleValue(), precision2.getValue().doubleValue());
        internalParams = new Float[]{-angle+90};
    }

    public Image applyTransformation(Image input) {
        return ImageTransformation.rotateXY(input, internalParams[0]);
    }

    public boolean isTimeDependent() {
        return false;
    }

    public Object[] getConfigurationParameters() {
        return internalParams;
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    
}
