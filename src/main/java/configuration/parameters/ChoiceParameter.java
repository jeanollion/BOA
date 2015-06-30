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

/**
 *
 * @author jollion
 */
public class ChoiceParameter extends SimpleParameter implements ActionableParameter {
    String[] listChoice;
    String selectedItem;
    @Transient int selectedIndex;
    @Transient ChoiceParameterUI gui;
    @Transient ConditionalParameter cond;
    public ChoiceParameter(String name, String[] listChoice, String selectedItem) {
        super(name);
        this.listChoice=listChoice;
        setSelectedItem(selectedItem);
    }
    
    public String[] getChoices() {return listChoice;}
    public String getSelectedItem() {return selectedItem;}
    public int getSelectedIndex() {return selectedIndex;}
    
    public void setSelectedItem(String selectedItem) {
        this.selectedIndex=Arrays.binarySearch(listChoice, selectedItem);
        if (selectedIndex==-1) this.selectedItem = "no item selected";
        else this.selectedItem=selectedItem;
        setCondValue();
    }
    protected void setCondValue() {
        if (cond!=null) cond.setActionValue(selectedItem);
    }
    
    public void setSelectedIndex(int selectedIndex) {
        this.selectedItem=listChoice[selectedIndex];
        this.selectedIndex=selectedIndex;
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
        return new ChoiceParameter(name, listChoice, selectedItem);
    }
    
    // actionable parameter
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
