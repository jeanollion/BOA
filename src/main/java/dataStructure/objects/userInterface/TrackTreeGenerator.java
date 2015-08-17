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

import static configuration.userInterface.ConfigurationTree.addToMenu;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.ExperimentDAO;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;
import de.caluga.morphium.Morphium;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.Icon;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author nasique
 */
public class TrackTreeGenerator {
    public final static Logger logger = LoggerFactory.getLogger(TrackTreeGenerator.class);
    ExperimentDAO xpDAO;
    ObjectDAO objectDAO;
    Experiment xp;
    protected StructureObjectTreeModel treeModel;
    JTree tree;

    public TrackTreeGenerator(ObjectDAO dao, ExperimentDAO xpDAO, StructureObject parentTrack) {
        this.objectDAO=dao;
        this.xpDAO=xpDAO;
        xp = xpDAO.getExperiment();
        generateTree(new RootTrackNode(this, parentTrack));
    }
    
    public TrackTreeGenerator(ObjectDAO dao, ExperimentDAO xpDAO) {
        this.objectDAO=dao;
        this.xpDAO=xpDAO;
        xp = xpDAO.getExperiment();
        generateTree(new TrackExperimentNode(this));
    }
    
    private void generateTree(TreeNode root) {
        treeModel = new StructureObjectTreeModel(root);
        tree=new JTree(treeModel);
        tree.setRootVisible(false);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
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
                        logger.debug("right-click on element: {}", lastO);
                        if (lastO instanceof UIContainer) {
                            UIContainer UIC=(UIContainer)lastO;
                            addToMenu(UIC.getDisplayComponent(), menu);
                        }
                        menu.show(tree, pathBounds.x, pathBounds.y + pathBounds.height);
                    }
                }
            }
        });
    }
}
