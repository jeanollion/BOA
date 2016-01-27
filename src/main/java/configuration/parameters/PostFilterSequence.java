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

import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import image.ImageProperties;
import plugins.PostFilter;

/**
 *
 * @author jollion
 */
public class PostFilterSequence extends SimpleListParameter<PluginParameter<PostFilter>> {

    public PostFilterSequence(String name) {
        super(name, -1, new PluginParameter<PostFilter>("Post-Filter", PostFilter.class, false));
    }
    
    public PostFilterSequence addPostFilters(PostFilter... postFilters) {
        for (PostFilter f : postFilters) super.createChildInstance("Post-Filter").setPlugin(f);
        return this;
    }
    
    public ObjectPopulation filter(ObjectPopulation objectPopulation, int structureIdx, StructureObject structureObject) {
        ImageProperties initialProperites = objectPopulation.getImageProperties();
        ObjectPopulation currentObjectPopulation=objectPopulation;
        for (PluginParameter<PostFilter> pp : this.getActivatedChildren()) {
            PostFilter p = pp.instanciatePlugin();
            if (p!=null) {
                currentObjectPopulation = p.runPostFilter(structureObject, structureIdx, objectPopulation);
                currentObjectPopulation.setProperties(initialProperites, true);
            }
        }
        return currentObjectPopulation;
    }
}
