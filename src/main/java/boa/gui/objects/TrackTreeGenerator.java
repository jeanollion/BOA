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

import boa.gui.GUI;
import static boa.gui.configuration.ConfigurationTreeGenerator.addToMenu;
import dataStructure.configuration.Experiment;
import dataStructure.objects.StructureObject;
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
import boa.gui.selection.SelectionUtils;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.Selection;
import dataStructure.objects.StructureObjectUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.tree.MutableTreeNode;
import utils.HashMapGetCreate;
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
    final protected HashMapGetCreate<String, Set<StructureObject>> highlightedObjects = new HashMapGetCreate<>(new HashMapGetCreate.SetFactory<>());
    final protected Set<String> highlightedPositions = new HashSet<>();
    public TrackTreeGenerator(MasterDAO db, TrackTreeController controller) {
        this.db = db;
        this.controller=controller;
    }
    public void setEnabled(boolean enabled) {
        if (tree!=null) tree.setEnabled(enabled);
    }
    public ObjectDAO getObjectDAO(String fieldName) {
        return db.getDao(fieldName);
    }
    
    public Experiment getExperiment() {
        return db.getExperiment();
    }

    public Set<StructureObject> getHighlightedObjects(String position) {
        if (!highlightedObjects.containsKey(position)) {
            if (GUI.getInstance()==null) return Collections.EMPTY_SET;     
            for (Selection s: GUI.getInstance().getSelections()) {
                if (s.isHighlightingTracks() && db.getExperiment().isChildOf(getStructureIdx(), s.getStructureIdx())) {
                    List<StructureObject> parents = SelectionUtils.getParentTrackHeads(s, position, getStructureIdx(), db);
                    logger.debug("highlight: parents for sel: {} structure: {}, eg:{}, tot: {}", s.getName(), getStructureIdx(), parents.isEmpty()?null:parents.get(0), parents.size());
                    if (!parents.isEmpty()) highlightedObjects.getAndCreateIfNecessary(position).addAll(parents);
                }
            }
            logger.debug("Structure: {}, position: {}, #{} highlighted objects", getStructureIdx(), position, highlightedObjects.getAndCreateIfNecessary(position).size());
        }
        return highlightedObjects.getAndCreateIfNecessary(position);
    }

    public void resetHighlightedObjects() {
        highlightedObjects.clear();
        highlightedPositions.clear();
        for (Selection s: GUI.getInstance().getSelections()) if (s.isHighlightingTracks()) highlightedPositions.addAll(s.getAllPositions());
    }
    
    public boolean isHighlightedPosition(String position) {
        return highlightedPositions.contains(position);
    }
    public StructureObject getSelectedTrack() {
        if (hasSelection() && tree.getSelectionPath().getLastPathComponent() instanceof TrackNode) return ((TrackNode)tree.getSelectionPath().getLastPathComponent()).trackHead;
        else return null;
    }
    public String getSelectedPosition() {
        if (hasSelection()) {
            if (treeModel.getRoot() instanceof RootTrackNode) return ((RootTrackNode)tree.getSelectionPath().getPathComponent(0)).position;
            else if (treeModel.getRoot() instanceof TrackExperimentNode) return ((RootTrackNode)tree.getSelectionPath().getPathComponent(1)).position;
        }
        return null;
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
        if (parentTrack==null) return;
        generateTree(new RootTrackNode(this, parentTrack, structureIdx));
        if (tree!=null) {
            tree.updateUI();
        }
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
        //logger.debug("get parent trackhead: root: {}", root.getClass().getSimpleName());
        if (root instanceof RootTrackNode) return ((RootTrackNode)root).getParentTrackHead();
        else if (root instanceof TrackExperimentNode && tree.getSelectionCount()==1) {
            List<StructureObject> al = getSelectedTrackHeads();
            if (!al.isEmpty()) return getSelectedTrackHeads().get(0);
            else return null;
        }
        else return null;
    }
    
    private void generateTree(TreeNode root) {
        treeModel = new StructureObjectTreeModel(root);
        tree=new JTree(treeModel);
        if (root instanceof TrackExperimentNode) tree.setRootVisible(false);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        tree.setOpaque(false);
        tree.setCellRenderer(new TrackTreeCellRenderer(this));
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
                        //Utils.addToSelectionPaths(tree, pathToSelect);
                    } //else Utils.addToSelectionPaths(tree, path);
                }
            }
        });
    }
    
    /*private ImageObjectInterface getImageObjectInterface() {
        StructureObject parentTrackHead = getParentTrackHead();
        if (parentTrackHead==null) return null;
        return ImageWindowManagerFactory.getImageManager().getImageTrackObjectInterfaceIfExisting(parentTrackHead, getStructureIdx());
    }
    
    public void displaySelectedTracks() {
        logger.debug("display: {} selected tracks", tree.getSelectionCount());
        ImageObjectInterface i = getImageObjectInterface();
        if (i!=null) ImageWindowManagerFactory.getImageManager().displayTrackAllImages(i, false, null, null, false); // unselect tracks
        if (tree.getSelectionCount()>0 && i!=null) {
            //Color[] palette = Utils.generatePalette(tree.getSelectionCount(), true);
            int idx=0;
            for (TreePath p : tree.getSelectionPaths()) {
                Object lastO = p.getLastPathComponent();
                if (lastO instanceof TrackNode) {
                    ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
                    iwm.displayTrackAllImages(i, true, i.pairWithOffset(StructureObjectUtils.extendTrack(((TrackNode)lastO).track)), ImageWindowManager.getColor(), false);
                }
            }
        }
    }*/
    public void deleteSelectedTracks() {
        List<StructureObject> selectedTrackHeads = getSelectedTrackHeads();
        for (StructureObject s : selectedTrackHeads) deleteTrack(s);
    }
    public void deleteTrack(StructureObject trackHead) {
        List<StructureObject> track = trackHead.getDAO().getTrack(trackHead);
        trackHead.getDAO().delete(track, true, true, true);
        
        TreePath  p = getTreePath(trackHead);
        if (p!=null) {
            treeModel.removeNodeFromParent((MutableTreeNode)p.getLastPathComponent());
            if (p.getPathCount()>=2 ) treeModel.nodeChanged((TreeNode)p.getPathComponent(p.getPathCount()-2));
        }
        
        // reload object tree
        //for (StructureObject t : track) this.controller.objectGenerator.reload(t);
        
    }

    
    public void selectTracks(Collection<StructureObject> trackHeads, boolean addToSelection) {
        if (!addToSelection) tree.setSelectionRow(-1);
        if (trackHeads==null) return;
        List<TreePath> list = new ArrayList<TreePath>(trackHeads.size());
        for (StructureObject o : trackHeads) {
            TreePath  p = getTreePath(o);
            if (p!=null) list.add(p);
        }
        Utils.addToSelectionPaths(tree, list);
    }
    
    public void deselectTracks(Collection<StructureObject> trackHeads) {
        if (trackHeads==null) return;
        List<TreePath> list = new ArrayList<TreePath>(trackHeads.size());
        for (StructureObject o : trackHeads) {
            TreePath  p = getTreePath(o);
            if (p!=null) list.add(p);
        }
        Utils.removeFromSelectionPaths(tree, list);
    }
    
    public void deselectAllTracks () {
        if (tree!=null) tree.setSelectionRow(-1);
    }

    public TreePath getTreePath(StructureObject object) {
        ArrayList<TreeNode> path = new ArrayList<TreeNode>(); 
        final RootTrackNode root;
        if (treeModel.getRoot() instanceof RootTrackNode) root = (RootTrackNode)treeModel.getRoot();
        else if (treeModel.getRoot() instanceof TrackExperimentNode) {
            TrackExperimentNode ten = (TrackExperimentNode) treeModel.getRoot();
            path.add(ten);
            root = ten.getRootNodeOf(object);
        } else throw new RuntimeException("Invalid root");
        
        path.add(root);
        if (!object.isRoot()) {
            ArrayList<StructureObject> objectPathIndices = getObjectPath(object);
            TrackNode t = root.getChild(objectPathIndices.get(objectPathIndices.size()-1));
            if (t==null) {
                logger.debug("object: {} was not found in tree, last element found: {}", object, null);
                return null;
            }
            path.add(t);
            for (int i = objectPathIndices.size()-2; i>=0; --i) {
                t=t.getChild(objectPathIndices.get(i));
                if (t==null) {
                    logger.debug("object: {} was not found in tree, last element found: {}", object, objectPathIndices.get(i+1));
                    return null;
                }
                path.add(t);
            }
        }
        return new TreePath(path.toArray(new TreeNode[path.size()]));
    }
    
    private ArrayList<StructureObject> getObjectPath(StructureObject o) {
        ArrayList<StructureObject> res = new ArrayList<StructureObject>();
        //o = db.getDao(o.getFieldName()).getById(o.getTrackHeadId());
        o = o.getTrackHead();
        res.add(o);
        while(o.getFrame()>0 && o.getPrevious()!=null) {
            //o = db.getDao(o.getFieldName()).getById(o.getPrevious().getTrackHeadId());
            o = o.getPrevious().getTrackHead();
            res.add(o);
        }
        return res;
    }
    
    public List<StructureObject> getSelectedTrackHeads() {
        int count = tree.getSelectionCount();
        ArrayList<StructureObject> res = new ArrayList<StructureObject>(count);
        if (count==0) return res;
        for (TreePath p : tree.getSelectionPaths()) {
            if (p.getLastPathComponent() instanceof TrackNode) {
                res.add(((TrackNode)p.getLastPathComponent()).trackHead);
            }
        }
        logger.debug("getSelectedTrackHead: count: {}", res.size());
        return res;
    }
    
    
    public List<TrackNode> getSelectedTrackNodes() {
        if (tree==null) return Collections.EMPTY_LIST;
        int sel = tree.getSelectionCount();
        if (sel==0) return Collections.EMPTY_LIST;
        ArrayList<TrackNode> res = new ArrayList<TrackNode>(sel);
        for (TreePath p : tree.getSelectionPaths()) {
            if (p.getLastPathComponent() instanceof TrackNode) {
                res.add(((TrackNode)p.getLastPathComponent()));
            }
        }
        logger.debug("getSelectedTrackNodes: count: {}", res.size());
        return res;
    }
    
    public List<List<StructureObject>> getSelectedTracks(boolean extended) {
        List<TrackNode> nodes = getSelectedTrackNodes();
        List<List<StructureObject>> res = new ArrayList<List<StructureObject>>(nodes.size());
        for (TrackNode n : nodes) {
            if (extended) res.add(StructureObjectUtils.extendTrack(n.getTrack()));
            else res.add(n.getTrack()); 
        }
        return res;
    }
}
