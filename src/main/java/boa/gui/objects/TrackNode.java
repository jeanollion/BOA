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

import boa.gui.GUI;
import static boa.gui.GUI.logger;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import core.Processor;
import dataStructure.configuration.Experiment;
import dataStructure.objects.Selection;
import dataStructure.objects.SelectionDAO;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
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
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import org.bson.types.ObjectId;
import utils.Pair;
import utils.ThreadRunner;
import utils.ThreadRunner.ThreadAction;

/**
 *
 * @author nasique
 */
public class TrackNode implements TrackNodeInterface, UIContainer {
    StructureObject trackHead;
    List<StructureObject> track;
    TrackNodeInterface parent;
    RootTrackNode root;
    List<TrackNode> children;
    Boolean containsErrors;
    public TrackNode(TrackNodeInterface parent, RootTrackNode root, StructureObject trackHead) {
        this.parent=parent;
        this.trackHead=trackHead;
        this.root=root;
    }
    public TrackNode(TrackNodeInterface parent, RootTrackNode root, StructureObject trackHead, boolean containsErrors) {
        this(parent, root, trackHead);
        this.containsErrors = containsErrors;
    }

    public StructureObject getTrackHead() {
        return trackHead;
    }
    
    public List<StructureObject> getTrack() {
        if (track==null) track=root.generator.getObjectDAO(this.trackHead.getPositionName()).getTrack(trackHead);
        if (track==null) logger.error("Could not retrieve track from trackHead: {}", trackHead);
        return track;
    }
    
    public boolean containsError() {
        if (containsErrors==null) {
            if (track==null) return false;
            for (StructureObject t: track) { //look for error within track
                if (t.hasTrackLinkError(true, false)) {
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
    
    public List<TrackNode> getChildren() {
        if (children==null) {
            if (getTrack()==null || getTrack().size()<=1) children=new ArrayList<TrackNode>(0);
            else {
                children=new ArrayList<TrackNode>();
                //Iterator<Entry<Integer, List<StructureObject>>> it = root.getRemainingTrackHeads().subMap(track.get(1).getTimePoint(), true, track.get(track.size()-1).getTimePoint(), true).entrySet().iterator();
                Iterator<Entry<Integer, List<StructureObject>>> it = root.getRemainingTrackHeads().entrySet().iterator();
                //logger.trace("looking for children for node: {} timePoint left: {} timePoint right:{}", toString(), track.get(1).getTimePoint(), track.get(track.size()-1).getTimePoint());
                int leftLimit = track.get(1).getFrame();
                int rightLimit = track.get(track.size()-1).getFrame();
                while (it.hasNext()) {
                    Entry<Integer, List<StructureObject>> entry = it.next();
                    if (entry.getKey()<leftLimit) continue;
                    if (entry.getKey()>=rightLimit) break;
                    Iterator<StructureObject> subIt = entry.getValue().iterator();
                    while (subIt.hasNext()) {
                        StructureObject o = subIt.next();
                        if (trackContainscontainsId(o.getPrevious())) {
                            children.add(new TrackNode(this, root, o));
                            subIt.remove();
                        }
                        
                        //logger.trace("looking for structureObject: {} in track of node: {} found? {}", o, toString(), trackContainscontainsId(o.getPrevious()));
                    }
                    if (entry.getValue().isEmpty()) {
                        it.remove();
                    }
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
    @Override public Object[] getDisplayComponent(boolean multipleSelection) {
        return (new TrackNodeUI(this)).getDisplayComponent(multipleSelection);
    }
    
    // TreeNode implementation
    @Override public String toString() {
        if (trackHead==null) return "tracking should be re-run";
        getTrack();
        return "Track: Head idx="+trackHead.getIdx()+ " t="+trackHead.getFrame()+" length: "+(track!=null?track.size():"........."); 
    }
    
    public TrackNode getChildAt(int childIndex) {
        return getChildren().get(childIndex);
    }

    public int getChildCount() {
        return getChildren().size();
    }

    public TrackNodeInterface getParent() {
        return parent;
    }

    public int getIndex(TreeNode node) {
        return getChildren().indexOf(node);
    }

    public boolean getAllowsChildren() {
        return true;
    }

    public boolean isLeaf() {
        if (track==null && getParent() instanceof RootTrackNode && root.generator.getExperiment().getPathToRoot(root.structureIdx).length==1) return false; // Lazy loading only for 1st structure after root
        return getChildCount()==0;
    }

    public Enumeration children() {
        return Collections.enumeration(getChildren());
    }
    /*
    public void loadAllObjects(StructureObject object, int [] pathToChildStructureIdx) {
        //root.generator.controller.objectGenerator.getObjectNode(object).loadAllChildObjects(pathToChildStructureIdx, 0);
    }
    
    public void loadAllTrackObjects(int[] pathToChildStructureIdx) {
        for (StructureObject o : getTrack()) loadAllObjects(o, pathToChildStructureIdx);
    }
    */
    // mutable tree node interface
    public void insert(MutableTreeNode child, int index) {
        getChildren().add(index, (TrackNode)child);
    }

    public void remove(int index) {
        getChildren().remove(index);
    }

    public void remove(MutableTreeNode node) {
        getChildren().remove(node);
    }

    public void setUserObject(Object object) {
        
    }

    public void removeFromParent() {
        parent.remove(this);
    }

    public void setParent(MutableTreeNode newParent) {
        this.parent=(TrackNodeInterface)newParent;
    }

    class TrackNodeUI {
        TrackNode trackNode;
        JMenuItem[] actions;
        JMenuItem[] openRaw;
        JMenuItem[] openSeg;
        JMenuItem[] runSegAndTracking;
        JMenuItem[] runTracking;
        JMenuItem[] createSelection;
        JMenuItem delete;
        JMenuItem addToSelection, removeFromSelection;
        public TrackNodeUI(TrackNode tn) {
            this.trackNode=tn;
            String[] childStructureNames = trackNode.trackHead.getExperiment().getChildStructuresAsString(trackNode.trackHead.getStructureIdx());
            this.actions = new JMenuItem[7];
            JMenu segSubMenu = new JMenu("Open Segmented Track Image");
            //actions[0] = segSubMenu;
            JMenu rawSubMenu = new JMenu("Open Raw Track Image");
            actions[0] = rawSubMenu;
            String[] structureNames = trackNode.trackHead.getExperiment().getStructuresAsString();
            JMenu runSegAndTrackingSubMenu = new JMenu("Run segmentation and tracking");
            actions[1] = runSegAndTrackingSubMenu;
            JMenu runTrackingSubMenu = new JMenu("Run tracking");
            actions[2] = runTrackingSubMenu;
            JMenu createSelectionSubMenu = new JMenu("Create Selection");
            actions[3] = createSelectionSubMenu;
            addToSelection = new JMenuItem("Add to Selected Selection(s)");
            actions[4] = addToSelection;
            removeFromSelection = new JMenuItem("Remove from Selected Selection(s)");
            actions[5] = removeFromSelection;
            delete = new JMenuItem("Delete");
            actions[6] = delete;
            //delete.setEnabled(false);
            delete.setAction(new AbstractAction("Delete") {
                @Override public void actionPerformed(ActionEvent e) {
                    //trackNode.trackHead.getDAO().delete(track, true);
                    //trackNode.getParent().getChildren().remove(trackNode);
                    trackNode.root.generator.controller.getTreeGenerator(trackHead.getStructureIdx()).deleteSelectedTracks();
                }
            });
            
            openSeg=new JMenuItem[structureNames.length];
            for (int i = 0; i < openSeg.length; i++) {
                openSeg[i] = new JMenuItem(structureNames[i]);
                openSeg[i].setAction(new AbstractAction(structureNames[i]) {
                        @Override
                        public void actionPerformed(ActionEvent ae) {
                            int structureIdx = getStructureIdx(ae.getActionCommand(), openRaw);
                            if (logger.isDebugEnabled()) logger.debug("opening track mask for structure: {} of idx: {} from structure idx: {}", ae.getActionCommand(),structureIdx, trackNode.trackHead.getStructureIdx());
                            //int[] path = trackNode.trackHead.getExperiment().getPathToStructure(trackNode.trackHead.getStructureIdx(), getStructureIdx(ae.getActionCommand(), openRaw));
                            //trackNode.loadAllTrackObjects(path);
                            ImageObjectInterface i = ImageWindowManagerFactory.getImageManager().getImageTrackObjectInterface(getTrack(), structureIdx);
                            if (i!=null) ImageWindowManagerFactory.getImageManager().addImage(i.generateLabelImage(), i, i.getChildStructureIdx(), true, true);
                            GUI.getInstance().setInteractiveStructureIdx(structureIdx);
                            GUI.getInstance().setTrackStructureIdx(structureIdx);
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
                            //int[] path = trackNode.trackHead.getExperiment().getPathToStructure(trackNode.trackHead.getStructureIdx(), getStructureIdx(ae.getActionCommand(), openRaw));
                            //trackNode.loadAllTrackObjects(path);
                            int structureIdx = getStructureIdx(ae.getActionCommand(), openRaw);
                            ImageObjectInterface i = ImageWindowManagerFactory.getImageManager().getImageTrackObjectInterface(getTrack(), structureIdx);
                            if (i!=null) ImageWindowManagerFactory.getImageManager().addImage(i.generateRawImage(structureIdx, true), i, structureIdx, false, true);
                            GUI.getInstance().setInteractiveStructureIdx(structureIdx);
                            GUI.getInstance().setTrackStructureIdx(structureIdx);
                        }
                    }
                );
                rawSubMenu.add(openRaw[i]);
            }
            runSegAndTracking = new JMenuItem[childStructureNames.length];
            for (int i = 0; i < runSegAndTracking.length; i++) {
                runSegAndTracking[i] = new JMenuItem(childStructureNames[i]);
                runSegAndTracking[i].setAction(new AbstractAction(childStructureNames[i]) {
                        @Override
                        public void actionPerformed(ActionEvent ae) {
                            final int structureIdx = getStructureIdx(ae.getActionCommand(), openRaw);
                            logger.debug("running segmentation and tracking for structure: {} of idx: {}, within track: {}", ae.getActionCommand(), structureIdx, trackHead);
                            /*Experiment xp = root.generator.getExperiment();
                            ArrayList<StructureObject> parents = new ArrayList<StructureObject>();
                            for (TrackNode n : root.generator.getSelectedTrackNodes()) parents.addAll(n.track);
                            Processor.processStructure(structureIdx, xp, xp.getMicroscopyField(trackHead.getFieldName()), root.generator.getObjectDAO(), parents, null);
                            Processor.trackStructure(structureIdx, xp, xp.getMicroscopyField(trackHead.getFieldName()), root.generator.getObjectDAO(), true, root.generator.getSelectedTrackHeads());
                            */
                            ThreadRunner.execute(root.generator.getSelectedTrackNodes(), false, (TrackNode n, int idx) -> {
                                List<Pair<String, Exception>> errors = Processor.executeProcessingScheme(n.getTrack(), structureIdx, false, true);
                                logger.debug("errors: {}", errors.size());
                                for (Pair<String, Exception> e : errors) logger.error(e.key, e.value);
                            });
                            // reload tree
                            root.generator.controller.updateParentTracks(root.generator.controller.getTreeIdx(trackHead.getStructureIdx()));
                            // reload objects
                            ImageWindowManagerFactory.getImageManager().reloadObjects(trackHead, structureIdx, true);
                            // reload selection
                            GUI.getInstance().populateSelections();
                        }
                    }
                );
                runSegAndTrackingSubMenu.add(runSegAndTracking[i]);
            }
            
            runTracking = new JMenuItem[childStructureNames.length];
            for (int i = 0; i < runTracking.length; i++) {
                runTracking[i] = new JMenuItem(childStructureNames[i]);
                runTracking[i].setAction(new AbstractAction(childStructureNames[i]) {
                        @Override
                        public void actionPerformed(ActionEvent ae) {
                            final int structureIdx = getStructureIdx(ae.getActionCommand(), openRaw);
                            logger.debug("running tracking for structure: {} of idx: {}, within track: {}", ae.getActionCommand(), structureIdx, trackHead);
                            /*Experiment xp = root.generator.getExperiment();
                            Processor.trackStructure(structureIdx, xp, xp.getMicroscopyField(trackHead.getFieldName()), root.generator.getObjectDAO(), true, root.generator.getSelectedTrackHeads());
                            */
                            ThreadRunner.execute(root.generator.getSelectedTrackNodes(), false, new ThreadAction<TrackNode>() {
                                @Override public void run(TrackNode n, int idx) {
                                    List<Pair<String, Exception>> errors = Processor.executeProcessingScheme(n.getTrack(), structureIdx, true, false);
                                    for (Pair<String, Exception> e : errors) logger.error(e.key, e.value);
                                }
                            });
                            // reload tree
                            root.generator.controller.updateParentTracks(root.generator.controller.getTreeIdx(trackHead.getStructureIdx()));
                            // reload objects
                            ImageWindowManagerFactory.getImageManager().reloadObjects(trackHead, structureIdx, true);
                        }
                    }
                );
                runTrackingSubMenu.add(runTracking[i]);
            }
            
            createSelection = new JMenuItem[childStructureNames.length];
            for (int i = 0; i < createSelection.length; i++) {
                createSelection[i] = new JMenuItem(childStructureNames[i]);
                createSelection[i].setAction(new AbstractAction(childStructureNames[i]) {
                        @Override
                        public void actionPerformed(ActionEvent ae) {
                            final int structureIdx = getStructureIdx(ae.getActionCommand(), openRaw);
                            logger.debug("create selectionfor structure: {} of idx: {}, within track: {}", ae.getActionCommand(), structureIdx, trackHead);
                            List<TrackNode> selectedNodes = root.generator.getSelectedTrackNodes();
                            List<StructureObject> objectsToAdd = new ArrayList<StructureObject>();
                            for (TrackNode tn : selectedNodes) {
                                //logger.debug("creating selection: th: {}, length: {}", tn.trackHead, tn.getTrack().size());
                                for (StructureObject p : tn.getTrack()) {
                                    //if (p.getChildObjects(structureIdx)!=null && !p.getChildren(structureIdx).isEmpty()) logger.debug("creating selection: parent idx: {} children : {}", p, p.getChildren(structureIdx));
                                    objectsToAdd.addAll(p.getChildren(structureIdx));
                                }
                            }
                            Selection s = root.generator.db.getSelectionDAO().getOrCreate(ae.getActionCommand(), true);
                            s.addElements(objectsToAdd);
                            s.setIsDisplayingObjects(true);
                            root.generator.db.getSelectionDAO().store(s);
                            GUI.getInstance().populateSelections();
                        }
                    }
                );
                createSelectionSubMenu.add(createSelection[i]);
            }
            addToSelection.setAction(new AbstractAction("Add to Selected Selection(s)") {
                @Override public void actionPerformed(ActionEvent e) {
                    List<StructureObject> sel = trackNode.root.generator.controller.getTreeGenerator(trackHead.getStructureIdx()).getSelectedTrackHeads();
                    SelectionDAO dao = GUI.getDBConnection().getSelectionDAO();
                    for (Selection s : GUI.getInstance().getSelectedSelections(false)) {
                        if (s.getStructureIdx()==-1 || s.getStructureIdx()==trackHead.getStructureIdx()) {
                            s.addElements(sel);
                            dao.store(s);
                        }
                    }
                    GUI.updateRoiDisplayForSelections(null, null);
                }
            });
            removeFromSelection.setAction(new AbstractAction("Remove from Selected Selection(s)") {
                @Override public void actionPerformed(ActionEvent e) {
                    List<StructureObject> sel = trackNode.root.generator.controller.getTreeGenerator(trackHead.getStructureIdx()).getSelectedTrackHeads();
                    SelectionDAO dao = GUI.getDBConnection().getSelectionDAO();
                    for (Selection s : GUI.getInstance().getSelectedSelections(false)) {
                        if (s.getStructureIdx()==-1 || s.getStructureIdx()==trackHead.getStructureIdx()) {
                            s.removeElements(sel);
                            dao.store(s);
                        }
                    }
                    GUI.updateRoiDisplayForSelections(null, null);
                }
            });
        }
        public Object[] getDisplayComponent(boolean multipleSelection) {
            if (multipleSelection) {
                return new JMenuItem[]{actions[1], actions[2], actions[3], actions[4], actions[5], actions[6]};
            } else return actions;
        }
        private int getStructureIdx(String name, JMenuItem[] actions) {
            for (int i = 0; i<actions.length; ++i) if (actions[i].getActionCommand().equals(name)) return i;
            return -1;
        }
    }
}
