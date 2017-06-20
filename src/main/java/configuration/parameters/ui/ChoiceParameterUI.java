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

import static boa.gui.GUI.logger;
import configuration.parameters.ActionableParameter;
import configuration.parameters.ChoiceParameter;
import configuration.parameters.ChoosableParameter;
import configuration.parameters.ConditionalParameter;
import configuration.parameters.ParameterUtils;
import boa.gui.configuration.ConfigurationTreeModel;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;

/**
 *
 * @author jollion
 */
public class ChoiceParameterUI implements ArmableUI {
    ChoosableParameter choice;
    ConditionalParameter cond;
    ConfigurationTreeModel model;
    JMenuItem[] actionChoice;
    List allActions;
    int inc;
    //final static int choiceLimit = 50;
    boolean limitChoice;
    public static String NO_SELECTION="no selection";
    //double modulo;
    public ChoiceParameterUI(ChoosableParameter choice_, boolean limitChoice) {
        this(choice_, limitChoice, null);
    } 
    
    public ChoiceParameterUI(ChoosableParameter choice_, boolean limitChoice, String subMenuTitle) {
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
        //this.actionChoice = new JMenuItem[!limitChoice || choiceLimit>choices.length? choices.length:choiceLimit];
        this.actionChoice = new JMenuItem[choices.length];
        //modulo = (!limitChoice || choiceLimit>choices.length)? 1 : (double)choices.length/(double)(choiceLimit-1);
        for (int i = 0; i < actionChoice.length; i++) {
            //int choiceIdx = (int)(i * modulo);
            actionChoice[i] = new JMenuItem(choices[i]);
            actionChoice[i].setAction(
                new AbstractAction(choices[i]) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        //if (ae.getActionCommand().equals("no selection"))
                        choice.setSelectedItem(ae.getActionCommand());
                        choice.fireListeners();
                        //logger.debug("choice modif: {}, cond null? {}, model null?: {}", ae.getActionCommand(), cond==null, model==null);
                        if (cond!=null) model.nodeStructureChanged(cond);
                        else if (model!=null) model.nodeStructureChanged(choice);
                    }
                }
            );
        }
        if (subMenuTitle!=null) {
            JMenu subMenu = new JMenu(subMenuTitle);
            for (JMenuItem a : actionChoice) subMenu.add(a);
            allActions = new ArrayList(){{add(subMenu);}};
        } else allActions = new ArrayList(Arrays.asList(actionChoice));
        refreshArming();
    }
    public void addActions(JMenuItem action, boolean addSeparator) {
        if (addSeparator) allActions.add(new JSeparator());
        allActions.add(action);
    }
    public void updateUIFromParameter() {
        refreshArming();
    }
    @Override
    public void refreshArming() {
        unArm();
        int sel = choice.getSelectedIndex();
        if (sel>=0 && (sel+inc) < actionChoice.length) {
            //actions[(int)((sel+inc) / modulo+0.5)].setArmed(true);
            actionChoice[sel+inc].setArmed(true);
        }
        if (inc>0 && sel<0) actionChoice[0].setArmed(true);
    }
    
    public void unArm() {
        for (JMenuItem a:actionChoice) a.setArmed(false);
    }

    public Object[] getDisplayComponent() {return allActions.toArray();}
}
