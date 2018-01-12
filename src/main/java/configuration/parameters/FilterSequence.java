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
package configuration.parameters;

import dataStructure.containers.InputImages;
import dataStructure.objects.RegionPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectPreProcessing;
import image.Image;
import image.ImageProperties;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import plugins.Filter;
import plugins.PostFilter;
import plugins.PreFilter;
import plugins.Transformation;

/**
 *
 * @author jollion
 */
public class FilterSequence extends PluginParameterList<Filter> {

    public FilterSequence(String name) {
        super(name, "Transformation", Filter.class);
    }
    
    public Image filter(Image input)  throws Exception {
        ImageProperties prop = input.getProperties();
        for (Filter t : get()) {
            input = t.applyTransformation(0, 0, input);
        }
        input.setCalibration(prop);
        if (input.sameSize(prop)) input.resetOffset().addOffset(prop);
        return input;
    }
    @Override public FilterSequence add(Filter... instances) {
        super.add(instances);
        return this;
    }
    
    @Override public FilterSequence add(Collection<Filter> instances) {
        super.add(instances);
        return this;
    }
}
