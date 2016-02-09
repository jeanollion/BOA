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
import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.IJImageWindowManager;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageWindowManager;
import dataStructure.objects.MorphiumMasterDAO;
import dataStructure.objects.MorphiumObjectDAO;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import image.Image;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import plugins.PluginFactory;
import plugins.plugins.trackers.LAPTracker;

/**
 *
 * @author jollion
 */
public class TestLAPTrackerMutations {
    MorphiumMasterDAO db;
    static final int microchannelIdx = 0;
    static final int mutationIdx = 2;
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        String dbName = "fluo151127";
        TestLAPTrackerMutations t = new TestLAPTrackerMutations();
        t.init(dbName);
        t.testLAPTracking(0, 0, 0, 10);
    }
    
    public void testLAPTracking(int fieldIdx, int mcIdx, int tStart, int tEnd) {
        MorphiumObjectDAO dao = db.getDao(db.getExperiment().getMicroscopyField(fieldIdx).getName());
        ArrayList<StructureObject> parentTrack = new ArrayList<StructureObject>(tEnd-tStart+1);
        for (int t = tStart; t<=tEnd; ++t) {
            StructureObject root = dao.getRoot(t);
            StructureObject mc = root.getChildren(microchannelIdx).get(mcIdx);
            parentTrack.add(mc);
        }
        
        LAPTracker tracker = new LAPTracker();
        tracker.track(mutationIdx, parentTrack);
        
        HashMap<StructureObject, ArrayList<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(parentTrack, mutationIdx);
        logger.info("LAP tracker number of tracks: {}", allTracks.size());

        IJImageWindowManager windowManager = new IJImageWindowManager(null);
        ImageObjectInterface i = windowManager.getImageTrackObjectInterface(parentTrack, mutationIdx);
        i.setGUIMode(false);
        int colorIdx=0;
        Image im = i.generateRawImage(mutationIdx);
        windowManager.getDisplayer().showImage(im);
        for (ArrayList<StructureObject> track : allTracks.values()) windowManager.displayTrack(im, i, true, track, ImageWindowManager.getColor(colorIdx++));
        windowManager.displayObjects(im, i, true, i.getObjects());
    }
    
    
    public void init(String dbName) {
        db = new MorphiumMasterDAO(dbName);
        logger.info("Experiment: {} retrieved from db: {}", db.getExperiment().getName(), dbName);
    }
}
