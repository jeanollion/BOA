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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;
import de.caluga.morphium.annotations.Transient;

/**
 *
 * @author jollion
 */
public class ConditionalParameter extends SimpleContainerParameter {
    ActionableParameter action;
    HashMap<Object, Parameter[]> parameters;
    Parameter[] defaultParameters;
    Object currentValue;
    
    public ConditionalParameter(ActionableParameter action) {
        this(action, new HashMap<Object, Parameter[]>(), null);
    }
    
    public ConditionalParameter(ActionableParameter action, HashMap<Object, Parameter[]> parameters, Parameter[] defaultParameters) {
        super(action.getName());
        this.action=action;
        this.parameters=parameters;
        this.defaultParameters=defaultParameters;
        action.setConditionalParameter(this);
        setActionValue(action.getValue());
    }
    
    public void setAction(Object actionValue, Parameter[] parameters) {
        this.parameters.put(actionValue, parameters);
        if (actionValue.equals(action.getValue())) setActionValue(action.getValue());
    }
    public void setDefaultParameters(Parameter[] defaultParameters) {
        this.defaultParameters=defaultParameters;
    } 

    @Override
    public void setContentFrom(Parameter other) {
        if (other instanceof ConditionalParameter) {
            ConditionalParameter otherC = (ConditionalParameter)other;
            action=(ActionableParameter)otherC.action.duplicate();
            action.setConditionalParameter(this);
            parameters=new HashMap<Object, Parameter[]>(otherC.parameters.size());
            for (Entry<Object, Parameter[]> e : otherC.parameters.entrySet()) setAction(e.getKey(), ParameterUtils.duplicateArray(e.getValue()));
            defaultParameters = ParameterUtils.duplicateArray(otherC.defaultParameters);
        } else throw new IllegalArgumentException("wrong parameter type");
    }
    
    public ActionableParameter getActionableParameter() {return action;}
    
    public void setActionValue(Object actionValue) {
        if (actionValue==null) return;
        currentValue = actionValue;
        if (!action.getValue().equals(actionValue)) this.action.setValue(actionValue); // avoid loop
        initChildList();
    }
    
    public Parameter[] getCurrentParameters() {
        if (parameters.containsKey(currentValue)) return parameters.get(currentValue);
        else return defaultParameters;
    }
    
    @Override
    public boolean sameContent(Parameter other) {
        if (other instanceof ConditionalParameter) {
            return ((ConditionalParameter)other).getActionableParameter().sameContent(this) && super.sameContent(other);
        } else return false;
    }
    
    @Override
    public String toString() {
        return action.toString();
    }
    
    @Override
    public ParameterUI getUI() {
        return action.getUI();
    }
    
    @Override
    protected void initChildList() {
        if (!parameters.containsKey(currentValue)) super.initChildren();
        else super.initChildren(getCurrentParameters());
    }
    @Override public ConditionalParameter duplicate() {
        ConditionalParameter res = new ConditionalParameter(action); // action will be duplicated in setContent method
        res.setContentFrom(this);
        return res;
    }
}
