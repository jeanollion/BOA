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
import dataStructure.configuration.ExperimentDAO;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import javax.swing.JTree;

/**
 *
 * @author nasique
 */
public class TrackTreeController {
    ObjectDAO objectDAO;
    ExperimentDAO xpDAO;
    HashMap<Integer, TrackTreeGenerator> generatorS;
    int[] structurePathToRoot;
    StructureObjectTreeGenerator objectGenerator;
    
    public TrackTreeController(ObjectDAO objectDAO, ExperimentDAO xpDAO, StructureObjectTreeGenerator objectGenerator) {
        this.objectDAO = objectDAO;
        this.xpDAO = xpDAO;   
        this.objectGenerator=objectGenerator;
    }
    
    public void setStructure(int structureIdx) {
        structurePathToRoot = xpDAO.getExperiment().getPathToRoot(structureIdx);
        HashMap<Integer, TrackTreeGenerator> newGeneratorS = new HashMap<Integer, TrackTreeGenerator>(xpDAO.getExperiment().getStructureCount());
        for (int s: structurePathToRoot) {
            if (generatorS!=null && generatorS.containsKey(s)) newGeneratorS.put(s, generatorS.get(s));
            else newGeneratorS.put(s, new TrackTreeGenerator(objectDAO, xpDAO, this));
        }
        generatorS=newGeneratorS;
        updateParentTracks();
        if (logger.isTraceEnabled()) logger.trace("track tree controller set structure: number of generators: {}", generatorS.size());
    }
    
    private int getLastTreeIdxWithSingleSelection() {
        for (int i = structurePathToRoot.length-1; i>=0; --i) {
            if (generatorS.get(structurePathToRoot[i]).hasSelection()) return i;
        }
        return -1;
    }
    
    private void updateParentTracks() {
        int lastTreeIdx = getLastTreeIdxWithSingleSelection();
        if (lastTreeIdx+1<structurePathToRoot.length && !generatorS.get(structurePathToRoot[lastTreeIdx+1]).hasSelection()) {
            updateParentTracks(lastTreeIdx);
        }
    }
    /**
     * Updates the parent track for the tree after {@param lastSelectedTreeIdx} and clear the following trees.
     * @param lastSelectedTreeIdx 
     */
    public void updateParentTracks(int lastSelectedTreeIdx) {
        logger.debug("update parent track lastSelectedIdx: {} number of structures: {}", lastSelectedTreeIdx, structurePathToRoot.length);
        if (lastSelectedTreeIdx==-1) setParentTrackOnRootTree();
        else if (lastSelectedTreeIdx+1<structurePathToRoot.length) {
            logger.debug("setting parent track on tree for structure: {}", structurePathToRoot[lastSelectedTreeIdx+1]);
            generatorS.get(structurePathToRoot[lastSelectedTreeIdx+1]).setParentTrack(generatorS.get(structurePathToRoot[lastSelectedTreeIdx]).getSelectedTrack(), structurePathToRoot[lastSelectedTreeIdx+1]);
            clearTreesFromIdx(lastSelectedTreeIdx+2);
        }
    }
    
    public void clearTreesFromIdx(int treeIdx) {
        for (int i = treeIdx; i < structurePathToRoot.length; ++i) {
            logger.debug("clearing tree for structure: {}", structurePathToRoot[i]);
            generatorS.get(structurePathToRoot[i]).clearTree();
        }
    }
    
    public void setParentTrackOnRootTree() {
        generatorS.get(structurePathToRoot[0]).setRootParentTrack(false, structurePathToRoot[0]);
        clearTreesFromIdx(1);
    }
    
    public int getTreeIdx(int structureIdx) {
        for (int i = 0; i<structurePathToRoot.length; ++i) if (structureIdx==structurePathToRoot[i]) return i;
        return 0;
    }
    
    public TrackTreeGenerator getLastTreeGenerator() {
        if (generatorS.isEmpty()) return null;
        int max = Integer.MIN_VALUE;
        for (int i : this.generatorS.keySet()) if (i>max) max=i;
        return generatorS.get(max);
    }
    
    /*public ArrayList<JTree> getTrees() {
        ArrayList<JTree> res = new ArrayList<JTree>(structurePathToRoot.length);
        for (TrackTreeGenerator generator : generatorS.values()) if (generator.getTree()!=null) res.add(generator.getTree());
        return res;
    }*/
    
    public HashMap<Integer, TrackTreeGenerator> getGeneratorS() {
        return generatorS;
    }
    
    public void selectTrack(StructureObject object) {
        for (Entry<Integer, TrackTreeGenerator> e : generatorS.entrySet()) if (e.getKey()!=object.getStructureIdx()) e.getValue().selectObject(null);
        if (generatorS.containsKey(object.getStructureIdx())) generatorS.get(object.getStructureIdx()).selectObject(object);
    }
}
