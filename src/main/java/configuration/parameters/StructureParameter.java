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
import dataStructure.configuration.Experiment;
import configuration.parameters.ui.ParameterUI;
import de.caluga.morphium.annotations.Transient;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class StructureParameter extends SimpleParameter implements ChoosableParameter {
    protected int selectedStructure;
    protected boolean allowNoSelection;
    @Transient protected Experiment xp;
    
    public StructureParameter(String name, int selectedStructure, boolean allowNoSelection) {
        super(name);
        if (selectedStructure<-1) this.selectedStructure=-1;
        else this.selectedStructure = selectedStructure;
        this.allowNoSelection=allowNoSelection;
    }
    
    public boolean sameContent(Parameter other) {
        if (other instanceof StructureParameter) {
            StructureParameter otherP = (StructureParameter) other;
            return selectedStructure==otherP.selectedStructure;
        } else return false;
    }

    public void setContentFrom(Parameter other) {
        if (other instanceof StructureParameter) {
            StructureParameter otherP = (StructureParameter) other;
            this.setSelectedIndex(otherP.selectedStructure);
        } else throw new IllegalArgumentException("wrong parameter type");
    }

    public Parameter duplicate() {
        return new StructureParameter(name, selectedStructure, allowNoSelection);
    }
    
    public int getSelectedIndex() {
        return selectedStructure;
    }

    public boolean isAllowNoSelection() {
        return allowNoSelection;
    }
    
    protected Experiment getXP() {
        if (xp==null) xp= ParameterUtils.getExperiment(this);
        return xp;
    }
    
    
    
    @Override 
    public String toString(){
        if (selectedStructure>=0) return name+": "+getChoiceList()[selectedStructure];
        else return name+": no selected structure";
    }
    
    public ParameterUI getUI() {
        return new ChoiceParameterUI(this);
    }

    public void setSelectedIndex(int selectedStructure) {
        if (allowNoSelection) {
            if (selectedStructure>=0) this.selectedStructure = selectedStructure;
            else this.selectedStructure=-1;
        }
        else {
            if (selectedStructure<0) this.selectedStructure=0;
            else this.selectedStructure = selectedStructure;
        }
    }
    
    // choosable parameter
    public void setSelectedItem(String item) {
        setSelectedIndex(Utils.getIndex(this.getChoiceList(), item));
    }
    
    public String[] getChoiceList() {
        String[] choices;
        if (getXP()!=null) {
            choices=getXP().getStructuresAsString();
        } else {
            choices = new String[]{"error"}; //no experiment in the tree, make a static method to get experiment...
        }
        return choices;
    }
    

    // morphia
    protected StructureParameter(){super();}

    

}
