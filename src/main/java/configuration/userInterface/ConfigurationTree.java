/*
 * Copyright (C) 2015 ImageJ
 *
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
import configuration.parameters.Parameter;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 *
 * @author jollion
 */
public class ConfigurationTree extends JPanel {
    protected Experiment rootParameter;
    protected ConfigurationTreeModel treeModel;
    protected JTree tree;
    
    public ConfigurationTree() {
        super(new GridLayout(1,0));
        rootParameter = new Experiment("Test Experiment");
        treeModel = new ConfigurationTreeModel(rootParameter);
        tree = new JTree(treeModel);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    tree.setSelectionPath(path);
                    Rectangle pathBounds = tree.getUI().getPathBounds(tree, path);
                    if (pathBounds != null && pathBounds.contains(e.getX(), e.getY())) {
                        JPopupMenu menu = new JPopupMenu();
                        
                        if (path.getLastPathComponent() instanceof Parameter) {
                            Parameter p = (Parameter) path.getLastPathComponent();
                            Object[] ui = p.getDisplayComponents();
                            for (Object o : ui) {
                                if (o instanceof Component) menu.add((Component)o);
                                
                            }
                        }
                        
                        menu.show(tree, pathBounds.x, pathBounds.y + pathBounds.height);
                    }
                }
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(tree);
        add(scrollPane);
    }
}
