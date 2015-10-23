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

import static boa.gui.GUI.logger;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import core.Processor;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import image.Image;
import image.ImageInteger;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

/**
 *
 * @author nasique
 */
public class StructureNode implements TreeNode, UIContainer {
    StructureNodeContainer parent; // can be either TimePointNode or ObjectNode
    ArrayList<ObjectNode> children;
    int idx;
    
    public StructureNode(int structureIdx, ObjectNode parent) {
        this.parent=parent;
        this.idx=structureIdx;
    }
    
    public StructureNode(int structureIdx, TimePointNode parent) {
        this.parent=parent;
        this.idx=structureIdx;
    }
    
    public ArrayList<ObjectNode> getChildren() {
        if (idx>=0) {
            if (children==null) {
                StructureObject parentObject = getParentObject();
                if (parentObject==null) return null;
                ArrayList<StructureObject> data = parentObject.getChildren(idx);
                if (data==null) {
                    data = getGenerator().objectDAO.getObjects(parentObject.getId(), idx);
                    parentObject.setChildObjects(data, idx);
                    if (logger.isDebugEnabled()) logger.debug("retrieving object from db: fieldName: {} timePoint: {} structure: {}, nb objects: {}", getTimePointNode().parent.fieldName, getTimePointNode().timePoint, idx, data==null?"null":data.size());
                }
                if (data!=null) {
                    children = new ArrayList<ObjectNode>(data.size());
                    for (int i = 0; i<data.size(); ++i) children.add(new ObjectNode(this, i, data.get(i)));
                }
            }
        }
        return children;
    }
    
    public StructureObject getParentObject() {
        return parent.getData();
    }
    
    public TimePointNode getTimePointNode() {
        return (parent instanceof TimePointNode) ? ((TimePointNode)parent) : ((ObjectNode)parent).parent.getTimePointNode();
    }
    
    public void createChildren() {
        children=null;
        getChildren();
    }
    
    public StructureObjectTreeGenerator getGenerator() {
        return parent.getGenerator();
    }
    
    
    
    // UIContainer implementation
    @Override public Object[] getDisplayComponent() {
        return getParentObject()==null? new Object[0]:(new StructureNodeUI(this)).getDisplayComponent();
    }
    
    // TreeNode implementation
    @Override public String toString() {
        return getGenerator().getExperiment().getStructure(idx).getName();
    }
    
    public ObjectNode getChildAt(int childIndex) {
        return getChildren().get(childIndex);
    }

    public int getChildCount() {
        return getChildren()==null?0:children.size();
    }

    public StructureNodeContainer getParent() {
        return parent;
    }

    public int getIndex(TreeNode node) {
        if (node instanceof ObjectNode) return ((ObjectNode)node).idx;
        else return -1;
    }

    public boolean getAllowsChildren() {
        return true;
    }

    public boolean isLeaf() {
        return getChildCount()==0;
    }

    public Enumeration children() {
        if (getChildren()==null) return Collections.emptyEnumeration();
        return Collections.enumeration(Arrays.asList(getChildren()));
    }
    
    public TreePath getTreePath() {
        return parent.getTreePath().pathByAddingChild(this);
    }
    
    class StructureNodeUI {
        StructureNode node;
        JMenuItem[] actions;
        JMenuItem[] openRaw;
        JMenuItem[] openSeg;
        JMenuItem[] segmentation;
        public StructureNodeUI(StructureNode node_) {
            this.node=node_;
            this.actions = new JMenuItem[3];
            JMenu segSubMenu = new JMenu("Open Segmented Image");
            actions[0] = segSubMenu;
            JMenu rawSubMenu = new JMenu("Open Raw Input Image");
            actions[1] = rawSubMenu;
            JMenu segmentationSubMenu = new JMenu("Segmentation");
            actions[2] = segmentationSubMenu;
            String[] structureNames = getGenerator().getExperiment().getStructuresAsString();
            
            openSeg=new JMenuItem[structureNames.length];
            for (int i = 0; i < openSeg.length; i++) {
                openSeg[i] = new JMenuItem(structureNames[i]);
                openSeg[i].setAction(
                    new AbstractAction(structureNames[i]) {
                        @Override
                        public void actionPerformed(ActionEvent ae) {
                            int structureIdx = getStructureIdx(ae.getActionCommand(), openRaw);
                            if (logger.isDebugEnabled()) logger.debug("opening segmented image for structure: {} of idx: {} from structure idx: {}", ae.getActionCommand(), structureIdx, getParentObject().getStructureIdx());
                            
                            int[] path = getGenerator().getExperiment().getPathToStructure(getParentObject().getStructureIdx(), structureIdx);
                            parent.loadAllChildObjects(path, 0);
                            ImageObjectInterface i = ImageWindowManagerFactory.getImageManager().getImageObjectInterface(getParentObject(), structureIdx);
                            //logger.debug("IO-Interface: {}, parent: {}, number of children: {}", i, getParentObject(), getParentObject().getChildren(structureIdx).size());
                            ImageWindowManagerFactory.getImageManager().addImage(i.generateImage(), i, true);
                        }
                    }
                );
                segSubMenu.add(openSeg[i]);
            }
            
            openRaw=new JMenuItem[structureNames.length];
            for (int i = 0; i < openRaw.length; i++) {
                openRaw[i] = new JMenuItem(structureNames[i]);
                openRaw[i].setAction(
                    new AbstractAction(structureNames[i]) {
                        @Override
                        public void actionPerformed(ActionEvent ae) {
                            if (logger.isDebugEnabled()) logger.debug("opening input image for structure: {} of idx: {}", ae.getActionCommand(), getStructureIdx(ae.getActionCommand(), openRaw));
                            int[] path = getGenerator().getExperiment().getPathToStructure(getParentObject().getStructureIdx(), getStructureIdx(ae.getActionCommand(), openRaw));
                            parent.loadAllChildObjects(path, 0);
                            ImageObjectInterface i = ImageWindowManagerFactory.getImageManager().getImageObjectInterface(getParentObject(), getStructureIdx(ae.getActionCommand(), openRaw));
                            ImageWindowManagerFactory.getImageManager().addImage(i.generateRawImage(getStructureIdx(ae.getActionCommand(), openRaw)), i, true);
                        }
                    }
                );
                rawSubMenu.add(openRaw[i]);
            }
            
            segmentation=new JMenuItem[structureNames.length];
            for (int i = 0; i < segmentation.length; i++) {
                segmentation[i] = new JMenuItem(structureNames[i]);
                segmentation[i].setAction(
                    new AbstractAction(structureNames[i]) {
                        @Override
                        public void actionPerformed(ActionEvent ae) {
                            if (logger.isDebugEnabled()) logger.debug("test segmentation for structure: {} of idx: {} from structure idx: {}", ae.getActionCommand(), getStructureIdx(ae.getActionCommand(), openRaw), getParentObject().getStructureIdx());
                            int directParentIdx = getGenerator().getExperiment().getStructure(getStructureIdx(ae.getActionCommand(), openRaw)).getParentStructure();
                            if (directParentIdx!=getParentObject().getStructureIdx()) {
                                int[] path = getGenerator().getExperiment().getPathToStructure(getParentObject().getStructureIdx(), directParentIdx);
                                parent.loadAllChildObjects(path, 0);
                            }
                            
                            Processor.processChildren(getStructureIdx(ae.getActionCommand(), openRaw), getParentObject(), getGenerator().objectDAO, true, null);
                            
                            //TODO: process child structures...
                            
                            // actualiser l'arbre a partir du noeud
                            //if (directParentIdx==getParentObject().getStructureIdx()) parent.resetData();
                            children=null;
                            getGenerator().getModel().nodeStructureChanged(node);
                            ImageWindowManagerFactory.getImageManager().resetImageObjectInterface(getParentObject(), getStructureIdx(ae.getActionCommand(), openRaw));
                            
                        }
                    }
                );
                segmentationSubMenu.add(segmentation[i]);
            }
            
        }
        public Object[] getDisplayComponent() {return actions;}
        private int getStructureIdx(String name, JMenuItem[] actions) {
            for (int i = 0; i<actions.length; ++i) if (actions[i].getActionCommand().equals(name)) return i;
            return -1;
        }
    }
}
