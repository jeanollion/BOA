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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeMap;
import javax.swing.tree.TreeNode;

/**
 *
 * @author nasique
 */
public class RootTrackNode implements TreeNode {
    TrackTreeGenerator generator;
    private ArrayList<TrackNode> children;
    private TreeMap<Integer, ArrayList<StructureObject>> remainingTrackHeadsTM;
    private StructureObject parentTrackHead;
    int structureIdx;
    TrackExperimentNode parent;
    String fieldName;
    
    public RootTrackNode(TrackTreeGenerator generator, StructureObject parentTrackHead, int structureIdx) {
        this.generator = generator;
        this.parentTrackHead=parentTrackHead;
        this.structureIdx=structureIdx;
    }
    
    public RootTrackNode(TrackExperimentNode parent, String fieldName, int structureIdx) { // constructor when parent == root
        this.generator = parent.generator;
        this.parent = parent;
        this.fieldName=fieldName;
        this.structureIdx=structureIdx;
    }
    
    public StructureObject getParentTrackHead() {
        if (parentTrackHead==null) {
            if (fieldName==null) throw new RuntimeException("No track head or fieldName defined for RootTrackNode instance");
            parentTrackHead = parent.generator.objectDAO.getRoot(fieldName, 0);
        }
        return parentTrackHead;
    }
    
    public TreeMap<Integer, ArrayList<StructureObject>> getRemainingTrackHeads() {
        if (remainingTrackHeadsTM==null) {
            StructureObject[] trackHeads = generator.objectDAO.getTrackHeads(getParentTrackHead(), structureIdx);
            HashMap<Integer, ArrayList<StructureObject>> map  = new HashMap<Integer, ArrayList<StructureObject>> (trackHeads[trackHeads.length].getTimePoint()-trackHeads[0].getTimePoint()+1);
            int currentTimePoint = trackHeads[0].getTimePoint();
            int lastIdx = 0;
            int currentIdx = 1;
            while (currentIdx<trackHeads.length) {
                if (trackHeads[currentIdx].getTimePoint()>currentTimePoint) {
                    ArrayList<StructureObject> currentHeads = new ArrayList<StructureObject>(currentIdx-lastIdx);
                    for (int i = lastIdx; i<currentIdx; ++i) currentHeads.add(trackHeads[i]);
                    map.put(currentTimePoint, currentHeads);
                    lastIdx=currentIdx;
                    currentTimePoint = trackHeads[currentIdx].getTimePoint();
                }
                currentIdx++;
            }
            remainingTrackHeadsTM = new TreeMap<Integer, ArrayList<StructureObject>>(map);
        }
        return remainingTrackHeadsTM;
    }
    
    public ArrayList<TrackNode> getChildren() {
        if (children==null) {
            Entry<Integer, ArrayList<StructureObject>>  childrenObjects = getRemainingTrackHeads().firstEntry();
            remainingTrackHeadsTM.remove(childrenObjects.getKey());
            children = new ArrayList<TrackNode>(childrenObjects.getValue().size());
            for (StructureObject o : childrenObjects.getValue()) children.add(new TrackNode(this, this, o));
        }
        return children;
    }
    
    // TreeNode implementation
    public TrackNode getChildAt(int childIndex) {
        return getChildren().get(childIndex);
    }

    public int getChildCount() {
        return getChildren().size();
    }

    public TreeNode getParent() {
        return parent;
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

    @Override
    public Enumeration children() {
        return Collections.enumeration(getChildren());
    }
}
