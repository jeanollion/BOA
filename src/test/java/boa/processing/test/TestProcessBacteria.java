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
package boa.processing.test;

import static boa.test_utils.TestUtils.logger;
import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageDisplayer;
import boa.core.Task;
import boa.configuration.experiment.Position;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import ij.ImageJ;
import boa.image.Image;
import boa.image.ImageMask;
import boa.plugins.PluginFactory;
import boa.plugins.ProcessingScheme;
import boa.plugins.Segmenter;
import boa.plugins.TrackParametrizable;
import boa.plugins.plugins.segmenters.BacteriaIntensity;
import static boa.processing.test.TestProcessBacteriaPhase.trackPrefilterRange;
import boa.utils.Utils;
import java.util.List;

/**
 *
 * @author jollion
 */
public class TestProcessBacteria {
    static int bacteriaStructureIdx = 3;
    public static void main(String[] args) {
        PluginFactory.findPlugins("boa.plugins.plugins");
        new ImageJ();
        int field = 0;
        int microChannel =0;
        int time =0;
        
        
        //String dbName = "fluo171219_WT_750ms";
        //String dbName = "fluo170512_WT";
        String dbName = "WT_180318_Fluo";
        testSegBacteriesFromXP(dbName, field, time, microChannel);
    }
    
    public static void testSegBacteriesFromXP(String dbName, int fieldNumber, int timePoint, int microChannel) {
        MasterDAO mDAO = new Task(dbName).getDB();
        mDAO.setReadOnly(true);
        Position f = mDAO.getExperiment().getPosition(fieldNumber);
        int parentSIdx = mDAO.getExperiment().getStructure(bacteriaStructureIdx).getParentStructure();
        List<StructureObject> rootTrack = mDAO.getDao(f.getName()).getRoots();
        List<StructureObject> parentTrack = Utils.getFirst(StructureObjectUtils.getAllTracks(rootTrack, parentSIdx), o->o.getIdx()==microChannel);
        
        ProcessingScheme psc = mDAO.getExperiment().getStructure(bacteriaStructureIdx).getProcessingScheme();
        parentTrack.removeIf(o -> o.getFrame()<timePoint-trackPrefilterRange || o.getFrame()>timePoint+trackPrefilterRange);
        psc.getTrackPreFilters(true).filter(bacteriaStructureIdx, parentTrack, null);
        TrackParametrizable.TrackParametrizer<Segmenter> apply = TrackParametrizable.getTrackParametrizer(bacteriaStructureIdx, parentTrack, psc.getSegmenter(), null);
        parentTrack.removeIf(o -> o.getFrame()<timePoint || o.getFrame()>timePoint);
        
        
        StructureObject root = mDAO.getDao(f.getName()).getRoots().get(timePoint);
        logger.debug("field name: {}, root==null? {}", f.getName(), root==null);
        StructureObject mc = root.getChildren(parentSIdx).get(microChannel);
        Image input = mc.getPreFilteredImage(bacteriaStructureIdx);
        BacteriaIntensity.verbose=true;
        Segmenter seg = mDAO.getExperiment().getStructure(bacteriaStructureIdx).getProcessingScheme().getSegmenter();
        if (apply !=null) apply.apply(mc, seg);
        
        ImageWindowManagerFactory.showImage(input);
        RegionPopulation pop = seg.runSegmenter(input, bacteriaStructureIdx, mc);
        ImageWindowManagerFactory.showImage(input);
        ImageWindowManagerFactory.showImage(pop.getLabelMap());
        
        // test split
        //ObjectPopulation popSplit = testObjectSplitter(intensityMap, pop.getChildren().get(0));
        //disp.showImage(popSplit.getLabelImage());
    }
}
