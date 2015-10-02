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

import configuration.parameters.ui.ChoiceParameterUI;
import static configuration.parameters.ui.ChoiceParameterUI.NO_SELECTION;
import configuration.parameters.ui.ParameterUI;
import core.Core;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.logging.Level;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import de.caluga.morphium.annotations.Embedded;
import de.caluga.morphium.annotations.Transient;
import de.caluga.morphium.annotations.lifecycle.Lifecycle;
import de.caluga.morphium.annotations.lifecycle.PostLoad;
import java.util.Arrays;
import java.util.logging.Logger;
import plugins.Plugin;
import plugins.PluginFactory;

/**
 *
 * @author jollion
 * @param <T> type of plugin
 */
public class PluginParameter<T extends Plugin> extends SimpleContainerParameter implements Deactivatable, ChoosableParameter {
    
    @Transient private static HashMap<Class<? extends Plugin>, ArrayList<String>> pluginNames=new HashMap<Class<? extends Plugin>, ArrayList<String>>();
    protected ArrayList<Parameter> pluginParameters;
    protected String pluginName=NO_SELECTION;
    @Transient private Class<T> pluginType;
    protected String pluginTypeName;
    protected boolean allowNoSelection;
    protected boolean activated=true;
    
    public PluginParameter(String name, Class<T> pluginType, boolean allowNoSelection) {
        super(name);
        this.pluginType=pluginType;
        this.pluginTypeName=pluginType.getName();
        this.allowNoSelection=allowNoSelection;
        super.initChildren();
    }
    
    public PluginParameter(String name, Class<T> pluginType, String defautlMethod, boolean allowNoSelection) {
        this(name, pluginType, allowNoSelection);
        this.pluginName=defautlMethod; // do not call setPlugin Method because plugins are no initiated at startup
    }
    
    public PluginParameter(String name, Class<T> pluginType, T pluginInstance, boolean allowNoSelection) {
        this(name, pluginType, allowNoSelection);
        setPlugin(pluginInstance);
    }
    
    public void setPlugin(T pluginInstance) {
        this.pluginParameters=new ArrayList<Parameter>(Arrays.asList(pluginInstance.getParameters()));
        initChildList();
        this.pluginName=pluginInstance.getClass().getSimpleName();
    }
    
    @Override
    protected void initChildList() {
        if (pluginParameters!=null) super.initChildren(pluginParameters);
        else super.initChildren();
    }
    
    
    public String getPluginName() {
        return pluginName;
    }
    
    public boolean isOnePluginSet() {
        if (pluginParameters==null && !NO_SELECTION.equals(pluginName)) setPlugin(pluginName); // case of constructor with default method  
        return NO_SELECTION.equals(pluginName) || pluginParameters!=null;
        //return (pluginName!=null && !NOPLUGIN.equals(pluginName));
    }
    
    public void setPlugin(String pluginName) {
        //System.out.println(toString()+ ": set plugin: "+pluginName+ " currentStatus: pluginSet?"+pluginSet+" plugin name: "+pluginName);
        if (NO_SELECTION.equals(pluginName)) {
            this.pluginParameters=null;
            this.pluginName=NO_SELECTION;
            super.initChildren();
        } else if (pluginParameters==null || !pluginName.equals(this.pluginName)) {
            T instance = PluginFactory.getPlugin(getPluginType(), pluginName);
            if (instance==null) {
                Parameter.logger.error("Couldn't find plugin: {}", pluginName);
                this.pluginName=NO_SELECTION;
                this.pluginParameters=null;
                return;
            }
            setPlugin(instance);
        }
    }
    
    public T getPlugin() {
        if (!isOnePluginSet()) return null;
        T instance = PluginFactory.getPlugin(getPluginType(), pluginName);
        //if (Parameter.logger.isTraceEnabled()) Parameter.logger.trace("instanciating plugin: type {}, name {} instance==null? {} current parameters {}", pluginType, pluginName, instance==null, pluginParameters.size());
        if (instance==null) return null;
        Parameter[] params = instance.getParameters();
        if (params.length==this.pluginParameters.size()) {
            //Parameter.logger.debug("Parametrizing plugin: {}", pluginName);
            for (int i = 0; i<params.length; i++) {
                //if (Parameter.logger.isTraceEnabled()) Parameter.logger.trace("before set content from: reference: {} target: {}, children: {}", pluginParameters.get(i), params[i], children.get(i));
                params[i].setContentFrom(pluginParameters.get(i));
                params[i].setParent(this);
                //if (Parameter.logger.isTraceEnabled()) Parameter.logger.trace("set content from: reference: {} target: {}, children: {}", pluginParameters.get(i), params[i], children.get(i));
            }
        } else {
            Parameter.logger.error("Couldn't parametrize plugin: {}", pluginName);
        }
        
        return instance;
    }
    
    @Override
    public void setContentFrom(Parameter other) {
        if (other instanceof PluginParameter && ((PluginParameter)other).getPluginType().equals(getPluginType())) {
            PluginParameter otherPP = (PluginParameter) other;
            this.activated=otherPP.activated;
            this.allowNoSelection=otherPP.allowNoSelection;
            
            if (otherPP.pluginName != null && otherPP.pluginName.equals(this.pluginName) && pluginParameters!=null) {
                ParameterUtils.setContent(pluginParameters, otherPP.pluginParameters);
            } else {
                this.pluginName = otherPP.pluginName;
                if (otherPP.pluginParameters != null) {
                    this.pluginParameters = new ArrayList<Parameter>(otherPP.pluginParameters.size());
                    ArrayList<Parameter> pp= otherPP.pluginParameters;
                    for (Parameter p : pp) pluginParameters.add(p.duplicate());
                    initChildList();
                } else {
                    this.pluginParameters = null;
                }
            }
        } else throw new IllegalArgumentException("wrong parameter type");
    }

    @Override
    public PluginParameter<T> duplicate() {
        PluginParameter res = new PluginParameter(name, getPluginType(), allowNoSelection);
        res.setContentFrom(this);
        return res;
    }
    
    private static synchronized ArrayList<String> getPluginNames(Class<? extends Plugin> clazz) {
        ArrayList<String> res = pluginNames.get(clazz);
        if (res==null) {
            res=PluginFactory.getPluginNames(clazz);
            pluginNames.put(clazz, res);
            System.out.println("put :"+res.size()+ " plugins of type:"+clazz);
        }
        return res;
    }
    
    @Override
    public ChoiceParameterUI getUI(){
        return new ChoiceParameterUI(this);
    }
    
    @Override
    public String toString() {
        String res = name+ ": "+this.getPluginName();
        if (isActivated()) return res;
        else return "<HTML><S>"+res+"<HTML></S>";
    }
    
    // deactivatable interface
    public boolean isActivated() {
        return activated;
    }

    public void setActivated(boolean activated) {
        this.activated=activated;
    }
    
    // choosable parameter interface
    public void setSelectedItem(String item) {
        this.setPlugin(item);
    }
    
    public ArrayList<String> getPluginNames() {
        return getPluginNames(getPluginType());
    }

    @Override
    public String[] getChoiceList() {
        ArrayList<String> res = getPluginNames(getPluginType());
        return res.toArray(new String[res.size()]);
    }

    public int getSelectedIndex() {
        String[] choices = getChoiceList();
        for (int i = 0; i<choices.length; ++i) {
            if (choices[i].equals(pluginName)) return i;
        }
        return -1;
    }

    public boolean isAllowNoSelection() {
        return allowNoSelection;
    }
    
    protected Class<T> getPluginType() {
        if (pluginType==null) {
            try {
                pluginType = (Class<T>) Class.forName(pluginTypeName);
            } catch (ClassNotFoundException ex) {
                logger.error("error init pluginparameter: {}, plugin: {} not found", name, pluginTypeName);
            }
        }
        return pluginType;
    }
    
}
