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
package plugins.plugins.segmenters;

import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import configuration.parameters.SimpleListParameter;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectProcessing;
import image.Image;
import plugins.PostFilter;
import plugins.PreFilter;
import plugins.Segmenter;

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
    
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        if (preFilters.getChildCount()!=0) {
            for (PluginParameter<PreFilter> p : preFilters.getActivatedChildren() ) {
                PreFilter pre = p.instanciatePlugin();
                if (pre!=null) {
                    input = pre.runPreFilter(input, parent);
                }
            }
        }
        Segmenter s = segmenter.instanciatePlugin();
        ObjectPopulation pop = s.runSegmenter(input, structureIdx, parent);
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
