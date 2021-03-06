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
package boa.plugins.plugins.pre_filters;

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
import boa.image.ImageMask;
import boa.image.processing.ImageOperations;
import boa.plugins.PreFilter;
import static boa.plugins.plugins.pre_filters.ImageFeature.Feature.GAUSS;
import static boa.plugins.plugins.pre_filters.ImageFeature.Feature.GRAD;
import static boa.plugins.plugins.pre_filters.ImageFeature.Feature.HessianDet;
import static boa.plugins.plugins.pre_filters.ImageFeature.Feature.HessianMax;
import static boa.plugins.plugins.pre_filters.ImageFeature.Feature.HessianMin;
import static boa.plugins.plugins.pre_filters.ImageFeature.Feature.LoG;
import static boa.plugins.plugins.pre_filters.ImageFeature.Feature.StructureMax;
import boa.image.processing.ImageFeatures;
import boa.plugins.ToolTip;
import boa.utils.Utils;
import java.util.Arrays;

/**
 *
 * @author Jean Ollion
 */
public class ImageFeature implements PreFilter, ToolTip {

    @Override
    public String getToolTipText() {
        return "Collection of features computed on image, such as Gaussian Smooth, Laplacian etc.. Uses ImageScience library (imagescience.org)";
    }
    public static enum Feature {
        GAUSS("Gaussian Smooth"),
        GRAD("Gradient"), 
        LoG("Laplacian"), 
        HessianDet("Hessian Det"), 
        HessianMax("Hessian Max"),
        HessianMin("Hessian Min"),
        StructureMax("Structure Max"),
        StructureDet("Structure Det");
        final String name;
        Feature(String name) {
            this.name=name;
        }
        public static  Feature getFeature(String name) {
            return Utils.getFirst(Arrays.asList(Feature.values()), f->f.name.equals(name));
        }
    }
    ChoiceParameter feature = new ChoiceParameter("Feature", Utils.transform(Feature.values(), new String[Feature.values().length], f->f.name), Feature.GAUSS.name, false);
    ScaleXYZParameter scale = new ScaleXYZParameter("Scale", 2, 1, true);
    ScaleXYZParameter smoothScale = new ScaleXYZParameter("Smooth Scale", 2, 1, true);
    ConditionalParameter cond = new ConditionalParameter(feature).setDefaultParameters(new Parameter[]{scale}).setActionParameters(StructureMax.name, new Parameter[]{scale, smoothScale});

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
    public Image runPreFilter(Image input, ImageMask mask) {
        //logger.debug("ImageFeature: feature equasl: {}, scale equals: {}, normScale equals: {}", feature==cond.getActionableParameter(), scale == cond.getCurrentParameters().get(0), normScale == cond.getParameters("Normalized Hessian Max").get(1));
        //logger.debug("ImageFeauture: feature: {}, scale: {}, scaleZ: {} (from image: {}) normScale: {}", feature.getSelectedItem(), scale.getScaleXY(), scale.getScaleZ(mask.getScaleXY(), mask.getScaleZ()), scale.getUseImageCalibration(), normScale.getValue());
        Feature f = Feature.getFeature(feature.getSelectedItem());
        double scaleXY = scale.getScaleXY();
        double scaleZ = scale.getScaleZ(mask.getScaleXY(), mask.getScaleZ());
        switch(f) {
            case GAUSS:
                return ImageFeatures.gaussianSmooth(input, scaleXY, scaleZ, false);
            case GRAD: 
                return ImageFeatures.getGradientMagnitude(input, scaleXY, false);
            case LoG:
                return ImageFeatures.getLaplacian(input, scaleXY, true, false);
            case HessianDet:
                return ImageFeatures.getHessianMaxAndDeterminant(input, scaleXY, false)[1];
            case HessianMax:
                return ImageFeatures.getHessian(input, scaleXY, false)[0];
            case HessianMin:
                ImageFloat[] hess = ImageFeatures.getHessian(input, scaleXY, false);
                return hess[hess.length-1];
            case StructureMax:
                return ImageFeatures.getStructure(input, smoothScale.getScaleXY(), scale.getScaleXY(), false)[0];
            case StructureDet:
                return ImageFeatures.getStructureMaxAndDeterminant(input, smoothScale.getScaleXY(), scale.getScaleXY(), false)[1];
            default:
                throw new IllegalArgumentException("Feature "+feature.getSelectedItem()+"not supported");
        }
    }
    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{cond};
    }
    
}
