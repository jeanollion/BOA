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
import configuration.parameters.ui.MultipleChoiceParameterUI;
import dataStructure.configuration.Experiment;
import configuration.parameters.ui.ParameterUI;
import de.caluga.morphium.annotations.Transient;
import utils.Utils;

/**
 *
 * @author jollion
 */
public abstract class IndexChoiceParameter extends SimpleParameter implements ChoosableParameter, ChoosableParameterMultiple {
    protected int[] selectedIndicies;
    @Transient protected boolean allowNoSelection, multipleSelection;
    //@Transient ParameterUI ui;
    
    public IndexChoiceParameter(String name) {
        this(name, -1, false, false);
    }
    
    public IndexChoiceParameter(String name, int selectedIndex, boolean allowNoSelection, boolean multipleSelection) {
        super(name);
        if (selectedIndex<-1) this.selectedIndicies=new int[]{-1};
        else this.selectedIndicies = new int[]{selectedIndex};
        this.allowNoSelection=allowNoSelection;
        this.multipleSelection=multipleSelection;
    }
    
    public IndexChoiceParameter(String name, int[] selectedIndicies, boolean allowNoSelection) {
        super(name);
        this.selectedIndicies = selectedIndicies;
        this.allowNoSelection=allowNoSelection;
        this.multipleSelection=true;
    }
    @Override
    public boolean sameContent(Parameter other) {
        if (other instanceof StructureParameter) {
            StructureParameter otherP = (StructureParameter) other;
            return ParameterUtils.arraysEqual(selectedIndicies, otherP.selectedIndicies);
        } else return false;
    }
    @Override
    public void setContentFrom(Parameter other) {
        if (other instanceof IndexChoiceParameter) {
            IndexChoiceParameter otherP = (IndexChoiceParameter) other;
            if (otherP.selectedIndicies!=null) this.setSelectedIndicies(Utils.copyArray(otherP.selectedIndicies));
            else this.setSelectedIndex(-1);
            //this.allowNoSelection=otherP.allowNoSelection;
            //this.multipleSelection=otherP.multipleSelection;
        } else throw new IllegalArgumentException("wrong parameter type");
    }
    @Override
    public int getSelectedIndex() {
        if (selectedIndicies==null) return -1;
        return selectedIndicies[0];
    }
    @Override
    public boolean isAllowNoSelection() {
        return allowNoSelection;
    }
    
    public void setMonoSelection(int selectedIndex) {
        this.multipleSelection=false;
        this.selectedIndicies=new int[]{selectedIndex};
    }
    
    public void setMultipleSelection(int[] selectedIndicies) {
        this.multipleSelection=true;
        this.selectedIndicies=selectedIndicies;
    }
    
    @Override 
    public String toString(){
        if (!multipleSelection) {
            if (getSelectedIndex()>=0 && getChoiceList().length>getSelectedIndex()) return name+": "+getChoiceList()[getSelectedIndex()];
            else return name+": no selected index";
        } else return name +": "+ Utils.getStringArrayAsStringTrim(50, getSelectedItemsNames());
    }
    
    public String[] getSelectedItemsNames() {
        String[] res = new String[getSelectedItems().length];
        for (int i = 0 ; i<res.length; ++i) res[i] = selectedIndicies[i]>=0?getChoiceList()[selectedIndicies[i]]:"void";
        return res;
    }
    
    public ParameterUI getUI() {
        if (multipleSelection) {
            return new MultipleChoiceParameterUI(this);
            //else ((MultipleChoiceParameterUI)ui).updateUIFromParameter();
            //return ui;
        }
        else {
            return  new ChoiceParameterUI(this, true);
            //else ((ChoiceParameterUI)ui).updateUIFromParameter();
            //return ui;
        }
    }

    public void setSelectedIndex(int selectedIndex) {
        if (allowNoSelection) {
            if (selectedIndex>=0) this.selectedIndicies = new int[]{selectedIndex};
            else this.selectedIndicies=new int[]{-1};
        }
        else {
            if (selectedIndex<0) this.selectedIndicies=new int[]{0};
            else this.selectedIndicies = new int[]{selectedIndex};
        }
    }
    
    // choosable parameter
    @Override
    public void setSelectedItem(String item) {
        setSelectedIndex(Utils.getIndex(this.getChoiceList(), item));
    }
    @Override
    public abstract String[] getChoiceList();

    // choosable parameter multiple
    @Override
    public void setSelectedIndicies(int[] selectedItems) {
        this.selectedIndicies=selectedItems;
    }
    @Override
    public int[] getSelectedItems() {
        if (selectedIndicies==null) {
            String[] list = getChoiceList();
            if (!allowNoSelection && list!=null) { // select all
                selectedIndicies = new int[list.length];
                for (int i = 0; i<list.length; ++i) selectedIndicies[i]=i;
            } else {
                selectedIndicies = new int[0];
            }
        }
        return selectedIndicies;
    }
}
