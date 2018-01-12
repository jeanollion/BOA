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

import dataStructure.objects.RegionPopulation;
import dataStructure.objects.StructureObject;
import image.ImageProperties;
import java.util.Collection;
import plugins.PostFilter;

/**
 *
 * @author jollion
 */
public class PostFilterSequence extends PluginParameterList<PostFilter> {
    Boolean configured = false;
    
    public PostFilterSequence(String name) {
        super(name, "Post-Filter", PostFilter.class);
    }
    
    public RegionPopulation filter(RegionPopulation objectPopulation, int structureIdx, StructureObject parent) {
        if (objectPopulation == null) return null;
        if (!configured) {
            synchronized(configured) {
                if (!configured) ParameterUtils.configureStructureParameters(structureIdx, this);
            }
        }
        ImageProperties prop = objectPopulation.getImageProperties().getProperties();
        for (PostFilter p : this.get()) objectPopulation = p.runPostFilter(parent, structureIdx, objectPopulation);
        objectPopulation.setProperties(prop, true);
        return objectPopulation;
    }
    @Override public PostFilterSequence add(PostFilter... instances) {
        super.add(instances);
        return this;
    }
    
    @Override public PostFilterSequence add(Collection<PostFilter> instances) {
        super.add(instances);
        return this;
    }
}
