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
package boa.image.processing;

import boa.gui.image_interaction.IJImageDisplayer;
import boa.core.Task;
import boa.configuration.experiment.Position;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.data_structure.Voxel;
import boa.data_structure.dao.ObjectDAO;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.Offset;
import boa.image.SimpleOffset;
import boa.image.processing.bacteria_spine.BacteriaSpineFactory;
import boa.image.processing.bacteria_spine.BacteriaSpineCoord;
import boa.image.processing.bacteria_spine.BacteriaSpineFactory.SpineResult;
import boa.image.processing.bacteria_spine.BacteriaSpineLocalizer;
import static boa.image.processing.bacteria_spine.BacteriaSpineLocalizer.PROJECTION.NEAREST_POLE;
import static boa.image.processing.bacteria_spine.BacteriaSpineLocalizer.PROJECTION.PROPORTIONAL;
import boa.image.processing.bacteria_spine.CircularContourFactory;
import boa.image.processing.bacteria_spine.CircularNode;
import static boa.image.processing.bacteria_spine.CleanVoxelLine.cleanContour;
import boa.plugins.PluginFactory;
import static boa.test_utils.TestUtils.logger;
import boa.utils.HashMapGetCreate;
import boa.utils.HashMapGetCreate.Syncronization;
import boa.utils.Pair;
import boa.utils.geom.Point;
import boa.utils.geom.PointContainer2;
import boa.utils.geom.Vector;
import ij.ImageJ;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author jollion
 */
public class TestSpine {
    public static void main(String[] args) {
        PluginFactory.findPlugins("boa.plugins.plugins");
        new ImageJ();
        int structureIdx = 1;
        //String dbName = "AyaWT_mmglu";
        //String dbName = "MutH_150324";
        //String dbName = "WT_180318_Fluo";
        //String dbName = "WT_150609";
        //String dbName = "fluo160501_uncorr_TestParam";
        String dbName = "dataset2";
        int postition= 0, frame=469, mc=0, b=1, m = 2;
        //int postition= 3, frame=204, mc=4, b=0;
        //String dbName = "MutH_140115";
        //int postition= 24, frame=310, mc=0, b=1; // F=2 B=1
        
        MasterDAO mDAO = new Task(dbName).getDB();
        
        //testAllObjects(mDAO, structureIdx, 26);
        
        int parentStructure = mDAO.getExperiment().getStructure(structureIdx).getParentStructure();
        Position f = mDAO.getExperiment().getPosition(postition);
        StructureObject root = mDAO.getDao(f.getName()).getRoots().get(frame);
        StructureObject bact = root.getChildren(parentStructure).stream().filter(o->o.getTrackHead().getIdx()==mc).findAny().get().getChildren(structureIdx).get(b);
        
        //testSpineCreation(bact);
        //testContourCleaning(bact);
        //testAllSteps(bact);
        //testLocalization(bact, true);
        //testLocalization(bact, false);
        //testSkeleton(bact);
        StructureObject mut = bact.getParent().getChildren(2).get(m);
        testCoordCreation(bact, mut.getRegion().getCenter());
        //StructureObject root2 = mDAO.getDao(f.getName()).getRoots().get(4);
        //StructureObject bact2 = root2.getChildren(parentStructure).get(mc).getChildren(structureIdx).get(1);
        
        
        //testProjection(bact, bact2);
        
        /*Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(mDAO.getDao(f.getName()).getRoots(), parentStructure, false);
        allTracks.entrySet().stream().filter(e->e.getKey().getIdx()==mc).findAny().get().getValue().stream().filter(o->o.getFrame()>429).map(mic -> mic.getChildren(structureIdx).get(0)).forEach(bacteria-> {
            try {
                Set<Voxel> contour =cleanContour(bacteria.getRegion().getContour(), false);
                List<Voxel> skeleton = BacteriaSpineFactory.getSkeleton(BacteriaSpineFactory.getMaskFromContour(contour));
            } catch(Throwable t) {
                logger.error("error for spine: {}", bacteria);
                //BacteriaSpineFactory.verbose=true;
                //BacteriaSpineFactory.getSkeleton(BacteriaSpineFactory.getMaskFromContour(cleanContour(bacteria.getRegion().getContour(), true)));
            }
            
        });*/
    }
    public static void testAllObjects(MasterDAO mDAO, int structureIdx, int fromPosition) {
        int parentStructure = mDAO.getExperiment().getStructure(structureIdx).getParentStructure();
        for (int pos = fromPosition; pos<mDAO.getExperiment().getPositionCount(); ++pos) {
            logger.debug("testing postition: {}", pos);
            Stream<StructureObject> parentTrack = StructureObjectUtils.getAllChildrenAsStream(mDAO.getDao(mDAO.getExperiment().getPosition(pos).getName()).getRoots().stream(), parentStructure);
            //parentTrack = parentTrack.filter(p->p.getIdx()==1);
            StructureObjectUtils.getAllChildrenAsStream(parentTrack, structureIdx).parallel().forEach(bo -> testAllSteps(bo, true, true));
        }
    }
    public static void testContourCleaning(StructureObject b) {
        cleanContour(b.getRegion().getContour(), true);
    }
    public static void testSkeleton(StructureObject b) {
        Set<Voxel> contour =cleanContour(b.getRegion().getContour(), true);
        BacteriaSpineFactory.verbose = true;
        Image mask  = BacteriaSpineFactory.getMaskFromContour(contour).setName("mask after clean contour");
        
        List<Voxel> skeleton = BacteriaSpineFactory.getSkeleton(BacteriaSpineFactory.getMaskFromContour(contour));
        for (Voxel v : skeleton) mask.setPixelWithOffset(v.x, v.y, v.z, 0);
        ImageWindowManagerFactory.showImage(mask);
    }
    public static void testAllSteps(StructureObject b, boolean sk, boolean spine) {
        Set<Voxel> contour;
        try {
            logger.debug("clean contour for: {}", b);
            contour = cleanContour(b.getRegion().getContour());
        } catch (Exception e) {
            logger.debug("failed to clean contour: for bact: "+b, e);
            contour = cleanContour(b.getRegion().getContour(), true); // run in verbose mode
        }
        try {
            logger.debug("get circular contour for: {}", b);
            CircularContourFactory.getCircularContour(contour);
            //logger.debug("contour ok for : {}", b);
        } catch (Exception e) {
            logger.debug("failed to create circular contour: for bact: "+b, e);
            contour = cleanContour(b.getRegion().getContour(), true); // run in verbose mode
            BacteriaSpineFactory.verbose=true;
            CircularContourFactory.getCircularContour(contour); // will throw exception
        }
        if (sk) {
            try {
                logger.debug("get skeleton for: {}", b);
                BacteriaSpineFactory.getSkeleton(BacteriaSpineFactory.getMaskFromContour(contour));
            } catch (Exception e) {
                logger.debug("failed to create skeleton for bact: "+b, e);
                BacteriaSpineFactory.verbose = true;
                BacteriaSpineFactory.getSkeleton(BacteriaSpineFactory.getMaskFromContour(contour));
            }
        }
        if (spine) {
            try {
                logger.debug("get spine for: {}", b);
                //BacteriaSpineFactory.verbose=true;
                PointContainer2<Vector, Double>[] sp = BacteriaSpineFactory.createSpine(b.getRegion()).spine;
                logger.debug("spine for: {}", b);
                if (sp==null || sp.length==1) throw new RuntimeException("could not create spine");

            } catch (Exception e) {
                logger.debug("failed to create spine for bact: "+b, e);
                BacteriaSpineFactory.verbose=true;
                BacteriaSpineFactory.createSpine(b.getRegion());
                throw e;
            }
        }
    }
    public static void testCoordCreation(StructureObject b, Point p) {
        int zoomFactor = 7;
        BacteriaSpineLocalizer loc = new BacteriaSpineLocalizer(b.getRegion()).setTestMode(true);
        BacteriaSpineCoord coord = loc.getSpineCoord(p);
        logger.debug("Coord: {}", coord);
    }
    public static void testProjection(StructureObject bact1, StructureObject bact2) {
        int zoomFactor = 7;
        Map<StructureObject, BacteriaSpineLocalizer> locMap = HashMapGetCreate.getRedirectedMap((StructureObject b)->new BacteriaSpineLocalizer(b.getRegion()).setTestMode(true), Syncronization.NO_SYNC);
        Point center = locMap.get(bact1).getSpine()[1].duplicate().translate(new Vector(-2, 1.3f));
        if (!bact1.getRegion().contains(center.asVoxel())) throw new IllegalArgumentException("projected point outside bacteria");
        Point proj = BacteriaSpineLocalizer.project(center, bact1, bact2, NEAREST_POLE, locMap, true); 
        //Point proj = BacteriaSpineLocalizer.project(center, bact1, bact2, PROPORTIONAL, locMap);
        
        center.translateRev(bact1.getBounds());
        proj.translateRev(bact2.getBounds());
        Image im1 = locMap.get(bact1).draw(zoomFactor).setName("bact:"+bact1);
        im1.setPixel((int)(center.get(0)*zoomFactor+0.5)+1, (int)(center.get(1)*zoomFactor+0.5)+1, 0, 1000);
        Image im2 = locMap.get(bact2).draw(zoomFactor).setName("bact:"+bact2);
        
        im2.setPixel((int)(proj.get(0)*zoomFactor+0.5)+1, (int)(proj.get(1)*zoomFactor+0.5)+1, 0, 1000);
        ImageWindowManagerFactory.showImage(im1);
        ImageWindowManagerFactory.showImage(im2);
        /*Image im1 = bact1.getRawImage(bact1.getStructureIdx());
        im1.setPixelWithOffset(center.xMin(), center.yMin(),center.zMin(), 4000);
        Image im2 = bact2.getRawImage(bact2.getStructureIdx());
        im2.setPixelWithOffset(proj.xMin(), proj.yMin(),proj.zMin(), 4000);
        ImageWindowManagerFactory.showImage(im1);
        ImageWindowManagerFactory.showImage(im2);*/
    }
    
    public static void testLocalization(StructureObject bact, boolean skeleton) {
        long t0 = System.currentTimeMillis();
        SpineResult spine = BacteriaSpineFactory.createSpine(bact.getRegion());
        long t1 = System.currentTimeMillis();
        Image test = spine.drawSpine(13, true);
        // test localization
        
        Point p = spine.spine[1].duplicate().translate(new Vector(-3, 0.7f));
        Point pT = p.duplicate().translateRev(bact.getBounds());
        test.setPixel((int)(pT.get(0)*5+0.5)+1, (int)(pT.get(1)*5+0.5)+1, 0, 1000);
        BacteriaSpineLocalizer loc = new BacteriaSpineLocalizer(bact.getRegion());
        loc.testMode=true;
        BacteriaSpineCoord coord = loc.getSpineCoord(p);
        Point p2 = loc.project(coord, NEAREST_POLE);
        Point p2T = p2.duplicate().translateRev(bact.getBounds());
        test.setPixel((int)(p2T.get(0)*5+0.5)+1, (int)(p2T.get(1)*5+0.5)+1, 0, 1001);
        logger.debug("coords: {} (skeleton: {}), in {}ms", coord, skeleton, t1-t0);
        logger.debug("testDistance: {} (skeleton: {})", p2.dist(p), skeleton);
        ImageWindowManagerFactory.showImage(test);
    }
    public static void testSpineCreation(StructureObject bact) {
        ImageWindowManagerFactory.showImage(bact.getRegion().getMaskAsImageInteger().setName("original mask"));
        BacteriaSpineFactory.verbose=true;
        SpineResult spineSk = BacteriaSpineFactory.createSpine(bact.getRegion());
        //SpineResult spine = BacteriaSpineFactory.createSpine(bact.getRegion(), false);
        logger.debug("bounds: {}, spine sk: {}", bact.getBounds(), spineSk.spine[spineSk.spine.length-1].getContent2());
        //logger.debug("bounds: {}, spine: {}", bact.getBounds(), spine.spine[spine.spine.length-1].getContent2());
    }
}
