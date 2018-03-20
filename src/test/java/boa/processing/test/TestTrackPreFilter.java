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
import boa.gui.GUI;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.gui.imageInteraction.TrackMaskX;
import boa.plugins.ProcessingScheme;
import boa.plugins.TrackParametrizable;
import boa.plugins.TrackParametrizable.TrackParametrizer;
import static boa.test_utils.TestUtils.logger;
import boa.utils.Utils;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jollion
 */
public class TestTrackPreFilter {
    public static final Logger logger = LoggerFactory.getLogger(TestTrackPreFilter.class);
    public static void main(String[] args) {
        
        //String dbName = "170919_thomas";
        //String dbName = "AyaWT_mmglu";
        String dbName = "WT_150609";
        int pIdx =0;
        if (new Task(dbName).getDir()==null) {
            logger.error("DB {} not found", dbName);
            return;
        }
        GUI.getInstance().setDBConnection(dbName, new Task(dbName).getDir(), true); // so that manual correction shortcuts work
        
        int structureIdx =1;
        int[] frames = new int[]{0, 1000};
        MasterDAO db = GUI.getDBConnection();
        ImageWindowManagerFactory.getImageManager().setDisplayImageLimit(1000);
        ProcessingScheme ps = db.getExperiment().getStructure(structureIdx).getProcessingScheme();
        ObjectDAO dao = db.getDao(db.getExperiment().getPosition(pIdx).getName());
        
        int mcCount = StructureObjectUtils.getAllTracks(Processor.getOrCreateRootTrack(dao), 0).size();
        mcCount = 1;
        for (int i =0; i<mcCount; ++i) {
            logger.debug("testing mc: {}", i);
            test(dao, ps, structureIdx, i, frames);
            
        }
        
    }
    public static void test(ObjectDAO dao, ProcessingScheme ps, int structureIdx, int mcIdx, int[] frames) {
        List<StructureObject> roots = Processor.getOrCreateRootTrack(dao);
        List<StructureObject> parentTrack=null;
        if (structureIdx==0) {
            parentTrack = roots;
            roots.removeIf(o -> o.getFrame()<frames[0] || o.getFrame()>frames[1]);
            ps.getTrackPreFilters(true).filter(structureIdx, parentTrack, null);
        }
        else {
            parentTrack = Utils.getFirst(StructureObjectUtils.getAllTracks(roots, 0), o->o.getIdx()==mcIdx&& o.getFrame()<=frames[1]);
            parentTrack.removeIf(o -> o.getFrame()<frames[0] || o.getFrame()>frames[1]);
            ps.getTrackPreFilters(true).filter(structureIdx, parentTrack, null);
            TrackMaskX tm = new TrackMaskX(parentTrack, structureIdx, false);
            tm.setDisplayPreFilteredImages(true);
            ImageWindowManagerFactory.showImage(tm.generatemage(structureIdx, false).setName("track:"+parentTrack.get(0)));
        }
        TrackParametrizer  tp = TrackParametrizable.getTrackParametrizer(structureIdx, parentTrack, ps.getSegmenter(), null);
        
        //return ps.getSegmenter()
    } 
}
