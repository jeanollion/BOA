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

import boa.gui.imageInteraction.IJImageDisplayer;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.containers.InputImages;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageOperations;
import java.util.ArrayList;
import plugins.Transformation;

/**
 *
 * @author jollion
 */
public class SuppressCentralHorizontalLine implements Transformation {
    NumberParameter pixelNumber = new BoundedNumberParameter("Number of pixel to erase", 0, 8, 1, null);
    
    public SuppressCentralHorizontalLine(){};
    
    public SuppressCentralHorizontalLine(int pixelNumber){this.pixelNumber.setValue(pixelNumber);};
    
    public void computeConfigurationData(int channelIdx, InputImages inputImages) {}

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        int pixNb = pixelNumber.getValue().intValue();
        if ((image.getSizeY()-pixNb)%2==1) pixNb++;
        Image res = Image.createEmptyImage(image.getName(), image, new BlankMask(image.getSizeX(), image.getSizeY()-pixNb, image.getSizeZ()));
        ImageOperations.pasteImage(image, res, new BoundingBox(0, 0, 0), new BoundingBox(0, image.getSizeX()-1, 0, (image.getSizeY()-pixNb)/2-1, 0, image.getSizeZ()-1));
        ImageOperations.pasteImage(image, res, new BoundingBox(0, (image.getSizeY()-pixNb)/2, 0), new BoundingBox(0, image.getSizeX()-1, (image.getSizeY()+pixNb)/2, image.getSizeY()-1, 0, image.getSizeZ()-1));
        return res;
    }

    public ArrayList getConfigurationData() {
        return null;
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
    
}
