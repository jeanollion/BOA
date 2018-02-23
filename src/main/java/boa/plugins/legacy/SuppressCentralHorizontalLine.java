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
package boa.plugins.legacy;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.input_image.InputImages;
import boa.image.BlankMask;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import boa.image.processing.ImageOperations;
import java.util.ArrayList;
import boa.plugins.Transformation;

/**
 *
 * @author jollion
 */
public class SuppressCentralHorizontalLine  implements Transformation {
    NumberParameter pixelNumber = new BoundedNumberParameter("Number of pixel to erase", 0, 8, 1, null);
    
    public SuppressCentralHorizontalLine(){};
    
    public SuppressCentralHorizontalLine(int pixelNumber){this.pixelNumber.setValue(pixelNumber);};
    
    public void computeConfigurationData(int channelIdx, InputImages inputImages) {}

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        int pixNb = pixelNumber.getValue().intValue();
        if ((image.sizeY()-pixNb)%2==1) pixNb++;
        Image res = Image.createEmptyImage(image.getName(), image, new BlankMask(image.sizeX(), image.sizeY()-pixNb, image.sizeZ()));
        Image.pasteImage(image, res, new MutableBoundingBox(0, 0, 0), new MutableBoundingBox(0, image.sizeX()-1, 0, (image.sizeY()-pixNb)/2-1, 0, image.sizeZ()-1));
        Image.pasteImage(image, res, new MutableBoundingBox(0, (image.sizeY()-pixNb)/2, 0), new MutableBoundingBox(0, image.sizeX()-1, (image.sizeY()+pixNb)/2, image.sizeY()-1, 0, image.sizeZ()-1));
        return res;
    }

    public ArrayList getConfigurationData() {
        return null;
    }
    
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return true;
    }

    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.ALL;
    }

    public Parameter[] getParameters() {
        return new Parameter[]{pixelNumber};
    }

    public boolean does3D() {
        return true;
    }
    boolean testMode;
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}
}
