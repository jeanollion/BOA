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
import boa.plugins.ToolTip;

/**
 *
 * @author Jean Ollion
 */
public class FeatureFilter implements PostFilter, ToolTip {
    PluginParameter<ObjectFeature> feature = new PluginParameter<>("Feature", ObjectFeature.class, false);
    NumberParameter threshold = new NumberParameter("Threshold", 4, 0);
    BooleanParameter keepOverThreshold = new BooleanParameter("Keep over threshold", true);
    BooleanParameter strict = new BooleanParameter("Strict comparison with threshold", true);
    
    Parameter[] parameters = new Parameter[]{feature, threshold, keepOverThreshold, strict};
    
    @Override
    public String getToolTipText() {
        return "Filter regions according to a user-defined feature";
    }
    
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
