/*
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.processing.test;

import boa.core.Processor;
import boa.core.Task;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.ObjectDAO;
import boa.ui.GUI;
import static boa.ui.PluginConfigurationUtils.displayIntermediateImages;
import static boa.ui.PluginConfigurationUtils.testImageProcessingPlugin;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.plugins.ImageProcessingPlugin;
import boa.plugins.PluginFactory;
import boa.plugins.TestableProcessingPlugin;
import static boa.processing.test.TestTracker.trackPrefilterRange;
import static boa.test_utils.TestUtils.logger;
import boa.utils.Utils;
import ij.ImageJ;
import java.util.List;
import java.util.Map;
import boa.plugins.ProcessingPipeline;
import boa.plugins.plugins.processing_pipeline.SegmentationAndTrackingProcessingPipeline;

/**
 *
 * @author Jean Ollion
 */
public class TestImageProcessingPlugin {
    public static void main(String[] args) {
        PluginFactory.findPlugins("boa.plugins.plugins");
        new ImageJ();
        String dbName = "140115";
        //String dbName = "RecA_180606";
        //String dbName = "MF1_180509";
        //String dbName = "fluo160501_uncorr_TestParam";
        //String dbName = "WT_180504";
        //String dbName = "MF1_180509";
        //String dbName = "MutH_151220";
        //String dbName = "WT_150616";
        //String dbName = "WT_150609";
        //String dbName = "WT_180318_Fluo";
        //String dbName = "Aya_170324";
        //String dbName = "Aya_180315";
        //String dbName = "170919_glyc_lac";
        boolean segmentation = true;
        boolean track = false;
        int structureIdx =1;
        
        int pIdx =0;
        int mcIdx =4;
        int[] frames = new int[]{715,755}; 
        
        //BacteriaClosedMicrochannelTrackerLocalCorrections.bactTestFrame=4;
        if (new Task(dbName).getDir()==null) {
            logger.error("DB {} not found", dbName);
            return;
        }
        GUI.getInstance().openExperiment(dbName, new Task(dbName).getDir(), true); // so that manual correction shortcuts work
        MasterDAO db = GUI.getDBConnection();
        ImageWindowManagerFactory.getImageManager().setDisplayImageLimit(1000);
        ProcessingPipeline ps = db.getExperiment().getStructure(structureIdx).getProcessingScheme();
        ObjectDAO dao = db.getDao(db.getExperiment().getPosition(pIdx).getName());
        List<StructureObject> roots = Processor.getOrCreateRootTrack(dao);
        int parentSIdx = dao.getExperiment().getStructure(structureIdx).getParentStructure();
        List<StructureObject> parentTrack=null;
        if (parentSIdx==-1) {
            parentTrack = roots;
            roots.removeIf(o -> o.getFrame()<frames[0] || o.getFrame()>frames[1]);
        }
        else {
            parentTrack = Utils.getFirst(StructureObjectUtils.getAllTracks(roots, parentSIdx), o->o.getIdx()==mcIdx&& o.getFrame()<=frames[1]);
            parentTrack.removeIf(o -> o.getFrame()<frames[0] || o.getFrame()>frames[1]);
        }
        ImageProcessingPlugin p = track && (ps instanceof SegmentationAndTrackingProcessingPipeline) ? ((SegmentationAndTrackingProcessingPipeline)ps).getTracker() : ps.getSegmenter(); 
        Map<StructureObject, TestableProcessingPlugin.TestDataStore> stores = testImageProcessingPlugin(p, db.getExperiment(), structureIdx, parentTrack, track && !segmentation);
        if (stores!=null) displayIntermediateImages(stores, structureIdx);
    }
}
