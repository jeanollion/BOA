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
import boa.image.ImageMask;
import boa.image.ImageProperties;
import boa.plugins.MultiThreaded;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import boa.plugins.PostFilter;
import boa.plugins.PreFilter;
import boa.plugins.TrackPreFilter;
import boa.utils.MultipleException;
import boa.utils.Pair;
import boa.utils.ThreadRunner;
import boa.utils.ThreadRunner.ThreadAction;
import boa.utils.Utils;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;

/**
 *
 * @author jollion
 */
public class PreFilterSequence extends PluginParameterList<PreFilter> {

    public PreFilterSequence(String name) {
        super(name, "Pre-Filter", PreFilter.class);
    }
    
    public Image filter(Image input, ImageMask mask) {
        ImageProperties prop = input.getProperties();
        for (PreFilter p : get()) {
            input = p.runPreFilter(input, mask);
            //logger.debug("prefilter: {}", p.getClass().getSimpleName());
        }
        input.setCalibration(prop);
        if (input.sameSize(prop)) input.resetOffset().addOffset(prop);
        return input;
    }
    @Override public PreFilterSequence removeAll() {
        this.removeAllElements();
        return this;
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
