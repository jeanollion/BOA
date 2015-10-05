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
package boa.gui.objects;

import static boa.gui.configuration.ConfigurationTreeGenerator.addToMenu;
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
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import static boa.gui.GUI.logger;
import boa.gui.configuration.TransparentTreeCellRenderer;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import java.awt.Color;
import java.util.ArrayList;
import javax.swing.tree.DefaultTreeModel;
import utils.Utils;
/**
 *
 * @author nasique
 */
public class TrackTreeGenerator {
    ExperimentDAO xpDAO;
    ObjectDAO objectDAO;
    protected StructureObjectTreeModel treeModel;
    JTree tree;
    TrackTreeController controller;
    
    public TrackTreeGenerator(ObjectDAO dao, ExperimentDAO xpDAO, TrackTreeController controller) {
        this.objectDAO=dao;
        this.xpDAO=xpDAO;
        this.controller=controller;
    }
    
    public StructureObject getSelectedTrack() {
        if (hasSelection() && tree.getSelectionPath().getLastPathComponent() instanceof TrackNode) return ((TrackNode)tree.getSelectionPath().getLastPathComponent()).trackHead;
        else return null;
    }
    
    public boolean hasSelection() {return tree!=null?tree.getSelectionCount()>0:false;}
    
    public boolean hasSingleSelection() {return tree!=null?tree.getSelectionCount()==1:false;}
    
    public boolean isRootSet() {return treeModel!=null && treeModel.getRoot()!=null;}
    
    public void clearTree() {
        tree=null;
        treeModel=null;
    }
    
    public void setRootParentTrack(boolean force, int structureIdx) {
        if (force || !isRootSet()) generateTree(new TrackExperimentNode(this, structureIdx));
    }
    
    public JTree getTree() {return tree;}
    
    public void setParentTrack(StructureObject parentTrack, int structureIdx) {
        generateTree(new RootTrackNode(this, parentTrack, structureIdx));
    }
    
    
    private int getStructure() {
        if (isRootSet()) {
            Object root = treeModel.getRoot();
            if (root instanceof TrackExperimentNode) return ((TrackExperimentNode)root).structureIdx;
            if (root instanceof RootTrackNode) return ((RootTrackNode)root).structureIdx;
        }
        return -1;
    }
    private void generateTree(TreeNode root) {
        treeModel = new StructureObjectTreeModel(root);
        tree=new JTree(treeModel);
        //tree.setRootVisible(false);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        tree.setOpaque(false);
        DefaultTreeCellRenderer renderer = new TransparentTreeCellRenderer();
        Icon personIcon = null;
        renderer.setLeafIcon(personIcon);
        renderer.setClosedIcon(personIcon);
        renderer.setOpenIcon(personIcon);
        tree.setCellRenderer(renderer);
        tree.setScrollsOnExpand(true);
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (SwingUtilities.isRightMouseButton(e)) {
                    tree.setSelectionPath(path);
                    // display arrows?
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
                displaySelectedTracks();
            }
        });
    }
    
    public void displaySelectedTracks() {
        if (logger.isTraceEnabled()) logger.trace("display: {} selected tracks", tree.getSelectionCount());
        ImageWindowManagerFactory.getImageManager().displayTrack(null, false, null, null); // unselect tracks
        if (tree.getSelectionCount()==0) return;
        Color[] palette = Utils.generatePalette(tree.getSelectionCount(), true);
        int idx=0;
        for (TreePath p : tree.getSelectionPaths()) {
            Object lastO = p.getLastPathComponent();
            if (lastO instanceof TrackNode) {
                ImageWindowManagerFactory.getImageManager().displayTrack(null, true, ((TrackNode)lastO).track, palette[idx++]);
            }
        }
    }
    
    public void selectObject(StructureObject object) {
        if (object==null) {
            tree.setSelectionRow(-1);
            return;
        }
        ArrayList<TreeNode> path = new ArrayList<TreeNode>(); 
        RootTrackNode root = (RootTrackNode)treeModel.getRoot();
        path.add(root);
        ArrayList<StructureObject> objectPath = getObjectPath(object);
        TrackNode t = root.getChild(objectPath.get(objectPath.size()-1));
        path.add(t);
        for (int i = objectPath.size()-2; i>=0; --i) {
            t=t.getChild(objectPath.get(i));
            path.add(t);
        }
        tree.setSelectionPath(new TreePath(path.toArray(new TreeNode[path.size()])));
    }
    
    private ArrayList<StructureObject> getObjectPath(StructureObject o) {
        ArrayList<StructureObject> res = new ArrayList<StructureObject>();
        o = objectDAO.getObject(o.getTrackHeadId());
        res.add(o);
        while(o.getTimePoint()>0) {
            o = objectDAO.getObject(o.getPrevious().getTrackHeadId());
            res.add(o);
        }
        return res;
    }
}
