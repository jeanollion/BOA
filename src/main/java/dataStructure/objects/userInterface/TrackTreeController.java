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
import dataStructure.configuration.ExperimentDAO;
import dataStructure.objects.ObjectDAO;
import java.util.ArrayList;
import java.util.HashMap;
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
    
    public TrackTreeController(ObjectDAO objectDAO, ExperimentDAO xpDAO) {
        this.objectDAO = objectDAO;
        this.xpDAO = xpDAO;   
    }
    
    public void setStructure(int structureIdx) {
        structurePathToRoot = xpDAO.getExperiment().getPathToRoot(structureIdx);
        HashMap<Integer, TrackTreeGenerator> newGeneratorS = new HashMap<Integer, TrackTreeGenerator>(xpDAO.getExperiment().getStructureNB());
        for (int s: structurePathToRoot) {
            if (generatorS!=null && generatorS.containsKey(s)) newGeneratorS.put(s, generatorS.get(s));
            else newGeneratorS.put(s, new TrackTreeGenerator(objectDAO, xpDAO, this));
        }
        generatorS=newGeneratorS;
        updateParentTracks();
        
    }
    
    private int getLastTreeIdxWithSingleSelection() {
        for (int i = structurePathToRoot.length-1; i>=0; --i) {
            if (generatorS.get(structurePathToRoot[i]).hasSelection()) return i;
        }
        return -1;
    }
    
    private void updateParentTracks() {
        int lastTreeIdx = getLastTreeIdxWithSingleSelection();
        if (lastTreeIdx>=0) {
            if (lastTreeIdx+1<structurePathToRoot.length && !generatorS.get(structurePathToRoot[lastTreeIdx+1]).hasSelection()) {
                updateParentTracks(lastTreeIdx);
            }
        } else clearTreeFromIdx(1);
    }
    /**
     * Updates the parent track for the tree after {@param lastSelectedTreeIdx} and clear the following trees.
     * @param lastSelectedTreeIdx 
     */
    public void updateParentTracks(int lastSelectedTreeIdx) {
        if (lastSelectedTreeIdx+1<structurePathToRoot.length) {
            logger.debug("setting parent track on tree for structure: {}", structurePathToRoot[lastSelectedTreeIdx+1]);
            generatorS.get(structurePathToRoot[lastSelectedTreeIdx+1]).setParentTrack(generatorS.get(structurePathToRoot[lastSelectedTreeIdx]).getSelectedTrack(), structurePathToRoot[lastSelectedTreeIdx+1]);
            clearTreeFromIdx(lastSelectedTreeIdx+2);
        }
    }
    
    public void clearTreeFromIdx(int treeIdx) {
        for (int i = treeIdx; i < structurePathToRoot.length; ++i) {
            logger.debug("clearing tree for structure: {}", structurePathToRoot[i]);
            generatorS.get(structurePathToRoot[i]).clearTree();
        }
    }
    
    public void setParentTrackOnRootTree() {
        generatorS.get(structurePathToRoot[0]).setRootParentTrack(false, structurePathToRoot[0]);
        clearTreeFromIdx(1);
    }
    
    public int getTreeIdx(int structureIdx) {
        for (int i = 0; i<structurePathToRoot.length; ++i) if (structureIdx==structurePathToRoot[i]) return i;
        return 0;
    }
    
    public ArrayList<JTree> getTrees() {
        ArrayList<JTree> res = new ArrayList<JTree>(structurePathToRoot.length);
        for (TrackTreeGenerator generator : generatorS.values()) if (generator.getTree()!=null) res.add(generator.getTree());
        return res;
    }
}
