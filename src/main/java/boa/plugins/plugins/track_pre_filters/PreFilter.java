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
package boa.plugins.plugins.track_pre_filters;

import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.PreFilterSequence;
import boa.data_structure.StructureObject;
import boa.image.Image;
import boa.plugins.ToolTip;
import boa.plugins.TrackPreFilter;
import static boa.utils.Utils.parallele;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 *
 * @author Jean Ollion
 */
public class PreFilter implements TrackPreFilter, ToolTip {
    
    PluginParameter<boa.plugins.PreFilter> filter = new PluginParameter<>("Filter",boa.plugins.PreFilter.class, false);

    public PreFilter() {}
    public PreFilter(boa.plugins.PreFilter preFilter) {
        this.filter.setPlugin(preFilter);
    }
    public PreFilter setFilter(PluginParameter<boa.plugins.PreFilter> filter) {
        this.filter = filter;
        return this;
    }
    public static PreFilter[] splitPreFilterSequence(PreFilterSequence preFilters) {
        PreFilter[] pfs = new PreFilter[preFilters.getChildCount()];
        for (int i = 0; i<pfs.length; ++i) pfs[i] = new PreFilter().setFilter(preFilters.getChildAt(i));
        return pfs;
    }
    @Override
    public void filter(int structureIdx, TreeMap<StructureObject, Image> preFilteredImages, boolean canModifyImages) {
        Consumer<Map.Entry<StructureObject, Image>> c  = e->e.setValue(filter.instanciatePlugin().runPreFilter(e.getValue(), e.getKey().getMask()));
        parallele(preFilteredImages.entrySet().stream(), true).forEach(c);
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{filter};
    }

    @Override
    public String getToolTipText() {
        return "Performs regular pre-filter at each frame of the track";
    }
    
}
