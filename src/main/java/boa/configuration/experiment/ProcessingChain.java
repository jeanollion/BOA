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
package boa.configuration.experiment;

import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.ScaleXYZParameter;
import boa.configuration.parameters.SimpleContainerParameter;
import boa.configuration.parameters.SimpleListParameter;
import java.util.ArrayList;
import java.util.Arrays;
import org.json.simple.JSONObject;
import boa.plugins.PostFilter;
import boa.plugins.PreFilter;
import boa.plugins.Segmenter;

/**
 *
 * @author jollion
 */
public class ProcessingChain extends SimpleContainerParameter {
    
    protected SimpleListParameter<PluginParameter<PreFilter>> preFilters = new SimpleListParameter<>("Pre-Filters", new PluginParameter<PreFilter>("Pre-Filter", PreFilter.class, false));
    protected PluginParameter<Segmenter> segmenter = new PluginParameter<>("Segmentation algorithm", Segmenter.class, false);
    protected SimpleListParameter<PluginParameter<PostFilter>> postFilters = new SimpleListParameter<>("Post-Filters", new PluginParameter<PostFilter>("Post-Filter", PostFilter.class, false));
    
    @Override
    public JSONObject toJSONEntry() {
        JSONObject res= new JSONObject();
        res.put("preFilters", preFilters.toJSONEntry());
        res.put("segmenter", segmenter.toJSONEntry());
        res.put("postFilters", postFilters.toJSONEntry());
        return res;
    }

    @Override
    public void initFromJSONEntry(Object jsonEntry) {
        JSONObject jsonO = (JSONObject)jsonEntry;
        preFilters.initFromJSONEntry(jsonO.get("preFilters"));
        segmenter.initFromJSONEntry(jsonO.get("segmenter"));
        postFilters.initFromJSONEntry(jsonO.get("postFilters"));
    }
    
    public ProcessingChain(String name) {
        super(name);
        initChildList();
    }
    
    public ArrayList<PreFilter> getPrefilters() {
        ArrayList<PreFilter> res = new ArrayList<PreFilter> (preFilters.getChildCount());
        for (PluginParameter p : preFilters.getChildren()) if (((PluginParameter)p).isOnePluginSet() && ((PluginParameter)p).isActivated()) res.add((PreFilter)((PluginParameter)p).instanciatePlugin());
        return res;
        //return preFilters.getChildren().toArray(new PreFilter[preFilters.getChildCount()]);
        //return Arrays.copyOf(preFilters.getChildren().toArray(new Parameter[preFilters.getChildCount()]), preFilters.getChildCount(), PreFilter[].class);
    }
    
    public ArrayList<PostFilter> getPostfilters() {
        ArrayList<PostFilter> res = new ArrayList<PostFilter> (postFilters.getChildCount());
        for (Parameter p : postFilters.getChildren()) if (((PluginParameter)p).isOnePluginSet() && ((PluginParameter)p).isActivated()) res.add((PostFilter)((PluginParameter)p).instanciatePlugin());
        return res;
    }
    
    
    public Segmenter getSegmenter() {
        if (segmenter.isActivated()) return segmenter.instanciatePlugin();
        else return null;
    }
    
    public void setSegmenter (Segmenter segmenter) {
        this.segmenter.setPlugin(segmenter);
    }
    
    public void addPreFilters(PreFilter... preFilter) {
        for (PreFilter p : preFilter) this.preFilters.insert(new PluginParameter<PreFilter>("Pre-Filter", PreFilter.class, p, false));
    }
    
    public void addPostFilters(PostFilter... postFilter) {
        for (PostFilter p : postFilter) this.postFilters.insert(new PluginParameter<PostFilter>("Post-Filter", PostFilter.class, p, false));
    }
    
    @Override
    protected void initChildList() {
        super.initChildren(preFilters, segmenter, postFilters);
    }

    
}
