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
package plugins.plugins.postFilters;

import configuration.parameters.BooleanParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import configuration.parameters.SimpleListParameter;
import configuration.parameters.TextParameter;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectProcessing;
import plugins.ObjectFeature;
import plugins.PostFilter;

/**
 *
 * @author jollion
 */
public class FeatureFilter implements PostFilter {
    PluginParameter<ObjectFeature> feature = new PluginParameter<ObjectFeature>("Feature", ObjectFeature.class, false);
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
    public ObjectPopulation runPostFilter(StructureObject parent, int childStructureIdx, ObjectPopulation childPopulation) {
        ObjectFeature f = feature.instanciatePlugin();
        f.setUp(parent, childStructureIdx, childPopulation);
        childPopulation=childPopulation.filter(new ObjectPopulation.Feature(f, threshold.getValue().doubleValue(), keepOverThreshold.getSelected(), strict.getSelected()));
        return childPopulation;
    }
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
}
