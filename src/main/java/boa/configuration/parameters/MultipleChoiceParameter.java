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

import boa.configuration.parameters.ui.MultipleChoiceParameterUI;
import boa.configuration.parameters.ui.ParameterUI;
import org.json.simple.JSONArray;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class MultipleChoiceParameter extends SimpleParameter implements ChoosableParameterMultiple {
    int[] selectedItems;
    String[] listChoice;
    MultipleChoiceParameterUI ui;
    int displayTrimSize=50; // for toString method
    
    public MultipleChoiceParameter(String name, String[] listChoice, int[] selectedItems) {
        super(name);
        this.listChoice=listChoice;
        this.selectedItems=selectedItems;
    }
    
    public MultipleChoiceParameter(String name, String[] listChoice, boolean selectAll) {
        super(name);
        this.listChoice=listChoice;
        if (selectAll) setAllSelectedItems();
        else selectedItems=new int[0];
    }
    
    
    public void setTrimSize(int trimSize) {
        this.displayTrimSize=trimSize;
    }
    
    public ParameterUI getUI() {
        if (ui==null) ui=new MultipleChoiceParameterUI(this);
        else ui.updateUIFromParameter();
        return ui;
    }
    
    // multiple choice parameter implementation
    public void setSelectedIndicies(int[] selectedItems) {
        if (selectedItems==null) this.selectedItems=new int[0];
        else this.selectedItems = selectedItems;
    }
    
    public void setAllSelectedItems() {
        this.selectedItems=new int[listChoice.length];
        for (int i = 0; i<selectedItems.length; ++i) selectedItems[i]=i;
    }

    public int[] getSelectedItems() {
        if (selectedItems==null) selectedItems = new int[0];
        return selectedItems;
    }
    
    public String[] getSelectedItemsNames() {
        String[] res = new String[getSelectedItems().length];
        for (int i = 0 ; i<res.length; ++i) res[i] = listChoice[selectedItems[i]];
        return res;
    }

    public String[] getChoiceList() {
        return listChoice;
    }

    // parameter implementation 
    
    @Override
    public String toString() {
        return name +": "+ Utils.getStringArrayAsStringTrim(displayTrimSize, getSelectedItemsNames());
    }
    @Override
    public boolean sameContent(Parameter other) { // checks only indicies
        if (other instanceof ChoosableParameterMultiple) {
            if (!ParameterUtils.arraysEqual(getSelectedItems(), ((ChoosableParameterMultiple)other).getSelectedItems())) {
                logger.debug("MultipleChoiceParameter: {}!={} {} vs {}", this, other ,getSelectedItems(),((ChoosableParameterMultiple)other).getSelectedItems());
                return false;
            } else return true;
        } else return false;
    }
    @Override
    public void setContentFrom(Parameter other) {
        if (other instanceof ChoosableParameterMultiple) {
            setSelectedIndicies(((ChoosableParameterMultiple)other).getSelectedItems());
        } else if (other instanceof ChoosableParameter) {
            String sel = ((ChoosableParameter)other).getChoiceList()[((ChoosableParameter)other).getSelectedIndex()];
            int i = Utils.getIndex(listChoice, sel);
            if (i>=0) this.selectedItems=new int[]{i};
        } else throw new IllegalArgumentException("wrong parameter type");
    }
    
    @Override
    public MultipleChoiceParameter duplicate() {
        return new MultipleChoiceParameter(name, listChoice, selectedItems);
    }
    
    @Override
    public Object toJSONEntry() {
        JSONArray res = new JSONArray();
        for (int i : selectedItems) res.add(i);
        return res;
    }

    @Override
    public void initFromJSONEntry(Object jsonEntry) {
        JSONArray source = (JSONArray)jsonEntry;
        this.selectedItems=new int[source.size()];
        for (int i = 0; i<source.size(); ++i) selectedItems[i] = ((Number)source.get(i)).intValue();
    }
}
