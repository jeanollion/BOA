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
import boa.image.BoundingBox;
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
    public static boolean verbose = true;

    public static Image drawSpine(BoundingBox bounds, PointContainer2<Vector, Double>[] spine, CircularNode<Voxel> circularContour) { // image size x3 for subpixel visualization
        int zoomFactor = 5;
        ImageFloat spineImage = new ImageFloat("", new SimpleImageProperties(new SimpleBoundingBox(0, bounds.sizeX()*zoomFactor-1, 0, bounds.sizeY()*zoomFactor-1, 0, 0), 1, 1));
        Offset off = bounds;
        Voxel vox = new Voxel(0, 0, 0);
        // draw contour of bacteria
        int startLabel = Math.max(spine[spine.length-1].getContent2().intValue(), spine.length) +10;
        CircularNode<Voxel> current = circularContour;
        EllipsoidalNeighborhood neigh = new EllipsoidalNeighborhood(zoomFactor/2d, false);
        boolean start  = true;
        while(start || !current.equals(circularContour)) {
            for (int i = 0; i<neigh.getSize(); ++i) {
                vox.x = (current.element.x-off.xMin())*zoomFactor+1+neigh.dx[i];
                vox.y = (current.element.y-off.yMin())*zoomFactor+1+neigh.dy[i];
                if (spineImage.contains(vox.x, vox.y, 0)) spineImage.setPixel(vox.x, vox.y, 0, startLabel);
            }
            current = current.next();
            start = false;
            startLabel++;
        }
        // draw spine direction
        int spineVectLabel = 1;
        for (PointContainer2<Vector, Double> p : spine) {
            int vectSize= (int) (p.getContent1().norm()/2.0+0.5);
            Vector dir = p.getContent1().duplicate().normalize().multiply(1d/zoomFactor);
            Point cur = p.duplicate().translate(new SimpleOffset(off).reverseOffset());
            for (int i = 0; i<vectSize*zoomFactor; ++i) {
                cur.translate(dir);
                vox.x = (int)(cur.get(0)*zoomFactor+1);
                vox.y = (int)(cur.get(1)*zoomFactor+1);
                if (spineImage.contains(vox.x, vox.y, 0)) spineImage.setPixel(vox.x, vox.y, 0, spineVectLabel);
            }
            spineVectLabel++;
        }
        // draw spine
        for (PointContainer2<Vector, Double> p : spine) {
            vox.x = (int)((p.get(0)-off.xMin())*zoomFactor+1);
            vox.y = (int)((p.get(1)-off.yMin())*zoomFactor+1);
            spineImage.setPixel(vox.x, vox.y, 0, p.getContent2()==0?Float.MIN_VALUE:p.getContent2());
        }
        return spineImage;
    }
    
    public static PointContainer2<Vector, Double>[] createSpine(Region bacteria) {
        return createSpine(bacteria, null);
    }
    public static PointContainer2<Vector, Double>[] createSpine(Region bacteria, CircularNode<Voxel>[] contourOutput) {
        // 1) init  as an XY-oriented circular linked list
        EllipsoidalNeighborhood neigh = new EllipsoidalNeighborhood(1.5, true);
        double[] centerCoord = bacteria.getGeomCenter(false);
        Point center = new Point((float)centerCoord[0], (float)centerCoord[1]);
        Set<Voxel> contour = bacteria.getContour();
        CircularNode<Voxel> circularContourStart = new CircularNode(contour.stream().min((v1, v2)->Integer.compare(v1.x+v1.y, v2.x+v2.y)).get()); // circularContourStart with upper-leftmost voxel
        if (contourOutput!=null && contourOutput.length==1) contourOutput[0] = circularContourStart;
        int count = 1;
        CircularNode<Voxel> current=null;
        Voxel next = new Voxel(0, 0, 0);
        Vector refV = new Vector(circularContourStart.element.x-center.get(0), circularContourStart.element.y-center.get(1));
        for (int i = 0; i<neigh.getSize(); ++i) {
            next.x = circularContourStart.element.x + neigh.dx[i];
            next.y = circularContourStart.element.y + neigh.dy[i];
            next.z = circularContourStart.element.z;
            if (contour.contains(next)) { // getFollowing the point with positive oriented angle relative to the center
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
                if (contour.contains(n) && !n.equals(current.prev().element)) { // getFollowing the next point (different from previous -> each point of the circularContourStart should have exactly 2 neighbors)
                    current = current.setNext(n);
                    ++count;
                    break;
                }
            }
        }
        if (circularContourStart.element.equals(current.element)) {
            circularContourStart.setPrev(current.prev());
        } else throw new IllegalArgumentException("unable to close contour");
    
        // 2) getFollowing initial spList point: contour point closest to the center 
        Voxel startSpineVox1 = contour.stream().min((v1, v2)-> Double.compare(v1.getDistanceSquareXY(centerCoord[0], centerCoord[1]), v2.getDistanceSquareXY(centerCoord[0], centerCoord[1]))).get();
        CircularNode<Voxel> startSpine1 = circularContourStart.getInNext(startSpineVox1);
        // 3) getFollowing opposed circularContourStart point: point of the contour in direction of the center, with local min distance to circularContourStart point
        Vector spDir = new Vector((float)(centerCoord[0]-startSpineVox1.x), (float)(centerCoord[1]-startSpineVox1.y)).normalize();
        Point curSP2 = center.duplicate().translate(spDir);
        while(bacteria.getMask().containsWithOffset(curSP2.xMin(), curSP2.yMin(), bacteria.getMask().zMin()) && bacteria.getMask().insideMaskWithOffset(curSP2.xMin(), curSP2.yMin(), bacteria.getMask().zMin())) curSP2.translate(spDir);
        Voxel startSpineVox2 = contour.stream().min((v1, v2)->Double.compare(v1.getDistanceSquareXY(curSP2.get(0), curSP2.get(1)), v2.getDistanceSquareXY(curSP2.get(0), curSP2.get(1)))).get();
        CircularNode<Voxel> startSpine2 = circularContourStart.getInNext(startSpineVox2);
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
        List<PointContainer2<Vector, Double>> spList = getSpine(bacteria, startSpine1, startSpine2, true, contour);
        spList = Utils.reverseOrder(spList);
        spList.addAll(getSpine(bacteria, startSpine1, startSpine2, false, contour));
        if (verbose) logger.debug("spine: total points: {}", spList.size());
        
        // make shure first point sis closer to start to the upper-leftmost
        if (circularContourStart.element.getDistanceSquareXY(spList.get(0).get(0), spList.get(0).get(1))>circularContourStart.element.getDistanceSquareXY(spList.get(spList.size()-1).get(0), spList.get(spList.size()-1).get(1))) {
            spList = Utils.reverseOrder(spList);
            for (PointContainer2<Vector, Double> p : spList) p.getContent1().reverseOffset();
        }
        
        PointContainer2<Vector, Double>[] spine = spList.toArray(new PointContainer2[spList.size()]);
        // compute distances from first poles
        for (int i = 1; i<spine.length; ++i) spine[i].setContent2((spine[i-1].getContent2() + spine[i].dist(spine[i-1])));
        if (verbose) ImageWindowManagerFactory.showImage(drawSpine(bacteria.getBounds(), spine, circularContourStart));
        return spine;
    }
    
    private static List<PointContainer2<Vector, Double>> getSpine(Region bacteria, CircularNode<Voxel> s1, CircularNode<Voxel> s2, boolean firstNext, Set<Voxel> contour) {
        List<PointContainer2<Vector, Double>> sp = new ArrayList<>();
        Point lastPoint = Point.middle2D(s1.element, s2.element);
        if (firstNext) sp.add(PointContainer2.fromPoint(lastPoint,  Vector.vector2D(s1.element, s2.element), 0d)); // add this point only one time
        SlidingVector lastDir  = new SlidingVector(5, Vector.vector2D(s1.element, s2.element));
        
        // loop until contour points reach each other
        // to take into acount deforamations of the bacteria (is contour longer on a side than on the other) consider 3 next: 1 next & 2, 2next & 1, 1 next & 2 next & getFollowing the minimal scenario
        List<CircularNode<Voxel>> bucketSecond=new ArrayList<>(4);
        List<CircularNode<Voxel>> bucketFirst=new ArrayList<>(4);
        while(continueLoop(s1, s2, firstNext)) {
            
            Pair<CircularNode<Voxel>, CircularNode<Voxel>> next = getNextUnAlignedCandidates(s1, s2, firstNext);
            if (next==null) return sp;
            Vector dir;
            Point newPoint;
            PointContainer2<Vector, Double> vertebra = getPointAndDirIfAligned(next.key, next.value);
            if (vertebra!=null) {
                dir = vertebra.getContent1();
                newPoint = vertebra;
                s1 = next.key;
                s2 = next.value;
            } else {
                Point other1  =getNearestPoint(next.key, next.value, firstNext, bucketSecond);
                dir = new Vector(other1.get(0)-next.key.element.x, other1.get(1)-next.key.element.y);
                newPoint = new Point((other1.get(0)+next.key.element.x)/2f, (other1.get(1)+next.key.element.y)/2f);

                Point other2  =getNearestPoint(next.value, next.key, !firstNext, bucketFirst);
                Vector dir2 = new Vector(next.value.element.x-other2.get(0), next.value.element.y-other2.get(1));
                Point newPoint2 = new Point((other2.get(0)+next.value.element.x)/2f, (other2.get(1)+next.value.element.y)/2f);
                boolean push1 = true;
                boolean push2 = true;
                if (newPoint!=newPoint2) { // keep point closest to both borders
                    double d1 = dir.norm();
                    double d2 = dir2.norm();
                    if (d1==d2) { // average with 
                        logger.debug("average point: {}+{} -> {}", newPoint, newPoint2, newPoint.duplicate().averageWith(newPoint2));
                        newPoint.averageWith(newPoint2);
                        logger.debug("average dir: {}+{} -> {}", dir, dir2, dir.duplicate().averageWith(dir2));
                        dir.averageWith(dir2);
                    } else if (d1<d2) push2 = false;
                    else push1=false;
                }
                if (push2 && !push1) {
                    newPoint = newPoint2;
                    dir=dir2;
                }
                // push to next points according to the adopted solution. 
                if (push2) {
                    bucketFirst.add(next.key);
                    Collections.sort(bucketFirst);
                    s1 = firstNext ? bucketFirst.get(bucketFirst.size()-1) : bucketFirst.get(0);
                } else s1 = next.key; // stabilizes
                if (push1) {
                    bucketSecond.add(next.value);
                    Collections.sort(bucketSecond);
                    s2 = firstNext ? bucketSecond.get(0) : bucketSecond.get(bucketSecond.size()-1);
                } else s2 = next.value; // stabilizes
            }
            
            lastDir.push(dir);
            sp.add(PointContainer2.fromPoint(newPoint, dir, 0d));
            // stop condition: reach contour
            if (contour.contains(newPoint.asVoxel())) return sp;
            // other stop condition: reach contour in direction of spine
            Vector spineDir = lastDir.get().duplicate().normalize().rotateXY90();
            if (!firstNext) spineDir.reverseOffset();
            Point nextPoint = newPoint.duplicate().translate(spineDir);
            Voxel nextPointV = nextPoint.asVoxel();
            if (contour.contains(nextPointV)) {
                sp.add(PointContainer2.fromPoint(nextPoint, dir.duplicate(), 0d));
                return sp;
            }
            else if (!bacteria.getVoxels().contains(nextPointV)) return sp;
            
            lastPoint = newPoint;
            if (verbose) logger.debug("contour: [{};{}] -> {}", s1.element, s2.element, sp.get(sp.size()-1));
            
        }
        if (sp.size()>1) sp.remove(sp.size()-1); // if stop with meet condition remove last point -> no direction
        return sp;
    }
    private static PointContainer2<Vector, Double> getPointAndDirIfAligned(CircularNode<Voxel> p1, CircularNode<Voxel> p2) {
        Vector ref = Vector.vector2D(p1.element, p2.element).normalize();
        boolean aligned1 = Vector.vector2D(p1.prev.element, p2.element).normalize()==ref || Vector.vector2D(p1.next.element, p2.element).normalize()==ref;
        boolean aligned2 = Vector.vector2D(p1.element, p2.prev.element).normalize()==ref || Vector.vector2D(p1.element, p2.next.element).normalize()==ref;
        if (!aligned1 && !aligned2) return null;
        Point mid1, mid2;
        if (aligned1) { // middle point
            CircularNode<Voxel> p1Prev = p1;
            while(Vector.vector2D(p1Prev.prev.element, p2.element).normalize()==ref) p1Prev=p1.prev;
            CircularNode<Voxel> p1Next = p1;
            while(Vector.vector2D(p1Next.next.element, p2.element).normalize()==ref) p1Next=p1.next;
            mid1 = Point.middle2D(p1Prev.element, p1Next.element);
        } else mid1  = new Point(p1.element.x, p1.element.y);
        if (aligned2) { // middle point
            CircularNode<Voxel> p2Prev = p2;
            while(Vector.vector2D(p1.element, p2Prev.prev.element).normalize()==ref) p2Prev=p2.prev;
            CircularNode<Voxel> p2Next = p2;
            while(Vector.vector2D(p1.element, p2Next.next.element).normalize()==ref) p2Next=p2.next;
            mid2 = Point.middle2D(p2Prev.element, p2Next.element);
        } else mid2  = new Point(p2.element.x, p2.element.y);
        return PointContainer2.fromPoint(mid1.duplicate().averageWith(mid2), Vector.vector(mid1, mid2), 0d);
    }
    private static Point getNearestPoint(CircularNode<Voxel> reference, CircularNode<Voxel> firstSearchPoint, boolean searchNext, List<CircularNode<Voxel>> bucket) {
        Function<CircularNode<Voxel>, Double> dist = other->reference.element.getDistanceSquareXY(other.element);
        bucket.clear();
        bucket.add(firstSearchPoint);
        bucket.add(firstSearchPoint.next);
        bucket.add(firstSearchPoint.prev);
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
    private static Pair<CircularNode<Voxel>, CircularNode<Voxel>> getNextUnAlignedCandidates(CircularNode<Voxel> s1, CircularNode<Voxel> s2, boolean firstNext) {
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
    private static CircularNode<Voxel> getNextUnAlignedCandidate(CircularNode<Voxel> reference, CircularNode<Voxel> toMove, boolean moveNext) {
        Vector ref = Vector.vector2D(reference.element, toMove.element).normalize();
        toMove =  moveNext?toMove.next():toMove.prev();
        if (!continueLoop(reference, toMove, moveNext)) return null;
        while( Vector.vector2D(reference.element, toMove.element).normalize().equals(ref) ) { // move second until unaligned voxel
            toMove = moveNext?toMove.next():toMove.prev();
            if (!continueLoop(reference, toMove, moveNext)) return null;
        }
        return toMove;
    }
    private static class SlidingVector  {
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
