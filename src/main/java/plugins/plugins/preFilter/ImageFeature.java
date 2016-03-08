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
package plugins.plugins.preFilter;

import configuration.parameters.BooleanParameter;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.ChoiceParameter;
import configuration.parameters.ConditionalParameter;
import configuration.parameters.Parameter;
import configuration.parameters.ScaleXYZParameter;
import dataStructure.objects.StructureObjectPreProcessing;
import image.Image;
import image.ImageOperations;
import plugins.PreFilter;
import processing.ImageFeatures;

/**
 *
 * @author jollion
 */
public class ImageFeature implements PreFilter {
    ChoiceParameter feature = new ChoiceParameter("Feature", new String[]{"Gaussian Smooth", "Gradient", "Laplacian", "Hessian Det", "Hessian Max", "Normalized Hessian Max"}, "Gaussian Smooth", false);
    ScaleXYZParameter scale = new ScaleXYZParameter("Scale", 2, 1, true);
    BoundedNumberParameter normScale = new BoundedNumberParameter("Normalization Scale (pix)", 2, 3, 1, null);
    ConditionalParameter cond = new ConditionalParameter(feature).setDefaultParameters(new Parameter[]{scale}).setAction("Normalized Hessian Max", new Parameter[]{scale, normScale});
    
    public Image runPreFilter(Image input, StructureObjectPreProcessing structureObject) {
        logger.debug("ImageFeature: feature equlas: {}, scale equals: {}", feature==cond.getActionableParameter(), scale == cond.getCurrentParameters().get(0));
        String f = feature.getSelectedItem();
        double scaleXY = scale.getScaleXY();
        double scaleZ = scale.getScaleZ();
        if ("Gaussian Smooth".equals(f)) return ImageFeatures.gaussianSmoothScaled(input, scaleXY, scaleZ, true);
        else if ("Gradient".equals(f)) return ImageFeatures.getGradientMagnitude(input, scaleXY, true);
        else if ("Laplacian".equals(f)) return ImageFeatures.getLaplacian(input, scaleXY, true, true);
        else if ("Hessian Det".equals(f)) return ImageFeatures.getHessianMaxAndDeterminant(input, scaleXY, true)[1];
        else if ("Hessian Max".equals(f)) return ImageFeatures.getHessian(input, scaleXY, true)[0];
        else if ("Normalized Hessian Max".equals(f)) {
            Image hess  = ImageFeatures.getHessian(input, scaleXY, true)[0];
            Image norm = ImageFeatures.gaussianSmooth(input, scaleXY, scaleZ, true);
            ImageOperations.divide(hess, norm, hess);
            return hess;
        } else throw new IllegalArgumentException("No selected feature");
    }

    public Parameter[] getParameters() {
        return new Parameter[]{cond};
    }
    
}
