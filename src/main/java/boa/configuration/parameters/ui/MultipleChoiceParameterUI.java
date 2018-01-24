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
package boa.configuration.parameters.ui;

import static boa.gui.GUI.logger;
import boa.configuration.parameters.ActionableParameter;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.ChoosableParameter;
import boa.configuration.parameters.ChoosableParameterMultiple;
import boa.configuration.parameters.ConditionalParameter;
import boa.configuration.parameters.ParameterUtils;
import boa.gui.configuration.ConfigurationTreeModel;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.MenuSelectionManager;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicCheckBoxMenuItemUI;

/**
 *
 * @author jollion
 */
public class MultipleChoiceParameterUI implements ParameterUI {
    ChoosableParameterMultiple choice;
    ConfigurationTreeModel model;
    JList list;
    DefaultListModel<String> listModel;
    JScrollPane listJsp;
    JMenuItem[] menuItems;
    boolean multiple=true;
    JPopupMenu menu;
    int X, Y;
    Component parentComponent;
    public MultipleChoiceParameterUI(ChoosableParameterMultiple choice_) {
        this.choice = choice_;
        this.model= ParameterUtils.getModel(choice);
        listModel = new DefaultListModel();
        for (String c : choice.getChoiceList()) listModel.addElement(c);
        list = new JList(listModel);
        listJsp = new JScrollPane(list);
        listJsp.setMinimumSize(new Dimension(100, 300));
        updateUIFromParameter();
        menuItems = new JMenuItem[2];
        menuItems[0] = new StayOpenMenuItem("Select All", this);
        menuItems[0].setAction(
            new AbstractAction("Select All") {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    list.setSelectionInterval(0, listModel.getSize()-1);
                    updateSelectedItemsToParameter();
                }
            }
        );
        
        JMenu subMenu = new JMenu("Items");
        menuItems[1] = subMenu;
        subMenu.add(listJsp);
    }
    
    public int[] getSelectedItems() {
        return list.getSelectedIndices();
    }
    
    public void updateSelectedItemsToParameter() {
        choice.setSelectedIndicies(getSelectedItems() );
        choice.fireListeners();
        if (model!=null) model.nodeChanged(choice);
    }
    
    public void updateUIFromParameter() {
        list.setSelectedIndices(choice.getSelectedItems());
    }
    
    @Override
    public JMenuItem[] getDisplayComponent() {return menuItems;}
    
    public void addMenuListener(JPopupMenu menu, int X, int Y, Component parent) {
        this.menu=menu;
        this.X=X;
        this.Y=Y;
        this.parentComponent=parent;
        //logger.debug("menu set!");
        menu.addPopupMenuListener(new PopupMenuListener() {
            
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                updateUIFromParameter();
            }

            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                updateSelectedItemsToParameter();
            }

            public void popupMenuCanceled(PopupMenuEvent e) {
                updateSelectedItemsToParameter();
            }
        });
    }
    public void showMenu() {
        //logger.debug("menu null? {}", menu==null);
        if (menu!=null) menu.show(parentComponent, X, Y);
    }
}
