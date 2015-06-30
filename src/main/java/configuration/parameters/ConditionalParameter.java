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
import org.mongodb.morphia.annotations.PostLoad;
import org.mongodb.morphia.annotations.Transient;

/**
 *
 * @author jollion
 */
public class ConditionalParameter extends SimpleContainerParameter {
    ActionableParameter action;
    @Transient HashMap<Object, Parameter[]> parameters;
    Parameter[] currentParameters;
    
    public ConditionalParameter(ActionableParameter action) {
        this(action, new HashMap<Object, Parameter[]>(4));
    }
    
    public ConditionalParameter(ActionableParameter action, HashMap<Object, Parameter[]> parameters) {
        super(action.getName());
        this.action=action;
        this.parameters=parameters;
        action.setConditionalParameter(this);
        setActionValue(action.getValue());
    }
    
    public void setAction(Object actionValue, Parameter[] parameters) {
        this.parameters.put(actionValue, parameters);
        if (actionValue.equals(action.getValue())) setActionValue(action.getValue());
    }

    public void setContentFrom(Parameter other) {
        if (other instanceof ConditionalParameter) {
            ConditionalParameter otherC = (ConditionalParameter)other;
            if ((otherC.getActionableParameter().getValue()).equals(this.action.getValue()) && currentParameters.length==otherC.currentParameters.length) {
                ParameterUtils.setContent(currentParameters, otherC.currentParameters);
            } else {
                action.setValue(otherC.getActionableParameter().getValue());
                currentParameters=ParameterUtils.duplicateArray(otherC.currentParameters);
                initChildren();
            }
            action.setContentFrom(otherC.action);
        } else throw new IllegalArgumentException("wrong parameter type");
    }
    
    public ActionableParameter getActionableParameter() {return action;}
    
    public void setActionValue(Object actionValue) {
        if (actionValue==null) return;
        currentParameters=parameters.get(actionValue);
        if (!action.getValue().equals(actionValue)) this.action.setValue(actionValue); // avoid loop
        initChildren();
    }

    public Parameter duplicate() {
        ConditionalParameter cond = new ConditionalParameter((ActionableParameter)action.duplicate());
        for (Entry<Object, Parameter[]> e : this.parameters.entrySet()) cond.setAction(e.getKey(), e.getValue());
        cond.setContentFrom(this);
        return cond;
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
    
    protected void initChildren() {
        if (this.currentParameters==null) children = new ArrayList<Parameter>(0);
        else children=new ArrayList<Parameter>(Arrays.asList(currentParameters));
        for (Parameter p : children) p.setParent(this);
    }
    
    // morphia
    ConditionalParameter(){super();}
    @PostLoad void postLoad() {initChildren();}
}
