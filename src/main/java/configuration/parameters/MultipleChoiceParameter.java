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

import configuration.parameters.ui.MultipleChoiceParameterUI;
import configuration.parameters.ui.ParameterUI;
import de.caluga.morphium.annotations.Transient;
import org.json.simple.JSONArray;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class MultipleChoiceParameter extends SimpleParameter implements ChoosableParameterMultiple {
    int[] selectedItems;
    @Transient String[] listChoice;
    @Transient MultipleChoiceParameterUI ui;
    @Transient int displayTrimSize=50; // for toString method
    
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
    
    public boolean sameContent(Parameter other) { // checks only indicies
        if (other instanceof ChoosableParameterMultiple) {
            return ParameterUtils.arraysEqual(getSelectedItems(), ((ChoosableParameterMultiple)other).getSelectedItems());
        } else return false;
    }

    public void setContentFrom(Parameter other) {
        if (other instanceof ChoosableParameterMultiple) {
            //this.listChoice=((ChoosableParameterMultiple)other).getChoiceList();
            this.setSelectedIndicies(((ChoosableParameterMultiple)other).getSelectedItems());
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
