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
import plugins.TransformationTimeIndependent;
import processing.ImageTransformation;

/**
 *
 * @author jollion
 */
public class SimpleRotationXY implements TransformationTimeIndependent {
    NumberParameter angle = new NumberParameter("Angle (degree)", 4, 0);
    Parameter[] parameters = new Parameter[]{angle};
    
    public void computeParameters(int structureIdx, StructureObjectPreProcessing structureObject) {
        
    }

    public Image applyTransformation(Image input) {
        return ImageTransformation.rotateXY(input, angle.getValue().floatValue());
    }

    public boolean isTimeDependent() {
        return false;
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public Object[] getConfigurationData() {
        return null;
    }

    public boolean does3D() {
        return true;
    }
    
}
