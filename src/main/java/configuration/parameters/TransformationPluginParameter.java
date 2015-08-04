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
    Object[] configurationData;
    ChannelImageParameter inputChannel = new ChannelImageParameter("Configuration Channel", -1);
    ChannelImageParameter outputChannel=null;
    //Parameter inputTimePoints;
    
    public TransformationPluginParameter(String name, Class<T> pluginType, boolean allowNoSelection) {
        super(name, pluginType, allowNoSelection);
    }
    
    public TransformationPluginParameter(String name, Class<T> pluginType, boolean allowNoSelection, String defaultMethod) {
        super(name, pluginType, allowNoSelection, defaultMethod);
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
        configurationData = duplicateConfigurationDataArray(pluginInstance.getConfigurationData());
    }
    
    public void setConfigurationData(Object[] configurationData) {
        this.configurationData = duplicateConfigurationDataArray(configurationData);
    }
    
    public void setOutputChannel(int... channelIdx) { // null -> all selected
        outputChannel.setSelectedIndicies(channelIdx);
    }
    
    public void setInputChannel(int channelIdx) {
        this.inputChannel.setSelectedIndex(channelIdx);
    }
    
    public int[] getOutputChannels() { // if null -> all selected
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
        ArrayList<Parameter> p = new ArrayList<Parameter>();
        p.add(inputChannel);
        if (outputChannel!=null) p.add(outputChannel);
        if (pluginParameters!=null) p.addAll(Arrays.asList(pluginParameters));
        //System.out.println("init child list! for: "+toString()+ " number of pp:"+(pluginParameters==null?0:pluginParameters.length)+" number total:"+p.size());
        super.initChildren(p.toArray(new Parameter[p.size()]));
    }
    
    @Override
    public T getPlugin() {
        T instance = super.getPlugin();
        if (instance!=null) {
            Object[] target = instance.getConfigurationData();
            if (target!=null && configurationData!=null) for (int i = 0; i<target.length; ++i) target[i] = duplicateConfigurationData(configurationData[i]);
        }
        return instance;
    }
    
    @Override
    public void setContentFrom(Parameter other) {
        super.setContentFrom(other);
        if (other instanceof TransformationPluginParameter && ((TransformationPluginParameter)other).pluginType.equals(pluginType)) {
            TransformationPluginParameter otherPP = (TransformationPluginParameter) other;
            this.configurationData=duplicateConfigurationDataArray(otherPP.configurationData);
        } else throw new IllegalArgumentException("wrong parameter type");
    }
    
    @Override
    public TransformationPluginParameter<T> duplicate() {
        TransformationPluginParameter res = new TransformationPluginParameter(name, pluginType, allowNoSelection);
        res.setContentFrom(this);
        return res;
    }
    
    private static Object[] duplicateConfigurationDataArray(Object[] in) {
        if (in!=null) {
            Object[] res  = new Object[in.length];
            for (int i = 0; i<res.length; ++i) res[i] = duplicateConfigurationData(in[i]);
            return res;
        } else return null;
    }
    
    private static Object duplicateConfigurationData(Object in) {
        if (in != null) {
            if (in instanceof Number) {
                if (in instanceof Double || in instanceof Float) {
                    return ((Number) in).doubleValue();
                } else if (in instanceof Long) {
                    return ((Number) in).longValue();
                } else {
                    return ((Number) in).intValue();
                }
            } else if (in instanceof String) {
                return ((String) in); // Strings are immutable
            } else if (in.getClass().isArray()) {
                if (in instanceof Object[]) return duplicateConfigurationDataArray((Object[])in);
                } else if (in instanceof int[]) {
                    int length = ((int[]) in).length;
                    int[] res = new int[length];
                    System.arraycopy(in, 0, res, 0, length);
                    return res;
                } else if (in instanceof double[]) {
                    int length = ((double[]) in).length;
                    double[] res = new double[length];
                    System.arraycopy(in, 0, res, 0, length);
                    return res;
                }
            }
        return null;
    }

}
