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
import dataStructure.objects.StructureObjectPreFilter;
import image.BoundingBox;
import image.Image;
import plugins.Crop;

/**
 *
 * @author jollion
 */
public class SimpleCrop implements Crop {
    NumberParameter xMin = new NumberParameter("X-Min", 0, 0);
    NumberParameter yMin = new NumberParameter("Y-Min", 0, 0);
    NumberParameter zMin = new NumberParameter("Z-Min", 0, 0);
    NumberParameter xLength = new NumberParameter("X-Length", 0, 0);
    NumberParameter yLength = new NumberParameter("Y-Length", 0, 0);
    NumberParameter zLength = new NumberParameter("Z-Length", 0, 0);
    Parameter[] parameters = new Parameter[]{xMin, xLength, yMin, yLength, zMin, zLength};
    BoundingBox bounds;
    
    public void computeParameters(int structureIdx, StructureObjectPreFilter structureObject) {
        Image input = structureObject.getRawImage(structureIdx);
        if (xLength.getValue().intValue()==0) xLength.setValue(input.getSizeX()-xMin.getValue().intValue());
        if (yLength.getValue().intValue()==0) yLength.setValue(input.getSizeY()-yMin.getValue().intValue());
        if (zLength.getValue().intValue()==0) zLength.setValue(input.getSizeZ()-zMin.getValue().intValue());
        bounds = new BoundingBox(xMin.getValue().intValue(), xMin.getValue().intValue()+xLength.getValue().intValue()-1, 
        yMin.getValue().intValue(), yMin.getValue().intValue()+yLength.getValue().intValue()-1, 
        zMin.getValue().intValue(), zMin.getValue().intValue()+zLength.getValue().intValue()-1);
        bounds.trimToImage(input);
    }

    public Image applyTransformation(Image input) {
        return input.crop(bounds);
    }

    public boolean isTimeDependent() {
        return false;
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
}
