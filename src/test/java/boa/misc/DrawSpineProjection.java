/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.misc;

import boa.core.Task;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.ObjectDAO;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.MutableBoundingBox;
import boa.image.Offset;
import boa.image.SimpleBoundingBox;
import boa.image.processing.bacteria_spine.BacteriaSpineCoord;
import boa.image.processing.bacteria_spine.BacteriaSpineFactory;
import boa.image.processing.bacteria_spine.BacteriaSpineFactory.SpineResult;
import static boa.image.processing.bacteria_spine.BacteriaSpineFactory.drawVector;
import boa.image.processing.bacteria_spine.BacteriaSpineLocalizer;
import boa.image.processing.bacteria_spine.CircularNode;
import boa.image.processing.bacteria_spine.SpineOverlayDrawer;
import static boa.image.processing.bacteria_spine.SpineOverlayDrawer.drawLine;
import static boa.image.processing.bacteria_spine.SpineOverlayDrawer.trimSpine;
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;
import static boa.test_utils.TestUtils.logger;
import boa.utils.geom.Point;
import boa.utils.geom.PointContainer2;
import boa.utils.geom.Vector;
import ij.ImageJ;
import ij.gui.Overlay;
import java.awt.Color;
import java.util.Collection;
import java.util.stream.IntStream;

/**
 *
 * @author Jean Ollion
 */
public class DrawSpineProjection {
    static final double WIDTH = 1;
    static final Color CONTOUR_COLOR = Color.YELLOW;
    static final Color SPINE_COLOR = Color.BLUE;
    static final Color SOURCE_COORD_COLOR = Color.RED;
    static final Color SOURCE_COLOR = Color.ORANGE;
    static final Color TARGET_COLOR = new Color(0, 150, 0);
    static final double TRIM_SPINE_FACTOR = 0.3333d;
    public static void main(String[] args) {
        new ImageJ();
        String dbName = "fluo160501_uncorr_TestParam";
        //int position= 0, mc=1, frame=86, b=1, m=1, frame2 = 89, b2= 1, m2 = 2, frame3 = 94, b31=2, b32=3, m3 = 3;
        int position= 0, mc=0, frame=383, b=1, m=2, frame2 = 386, b2= 1, m2 = 3, frame3 = 390, b31=2, b32=3, m3 = 2;
        MasterDAO mDAO = new Task(dbName).getDB();
        mDAO.setConfigurationReadOnly(true);
        ObjectDAO dao = MasterDAO.getDao(mDAO, position);
        StructureObject bact = dao.getRoots().get(frame).getChildren(0).stream().filter(o->o.getTrackHead().getIdx()==mc).findAny().get().getChildren(1).get(b);
        StructureObject bact2 = dao.getRoots().get(frame2).getChildren(0).stream().filter(o->o.getTrackHead().getIdx()==mc).findAny().get().getChildren(1).get(b2);
        StructureObject bact31 = dao.getRoots().get(frame3).getChildren(0).stream().filter(o->o.getTrackHead().getIdx()==mc).findAny().get().getChildren(1).get(b31);
        StructureObject bact32 = dao.getRoots().get(frame3).getChildren(0).stream().filter(o->o.getTrackHead().getIdx()==mc).findAny().get().getChildren(1).get(b32);
        
        StructureObject mic = bact.getParent();
        StructureObject mic2 = bact2.getParent();
        StructureObject mic3 = bact31.getParent();
        
        StructureObject mut = bact.getParent().getChildren(2).get(m);
        StructureObject mut2 = bact2.getParent().getChildren(2).get(m2);
        StructureObject mut3 = bact32.getParent().getChildren(2).get(m3);
        
        BacteriaSpineLocalizer loc = new BacteriaSpineLocalizer(bact.getRegion());
        BacteriaSpineCoord coord = loc.getSpineCoord(mut.getRegion().getCenter());
        Overlay spine = SpineOverlayDrawer.getSpineOverlay(trimSpine(loc.spine, TRIM_SPINE_FACTOR), mic.getBounds(), SPINE_COLOR, CONTOUR_COLOR, WIDTH);
        drawCoord(spine, mic.getBounds(), loc.spine, coord, mut.getRegion().getCenter());
        //SpineOverlayDrawer.drawPoint(spine, mic.getBounds(), mut.getRegion().getCenter(), SOURCE_COLOR, WIDTH/4);
        
        BacteriaSpineLocalizer loc2 = new BacteriaSpineLocalizer(bact2.getRegion());
        BacteriaSpineCoord coord2 = loc2.getSpineCoord(mut2.getRegion().getCenter());
        BacteriaSpineCoord coordProj2 = coord.duplicate().setCurvilinearCoord(coord.getProjectedCurvilinearCoord(loc2.getLength(), BacteriaSpineLocalizer.PROJECTION.PROPORTIONAL));
        Point mutProj2 = loc2.project(coord, BacteriaSpineLocalizer.PROJECTION.PROPORTIONAL);
        Overlay spine2 = SpineOverlayDrawer.getSpineOverlay(trimSpine(loc2.spine, TRIM_SPINE_FACTOR), mic2.getBounds(), SPINE_COLOR, CONTOUR_COLOR, WIDTH);
        drawCoord(spine2, mic2.getBounds(), loc2.spine, coordProj2, mutProj2);
        //SpineOverlayDrawer.drawPoint(spine2, mic2.getBounds(), mutProj2, SOURCE_COLOR, WIDTH/4);
        //SpineOverlayDrawer.drawPoint(spine2, mic2.getBounds(), mut2.getRegion().getCenter(), TARGET_COLOR, WIDTH/4);
        //drawArrow(spine2, mic2.getBounds(), mutProj2, mut2.getRegion().getCenter(), TARGET_COLOR);
        
        BacteriaSpineLocalizer loc31 = new BacteriaSpineLocalizer(bact31.getRegion());
        BacteriaSpineLocalizer loc32 = new BacteriaSpineLocalizer(bact32.getRegion());
        double divProp = loc32.getLength() / (loc31.getLength() + loc32.getLength());
        BacteriaSpineCoord coordDiv = coord2.duplicate().setDivisionPoint(divProp, false);
        BacteriaSpineCoord coordDivProj = coordDiv.duplicate().setCurvilinearCoord(coordDiv.getProjectedCurvilinearCoord(loc32.getLength(), BacteriaSpineLocalizer.PROJECTION.PROPORTIONAL));
        Point mutProj3 = loc32.project(coordDiv, BacteriaSpineLocalizer.PROJECTION.PROPORTIONAL);
        logger.debug("coord: {} coord div: {}, div length: {}+{}", coord2, coordDiv, loc31.getLength(), loc32.getLength());
        logger.debug("target coord: {}", loc32.getSpineCoord(mut3.getRegion().getCenter()));
        logger.debug("target: {} projDiv: {}", mut3.getRegion().getCenter(), mutProj3);
        Overlay spine31 = SpineOverlayDrawer.getSpineOverlay(trimSpine(loc31.spine, TRIM_SPINE_FACTOR), mic3.getBounds(), SPINE_COLOR, CONTOUR_COLOR, WIDTH);
        drawCoord(spine31, mic3.getBounds(), loc31.spine, coord2.duplicate().setCurvilinearCoord(loc31.getLength()*2).setSpineLength(loc31.getLength()), null); // draw whole spine on bact 31
        Overlay spine32 = SpineOverlayDrawer.getSpineOverlay(trimSpine(loc32.spine, TRIM_SPINE_FACTOR), mic3.getBounds(), SPINE_COLOR, CONTOUR_COLOR, WIDTH);
        drawCoord(spine32, mic3.getBounds(), loc32.spine, coordDivProj, mutProj3); // normal draw on bact 32
        //SpineOverlayDrawer.drawPoint(spine32, mic3.getBounds(), mutProj3, SOURCE_COLOR, WIDTH/4);
        //SpineOverlayDrawer.drawPoint(spine32, mic3.getBounds(), mut3.getRegion().getCenter(), TARGET_COLOR, WIDTH/4);
        //drawArrow(spine32, mic3.getBounds(), mutProj3, mut3.getRegion().getCenter(), TARGET_COLOR);
        IntStream.range(0, spine31.size()).forEach(i->spine32.add(spine31.get(i)));
        
        SpineOverlayDrawer.display("bact 0", mic.getRawImage(2), spine);
        SpineOverlayDrawer.display("bact 1", mic2.getRawImage(2), spine2);
        SpineOverlayDrawer.display("bact 2", mic3.getRawImage(2), spine32);
        
        // also display all images with only bacteria conours
        
        drawChildrenContours(mic, 1, 2);
        drawChildrenContours(mic2, 1, 2);
        drawChildrenContours(mic3, 1, 2);
    }
    
    private static void drawChildrenContours(StructureObject parent, int childSIdx, int displaySIdx) {
        Overlay overlay = new Overlay();
        // draw contour
        parent.getChildren(childSIdx).forEach(b -> {
            BacteriaSpineLocalizer s = new BacteriaSpineLocalizer(b.getRegion());
            CircularNode.apply(s.spine.circContour, n -> {
                Point cur = Point.asPoint2D(n.getElement()).translateRev(parent.getBounds());
                Vector dir = Vector.vector2D(n.getElement(), n.next().getElement());
                overlay.add(drawLine(cur, dir, CONTOUR_COLOR, WIDTH));
            }, true);
        });
        SpineOverlayDrawer.display("parent: "+parent.getFrame(), parent.getRawImage(displaySIdx).duplicate(), overlay);
    }
    public static void drawCoord(Overlay overlay, Offset offset, SpineResult spine, BacteriaSpineCoord coord, Point target) {
        Point spineIntersection = null;
        // draw central line
        for (int i = 1; i < spine.spine.length; ++i) {
            PointContainer2<Vector, Double> p = spine.spine[i - 1];
            PointContainer2<Vector, Double> p2 = spine.spine[i];
            Vector dir = Vector.vector2D(p, p2);
            boolean last = p2.getContent2() >= coord.curvilinearCoord(false);
            if (last) {
                // only draw until coord
                double newL = coord.curvilinearCoord(false) - p.getContent2();
                dir.normalize().multiply(newL);
                spineIntersection = p.duplicate().translate(dir);
            }
            overlay.add(drawLine(p.duplicate().translateRev(offset), dir, DrawSpineProjection.SOURCE_COORD_COLOR, WIDTH));
            if (last) {
                break;
            }
        }
        // draw radial coordinate
        if (spineIntersection != null && target != null) {
            overlay.add(drawLine(spineIntersection.duplicate().translateRev(offset), Vector.vector(spineIntersection, target), DrawSpineProjection.SOURCE_COORD_COLOR, DrawSpineProjection.WIDTH));
        }
    }
}
