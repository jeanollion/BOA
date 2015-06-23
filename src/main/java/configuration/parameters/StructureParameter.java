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

import configuration.dataStructure.Experiment;
import configuration.parameters.ui.ParameterUI;
import configuration.parameters.ui.StructureParameterUI;
import org.mongodb.morphia.annotations.Transient;

/**
 *
 * @author jollion
 */
public class StructureParameter extends SimpleParameter {
    int selectedStructure;
    boolean allowNoSelection;
    @Transient private Experiment xp;
    
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
            this.setSelectedStructure(otherP.selectedStructure);
        } else throw new IllegalArgumentException("wrong parameter type");
    }

    public Parameter duplicate() {
        return new StructureParameter(name, selectedStructure, allowNoSelection);
    }
    
    public int getSelectedStructure() {
        return selectedStructure;
    }

    public boolean isAllowNoSelection() {
        return allowNoSelection;
    }
    
    protected Experiment getXP() {
        if (xp==null) xp= SimpleParameter.getExperiment(this);
        return xp;
    }
    
    public String[] getStructureNames() {
        String[] choices;
        if (getXP()!=null) {
            choices=getXP().getStructuresAsString();
        } else {
            choices = new String[]{"error"}; //no experiment in the tree, make a static method to get experiment...
        }
        if (allowNoSelection) {
            String[] res = new String[choices.length+1];
            res[0] = "no selection";
            System.arraycopy(choices, 0, res, 1, choices.length);
            return res;
        } else return choices;
    }
    
    @Override 
    public String toString(){
        if (allowNoSelection) return name+": "+getStructureNames()[selectedStructure+1];
        else return name+": "+getStructureNames()[selectedStructure];
    }
    
    public ParameterUI getUI() {
        return new StructureParameterUI(this);
    }

    public void setSelectedStructure(int selectedStructure) {
        if (allowNoSelection) {
            if (selectedStructure>=0) this.selectedStructure = selectedStructure-1;
            else this.selectedStructure=-1;
        }
        else this.selectedStructure = selectedStructure;
    }

    // morphia
    protected StructureParameter(){super();}

    
}
