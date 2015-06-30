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

import configuration.parameters.ParameterUtils;
import configuration.parameters.PluginParameter;
import configuration.userInterface.ConfigurationTreeModel;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;

/**
 *
 * @author jollion
 */
public class PluginParameterUI implements ArmableUI {
    PluginParameter parameter;
    JMenuItem[] actions;
    ConfigurationTreeModel model;
    public PluginParameterUI(PluginParameter parameter_) {
        this.parameter = parameter_;
        this.model= ParameterUtils.getModel(parameter);
        final ArrayList<String> choices = parameter.getPluginNames();
        this.actions = new JMenuItem[choices.size()];
        for (int i = 0; i < actions.length; i++) {
            actions[i] = new JMenuItem(choices.get(i));
            actions[i].setAction(
                new AbstractAction(choices.get(i)) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        parameter.setPlugin(ae.getActionCommand());
                        if (model!=null) model.nodeChanged(parameter);
                    }
                }
            );
        }
        refreshArming();
    }
    @Override
    public void refreshArming() {
        String pname = parameter.getPluginName();
        if (pname==null) return;
        for (JMenuItem j : actions) if (pname.equals(j.getText())) j.setArmed(true);
    }
    public JMenuItem[] getDisplayComponent() {return actions;}
}
