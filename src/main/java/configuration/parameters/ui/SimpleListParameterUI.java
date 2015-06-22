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

import configuration.parameters.ListParameter;
import configuration.parameters.Parameter;
import configuration.parameters.SimpleParameter;
import configuration.parameters.ui.ListParameterUI;
import configuration.userInterface.ConfigurationTreeModel;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;

/**
 *
 * @author jollion
 */
public class SimpleListParameterUI implements ListParameterUI{
    ListParameter list;
    JMenuItem[] actions;
    ConfigurationTreeModel model;
    static String[] actionNames=new String[]{"Add Element", "Remove All"};
    static String[] childActionNames=new String[]{"Add", "Remove", "Up", "Down"};
    
    public SimpleListParameterUI(ListParameter list_) {
        this.list = list_;
        this.model= SimpleParameter.getModel(list);
        this.actions = new JMenuItem[2];
        actions[0] = new JMenuItem(actionNames[0]);
        actions[0].setAction(
            new AbstractAction(actionNames[0]) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    Parameter p = list.createChildInstance();
                    model.insertNodeInto(p, list);
                }
            }
        );
        actions[1] = new JMenuItem(actionNames[1]);
        actions[1].setAction(
            new AbstractAction(actionNames[1]) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    list.removeAllElements();
                    model.nodeStructureChanged(list);
                }
            }
        );
    }
    public JMenuItem[] getDisplayComponent() {return actions;}
    
    public JMenuItem[] getChildDisplayComponent(final Parameter child) {
        final int unMutableIdx = list.getUnMutableIndex();
        final int idx = list.getIndex(child);
        final boolean mutable = idx>unMutableIdx;
        JMenuItem[] childActions = new JMenuItem[4];
        childActions[0] = new JMenuItem(childActionNames[0]);
        childActions[0].setAction(
            new AbstractAction(childActionNames[0]) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    Parameter p = list.createChildInstance();
                    model.insertNodeInto(p, list, mutable?idx+1:unMutableIdx+1);
                }
            }
        );
        childActions[1] = new JMenuItem(childActionNames[1]);
        childActions[1].setAction(
            new AbstractAction(childActionNames[1]) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    model.removeNodeFromParent(child);
                }
            }
        );
        childActions[2] = new JMenuItem(childActionNames[2]);
        childActions[2].setAction(
            new AbstractAction(childActionNames[2]) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    model.moveUp(list, child);
                }
            }
        );
        
        childActions[3] = new JMenuItem(childActionNames[3]);
        childActions[3].setAction(
            new AbstractAction(childActionNames[3]) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    model.moveDown(list, child);
                }
            }
        );
        if (!mutable) {
            childActions[1].setEnabled(false);
            childActions[2].setEnabled(false);
            childActions[3].setEnabled(false);
        }
        if (idx==unMutableIdx+1) childActions[2].setEnabled(false);
        if (idx==0) childActions[2].setEnabled(false);
        if (idx==list.getChildCount()-1) childActions[3].setEnabled(false);
        return childActions;
    }
    
    @Override public void refresh(){}
}
