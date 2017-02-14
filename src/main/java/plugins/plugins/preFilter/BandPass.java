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
import dataStructure.containers.InputImages;
import dataStructure.objects.StructureObjectPreProcessing;
import image.Image;
import java.util.ArrayList;
import plugins.Filter;
import plugins.PreFilter;
import plugins.TransformationTimeIndependent;
import processing.Filters;
import processing.IJFFTBandPass;
import processing.neighborhood.EllipsoidalNeighborhood;

/**
 *
 * @author jollion
 */
public class BandPass implements PreFilter, Filter {
    NumberParameter min = new BoundedNumberParameter("Remove structures under size (pixels)", 1, 0, 1, null);
    NumberParameter max = new BoundedNumberParameter("Remove structures over size (pixels)", 1, 100, 0, null); //TODO: conditional parameter that allow to automatically take in account z-anisotropy
    ChoiceParameter removeStripes = new ChoiceParameter("Remove Stripes", new String[]{"None", "Horizontal", "Vertical"}, "None", false);
    NumberParameter stripeTolerance = new BoundedNumberParameter("Stripes tolerance (%)", 1, 100, 0, 100);
    Parameter[] parameters = new Parameter[]{min, max, removeStripes, stripeTolerance};
    public BandPass() {}
    public BandPass(double min, double max) {
        this(min, max, 0, 0);
    }
    public BandPass(double min, double max, int removeStripes, double stripeTolerance) {
        this.min.setValue(min);
        this.max.setValue(max);
        this.removeStripes.setSelectedIndex(removeStripes);
        this.stripeTolerance.setValue(stripeTolerance);
    }
    @Override public Image runPreFilter(Image input, StructureObjectPreProcessing structureObject) {
        return filter(input, min.getValue().doubleValue(), max.getValue().doubleValue(), removeStripes.getSelectedIndex(), stripeTolerance.getValue().doubleValue());
    }
    
    private static Image filter(Image input, double min, double max, int stripes, double stripeTolerance) {
        return IJFFTBandPass.bandPass(input, min, max, stripes, stripeTolerance);
    }

    @Override public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }

    @Override public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.SAME;
    }

    @Override public void computeConfigurationData(int channelIdx, InputImages inputImages) { }
    @Override public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return true;
    }
    @Override public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        return filter(image, min.getValue().doubleValue(), max.getValue().doubleValue(), removeStripes.getSelectedIndex(), stripeTolerance.getValue().doubleValue());
    }

    @Override public ArrayList getConfigurationData() {
        return null;
    }
    
}
