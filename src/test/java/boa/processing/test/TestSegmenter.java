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
package boa.processing.test;

import static boa.test_utils.TestUtils.logger;
import boa.ui.GUI;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.core.Processor;
import boa.core.Task;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.ObjectDAO;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import ij.ImageJ;
import java.util.List;
import java.util.Map;
import boa.plugins.PluginFactory;
import boa.plugins.Segmenter;
import boa.plugins.plugins.processing_pipeline.SegmentOnly;
import boa.plugins.plugins.segmenters.WatershedSegmenter;
import boa.utils.Utils;
import boa.plugins.ProcessingPipeline;

/**
 *
 * @author jollion
 */
public class TestSegmenter {
    
    public static void main(String[] args) {
        PluginFactory.findPlugins("boa.plugins.plugins");
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
        GUI.getInstance().openExperiment(dbName, new Task(dbName).getDir(), true); // so that manual correction shortcuts work
        MasterDAO db = GUI.getDBConnection();
        
        ProcessingPipeline ps = db.getExperiment().getStructure(structureIdx).getProcessingScheme();
        testProcessing(db.getDao(db.getExperiment().getPosition(pIdx).getName()), ps, structureIdx, mcIdx, frame);
    }
    public static void testProcessing(ObjectDAO dao, ProcessingPipeline ps, int structureIdx, int mcIdx, int frame) {
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
        Map<String, StructureObject> gCutMap = StructureObjectUtils.createGraphCut(parentTrack, true, false); 
        logger.debug("parentTrack: {} ({})", parentTrack.get(0), parentTrack.size());
        parentTrack = Utils.transform(parentTrack, o->gCutMap.get(o.getId()));
        for (StructureObject p : parentTrack) p.setChildren(null, structureIdx);
        WatershedSegmenter.debug=true;
        ImageWindowManagerFactory.showImage(parentTrack.get(0).getRawImage(structureIdx).duplicate("Input"));
        if (ps instanceof SegmentOnly) {
            ((SegmentOnly)ps).segmentAndTrack(structureIdx, parentTrack, null);
            RegionPopulation pop = parentTrack.get(0).getChildRegionPopulation(structureIdx);
            ImageWindowManagerFactory.showImage(pop.getLabelMap());
        } else {
            Segmenter s = ps.getSegmenter();
        } // todo convert to segmentonly
        
    }
}
