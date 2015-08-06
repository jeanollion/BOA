/*
 * Copyright (C) 2015 nasique
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
package dataStructure.objects.userInterface;

import dataStructure.configuration.Experiment;
import dataStructure.objects.ObjectDAO;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.Icon;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 *
 * @author nasique
 */
public class StructureObjectTreeGenerator {
    Experiment xp;
    ObjectDAO objectDAO;
    String currentFieldName;
    int currentTimePoint;
    StructureNode currentRootStructure;
    
    protected StructureObjectTreeModel treeModel;
    protected JTree tree;
    protected JScrollPane scroll;
    
    public StructureObjectTreeGenerator() {
        tree=new JTree();
        scroll = new JScrollPane(tree);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.CONTIGUOUS_TREE_SELECTION);
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
                        
                    }
                }
            }
        });
    }
    
    public void generateTree(String fieldName, int timePoint) {
        this.currentFieldName=fieldName;
        this.currentTimePoint=timePoint;
        currentRootStructure = new StructureNode(this, -1, null);
        currentRootStructure.setRootObject(objectDAO.getRoot(currentFieldName, currentTimePoint));
        treeModel = new StructureObjectTreeModel(currentRootStructure.getChildAt(0));
        tree.setModel(treeModel);
    }
    
}
