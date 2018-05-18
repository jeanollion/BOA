/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins.plugins.pre_filters;

import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.ScaleXYZParameter;
import boa.data_structure.input_image.InputImages;
import boa.data_structure.StructureObjectPreProcessing;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.ImageMask;
import java.util.ArrayList;
import boa.plugins.Filter;
import boa.plugins.PreFilter;
import boa.image.processing.Filters;
import static boa.image.processing.Filters.applyFilter;

/**
 *
 * @author jollion
 */
public class Sigma implements PreFilter, Filter {
    ScaleXYZParameter radius = new ScaleXYZParameter("Radius", 3, 1, true).setToolTipText("Radius in pixel");
    ScaleXYZParameter medianRadius = new ScaleXYZParameter("Median Filtering Radius", 0, 1, true).setToolTipText("Radius for median filtering, prior to sigma, in pixel. 0 = no median filtering");
    Parameter[] parameters = new Parameter[]{radius, medianRadius};
    public Sigma() {}
    public Sigma(double radius) {
        this.radius.setScaleXY(radius);
        this.radius.setUseImageCalibration(true);
    }
    public Sigma(double radiusXY, double radiusZ) {
        this.radius.setScaleXY(radiusXY);
        this.radius.setScaleZ(radiusZ);
    }
    public Sigma setMedianRadius(double radius) {
        this.medianRadius.setScaleXY(radius);
        this.medianRadius.setUseImageCalibration(true);
        return this;
    }
    public Sigma setMedianRadius(double radiusXY, double radiusZ) {
        this.medianRadius.setScaleXY(radiusXY);
        this.medianRadius.setScaleZ(radiusZ);
        return this;
    }
    @Override
    public Image runPreFilter(Image input, ImageMask mask) {
        return filter(input, mask, radius.getScaleXY(), radius.getScaleZ(input.getScaleXY(), input.getScaleZ()), medianRadius.getScaleXY(), medianRadius.getScaleZ(input.getScaleXY(), input.getScaleZ()));
    }
    
    public static Image filter(Image input, double radiusXY, double radiusZ, double medianXY, double medianZ) {
        return filter(input, null, radiusXY, radiusZ, medianXY, medianZ);
    }
    public static Image filter(Image input, ImageMask mask, double radiusXY, double radiusZ, double medianXY, double medianZ) {
        if (medianXY>1)  input = applyFilter(input, new ImageFloat("sigma", input), new Filters.Median(mask), Filters.getNeighborhood(medianXY, medianZ, input));
        return applyFilter(input, new ImageFloat("sigma", input), new Filters.Sigma(mask), Filters.getNeighborhood(radiusXY, radiusZ, input));
    }
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }

    @Override 
    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        return runPreFilter(image, null);
    }

    boolean testMode;
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}
}
