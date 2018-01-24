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
package boa.plugins.plugins.pre_filter;

import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.input_image.InputImages;
import boa.data_structure.StructureObjectPreProcessing;
import boa.image.Image;
import java.util.ArrayList;
import boa.plugins.Filter;
import boa.plugins.PreFilter;
import boa.plugins.TransformationTimeIndependent;
import boa.image.processing.Filters;
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;

/**
 *
 * @author jollion
 */
public class Median implements PreFilter, Filter {
    NumberParameter radiusXY = new BoundedNumberParameter("Radius XY (pixels)", 1, 2, 1, null);
    NumberParameter radiusZ = new BoundedNumberParameter("Radius Z (pixels)", 1, 1, 0, null); //TODO: conditional parameter that allow to automatically take in account z-anisotropy
    Parameter[] parameters = new Parameter[]{radiusXY, radiusZ};
    public Median() {}
    public Median(double radiusXY, double radiusZ) {
        this.radiusXY.setValue(radiusXY);
        this.radiusZ.setValue(radiusZ);
    }
    public Image runPreFilter(Image input, StructureObjectPreProcessing structureObject) {
        return filter(input, radiusXY.getValue().doubleValue(), radiusZ.getValue().doubleValue());
    }
    
    public static Image filter(Image input, double radiusXY, double radiusZ) {
        return Filters.median(input, null, Filters.getNeighborhood(radiusXY, radiusZ, input));
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

    public void computeConfigurationData(int channelIdx, InputImages inputImages) { }
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return true;
    }
    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        return filter(image, radiusXY.getValue().doubleValue(), radiusZ.getValue().doubleValue());
    }

    public ArrayList getConfigurationData() {
        return null;
    }
    boolean testMode;
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}
}
