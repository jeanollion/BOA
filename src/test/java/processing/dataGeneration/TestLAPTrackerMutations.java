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
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.TextRoi;
import image.Image;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import plugins.PluginFactory;
import plugins.plugins.trackers.LAPTracker;
import plugins.plugins.trackers.trackMate.SpotWithinCompartment;
import utils.ThreadRunner;
import utils.ThreadRunner.ThreadAction;

/**
 *
 * @author jollion
 */
public class TestLAPTrackerMutations {
    MorphiumMasterDAO db;
    List<StructureObject> parentTrack;
    static final int microchannelIdx = 0;
    static final int bacteriaIdx = 1;
    static final int mutationIdx = 2;
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        final String dbName = "fluo151127";
        //int fieldIdx = 28; // lapTop
        final int fieldIdx = 0;
        final TestLAPTrackerMutations[] tests = new TestLAPTrackerMutations[1];
        long t0 = System.currentTimeMillis();
        for (int i = 0; i<tests.length; ++i) {
            tests[i] = new TestLAPTrackerMutations();
            long t00 = System.currentTimeMillis();
            tests[i].init(dbName, fieldIdx, 0, 0, 600);
            long t01 = System.currentTimeMillis();
            logger.debug("retrieve time: {}", t01-t00);
        }
        long t1 = System.currentTimeMillis();
        ThreadRunner.execute(tests, false, new ThreadAction<TestLAPTrackerMutations>() {
            public void run(TestLAPTrackerMutations object, int idx, int threadIdx) {
                object.testLAPTracking();
            }
        });
        long t2 = System.currentTimeMillis();
        logger.debug("total processing time: {}, retrieve time: {}", t2-t1, t1-t0);
    }
    
    public void testLAPTracking() {
        
        
        IJImageWindowManager windowManager = new IJImageWindowManager(null);
        ImageObjectInterface iB = windowManager.getImageTrackObjectInterface(parentTrack, bacteriaIdx);
        iB.setGUIMode(false);
        Image im = iB.generateRawImage(mutationIdx);
        ImagePlus ip = windowManager.getDisplayer().showImage(im);
        Overlay o = new Overlay(); ip.setOverlay(o);
        SpotWithinCompartment.bacteria=iB;
        SpotWithinCompartment.testOverlay=o;
        TextRoi.setFont("SansSerif", 6, Font.PLAIN);
        
        LAPTracker tracker = new LAPTracker().setLinkingMaxDistance(0, 0.75, 0.75);
        tracker.track(mutationIdx, parentTrack);
        
        Map<StructureObject, ArrayList<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(parentTrack, mutationIdx);
        logger.info("LAP tracker number of tracks: {}", allTracks.size());
        
        int colorIdx=0;
        ImageObjectInterface i = windowManager.getImageTrackObjectInterface(parentTrack, mutationIdx);
        i.setGUIMode(false);
        
        for (ArrayList<StructureObject> track : allTracks.values()) windowManager.displayTrack(im, i, i.pairWithOffset(StructureObjectUtils.extendTrack(track)), ImageWindowManager.getColor(colorIdx++), false);
        windowManager.displayObjects(im, iB.getObjects(), null, false);
        windowManager.displayObjects(im, i.getObjects(), null, false);
                
    }
    
    
    public void init(String dbName, int fieldIdx, int mcIdx, int tStart, int tEnd) {
        db = new MorphiumMasterDAO(dbName);
        logger.info("Experiment: {} retrieved from db: {}", db.getExperiment().getName(), dbName);
        MorphiumObjectDAO dao = db.getDao(db.getExperiment().getMicroscopyField(fieldIdx).getName());
        parentTrack = new ArrayList<StructureObject>(tEnd-tStart+1);
        for (int t = tStart; t<=tEnd; ++t) {
            StructureObject root = dao.getRoot(t);
            StructureObject mc = root.getChildren(microchannelIdx).get(mcIdx);
            parentTrack.add(mc);
            // load all the data
            mc.getChildren(mutationIdx);
            mc.getChildren(bacteriaIdx);
            mc.getRawImage(mutationIdx);
        }
    }
}
