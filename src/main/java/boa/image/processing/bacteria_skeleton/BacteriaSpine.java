/*
 * Copyright (C) 2018 jollion
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
package boa.image.processing.bacteria_skeleton;

import boa.data_structure.Region;
import boa.data_structure.Voxel;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.ImageShort;
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;
import boa.utils.Pair;
import boa.utils.Utils;
import boa.utils.geom.Point;
import boa.utils.geom.PointContainer2;
import boa.utils.geom.Vector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jollion
 */
public class BacteriaSpine {
    public static final Logger logger = LoggerFactory.getLogger(BacteriaSpine.class);
    boolean verbose = true;
    Region bacteria;
    List<PointContainer2<Vector, Double>> spine;
    double spineLength = Double.NaN;

    public BacteriaSpine(Region bacteria) {
        this.bacteria=bacteria;
        if (!bacteria.is2D()) throw new IllegalArgumentException("Spine is only implemented in 2D");
        //neigh = !bacteria.is2D() ? new EllipsoidalNeighborhood(1.5, 1.5, true) : new EllipsoidalNeighborhood(1.5, true);
        //ImageWindowManagerFactory.showImage(drawContour());
        initSpine();
    }
    
    protected Image drawContour(CircularNode<Voxel> circularContour, int startLabel) {
        ImageFloat contourLabel = new ImageFloat("", bacteria.getImageProperties());
        contourLabel.setPixelWithOffset(circularContour.element.x, circularContour.element.y, circularContour.element.z, startLabel++);
        CircularNode<Voxel> current = circularContour.next();
        while(!current.equals(circularContour)) {
            contourLabel.setPixelWithOffset(current.element.x, current.element.y, current.element.z, startLabel++);
            current = current.next();
        }
        return contourLabel;
    }
    
    private void initSpine() {
        // 1) init  as an XY-oriented circular linked list
        EllipsoidalNeighborhood neigh = new EllipsoidalNeighborhood(1.5, true);
        double[] centerCoord = bacteria.getGeomCenter(false);
        Point center = new Point((float)centerCoord[0], (float)centerCoord[1]);
        Set<Voxel> contour = bacteria.getContour();
        CircularNode<Voxel> circularContourStart = new CircularNode(contour.stream().min((v1, v2)->Integer.compare(v1.x+v1.y, v2.x+v2.y)).get()); // circularContourStart with upper-leftmost voxel
        int count = 1;
        CircularNode<Voxel> current=null;
        Voxel next = new Voxel(0, 0, 0);
        Vector refV = new Vector(circularContourStart.element.x-center.get(0), circularContourStart.element.y-center.get(1));
        for (int i = 0; i<neigh.getSize(); ++i) {
            next.x = circularContourStart.element.x + neigh.dx[i];
            next.y = circularContourStart.element.y + neigh.dy[i];
            next.z = circularContourStart.element.z;
            if (contour.contains(next)) { // get the point with positive oriented angle relative to the center
                Vector v = new Vector(next.x-center.get(0), next.y-center.get(1));
                if (refV.angleXY(v)>0) {
                    current = circularContourStart.setNext(next);
                    ++count;
                    break;
                }
            }
        }
        if (current == null) throw new IllegalArgumentException("no first neighbor found");
        // loop
        while(!circularContourStart.element.equals(current.element) && count<=contour.size()) {
            Voxel n = new Voxel(0, 0, 0);
            for (int i = 0; i<neigh.getSize(); ++i) {
                n.x = current.element.x + neigh.dx[i];
                n.y = current.element.y + neigh.dy[i];
                n.z = current.element.z;
                if (contour.contains(n) && !n.equals(current.prev().element)) { // get the next point (different from previous -> each point of the circularContourStart should have exactly 2 neighbors)
                    current = current.setNext(n);
                    ++count;
                    break;
                }
            }
        }
        if (circularContourStart.element.equals(current.element)) {
            circularContourStart.setPrev(current.prev());
        } else throw new IllegalArgumentException("unable to close contour");
    
        // 2) get initial spList point: contour point closest to the center 
        Voxel startSpineVox1 = contour.stream().min((v1, v2)-> Double.compare(v1.getDistanceSquareXY(centerCoord[0], centerCoord[1]), v2.getDistanceSquareXY(centerCoord[0], centerCoord[1]))).get();
        CircularNode<Voxel> startSpine1 = circularContourStart.get(startSpineVox1);
        // 3) get opposed circularContourStart point: point of the contour in direction of the center, with local min distance to circularContourStart point
        Vector spDir = new Vector((float)(centerCoord[0]-startSpineVox1.x), (float)(centerCoord[1]-startSpineVox1.y)).normalize();
        Point curSP2 = center.duplicate().translate(spDir);
        while(bacteria.getMask().containsWithOffset(curSP2.xMin(), curSP2.yMin(), bacteria.getMask().zMin()) && bacteria.getMask().insideMaskWithOffset(curSP2.xMin(), curSP2.yMin(), bacteria.getMask().zMin())) curSP2.translate(spDir);
        Voxel startSpineVox2 = contour.stream().min((v1, v2)->Double.compare(v1.getDistanceSquareXY(curSP2.get(0), curSP2.get(1)), v2.getDistanceSquareXY(curSP2.get(0), curSP2.get(1)))).get();
        CircularNode<Voxel> startSpine2 = circularContourStart.get(startSpineVox2);
        double minDist = startSpine2.element.getDistanceSquareXY(startSpineVox1);
        CircularNode<Voxel> start2L = startSpine2; // search in one direction
        while(start2L.next.element.getDistanceSquareXY(startSpineVox1)<minDist) {
            start2L = start2L.next;
            minDist = start2L.element.getDistanceSquareXY(startSpineVox1);
        }
        CircularNode<Voxel> start2R = startSpine2; // search in the other direction
        while(start2R.prev.element.getDistanceSquareXY(startSpineVox1)<minDist) {
            start2R = start2R.prev;
            minDist = start2R.element.getDistanceSquareXY(startSpineVox1);
        }
        startSpine2 = start2R.equals(startSpine2) ? start2L : start2R; // if start2R was modified, a closer vox was found in this direction
        if (verbose) logger.debug("center: {}, startSpine1: {} startSpine2: {}", center, startSpineVox1, startSpineVox2);
        // 4) start getting the spList in one direction and the other
        List<PointContainer2<Vector, Double>> spList = getSpine(startSpine1, startSpine2, true);
        spList = Utils.reverseOrder(spList);
        spList.addAll(getSpine(startSpine1, startSpine2, false));
        if (verbose) logger.debug("spine: total points: {}", spList.size());
        // remove right angles 
        if (spList.size()>2) {
            Iterator<? extends Point> it = spList.iterator();
            Point p1 = it.next();
            Point p2 = it.next();
            while(it.hasNext()) {
                
            }
        }
        
        // make shure first point sis closer to start to the upper-leftmost
        if (circularContourStart.element.getDistanceSquareXY(spList.get(0).get(0), spList.get(0).get(1))>circularContourStart.element.getDistanceSquareXY(spList.get(spList.size()-1).get(0), spList.get(spList.size()-1).get(1))) {
            spList = Utils.reverseOrder(spList);
        }
        this.spine=spList;
        // compute distances from first poles
        for (int i = 1; i<spine.size(); ++i) spine.get(i).set2((spine.get(i-1).get2() + spine.get(i).dist(spine.get(i-1))));
        spineLength = spine.get(spine.size()-1).get2();
        if (verbose) {
            Image contourMap = this.drawContour(circularContourStart, (int)spineLength+1);
            for (PointContainer2<Vector, Double> p : spList) contourMap.setPixelWithOffset(p.xMin(), p.yMin(), 0, p.get2());
            ImageWindowManagerFactory.showImage(contourMap);
        }
    }
    
    private List<PointContainer2<Vector, Double>> getSpine(CircularNode<Voxel> s1, CircularNode<Voxel> s2, boolean firstUp) {
        List<PointContainer2<Vector, Double>> sp = new ArrayList<>();
        if (firstUp) sp.add(PointContainer2.fromPoint(Point.middle2D(s1.element, s2.element),  Vector.vector2D(s1.element, s2.element).normalize(), 0d)); // add this point only one time
        // loop until contour points reach each other
        // to take into acount angles consider 3 next: 1 next & 2, 2next & 1, 1 next & 2 next & get the minimal scenario
        Map<Double, Pair<CircularNode<Voxel>, CircularNode<Voxel>>> candidates = new HashMap<>(3);
        Vector lastDir = new Vector(0, 0);
        Function<Pair<CircularNode<Voxel>, CircularNode<Voxel>>, Double> score = p->Vector.vector2D(p.key.element, p.value.element).normalize().dotProduct(lastDir);
        
        while(!s1.equals(s2) && (firstUp ? !s1.prev().equals(s2):!s1.next().equals(s2))) {
            Pair<CircularNode<Voxel>, CircularNode<Voxel>> cand = new Pair(firstUp?s1.next():s1.prev(), s2);
            candidates.put(score.apply(cand), cand);
            cand = new Pair(s1, firstUp?s2.prev():s2.next());
            candidates.put(score.apply(cand), cand);
            cand = new Pair(firstUp?s1.next():s1.prev(), firstUp?s2.prev():s2.next()); // last -> prefered option if all are paralleles
            candidates.put(score.apply(cand), cand);
            Pair<CircularNode<Voxel>, CircularNode<Voxel>> p = candidates.entrySet().stream().max((e1, e2)->Double.compare(e1.getKey(), e2.getKey())).get().getValue(); // maximize scalar product -> most aligned with last
            s1 = p.key;
            s2 = p.value;
            Point newPoint =Point.middle2D(s1.element, s2.element);
            if (!sp.contains(newPoint) && (sp.isEmpty()|| sp.get(sp.size()-1).distSq(newPoint)>=1)) {
                Vector dir = Vector.vector2D(s1.element, s2.element).normalize();
                sp.add(PointContainer2.fromPoint(newPoint, dir, 0d));
                lastDir.setData(dir);
                if (verbose) logger.debug("contour: [{};{}] -> {}", s1.element, s2.element, sp.get(sp.size()-1));
            }
            candidates.clear();
        }
        return sp;
    }
    
}
