/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins.plugins.transformations;

import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.input_image.InputImages;
import boa.data_structure.StructureObjectPreProcessing;
import boa.image.BoundingBox;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import boa.plugins.ConfigurableTransformation;
import java.util.ArrayList;
import boa.plugins.Cropper;
import boa.plugins.MultichannelTransformation;
import boa.plugins.ToolTip;
import boa.plugins.Transformation;

/**
 *
 * @author Jean Ollion
 */
public class SimpleCrop implements MultichannelTransformation, ToolTip {
    NumberParameter xMin = new NumberParameter("X-Min", 0, 0);
    NumberParameter yMin = new NumberParameter("Y-Min", 0, 0);
    NumberParameter zMin = new NumberParameter("Z-Min", 0, 0);
    NumberParameter xLength = new NumberParameter("X-Length", 0, 0);
    NumberParameter yLength = new NumberParameter("Y-Length", 0, 0);
    NumberParameter zLength = new NumberParameter("Z-Length", 0, 0);
    Parameter[] parameters = new Parameter[]{xMin, xLength, yMin, yLength, zMin, zLength};
    MutableBoundingBox bounds;
    public SimpleCrop(){}
    public SimpleCrop(int x, int xL, int y, int yL, int z, int zL){
        xMin.setValue(x);
        xLength.setValue(xL);
        yMin.setValue(y);
        yLength.setValue(yL);
        zMin.setValue(z);
        zLength.setValue(zL);
    }
    public SimpleCrop yMin(int y) {
        this.yMin.setValue(y);
        return this;
    }
    public SimpleCrop xMin(int x) {
        this.xMin.setValue(x);
        return this;
    }
    public SimpleCrop zMin(int z) {
        this.zMin.setValue(z);
        return this;
    }
    public SimpleCrop(int... bounds){
        if (bounds.length>0) xMin.setValue(bounds[0]);
        if (bounds.length>1) xLength.setValue(bounds[1]);
        if (bounds.length>2) yMin.setValue(bounds[2]);
        if (bounds.length>3) yLength.setValue(bounds[3]);
        if (bounds.length>4) zMin.setValue(bounds[4]);
        if (bounds.length>5) zLength.setValue(bounds[5]);
    }
    /*@Override
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
       return bounds!=null;
    }
    @Override
    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        Image input = inputImages.getImage(channelIdx, inputImages.getDefaultTimePoint());
        if (xLength.getValue().intValue()==0) xLength.setValue(input.sizeX()-xMin.getValue().intValue());
        if (yLength.getValue().intValue()==0) yLength.setValue(input.sizeY()-yMin.getValue().intValue());
        if (zLength.getValue().intValue()==0) zLength.setValue(input.sizeZ()-zMin.getValue().intValue());
        bounds = new MutableBoundingBox(xMin.getValue().intValue(), xMin.getValue().intValue()+xLength.getValue().intValue()-1, 
        yMin.getValue().intValue(), yMin.getValue().intValue()+yLength.getValue().intValue()-1, 
        zMin.getValue().intValue(), zMin.getValue().intValue()+zLength.getValue().intValue()-1);
        bounds.trim(input.getBoundingBox());
        
    }*/
    @Override
    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        enshureValidBounds(image);
        return image.crop(bounds);
    }

    private void enshureValidBounds(BoundingBox bb) {
        if (bounds!=null && bounds.getSizeXYZ()!=0) return;
        else synchronized (this) {
            if (bounds == null) bounds = new MutableBoundingBox(xMin.getValue().intValue(), yMin.getValue().intValue(), zMin.getValue().intValue());
            bounds.setxMax(xLength.getValue().intValue()==0 ? bb.xMax() : bounds.xMin()+xLength.getValue().intValue());
            bounds.setyMax(yLength.getValue().intValue()==0 ? bb.yMax() : bounds.yMin()+yLength.getValue().intValue());
            bounds.setzMax(zLength.getValue().intValue()==0 ? bb.zMax() : bounds.zMin()+zLength.getValue().intValue());
            bounds.trim(bb);
        }
        
    }
    
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    @Override
    public OUTPUT_SELECTION_MODE getOutputChannelSelectionMode() {
        return OUTPUT_SELECTION_MODE.ALL;
    }

    boolean testMode;
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}

    @Override
    public String getToolTipText() {
        return "Crop All preprocessed image within a constant bounding box.";
    }

    
}
