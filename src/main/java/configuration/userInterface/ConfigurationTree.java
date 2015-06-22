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
package configuration.userInterface;

import configuration.dataStructure.Experiment;
import configuration.parameters.ContainerParameter;
import configuration.parameters.ListParameter;
import configuration.parameters.ui.ListParameterUI;
import configuration.parameters.Parameter;
import configuration.parameters.ui.ParameterUI;
import configuration.parameters.SimpleListParameter;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 *
 * @author jollion
 */
public class ConfigurationTree {
    protected Experiment rootParameter;
    protected ConfigurationTreeModel treeModel;
    protected JTree tree;
    protected JScrollPane scroll;
    
    public ConfigurationTree() {
        rootParameter = new Experiment("Test Experiment");
        treeModel = new ConfigurationTreeModel(rootParameter);
        tree = new JTree(treeModel);
        treeModel.setJTree(tree);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        Icon personIcon = null;
        renderer.setLeafIcon(personIcon);
        renderer.setClosedIcon(personIcon);
        renderer.setOpenIcon(personIcon);
        tree.setCellRenderer(renderer);
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    tree.setSelectionPath(path);
                    Rectangle pathBounds = tree.getUI().getPathBounds(tree, path);
                    if (pathBounds != null && pathBounds.contains(e.getX(), e.getY())) {
                        JPopupMenu menu = new JPopupMenu();
                        Object lastO = path.getLastPathComponent();
                        if (lastO instanceof Parameter) {
                            if (lastO instanceof ListParameter) { // specific actions for ListParameters
                                ListParameter lp = (ListParameter)lastO;
                                ListParameterUI listUI = (ListParameterUI)lp.getUI();
                                addToMenu(listUI.getDisplayComponent(), menu);
                            } else if (path.getPathCount()>=2 && path.getPathComponent(path.getPathCount()-2) instanceof ListParameter) { // specific actions for children of ListParameters 
                                ListParameter lp = (ListParameter)path.getPathComponent(path.getPathCount()-2);
                                Parameter selectedP = (Parameter)lastO;
                                ListParameterUI listUI = (ListParameterUI)lp.getUI();
                                addToMenu(listUI.getChildDisplayComponent(selectedP), menu);
                                menu.addSeparator();
                            }
                            Parameter p = (Parameter) path.getLastPathComponent();
                            ParameterUI ui = p.getUI();
                            if (ui!=null) {
                                ui.refresh();
                                addToMenu(ui.getDisplayComponent(), menu);
                            }
                        }
                        
                        menu.show(tree, pathBounds.x, pathBounds.y + pathBounds.height);
                    }
                }
            }
        });
        
        scroll = new JScrollPane(tree);
    }
    
    public JScrollPane getUI() {
        return scroll;
    }
    
    public static void addToMenu(Object[] UIElements, JPopupMenu menu) {
        if (menu.getComponentCount()>0) menu.addSeparator();
        for (Object o : UIElements) {
            if (o instanceof Action) menu.add((Action)o);
            else if (o instanceof JMenuItem) menu.add((JMenuItem)o);
            else if (o instanceof Component) menu.add((Component)o);
        }
    }
}
