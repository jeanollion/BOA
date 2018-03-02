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
import boa.image.Offset;
import boa.image.SimpleBoundingBox;
import boa.image.SimpleImageProperties;
import boa.image.SimpleOffset;
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;
import boa.utils.Pair;
import boa.utils.Utils;
import boa.utils.geom.Point;
import boa.utils.geom.PointContainer2;
import boa.utils.geom.Vector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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
        //ImageWindowManagerFactory.showImage(draw());
        initSpine();
    }
    
    protected Image draw(CircularNode<Voxel> circularContour) { // image size x3 for subpixel visualization
        int zoomFactor = 5;
        ImageFloat contourLabel = new ImageFloat("", new SimpleImageProperties(new SimpleBoundingBox(0, bacteria.getBounds().sizeX()*zoomFactor-1, 0, bacteria.getBounds().sizeY()*zoomFactor-1, 0, 0), 1, 1));
        Offset off = bacteria.getBounds();
        Voxel vox = new Voxel(0, 0, 0);
        // draw contour of bacteria
        int startLabel = Math.max((int)spineLength, spine.size()) +10;
        CircularNode<Voxel> current = circularContour;
        EllipsoidalNeighborhood neigh = new EllipsoidalNeighborhood(zoomFactor/2d, false);
        boolean start  = true;
        while(start || !current.equals(circularContour)) {
            for (int i = 0; i<neigh.getSize(); ++i) {
                vox.x = (current.element.x-off.xMin())*zoomFactor+1+neigh.dx[i];
                vox.y = (current.element.y-off.yMin())*zoomFactor+1+neigh.dy[i];
                if (contourLabel.contains(vox.x, vox.y, 0)) contourLabel.setPixel(vox.x, vox.y, 0, startLabel);
            }
            current = current.next();
            start = false;
            startLabel++;
        }
        // draw spine direction
        int vectSize = 4;
        int spineVectLabel = 1;
        for (PointContainer2<Vector, Double> p : spine) {
            Vector dir = p.get1().duplicate().normalize().multiply(1d/zoomFactor);
            Point cur = p.duplicate().translate(new SimpleOffset(off).reverseOffset());
            for (int i = 0; i<vectSize*zoomFactor; ++i) {
                cur.translate(dir);
                contourLabel.setPixel((int)(cur.get(0)*zoomFactor+1), (int)(cur.get(1)*zoomFactor+1), 0, spineVectLabel);
            }
            spineVectLabel++;
        }
        // draw spine
        for (PointContainer2<Vector, Double> p : spine) {
            vox.x = (int)((p.get(0)-off.xMin())*zoomFactor+1);
            vox.y = (int)((p.get(1)-off.yMin())*zoomFactor+1);
            contourLabel.setPixel(vox.x, vox.y, 0, p.get2()==0?Float.MIN_VALUE:p.get2());
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
        List<PointContainer2<Vector, Double>> spList = getSpine(startSpine1, startSpine2, true, contour);
        spList = Utils.reverseOrder(spList);
        spList.addAll(getSpine(startSpine1, startSpine2, false, contour));
        if (verbose) logger.debug("spine: total points: {}", spList.size());
        // remove right angles 
        /*if (spList.size()>2) {
            Iterator<? extends Point> it = spList.iterator();
            Point p1 = it.next();
            Point p2 = it.next();
            while(it.hasNext()) {
                
            }
        }*/
        
        // make shure first point sis closer to start to the upper-leftmost
        if (circularContourStart.element.getDistanceSquareXY(spList.get(0).get(0), spList.get(0).get(1))>circularContourStart.element.getDistanceSquareXY(spList.get(spList.size()-1).get(0), spList.get(spList.size()-1).get(1))) {
            spList = Utils.reverseOrder(spList);
            for (PointContainer2<Vector, Double> p : spList) p.get1().reverseOffset();
        }
        
        this.spine=spList;
        // compute distances from first poles
        for (int i = 1; i<spine.size(); ++i) spine.get(i).set2((spine.get(i-1).get2() + spine.get(i).dist(spine.get(i-1))));
        spineLength = spine.get(spine.size()-1).get2();
        if (verbose) ImageWindowManagerFactory.showImage(draw(circularContourStart));
        if (verbose) for (Point p : spList) logger.debug("{}",p);
    }
    
    private List<PointContainer2<Vector, Double>> getSpine(CircularNode<Voxel> s1, CircularNode<Voxel> s2, boolean firstNext, Set<Voxel> contour) {
        List<PointContainer2<Vector, Double>> sp = new ArrayList<>();
        Point lastPoint = Point.middle2D(s1.element, s2.element);
        if (firstNext) sp.add(PointContainer2.fromPoint(lastPoint,  Vector.vector2D(s1.element, s2.element), 0d)); // add this point only one time
        SlidingVector lastDir  = new SlidingVector(10, Vector.vector2D(s1.element, s2.element));
        
        // loop until contour points reach each other
        // to take into acount deforamations of the bacteria (is contour longer on a side than on the other) consider 3 next: 1 next & 2, 2next & 1, 1 next & 2 next & get the minimal scenario
        List<CircularNode<Voxel>> bucketSecond=new ArrayList<>(4);
        List<CircularNode<Voxel>> bucketFirst=new ArrayList<>(4);
        while(continueLoop(s1, s2, firstNext)) {
            
            Pair<CircularNode<Voxel>, CircularNode<Voxel>> next = getNextCandidates(s1, s2, firstNext);
            if (next==null) return sp;
            // fist vector : 
            Point other1  =getNearestPoint(next.key, next.value, firstNext, bucketSecond);
            Vector dir1 = new Vector(other1.get(0)-next.key.element.x, other1.get(1)-next.key.element.y);
            Point newPoint1 = new Point((other1.get(0)+next.key.element.x)/2f, (other1.get(1)+next.key.element.y)/2f);
            
            Point other2  =getNearestPoint(next.value, next.key, !firstNext, bucketFirst);
            Vector dir2 = new Vector(next.value.element.x-other2.get(0), next.value.element.y-other2.get(1));
            Point newPoint2 = new Point((other2.get(0)+next.value.element.x)/2f, (other2.get(1)+next.value.element.y)/2f);
            boolean push1 = true;
            boolean push2 = true;
            if (newPoint1!=newPoint2) { // keep only point with maximal dot product in spine direction
                Vector spineDir = lastDir.get().duplicate().rotateXY90();
                if (!firstNext) spineDir.reverseOffset();
                double dp1 = Vector.vector(lastPoint, newPoint1).normalize().dotProduct(spineDir);
                double dp2 = Vector.vector(lastPoint, newPoint2).normalize().dotProduct(spineDir);
                if (dp2>dp1) {
                    newPoint1 = newPoint2;
                    dir1=dir2;
                    push1 = false;
                } else push2 = false;
            }
            lastDir.push(dir1);
            sp.add(PointContainer2.fromPoint(newPoint1, dir1, 0d));
            if (contour.contains(newPoint1.asVoxel())) return sp;
            lastPoint = newPoint1;
            if (verbose) logger.debug("contour: [{};{}] -> {}", s1.element, s2.element, sp.get(sp.size()-1));
            
            // push to next points: 
            if (push2) {
                bucketFirst.add(next.key);
                Collections.sort(bucketFirst);
                s1 = firstNext ? bucketFirst.get(bucketFirst.size()-1) : bucketFirst.get(0);
            } else s1 = next.key;
            if (push1) {
                bucketSecond.add(next.value);
                Collections.sort(bucketSecond);
                s2 = firstNext ? bucketSecond.get(0) : bucketSecond.get(bucketSecond.size()-1);
            } else s2 = next.value;
        }
        if (sp.size()>1) sp.remove(sp.size()-1); // if stop with meet condition remove last point -> no direction
        return sp;
    }
    private static Point getNearestPoint(CircularNode<Voxel> reference, CircularNode<Voxel> firstSearchPoint, boolean searchNext, List<CircularNode<Voxel>> bucket) {
        Function<CircularNode<Voxel>, Double> dist = other->reference.element.getDistanceSquareXY(other.element);
        bucket.clear();
        bucket.add(firstSearchPoint);
        CircularNode<Voxel> next = getNextCandidate(reference, firstSearchPoint, searchNext);
        if (next!=null) {
            bucket.add(next);
            next = getNextCandidate(reference, next, searchNext);
            if (next!=null) bucket.add(next);
        }
        CircularNode<Voxel> prev = getNextCandidate(reference, firstSearchPoint, !searchNext); // if no search in prev -> direction are all parallele
        if (prev!=null) {
            bucket.add(prev);
            prev = getNextCandidate(reference, prev, !searchNext);
            if (prev!=null) bucket.add(prev);
        }
        bucket.sort((c1, c2)->Double.compare(dist.apply(c1), dist.apply(c2)));
        if (bucket.size()>=2) {
            while(bucket.size()>2) bucket.remove(bucket.size()-1);
            if (dist.apply(bucket.get(0))==dist.apply(bucket.get(1))) { // middle point
                return Point.middle2D(bucket.get(0).element, bucket.get(1).element);
            }
        } 
        while(bucket.size()>1) bucket.remove(bucket.size()-1);
        return new Point(bucket.get(0).element.x, bucket.get(0).element.y);        
    }
    
    private List<PointContainer2<Vector, Double>> getSpine1(CircularNode<Voxel> s1, CircularNode<Voxel> s2, boolean firstNext, Set<Voxel> contour) {
        List<PointContainer2<Vector, Double>> sp = new ArrayList<>();
        if (firstNext) sp.add(PointContainer2.fromPoint(Point.middle2D(s1.element, s2.element),  Vector.vector2D(s1.element, s2.element), 0d)); // add this point only one time
        SlidingVector lastDir  = new SlidingVector(5, Vector.vector2D(s1.element, s2.element));
        /*Function<Pair<CircularNode<Voxel>, CircularNode<Voxel>>, Double> distanceAndDirectionScore = p->{
            Vector cur = Vector.vector2D(p.key.element, p.value.element);
            Vector dirVector = lastDir.get().duplicate();
            double dirNorm = dirVector.norm();
            dirVector.multiply(1/dirNorm);
            dirNorm/=lastDir.queueCount(); // 
            cur.multiply(dirNorm/Math.pow(cur.norm(), 2));
            return -cur.dotProduct(lastDir.get().duplicate().normalize());
        };*/
        
        Function<Pair<CircularNode<Voxel>, CircularNode<Voxel>>, Double> distanceScore = p->p.key.element.getDistanceSquareXY(p.value.element);
        Function<Pair<CircularNode<Voxel>, CircularNode<Voxel>>, Double> directionScore = p->-Vector.vector2D(p.key.element, p.value.element).normalize().dotProduct(lastDir.get()); // -1 = aligned in same direction , 0 = perp, 1 = aligned & opposite direction
        
        Comparator<Pair<CircularNode<Voxel>, CircularNode<Voxel>>> compDist = (p1, p2) -> { // will be called with min
            int c = Double.compare(distanceScore.apply(p1), distanceScore.apply(p2)); // most aligned is first 
            if (c==0) c = Double.compare(directionScore.apply(p1), directionScore.apply(p2)); // shorter is first
            return c;
        };
        Comparator<Pair<CircularNode<Voxel>, CircularNode<Voxel>>> compDir = (p1, p2) -> { // will be called with min
            int c = Double.compare(directionScore.apply(p1), directionScore.apply(p2)); // shorter is first
            if (c==0) c = Double.compare(distanceScore.apply(p1), distanceScore.apply(p2)); // most aligned is first 
            return c;
        };
        // loop until contour points reach each other
        // to take into acount deforamations of the bacteria (is contour longer on a side than on the other) consider 3 next: 1 next & 2, 2next & 1, 1 next & 2 next & get the minimal scenario
        List<Pair<CircularNode<Voxel>, CircularNode<Voxel>>> candidates=new ArrayList<>(3);
        while(continueLoop(s1, s2, firstNext)) {
            Pair<CircularNode<Voxel>, CircularNode<Voxel>> cand = getNextCandidates(s1, s2, firstNext);
            if (cand!=null) {
                candidates.add(cand);
                candidates.add(new Pair(s1, cand.value));
                candidates.add(new Pair(cand.key, s2));
            } else return sp;
            
            Pair<CircularNode<Voxel>, CircularNode<Voxel>> pDir = candidates.stream().min(compDir).get();
            Pair<CircularNode<Voxel>, CircularNode<Voxel>> pDist = candidates.stream().min(compDist).get();
            
            Point newPoint =Point.middle2D(pDist.key.element, pDist.value.element);
            Vector dir = Vector.vector2D(pDist.key.element, pDist.value.element);
            s1 = pDir.key;
            s2 = pDir.value;
            /*if (!pDir.equals(pDist)) {
                // move on to maximal -> this is the determining step wheter to choose min or dist solution will change the fate of the spine
                if (pDist.key!=s1) s1 = pDist.key;
                //if (pDir.key!=s1) s1=pDir.key;
                if (pDist.value!=s2) s2 = pDist.value;
                //if (pDir.value!=s2) s2=pDir.value;
                // average newPoint & dir on the 2 solutions
                newPoint.averageWith(Point.middle2D(pDist.key.element, pDist.value.element));
                dir.averageWith(Vector.vector2D(pDist.key.element, pDist.value.element));
            } else {
                s1 = pDir.key;
                s2 = pDir.value;
            }*/
            
            lastDir.push(dir);
            sp.add(PointContainer2.fromPoint(newPoint, dir, 0d)); // mettre last dir au lieu de dir? 
            if (verbose) logger.debug("contour: [{};{}] -> {}", s1.element, s2.element, sp.get(sp.size()-1));
            if (contour.contains(newPoint.asVoxel())) return sp;
            // stop condition on the next voxel in the perpendicular direction
            /*Vector spineDir = lastDir.get().duplicate().rotateXY90().normalize();
            if (!firstNext) spineDir.reverseOffset();
            Point next = newPoint.duplicate().translate(spineDir);
            Voxel nextV = next.asVoxel();
            logger.debug("next: {}", next);
            if (!bacteria.getVoxels().contains(nextV) || !bacteria.getBounds().containsWithOffset(nextV.x, nextV.y, nextV.z)) return sp;
            */
            candidates.clear();
        }
        if (sp.size()>1) sp.remove(sp.size()-1); // if stop with meet condition remove last point -> no direction
        return sp;
    }
    private List<PointContainer2<Vector, Double>> getSpine2(CircularNode<Voxel> s1, CircularNode<Voxel> s2, boolean firstNext, Set<Voxel> contour) {
        List<PointContainer2<Vector, Double>> sp = new ArrayList<>();
        Point lastPoint = Point.middle2D(s1.element, s2.element);
        if (firstNext) sp.add(PointContainer2.fromPoint(lastPoint,  Vector.vector2D(s1.element, s2.element), 0d)); // add this point only one time
        SlidingVector lastDir  = new SlidingVector(1, Vector.vector2D(s1.element, s2.element));
        
        // loop until contour points reach each other
        // to take into acount deforamations of the bacteria (is contour longer on a side than on the other) consider 3 next: 1 next & 2, 2next & 1, 1 next & 2 next & get the minimal scenario
        List<Pair<CircularNode<Voxel>, CircularNode<Voxel>>> candidates=new ArrayList<>(3);
        while(continueLoop(s1, s2, firstNext)) {
            Vector spineDir = lastDir.get().duplicate().rotateXY90().normalize();
            if (!firstNext) spineDir.reverseOffset();
            Point nextPoint = lastPoint.translate(spineDir);
            //Function<Pair<CircularNode<Voxel>, CircularNode<Voxel>>, Double> score = p->Vector.crossProduct3D(new Vector( p.key.element.x-nextPoint.get(0), p.key.element.y-nextPoint.get(1)), new Vector( p.value.element.x-nextPoint.get(0), p.value.element.y-nextPoint.get(1))).norm(); // norm of the cross product is proportional to the area of the triangle formed by newPoint & 2 points of p
            Function<Pair<CircularNode<Voxel>, CircularNode<Voxel>>, Double> score = p->p.key.element.getDistanceSquareXY(nextPoint.get(0), nextPoint.get(1))+p.value.element.getDistanceSquareXY(nextPoint.get(0), nextPoint.get(1));
            Pair<CircularNode<Voxel>, CircularNode<Voxel>> cand = getNextCandidates(s1, s2, firstNext);
            if (cand!=null) {
                candidates.add(cand);
                candidates.add(new Pair(s1, cand.value));
                candidates.add(new Pair(cand.key, s2));
            } else return sp;
            Pair<CircularNode<Voxel>, CircularNode<Voxel>> p = candidates.stream().min((p1, p2)->Double.compare(score.apply(p1), score.apply(p2))).get();
            s1 = p.key;
            s2 = p.value;
            
            //Point newPoint = Point.middle2D(s1.element, s2.element); // ici : modifier newPoint ? 
            Point newPoint = nextPoint.duplicate();
            Vector dir = Vector.vector2D(s1.element, s2.element);
            
            lastDir.push(dir);
            sp.add(PointContainer2.fromPoint(newPoint, dir, 0d)); // mettre last dir au lieu de dir? 
            lastPoint = newPoint;
            if (verbose) logger.debug("contour: [{};{}] -> {}", s1.element, s2.element, sp.get(sp.size()-1));
            if (contour.contains(newPoint.asVoxel())) return sp;
            
            candidates.clear();
        }
        if (sp.size()>1) sp.remove(sp.size()-1); // if stop with meet condition remove last point -> no direction
        return sp;
    }
    /**
     * Stop condition when s1 and s2 meet or cross each other
     * @param s1
     * @param s2
     * @param firstNext
     * @return 
     */
    private static boolean continueLoop(CircularNode<Voxel> s1, CircularNode<Voxel> s2, boolean firstNext) {
        return !s1.equals(s2) && (firstNext ? !s1.prev().equals(s2):!s1.next().equals(s2));
    }
    /**
     * Return the next candidate on the contour
     * Ensures next candidate is not aligned with previous vector
     * @param s1
     * @param s2
     * @param firstNext
     * @param move1
     * @param move2
     * @return 
     */
    private static Pair<CircularNode<Voxel>, CircularNode<Voxel>> getNextCandidates(CircularNode<Voxel> s1, CircularNode<Voxel> s2, boolean firstNext) {
        Vector ref = Vector.vector2D(s1.element, s2.element).normalize();
        Pair<CircularNode<Voxel>, CircularNode<Voxel>> cand = new Pair( firstNext?s1.next():s1.prev(), firstNext?s2.prev():s2.next());
        if (!continueLoop(cand.key, cand.value, firstNext)) return null;
        while( Vector.vector2D(cand.key.element, s2.element).normalize().equals(ref) ) { //move first until unaligned voxel
            cand.key = firstNext?cand.key.next():cand.key.prev();
            if (!continueLoop(cand.key, cand.value, firstNext)) return null;
        }
        while( Vector.vector2D(s1.element, cand.value.element).normalize().equals(ref) ) { // move second until unaligned voxel
            cand.value = firstNext?cand.value.prev():cand.value.next();
            if (!continueLoop(cand.key, cand.value, firstNext)) return null;
        }
        return cand;
    }
    private static CircularNode<Voxel> getNextCandidate(CircularNode<Voxel> reference, CircularNode<Voxel> toMove, boolean moveNext) {
        Vector ref = Vector.vector2D(reference.element, toMove.element).normalize();
        toMove =  moveNext?toMove.next():toMove.prev();
        if (!continueLoop(reference, toMove, moveNext)) return null;
        while( Vector.vector2D(reference.element, toMove.element).normalize().equals(ref) ) { // move second until unaligned voxel
            toMove = moveNext?toMove.next():toMove.prev();
            if (!continueLoop(reference, toMove, moveNext)) return null;
        }
        return toMove;
    }
    private class SlidingVector  {
        final int n;
        Vector v;
        Queue<Vector> queue;
        public SlidingVector(int n, Vector initVector) {
            this.n = n;
            if (n>1) {
                queue =new LinkedList<>();
                queue.add(initVector);
                v = initVector.duplicate();
            } else v=initVector;
        }
        public Vector get() {
            return v;
        }
        public Vector push(Vector add) {
            if (queue!=null) {
                if (queue.size()==n) v.add(queue.poll(), -1);
                queue.add(add);
                v.add(add, 1);
            } else v = add;
            if (verbose) logger.debug("current dir: {}", v);
            return v;
        }
        public int queueCount() {
            if (queue==null) return 1;
            else return queue.size();
        }
    }
}
