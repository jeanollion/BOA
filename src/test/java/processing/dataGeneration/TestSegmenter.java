/*
 * Copyright (C) 2017 jollion
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

import static TestUtils.TestUtils.logger;
import boa.gui.GUI;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import core.Processor;
import core.Task;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.RegionPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import ij.ImageJ;
import java.util.List;
import java.util.Map;
import plugins.PluginFactory;
import plugins.ProcessingScheme;
import plugins.Segmenter;
import plugins.plugins.processingScheme.SegmentOnly;
import plugins.plugins.segmenters.WatershedSegmenter;
import plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class TestSegmenter {
    
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        new ImageJ();
        
        String dbName = "fluo171219_WT_750ms";
        int pIdx = 0;
        int mcIdx =0;
        int frame = 0;
        int structureIdx = 1;
        
        if (new Task(dbName).getDir()==null) {
            logger.error("DB {} not found", dbName);
            return;
        }
        GUI.getInstance().setDBConnection(dbName, new Task(dbName).getDir(), true); // so that manual correction shortcuts work
        MasterDAO db = GUI.getDBConnection();
        
        ProcessingScheme ps = db.getExperiment().getStructure(structureIdx).getProcessingScheme();
        testProcessing(db.getDao(db.getExperiment().getPosition(pIdx).getName()), ps, structureIdx, mcIdx, frame);
    }
    public static void testProcessing(ObjectDAO dao, ProcessingScheme ps, int structureIdx, int mcIdx, int frame) {
        List<StructureObject> roots = Processor.getOrCreateRootTrack(dao);
        List<StructureObject> parentTrack=null;
        if (structureIdx==0) {
            parentTrack = roots;
            roots.removeIf(o -> o.getFrame()<frame || o.getFrame()>frame);
        }
        else {
            Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(roots, 0);
            logger.debug("all tracks: {}", allTracks.size());
            for (StructureObject th : allTracks.keySet()) {
                if (th.getIdx()==mcIdx && th.getFrame()<=frame) {
                    if (parentTrack==null || parentTrack.isEmpty()) {
                        parentTrack = allTracks.get(th);
                        parentTrack.removeIf(o -> o.getFrame()<frame || o.getFrame()>frame);
                        if (!parentTrack.isEmpty()) break;
                    }
                }
            }
        }
        Map<String, StructureObject> gCutMap = StructureObjectUtils.createGraphCut(parentTrack, true); 
        logger.debug("parentTrack: {} ({})", parentTrack.get(0), parentTrack.size());
        parentTrack = Utils.transform(parentTrack, o->gCutMap.get(o.getId()));
        for (StructureObject p : parentTrack) p.setChildren(null, structureIdx);
        WatershedSegmenter.debug=true;
        ImageWindowManagerFactory.showImage(parentTrack.get(0).getRawImage(structureIdx).duplicate("Input"));
        if (ps instanceof SegmentOnly) {
            ((SegmentOnly)ps).segmentAndTrack(structureIdx, parentTrack, null);
            RegionPopulation pop = parentTrack.get(0).getObjectPopulation(structureIdx);
            ImageWindowManagerFactory.showImage(pop.getLabelMap());
        } else {
            Segmenter s = ps.getSegmenter();
        } // todo convert to segmentonly
        
    }
}
