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
import java.util.List;

/**
 *
 * @author jollion
 */
public class ConditionalParameter extends SimpleContainerParameter {
    ActionableParameter action;
    HashMap<Object, List<Parameter>> parameters;
    List<Parameter> defaultParameters;
    Object currentValue;
    
    public ConditionalParameter(ActionableParameter action) {
        this(action, new HashMap<Object, List<Parameter>>(), null);
    }
    
    public ConditionalParameter(ActionableParameter action, HashMap<Object, List<Parameter>> parameters, List<Parameter> defaultParameters) {
        super(action.getName());
        this.action=action;
        this.parameters=parameters;
        this.defaultParameters=defaultParameters;
        action.setConditionalParameter(this);
        setActionValue(action.getValue());
    }
    public ConditionalParameter setAction(Object actionValue, Parameter[] parameters) {
        return setAction(actionValue, parameters, false);
    }
    
    public ConditionalParameter setAction(Object actionValue, Parameter[] parameters, boolean setContentFromAlreadyPresent) {
        List<Parameter> paramToSet = Arrays.asList(parameters);
        if (setContentFromAlreadyPresent) {
            List<Parameter> p = this.parameters.get(actionValue);
            if (p!=null && p.size()==parameters.length) ParameterUtils.setContent(paramToSet, p);
        }
        this.parameters.put(actionValue, paramToSet);
        if (actionValue.equals(action.getValue())) setActionValue(action.getValue());
        //logger.debug("setActionValue: {}, nParams: {}, allActions: {}", actionValue, parameters.length, this.parameters.keySet());
        return this;
    }
    void replaceActionParameter(ActionableParameter action) {
        logger.debug("replace ap: toReplace: {}; old: {}", action.getValue(), this.action.getValue());
        action.setContentFrom(this.action);
        action.setValue(this.action.getValue());
        this.action=action;
        action.setConditionalParameter(this);
        logger.debug("after replace ap: toReplace: {}; old: {}", action.getValue(), this.action.getValue());
    }

    
    public ConditionalParameter setDefaultParameters(Parameter[] defaultParameters) {
        this.defaultParameters=Arrays.asList(defaultParameters);
        initChildList();
        return this;
    } 

    @Override
    public void setContentFrom(Parameter other) {
        if (other instanceof ConditionalParameter) {
            ConditionalParameter otherC = (ConditionalParameter)other;
            action.setContentFrom(otherC.action);
            action.setConditionalParameter(this);
            HashMap<Object, List<Parameter>> oldParam = parameters;
            parameters=new HashMap<Object, List<Parameter>>(otherC.parameters.size());
            for (Entry<Object, List<Parameter>> e : otherC.parameters.entrySet()) {
                List<Parameter> oldArray = oldParam.get(e.getKey());
                if (oldArray!=null && oldArray.size()==e.getValue().size()) {
                    ParameterUtils.setContent(oldArray, e.getValue());
                    parameters.put(e.getKey(), oldArray);
                } else {
                    this.parameters.put(e.getKey(), ParameterUtils.duplicateArray(e.getValue()));
                }
            }
            if (otherC.defaultParameters!=null) {
                if (this.defaultParameters!=null && this.defaultParameters.size()==otherC.defaultParameters.size()) ParameterUtils.setContent(defaultParameters, otherC.defaultParameters);
                else this.defaultParameters = ParameterUtils.duplicateArray(otherC.defaultParameters);
            } else this.defaultParameters=null;
            setActionValue(action.getValue());
        } else throw new IllegalArgumentException("wrong parameter type");
    }
    
    public ActionableParameter getActionableParameter() {return action;}
    
    public void setActionValue(Object actionValue) {
        if (actionValue==null) return;
        currentValue = actionValue;
        if (!action.getValue().equals(actionValue)) this.action.setValue(actionValue); // avoid loop
        initChildList();
        logger.debug("setAction value: {}, children: {}", actionValue, getCurrentParameters()==null ? "null" : getCurrentParameters().size());
    }
    
    public List<Parameter> getParameters(Object actionValue) {
        List<Parameter> p = this.parameters.get(actionValue);
        if (p==null) return defaultParameters;
        else return p;
    }
    
    public List<Parameter> getCurrentParameters() {
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
        super.initChildren(getCurrentParameters());
    }
    @Override public ConditionalParameter duplicate() {
        ConditionalParameter res = new ConditionalParameter(action); // action will be duplicated in setContent method
        res.setContentFrom(this);
        return res;
    }
}
