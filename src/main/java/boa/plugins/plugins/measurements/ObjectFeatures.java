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
package boa.plugins.plugins.measurements;

import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.ParameterListener;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.PreFilterSequence;
import boa.configuration.parameters.SimpleListParameter;
import boa.configuration.parameters.StructureParameter;
import boa.configuration.parameters.TextParameter;
import boa.data_structure.StructureObject;
import java.util.ArrayList;
import java.util.List;
import boa.measurement.MeasurementKey;
import boa.measurement.MeasurementKeyObject;
import boa.plugins.Measurement;
import boa.plugins.ObjectFeature;
import boa.plugins.PreFilter;
import boa.plugins.objectFeature.ObjectFeatureCore;
import boa.plugins.objectFeature.ObjectFeatureWithCore;

/**
 *
 * @author jollion
 */
public class ObjectFeatures implements Measurement {
    StructureParameter structure = new StructureParameter("Structure", -1, false, false);
    PluginParameter<ObjectFeature> def = new PluginParameter<>("Feature", ObjectFeature.class, false).setAdditionalParameters(new TextParameter("Key", "", false));
    SimpleListParameter<PluginParameter<ObjectFeature>> features = new SimpleListParameter<>("Features", 0, def);
    PreFilterSequence preFilters = new PreFilterSequence("Pre-Filters").setToolTipText("Pre-Filters that will be used by all intensity measurement");
    Parameter[] parameters = new Parameter[]{structure, preFilters, features};
    public ObjectFeatures() {
        def.addListener((Parameter sourceParameter) -> {
            PluginParameter<ObjectFeature> s = (PluginParameter<ObjectFeature>)sourceParameter;
            TextParameter tp = ((TextParameter)s.getAdditionalParameters().get(0));
            if (s.isOnePluginSet()) tp.setValue(s.instanciatePlugin().getDefaultName());
            else tp.setValue("");
        });
    }

    public ObjectFeatures(int structureIdx) {
        this();
        this.structure.setSelectedIndex(structureIdx);
    }
    public ObjectFeatures addFeatures(ObjectFeature... features) {
        if (features==null) return this;
        for (ObjectFeature f : features) {
            PluginParameter<ObjectFeature> dup = def.duplicate().setPlugin(f);
            ((TextParameter)dup.getAdditionalParameters().get(0)).setValue(f.getDefaultName());
            this.features.insert(dup);
        }
        return this;
    }
    public ObjectFeatures addFeature(ObjectFeature feature, String key) {
        PluginParameter<ObjectFeature> f = def.duplicate().setPlugin(feature);
        ((TextParameter)f.getAdditionalParameters().get(0)).setValue(key);
        this.features.insert(f);
        return this;
    }
    public ObjectFeatures addPreFilter(PreFilter... prefilters) {
        this.preFilters.add(prefilters);
        return this;
    }
    public int getCallStructure() {
        return structure.getParentStructureIdx();
    }

    public boolean callOnlyOnTrackHeads() {
        return false;
    }

    public List<MeasurementKey> getMeasurementKeys() {
        ArrayList<MeasurementKey> res=  new ArrayList<>(features.getChildCount());
        for (PluginParameter<ObjectFeature> ofp : features.getActivatedChildren()) res.add(new MeasurementKeyObject(((TextParameter)ofp.getAdditionalParameters().get(0)).getValue(), structure.getSelectedIndex()));
        return res;
    }
    @Override
    public void performMeasurement(StructureObject object) {
        int structureIdx = structure.getSelectedIndex();
        //logger.debug("performing features on object: {} (children: {})", object, object.getChildren(structureIdx).size());
        ArrayList<ObjectFeatureCore> cores = new ArrayList<>();
        for (PluginParameter<ObjectFeature> ofp : features.getActivatedChildren()) {
            ObjectFeature f = ofp.instanciatePlugin();
            if (f!=null) {
                f.setUp(object, structureIdx, object.getObjectPopulation(structureIdx));
                if (f instanceof ObjectFeatureWithCore) ((ObjectFeatureWithCore)f).setUpOrAddCore(cores, preFilters);
                for (StructureObject o : object.getChildren(structureIdx)) {
                    double m = f.performMeasurement(o.getObject()); 
                    o.getMeasurements().setValue(((TextParameter)ofp.getAdditionalParameters().get(0)).getValue(), m);
                }
            }
        }
    }
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
}
