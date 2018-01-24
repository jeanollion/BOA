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
package boa.plugins.plugins.post_filters;

import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.SimpleListParameter;
import boa.configuration.parameters.TextParameter;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectProcessing;
import boa.plugins.ObjectFeature;
import boa.plugins.PostFilter;

/**
 *
 * @author jollion
 */
public class FeatureFilter implements PostFilter {
    PluginParameter<ObjectFeature> feature = new PluginParameter<>("Feature", ObjectFeature.class, false);
    NumberParameter threshold = new NumberParameter("Threshold", 4, 0);
    BooleanParameter keepOverThreshold = new BooleanParameter("Keep over threshold", true);
    BooleanParameter strict = new BooleanParameter("Strict comparison with threshold", true);
    
    Parameter[] parameters = new Parameter[]{feature, threshold, keepOverThreshold, strict};
    
    public FeatureFilter() {}
    public FeatureFilter(ObjectFeature feature, double threshold, boolean keepOverThreshold, boolean strictComparison) {
        this.feature.setPlugin(feature);
        this.threshold.setValue(threshold);
        this.keepOverThreshold.setSelected(keepOverThreshold);
        this.strict.setSelected(strictComparison);
    }
    public FeatureFilter(ObjectFeature feature, double threshold, boolean keepOverThreshold) {
        this(feature, threshold, keepOverThreshold, true);
    } 
    
    @Override
    public RegionPopulation runPostFilter(StructureObject parent, int childStructureIdx, RegionPopulation childPopulation) {
        ObjectFeature f = feature.instanciatePlugin();
        f.setUp(parent, childStructureIdx, childPopulation);
        childPopulation=childPopulation.filter(new RegionPopulation.Feature(f, threshold.getValue().doubleValue(), keepOverThreshold.getSelected(), strict.getSelected()));
        return childPopulation;
    }
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
}
