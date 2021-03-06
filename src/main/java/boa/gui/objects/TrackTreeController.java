/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.gui.objects;

import boa.ui.GUI;
import static boa.ui.GUI.logger;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.swing.JTree;

/**
 *
 * @author Jean Ollion
 */
public class TrackTreeController {
    MasterDAO db;
    TreeMap<Integer, TrackTreeGenerator> displayedGeneratorS;
    HashMap<Integer, TrackTreeGenerator> allGeneratorS;
    int[] structurePathToRoot;
    //StructureObjectTreeGenerator objectGenerator;
    boolean updateRoiDisplayWhenSelectionChange = true;
    
    public TrackTreeController(MasterDAO db) {
        this.db = db;
        //this.objectGenerator=objectGenerator;
        allGeneratorS = new HashMap<>();
        for (int s = 0; s<db.getExperiment().getStructureCount(); ++s) {
            allGeneratorS.put(s, new TrackTreeGenerator(db, this));
        }
        displayedGeneratorS=new TreeMap<>();
    }
    public void setEnabled(boolean enabled) {
        for (TrackTreeGenerator t : allGeneratorS.values()) t.setEnabled(enabled);
    }
    public boolean isUpdateRoiDisplayWhenSelectionChange() {
        return updateRoiDisplayWhenSelectionChange;
    }

    public void setUpdateRoiDisplayWhenSelectionChange(boolean updateRoiDisplayWhenSelectionChange) {
        this.updateRoiDisplayWhenSelectionChange = updateRoiDisplayWhenSelectionChange;
    }
    
    public void setStructure(int structureIdx) {
        structurePathToRoot = db.getExperiment().getPathToRoot(structureIdx);
        if (structureIdx<0) {
            for (TrackTreeGenerator t : displayedGeneratorS.values()) t.clearTree();
            displayedGeneratorS.clear();
            return;
        }
        displayedGeneratorS.clear();
        for (int s: structurePathToRoot) displayedGeneratorS.put(s, allGeneratorS.get(s));
        updateParentTracks();
        if (logger.isTraceEnabled()) logger.trace("track tree controller set structure: number of generators: {}", displayedGeneratorS.size());
    }
    
    private int getLastTreeIdxWithSelection() {
        for (int i = structurePathToRoot.length-1; i>=0; --i) {
            if (displayedGeneratorS.get(structurePathToRoot[i]).hasSelection()) return i;
        }
        return -1;
    }
    
    public void updateParentTracks() {
        int lastTreeIdx = getLastTreeIdxWithSelection();
        if (lastTreeIdx+1<structurePathToRoot.length && !displayedGeneratorS.get(structurePathToRoot[lastTreeIdx+1]).hasSelection()) {
            updateParentTracks(lastTreeIdx);
        }
    }
    /**
     * Updates the parent track for the tree after {@param lastSelectedTreeIdx} and clear the following trees.
     * @param lastSelectedTreeIdx 
     */
    public void updateParentTracks(int lastSelectedTreeIdx) {
        //logger.debug("update parent track lastSelectedIdx: {} number of structures: {}", lastSelectedTreeIdx, structurePathToRoot.length);
        if (lastSelectedTreeIdx==-1) setParentTrackOnRootTree();
        else if (lastSelectedTreeIdx+1<structurePathToRoot.length) {
            //logger.debug("setting parent track on tree for structure: {}", structurePathToRoot[lastSelectedTreeIdx+1]);
            allGeneratorS.get(structurePathToRoot[lastSelectedTreeIdx+1]).setParentTrack(allGeneratorS.get(structurePathToRoot[lastSelectedTreeIdx]).getSelectedTrack(), structurePathToRoot[lastSelectedTreeIdx+1]);
            clearTreesFromIdx(lastSelectedTreeIdx+2);
        }
    }
    
    public void clearTreesFromIdx(int treeIdx) {
        for (int i = treeIdx; i < structurePathToRoot.length; ++i) {
            logger.debug("clearing tree for structure: {}", structurePathToRoot[i]);
            allGeneratorS.get(structurePathToRoot[i]).clearTree();
        }
    }
    
    public void setParentTrackOnRootTree() {
        allGeneratorS.get(structurePathToRoot[0]).setRootParentTrack(false, structurePathToRoot[0]);
        clearTreesFromIdx(1);
    }
    
    public int getTreeIdx(int structureIdx) {
        for (int i = 0; i<structurePathToRoot.length; ++i) if (structureIdx==structurePathToRoot[i]) return i;
        return 0;
    }
    
    public TrackTreeGenerator getLastTreeGenerator() {
        if (displayedGeneratorS.isEmpty()) return null;
        return displayedGeneratorS.lastEntry().getValue();
    }
    
    public TrackTreeGenerator getTreeGenerator(int structureIdx) {
        return allGeneratorS.get(structureIdx);
    }
    
    /*public ArrayList<JTree> getTrees() {
        ArrayList<JTree> res = new ArrayList<JTree>(structurePathToRoot.length);
        for (TrackTreeGenerator generator : generatorS.values()) if (generator.getTree()!=null) res.add(generator.getTree());
        return res;
    }*/
    
    public TreeMap<Integer, TrackTreeGenerator> getDisplayedGeneratorS() {
        return displayedGeneratorS;
    }
    
    public void selectTracks(List<StructureObject> trackHeads, boolean addToCurrentSelection) {
        if (trackHeads==null) {
            if (!addToCurrentSelection) this.getLastTreeGenerator().selectTracks(null, addToCurrentSelection);
            return;
        } else if (trackHeads.isEmpty()) return;
        int structureIdx = StructureObjectUtils.getStructureIdx(trackHeads);
        if (structureIdx == -2) throw new IllegalArgumentException("TrackHeads have different structure indicies");
        // TODO : select parent tracks in previous trees
        allGeneratorS.get(structureIdx).selectTracks(trackHeads, addToCurrentSelection);
    }
    public void deselectTracks(List<StructureObject> trackHeads) {
        if (trackHeads==null) return;
        else if (trackHeads.isEmpty()) return;
        int structureIdx = StructureObjectUtils.getStructureIdx(trackHeads);
        logger.debug("unselect : {} tracks from structure: {}", trackHeads.size(), structureIdx);
        if (structureIdx == -2) throw new IllegalArgumentException("TrackHeads have different structure indicies");
        allGeneratorS.get(structureIdx).deselectTracks(trackHeads);
    }
    public void deselectAllTracks(int structureIdx) {
        allGeneratorS.get(structureIdx).deselectAllTracks();
    }
    public void resetHighlight() {
        for (TrackTreeGenerator t : allGeneratorS.values()) t.resetHighlightedObjects();
        for (TrackTreeGenerator t : this.displayedGeneratorS.values()) if (t.tree!=null) t.tree.updateUI();
    }
    public String getSelectedPosition() {
        if (displayedGeneratorS.isEmpty()) return null;
        int count = displayedGeneratorS.get(0).tree.getSelectionCount();
        if (count!=1) return null;
        return displayedGeneratorS.get(0).getSelectedPosition();
    }
    public void selectPosition(String position) {
        if (displayedGeneratorS.isEmpty()) return;
        int count = displayedGeneratorS.get(0).tree.getSelectionCount();
        if (count>1) return;
        displayedGeneratorS.get(0).selectTracks(new ArrayList<StructureObject>(){{add(displayedGeneratorS.get(0).getObjectDAO(position).getRoot(0));}}, false);
    }
}
