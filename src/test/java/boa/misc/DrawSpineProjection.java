/*
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.misc;

import boa.core.Task;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.ObjectDAO;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.MutableBoundingBox;
import boa.image.SimpleBoundingBox;
import boa.image.processing.bacteria_spine.BacteriaSpineCoord;
import boa.image.processing.bacteria_spine.BacteriaSpineFactory;
import boa.image.processing.bacteria_spine.BacteriaSpineFactory.SpineResult;
import static boa.image.processing.bacteria_spine.BacteriaSpineFactory.drawVector;
import boa.image.processing.bacteria_spine.BacteriaSpineLocalizer;
import boa.utils.geom.Point;
import boa.utils.geom.PointContainer2;
import boa.utils.geom.Vector;
import ij.ImageJ;

/**
 *
 * @author Jean Ollion
 */
public class DrawSpineProjection {
    static final int zoomFactor = 13;
    public static void main(String[] args) {
        new ImageJ();
        String dbName = "fluo160501_uncorr_TestParam";
        int position= 0, mc=1, frame=86, b=1, m=1, frame2 = 89, b2= 1, m2 = 2, frame3 = 94, b31=2, b32=3, m3 = 2;
        
        MasterDAO mDAO = new Task(dbName).getDB();
        ObjectDAO dao = MasterDAO.getDao(mDAO, position);
        StructureObject bact = dao.getRoots().get(frame).getChildren(0).stream().filter(o->o.getTrackHead().getIdx()==mc).findAny().get().getChildren(1).get(b);
        StructureObject bact2 = dao.getRoots().get(frame2).getChildren(0).stream().filter(o->o.getTrackHead().getIdx()==mc).findAny().get().getChildren(1).get(b2);
        StructureObject bact31 = dao.getRoots().get(frame3).getChildren(0).stream().filter(o->o.getTrackHead().getIdx()==mc).findAny().get().getChildren(1).get(b31);
        StructureObject bact32 = dao.getRoots().get(frame3).getChildren(0).stream().filter(o->o.getTrackHead().getIdx()==mc).findAny().get().getChildren(1).get(b32);
        
        StructureObject mut = bact.getParent().getChildren(2).get(m);
        StructureObject mut2 = bact2.getParent().getChildren(2).get(m2);
        StructureObject mut3 = bact32.getParent().getChildren(2).get(m3);
        
        
        BoundingBox extentBds = new SimpleBoundingBox(-1, 1, -1, 1, 0, 0);
        
        BacteriaSpineLocalizer loc = new BacteriaSpineLocalizer(bact.getRegion());
        BacteriaSpineCoord coord = loc.getSpineCoord(mut.getRegion().getCenter());
        Image spineImage =  BacteriaSpineFactory.drawSpine(new MutableBoundingBox(bact.getBounds()).extend(extentBds), loc.spine.spine, loc.spine.circContour, zoomFactor, false);
        drawCoord(spineImage, loc.spine, coord, mut.getRegion().getCenter(), 4);
        drawPoint(spineImage, mut.getRegion().getCenter(), 0.5f, 4);
        
        BacteriaSpineLocalizer loc2 = new BacteriaSpineLocalizer(bact2.getRegion());
        BacteriaSpineCoord coordProj2 = coord.duplicate().setSpineCoord(coord.getProjectedSpineCoord(loc2.getLength(), BacteriaSpineLocalizer.PROJECTION.PROPORTIONAL));
        Point mutProj2 = loc2.project(coord, BacteriaSpineLocalizer.PROJECTION.PROPORTIONAL);
        Image spineImage2 =  BacteriaSpineFactory.drawSpine(new MutableBoundingBox(bact2.getBounds()).extend(extentBds), loc2.spine.spine, loc2.spine.circContour, zoomFactor, false);
        drawCoord(spineImage2, loc2.spine, coordProj2, mutProj2, 4);
        drawPoint(spineImage2, mutProj2, 0.5f, 4);
        drawPoint(spineImage2, mut2.getRegion().getCenter(), 0.5f, 5);
        
        BacteriaSpineLocalizer loc31 = new BacteriaSpineLocalizer(bact31.getRegion());
        BacteriaSpineLocalizer loc32 = new BacteriaSpineLocalizer(bact32.getRegion());
        // set division point
        Image spineImage31 =  BacteriaSpineFactory.drawSpine(new MutableBoundingBox(bact31.getBounds()).extend(extentBds), loc31.spine.spine, loc31.spine.circContour, zoomFactor, false);
        // draw whole spine on bact 31
        // normal draw on bact 32
        
        ImageWindowManagerFactory.showImage(spineImage.setName("bact 0"));
        ImageWindowManagerFactory.showImage(spineImage2.setName("bact 1"));
    }
    
    private static void drawCoord(Image output, SpineResult spine, BacteriaSpineCoord coord, Point target, int value) {
        // draw central line
        int add = zoomFactor > 1 ? 1 : 0;
        PointContainer2<Vector, Double> p0 = spine.spine[0];
        int xS = (int)((p0.get(0)-output.xMin())*zoomFactor+add);
        int yS = (int)((p0.get(1)-output.yMin())*zoomFactor+add);
        output.setPixel(xS, yS, 0,  value);
        Point spineIntersection=null;
        for (int i = 1; i<spine.spine.length; ++i) { 
            PointContainer2<Vector, Double> p = spine.spine[i-1];
            PointContainer2<Vector, Double> p2 = spine.spine[i];
            Vector dir = Vector.vector2D(p, p2);
            boolean last = p2.getContent2()>=coord.spineCoord(false);
            if (last) { // only draw until coord
                double newL = coord.spineCoord(false) - p.getContent2();
                dir.normalize().multiply(newL);
                spineIntersection = p.duplicate().translate(dir);
            }
            drawVector(output, p, dir, zoomFactor, value);
            if (last) break;
        }
        // draw radial coordinate
        if (spineIntersection!=null) {
            drawVector(output, spineIntersection, Vector.vector(spineIntersection, target), zoomFactor, value);
        }
        
    }
    public static void drawPoint(Image output, Point p, float size, int value) {
        drawVector(output, p.duplicate().translate(new Vector(-size, -size)), new Vector(2* size, 2 * size), zoomFactor, value);
        drawVector(output, p.duplicate().translate(new Vector(-size, size)), new Vector(2 * size, - 2 * size), zoomFactor, value);
    }
}
