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

import static TestUtils.Utils.logger;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import core.Processor;
import core.Task;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import image.Image;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import utils.ThreadRunner;

/**
 *
 * @author jollion
 */
public class TestOpenTiles {
    public static void main(String[] args) {
        String xpName = "fluo170517_MutH";
        MasterDAO db = new Task(xpName).getDB();
        String position = db.getExperiment().getPosition(0).getName();
        ObjectDAO dao = db.getDao(position);
        int frameLimit = 50;
        
        //showImagesFromTrack(dao, frameLimit);
        /*
        long t00 = System.currentTimeMillis();
        db.getExperiment().getImageDAO().clearTrackImages(position, 0);
        
        long t0 = System.currentTimeMillis();
        openImages(dao, frameLimit, false, false);
        long t1 = System.currentTimeMillis();
        openImages(dao, frameLimit, false, false);
        long t11 = System.currentTimeMillis();
        openImages(dao, frameLimit, false, true);
        long t2 = System.currentTimeMillis();
        openImages(dao, frameLimit, false, true);
        long t22 = System.currentTimeMillis();
        Processor.generateTrackImages(dao, 0, 0);
        long t222 = System.currentTimeMillis();
        openImages(dao, frameLimit, false, false);
        long t3 = System.currentTimeMillis();
        openImages(dao, frameLimit, false, false);
        long t33 = System.currentTimeMillis();
        openImages(dao, frameLimit, false, true);
        long t4 = System.currentTimeMillis();
        openImages(dao, frameLimit, false, true);
        long t44 = System.currentTimeMillis();
        
        // test 
        
        logger.debug("from root images: {}/{}, idem multithread: {}/{}, from trrack images: {}/{}, idem multithread: {}/{}", t1-t0, t11-t1, t2-t11, t22-t2, t3-t222, t33-t3, t4-t33, t44-t4);
        logger.debug("track image: generate: {} clear: {}", t0-t00, t222-t22);
                */
    }
    private static void openImages(ObjectDAO dao, int frameLimit, boolean loadRootImagesFisrt, boolean multithread) {
        dao.clearCache();
        logger.debug("open image load root: {}, mt: {}", loadRootImagesFisrt, multithread);
        List<StructureObject> roots = dao.getRoots();
        roots.removeIf(o->o.getFrame()>frameLimit);
        long t0 = System.currentTimeMillis();
        if (loadRootImagesFisrt) {
            if (multithread) ThreadRunner.execute(roots, false, (o, idx)->o.getRawImage(0));
            else for (StructureObject r : roots) r.getRawImage(0);
        }
        long t1 = System.currentTimeMillis();
        logger.debug("root loaded");
        List<StructureObject> mcs = StructureObjectUtils.getAllChildren(roots, 0);
        //Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(roots, 0);
        //logger.debug("mc tracks: {}", allTracks.size());
        //List<StructureObject> mcs = allTracks.values().iterator().next();
        Collections.shuffle(mcs);
        long t2 = System.currentTimeMillis();
        if (multithread) ThreadRunner.execute(mcs, multithread, (o, idx)->o.getRawImage(0));
        else for (StructureObject o : mcs) o.getRawImage(0);
        long t3 = System.currentTimeMillis();
        logger.debug("open root: {}, getObjects: {}, openImages: {}", t1-t0, t2-t1, t3-t2);
    }
    private static void showImagesFromTrack(ObjectDAO dao, int frameLimit) {
        dao.clearCache();
        List<StructureObject> roots = dao.getRoots();
        roots.removeIf(o->o.getFrame()>frameLimit);
        Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(roots, 0);
        logger.debug("mc tracks: {}", allTracks.size());
        List<StructureObject> mcs = allTracks.values().iterator().next();
        if (mcs.get(0).getTrackImage(0)==null) {
            // generate track image, save and clear cache
            Processor.generateTrackImages(dao, 0, 0);
            dao.clearCache();
            roots = dao.getRoots();
            roots.removeIf(o->o.getFrame()>frameLimit);
            allTracks = StructureObjectUtils.getAllTracks(roots, 0);
            mcs = allTracks.values().iterator().next();
        }
        Image[][] mcImage = new Image[frameLimit][1];
        for (int i = 0; i<frameLimit; ++i) {
            mcImage[i][0] = mcs.get(i).getRawImage(0);
        }
        ImageWindowManagerFactory.instanciateDisplayer().showImage5D("mc 1 ", mcImage);
    }
}
