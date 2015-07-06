/*
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
import configuration.parameters.ui.ChoiceParameterUI;
import java.util.Arrays;
import org.mongodb.morphia.annotations.Transient;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class ChoiceParameter extends SimpleParameter implements ActionableParameter, ChoosableParameter {
    String[] listChoice;
    String selectedItem;
    boolean allowNoSelection;
    @Transient int selectedIndex;
    @Transient ChoiceParameterUI gui;
    @Transient ConditionalParameter cond;
    public ChoiceParameter(String name, String[] listChoice, String selectedItem, boolean allowNoSelection) {
        super(name);
        this.listChoice=listChoice;
        setSelectedItem(selectedItem);
        this.allowNoSelection=allowNoSelection;
    }
    
    public String getSelectedItem() {return selectedItem;}
    public int getSelectedIndex() {return selectedIndex;}
    
    public void setSelectedItem(String selectedItem) {
        this.selectedIndex=Utils.getIndex(listChoice, selectedItem);
        if (selectedIndex==-1) this.selectedItem = "no item selected";
        else this.selectedItem=selectedItem;
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
    }
    
    @Override
    public String toString() {return name + ": "+ selectedItem;}

    @Override
    public ParameterUI getUI() {
        if (gui==null) gui = new ChoiceParameterUI(this);
        return gui;
    }
    public boolean sameContent(Parameter other) {
        if (other instanceof ChoiceParameter) {
            return this.getSelectedItem().equals((ChoiceParameter)other);
        }
        else return false;
        
    }

    public void setContentFrom(Parameter other) {
        if (other instanceof ChoiceParameter) {
            setSelectedItem(((ChoiceParameter)other).getSelectedItem());
        } else throw new IllegalArgumentException("wrong parameter type");
    }

    public Parameter duplicate() {
        return new ChoiceParameter(name, listChoice, selectedItem, allowNoSelection);
    }
    
    // choosable parameter

    public boolean isAllowNoSelection() {
        return this.allowNoSelection;
    }
    
    // actionable parameter
    public String[] getChoiceList() {
        return listChoice;
    }
    
    
    protected void setCondValue() {
        if (cond!=null) cond.setActionValue(selectedItem);
    }
    
    public Object getValue() {
        return getSelectedItem();
    }

    public void setValue(Object value) {
        this.setSelectedItem(value.toString());
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
    
    // morphia 
    ChoiceParameter(){super();}

    

    

    

    
}
