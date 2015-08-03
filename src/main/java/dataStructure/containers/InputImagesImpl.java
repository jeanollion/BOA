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
package dataStructure.containers;

import image.Image;
import plugins.Transformation;
import plugins.TransformationTimeIndependent;

/**
 *
 * @author jollion
 */
public class InputImagesImpl implements InputImages{
    InputImage[][] imageTC;

    public InputImagesImpl(InputImage[][] imageTC) {
        this.imageTC = imageTC;
    }
    
    public int getTimePointNumber() {return imageTC.length;}
    public int getChannelNumber() {return imageTC[0].length;}
    
    public void addTransformation(int[] channelIndicies, Transformation transfo) {
        if (channelIndicies!=null) for (int c : channelIndicies) addTransformation(c, transfo);
        else for (int c = 0; c<imageTC[0].length; ++c) addTransformation(c, transfo);
    }
    
    public void addTransformation(int channelIdx, Transformation transfo) {
        for (int t = 0; t<this.imageTC.length; ++t) {
            imageTC[t][channelIdx].addTransformation(transfo);
        }
    }

    public Image getImage(int channelIdx, int timePoint) {
        return imageTC[timePoint][channelIdx].getImage();
    }
    
    public void applyTranformationsSaveAndClose() {
        for (int t = 0; t<getTimePointNumber(); ++t) {
            for (int c = 0; c<getChannelNumber(); ++c) {
                imageTC[t][c].getImage();
                imageTC[t][c].saveToDAO=true;
                imageTC[t][c].closeImage();
            }
        }
    }
}
