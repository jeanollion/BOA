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
import configuration.parameters.Parameter;
import dataStructure.containers.InputImages;
import image.Image;
import image.ImageFloat;
import image.ImageOperations;
import java.util.ArrayList;
import plugins.Transformation;
import plugins.plugins.thresholders.BackgroundFit;
import plugins.plugins.thresholders.KappaSigma;

/**
 *
 * @author jollion
 */
public class ScaleHistogram implements Transformation {
    BoundedNumberParameter scaleFactor= new BoundedNumberParameter("Scale Factor", 1, 100, 1, null);
    BooleanParameter method = new BooleanParameter("Mean estimation method", "Gaussian Fit", "Kappa-Sigma", true);
    Parameter[] parameters = new Parameter[]{scaleFactor, method};
    
    public ScaleHistogram() {}
    
    public ScaleHistogram(double scaleFactor, boolean gaussianFit) {
        this.scaleFactor.setValue(scaleFactor);
        method.setSelected(gaussianFit);
    }
    
    public void computeConfigurationData(int channelIdx, InputImages inputImages) {}

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        double[] meanSigma = new double[2];
        if (method.getSelected()) BackgroundFit.backgroundFit(image, null, 1, meanSigma);
        else KappaSigma.kappaSigmaThreshold(image, null, 3, 2, meanSigma);
        double scale = scaleFactor.getValue().doubleValue() / meanSigma[0];
        logger.debug("timePoint: {} estimated background : {}, scale value: {}", timePoint, meanSigma[0], scale);
        return ImageOperations.affineOperation(image, image instanceof ImageFloat? image: null, scale, 0);
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
