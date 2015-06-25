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
package configuration.parameters.ui;

import configuration.dataStructure.Experiment;
import configuration.parameters.ChoiceParameter;
import configuration.parameters.SimpleParameter;
import configuration.parameters.StructureParameter;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;

/**
 *
 * @author jollion
 */
public class StructureParameterUI implements ParameterUI {
    StructureParameter structure;
    JMenuItem[] actions;
    public StructureParameterUI(StructureParameter structure_) {
        this.structure = structure_;
        final String[] choices = structure.getStructureNames();
        this.actions = new JMenuItem[choices.length];
        for (int i = 0; i < actions.length; i++) {
            actions[i] = new JMenuItem(choices[i]);
            actions[i].setAction(
                new AbstractAction(choices[i]) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        structure.setSelectedStructure(getIndex(choices, ae.getActionCommand()));
                    }
                }
            );
        }
        refreshArming();
    }
    
    private static int getIndex(String[] array, String key) {
        for (int i = 0; i<array.length; i++) if (key.equals(array[i])) return i;
        return -1;
    }
    public void refreshArming() {
        int sel = structure.getSelectedStructure();
        if (structure.isAllowNoSelection()) sel++;
        if (sel>=0) actions[sel].setArmed(true);
    }
    public JMenuItem[] getDisplayComponent() {return actions;}
}
