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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import javax.swing.tree.TreeNode;

/**
 *
 * @author nasique
 */
public class TrackExperimentNode implements TreeNode, UIContainer {
    protected final TrackTreeGenerator generator;
    ArrayList<RootTrackNode> children;
    int structureIdx; 
    public TrackExperimentNode(TrackTreeGenerator generator, int structureIdx) {
        this.generator=generator;
        this.structureIdx=structureIdx;
        getChildren();
    }
    
    public TrackTreeGenerator getGenerator() {
        return generator;
    }
    
    public ArrayList<RootTrackNode> getChildren() {
        if (children==null) {
            String[] fieldNames = generator.getExperiment().getFieldsAsString();
            children= new ArrayList<RootTrackNode>(fieldNames.length);
            for (String fieldName : fieldNames) children.add(new RootTrackNode(this, fieldName, structureIdx));
        }
        return children;
    }
    
    // UIContainer implementation
    @Override public Object[] getDisplayComponent(boolean multipleSelection) {
        return new Object[0];
    }
    
    // TreeNode implementation
    @Override public String toString() {
        return generator.getExperiment().getName();
    }
    
    public RootTrackNode getChildAt(int childIndex) {
        return getChildren().get(childIndex);
    }

    public int getChildCount() {
        return getChildren().size();
    }

    public TreeNode getParent() {
        return null;
    }

    public int getIndex(TreeNode node) {
        return getChildren().indexOf(node);
    }

    public boolean getAllowsChildren() {
        return true;
    }

    public boolean isLeaf() {
        return getChildCount()==0;
    }

    public Enumeration children() {
        return Collections.enumeration(getChildren());
    }
}
