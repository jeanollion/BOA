/*
 * Copyright (C) 2018 jollion
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
package boa.plugins.plugins.thresholders;

import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.data_structure.StructureObjectProcessing;
import boa.image.Image;
import boa.image.ImageMask;
import boa.plugins.SimpleThresholder;

/**
 *
 * @author jollion
 */
public class CompareThresholds implements SimpleThresholder {
    public PluginParameter<SimpleThresholder> threshold1 = new PluginParameter("Threshold 1", SimpleThresholder.class, false);
    public PluginParameter<SimpleThresholder> threshold2 = new PluginParameter("Threshold 2", SimpleThresholder.class, false);
    public BooleanParameter max = new BooleanParameter("Compute", "Max", "Min", true);
    Parameter[] parameters = new Parameter[]{threshold1, threshold2, max};
    
    public CompareThresholds() {}
    public CompareThresholds(SimpleThresholder thld1, SimpleThresholder thld2, boolean max) {
        this.threshold1.setPlugin(thld1);
        this.threshold2.setPlugin(thld2);
        this.max.setSelected(max);
    }
    
    @Override
    public double runSimpleThresholder(Image image, ImageMask mask) {
        double thld1 = threshold1.instanciatePlugin().runSimpleThresholder(image, mask);
        double thld2 = threshold2.instanciatePlugin().runSimpleThresholder(image, mask);
        return max.getSelected() ? Math.max(thld1, thld2) : Math.min(thld1, thld2);
    }

    @Override
    public double runThresholder(Image input, StructureObjectProcessing structureObject) {
        double thld1 = threshold1.instanciatePlugin().runThresholder(input, structureObject);
        double thld2 = threshold2.instanciatePlugin().runThresholder(input, structureObject);
        return max.getSelected() ? Math.max(thld1, thld2) : Math.min(thld1, thld2);
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
}
