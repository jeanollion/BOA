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
package plugins.objectFeature;

import configuration.parameters.ParameterListener;
import configuration.parameters.PluginParameter;
import configuration.parameters.SimpleContainerParameter;
import configuration.parameters.TextParameter;
import plugins.ObjectFeature;

/**
 *
 * @author jollion
 */
public class ObjectFeatureParameter extends SimpleContainerParameter {
    PluginParameter<ObjectFeature> feature = new PluginParameter<ObjectFeature>("Feature", ObjectFeature.class, false);
    TextParameter keyName = new TextParameter("");
    
    @Override
    protected void initChildList() {
        super.initChildren(feature, keyName);
        feature.addListener(new ParameterListener() {
            public void fire() {
                if (feature.isOnePluginSet()) keyName.setValue(feature.instanciatePlugin().getDefaultName());
                else keyName.setValue("");
            }
        });
    }
    public String getKeyName() {
        return keyName.getValue();
    }
    
    public ObjectFeature getFeature() {
        return feature.instanciatePlugin();
    }
}
