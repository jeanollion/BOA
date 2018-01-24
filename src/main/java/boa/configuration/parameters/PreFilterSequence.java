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
package boa.configuration.parameters;

import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectPreProcessing;
import boa.image.Image;
import boa.image.ImageProperties;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import boa.plugins.PostFilter;
import boa.plugins.PreFilter;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class PreFilterSequence extends PluginParameterList<PreFilter> {

    public PreFilterSequence(String name) {
        super(name, "Pre-Filter", PreFilter.class);
    }
    
    public Image filter(Image input, StructureObjectPreProcessing parent) {
        ImageProperties prop = input.getProperties();
        for (PreFilter p : get()) {
            input = p.runPreFilter(input, parent);
            //logger.debug("prefilter: {}", p.getClass().getSimpleName());
        }
        input.setCalibration(prop);
        if (input.sameSize(prop)) input.resetOffset().addOffset(prop);
        return input;
    }
    @Override public PreFilterSequence add(PreFilter... instances) {
        super.add(instances);
        return this;
    }
    
    @Override public PreFilterSequence add(Collection<PreFilter> instances) {
        super.add(instances);
        return this;
    }
    public String toStringElements() {
        return Utils.toStringList(children, p -> p.pluginName);
    }
}
