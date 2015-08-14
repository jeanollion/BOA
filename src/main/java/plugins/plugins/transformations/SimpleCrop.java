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

import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.containers.InputImages;
import dataStructure.objects.StructureObjectPreProcessing;
import image.BoundingBox;
import image.Image;
import plugins.Cropper;

/**
 *
 * @author jollion
 */
public class SimpleCrop implements Cropper {
    NumberParameter xMin = new NumberParameter("X-Min", 0, 0);
    NumberParameter yMin = new NumberParameter("Y-Min", 0, 0);
    NumberParameter zMin = new NumberParameter("Z-Min", 0, 0);
    NumberParameter xLength = new NumberParameter("X-Length", 0, 0);
    NumberParameter yLength = new NumberParameter("Y-Length", 0, 0);
    NumberParameter zLength = new NumberParameter("Z-Length", 0, 0);
    Parameter[] parameters = new Parameter[]{xMin, xLength, yMin, yLength, zMin, zLength};
    BoundingBox bounds;
    int[] configurationData;
    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        Image input = inputImages.getImage(channelIdx, inputImages.getDefaultTimePoint());
        if (xLength.getValue().intValue()==0) xLength.setValue(input.getSizeX()-xMin.getValue().intValue());
        if (yLength.getValue().intValue()==0) yLength.setValue(input.getSizeY()-yMin.getValue().intValue());
        if (zLength.getValue().intValue()==0) zLength.setValue(input.getSizeZ()-zMin.getValue().intValue());
        bounds = new BoundingBox(xMin.getValue().intValue(), xMin.getValue().intValue()+xLength.getValue().intValue()-1, 
        yMin.getValue().intValue(), yMin.getValue().intValue()+yLength.getValue().intValue()-1, 
        zMin.getValue().intValue(), zMin.getValue().intValue()+zLength.getValue().intValue()-1);
        bounds.trimToImage(input);
        configurationData = new int[6];
        configurationData[0]=bounds.getxMin();
        configurationData[1]=bounds.getxMax();
        configurationData[2]=bounds.getyMin();
        configurationData[3]=bounds.getyMax();
        configurationData[4]=bounds.getzMin();
        configurationData[5]=bounds.getzMax();
    }

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        if (bounds==null) bounds= new BoundingBox(configurationData[0], configurationData[1], configurationData[2], configurationData[3], configurationData[4], configurationData[5]);
        return image.crop(bounds);
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
        return SelectionMode.ALL;
    }
    
}
