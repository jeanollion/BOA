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
import de.caluga.morphium.annotations.lifecycle.Lifecycle;
import de.caluga.morphium.annotations.lifecycle.PostLoad;
import java.util.List;

/**
 *
 * @author jollion
 */
@Lifecycle
public class ConditionalParameter extends SimpleContainerParameter {
    ActionableParameter action;
    HashMap<String, List<Parameter>> parameters;
    List<Parameter> defaultParameters;
    String currentValue;
    
    public ConditionalParameter(ActionableParameter action) {
        this(action, new HashMap<String, List<Parameter>>(), null);
    }
    
    public ConditionalParameter(ActionableParameter action, HashMap<String, List<Parameter>> parameters, List<Parameter> defaultParameters) {
        super(action.getName());
        this.action=action;
        this.parameters=parameters;
        this.defaultParameters=defaultParameters;
        action.setConditionalParameter(this);
        setActionValue(action.getValue());
    }
    public ConditionalParameter setActionParameters(String actionValue, Parameter[] parameters) {
        return setActionParameters(actionValue, parameters, false);
    }
    
    public ConditionalParameter setActionParameters(String actionValue, Parameter[] parameters, boolean setContentFromAlreadyPresent) {
        List<Parameter> paramToSet = Arrays.asList(parameters);
        if (setContentFromAlreadyPresent) {
            List<Parameter> p = this.parameters.get(actionValue);
            if (p!=null && p.size()==parameters.length) ParameterUtils.setContent(paramToSet, p);
        }
        this.parameters.put(actionValue, paramToSet);
        if (actionValue.equals(action.getValue())) setActionValue(action.getValue()); // to update parameters
        //logger.debug("setActionValue: {}, class: {}, nParams: {}, allActions: {}", actionValue, actionValue.getClass().getSimpleName(), parameters.length, this.parameters.keySet());
        return this;
    }
    public void replaceActionParameter(ActionableParameter action) {
        action.setContentFrom(this.action);
        action.setValue(this.action.getValue());
        this.action=action;
        action.setConditionalParameter(this);
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
            action.setConditionalParameter(null);
            action.setContentFrom(otherC.action);
            action.setConditionalParameter(this);
            String currentAction = otherC.currentValue;
            List<Parameter> currentParameters = currentAction==null? null : otherC.getParameters(currentAction);
            for (Entry<String, List<Parameter>> e : otherC.parameters.entrySet()) {
                if (e.getKey().equals(currentAction)) continue; // current action at the end, in case that parameters are used 
                ParameterUtils.setContent(parameters.get(e.getKey()), e.getValue());
            }
            if (otherC.defaultParameters!=null && defaultParameters!=null) {
                ParameterUtils.setContent(defaultParameters, otherC.defaultParameters);
            } else this.defaultParameters=null;
            if (currentAction!=null && currentParameters!=null) { // set current action @ the end
                ParameterUtils.setContent(parameters.get(currentAction), currentParameters);
            }
            setActionValue(action.getValue());
        } else throw new IllegalArgumentException("wrong parameter type");
    }
    
    public ActionableParameter getActionableParameter() {return action;}
    
    protected void setActionValue(String actionValue) {
        if (actionValue==null) return;
        currentValue = actionValue;
        if (!action.getValue().equals(actionValue)) this.action.setValue(actionValue); // avoid loop
        initChildList();
        //logger.debug("setActionParameters: {} value: {}, class: {}, children: {}, allActions: {}",this.hashCode(), actionValue, actionValue.getClass().getSimpleName(), getCurrentParameters()==null ? "null" : getCurrentParameters().size(), this.parameters.keySet());
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
        ConditionalParameter res = new ConditionalParameter((ActionableParameter)action.duplicate()); 
        res.setListeners(listeners);
        res.setContentFrom(this);
        return res;
    }
    /*@PostLoad @Override public void postLoad() {
        if (postLoaded) return;
        initChildList();
        if (defaultParameters!=null) for (Parameter p : defaultParameters) if (p instanceof PostLoadable) ((PostLoadable)p).postLoad();
        for (List<Parameter> l : this.parameters.values()) for (Parameter p : l) if (p instanceof PostLoadable) ((PostLoadable)p).postLoad();
        if (action instanceof PostLoadable) ((PostLoadable)action).postLoad();
        postLoaded=true;
    }*/
}
