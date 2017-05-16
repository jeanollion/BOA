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
import dataStructure.configuration.Structure;
import java.util.ArrayList;
import java.util.HashMap;
import de.caluga.morphium.annotations.Transient;
import java.util.Arrays;
import java.util.List;
import plugins.ParameterSetup;
import plugins.Plugin;
import plugins.PluginFactory;
import plugins.plugins.trackers.MicrochannelProcessorPhase;
import utils.Utils;

/**
 *
 * @author jollion
 * @param <T> type of plugin
 */
public class PluginParameter<T extends Plugin> extends SimpleContainerParameter implements Deactivatable, ChoosableParameter {
    
    @Transient private static HashMap<Class<? extends Plugin>, ArrayList<String>> pluginNames=new HashMap<Class<? extends Plugin>, ArrayList<String>>();
    protected List<Parameter> pluginParameters;
    protected String pluginName=NO_SELECTION;
    @Transient private Class<T> pluginType;
    protected String pluginTypeName;
    @Transient protected boolean allowNoSelection;
    protected boolean activated=true;
    protected List<Parameter> additionalParameters;
    
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
    
    public PluginParameter<T> setAdditionalParameters(List<Parameter> additionalParameters) {
        if (additionalParameters.isEmpty()) return this;
        this.additionalParameters=additionalParameters;
        initChildList();
        return this;
    }
    
    public PluginParameter<T> setAdditionalParameters(Parameter... additionalParameters) {
        if (additionalParameters.length==0) return this;
        return setAdditionalParameters(new ArrayList<Parameter>(Arrays.asList(additionalParameters)));
    }
    
    public List<Parameter> getAdditionalParameters() {
        return additionalParameters;
    }
    
    public List<Parameter> getParameters() {
        return this.pluginParameters;
    }
    
    public PluginParameter<T> setPlugin(T pluginInstance) {
        if (pluginInstance==null) setPlugin(NO_SELECTION);
        else {
            this.pluginParameters=new ArrayList<>(Arrays.asList(pluginInstance.getParameters()));
            initChildList();
            this.pluginName=pluginInstance.getClass().getSimpleName();
        }
        return this;
    }
    
    @Override
    protected void initChildList() {
        if (pluginParameters!=null && additionalParameters!=null) {
            ArrayList<Parameter> al = new ArrayList<Parameter>(pluginParameters);
            al.addAll(additionalParameters);
            super.initChildren(al); 
        } else if (pluginParameters!=null) super.initChildren(pluginParameters);
        else if (additionalParameters!=null) super.initChildren(additionalParameters);
        else super.initChildren();
    }
    
    
    public String getPluginName() {
        return pluginName;
    }
    
    public boolean isOnePluginSet() {
        if (pluginParameters==null && !NO_SELECTION.equals(pluginName)) setPlugin(pluginName); // case of constructor with default method  
        return !NO_SELECTION.equals(pluginName) || pluginParameters!=null;
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
    
    public T instanciatePlugin() {
        if (!isOnePluginSet()) return null;
        T instance = PluginFactory.getPlugin(getPluginType(), pluginName);
        //Parameter.logger.debug("instanciating plugin: type {}, name {} instance==null? {} current parameters {}", pluginType, pluginName, instance==null, pluginParameters.size());
        if (instance==null) return null;
        Parameter[] params = instance.getParameters();
        ParameterUtils.setContent(Arrays.asList(params), pluginParameters);
        for (Parameter p : params) p.setParent(this);
        return instance;
    }
    
    @Override
    public void setContentFrom(Parameter other) {
        if (other instanceof PluginParameter && ((PluginParameter)other).getPluginType().equals(getPluginType())) {
            PluginParameter otherPP = (PluginParameter) other;
            //logger.debug("set content PP: type: {} current: {} other: {}",this.pluginTypeName, this.pluginName, otherPP.pluginName);
            this.activated=otherPP.activated;
            //this.allowNoSelection=otherPP.allowNoSelection;
            boolean toInit = false;
            if (otherPP.additionalParameters!=null) {
                if (!ParameterUtils.setContent(additionalParameters, otherPP.additionalParameters)) {
                    //logger.warn("PP ({}) additional parameters for: {} could not be loaded (cur:{}/src:{})",pluginTypeName, name, Utils.toStringList(additionalParameters), Utils.toStringList(otherPP.additionalParameters));
                    //additionalParameters=ParameterUtils.duplicateList(otherPP.additionalParameters); // 
                    toInit=true;
                }
            } else this.additionalParameters=null;
            
            boolean setPlug = false;
            if (otherPP.pluginName != null && otherPP.pluginName.equals(this.pluginName) && pluginParameters!=null) {
                if (!ParameterUtils.setContent(pluginParameters, otherPP.pluginParameters)) {
                    setPlug=true;
                }
            } else setPlug=true;
            if (setPlug) {
                toInit = false;
                this.setPlugin(otherPP.pluginName);
                if (isOnePluginSet() && !ParameterUtils.setContent(pluginParameters, otherPP.pluginParameters)) {
                    logger.warn("pluginParameter ({}): parameters for: {}({}) could not be loaded (current: {}/source:{}) (c:{}/s:{})", pluginTypeName, name, otherPP.pluginName, pluginParameters!=null?pluginParameters.size():null, otherPP.pluginParameters!=null?otherPP.pluginParameters.size():null, Utils.toStringList(pluginParameters), Utils.toStringList(otherPP.pluginParameters));
                }
            }
            if (toInit) initChildList();
            this.setListeners(otherPP.listeners);
        } else throw new IllegalArgumentException("wrong parameter type");
    }

    @Override
    public PluginParameter<T> duplicate() {
        PluginParameter res = new PluginParameter(name, getPluginType(), allowNoSelection);
        if (additionalParameters!=null) res.setAdditionalParameters(ParameterUtils.duplicateList(additionalParameters));
        res.setContentFrom(this);
        return res;
    }
    
    private static synchronized ArrayList<String> getPluginNames(Class<? extends Plugin> clazz) {
        ArrayList<String> res = pluginNames.get(clazz);
        if (res==null) {
            res=PluginFactory.getPluginNames(clazz);
            pluginNames.put(clazz, res);
        }
        return res;
    }
    
    @Override
    public ChoiceParameterUI getUI(){
        ChoiceParameterUI ui =  new ChoiceParameterUI(this, false, "Plugins");
        if (this.isOnePluginSet()) {
            // get structureIdx
            Structure s = ParameterUtils.getFirstParameterFromParents(Structure.class, this, false);
            if (s!=null) {
                Plugin pl = this.instanciatePlugin();
                if (pl instanceof ParameterSetup) {
                    ui.addActions(ParameterUtils.getTestMenu("Test Parameters", (ParameterSetup)pl, this, pl.getParameters(), s.getIndex()), true);
                }
            }
        }
        return ui;
    }
    
    @Override
    public String toString() {
        String res = ((name!=null && name.length()>0) ? name+ ": " : "")+this.getPluginName();
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
