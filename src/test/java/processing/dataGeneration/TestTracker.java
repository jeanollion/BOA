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
import boa.gui.imageInteraction.ImageWindowManager.RoiModifier;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import core.Processor;
import core.Task;
import dataStructure.configuration.Experiment;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.MorphiumMasterDAO;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.Selection;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.TextRoi;
import image.Image;
import java.awt.Color;
import java.awt.Font;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import plugins.PluginFactory;
import plugins.ProcessingScheme;
import plugins.plugins.trackers.LAPTracker;
import plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections;
import plugins.plugins.trackers.MicrochannelProcessorPhase;
import plugins.plugins.trackers.MicrochannelTracker;
import plugins.plugins.trackers.trackMate.SpotWithinCompartmentRoiModifier;
import utils.Pair;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class TestTracker {
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        new ImageJ();
        //String dbName = "fluo160408_MutH";
        //String dbName = "fluo170602_uvrD";
        String dbName = "fluo160501";
        int fIdx = 0;
        int mcIdx =1;
        int structureIdx = 2;
        MasterDAO db = new Task(dbName).getDB();
        ProcessingScheme ps = db.getExperiment().getStructure(structureIdx).getProcessingScheme();
        MicrochannelTracker.debug=true;
        MicrochannelProcessorPhase.debug=true;
        BacteriaClosedMicrochannelTrackerLocalCorrections.debugCorr=true;
        //BacteriaClosedMicrochannelTrackerLocalCorrections.debugThreshold = 270;
        testSegmentationAndTracking(db.getDao(db.getExperiment().getPosition(fIdx).getName()), ps, structureIdx, mcIdx, 210, 230);
        //testBCMTLCStep(db.getDao(db.getExperiment().getPosition(fIdx).getName()), ps, structureIdx, mcIdx, 37, 38); // 91 to test rearrange objects 
    }
    public static void testSegmentationAndTracking(ObjectDAO dao, ProcessingScheme ps, int structureIdx, int mcIdx, int tStart, int tEnd) {
        test(dao, ps, false, structureIdx, mcIdx, tStart, tEnd);
    }
    public static void testTracking(ObjectDAO dao, ProcessingScheme ps, int structureIdx, int mcIdx, int tStart, int tEnd) {
        test(dao, ps, true, structureIdx, mcIdx, tStart, tEnd);
    }
    
    public static void test(ObjectDAO dao, ProcessingScheme ps, boolean trackOnly, int structureIdx, int mcIdx, int tStart, int tEnd) {
        List<StructureObject> roots = Processor.getOrCreateRootTrack(dao);
        List<StructureObject> parentTrack=null;
        if (structureIdx==0) {
            parentTrack = roots;
            roots.removeIf(o -> o.getFrame()<tStart || o.getFrame()>tEnd);
        }
        else {
            Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(roots, 0);
            logger.debug("all tracks: {}", allTracks.size());
            for (StructureObject th : allTracks.keySet()) {
                if (th.getIdx()==mcIdx && th.getFrame()<tEnd) {
                    if (parentTrack==null || parentTrack.isEmpty()) {
                        parentTrack = allTracks.get(th);
                        parentTrack.removeIf(o -> o.getFrame()<tStart || o.getFrame()>tEnd);
                        if (!parentTrack.isEmpty()) break;
                    }
                }
            }
        }
        logger.debug("parent track: {}", parentTrack.size());
        LAPTracker.registerTMI=true;
        //BacteriaClosedMicrochannelTrackerLocalCorrections.debug=true;
        //BacteriaClosedMicrochannelTrackerLocalCorrections.verboseLevelLimit=1;
        List<Pair<String, Exception>> l;
        if (trackOnly) l=ps.trackOnly(structureIdx, parentTrack, null);
        else l=ps.segmentAndTrack(structureIdx, parentTrack, null);
        for (Pair<String, Exception> p : l) logger.debug(p.key, p.value);
        logger.debug("children: {} ({})", StructureObjectUtils.getAllTracks(parentTrack, 0).size(), Utils.toStringList( StructureObjectUtils.getAllTracks(parentTrack, 0).values(), o->o.size()));

        GUI.getInstance();
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        if (LAPTracker.debugTMI!=null) iwm.setRoiModifier(new SpotWithinCompartmentRoiModifier(LAPTracker.debugTMI, 2));
        logger.debug("generating TOI");
        ImageObjectInterface i = iwm.getImageTrackObjectInterface(parentTrack, structureIdx);
        Image interactiveImage = i.generateRawImage(structureIdx, true);
        iwm.addImage(interactiveImage, i, structureIdx, false, true);
        
        
        if (structureIdx==2) {
            Collection<StructureObject> bact = Utils.flattenMap(StructureObjectUtils.getChildrenMap(parentTrack, 1));
            Selection bactS = new Selection("bact", dao.getMasterDAO());
            bactS.setColor("Grey");
            bactS.addElements(bact);
            bactS.setIsDisplayingObjects(true);
            GUI.getInstance().addSelection(bactS);
            GUI.updateRoiDisplayForSelections(interactiveImage, i);
            //ImageObjectInterface iB = iwm.getImageTrackObjectInterface(parentTrack, 1);
            //GUI.getInstance().getSelections()
            //iwm.displayObjects(im, iB.pairWithOffset(bact), Color.LIGHT_GRAY, false, false); // should remain on overlay! 
        }
        iwm.setInteractiveStructure(structureIdx);
        iwm.displayAllObjects(interactiveImage);
        iwm.displayAllTracks(interactiveImage);
        
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
        ps.segmentAndTrack(structureIdx, parentTrack, null);
        //ps.trackOnly(structureIdx, parentTrack);
        GUI.getInstance();
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        for (String name : BacteriaClosedMicrochannelTrackerLocalCorrections.stepParents.keySet()) {
            List<StructureObject> pt = BacteriaClosedMicrochannelTrackerLocalCorrections.stepParents.get(name);
            ImageObjectInterface i = iwm.getImageTrackObjectInterface(pt, structureIdx);
            Image im = i.generateRawImage(structureIdx, true);
            im.setName(name);
            iwm.addImage(im, i, structureIdx, false, true);
            iwm.setInteractiveStructure(structureIdx);
            iwm.displayAllObjects(im);
            iwm.displayAllTracks(im);
        }
    }
}
