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

import dataStructure.objects.StructureObject;
import static dataStructure.objects.userInterface.StructureObjectTreeGenerator.logger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import javax.swing.tree.TreeNode;
import utils.SmallArray;

/**
 *
 * @author nasique
 */
public class ObjectNode implements TreeNode, UIContainer {
    StructureObject data;
    StructureNode parent;
    StructureNode[] children;
    int idx;
    public ObjectNode(StructureNode parent, int idx, StructureObject data) {
        this.data=data;
        this.idx=idx;
        this.parent = parent;
        int[] childrenIndicies = getGenerator().xp.getChildStructures(parent.idx);
        children = new StructureNode[childrenIndicies.length];
        for (int i = 0; i<children.length; ++i) children[i]=new StructureNode(childrenIndicies[i], this);
    }
    
    public StructureObjectTreeGenerator getGenerator() {
        return parent.getGenerator();
    }
    
    public void loadAllChildObjects(int[] pathToChildStructureIdx, int currentIdxInPath) {
        /*int pathIdx; // start from index of current structure in the path, if present
        for (pathIdx=0; pathIdx<pathToChildStructureIdx.length; ++pathIdx) {
            if (pathToChildStructureIdx[pathIdx]==parent.idx) break;
            else if (pathIdx==pathToChildStructureIdx.length-1) return;
        }*/
        int childIdx = getChildStructureIdx(pathToChildStructureIdx[currentIdxInPath]);
        if (childIdx>=0) {
            children[childIdx].getChildren();
            if (currentIdxInPath<(pathToChildStructureIdx.length-1)) for (ObjectNode o : children[childIdx].getChildren()) o.loadAllChildObjects(pathToChildStructureIdx, currentIdxInPath+1);
        } else logger.warn("could not loadAllChildObjects: structure {} not in children of structure: {} [ pathToChildStructureIdx: {} currentIdxInPath: {} ] ", pathToChildStructureIdx[currentIdxInPath], parent.idx, pathToChildStructureIdx, currentIdxInPath);
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
        return "Object: "+idx;
    }
    
    public StructureNode getChildAt(int childIndex) {
        return children[childIndex];
    }

    public int getChildCount() {
        return children.length;
    }

    public TreeNode getParent() {
        return parent;
    }

    public int getIndex(TreeNode node) {
        if (node==null) return -1;
        for (int i = 0; i<children.length; ++i) if (node.equals(children[i])) return i;
        return -1;
    }

    public boolean getAllowsChildren() {
        return true;
    }

    public boolean isLeaf() {
        return getChildCount()==0;
    }

    public Enumeration children() {
        return Collections.enumeration(Arrays.asList(children));
    }
    
}
