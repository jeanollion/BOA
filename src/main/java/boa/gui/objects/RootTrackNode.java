/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.gui.objects;

import boa.ui.GUI;
import static boa.ui.GUI.logger;
import boa.gui.image_interaction.IJImageDisplayer;
import boa.gui.image_interaction.IJVirtualStack;
import boa.gui.image_interaction.InteractiveImage;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.core.DefaultWorker;
import boa.core.Processor;
import boa.configuration.experiment.Position;
import boa.data_structure.input_image.InputImages;
import boa.data_structure.Selection;
import boa.data_structure.dao.SelectionDAO;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.image.Image;
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
import boa.utils.ThreadRunner;
import boa.utils.Utils;
import java.util.stream.Collectors;

/**
 *
 * @author nasique
 */
public class RootTrackNode implements TrackNodeInterface, UIContainer {
    TrackTreeGenerator generator;
    private  List<TrackNode> children;
    private List<StructureObject> remainingTrackHeads;
    private StructureObject parentTrackHead;
    int structureIdx;
    TrackExperimentNode parent;
    final String position;
    Boolean containsErrors;
    final boolean root;
    public RootTrackNode(TrackTreeGenerator generator, StructureObject parentTrackHead, int structureIdx) {
        this.generator = generator;
        this.parentTrackHead=parentTrackHead;
        this.structureIdx=structureIdx;
        this.position=parentTrackHead.getPositionName();
        root = false;
    }
    
    public RootTrackNode(TrackExperimentNode parent, String position, int structureIdx) { // constructor when parent == root
        this.generator = parent.generator;
        this.parent = parent;
        this.position=position;
        this.structureIdx=structureIdx;
        root = true;
        //logger.debug("creating root track node for field: {} structure: {}", position, structureIdx);
    }

    public String getFieldName() {
        return position;
    }
    
    public void refresh() {
        children = null;
        remainingTrackHeads=null;
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
            if (position==null) {
                logger.warn("No track head or fieldName defined for RootTrackNode instance");
                return null;
            }
            List<StructureObject> roots = generator.getObjectDAO(position).getRoots();
            if (roots==null || roots.isEmpty()) logger.error("No root found for position: {}, please run pre-processing", position);
            else parentTrackHead = roots.get(0);
            if (parentTrackHead!=null) logger.trace("parentTrackHead id:"+parentTrackHead.getId());
        }
        return parentTrackHead;
    }
    private List<StructureObject> getParentTrack() {
        return generator.getObjectDAO(position).getRoots();
    }
    
    public List<StructureObject> getRemainingTracksPerFrame() {
        if (remainingTrackHeads==null) {
            if (getParentTrackHead()==null) return Collections.EMPTY_LIST;
            long t0 = System.currentTimeMillis();
            remainingTrackHeads = generator.getObjectDAO(position).getTrackHeads(getParentTrackHead(), structureIdx);
            long t1 = System.currentTimeMillis();
        }
        return remainingTrackHeads;
    }
    @Override
    public List<TrackNode> getChildren() {
        if (children==null) {
            children = getRemainingTracksPerFrame().stream()
                .filter(th->th.getPrevious()==null)
                .map(th-> new TrackNode(this, this, th))
                .collect(Collectors.toList());
            getRemainingTracksPerFrame().removeAll(children.stream().map(tn ->tn.trackHead).collect(Collectors.toSet()));
        }
        return children;
    }
    
    public TrackNode getChild(StructureObject trackHead) {
        return getChildren().stream().filter(t->t.trackHead==trackHead).findFirst().orElse(null);
    }
    
    // TreeNode implementation
    @Override public String toString() {
        return (parent!=null?"Position #"+generator.getExperiment().getPositionIdx(position)+" "+position: "Root");//+(structureIdx>=0? generator.getExperiment().getStructure(structureIdx).getName():"Root");
    }
    
    @Override public TrackNode getChildAt(int childIndex) {
        return getChildren().get(childIndex);
    }

    @Override public int getChildCount() {
        return getChildren().size();
    }

    @Override public TreeNode getParent() {
        return parent;
    }

    @Override public int getIndex(TreeNode node) {
        return getChildren().indexOf(node);
    }

    @Override public boolean getAllowsChildren() {
        return true;
    }

    @Override public boolean isLeaf() {
        if (children==null) return false; // lazy-loading
        return getChildCount()==0;
    }

    @Override
    public Enumeration children() {
        return Collections.enumeration(getChildren());
    }
    
    // mutable tree node interface
    @Override public void insert(MutableTreeNode child, int index) {
        getChildren().add(index, (TrackNode)child);
    }

    @Override public void remove(int index) {
        getChildren().remove(index);
    }

    @Override public void remove(MutableTreeNode node) {
        getChildren().remove(node);
    }

    @Override public void setUserObject(Object object) {
        
    }

    @Override public void removeFromParent() {
        parent.getChildren().remove(this);
    }

    @Override public void setParent(MutableTreeNode newParent) {
        if (newParent==null) parent = null;
        else parent=(TrackExperimentNode)newParent;
    }
    
    // UIContainer implementation
    @Override public Object[] getDisplayComponent(boolean multipleSelection) {
        if (!root) return null;
        return (new RootTrackNodeUI()).getDisplayComponent(multipleSelection);
    }
    
    class RootTrackNodeUI {
        JMenuItem openRawAllFrames, openPreprocessedAllFrames;
        JMenu rawSubMenu, createSelectionSubMenu;
        Object[] actions;
        JMenuItem[] openRaw, createSelection;
        public RootTrackNodeUI() {
            this.actions = new JMenuItem[4];
            
            openRawAllFrames = new JMenuItem("Open Input Images");
            actions[0] = openRawAllFrames;
            openRawAllFrames.setAction(new AbstractAction(openRawAllFrames.getActionCommand()) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    generator.db.getExperiment().flushImages(true, true, position);
                    IJVirtualStack.openVirtual(generator.getExperiment(), position, false);
                }
            }
            );
            openPreprocessedAllFrames = new JMenuItem("Open Pre-processed Frames");
            actions[1] = openPreprocessedAllFrames;
            openPreprocessedAllFrames.setAction(new AbstractAction(openPreprocessedAllFrames.getActionCommand()) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        generator.db.getExperiment().flushImages(true, true, position);
                        IJVirtualStack.openVirtual(generator.getExperiment(), position, true);
                    }
                }
            );
            openPreprocessedAllFrames.setEnabled(generator.getExperiment().getImageDAO().getPreProcessedImageProperties(position)!=null);
            rawSubMenu = new JMenu("Open Kymograph");
            actions[2] = rawSubMenu;
            List<String> directRootChild = new ArrayList<String>();
            for (int sIdx = 0; sIdx<generator.db.getExperiment().getStructureCount(); ++sIdx) {
                if (true ||generator.db.getExperiment().getStructure(sIdx).getParentStructure()==-1) directRootChild.add(generator.db.getExperiment().getStructure(sIdx).getName());
            }
            openRaw=new JMenuItem[directRootChild.size()];
            for (int i = 0; i < openRaw.length; i++) {
                openRaw[i] = new JMenuItem(directRootChild.get(i));
                openRaw[i].setAction(new AbstractAction(directRootChild.get(i)) {
                        @Override
                        public void actionPerformed(ActionEvent ae) {
                            int structureIdx = generator.getExperiment().getStructureIdx(ae.getActionCommand());
                            if (logger.isDebugEnabled()) logger.debug("opening track raw image for structure: {} of idx: {}", ae.getActionCommand(), structureIdx);
                            InteractiveImage i = ImageWindowManagerFactory.getImageManager().getImageTrackObjectInterface(generator.db.getDao(position).getRoots(), structureIdx);
                            if (i!=null) ImageWindowManagerFactory.getImageManager().addImage(i.generatemage(structureIdx, true), i, structureIdx, true);
                            GUI.getInstance().setInteractiveStructureIdx(structureIdx);
                            GUI.getInstance().setTrackStructureIdx(structureIdx);
                        }
                    }
                );
                rawSubMenu.add(openRaw[i]);
            }
            createSelectionSubMenu = new JMenu("Create Selection");
            actions[3] = createSelectionSubMenu;
            createSelection = new JMenuItem[directRootChild.size()];
            for (int i = 0; i < createSelection.length; i++) {
                createSelection[i] = new JMenuItem(directRootChild.get(i));
                createSelection[i].setAction(new AbstractAction(directRootChild.get(i)) {
                        @Override
                        public void actionPerformed(ActionEvent ae) {
                            int structureIdx = generator.getExperiment().getStructureIdx(ae.getActionCommand());
                            logger.debug("create selection for structure: {} of idx: {}", ae.getActionCommand(), structureIdx);
                            List<RootTrackNode> selectedNodes = generator.getSelectedRootTrackNodes();
                            logger.debug("selected nodes: {}", selectedNodes);
                            Selection s = generator.db.getSelectionDAO().getOrCreate(ae.getActionCommand(), true);
                            s.setColor("Grey");
                            // execute in background
                            DefaultWorker.execute(i->{
                                s.addElements(StructureObjectUtils.getAllObjects(generator.db.getDao(selectedNodes.get(i).position), structureIdx));
                                logger.debug("current objects: {}", s.getAllElementStrings().size());
                                if (i==1 || (selectedNodes.size()>5 && i%(selectedNodes.size()/5)==0) || i==(selectedNodes.size()-1)) {
                                    logger.debug("saving sel {}", s.getAllElementStrings().size());
                                    generator.db.getSelectionDAO().store(s);
                                }
                                
                                return "";
                            }, selectedNodes.size()).setEndOfWork(()->{
                                GUI.getInstance().populateSelections();
                                GUI.getInstance().getSelections().stream().filter(ss->ss.getName().equals(ae.getActionCommand())).findAny().get().setIsDisplayingObjects(true);
                            });
                            
                        }
                    }
                );
                createSelectionSubMenu.add(createSelection[i]);
            }
        }
        public Object[] getDisplayComponent(boolean multipleSelection) {
            if (multipleSelection) {
                return new Object[]{createSelectionSubMenu};
            } else return actions;
        }
        
    }
}
