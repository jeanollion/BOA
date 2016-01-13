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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
    private TreeMap<Integer, List<StructureObject>> remainingTrackHeadsTM;
    private StructureObject parentTrackHead;
    int structureIdx;
    TrackExperimentNode parent;
    String fieldName;
    Boolean containsErrors;
    public RootTrackNode(TrackTreeGenerator generator, StructureObject parentTrackHead, int structureIdx) {
        this.generator = generator;
        this.parentTrackHead=parentTrackHead;
        this.structureIdx=structureIdx;
        this.fieldName=parentTrackHead.getFieldName();
    }
    
    public RootTrackNode(TrackExperimentNode parent, String fieldName, int structureIdx) { // constructor when parent == root
        this.generator = parent.generator;
        this.parent = parent;
        this.fieldName=fieldName;
        this.structureIdx=structureIdx;
        logger.trace("creating root track node for field: {} structure: {}", fieldName, structureIdx);
    }
    
    public boolean containsError() {
        if (containsErrors==null) {
            if (children==null) return false; // lazy-loading
            for (TrackNode t : getChildren()) {
                if (t.containsError()) {
                    containsErrors=true;
                    break;
                }
            }
        }
        if (containsErrors==null) return false;
        return containsErrors;
    }
    
    public StructureObject getParentTrackHead() {
        if (parentTrackHead==null) {
            if (fieldName==null) {
                logger.warn("No track head or fieldName defined for RootTrackNode instance");
                return null;
            }
            parentTrackHead = parent.generator.getObjectDAO(fieldName).getRoot(0);
            if (parentTrackHead!=null) logger.trace("parentTrackHead id:"+parentTrackHead.getId());
        }
        return parentTrackHead;
    }
    
    public TreeMap<Integer, List<StructureObject>> getRemainingTrackHeads() {
        if (remainingTrackHeadsTM==null) {
            ArrayList<StructureObject> trackHeads = generator.getObjectDAO(fieldName).getTrackHeads(getParentTrackHead(), structureIdx);
            remainingTrackHeadsTM = new TreeMap<Integer, List<StructureObject>>();
            if (trackHeads.isEmpty()) {
                logger.debug("structure: {} no trackHeads found", structureIdx);
            } else {
                //logger.debug("structure: {} nb trackHeads found: {}", structureIdx, trackHeads.size());
                HashMap<Integer, List<StructureObject>> map  = new HashMap<Integer, List<StructureObject>> (trackHeads.get(trackHeads.size()-1).getTimePoint()-trackHeads.get(0).getTimePoint()+1);
                int currentTimePoint = trackHeads.get(0).getTimePoint();
                int lastIdx = 0;
                int currentIdx = 1;
                while (currentIdx<trackHeads.size()) {
                    if (trackHeads.get(currentIdx).getTimePoint()>currentTimePoint) {
                        //ArrayList<StructureObject> currentHeads = new ArrayList<StructureObject>(currentIdx-lastIdx);
                        //for (int i = lastIdx; i<currentIdx; ++i) currentHeads.add(trackHeads.get(i));
                        map.put(currentTimePoint, new ArrayList<StructureObject>(trackHeads.subList(lastIdx, currentIdx)));
                        lastIdx=currentIdx;
                        currentTimePoint = trackHeads.get(currentIdx).getTimePoint();
                    }
                    currentIdx++;
                }
                // put last portion in map:
                map.put(currentTimePoint, trackHeads.subList(lastIdx, currentIdx));

                remainingTrackHeadsTM = new TreeMap<Integer, List<StructureObject>>(map);
                /*if (logger.isTraceEnabled()) {
                    logger.trace("number of trackHeads found: {} number of distinct timePoints: {}", trackHeads.size(), map.size());
                    for (Entry<Integer, List<StructureObject>> e : remainingTrackHeadsTM.entrySet()) logger.trace("time point: {}, number of trackHeads {}, first: {}, last: {}", e.getKey(), e.getValue().size(), e.getValue().get(0).getTimePoint(), e.getValue().get(e.getValue().size()-1).getTimePoint());
                }*/
                
            }
        }
        return remainingTrackHeadsTM;
    }
    
    public ArrayList<TrackNode> getChildren() {
        if (children==null) {
            children = new ArrayList<TrackNode>();
            Iterator<Entry<Integer, List<StructureObject>>> it = getRemainingTrackHeads().entrySet().iterator();
            while (it.hasNext()) {
                Entry<Integer, List<StructureObject>> entry = it.next();
                Iterator<StructureObject> subIt = entry.getValue().iterator();
                while (subIt.hasNext()) {
                    StructureObject o = subIt.next();
                    if (o.getPrevious()==null) {
                        children.add(new TrackNode(this, this, o));
                        subIt.remove();
                    }
                }
                if (entry.getValue().isEmpty()) {
                    it.remove();
                }
            }
        }
        return children;
    }
    
    public TrackNode getChild(StructureObject trackHead) {
        for (TrackNode t : getChildren()) if (t.trackHead==trackHead) return t;
        return null;
    }
    
    // TreeNode implementation
    @Override public String toString() {return (parent!=null?fieldName+"::": "")+(structureIdx>=0?generator.getExperiment().getStructure(structureIdx).getName():"Root");}
    
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
        if (children==null) return false; // lazy-loading
        return getChildCount()==0;
    }

    @Override
    public Enumeration children() {
        return Collections.enumeration(getChildren());
    }
}
