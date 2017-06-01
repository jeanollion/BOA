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
import core.Task;
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
import plugins.plugins.trackers.MicrochannelTracker;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class TestTracker {
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        new ImageJ();
        //String dbName = "boa_fluo151127_test";
        //String dbName = "boa_mutH_140115";
        //final String dbName = "boa_phase140115mutH";
        //final String dbName = "boa_phase150324mutH";
        //String dbName = "boa_phase150616wt";
        String dbName = "fluo170517_MutH";
        //String dbName = "boa_fluo170207_150ms";
        int fIdx = 9;
        int mcIdx =0;
        int structureIdx = 0;
        MasterDAO db = new Task(dbName).getDB();
        ProcessingScheme ps = db.getExperiment().getStructure(structureIdx).getProcessingScheme();
        MicrochannelTracker.debug=true;
        MicrochannelProcessorPhase.debug=true;
        BacteriaClosedMicrochannelTrackerLocalCorrections.debugCorr=true;
        //BacteriaClosedMicrochannelTrackerLocalCorrections.debugThreshold = 270;
        //testSegmentationAndTracking(db.getDao(db.getExperiment().getPosition(fIdx).getName()), ps, structureIdx, mcIdx, 0, 5);
        testSegmentationAndTracking(db.getDao(db.getExperiment().getPosition(fIdx).getName()), ps, structureIdx, mcIdx, 229, 234);
        //testBCMTLCStep(db.getDao(db.getExperiment().getPosition(fIdx).getName()), ps, structureIdx, mcIdx, 37, 38); // 91 to test rearrange objects 
    }
    public static void testSegmentationAndTracking(ObjectDAO dao, ProcessingScheme ps, int structureIdx, int mcIdx, int tStart, int tEnd) {
        test(dao, ps, false, structureIdx, mcIdx, tStart, tEnd);
    }
    public static void testTracking(ObjectDAO dao, ProcessingScheme ps, int structureIdx, int mcIdx, int tStart, int tEnd) {
        test(dao, ps, true, structureIdx, mcIdx, tStart, tEnd);
    }
    
    public static void test(ObjectDAO dao, ProcessingScheme ps, boolean trackOnly, int structureIdx, int mcIdx, int tStart, int tEnd) {
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
        
        
        //BacteriaClosedMicrochannelTrackerLocalCorrections.debug=true;
        //BacteriaClosedMicrochannelTrackerLocalCorrections.verboseLevelLimit=1;
        if (trackOnly) ps.trackOnly(structureIdx, parentTrack);
        else ps.segmentAndTrack(structureIdx, parentTrack);
        logger.debug("children: {} ({})", StructureObjectUtils.getAllTracks(parentTrack, 0).size(), Utils.toStringList( StructureObjectUtils.getAllTracks(parentTrack, 0).values(), o->o.size()));

        GUI.getInstance();
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        
        ImageObjectInterface i = iwm.getImageTrackObjectInterface(parentTrack, structureIdx);
        Image im = i.generateRawImage(structureIdx);
        iwm.addImage(im, i, structureIdx, false, true);
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
            iwm.addImage(im, i, structureIdx, false, true);
            iwm.setInteractiveStructure(structureIdx);
            iwm.displayAllObjects(im);
            iwm.displayAllTracks(im);
        }
    }
    
}
