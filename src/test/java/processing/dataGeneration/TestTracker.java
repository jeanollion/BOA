/*
 * Copyright (C) 2016 jollion
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
package processing.dataGeneration;

import boa.gui.GUI;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageWindowManager;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import dataStructure.configuration.Experiment;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.MorphiumMasterDAO;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import ij.ImageJ;
import image.Image;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import plugins.PluginFactory;
import plugins.ProcessingScheme;
import plugins.plugins.trackers.BacteriaClosedMicrochannelTrackerLocalCorrections;

/**
 *
 * @author jollion
 */
public class TestTracker {
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        new ImageJ();
        //String dbName = "boa_mutH_140115";
        final String dbName = "boa_phase140115mutH";
        int fIdx = 0;
        int mcIdx =0;
        int structureIdx = 1;
        MasterDAO db = new MorphiumMasterDAO(dbName);
        if (db.getExperiment()==null) return;
        ProcessingScheme ps = db.getExperiment().getStructure(structureIdx).getProcessingScheme();
        //testSegmentationAndTracking(db.getDao(db.getExperiment().getMicroscopyField(fIdx).getName()), ps, structureIdx, mcIdx, 0, 28);
        testBCMTLCStep(db.getDao(db.getExperiment().getMicroscopyField(fIdx).getName()), ps, structureIdx, mcIdx, 78, 80); // 85-94
        
        int[][] testsF_MC_TT = {
           {0, 3, 90, 100}, // 0
           {0, 5, 48, 52}, // 1
           {0, 5, 103, 107}, // 2
           {0, 7, 716, 720}, // 3 cas cellules qui ne croissent plus + cellule mère morte
           {0, 14, 150, 166}, // 4
           {1, 2, 90, 94}, // 5 cas division de longue bacterie non reconnu car petite erreur de segmentation
           {1, 2, 195, 199}, // 6 petits objects avec découpage aléatoire. si on limite les scenario pas de bug
           {0, 3, 62, 64}, // 7
           {1, 2, 89, 90}, // 8 cas division de longue bacterie
           {1, 2, 114, 115}, // 9 cas petite erreur de seg qui cree une fausse division
           {0, 3, 138, 140}, // 10
           {0, 9, 249, 250}, // 11 cas besoin d'incrementer prev et cur en même temps
           {0, 9, 425, 427}, // 12 cas split scenario doit s'arreter avt car division non detectee (au bout d'un channel avec soeur non detectee)
           {0, 0, 416, 443}, // 13 accumulation, emballement des réparations 
           {0, 0, 78, 80} // 14 split and merge
        };
        int idxStartInc = 5; // for adaptative sizeIncrement Estimation
        int idx = 0;
        //testSegmentationAndTracking(db.getDao(db.getExperiment().getMicroscopyField(testsF_MC_TT[idx][0]).getName()), ps, structureIdx, testsF_MC_TT[idx][1], Math.max(0, testsF_MC_TT[idx][2]-idxStartInc), testsF_MC_TT[idx][3]);
    }
    public static void testSegmentationAndTracking(ObjectDAO dao, ProcessingScheme ps, int structureIdx, int mcIdx, int tStart, int tEnd) {
        List<StructureObject> roots = dao.getRoots();
        
        List<StructureObject> parentTrack=null;
        if (structureIdx==0) {
            parentTrack = roots;
            roots.removeIf(o -> o.getTimePoint()<tStart || o.getTimePoint()>tEnd);
        }
        else {
            Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(roots, 0);
            for (StructureObject th : allTracks.keySet()) {
                if (th.getIdx()==mcIdx && th.getTimePoint()<tEnd) {
                    if (parentTrack==null || parentTrack.isEmpty()) {
                        parentTrack = allTracks.get(th);
                        parentTrack.removeIf(o -> o.getTimePoint()<tStart || o.getTimePoint()>tEnd);
                    }
                }
            }
        }
        BacteriaClosedMicrochannelTrackerLocalCorrections.debugCorr=true;
        //BacteriaClosedMicrochannelTrackerLocalCorrections.debug=true;
        ps.segmentAndTrack(structureIdx, parentTrack);
        //ps.trackOnly(structureIdx, parentTrack);
        GUI.getInstance();
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        
        ImageObjectInterface i = iwm.getImageTrackObjectInterface(parentTrack, structureIdx);
        Image im = i.generateRawImage(structureIdx);
        iwm.addImage(im, i, false, true);
        iwm.setInteractiveStructure(structureIdx);
        iwm.displayAllObjects(im);
        iwm.displayAllTracks(im);
    }
    
    public static void testBCMTLCStep(ObjectDAO dao, ProcessingScheme ps, int structureIdx, int mcIdx, int tStart, int tEnd) {
        List<StructureObject> roots = dao.getRoots();
        
        List<StructureObject> parentTrack=null;
        if (structureIdx==0) {
            parentTrack = roots;
            roots.removeIf(o -> o.getTimePoint()<tStart || o.getTimePoint()>tEnd);
        }
        else {
            Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(roots, 0);
            for (StructureObject th : allTracks.keySet()) {
                if (th.getIdx()==mcIdx && th.getTimePoint()<tEnd) {
                    if (parentTrack==null || parentTrack.isEmpty()) {
                        parentTrack = allTracks.get(th);
                        parentTrack.removeIf(o -> o.getTimePoint()<tStart || o.getTimePoint()>tEnd);
                    }
                }
            }
        }
        BacteriaClosedMicrochannelTrackerLocalCorrections.debugCorr=true;
        //BacteriaClosedMicrochannelTrackerLocalCorrections.debug=true;
        BacteriaClosedMicrochannelTrackerLocalCorrections.correctionStep=true;
        BacteriaClosedMicrochannelTrackerLocalCorrections.verboseLevelLimit=1;
        ps.segmentAndTrack(structureIdx, parentTrack);
        //ps.trackOnly(structureIdx, parentTrack);
        GUI.getInstance();
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        for (String name : BacteriaClosedMicrochannelTrackerLocalCorrections.stepParents.keySet()) {
            List<StructureObject> pt = BacteriaClosedMicrochannelTrackerLocalCorrections.stepParents.get(name);
            ImageObjectInterface i = iwm.getImageTrackObjectInterface(pt, structureIdx);
            Image im = i.generateRawImage(structureIdx);
            im.setName(name);
            iwm.addImage(im, i, false, true);
            iwm.setInteractiveStructure(structureIdx);
            iwm.displayAllObjects(im);
            iwm.displayAllTracks(im);
        }
    }
    
}
