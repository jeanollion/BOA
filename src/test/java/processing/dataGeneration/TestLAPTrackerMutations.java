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
import static boa.gui.imageInteraction.ImageWindowManager.extendTrack;
import dataStructure.objects.MorphiumMasterDAO;
import dataStructure.objects.MorphiumObjectDAO;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.TextRoi;
import image.Image;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import plugins.PluginFactory;
import plugins.plugins.trackers.LAPTracker;
import plugins.plugins.trackers.trackMate.SpotWithinCompartment;

/**
 *
 * @author jollion
 */
public class TestLAPTrackerMutations {
    MorphiumMasterDAO db;
    static final int microchannelIdx = 0;
    static final int bacteriaIdx = 1;
    static final int mutationIdx = 2;
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        String dbName = "fluo151127";
        TestLAPTrackerMutations t = new TestLAPTrackerMutations();
        t.init(dbName);
        t.testLAPTracking(0, 0, 0, 200);
    }
    
    public void testLAPTracking(int fieldIdx, int mcIdx, int tStart, int tEnd) {
        MorphiumObjectDAO dao = db.getDao(db.getExperiment().getMicroscopyField(fieldIdx).getName());
        ArrayList<StructureObject> parentTrack = new ArrayList<StructureObject>(tEnd-tStart+1);
        for (int t = tStart; t<=tEnd; ++t) {
            StructureObject root = dao.getRoot(t);
            StructureObject mc = root.getChildren(microchannelIdx).get(mcIdx);
            parentTrack.add(mc);
        }
        
        IJImageWindowManager windowManager = new IJImageWindowManager(null);
        ImageObjectInterface i = windowManager.getImageTrackObjectInterface(parentTrack, mutationIdx);
        ImageObjectInterface iB = windowManager.getImageTrackObjectInterface(parentTrack, bacteriaIdx);
        i.setGUIMode(false);
        iB.setGUIMode(false);
        Image im = i.generateRawImage(mutationIdx);
        ImagePlus ip = windowManager.getDisplayer().showImage(im);
        Overlay o = new Overlay(); ip.setOverlay(o);
        SpotWithinCompartment.bacteria=iB;
        SpotWithinCompartment.testOverlay=o;
        TextRoi.setFont("SansSerif", 6, Font.PLAIN);
        
        LAPTracker tracker = new LAPTracker();
        tracker.track(mutationIdx, parentTrack);
        
        HashMap<StructureObject, ArrayList<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(parentTrack, mutationIdx);
        logger.info("LAP tracker number of tracks: {}", allTracks.size());
        
        int colorIdx=0;
        
        for (ArrayList<StructureObject> track : allTracks.values()) windowManager.displayTrack(im, i, i.pairWithOffset(extendTrack(track)), ImageWindowManager.getColor(colorIdx++), false);
        windowManager.displayObjects(im, iB.getObjects(), null, false);
        windowManager.displayObjects(im, i.getObjects(), null, false);
    }
    
    
    public void init(String dbName) {
        db = new MorphiumMasterDAO(dbName);
        logger.info("Experiment: {} retrieved from db: {}", db.getExperiment().getName(), dbName);
    }
}
