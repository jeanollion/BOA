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
import image.Image;
import java.util.List;
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
        final String dbName = "boa_mutH_140115";
        int fIdx = 0;
        int mcIdx =0;
        int structureIdx = 1;
        MasterDAO db = new MorphiumMasterDAO(dbName);
        if (db.getExperiment()==null) return;
        ObjectDAO dao = db.getDao(db.getExperiment().getMicroscopyField(fIdx).getName());
        ProcessingScheme ps = db.getExperiment().getStructure(structureIdx).getProcessingScheme();
        testSegmentationAndTracking(dao, ps, structureIdx, mcIdx, 60, 64);
    }
    public static void testSegmentationAndTracking(ObjectDAO dao, ProcessingScheme ps, int structureIdx, int mcIdx, int tStart, int tEnd) {
        List<StructureObject> roots = dao.getRoots();
        roots.removeIf(o -> o.getTimePoint()<tStart || o.getTimePoint()>tEnd);
        List<StructureObject> parentTrack = roots.stream().map(o -> o.getChildren(0).get(mcIdx)).collect(Collectors.toList());
        BacteriaClosedMicrochannelTrackerLocalCorrections.debugCorr=true;
        ps.segmentAndTrack(structureIdx, parentTrack);
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
