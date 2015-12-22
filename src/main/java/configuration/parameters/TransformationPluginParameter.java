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

import dataStructure.configuration.Experiment;
import de.caluga.morphium.annotations.lifecycle.Lifecycle;
import java.util.ArrayList;
import java.util.Arrays;
import plugins.Transformation;
import plugins.Transformation.SelectionMode;
import plugins.TransformationTimeIndependent;

/**
 *
 * @author jollion
 */
public class TransformationPluginParameter<T extends Transformation> extends PluginParameter<T> {
    ArrayList configurationData;
    ChannelImageParameter inputChannel;
    ChannelImageParameter outputChannel;
    //Parameter inputTimePoints;
    
    public TransformationPluginParameter(String name, Class<T> pluginType, boolean allowNoSelection) {
        super(name, pluginType, allowNoSelection);
        outputChannel=null;
        inputChannel = new ChannelImageParameter("Configuration Channel", -1);
    }
    
    public TransformationPluginParameter(String name, Class<T> pluginType, String defaultMethod, boolean allowNoSelection) {
        super(name, pluginType, defaultMethod, allowNoSelection);
        outputChannel=null;
        inputChannel = new ChannelImageParameter("Configuration Channel", -1);
    }
    
    // constructeur désactivé car la methode setPlugin a besoin de l'experience
    /*public TransformationPluginParameter(String name, boolean allowNoSelection, Class<T> pluginType, T pluginInstance) {
        super(name, allowNoSelection, pluginType, pluginInstance);
    }*/
    
    @Override 
    public void setPlugin(T pluginInstance) {
        if (pluginInstance instanceof TransformationTimeIndependent) {  
            SelectionMode oc = ((TransformationTimeIndependent)pluginInstance).getOutputChannelSelectionMode();
            if (SelectionMode.MULTIPLE.equals(oc)) outputChannel = new ChannelImageParameter("Channels on which apply transformation", null);
            else if (SelectionMode.SINGLE.equals(oc)) outputChannel = new ChannelImageParameter("Channels on which apply transformation", -1);
            else outputChannel=null;
        }
        super.setPlugin(pluginInstance);
        configurationData = ParameterUtils.duplicateConfigurationDataArrayList(pluginInstance.getConfigurationData());
    }
    
    public void setConfigurationData(ArrayList configurationData) {
        this.configurationData = ParameterUtils.duplicateConfigurationDataArrayList(configurationData);
    }
    
    public void setOutputChannel(int... channelIdx) { // null -> all selected OR same channel selected
        if (outputChannel!=null) outputChannel.setSelectedIndicies(channelIdx);
    }
    
    public void setInputChannel(int channelIdx) {
        this.inputChannel.setSelectedIndex(channelIdx);
    }
    
    public int[] getOutputChannels() { // if null -> all selected or same as input...
        if (outputChannel==null) return null;
        else return outputChannel.getSelectedItems();
    }
    
    /*public int[] getInputTimePoints() { // if null -> all selected
        if (inputTimePoints==null) return null;
        else if (inputTimePoints instanceof MultipleChoiceParameter) return ((MultipleChoiceParameter)inputTimePoints).getSelectedItems();
        else if (inputTimePoints instanceof ChoiceParameter) return new int[]{((ChoiceParameter)inputTimePoints).getSelectedIndex()};
        else return null;
    }*/
    
    public int getInputChannel() {
        return inputChannel.getSelectedIndex();
    }
    
    @Override
    protected void initChildList() {
        ArrayList<Parameter> p = new ArrayList<Parameter>(2+(pluginParameters!=null?pluginParameters.size():0));
        p.add(inputChannel);
        if (outputChannel!=null) p.add(outputChannel);
        if (pluginParameters!=null) p.addAll(pluginParameters);
        //System.out.println("init child list! for: "+toString()+ " number of pp:"+(pluginParameters==null?0:pluginParameters.length)+" number total:"+p.size());
        super.initChildren(p);
    }
    
    @Override
    public T instanciatePlugin() {
        T instance = super.instanciatePlugin();
        if (instance!=null) {
            ArrayList target = instance.getConfigurationData();
            if (target!=null && configurationData!=null) for (Object o : configurationData) target.add(ParameterUtils.duplicateConfigurationData(o));
            //logger.debug("copied configuration data to transformation: {}: config:{}", instance.getClass().getSimpleName(), instance.getConfigurationData());
        }
        return instance;
    }
    
    @Override
    public void setContentFrom(Parameter other) {
        super.setContentFrom(other);
        if (other instanceof TransformationPluginParameter && ((TransformationPluginParameter)other).getPluginType().equals(getPluginType())) {
            TransformationPluginParameter otherPP = (TransformationPluginParameter) other;
            this.configurationData=ParameterUtils.duplicateConfigurationDataArrayList(otherPP.configurationData);
            if (otherPP.outputChannel==null) this.outputChannel=null;
            else this.outputChannel=otherPP.outputChannel.duplicate();
            inputChannel.setContentFrom(otherPP.inputChannel);
        } else throw new IllegalArgumentException("wrong parameter type");
    }
    
    @Override
    public TransformationPluginParameter<T> duplicate() {
        TransformationPluginParameter res = new TransformationPluginParameter(name, getPluginType(), allowNoSelection);
        res.setContentFrom(this);
        return res;
    }
}
