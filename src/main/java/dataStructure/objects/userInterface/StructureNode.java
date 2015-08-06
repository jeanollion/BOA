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
import dataStructure.objects.StructureObjectRoot;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import javax.swing.tree.TreeNode;
import utils.SmallArray;

/**
 *
 * @author nasique
 */
public class StructureNode implements TreeNode {
    ObjectNode parent;
    ObjectNode[] children;
    StructureObjectTreeGenerator generator;
    int idx;
    
    public StructureNode(StructureObjectTreeGenerator generator, int structureIdx, ObjectNode parent) {
        this.parent=parent;
        this.generator=generator;
        this.idx=structureIdx;
    }
    
    public ObjectNode[] getChildrenNode() {
        if (idx>=0) {
            if (children==null) {
                StructureObject[] data = parent.data.getChildObjects(idx);
                if (data!=null) {
                    children = new ObjectNode[children.length];
                    for (int i = 0; i<children.length; ++i) children[i] = new ObjectNode(this, i, data[i]);
                }
            }
        }
        return children;
    }
    
    public void setRootObject(StructureObjectRoot root) {
        if (idx<0) throw new IllegalArgumentException("cannot set root object to a structure that is not root");
        ObjectNode rootNode = new ObjectNode(this, 1, root);
        this.children=new ObjectNode[]{rootNode};
    }
    
    public void createChildren() {
        children=null;
        getChildrenNode();
    }
    
    public StructureObject[] getChildren() {
        return parent.data.getChildObjects(parent.getIndex(this));
    }
    
    // TreeNode implementation
    @Override public String toString() {
        if (idx==-1) return "Root Structure";
        else return generator.xp.getStructure(idx).getName();
    }
    
    public ObjectNode getChildAt(int childIndex) {
        return children[childIndex];
    }

    public int getChildCount() {
        return children.length;
    }

    public ObjectNode getParent() {
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
        if (getChildrenNode()==null) return Collections.emptyEnumeration();
        return Collections.enumeration(Arrays.asList(children));
    }
}
