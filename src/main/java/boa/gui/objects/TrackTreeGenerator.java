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
import boa.gui.configuration.TrackTreeCellRenderer;
import boa.gui.configuration.TransparentTreeCellRenderer;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageWindowManager;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.ObjectDAO;
import java.util.ArrayList;
import utils.Utils;
/**
 *
 * @author nasique
 */
public class TrackTreeGenerator {
    MasterDAO db;
    protected StructureObjectTreeModel treeModel;
    JTree tree;
    TrackTreeController controller;
    
    public TrackTreeGenerator(MasterDAO db, TrackTreeController controller) {
        this.db = db;
        this.controller=controller;
    }
    
    public ObjectDAO getObjectDAO(String fieldName) {
        return db.getDao(fieldName);
    }
    
    public Experiment getExperiment() {
        return db.getExperiment();
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
    
    
    private int getStructureIdx() {
        if (isRootSet()) {
            Object root = treeModel.getRoot();
            if (root instanceof TrackExperimentNode) return ((TrackExperimentNode)root).structureIdx;
            if (root instanceof RootTrackNode) return ((RootTrackNode)root).structureIdx;
        }
        return -1;
    }
    
    private StructureObject getParentTrackHead() {
        Object root = treeModel.getRoot();
        if (root instanceof RootTrackNode) return ((RootTrackNode)root).getParentTrackHead();
        else return null;
    }
    
    private void generateTree(TreeNode root) {
        treeModel = new StructureObjectTreeModel(root);
        tree=new JTree(treeModel);
        //tree.setRootVisible(false);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        tree.setOpaque(false);
        tree.setCellRenderer(new TrackTreeCellRenderer());
        tree.setScrollsOnExpand(true);
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path==null) return;
                if (SwingUtilities.isRightMouseButton(e)) {
                    if (!Utils.isSelected(tree, path)) tree.setSelectionPath(path);
                    Rectangle pathBounds = tree.getUI().getPathBounds(tree, path);
                    if (pathBounds != null && pathBounds.contains(e.getX(), e.getY())) {
                        JPopupMenu menu = new JPopupMenu();
                        Object lastO = path.getLastPathComponent();
                        //logger.debug("right-click on element: {}", lastO);
                        if (lastO instanceof UIContainer) {
                            UIContainer UIC=(UIContainer)lastO;
                            addToMenu(UIC.getDisplayComponent(tree.getSelectionCount()>1), menu);
                        }
                        menu.show(tree, pathBounds.x, pathBounds.y + pathBounds.height);
                    }
                } else if (SwingUtilities.isLeftMouseButton(e) && !Utils.isCtrlOrShiftDown(e)) { 
                    if (tree.isCollapsed(path)) { // expand & select all children
                        ArrayList<TreePath> pathToSelect = new ArrayList<TreePath>();
                        Utils.expandAll(tree, path, pathToSelect);
                        Utils.addToSelectionPaths(tree, pathToSelect);
                    } else Utils.addToSelectionPaths(tree, path);
                }
                displaySelectedTracks();
            }
        });
    }
    
    private ImageObjectInterface getImageObjectInterface() {
        StructureObject parentTrackHead = getParentTrackHead();
        if (parentTrackHead==null) return null;
        return ImageWindowManagerFactory.getImageManager().getImageTrackObjectInterfaceIfExisting(parentTrackHead, getStructureIdx());
    }
    
    public void displaySelectedTracks() {
        logger.debug("display: {} selected tracks", tree.getSelectionCount());
        ImageObjectInterface i = getImageObjectInterface();
        if (i!=null) ImageWindowManagerFactory.getImageManager().displayTrackAllImages(i, false, null, null); // unselect tracks
        if (tree.getSelectionCount()>0 && i!=null) {
            //Color[] palette = Utils.generatePalette(tree.getSelectionCount(), true);
            int idx=0;
            for (TreePath p : tree.getSelectionPaths()) {
                Object lastO = p.getLastPathComponent();
                if (lastO instanceof TrackNode) {
                    //ImageWindowManagerFactory.getImageManager().displayTrack(null, true, ((TrackNode)lastO).track, ImageWindowManager.palette[idx++%ImageWindowManager.palette.length]);
                    ImageWindowManagerFactory.getImageManager().displayTrackAllImages(i, true, ((TrackNode)lastO).track, ImageWindowManager.palette[idx++%ImageWindowManager.palette.length]);
                }
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
        //o = db.getDao(o.getFieldName()).getById(o.getTrackHeadId());
        o = o.getTrackHead();
        res.add(o);
        while(o.getTimePoint()>0) {
            //o = db.getDao(o.getFieldName()).getById(o.getPrevious().getTrackHeadId());
            o = o.getPrevious().getTrackHead();
            res.add(o);
        }
        return res;
    }
    
    public ArrayList<StructureObject> getSelectedTrackHeads() {
        ArrayList<StructureObject> res = new ArrayList<StructureObject>(tree.getSelectionCount());
        for (TreePath p : tree.getSelectionPaths()) {
            if (p.getLastPathComponent() instanceof TrackNode) {
                res.add(((TrackNode)p.getLastPathComponent()).trackHead);
            }
        }
        logger.debug("getSelectedTrackHead: count: {}", res.size());
        return res;
    }
    
    public ArrayList<TrackNode> getSelectedTrackNodes() {
        ArrayList<TrackNode> res = new ArrayList<TrackNode>(tree.getSelectionCount());
        for (TreePath p : tree.getSelectionPaths()) {
            if (p.getLastPathComponent() instanceof TrackNode) {
                res.add(((TrackNode)p.getLastPathComponent()));
            }
        }
        logger.debug("getSelectedTrackNodes: count: {}", res.size());
        return res;
    }
}
