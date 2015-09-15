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
            this.actions = new JMenuItem[3];
            actions[0] = new JMenuItem("Open Track Mask");
            actions[0].setAction(new AbstractAction("Open Track Mask") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        logger.debug("opening track mask");
                        ImageObjectInterface i = ImageWindowManagerFactory.getImageManager().getImageTrackObjectInterface(track, trackHead.getStructureIdx());
                        ImageWindowManagerFactory.getImageManager().addImage(i.generateImage(), i, true);
                    }
                }
            );
            String[] structureNames = trackNode.trackHead.getExperiment().getStructuresAsString();
            int[] childStructures = trackNode.trackHead.getExperiment().getAllChildStructures(trackHead.getStructureIdx());
            JMenu segSubMenu = new JMenu("Open Child Structure Track Mask");
            actions[1] = segSubMenu;
            openSeg=new JMenuItem[childStructures.length];
            for (int i = 0; i < openSeg.length; i++) {
                openSeg[i] = new JMenuItem(structureNames[childStructures[i]]);
                openSeg[i].setAction(new AbstractAction(structureNames[childStructures[i]]) {
                        @Override
                        public void actionPerformed(ActionEvent ae) {
                            if (logger.isDebugEnabled()) logger.debug("opening track mask for structure: {} of idx: {} from structure idx: {}", ae.getActionCommand(), getStructureIdx(ae.getActionCommand(), openRaw), trackNode.trackHead.getStructureIdx());
                            int[] path = trackNode.trackHead.getExperiment().getPathToStructure(trackNode.trackHead.getStructureIdx(), getStructureIdx(ae.getActionCommand(), openRaw));
                            trackNode.loadAllTrackObjects(path);
                            ImageObjectInterface i = ImageWindowManagerFactory.getImageManager().getImageTrackObjectInterface(track, getStructureIdx(ae.getActionCommand(), openRaw));
                            ImageWindowManagerFactory.getImageManager().addImage(i.generateImage(), i, true);
                        }
                    }
                );
                segSubMenu.add(openSeg[i]);
            }
            
            JMenu rawSubMenu = new JMenu("Open Raw Input Image");
            actions[2] = rawSubMenu;
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
                            ImageWindowManagerFactory.getImageManager().addImage(i.generateRawImage(getStructureIdx(ae.getActionCommand(), openRaw)), i, true);
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
