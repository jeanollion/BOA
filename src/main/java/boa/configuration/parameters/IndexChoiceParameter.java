/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.configuration.parameters;

import boa.configuration.parameters.ui.ChoiceParameterUI;
import boa.configuration.parameters.ui.MultipleChoiceParameterUI;
import boa.configuration.experiment.Experiment;
import boa.configuration.parameters.ui.ParameterUI;
import org.json.simple.JSONArray;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public abstract class IndexChoiceParameter extends SimpleParameter implements ChoosableParameter, ChoosableParameterMultiple {
    protected int[] selectedIndicies;
    protected boolean allowNoSelection, multipleSelection;
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
    public boolean isValid() {
        return !(allowNoSelection && selectedIndicies.length==0);
    }
    @Override
    public boolean sameContent(Parameter other) {
        if (other instanceof StructureParameter) {
            StructureParameter otherP = (StructureParameter) other;
            if (!ParameterUtils.arraysEqual(selectedIndicies, otherP.selectedIndicies)) {
                logger.debug("IndexChoiceParameter: {}!={} : {} vs {}", this, other, selectedIndicies, otherP.selectedIndicies);
                return false;
            } else return true;
        } else return false;
    }
    @Override
    public void setContentFrom(Parameter other) {
        if (other instanceof IndexChoiceParameter) {
            bypassListeners=true;
            IndexChoiceParameter otherP = (IndexChoiceParameter) other;
            if (otherP.selectedIndicies!=null) this.setSelectedIndicies(Utils.copyArray(otherP.selectedIndicies));
            else this.setSelectedIndex(-1);
            bypassListeners=false;
            //logger.debug("ICP: {} recieve from: {} -> {} ({})", name, otherP.getSelectedItems(), this.getSelectedItems(), this.getSelectedIndex());
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
            return  new ChoiceParameterUI(this);
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
        fireListeners();
    }
    
    // choosable parameter
    @Override
    public void setSelectedItem(String item) {
        setSelectedIndex(Utils.getIndex(getChoiceList(), item));
    }
    @Override
    public abstract String[] getChoiceList();

    // choosable parameter multiple
    @Override
    public void setSelectedIndicies(int[] selectedItems) {
        this.selectedIndicies=selectedItems;
        fireListeners();
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
    @Override
    public Object toJSONEntry() {
        JSONArray res = new JSONArray();
        for (int i : selectedIndicies) res.add(i);
        return res;
    }

    @Override
    public void initFromJSONEntry(Object jsonEntry) {
        JSONArray source = (JSONArray)jsonEntry;
        this.selectedIndicies=new int[source.size()];
        for (int i = 0; i<source.size(); ++i) selectedIndicies[i] = ((Number)source.get(i)).intValue();
    }
}
