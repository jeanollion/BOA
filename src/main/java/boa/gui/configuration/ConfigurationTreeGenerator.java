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
package boa.gui.configuration;

import static boa.gui.GUI.logger;
import dataStructure.configuration.Experiment;
import configuration.parameters.ListParameter;
import configuration.parameters.ui.ListParameterUI;
import configuration.parameters.Parameter;
import configuration.parameters.ui.ParameterUI;
import configuration.parameters.ui.ArmableUI;
import configuration.parameters.ui.ChoiceParameterUI;
import configuration.parameters.ui.MultipleChoiceParameterUI;
import dataStructure.configuration.PreProcessingChain.PreProcessingChainUI;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 *
 * @author jollion
 */
public class ConfigurationTreeGenerator {
    protected Experiment rootParameter;
    protected ConfigurationTreeModel treeModel;
    protected JTree tree;
    private static final boolean soutParent=false;
    
    public ConfigurationTreeGenerator(Experiment xp) {
        rootParameter = xp;
    }
    
    public JTree getTree() {
        if (tree==null) generateTree();
        return tree;
    }
    public void flush() {
        if (tree!=null) {
            ToolTipManager.sharedInstance().unregisterComponent(tree);
            tree= null;
            rootParameter = null;
        }
    }
    private void generateTree() {
        treeModel = new ConfigurationTreeModel(rootParameter);
        tree = new JTree(treeModel) {
            @Override
            public String getToolTipText(MouseEvent evt) {
                if (getRowForLocation(evt.getX(), evt.getY()) == -1) return null;
                TreePath curPath = getPathForLocation(evt.getX(), evt.getY());
                Object node = curPath.getLastPathComponent();
                if (node instanceof Parameter) {
                    return ((Parameter)node).getToolTipText();
                } else return null;
            }
        };
        treeModel.setJTree(tree);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        DefaultTreeCellRenderer renderer = new TransparentTreeCellRenderer();
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
                            Parameter p = (Parameter) lastO;
                            ParameterUI ui = p.getUI();
                            if (ui!=null) {
                                //logger.debug("right click: UI: {}", ui.getClass().getSimpleName());
                                if (ui instanceof ChoiceParameterUI) ((ArmableUI)ui).refreshArming();
                                if (ui instanceof MultipleChoiceParameterUI) ((MultipleChoiceParameterUI)ui).addMenuListener(menu, pathBounds.x, pathBounds.y + pathBounds.height, tree);
                                if (ui instanceof PreProcessingChainUI) ((PreProcessingChainUI)ui).addMenuListener(menu, pathBounds.x, pathBounds.y + pathBounds.height, tree);
                                addToMenu(ui.getDisplayComponent(), menu);
                                menu.addSeparator();
                            }
                            if (path.getPathCount()>=2 && path.getPathComponent(path.getPathCount()-2) instanceof ListParameter) { // specific actions for children of ListParameters 
                            ListParameter lp = (ListParameter)path.getPathComponent(path.getPathCount()-2);
                            ListParameterUI listUI = (ListParameterUI)lp.getUI();
                            addToMenu(listUI.getChildDisplayComponent(p), menu);
                                //menu.addSeparator();
                            }
                        }
                        
                        menu.show(tree, pathBounds.x, pathBounds.y + pathBounds.height);
                    }
                } 
                if (soutParent && SwingUtilities.isLeftMouseButton(e)) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    tree.setSelectionPath(path);
                    Rectangle pathBounds = tree.getUI().getPathBounds(tree, path);
                    if (pathBounds != null && pathBounds.contains(e.getX(), e.getY())) {
                        Object lastO = path.getLastPathComponent();
                        if (lastO instanceof Parameter) {
                            Parameter p = (Parameter) lastO;
                            if (p.getParent() == null) {
                                System.out.println(p.toString() + " no parent");
                            } else {
                                System.out.println(p.toString() + ": has parent: " + p.getParent().toString());
                            }
                        }
                    }
                }
            }
        });
        ToolTipManager.sharedInstance().registerComponent(tree);
    }
    
    public static void addToMenu(Object[] UIElements, JPopupMenu menu) {
        for (Object o : UIElements) {
            if (o instanceof Action) menu.add((Action)o);
            else if (o instanceof JMenuItem) menu.add((JMenuItem)o);
            else if (o instanceof JSeparator) menu.addSeparator();
            else if (o instanceof Component) menu.add((Component)o);
            
        }
    }
}
