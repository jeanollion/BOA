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
import dataStructure.configuration.MicroscopyField;
import dataStructure.objects.Selection;
import dataStructure.objects.SelectionDAO;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import image.Image;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import utils.ThreadRunner;

/**
 *
 * @author nasique
 */
public class RootTrackNode implements TrackNodeInterface, UIContainer {
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

    public String getFieldName() {
        return fieldName;
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
            List<StructureObject> roots = generator.getObjectDAO(fieldName).getRoots();
            if (roots==null || roots.isEmpty()) logger.error("No root found for position: {}, please run pre-processing", fieldName);
            else parentTrackHead = roots.get(0);
            if (parentTrackHead!=null) logger.trace("parentTrackHead id:"+parentTrackHead.getId());
        }
        return parentTrackHead;
    }
    private List<StructureObject> getParentTrack() {
        return generator.getObjectDAO(fieldName).getRoots();
    }
    
    public TreeMap<Integer, List<StructureObject>> getRemainingTrackHeads() {
        if (remainingTrackHeadsTM==null) {
            List<StructureObject> trackHeads = generator.getObjectDAO(fieldName).getTrackHeads(getParentTrackHead(), structureIdx);
            //List<StructureObject> trackHeads = new ArrayList<StructureObject> (StructureObjectUtils.getAllTracks(getParentTrack(), structureIdx).keySet());
            //Collections.sort(trackHeads);
            if (trackHeads.isEmpty()) {
                remainingTrackHeadsTM = new TreeMap<Integer, List<StructureObject>>();
                logger.debug("structure: {} no trackHeads found", structureIdx);
            } else {
                logger.debug("structure: {} nb trackHeads found: {}", structureIdx, trackHeads.size());
                HashMap<Integer, List<StructureObject>> map  = new HashMap<Integer, List<StructureObject>> (trackHeads.get(trackHeads.size()-1).getFrame()-trackHeads.get(0).getFrame()+1);
                int currentTimePoint = trackHeads.get(0).getFrame();
                int lastIdx = 0;
                int currentIdx = 1;
                while (currentIdx<trackHeads.size()) {
                    if (trackHeads.get(currentIdx).getFrame()>currentTimePoint) {
                        //ArrayList<StructureObject> currentHeads = new ArrayList<StructureObject>(currentIdx-lastIdx);
                        //for (int i = lastIdx; i<currentIdx; ++i) currentHeads.add(trackHeads.get(i));
                        map.put(currentTimePoint, new ArrayList<StructureObject>(trackHeads.subList(lastIdx, currentIdx)));
                        lastIdx=currentIdx;
                        currentTimePoint = trackHeads.get(currentIdx).getFrame();
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
    
    public List<TrackNode> getChildren() {
        if (children==null) {
            children = new ArrayList<TrackNode>();
            
            Iterator<Entry<Integer, List<StructureObject>>> it = getRemainingTrackHeads().entrySet().iterator();
            //logger.debug("get track nodes from root: remaining: {}",remainingTrackHeadsTM.size());
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
            //logger.debug("get track nodes from root: {}, remaining: {}", children.size(), remainingTrackHeadsTM.size());
        }
        return children;
    }
    
    public TrackNode getChild(StructureObject trackHead) {
        for (TrackNode t : getChildren()) if (t.trackHead==trackHead) return t;
        return null;
    }
    
    // TreeNode implementation
    @Override public String toString() {
        return (parent!=null?fieldName+"::": "")+(structureIdx>=0?generator.getExperiment().getStructure(structureIdx).getName():"Root");
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
        if (children==null) return false; // lazy-loading
        return getChildCount()==0;
    }

    @Override
    public Enumeration children() {
        return Collections.enumeration(getChildren());
    }
    
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
        parent.getChildren().remove(this);
    }

    public void setParent(MutableTreeNode newParent) {
        this.parent=(TrackExperimentNode)newParent;
    }
    
    // UIContainer implementation
    @Override public Object[] getDisplayComponent(boolean multipleSelection) {
        return (new RootTrackNodeUI()).getDisplayComponent(multipleSelection);
    }
    
    class RootTrackNodeUI {
        JMenuItem openPreprocessedFrame, openPreprocessedAllFrames;
        Object[] actions;
        
        public RootTrackNodeUI() {
            this.actions = new JMenuItem[2];
            openPreprocessedFrame = new JMenuItem("Open Pre-processed (Default Frame)");
            openPreprocessedFrame.setAction(new AbstractAction(openPreprocessedFrame.getActionCommand()) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        int channels = generator.db.getExperiment().getChannelImageCount();
                        Image[][] imagesTC = new Image[1][channels];
                        MicroscopyField f = generator.db.getExperiment().getPosition(fieldName);
                        for (int channel = 0; channel<channels; ++channel) {
                            imagesTC[0][channel] = generator.db.getExperiment().getImageDAO().openPreProcessedImage(channel, f.getDefaultTimePoint(), fieldName);
                            if (imagesTC[0][channel]==null) return;
                        }
                        f.flushImages();
                        ImageWindowManagerFactory.getImageManager().getDisplayer().showImage5D("PreProcessed Image of Position: "+fieldName+ " Frame: "+f.getDefaultTimePoint(), imagesTC);
                    }
                }
            );
            actions[0] = openPreprocessedFrame;
            openPreprocessedAllFrames = new JMenuItem("Open Pre-processed Frames");
            openPreprocessedAllFrames.setAction(new AbstractAction(openPreprocessedAllFrames.getActionCommand()) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        MicroscopyField f = generator.db.getExperiment().getPosition(fieldName);
                        int channels = generator.db.getExperiment().getChannelImageCount();
                        int frames = f.getTimePointNumber(false);
                        Image[][] imagesTC = new Image[frames][channels];
                        for (int channel = 0; channel<channels; ++channel) {
                            for (int frame = 0; frame<frames; ++frame) {
                                imagesTC[frame][channel] = generator.db.getExperiment().getImageDAO().openPreProcessedImage(channel, frame, fieldName);
                                if (imagesTC[frame][channel]==null) return;
                            }
                        }
                        f.flushImages();
                        ImageWindowManagerFactory.getImageManager().getDisplayer().showImage5D("PreProcessed Images of Position: "+fieldName, imagesTC);
                    }
                }
            );
            actions[1] = openPreprocessedAllFrames;
        }
        public Object[] getDisplayComponent(boolean multipleSelection) {
            if (multipleSelection) {
                return new JMenuItem[]{};
            } else return actions;
        }
    }
}
