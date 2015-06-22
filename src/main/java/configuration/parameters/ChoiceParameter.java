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
public class ChoiceParameter extends SimpleParameter {
    String[] listChoice;
    String selectedItem;
    @Transient 
    int selectedIndex;
    ChoiceParameterUI gui;
    
    public ChoiceParameter(String name, String[] listChoice, String selectedItem) {
        super(name);
        this.listChoice=listChoice;
        this.selectedItem=selectedItem;
    }
    
    public String[] getChoices() {return listChoice;}
    public String getSelectedItem() {return selectedItem;}
    public int getSelectedIndex() {return selectedIndex;}
    public void setSeletectItem(String selectedItem) {
        this.selectedIndex=Arrays.binarySearch(listChoice, selectedItem);
        if (selectedIndex==-1) this.selectedItem = "no item selected";
        else this.selectedItem=selectedItem;
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
    
    // morphia 
    private ChoiceParameter(){super();}
}
