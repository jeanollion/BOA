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
import plugins.Registration;
import plugins.Transformation;
import plugins.Transformation.SelectionMode;
import plugins.TransformationTimeIndependent;

/**
 *
 * @author jollion
 */
public class RegistrationPluginParameter<T extends Registration> extends PluginParameter<T> {
    Object[][] configurationDataT;
    ChannelImageParameter inputChannel = new ChannelImageParameter("Configuration Channel", -1);
    
    public RegistrationPluginParameter(String name, Class<T> pluginType, boolean allowNoSelection) {
        super(name, pluginType, allowNoSelection);
    }
    
    public RegistrationPluginParameter(String name, Class<T> pluginType, boolean allowNoSelection, String defaultMethod) {
        super(name, pluginType, allowNoSelection, defaultMethod);
    }
    
    public RegistrationPluginParameter(String name, boolean allowNoSelection, Class<T> pluginType, T pluginInstance) {
        super(name, allowNoSelection, pluginType, pluginInstance);
    }
    
    @Override 
    public void setPlugin(T pluginInstance) {
        super.setPlugin(pluginInstance);
        configurationData = duplicateConfigurationDataArray(pluginInstance.getConfigurationData());
    }
    
    public int getIntputChannel() {
        return inputChannel.getSelectedIndex();
    }
    
    @Override
    protected void initChildList() {
        ArrayList<Parameter> p = new ArrayList<Parameter>();
        p.add(inputChannel);
        if (pluginParameters!=null) p.addAll(Arrays.asList(pluginParameters));
        super.initChildren(p.toArray(new Parameter[p.size()]));
    }
    
    @Override
    public T getPlugin() {
        T instance = super.getPlugin();
        Object[] target = instance.getConfigurationData();
        for (int i = 0; i<target.length; ++i) target[i] = duplicateConfigurationData(configurationData[i]);
        return instance;
    }
    
    @Override
    public void setContentFrom(Parameter other) {
        super.setContentFrom(other);
        if (other instanceof RegistrationPluginParameter && ((RegistrationPluginParameter)other).pluginType.equals(pluginType)) {
            RegistrationPluginParameter otherPP = (RegistrationPluginParameter) other;
            this.configurationData = new 
            this.configurationData=duplicateConfigurationDataArray(otherPP.configurationData);
        } else throw new IllegalArgumentException("wrong parameter type");
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
