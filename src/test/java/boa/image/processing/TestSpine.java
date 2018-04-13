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

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.core.Task;
import boa.configuration.experiment.Position;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.data_structure.Voxel;
import boa.data_structure.dao.ObjectDAO;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
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
        //String dbName = "AyaWT_mmglu";
        String dbName = "MutH_150324";
        int postition= 0, frame=1, mc=0, b=0;
        //String dbName = "MutH_140115";
        //int postition= 24, frame=310, mc=0, b=1; // F=2 B=1
        
        MasterDAO mDAO = new Task(dbName).getDB();
        Position f = mDAO.getExperiment().getPosition(postition);
        StructureObject root = mDAO.getDao(f.getName()).getRoots().get(frame);
        StructureObject bact = root.getChildren(0).get(mc).getChildren(1).get(b);
        StructureObject root2 = mDAO.getDao(f.getName()).getRoots().get(4);
        StructureObject bact2 = root2.getChildren(0).get(mc).getChildren(1).get(1);
        /*for (int pos = 0; pos<mDAO.getExperiment().getPositionCount(); ++pos) {
            logger.debug("testing postition: {}", pos);
            Stream<StructureObject> parentTrack = StructureObjectUtils.getAllChildrenAsStream(mDAO.getDao(mDAO.getExperiment().getPosition(pos).getName()).getRoots().stream(), 0);
            StructureObjectUtils.getAllChildrenAsStream(parentTrack, 1).forEach(bo -> testAllSteps(bo));
        }*/
        //testContourCleaning(bact);
        //testAllSteps(bact);
        //testLocalization(bact, true);
        //testLocalization(bact, false);
        //testProjection(bact, bact2);
        testSpineCreation(bact);
        //testSkeleton(bact);
    }
    public static void testContourCleaning(StructureObject b) {
        cleanContour(b.getRegion().getContour(), true);
    }
    public static void testSkeleton(StructureObject b) {
        Set<Voxel> contour =cleanContour(b.getRegion().getContour(), true);
        BacteriaSpineFactory.verbose = true;
        ImageWindowManagerFactory.showImage(BacteriaSpineFactory.getMaskFromContour(contour).setName("mask after clean contour"));
        List<Voxel> skeleton = BacteriaSpineFactory.getSkeleton(BacteriaSpineFactory.getMaskFromContour(contour));
    }
    public static void testAllSteps(StructureObject b) {
        Set<Voxel> contour;
        try {
            logger.debug("clean contour for: {}", b);
            contour = cleanContour(b.getRegion().getContour());
        } catch (Exception e) {
            logger.debug("failed to clean contour: for bact: "+b, e);
            contour = cleanContour(b.getRegion().getContour(), true); // run in verbose mode
        }
        List<Voxel> skeleton;
        try {
            logger.debug("get skeleton for: {}", b);
            
            skeleton = BacteriaSpineFactory.getSkeleton(BacteriaSpineFactory.getMaskFromContour(contour));
        } catch (Exception e) {
            logger.debug("failed to create skeleton for bact: "+b, e);
            BacteriaSpineFactory.verbose = true;
            skeleton = BacteriaSpineFactory.getSkeleton(BacteriaSpineFactory.getMaskFromContour(contour));
        }
        CircularNode<Voxel> circContour;
        Point center = Point.asPoint((Offset)skeleton.get(skeleton.size()/2));
        try {
             logger.debug("get circular contour for: {}", b);
            circContour = CircularContourFactory.getCircularContour(contour, center);
            //logger.debug("contour ok for : {}", b);
        } catch (Exception e) {
            logger.debug("failed to create circular contour: for bact: "+b, e);
            contour = cleanContour(b.getRegion().getContour(), true); // run in verbose mode
            BacteriaSpineFactory.verbose=true;
            circContour = CircularContourFactory.getCircularContour(contour, center); // will throw exception
        }

        try {
            logger.debug("get spine for: {}", b);
            PointContainer2<Vector, Double>[] spine = BacteriaSpineFactory.createSpineFromSkeleton(b.getMask(), skeleton, contour, circContour);
            if (spine==null || spine.length==1) throw new RuntimeException("could not create spine");
            
        } catch (Exception e) {
            logger.debug("failed to create spine for bact: "+b, e);
            BacteriaSpineFactory.verbose=true;
            PointContainer2<Vector, Double>[] spine = BacteriaSpineFactory.createSpineFromSkeleton(b.getMask(), skeleton, contour, circContour);
            throw e;
        }
    }
    public static void testProjection(StructureObject bact1, StructureObject bact2) {
        int zoomFactor = 7;
        Point center = bact1.getRegion().getGeomCenter(false).translate(new Vector(-2, 105.7f));
        if (!bact1.getRegion().contains(center.asVoxel())) throw new IllegalArgumentException("projected point outside bacteria");
        Map<StructureObject, BacteriaSpineLocalizer> locMap = HashMapGetCreate.getRedirectedMap((StructureObject b)->new BacteriaSpineLocalizer(b.getRegion()).setTestMode(true), Syncronization.NO_SYNC);
        Point proj = BacteriaSpineLocalizer.project(center, bact1, bact2, NEAREST_POLE, locMap); 
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
        SpineResult spine = BacteriaSpineFactory.createSpine(bact.getRegion(), skeleton);
        Image test = spine.drawSpine(7);
        // test localization
        
        Point p = spine.center.duplicate().translate(new Vector(-3, 0.7f));
        Point pT = p.duplicate().translateRev(bact.getBounds());
        test.setPixel((int)(pT.get(0)*5+0.5)+1, (int)(pT.get(1)*5+0.5)+1, 0, 1000);
        BacteriaSpineLocalizer loc = new BacteriaSpineLocalizer(bact.getRegion());
        loc.testMode=true;
        BacteriaSpineCoord coord = loc.getSpineCoord(p);
        Point p2 = loc.project(coord, NEAREST_POLE);
        Point p2T = p2.duplicate().translateRev(bact.getBounds());
        test.setPixel((int)(p2T.get(0)*5+0.5)+1, (int)(p2T.get(1)*5+0.5)+1, 0, 1001);
        logger.debug("coords: {} (skeleton: {})", coord, skeleton);
        logger.debug("testDistance: {} (skeleton: {})", p2.dist(p), skeleton);
        ImageWindowManagerFactory.showImage(test);
    }
    public static void testSpineCreation(StructureObject bact) {
        ImageWindowManagerFactory.showImage(bact.getRegion().getMaskAsImageInteger().setName("original mask"));
        BacteriaSpineFactory.verbose=true;
        SpineResult spineSk = BacteriaSpineFactory.createSpine(bact.getRegion(), true);
        //SpineResult spine = BacteriaSpineFactory.createSpine(bact.getRegion(), false);
        logger.debug("bounds: {}, spine sk: {}", bact.getBounds(), spineSk.spine[spineSk.spine.length-1].getContent2());
        //logger.debug("bounds: {}, spine: {}", bact.getBounds(), spine.spine[spine.spine.length-1].getContent2());
    }
}
