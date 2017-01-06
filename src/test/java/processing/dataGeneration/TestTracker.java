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

import static TestUtils.Utils.logger;
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
import plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections;
import plugins.plugins.trackers.MicrochannelProcessorPhase;

/**
 *
 * @author jollion
 */
public class TestTracker {
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        new ImageJ();
        //String dbName = "boa_mutH_140115";
        //final String dbName = "boa_phase140115mutH";
        //final String dbName = "boa_phase150324mutH";
        String dbName = "boa_phase150616wt";
        //String dbName = "boa_phase141107wt";
        int fIdx = 0;
        int mcIdx =0;
        int structureIdx = 1;
        MasterDAO db = new MorphiumMasterDAO(dbName);
        ProcessingScheme ps = db.getExperiment().getStructure(structureIdx).getProcessingScheme();
        //BacteriaClosedMicrochannelTrackerLocalCorrections.debugThreshold = 270;
        testSegmentationAndTracking(db.getDao(db.getExperiment().getPosition(fIdx).getName()), ps, structureIdx, mcIdx, 0, 1000);
        
        //testBCMTLCStep(db.getDao(db.getExperiment().getPosition(fIdx).getName()), ps, structureIdx, mcIdx, 37, 38); // 91 to test rearrange objects 
        
        int[][] testsF_MC_TT = {
           {0, 3, 90, 100}, // 0
           {0, 5, 48, 52}, // 1
           {0, 5, 103, 107}, // 2
           {0, 7, 716, 721}, // 3 cas cellules qui ne croissent plus + cellule mère morte
           {0, 14, 150, 166}, // 4
           {1, 2, 90, 94}, // 5 cas division de longue bacterie non reconnu car petite erreur de segmentation
           {1, 2, 195, 199}, // 6 petits objects avec découpage aléatoire. si on limite les scenario pas de bug
           {0, 3, 62, 64}, // 7
           {1, 2, 113, 115}, // 8 cas petite erreur de seg qui cree une fausse division
           {0, 3, 138, 140}, // 9
           {0, 9, 249, 250}, // 10 cas besoin d'incrementer prev et cur en même temps
           {0, 9, 425, 427}, // 11 cas split scenario doit s'arreter avt car division non detectee (au bout d'un channel avec soeur non detectee)
           {0, 0, 416, 443}, // 12 accumulation, emballement des réparations 
           {0, 0, 78, 80} // 13 split and merge
        };
        int idxStartInc = 5; // for adaptative sizeIncrement Estimation
        int idx =13;
        //testSegmentationAndTracking(db.getDao(db.getExperiment().getMicroscopyField(testsF_MC_TT[idx][0]).getName()), ps, structureIdx, testsF_MC_TT[idx][1], Math.max(0, testsF_MC_TT[idx][2]-idxStartInc), testsF_MC_TT[idx][3]);
    }
    public static void testSegmentationAndTracking(ObjectDAO dao, ProcessingScheme ps, int structureIdx, int mcIdx, int tStart, int tEnd) {
        List<StructureObject> roots = dao.getRoots();
        
        List<StructureObject> parentTrack=null;
        if (structureIdx==0) {
            parentTrack = roots;
            roots.removeIf(o -> o.getFrame()<tStart || o.getFrame()>tEnd);
        }
        else {
            Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(roots, 0);
            for (StructureObject th : allTracks.keySet()) {
                if (th.getIdx()==mcIdx && th.getFrame()<tEnd) {
                    if (parentTrack==null || parentTrack.isEmpty()) {
                        parentTrack = allTracks.get(th);
                        parentTrack.removeIf(o -> o.getFrame()<tStart || o.getFrame()>tEnd);
                    }
                }
            }
        }
        MicrochannelProcessorPhase.debug=true;
        BacteriaClosedMicrochannelTrackerLocalCorrections.debugCorr=true;
        //BacteriaClosedMicrochannelTrackerLocalCorrections.debug=true;
        //BacteriaClosedMicrochannelTrackerLocalCorrections.verboseLevelLimit=1;
        ps.segmentAndTrack(structureIdx, parentTrack);
        logger.debug("children: {}", StructureObjectUtils.getAllTracks(parentTrack, 0));
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
            roots.removeIf(o -> o.getFrame()<tStart || o.getFrame()>tEnd);
        }
        else {
            Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(roots, 0);
            for (StructureObject th : allTracks.keySet()) {
                if (th.getIdx()==mcIdx && th.getFrame()<tEnd) {
                    if (parentTrack==null || parentTrack.isEmpty()) {
                        parentTrack = allTracks.get(th);
                        parentTrack.removeIf(o -> o.getFrame()<tStart || o.getFrame()>tEnd);
                        //logger.debug("parents: {}", parentTrack);
                        //return;
                    }
                }
            }
        }
        BacteriaClosedMicrochannelTrackerLocalCorrections.debugCorr=true;
        BacteriaClosedMicrochannelTrackerLocalCorrections.debug=true;
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
