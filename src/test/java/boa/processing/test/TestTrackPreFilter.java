/*
 * Copyright (C) 2018 jollion
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

import boa.core.Processor;
import boa.core.Task;
import boa.data_structure.ProcessingTest;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.ObjectDAO;
import boa.ui.GUI;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.gui.image_interaction.KymographX;
import boa.plugins.plugins.track_pre_filters.SubtractBackgroundMicrochannels;
import static boa.test_utils.TestUtils.logger;
import boa.utils.Utils;
import ij.ImageJ;
import java.util.List;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import boa.plugins.ProcessingPipeline;
import boa.plugins.TrackConfigurable;
import boa.plugins.TrackConfigurable.TrackConfigurer;

/**
 *
 * @author jollion
 */
public class TestTrackPreFilter {
    public static final Logger logger = LoggerFactory.getLogger(TestTrackPreFilter.class);
    public static void main(String[] args) {
        new ImageJ();
        //String dbName = "170919_thomas";
        //String dbName = "AyaWT_mmglu";
        //String dbName = "WT_150616";
        String dbName = "MutH_151220";
        int pIdx =0;
        if (new Task(dbName).getDir()==null) {
            logger.error("DB {} not found", dbName);
            return;
        }
        GUI.getInstance().openExperiment(dbName, new Task(dbName).getDir(), true); // so that manual correction shortcuts work
        
        int structureIdx =1;
        int[] frames = new int[]{0, 1000};
        MasterDAO db = GUI.getDBConnection();
        ImageWindowManagerFactory.getImageManager().setDisplayImageLimit(1000);
        ProcessingPipeline ps = db.getExperiment().getStructure(structureIdx).getProcessingScheme();
        ObjectDAO dao = db.getDao(db.getExperiment().getPosition(pIdx).getName());
        //SubtractBackgroundMicrochannels.debug=true;
        int mcCount = StructureObjectUtils.getAllTracks(Processor.getOrCreateRootTrack(dao), 0).size();
        //mcCount = 1;
        int mcIdx = 9;
        IntStream.range(mcIdx, mcIdx+1).forEachOrdered(i-> {
            logger.debug("testing mc: {}", i);
            test(dao, ps, structureIdx, i, frames);
        });
        
    }
    public static void test(ObjectDAO dao, ProcessingPipeline ps, int structureIdx, int mcIdx, int[] frames) {
        List<StructureObject> roots = Processor.getOrCreateRootTrack(dao);
        List<StructureObject> parentTrack=null;
        if (structureIdx==0) {
            parentTrack = roots;
            roots.removeIf(o -> o.getFrame()<frames[0] || o.getFrame()>frames[1]);
            ps.getTrackPreFilters(true).filter(structureIdx, parentTrack);
        }
        else {
            parentTrack = Utils.getFirst(StructureObjectUtils.getAllTracks(roots, 0), o->o.getIdx()==mcIdx&& o.getFrame()<=frames[1]);
            parentTrack.removeIf(o -> o.getFrame()<frames[0] || o.getFrame()>frames[1]);
            ps.getTrackPreFilters(true).filter(structureIdx, parentTrack);
            KymographX tm = new KymographX(parentTrack, structureIdx, false);
            tm.setDisplayPreFilteredImages(true);
            //ImageWindowManagerFactory.showImage(tm.generatemage(structureIdx, false).setName("track:"+parentTrack.get(0)));
        }
        TrackConfigurer  tp = TrackConfigurable.getTrackConfigurer(structureIdx, parentTrack, ps.getSegmenter());
        
        //return ps.getSegmenter()
    } 
}
