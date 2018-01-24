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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import boa.plugins.Plugin;
import boa.plugins.PreFilter;

/**
 *
 * @author jollion
 */
public class PluginParameterList<T extends Plugin> extends SimpleListParameter<PluginParameter<T>> {
    String childLabel;
    public PluginParameterList(String name, String childLabel, Class<T> childClass) {
        super(name, -1, new PluginParameter<T>(childLabel, childClass, false));
    }
    private void add(T instance) {
        super.insert(super.createChildInstance(childLabel).setPlugin(instance));
    } 
    public PluginParameterList<T> add(T... instances) {
        for (T t : instances) add(t);
        return this;
    }
    
    public PluginParameterList<T> add(Collection<T> instances) {
        for (T t : instances) add(t);
        return this;
    }
    
    public List<T> get() {
        List<T> res = new ArrayList<>(this.getChildCount());
        for (PluginParameter<T> pp : this.getActivatedChildren()) {
            T p = pp.instanciatePlugin();
            if (p!=null) res.add(p);
        }
        return res;
    }
    public boolean isEmpty() {
        for (PluginParameter<T> pp : this.children) if (pp.isActivated()) return false;
        return true;
    }
}
