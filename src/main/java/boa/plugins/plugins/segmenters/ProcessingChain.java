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
package boa.plugins.plugins.segmenters;

import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.SimpleListParameter;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectProcessing;
import boa.image.Image;
import boa.plugins.PostFilter;
import boa.plugins.PreFilter;
import boa.plugins.Segmenter;

/**
 *
 * @author jollion
 */
public class ProcessingChain implements Segmenter {
    
    protected SimpleListParameter<PluginParameter<PreFilter>> preFilters = new SimpleListParameter<PluginParameter<PreFilter>>("Pre-Filters", new PluginParameter<PreFilter>("Pre-Filter", PreFilter.class, false));
    protected PluginParameter<Segmenter> segmenter = new PluginParameter<Segmenter>("Segmentation algorithm", Segmenter.class, false);
    protected SimpleListParameter<PluginParameter<PostFilter>> postFilters = new SimpleListParameter<PluginParameter<PostFilter>>("Post-Filters", new PluginParameter<PostFilter>("Post-Filter", PostFilter.class, false));
    protected Parameter[] parameters = new Parameter[]{preFilters, segmenter, postFilters};
    
    public ProcessingChain(){}
    
    public ProcessingChain(Segmenter segmenter){
        this.segmenter.setPlugin(segmenter);
    }
    
    public ProcessingChain addPrefilters(PreFilter... preFilters) {
        for (PreFilter p : preFilters) this.preFilters.insert(new PluginParameter<PreFilter>("PreFilter", PreFilter.class, p, false));
        return this;
    }
    
    public ProcessingChain addPostfilters(PostFilter... postFilters) {
        for (PostFilter p : postFilters) this.postFilters.insert(new PluginParameter<PostFilter>("PostFilter", PostFilter.class, p, false));
        return this;
    }
    
    public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        if (preFilters.getChildCount()!=0) {
            for (PluginParameter<PreFilter> p : preFilters.getActivatedChildren() ) {
                PreFilter pre = p.instanciatePlugin();
                if (pre!=null) {
                    input = pre.runPreFilter(input, parent.getMask());
                }
            }
        }
        Segmenter s = segmenter.instanciatePlugin();
        RegionPopulation pop = s.runSegmenter(input, structureIdx, parent);
        if (postFilters.getChildCount()!=0) {
            for (PluginParameter<PostFilter> p : postFilters.getActivatedChildren() ) {
                PostFilter pre = p.instanciatePlugin();
                if (pre!=null) {
                    pop = pre.runPostFilter((StructureObject)parent, structureIdx, pop);
                }
            }
        }
        return pop;
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
}
