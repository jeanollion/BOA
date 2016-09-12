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
        //String dbName = "testSub60";
        final String dbName = "boa_phase140115mutH";
        int fIdx = 0;
        int mcIdx =7;
        int structureIdx = 1;
        MasterDAO db = new MorphiumMasterDAO(dbName);
        if (db.getExperiment()==null) return;
        ProcessingScheme ps = db.getExperiment().getStructure(structureIdx).getProcessingScheme();
        testSegmentationAndTracking(db.getDao(db.getExperiment().getMicroscopyField(fIdx).getName()), ps, structureIdx, mcIdx, 0, 720);
        int[][] testsF_MC_TT = {
           {0, 3, 90, 94},
           {0, 5, 48, 52}, 
           {0, 5, 103, 107}, 
           {0, 7, 716, 720}, 
           {1, 2, 90, 94}, 
           {1, 2, 195, 199}
        };
        int idx = 4;
        //testSegmentationAndTracking(db.getDao(db.getExperiment().getMicroscopyField(testsF_MC_TT[idx][0]).getName()), ps, structureIdx, testsF_MC_TT[idx][1], testsF_MC_TT[idx][2], testsF_MC_TT[idx][3]);
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
        BacteriaClosedMicrochannelTrackerLocalCorrections.debug=true;
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
    
}
