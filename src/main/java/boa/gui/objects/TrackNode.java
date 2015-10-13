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
import dataStructure.objects.StructureObject;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.tree.TreeNode;
import org.bson.types.ObjectId;

/**
 *
 * @author nasique
 */
public class TrackNode implements TreeNode, UIContainer {
    StructureObject trackHead;
    ArrayList<StructureObject> track;
    TreeNode parent;
    RootTrackNode root;
    ArrayList<TrackNode> children;
    Boolean containsErrors;
    public TrackNode(TreeNode parent, RootTrackNode root, StructureObject trackHead) {
        this.parent=parent;
        this.trackHead=trackHead;
        this.root=root;
    }
    public TrackNode(TreeNode parent, RootTrackNode root, StructureObject trackHead, boolean containsErrors) {
        this(parent, root, trackHead);
        this.containsErrors = containsErrors;
    }

    public ArrayList<StructureObject> getTrack() {
        if (track==null) track=root.generator.objectDAO.getTrack(trackHead);
        return track;
    }
    
    public boolean containsError() {
        if (containsErrors==null) {
            for (StructureObject t: getTrack()) { //look for error within track
                if (t.hasTrackLinkError()) {
                    containsErrors=true;
                    break;
                }
            }
            if (containsErrors==null) return false;
            if (!containsErrors) { //look in children
                for (TrackNode t : children) {
                    if (t.containsError()) {
                        containsErrors=true;
                        break;
                    }
                }
            }
        }
        return containsErrors;
    }
    
    public ArrayList<TrackNode> getChildren() {
        if (children==null) {
            if (getTrack().size()<=1) children=new ArrayList<TrackNode>(0);
            else {
                children=new ArrayList<TrackNode>();
                Iterator<Entry<Integer, List<StructureObject>>> it = root.getRemainingTrackHeads().subMap(track.get(1).getTimePoint(), true, track.get(track.size()-1).getTimePoint(), true).entrySet().iterator();
                //if (logger.isTraceEnabled()) logger.trace("looking for children for node: {} timePoint left: {} timePoint right:{} head submap: {}", toString(), track[1].getTimePoint(), track[track.length-1].getTimePoint(), root.getRemainingTrackHeads().subMap(track[1].getTimePoint(), true, track[track.length-1].getTimePoint(), true).size());
                while (it.hasNext()) {
                    List<StructureObject> e = it.next().getValue();
                    Iterator<StructureObject> subIt = e.iterator();
                    while (subIt.hasNext()) {
                        StructureObject o = subIt.next();
                        if (o.getPrevious()==null) {
                            children.add(new TrackNode(this, root, o, true));
                            subIt.remove();
                        } else if (trackContainscontainsId(o.getPrevious())) {
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
            //if (!o.equals(object)) logger.error("unique instanciation failed for {} and {} ", o, object);
            return true;
        }
        return false;
    }
    
    public TrackNode getChild(StructureObject trackHead) {
        for (TrackNode t : getChildren()) if (t.trackHead==trackHead) return t;
        return null;
    }
    
    // UIContainer implementation
    @Override public Object[] getDisplayComponent() {
        return (new TrackNodeUI(this)).getDisplayComponent();
    }
    
    // TreeNode implementation
    @Override public String toString() {
        return "Track: Head idx="+trackHead.getIdx()+ " t="+trackHead.getTimePoint()+" length: "+getTrack().size(); //TODO lazy loading track length if necessary
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
    
    public void loadAllObjects(StructureObject object, int [] pathToChildStructureIdx) {
        root.generator.controller.objectGenerator.getObjectNode(object).loadAllChildObjects(pathToChildStructureIdx, 0);
    }
    
    public void loadAllTrackObjects(int[] pathToChildStructureIdx) {
        for (StructureObject o : track) loadAllObjects(o, pathToChildStructureIdx);
    }
    
    class TrackNodeUI {
        TrackNode trackNode;
        JMenuItem[] actions;
        JMenuItem[] openRaw;
        JMenuItem[] openSeg;
        public TrackNodeUI(TrackNode tn) {
            this.trackNode=tn;
            this.actions = new JMenuItem[2];
            JMenu segSubMenu = new JMenu("Open Segmented Track Image");
            actions[0] = segSubMenu;
            JMenu rawSubMenu = new JMenu("Open Raw Track Image");
            actions[1] = rawSubMenu;
            String[] structureNames = trackNode.trackHead.getExperiment().getStructuresAsString();
            openSeg=new JMenuItem[structureNames.length];
            for (int i = 0; i < openSeg.length; i++) {
                openSeg[i] = new JMenuItem(structureNames[i]);
                openSeg[i].setAction(new AbstractAction(structureNames[i]) {
                        @Override
                        public void actionPerformed(ActionEvent ae) {
                            if (logger.isDebugEnabled()) logger.debug("opening track mask for structure: {} of idx: {} from structure idx: {}", ae.getActionCommand(), getStructureIdx(ae.getActionCommand(), openRaw), trackNode.trackHead.getStructureIdx());
                            int[] path = trackNode.trackHead.getExperiment().getPathToStructure(trackNode.trackHead.getStructureIdx(), getStructureIdx(ae.getActionCommand(), openRaw));
                            trackNode.loadAllTrackObjects(path);
                            ImageObjectInterface i = ImageWindowManagerFactory.getImageManager().getImageTrackObjectInterface(track, getStructureIdx(ae.getActionCommand(), openRaw));
                            if (i!=null) ImageWindowManagerFactory.getImageManager().addImage(i.generateImage(), i, true);
                            /* for the 1st
                            ImageObjectInterface i = ImageWindowManagerFactory.getImageManager().getImageTrackObjectInterface(track, trackHead.getStructureIdx());
                            ImageWindowManagerFactory.getImageManager().addImage(i.generateImage(), i, true);
                            */
                        }
                    }
                );
                segSubMenu.add(openSeg[i]);
            }
            openRaw=new JMenuItem[structureNames.length];
            for (int i = 0; i < openRaw.length; i++) {
                openRaw[i] = new JMenuItem(structureNames[i]);
                openRaw[i].setAction(new AbstractAction(structureNames[i]) {
                        @Override
                        public void actionPerformed(ActionEvent ae) {
                            if (logger.isDebugEnabled()) logger.debug("opening track raw image for structure: {} of idx: {}", ae.getActionCommand(), getStructureIdx(ae.getActionCommand(), openRaw));
                            int[] path = trackNode.trackHead.getExperiment().getPathToStructure(trackNode.trackHead.getStructureIdx(), getStructureIdx(ae.getActionCommand(), openRaw));
                            trackNode.loadAllTrackObjects(path);
                            ImageObjectInterface i = ImageWindowManagerFactory.getImageManager().getImageTrackObjectInterface(track, getStructureIdx(ae.getActionCommand(), openRaw));
                            if (i!=null) ImageWindowManagerFactory.getImageManager().addImage(i.generateRawImage(getStructureIdx(ae.getActionCommand(), openRaw)), i, true);
                        }
                    }
                );
                rawSubMenu.add(openRaw[i]);
            }
        }
        public Object[] getDisplayComponent() {return actions;}
        private int getStructureIdx(String name, JMenuItem[] actions) {
            for (int i = 0; i<actions.length; ++i) if (actions[i].getActionCommand().equals(name)) return i;
            return -1;
        }
    }
}
