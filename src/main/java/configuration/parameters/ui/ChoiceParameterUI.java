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

import configuration.parameters.ActionableParameter;
import configuration.parameters.ChoiceParameter;
import configuration.parameters.ChoosableParameter;
import configuration.parameters.ConditionalParameter;
import configuration.parameters.ParameterUtils;
import boa.gui.configuration.ConfigurationTreeModel;
import java.awt.Component;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;

/**
 *
 * @author jollion
 */
public class ChoiceParameterUI implements ArmableUI {
    ChoosableParameter choice;
    ConditionalParameter cond;
    ConfigurationTreeModel model;
    JMenuItem[] actions;
    int inc;
    final static int choiceLimit = 50;
    boolean limitChoice;
    public static String NO_SELECTION="no selection";
    double modulo;
    public ChoiceParameterUI(ChoosableParameter choice_, boolean limitChoice) {
        this.choice = choice_;
        this.limitChoice=limitChoice;
        if (choice.isAllowNoSelection()) inc=1;
        else inc=0;
        if (choice instanceof ActionableParameter) cond = ((ActionableParameter)choice).getConditionalParameter();
        if (cond!=null) this.model= ParameterUtils.getModel(cond);
        else this.model= ParameterUtils.getModel(choice);
        final String[] choices;
        if (choice.isAllowNoSelection()) {
            String[] c = choice.getChoiceList();
            String[] res = new String[c.length+1];
            res[0] = NO_SELECTION;
            System.arraycopy(c, 0, res, 1, c.length);
            choices=res;
        } else choices=choice.getChoiceList();
        this.actions = new JMenuItem[!limitChoice || choiceLimit>choices.length? choices.length:choiceLimit];
        modulo = (!limitChoice || choiceLimit>choices.length)? 1 : (double)choices.length/(double)(choiceLimit-1);
        for (int i = 0; i < actions.length; i++) {
            int choiceIdx = (int)(i * modulo);
            actions[i] = new JMenuItem(choices[choiceIdx]);
            actions[i].setAction(
                new AbstractAction(choices[choiceIdx]) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        //if (ae.getActionCommand().equals("no selection"))
                        choice.setSelectedItem(ae.getActionCommand());
                        if (cond!=null) model.nodeStructureChanged(cond);
                        else if (model!=null) model.nodeStructureChanged(choice);
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
        if (sel>=0) {
            actions[(int)((sel+inc) / modulo+0.5)].setArmed(true);
        }
        if (inc>0 && sel<0) actions[0].setArmed(true);
    }
    
    public void unArm() {
        for (JMenuItem a:actions) a.setArmed(false);
    }

    public JMenuItem[] getDisplayComponent() {return actions;}
}
