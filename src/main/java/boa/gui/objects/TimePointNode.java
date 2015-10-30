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
import dataStructure.objects.StructureObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

/**
 *
 * @author nasique
 */
public class TimePointNode implements TreeNode, UIContainer, StructureNodeContainer {
    FieldNode parent;
    int timePoint;
    private StructureNode[] children;
    private StructureObject data;
    
    public TimePointNode(FieldNode parent, int timePoint) {
        this.timePoint=timePoint;
        this.parent=parent;
    }
    
    public StructureObjectTreeGenerator getGenerator() {
        return parent.getGenerator();
    }
    
    public StructureObject getData() {
        if (data==null) {
            data = getGenerator().getObjectDAO().getRoot(parent.fieldName, timePoint);
            logger.debug("Time Point: {} retrieving root object from db: {}", timePoint, data);
        }
        return data;
    }
    
    public void resetData() {data=null;}
    
    public StructureNode[] getChildren() {
        if (children==null) {
            int[] childrenIndicies = getGenerator().getExperiment().getChildStructures(-1);
            children = new StructureNode[childrenIndicies.length];
            for (int i = 0; i<children.length; ++i) children[i]=new StructureNode(childrenIndicies[i], this);
        }
        return children;
    }
    
    public StructureNode getStructureNode(int structureIdx) {
        for (StructureNode s : getChildren()) {
            logger.trace("getStructureNode: current structureIdx: {} asked: {}", s.idx, structureIdx);
            if (s.idx==structureIdx) return s;
        }
        return null;
    }
    
    public void loadAllChildObjects(int[] pathToChildStructureIdx, int currentIdxInPath) {
        if (pathToChildStructureIdx.length<=1) return;
        int childIdx = getChildStructureIdx(pathToChildStructureIdx[0]);
        if (childIdx>=0) for (ObjectNode o : children[childIdx].getChildren()) o.loadAllChildObjects(pathToChildStructureIdx, 1);
        else logger.warn("could not loadAllChildObjects: structure {} not in children [ pathToChildStructureIdx: {} from root object ] ", pathToChildStructureIdx[0], pathToChildStructureIdx);
    }
    
    public int getChildStructureIdx(int structureIdx) {
        for (int i = 0; i<children.length; ++i) if (children[i].idx==structureIdx) return i;
        return -1;
    }
    
    // UIContainer implementation
    @Override public Object[] getDisplayComponent() {
        return new Object[0];
    }
    
    // TreeNode implementation
    @Override public String toString() {
        return "Time Point: "+timePoint;
    }
    
    public StructureNode getChildAt(int childIndex) {
        return getChildren()[childIndex];
    }

    public int getChildCount() {
        return getChildren().length;
    }

    public TreeNode getParent() {
        return parent;
    }

    public int getIndex(TreeNode node) {
        if (node==null) return -1;
        for (int i = 0; i<getChildren().length; ++i) if (node.equals(children[i])) return i;
        return -1;
    }

    public boolean getAllowsChildren() {
        return true;
    }

    public boolean isLeaf() {
        return getChildCount()==0;
    }

    public Enumeration children() {
        return Collections.enumeration(Arrays.asList(getChildren()));
    }
    
    public TreePath getTreePath() {
        TreeNode[] path = new TreeNode[3];
        path[path.length-1]=this;
        path[path.length-2]=parent;
        path[path.length-3]=parent.parent;
        return new TreePath(path);
    }
}
