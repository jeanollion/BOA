/*
 * Copyright (C) 2016 jollion
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
import image.BoundingBox.LoopFunction;
import image.Image;
import java.util.ArrayList;
import plugins.Transformation;

/**
 *
 * @author jollion
 */
public class SaturateHistogram implements Transformation {
    NumberParameter threshold = new NumberParameter("Saturation initiation value", 4, 400);
    NumberParameter maxValue = new NumberParameter("Maximum value", 3, 500);
    Parameter[] parameters = new Parameter[]{threshold, maxValue};
    
    public SaturateHistogram(){}
    public SaturateHistogram(double saturationThreshold, double maxValue){
        threshold.setValue(saturationThreshold);
        this.maxValue.setValue(maxValue);
    }
    
    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        
    }

    public Image applyTransformation(int channelIdx, int timePoint, final Image image) {
        final double thld = threshold.getValue().doubleValue();
        final double maxTarget = maxValue.getValue().doubleValue();
        if (maxTarget<thld) throw new IllegalArgumentException("Saturate histogram transformation: configuration error: Maximum value should be superior to threhsold value");
        double maxObs = image.getMinAndMax(null)[1];
        if (maxObs<=thld) return image;
        
        final double factor = (maxTarget - thld) / (maxObs - thld);
        final double add = thld * (1 - factor);

        image.getBoundingBox().loop(new LoopFunction() {

            public void setUp() {}

            public void tearDown() {}

            public void loop(int x, int y, int z) {
                float value = image.getPixel(x, y, z);
                if (value>thld) {
                    image.setPixel(x, y, z, value * factor + add);
                }
            }
        });
        return image;
    }

    public ArrayList getConfigurationData() {
        return null;
    }

    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.SAME;
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
}
