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

import static configuration.userInterface.GUI.logger;
import dataStructure.objects.StructureObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import javax.swing.tree.TreeNode;
import org.bson.types.ObjectId;

/**
 *
 * @author nasique
 */
public class TrackNode implements TreeNode {
    StructureObject trackHead;
    StructureObject[] track;
    TreeNode parent;
    RootTrackNode root;
    ArrayList<TrackNode> children;
    public TrackNode(TreeNode parent, RootTrackNode root, StructureObject trackHead) {
        this.parent=parent;
        this.trackHead=trackHead;
        this.root=root;
        
    }

    public StructureObject[] getTrack() {
        if (track==null) track=root.generator.objectDAO.getTrack(trackHead);
        return track;
    }
    
    public ArrayList<TrackNode> getChildren() {
        if (children==null) {
            if (getTrack().length<=1) children=new ArrayList<TrackNode>(0);
            else {
                children=new ArrayList<TrackNode>();
                Iterator<Entry<Integer, ArrayList<StructureObject>>> it = root.getRemainingTrackHeads().subMap(track[1].getTimePoint(), true, track[track.length-1].getTimePoint(), true).entrySet().iterator();
                //if (logger.isTraceEnabled()) logger.trace("looking for children for node: {} timePoint left: {} timePoint right:{} head submap: {}", toString(), track[1].getTimePoint(), track[track.length-1].getTimePoint(), root.getRemainingTrackHeads().subMap(track[1].getTimePoint(), true, track[track.length-1].getTimePoint(), true).size());
                while (it.hasNext()) {
                    ArrayList<StructureObject> e = it.next().getValue();
                    Iterator<StructureObject> subIt = e.iterator();
                    while (subIt.hasNext()) {
                        StructureObject o = subIt.next();
                        if (trackContainscontainsId(o.getPrevious())) {
                            children.add(new TrackNode(this, root, o));
                            subIt.remove();
                        }
                        //if (logger.isTraceEnabled()) logger.trace("looking for structureObject: {} in track of node: {} found? {}", o, toString(), trackContainscontainsId(o.getPrevious()));
                    }
                    if (e.isEmpty()) it.remove();
                }
            }
            //logger.trace("get children: {} number of children: {} remaining distinct timePoint in root: {}", toString(),  children.size(), root.getRemainingTrackHeads().size());
        } 
        return children;
    }
    private boolean trackContainscontainsId(StructureObject object) {
        for (StructureObject o : getTrack()) if (o.getId().equals(object.getId())) {
            if (!o.equals(object)) logger.error("unique instanciation failed for {} and {} ", o, object);
            return true;
        }
        return false;
    }
    // TreeNode implementation
    @Override public String toString() {
        return "Track: Head idx="+trackHead.getIdx()+ " t="+trackHead.getTimePoint()+" length: "+getTrack().length; //TODO lazy loading track length if necessary
    }
    
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

    public Enumeration children() {
        return Collections.enumeration(getChildren());
    }
}
