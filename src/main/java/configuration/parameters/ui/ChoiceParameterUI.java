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

import configuration.parameters.ChoiceParameter;
import configuration.parameters.ConditionalParameter;
import configuration.parameters.ParameterUtils;
import configuration.userInterface.ConfigurationTreeModel;
import java.awt.Component;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;

/**
 *
 * @author jollion
 */
public class ChoiceParameterUI implements ArmableUI {
    ChoiceParameter choice;
    ConditionalParameter cond;
    ConfigurationTreeModel model;
    JMenuItem[] actions;
    public ChoiceParameterUI(ChoiceParameter choice_) {
        this.choice = choice_;
        cond = choice.getConditionalParameter();
        if (cond!=null) this.model= ParameterUtils.getModel(cond);
        else this.model= ParameterUtils.getModel(choice);
        final String[] choices = choice.getChoices();
        this.actions = new JMenuItem[choices.length];
        for (int i = 0; i < actions.length; i++) {
            actions[i] = new JMenuItem(choices[i]);
            actions[i].setAction(
                new AbstractAction(choices[i]) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        choice.setSelectedItem(ae.getActionCommand());
                        if (cond!=null) model.nodeStructureChanged(cond);
                        else if (model!=null) model.nodeChanged(choice);
                    }
                }
            );
        }
        refreshArming();
    }
    @Override
    public void refreshArming() {
        unArm();
        int sel = choice.getSelectedIndex();
        if (sel>=0) actions[sel].setArmed(true);
    }
    
    public void unArm() {
        for (JMenuItem a:actions) a.setArmed(false);
    }

    public JMenuItem[] getDisplayComponent() {return actions;}
}
