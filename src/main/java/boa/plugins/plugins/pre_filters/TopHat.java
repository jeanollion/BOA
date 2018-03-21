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
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.ScaleXYZParameter;
import boa.data_structure.input_image.InputImages;
import boa.data_structure.StructureObjectPreProcessing;
import ij.ImagePlus;
import ij.ImageStack;
import boa.image.IJImageWrapper;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.ImageMask;
import boa.image.processing.ImageOperations;
import boa.image.TypeConverter;
import java.util.ArrayList;
import boa.plugins.Filter;
import boa.plugins.PreFilter;
import boa.image.processing.Filters;
import static boa.image.processing.Filters.open;
import static boa.image.processing.Filters.close;
import boa.image.processing.ImageFeatures;
import boa.image.processing.neighborhood.Neighborhood;

/**
 *
 * @author jollion
 */
public class TopHat implements PreFilter, Filter {

    ScaleXYZParameter radius = new ScaleXYZParameter("Radius");
    BooleanParameter darkBackground = new BooleanParameter("Image Background", "Dark", "Light", true);
    BooleanParameter smooth = new BooleanParameter("Perform Smoothing", true);
    Parameter[] parameters = new Parameter[]{radius, darkBackground, smooth};
    
    public TopHat(double radiusXY, double radiusZ, boolean darkBackground, boolean smooth) {
        this.radius.setScaleXY(radiusXY);
        this.radius.setScaleZ(radiusZ);
        this.darkBackground.setSelected(darkBackground);
        this.smooth.setSelected(smooth);
    }
    public TopHat() { }
    @Override
    public Image runPreFilter(Image input, ImageMask mask) {
        return filter(input, radius.getScaleXY(), radius.getScaleZ(mask.getScaleXY(), mask.getScaleZ()), darkBackground.getSelected(), smooth.getSelected());
    }
    
    public static Image filter(Image input, double radiusXY, double radiusZ, boolean darkBackground, boolean smooth) {
        Neighborhood n = Filters.getNeighborhood(radiusXY, radiusZ, input);
        Image smoothed = smooth ? ImageFeatures.gaussianSmooth(input, 1.5, false) : input ;
        Image bck =darkBackground ? open(smoothed, smooth ? smoothed : null, n) : close(smoothed, smooth ? smoothed : null, n);
        ImageOperations.addImage(input, bck, bck, -1); //1-bck
        bck.resetOffset().translate(input);
        return bck;
    }
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    @Override
    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        return filter(image, radius.getScaleXY(), radius.getScaleZ(image.getScaleXY(), image.getScaleZ()), darkBackground.getSelected(), smooth.getSelected()); 
    }
    
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return true;
    }

    public ArrayList getConfigurationData() {
        return null;
    }
    boolean testMode;
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}
}
