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
package configuration.parameters;

import configuration.parameters.ui.ParameterUI;
import configuration.parameters.ui.PluginParameterUI;
import core.Core;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.logging.Level;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.annotations.PostLoad;
import org.mongodb.morphia.annotations.Transient;
import plugins.Plugin;
import plugins.PluginFactory;

/**
 *
 * @author jollion
 * @param <T> class of plugin
 */
@Embedded
public class PluginParameter extends SimpleContainerParameter { //<T extends Plugin> // TODO generic quand supporté par morphia
    @Transient private static HashMap<Class, ArrayList<String>> pluginNames=new HashMap<Class, ArrayList<String>>();
    protected Parameter[] pluginParameters;
    protected String pluginName;
    protected Class pluginType;
    
    public PluginParameter(String name, Class pluginType) {
        super(name);
        this.pluginType=pluginType;
        super.initChildren();
    }
    
    public ArrayList<String> getPluginNames() {
        return getPluginNames(pluginType);
    }
    
    public String getPluginName() {
        return pluginName;
    }
    
    public void setPlugin(String pluginName) {
        if (!pluginName.equals(this.pluginName)) {
            Plugin instance = PluginFactory.getPlugin(pluginType, pluginName);
            if (instance==null) {
                Core.getLogger().log(Level.WARNING, "Couldn't find plugin: {0}", pluginName);
                this.pluginName="no plugin selected";
                this.pluginParameters=null;
                return;
            }
            pluginParameters=instance.getParameters();
            super.initChildren(pluginParameters);
            this.pluginName=pluginName;
        }
    }
    
    public Plugin getPlugin() {
        Plugin instance = PluginFactory.getPlugin(pluginType, pluginName);
        Parameter[] params = instance.getParameters();
        if (params.length==this.pluginParameters.length) {
            for (int i = 0; i<params.length; i++) params[i].setContentFrom(pluginParameters[i]);
        } else {
            Core.getLogger().log(Level.WARNING, "Couldn't parametrize plugin: {0}", pluginName);
        }
        return instance;
    }
    
    public void setContentFrom(Parameter other) {
        if (other instanceof PluginParameter && ((PluginParameter)other).pluginType.equals(pluginType)) {
            PluginParameter otherPP = (PluginParameter) other;
            if (otherPP.pluginName != null && otherPP.pluginName.equals(this.pluginName) && pluginParameters!=null) {
                for (int i = 0; i < pluginParameters.length; i++) {
                    pluginParameters[i] = otherPP.pluginParameters[i].duplicate();
                }
            } else {
                this.pluginName = otherPP.pluginName;
                if (otherPP.pluginParameters != null) {
                    this.pluginParameters = new Parameter[otherPP.pluginParameters.length];
                    for (int i = 0; i < pluginParameters.length; i++) {
                        pluginParameters[i] = otherPP.pluginParameters[i].duplicate();
                    }
                    super.initChildren(pluginParameters);
                } else {
                    this.pluginParameters = null;
                }
            }
        } else throw new IllegalArgumentException("wrong parameter type");
    }

    public PluginParameter duplicate() {
        PluginParameter res = new PluginParameter(name, pluginType);
        res.setContentFrom(this);
        return res;
    }
    
    private static synchronized ArrayList<String> getPluginNames(Class clazz) {
        ArrayList<String> res = pluginNames.get(clazz);
        if (res==null) {
            res=PluginFactory.getPluginNames(clazz);
            pluginNames.put(clazz, res);
            System.out.println("put :"+res.size()+ " plugins of type:"+clazz);
        }
        return res;
    }
    
    @Override
    public PluginParameterUI getUI(){
        return new PluginParameterUI(this);
    }
    
    // morphia
    private PluginParameter(){}
    @PostLoad void postLoad() {super.initChildren(pluginParameters);}

    
}
