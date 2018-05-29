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

import boa.configuration.parameters.ui.ParameterUI;
import boa.configuration.parameters.ui.ChoiceParameterUI;

import boa.utils.Utils;
import java.util.function.Consumer;

/**
 *
 * @author jollion
 */

public class ChoiceParameter extends SimpleParameter implements ActionableParameter, ChoosableParameter, Listenable {
    String selectedItem;
    String[] listChoice;
    boolean allowNoSelection;
    private int selectedIndex=-2;
    ConditionalParameter cond;
    boolean postLoaded = false;
    
    
    
    public ChoiceParameter(String name, String[] listChoice, String selectedItem, boolean allowNoSelection) {
        super(name);
        this.listChoice=listChoice;
        setSelectedItem(selectedItem);
        this.allowNoSelection=allowNoSelection;
    }
    
    public String getSelectedItem() {return selectedItem;}
    public int getSelectedIndex() {
        return selectedIndex;
    }
    
    @Override 
    public void setSelectedItem(String selectedItem) {
        this.selectedIndex=Utils.getIndex(listChoice, selectedItem);
        if (selectedIndex==-1) this.selectedItem = "no item selected";
        else this.selectedItem=selectedItem;
        fireListeners();
        setCondValue();
    }
    
    public void setSelectedIndex(int selectedIndex) {
        if (selectedIndex>=0) {
            this.selectedItem=listChoice[selectedIndex];
            this.selectedIndex=selectedIndex;
        } else {
            selectedIndex=-1;
            selectedItem="no item selected";
        }
        fireListeners();
        setCondValue();
    }
    
    @Override
    public String toString() {return name + ": "+ selectedItem;}

    @Override
    public ParameterUI getUI() {
        return new ChoiceParameterUI(this);
    }
    @Override 
    public boolean isValid() {
        return !(!allowNoSelection && this.selectedIndex<0);
    }
    @Override
    public boolean sameContent(Parameter other) {
        if (other instanceof ChoiceParameter) {
            return this.getSelectedItem().equals(((ChoiceParameter)other).getSelectedItem());
        }
        else return false;
        
    }
    @Override
    public void setContentFrom(Parameter other) {
        if (other instanceof ChoiceParameter) {
            bypassListeners=true;
            ChoiceParameter otherC = (ChoiceParameter)other;
            setSelectedItem(otherC.getSelectedItem());
            bypassListeners=false;
            //logger.debug("choice {} set content from: {} current item: {}, current idx {}, other item: {}, other idx : {}", this.hashCode(), otherC.hashCode(), this.getSelectedItem(), this.getSelectedIndex(), otherC.getSelectedItem(), otherC.getSelectedIndex());
        } else throw new IllegalArgumentException("wrong parameter type: "+(other==null? "null":other.getClass()) +" instead of ChoiceParameter");
    }
    
    // choosable parameter
    @Override
    public boolean isAllowNoSelection() {
        return this.allowNoSelection;
    }
    
    @Override
    public String getNoSelectionString() {
        return ChoiceParameterUI.NO_SELECTION;
    }
    
    // actionable parameter
    @Override
    public String[] getChoiceList() {
        return listChoice;
    }
    
    
    protected void setCondValue() {
        if (cond!=null) cond.setActionValue(selectedItem);
    }
    
    public String getValue() {
        return getSelectedItem();
    }

    public void setValue(String value) {
        this.setSelectedItem(value);
    }
    
    public void setConditionalParameter(ConditionalParameter cond) {
        this.cond=cond;
    }
    /**
     * 
     * @return the asociated conditional parameter, or null if no conditionalParameter is associated
     */
    public ConditionalParameter getConditionalParameter() {
        return cond;
    }
    
    @Override public ChoiceParameter duplicate() {
        ChoiceParameter res = new ChoiceParameter(name, selectedItem);
        res.setListeners(listeners);
        return res;
    }
    private ChoiceParameter(String name, String selectedItem) {
        super(name);
        this.selectedItem=selectedItem;
    }
    
    //@PostLoad
    /*public void postLoad() {
        if (!postLoaded) {
            selectedIndex=Utils.getIndex(listChoice, selectedItem); 
            postLoaded = true;
        }
    }
    */

    @Override
    public Object toJSONEntry() {
        return selectedItem;
    }

    @Override
    public void initFromJSONEntry(Object json) {
        if (json instanceof String) {
            setSelectedItem((String)json);
        } else throw new IllegalArgumentException("JSON Entry is not String");
    }
}
