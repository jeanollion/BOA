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
package boa.plugins.plugins.pre_filter;

import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.ConditionalParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.ScaleXYZParameter;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectPreProcessing;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.processing.ImageOperations;
import boa.plugins.PreFilter;
import static boa.plugins.plugins.pre_filter.ImageFeature.Feature.GAUSS;
import static boa.plugins.plugins.pre_filter.ImageFeature.Feature.GRAD;
import static boa.plugins.plugins.pre_filter.ImageFeature.Feature.HessianDet;
import static boa.plugins.plugins.pre_filter.ImageFeature.Feature.HessianMax;
import static boa.plugins.plugins.pre_filter.ImageFeature.Feature.HessianMaxNorm;
import static boa.plugins.plugins.pre_filter.ImageFeature.Feature.HessianMin;
import static boa.plugins.plugins.pre_filter.ImageFeature.Feature.LoG;
import static boa.plugins.plugins.pre_filter.ImageFeature.Feature.StructureMax;
import boa.image.processing.ImageFeatures;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class ImageFeature implements PreFilter {
    public static enum Feature {
        GAUSS("Gaussian Smooth"),
        GRAD("Gradient"), 
        LoG("Laplacian"), 
        HessianDet("Hessian Det"), 
        HessianMax("Hessian Max"),
        HessianMin("Hessian Min"),
        HessianMaxNorm("Normalized Hessian Max"), 
        StructureMax("Structure Max"),;
        final String name;
        Feature(String name) {
            this.name=name;
        }
    }
    ChoiceParameter feature = new ChoiceParameter("Feature", Utils.transform(Feature.values(), new String[Feature.values().length], f->f.name), Feature.GAUSS.name, false);
    ScaleXYZParameter scale = new ScaleXYZParameter("Scale", 2, 1, true);
    ScaleXYZParameter smoothScale = new ScaleXYZParameter("Smooth Scale", 1, 1, true);
    BoundedNumberParameter normScale = new BoundedNumberParameter("Normalization Scale (pix)", 2, 3, 1, null);
    ConditionalParameter cond = new ConditionalParameter(feature).setDefaultParameters(new Parameter[]{scale}).setActionParameters(HessianMaxNorm.name, new Parameter[]{scale, normScale}).setActionParameters(StructureMax.name, new Parameter[]{scale, smoothScale});

    public ImageFeature() {}
    public ImageFeature setFeature(Feature f) {
        this.feature.setValue(f.name);
        return this;
    }
    public ImageFeature setScale(double scale) {
        this.scale.setScaleXY(scale);
        this.scale.setUseImageCalibration(true);
        return this;
    }
    public ImageFeature setSmoothScale(double scale) {
        this.smoothScale.setScaleXY(scale);
        this.smoothScale.setUseImageCalibration(true);
        return this;
    }
    public ImageFeature setScale(double scaleXY, double scaleZ) {
        this.scale.setScaleXY(scaleXY);
        this.scale.setScaleZ(scaleZ);
        this.scale.setUseImageCalibration(false);
        return this;
    }
    
    @Override
    public Image runPreFilter(Image input, StructureObjectPreProcessing structureObject) {
        //logger.debug("ImageFeature: feature equasl: {}, scale equals: {}, normScale equals: {}", feature==cond.getActionableParameter(), scale == cond.getCurrentParameters().get(0), normScale == cond.getParameters("Normalized Hessian Max").get(1));
        logger.debug("ImageFeauture: feature: {}, scale: {}, scaleZ: {} (from image: {}) normScale: {}", feature.getSelectedItem(), scale.getScaleXY(), scale.getScaleZ(structureObject.getScaleXY(), structureObject.getScaleZ()), scale.getUseImageCalibration(), normScale.getValue());
        String f = feature.getSelectedItem();
        double scaleXY = scale.getScaleXY();
        double scaleZ = scale.getScaleZ(structureObject.getScaleXY(), structureObject.getScaleZ());
        if (GAUSS.name.equals(f)) return ImageFeatures.gaussianSmooth(input, scaleXY, scaleZ, true);
        else if (GRAD.name.equals(f)) return ImageFeatures.getGradientMagnitude(input, scaleXY, true);
        else if (LoG.name.equals(f)) return ImageFeatures.getLaplacian(input, scaleXY, true, true);
        else if (HessianDet.name.equals(f)) return ImageFeatures.getHessianMaxAndDeterminant(input, scaleXY, true)[1];
        else if (HessianMax.name.equals(f)) return ImageFeatures.getHessian(input, scaleXY, true)[0];
        else if (HessianMin.name.equals(f)) {
            ImageFloat[] hess = ImageFeatures.getHessian(input, scaleXY, true);
            return hess[hess.length-1];
        }
        else if (HessianMaxNorm.name.equals(f)) {
            Image hess  = ImageFeatures.getHessian(input, scaleXY, true)[0];
            Image norm = ImageFeatures.gaussianSmooth(input, scaleXY, scaleZ, true);
            ImageOperations.divide(hess, norm, hess);
            return hess;
        } else if (StructureMax.name.equals(f)) {
            return ImageFeatures.getStructure(input, smoothScale.getScaleXY(), scale.getScaleXY(), false)[0];
        } else throw new IllegalArgumentException("No selected feature");
    }

    public Parameter[] getParameters() {
        return new Parameter[]{cond};
    }
    
}
