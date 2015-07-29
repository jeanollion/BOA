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
package dataStructure.configuration;

import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import configuration.parameters.SimpleContainerParameter;
import configuration.parameters.SimpleListParameter;
import java.util.ArrayList;
import java.util.Arrays;
import plugins.PostFilter;
import plugins.PreFilter;
import plugins.Segmenter;

/**
 *
 * @author jollion
 */
public class ProcessingChain extends SimpleContainerParameter {
    
    SimpleListParameter<PluginParameter<PreFilter>> preFilters = new SimpleListParameter<PluginParameter<PreFilter>>("Pre-Filters", new PluginParameter<PreFilter>("Pre-Filter", PreFilter.class, false));
    PluginParameter<Segmenter> segmenter = new PluginParameter<Segmenter>("Segmentation algorithm", Segmenter.class, false);
    SimpleListParameter<PluginParameter<PostFilter>> postFilters = new SimpleListParameter<PluginParameter<PostFilter>>("Post-Filters", new PluginParameter<PostFilter>("Post-Filter", PostFilter.class, false));
    
    public ProcessingChain(String name) {
        super(name);
        initChildList();
    }
    
    public ArrayList<PreFilter> getPrefilters() {
        ArrayList<PreFilter> res = new ArrayList<PreFilter> (preFilters.getChildCount());
        for (PluginParameter p : preFilters.getChildren()) if (((PluginParameter)p).isOnePluginSet() && ((PluginParameter)p).isActivated()) res.add((PreFilter)((PluginParameter)p).getPlugin());
        return res;
        //return preFilters.getChildren().toArray(new PreFilter[preFilters.getChildCount()]);
        //return Arrays.copyOf(preFilters.getChildren().toArray(new Parameter[preFilters.getChildCount()]), preFilters.getChildCount(), PreFilter[].class);
    }
    
    public ArrayList<PostFilter> getPostfilters() {
        ArrayList<PostFilter> res = new ArrayList<PostFilter> (postFilters.getChildCount());
        for (Parameter p : postFilters.getChildren()) if (((PluginParameter)p).isOnePluginSet() && ((PluginParameter)p).isActivated()) res.add((PostFilter)((PluginParameter)p).getPlugin());
        return res;
    }
    
    public Segmenter getSegmenter() {
        if (segmenter.isActivated()) return segmenter.getPlugin();
        else return null;
    }
    
    public void setSegmenter (Segmenter segmenter) {
        this.segmenter.setPlugin(segmenter);
    }
    
    public void addPreFilters(PreFilter... preFilter) {
        for (PreFilter p : preFilter) this.preFilters.insert(new PluginParameter<PreFilter>("Pre-Filter", false, PreFilter.class, p));
    }
    
    public void addPostFilters(PostFilter... postFilter) {
        for (PostFilter p : postFilter) this.postFilters.insert(new PluginParameter<PostFilter>("Post-Filter", false, PostFilter.class, p));
    }
    
    @Override
    protected void initChildList() {
        super.initChildren(preFilters, segmenter, postFilters);
    }

    @Override
    public Parameter duplicate() {
        ProcessingChain pc = new ProcessingChain(name);
        pc.setContentFrom(this);
        return pc;
    }
    
    // morphia
    public ProcessingChain(){super(); initChildList();}
    
}
