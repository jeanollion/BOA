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
import boa.configuration.experiment.MicroscopyField;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.StructureObject;
import boa.data_structure.Voxel;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.image.Image;
import boa.image.SimpleOffset;
import boa.image.processing.bacteria_spine.BacteriaSpineFactory;
import boa.image.processing.bacteria_spine.BacteriaSpineCoord;
import boa.image.processing.bacteria_spine.BacteriaSpineLocalizer;
import static boa.image.processing.bacteria_spine.BacteriaSpineLocalizer.PROJECTION.NEAREST_POLE;
import static boa.image.processing.bacteria_spine.BacteriaSpineLocalizer.PROJECTION.PROPORTIONAL;
import boa.image.processing.bacteria_spine.CircularNode;
import boa.plugins.PluginFactory;
import static boa.test_utils.TestUtils.logger;
import boa.utils.HashMapGetCreateRedirected;
import boa.utils.geom.Point;
import boa.utils.geom.PointContainer2;
import boa.utils.geom.Vector;
import ij.ImageJ;
import java.util.Set;

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
        int fieldNumber= 0, timePoint=0, mc=0, b=0; // F=2 B=1
        MasterDAO mDAO = new Task(dbName).getDB();
        MicroscopyField f = mDAO.getExperiment().getPosition(fieldNumber);
        StructureObject root = mDAO.getDao(f.getName()).getRoots().get(timePoint);
        StructureObject bact = root.getChildren(0).get(mc).getChildren(1).get(b);
        StructureObject root2 = mDAO.getDao(f.getName()).getRoots().get(1);
        StructureObject bact2 = root2.getChildren(0).get(mc).getChildren(1).get(0);
        //testLocalization(bact);
        testProjection(bact, bact2);
    }
    public static void testProjection(StructureObject bact1, StructureObject bact2) {
        Point center = bact1.getRegion().getGeomCenter(false).translate(new Vector(-2, 40.7f));
        HashMapGetCreateRedirected<StructureObject, BacteriaSpineLocalizer> locMap = new HashMapGetCreateRedirected<>(b->new BacteriaSpineLocalizer(b.getRegion()).setTestMode(true), HashMapGetCreateRedirected.Syncronization.NO_SYNC);
        Point proj = BacteriaSpineLocalizer.project(center, bact1, bact2, PROPORTIONAL, locMap);
        
        
        
        center.translateRev(bact1.getBounds());
        proj.translateRev(bact2.getBounds());
        Image im1 = locMap.get(bact1).draw().setName("bact:"+bact1);
        im1.setPixel((int)(center.get(0)*5+0.5)+1, (int)(center.get(1)*5+0.5)+1, 0, 1000);
        Image im2 = locMap.get(bact2).draw().setName("bact:"+bact2);
        
        im2.setPixel((int)(proj.get(0)*5+0.5)+1, (int)(proj.get(1)*5+0.5)+1, 0, 1000);
        ImageWindowManagerFactory.showImage(im1);
        ImageWindowManagerFactory.showImage(im2);
        /*Image im1 = bact1.getRawImage(bact1.getStructureIdx());
        im1.setPixelWithOffset(center.xMin(), center.yMin(),center.zMin(), 4000);
        Image im2 = bact2.getRawImage(bact2.getStructureIdx());
        im2.setPixelWithOffset(proj.xMin(), proj.yMin(),proj.zMin(), 4000);
        ImageWindowManagerFactory.showImage(im1);
        ImageWindowManagerFactory.showImage(im2);*/
    }
    
    public static void testLocalization(StructureObject bact) {
        Point center = bact.getRegion().getGeomCenter(false);
        Set<Voxel> contour = bact.getRegion().getContour();
        CircularNode<Voxel> circContour = BacteriaSpineFactory.getCircularContour(contour, center);
        PointContainer2<Vector, Double>[] spine = BacteriaSpineFactory.createSpine(bact.getMask(), contour, circContour, center);
        logger.debug("bounds: {}, spine: {}", bact.getBounds(), spine[0]);
        Image test = BacteriaSpineFactory.drawSpine(bact.getBounds(), spine, circContour);
        // test localization
        
        Point p = center.duplicate().translate(new Vector(-3, 0.7f));
        Point pT = p.duplicate().translateRev(bact.getBounds());
        test.setPixel((int)(pT.get(0)*5+0.5)+1, (int)(pT.get(1)*5+0.5)+1, 0, 1000);
        BacteriaSpineLocalizer loc = new BacteriaSpineLocalizer(bact.getRegion());
        loc.testMode=true;
        BacteriaSpineCoord coord = loc.getCoord(p);
        Point p2 = loc.project(coord, NEAREST_POLE);
        Point p2T = p2.duplicate().translateRev(bact.getBounds());
        test.setPixel((int)(p2T.get(0)*5+0.5)+1, (int)(p2T.get(1)*5+0.5)+1, 0, 1001);
        logger.debug("coords: {}", coord);
        logger.debug("testDistance: {}", p2.dist(p));
        ImageWindowManagerFactory.showImage(test);
    }
}
