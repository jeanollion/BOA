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
package plugins.plugins.preFilter;

import configuration.parameters.BooleanParameter;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.ChoiceParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.ScaleXYZParameter;
import dataStructure.containers.InputImages;
import dataStructure.objects.StructureObjectPreProcessing;
import ij.ImagePlus;
import ij.ImageStack;
import image.IJImageWrapper;
import image.Image;
import image.ImageFloat;
import image.ImageOperations;
import image.TypeConverter;
import java.util.ArrayList;
import plugins.Filter;
import plugins.PreFilter;
import plugins.TransformationTimeIndependent;
import processing.Filters;
import static processing.Filters.open;
import static processing.Filters.close;
import processing.ImageFeatures;
import processing.neighborhood.Neighborhood;

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
    
    public Image runPreFilter(Image input, StructureObjectPreProcessing structureObject) {
        return filter(input, radius.getScaleXY(), radius.getScaleZ(structureObject.getScaleXY(), structureObject.getScaleZ()), darkBackground.getSelected(), smooth.getSelected());
    }
    
    public static Image filter(Image input, double radiusXY, double radiusZ, boolean darkBackground, boolean smooth) {
        Neighborhood n = Filters.getNeighborhood(radiusXY, radiusZ, input);
        Image smoothed = smooth ? ImageFeatures.gaussianSmooth(input, 1.5, 1.5, false) : input ;
        Image bck =darkBackground ? open(smoothed, smooth ? smoothed : null, n) : close(smoothed, smooth ? smoothed : null, n);
        ImageOperations.addImage(input, bck, bck, -1); //1-bck
        bck.resetOffset().addOffset(input);
        return bck;
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }

    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.SAME;
    }

    public void computeConfigurationData(int channelIdx, InputImages inputImages) {}

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
