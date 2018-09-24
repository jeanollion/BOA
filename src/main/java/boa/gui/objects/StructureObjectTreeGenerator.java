/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.gui.objects;

import static boa.gui.configuration.ConfigurationTreeGenerator.addToMenu;
import static boa.ui.GUI.logger;
import boa.gui.configuration.TransparentTreeCellRenderer;
import boa.configuration.experiment.Experiment;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.ObjectDAO;
import boa.data_structure.StructureObject;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import boa.utils.Utils;

/**
 *
 * @author nasique
 */
public class StructureObjectTreeGenerator {
    MasterDAO db;
    protected StructureObjectTreeModel treeModel;
    protected JTree tree;
    protected ExperimentNode experimentNode;
    boolean updateRoiDisplayWhenSelectionChange = true;
    
    public StructureObjectTreeGenerator(MasterDAO db) {
        this.db = db;
        this.experimentNode=new ExperimentNode(this);
        
    }
    
    public boolean isUpdateRoiDisplayWhenSelectionChange() {
        return updateRoiDisplayWhenSelectionChange;
    }

    public void setUpdateRoiDisplayWhenSelectionChange(boolean updateRoiDisplayWhenSelectionChange) {
        this.updateRoiDisplayWhenSelectionChange = updateRoiDisplayWhenSelectionChange;
    }
    
    private void generateTree() {
        treeModel = new StructureObjectTreeModel(experimentNode);
        tree=new JTree(treeModel);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        tree.setOpaque(false);
        tree.setCellRenderer(new TransparentTreeCellRenderer());
        tree.setScrollsOnExpand(true);
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
                        //logger.debug("right-click on element: {}", lastO);
                        if (lastO instanceof UIContainer) {
                            UIContainer UIC=(UIContainer)lastO;
                            addToMenu(UIC.getDisplayComponent(false), menu);
                        }
                        menu.show(tree, pathBounds.x, pathBounds.y + pathBounds.height);
                    }
                }
            }
        });
    }
    
    public Experiment getExperiment() {return db.getExperiment();}
    
    public ObjectDAO getObjectDAO(String fieldName) {return db.getDao(fieldName);}
    
    public JTree getTree() {
        if (tree==null) generateTree();
        return tree;
    }
    
    public void collapseAll() {
        if (treeModel!=null) treeModel.reload();
    }
    
    public void reload(StructureObject o) {
        reload(getStructureNode(o));
    }
    
    public void reload(TreeNode node) {
        if (node==null) return;
        if (treeModel!=null) {
            TreePath p = Utils.getTreePath(node);
            boolean exp  = !tree.isCollapsed(p);
            treeModel.reload(node);
            if (exp) tree.expandPath(p);
        }
    }
    
    public void selectObject(StructureObject object, boolean addToSelection) {
        if (object==null && !addToSelection) tree.setSelectionRow(-1);
        else {
            if (object==null) return;
            if (addToSelection) Utils.addToSelectionPaths(tree, getObjectTreePath(object));
            else tree.setSelectionPath(getObjectTreePath(object));
        }
    }
    public void selectObjects(List<StructureObject> objects, boolean addToSelection) {
        if (objects==null) {
            if (!addToSelection) tree.setSelectionRow(-1);
            return;
        }
        List<TreePath> list = new ArrayList<TreePath>(objects.size());
        for (StructureObject o : objects) list.add(getObjectTreePath(o));
        if (!addToSelection) tree.setSelectionRow(-1);
        Utils.addToSelectionPaths(tree, list);
    }
    
    public void unSelectObject(StructureObject object) {
        if (object==null) return;
        Utils.addToSelectionPaths(tree, getObjectTreePath(object));
    }
    public void unSelectObjects(List<StructureObject> objects) {
        //logger.debug("unselect {} objects, sel before: {}", objects.size(), tree.getSelectionCount());
        List<TreePath> list = new ArrayList<TreePath>(objects.size());
        for (StructureObject o : objects) list.add(getObjectTreePath(o));
        Utils.removeFromSelectionPaths(tree, list);
        //logger.debug("unselect {} objects, sel after: {}", objects.size(), tree.getSelectionCount());
    }
    public void unselectAllObjects() {
        tree.setSelectionRow(-1);
    }
    
    public TreePath getObjectTreePath(StructureObject object) {
        ArrayList<TreeNode> path = new ArrayList<TreeNode>(); 
        path.add(experimentNode);
        FieldNode f = experimentNode.getFieldNode(object.getPositionName());
        path.add(f);
        TimePointNode t = f.getChildren()[object.getFrame()];
        path.add(t);
        List<StructureObject> objectPath = getObjectPath(object);
        StructureNodeContainer lastStructureContainer = t;
        for (StructureObject o : objectPath) {
            StructureNode s = lastStructureContainer.getStructureNode(o.getStructureIdx());
            path.add(s);
            if (s.getChildren().size()<=o.getIdx()) logger.error("getObjectTreePath error:: object idx too hight: object to find: {}, current parent: {}, current child: {}", object, s, o);
            ObjectNode on = s.getChild(o);
            path.add(on);
            lastStructureContainer=on;
        }
        return new TreePath(path.toArray(new TreeNode[path.size()]));
    }
    
    
    public ObjectNode getObjectNode(StructureObject object) {
        FieldNode f = experimentNode.getFieldNode(object.getPositionName());
        TimePointNode t = f.getChildren()[object.getFrame()];
         List<StructureObject> objectPath = getObjectPath(object);
        StructureNodeContainer lastStructureContainer = t;
        for (StructureObject o : objectPath) {
            StructureNode s = lastStructureContainer.getStructureNode(o.getStructureIdx());
            ObjectNode on = s.getChild(o);
            lastStructureContainer=on;
        }
        return (ObjectNode)lastStructureContainer;
    }
    
    public StructureNode getStructureNode(StructureObject object) {
        FieldNode f = experimentNode.getFieldNode(object.getPositionName());
        TimePointNode t = f.getChildren()[object.getFrame()];
        List<StructureObject> objectPath = getObjectPath(object);
        StructureNodeContainer lastStructureContainer = t;
        for (StructureObject o : objectPath) {
            StructureNode s = lastStructureContainer.getStructureNode(o.getStructureIdx());
            if (o==object) return s;
            ObjectNode on = s.getChild(o);
            if (on==null) return null;
            lastStructureContainer=on;
        }
        return null;
    }
    
    private List<StructureObject> getObjectPath(StructureObject object) {
        ArrayList<StructureObject> res = new ArrayList<StructureObject>();
        res.add(object);
        if (object.isRoot()) return res;
        while(!object.getParent().isRoot()) {
            res.add(object.getParent());
            object=object.getParent();
        }
        return Utils.reverseOrder(res);
    }
    
    /*public void setCollapsedNode(TreePath path, boolean collapsed) {
        if (collapsed) tree.collapsePath(path);
        else tree.expandPath(path);
    }*/
    
    public StructureObjectTreeModel getModel() {
        return this.treeModel;
    }
    
    public ArrayList<StructureObject> getSelectedObjects(boolean onlyFromSameStructureIdx, int... structureIdx) {
        
        ArrayList<StructureObject> res = new ArrayList<StructureObject>(tree.getSelectionCount());
        if (tree.getSelectionCount()==0) return res;
        int stIdx = -1;
        if (structureIdx.length>0) stIdx = structureIdx[0];
        for (TreePath p : tree.getSelectionPaths()) {
            if (p.getLastPathComponent() instanceof ObjectNode) {
                StructureObject o = ((ObjectNode)p.getLastPathComponent()).data;
                if (onlyFromSameStructureIdx) {
                    if (stIdx==-1) {
                        stIdx = o.getStructureIdx();
                        res.add(o);
                    } else if (o.getStructureIdx()==stIdx) res.add(o);
                } else res.add(o);
            }
        }
        return res;
    }
    /**
     * 
     * @return a list of selected structureObject that have the same parent & structrueIdx as the first selected object of the tree
     */
    public ArrayList<StructureObject> getSelectedObjectsFromSameParent() {
        ArrayList<StructureObject> res = new ArrayList<StructureObject>(tree.getSelectionCount());
        StructureNode parent = null;
        int structureIdx=-1;
        for (TreePath p : tree.getSelectionPaths()) {
            if (p.getLastPathComponent() instanceof ObjectNode) {
                if (parent==null) {
                    parent = (StructureNode) p.getPathComponent(p.getPathCount()-2);
                    StructureObject o = ((ObjectNode)p.getLastPathComponent()).data;
                    res.add(o);
                    structureIdx = o.getStructureIdx();
                } else if (p.getPathComponent(p.getPathCount()-2).equals(parent)) {
                    StructureObject o = ((ObjectNode)p.getLastPathComponent()).data;
                    if (o.getStructureIdx()==structureIdx) res.add(o);
                }
            }
        }
        return res;
    }
    public StructureObject getFisrtSelectedObject() {
        for (TreePath p : tree.getSelectionPaths()) {
            if (p.getLastPathComponent() instanceof ObjectNode) {
                return ((ObjectNode)p.getLastPathComponent()).data;
            }
        }
        return null;
    }
}
