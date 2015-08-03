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
import configuration.parameters.ChoosableParameterMultiple;
import configuration.parameters.ConditionalParameter;
import configuration.parameters.ParameterUtils;
import configuration.userInterface.ConfigurationTreeModel;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import javax.swing.AbstractAction;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
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
public class MultipleChoiceParameterUI implements ParameterUI{
    ChoosableParameterMultiple choice;
    ConfigurationTreeModel model;
    JCheckBoxMenuItem[] items;
    JMenuItem[] menuItems;
    boolean multiple=true;
    public MultipleChoiceParameterUI(ChoosableParameterMultiple choice_) {
        this.choice = choice_;
        this.model= ParameterUtils.getModel(choice);
        final String[] choices=choice.getChoiceList();
        this.items = new JCheckBoxMenuItem[choices.length];
        for (int i = 0; i < items.length; i++) {
            items[i] = new JCheckBoxMenuItem(choices[i]);
            items[i].setUI(new StayOpenCheckBoxMenuItemUI());
        }
        updateSelectedItemsToUI();
        menuItems = new JMenuItem[4];
        menuItems[0] = new JMenuItem("Select All");
        menuItems[0].setAction(
            new AbstractAction("Select All") {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    for (JCheckBoxMenuItem i : items) i.setSelected(true);
                    updateSelectedItemsToParameter();
                }
            }
        );
        menuItems[1] = new JMenuItem("Select None");
        menuItems[1].setAction(
            new AbstractAction("Select None") {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    for (JCheckBoxMenuItem i : items) i.setSelected(false);
                    updateSelectedItemsToParameter();
                }
            }
        );
        menuItems[2] = new JMenuItem("Select Range");
        menuItems[2].setAction(
            new AbstractAction("Select Range") {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    int idxMin = items.length;
                    int idxMax = -1;
                    for (int i = 0; i<items.length; ++i) {
                        if (items[i].isSelected()) {
                            if (i<idxMin) idxMin = i;
                            if (i>idxMax) idxMax = i;
                        }
                    }
                    if (idxMin<idxMax) {
                        for (int i = idxMin; i<=idxMax; ++i) items[i].setSelected(true);
                    }
                    updateSelectedItemsToParameter();
                }
            }
        );
        JMenu subMenu = new JMenu("Items");
        menuItems[3] = subMenu;
        for (JMenuItem i : items) subMenu.add(i);
        
    }
    
    public int[] getSelectedItems() {
        ArrayList<Integer> selectedItems = new ArrayList<Integer>();
        for (int i = 0; i<items.length; ++i) if (items[i].isSelected()) selectedItems.add(i);
        int[] sel = new int[selectedItems.size()];
        for (int i = 0; i<sel.length; ++i) sel[i]=selectedItems.get(i);
        return sel;
    }
    
    public void updateSelectedItemsToParameter() {
        choice.setSelectedIndicies(getSelectedItems() );
        if (model!=null) model.nodeChanged(choice);
    }
    
    public void updateSelectedItemsToUI() {
        for (int i : choice.getSelectedItems()) items[i].setSelected(true);
    }
    
    public JMenuItem[] getDisplayComponent() {return menuItems;}
    
    public void addMenuListener(JPopupMenu menu) {
        menu.addPopupMenuListener(new PopupMenuListener() {
            
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                updateSelectedItemsToUI();
            }

            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                updateSelectedItemsToParameter();
            }

            public void popupMenuCanceled(PopupMenuEvent e) {
                updateSelectedItemsToParameter();
            }
        });
    }
    class StayOpenCheckBoxMenuItemUI extends BasicCheckBoxMenuItemUI {
        @Override
        protected void doClick(MenuSelectionManager msm) {
            menuItem.doClick(0);
        }
    }
}
