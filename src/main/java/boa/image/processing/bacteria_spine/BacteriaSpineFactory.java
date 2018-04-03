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
package boa.image.processing.bacteria_spine;

import boa.data_structure.Region;
import boa.data_structure.Voxel;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.image.BoundingBox;
import boa.image.IJImageWrapper;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.image.ImageProperties;
import boa.image.ImageShort;
import boa.image.Offset;
import boa.image.SimpleBoundingBox;
import boa.image.SimpleImageProperties;
import boa.image.SimpleOffset;
import boa.image.TypeConverter;
import boa.image.processing.FillHoles2D;
import static boa.image.processing.bacteria_spine.CleanContour.cleanContour;
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;
import boa.image.processing.neighborhood.Neighborhood;
import boa.utils.ArrayUtil;
import boa.utils.Pair;
import boa.utils.Utils;
import boa.utils.geom.Point;
import boa.utils.geom.PointContainer2;
import boa.utils.geom.Vector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_;
import sc.fiji.analyzeSkeleton.Graph;
import sc.fiji.analyzeSkeleton.SkeletonResult;
import sc.fiji.analyzeSkeleton.Vertex;

/**
 *
 * @author jollion
 */
public class BacteriaSpineFactory {
    public static final Logger logger = LoggerFactory.getLogger(BacteriaSpineFactory.class);
    public static boolean verbose = false;

    public static Image drawSpine(BoundingBox bounds, PointContainer2<Vector, Double>[] spine, CircularNode<Voxel> circularContour, int zoomFactor) { 
        if (zoomFactor%2==0) throw new IllegalArgumentException("Zoom Factory should be uneven");
        int add = zoomFactor > 1 ? 1 : 0;
        ImageFloat spineImage = new ImageFloat("", new SimpleImageProperties(new SimpleBoundingBox(0, bounds.sizeX()*zoomFactor-1, 0, bounds.sizeY()*zoomFactor-1, 0, 0), 1, 1));
        Offset off = bounds;
        Voxel vox = new Voxel(0, 0, 0);
        // draw contour of bacteria
        int startLabel = spine==null ? 1: Math.max(spine[spine.length-1].getContent2().intValue(), spine.length) +10;
        if (circularContour!=null) {
            CircularNode<Voxel> current = circularContour;
            EllipsoidalNeighborhood neigh = new EllipsoidalNeighborhood(zoomFactor/2d, false);
            boolean start  = true;
            while(current!=null && (start || !current.equals(circularContour))) {
                for (int i = 0; i<neigh.getSize(); ++i) {
                    vox.x = (current.element.x-off.xMin())*zoomFactor+add+neigh.dx[i];
                    vox.y = (current.element.y-off.yMin())*zoomFactor+add+neigh.dy[i];
                    if (spineImage.contains(vox.x, vox.y, 0)) spineImage.setPixel(vox.x, vox.y, 0, startLabel);
                }
                current = current.next();
                start = false;
                startLabel++;
            }
        }
        if (spine!=null) {
            int spineVectLabel = 1;
            for (PointContainer2<Vector, Double> p : spine) {
                double norm = p.getContent1().norm();
                int vectSize= (int) (norm/2.0+0.5);
                Vector dir = p.getContent1().duplicate().normalize();
                Point cur = p.duplicate().translateRev(off).translateRev(dir.duplicate().multiply(norm/4d));
                dir.multiply(1d/zoomFactor);
                for (int i = 0; i<vectSize*zoomFactor; ++i) {
                    cur.translate(dir);
                    vox.x = (int)(cur.get(0)*zoomFactor+add);
                    vox.y = (int)(cur.get(1)*zoomFactor+add);
                    if (spineImage.contains(vox.x, vox.y, 0)) spineImage.setPixel(vox.x, vox.y, 0, spineVectLabel);
                }
                spineVectLabel++;
            }
            // draw spine
            for (PointContainer2<Vector, Double> p : spine) {
                vox.x = (int)((p.get(0)-off.xMin())*zoomFactor+add);
                vox.y = (int)((p.get(1)-off.yMin())*zoomFactor+add);
                if (!spineImage.contains(vox.x, vox.y, 0)) {
                    logger.debug("out of bounds: {}, p: {}", vox, p);
                    continue;
                }
                spineImage.setPixel(vox.x, vox.y, 0, p.getContent2()==0?Float.MIN_VALUE:p.getContent2());
            }
        }
        return spineImage;
    }
    
    public static PointContainer2<Vector, Double>[] createSpine(Region bacteria, boolean tryToFillHolesIfNecessary) {
        if (!bacteria.is2D()) throw new IllegalArgumentException("Only works on 2D regions");
        Point center = bacteria.getGeomCenter(false);
        Set<Voxel> contour = bacteria.getContour();
        cleanContour(contour);
        CircularNode<Voxel> circContour;
        try {
            circContour = getCircularContour(contour, center);
        } catch (RuntimeException e) {
            logger.error("error creating spine: ", e);
            if (!tryToFillHolesIfNecessary) return null;
            
            // retry after filling holes
            ImageByte newMask = TypeConverter.toByteMask(bacteria.getMask(), null, 1);
            FillHoles2D.fillHoles(newMask, 2);
            bacteria = new Region(newMask, bacteria.getLabel(), bacteria.is2D());
            center = bacteria.getGeomCenter(false);
            contour = bacteria.getContour();
            try {
                circContour = getCircularContour(contour, center);
            } catch (RuntimeException e2) {
                return null;
            }
        }
        if (circContour!=null) return createSpine(bacteria.getMask(), contour, circContour, center);
        return null;
    }
    
    /**
     * Requires that each point of the contour has exactly 2 neighbours
     * @param contour
     * @param center
     * @return positively XY-oriented  contou
     */
    public static CircularNode<Voxel> getCircularContour(Set<Voxel> contour, Point center) {
        Set<Voxel> contourVisited = new HashSet<>(contour.size());
        EllipsoidalNeighborhood neigh = new EllipsoidalNeighborhood(1.5, true);
        CircularNode<Voxel> circContour = new CircularNode(contour.stream().min((v1, v2)->Integer.compare(v1.x+v1.y, v2.x+v2.y)).get()); // circContour with upper-leftmost voxel
        contourVisited.add(circContour.element);
        int count = 1;
        CircularNode<Voxel> current=null;
        Voxel next = new Voxel(0, 0, 0);
        Vector refV = new Vector(circContour.element.x-center.get(0), circContour.element.y-center.get(1));
        Vector otherV = new Vector(0, 0);
        // 1) get first neighbour with the positive orientation relative to the center
        Map<Voxel, Double> crossPMap = new HashMap<>(neigh.getSize()-2);
        for (int i = 0; i<neigh.getSize(); ++i) {
            next.x = circContour.element.x + neigh.dx[i];
            next.y = circContour.element.y + neigh.dy[i];
            next.z = circContour.element.z;
            if (contour.contains(next)) { 
                otherV.set(next.x-center.get(0), 0).set(next.y-center.get(1), 1);
                double crossP = Vector.crossProduct2D(refV, otherV);
                crossPMap.put(next.duplicate(), crossP);
            }
        }
        if (crossPMap.isEmpty()) throw new RuntimeException("circular contour: no first neighbor found");
        if (crossPMap.size()==1) throw new RuntimeException("circular contour: first point is end point");
        current = circContour.setNext(crossPMap.entrySet().stream().max((e1, e2)->Double.compare(e1.getValue(), e2.getValue())).get().getKey());
        circContour.setPrev(crossPMap.entrySet().stream().min((e1, e2)->Double.compare(e1.getValue(), e2.getValue())).get().getKey());
        count+=2; 
        contourVisited.add(current.element);
        contourVisited.add(circContour.prev.element);
        //logger.debug("count: {}, source: {}, next: {}, prev: {}", count, circContour.element, circContour.next.element, circContour.prev.element);
        // 2) loop and get other points in the same direction. This requieres that each point of the contour has exactly 2 neighbours
        Map<Voxel, Integer> alternativeNext = new HashMap<>(neigh.getSize()-2);
        CircularNode<Voxel> lastIntersection = null;
        Voxel currentNext = null;
        int contourSize = contour.size();
        while(count<contourSize) {
            //logger.debug("current: {}, prev: {}, prev.prev: {}, count: {}/{}", current.element, current.prev.element, current.prev.prev.element, count, contour.size());
            Voxel n = new Voxel(0, 0, circContour.element.z);
            for (int i = 0; i<neigh.getSize(); ++i) {
                n.x = current.element.x + neigh.dx[i];
                n.y = current.element.y + neigh.dy[i];
                if (contour.contains(n) && !contourVisited.contains(n)) { // getFollowing unvisited point 
                    if (currentNext==null) {
                        currentNext = n.duplicate();
                        ++count;
                    } else { // a non visited neighbor was already added . Rare event.  put in a list to compare all solutions
                        if (alternativeNext.isEmpty()) alternativeNext.put(currentNext, getUnvisitedNeighborCount(contour, contourVisited, neigh, currentNext, circContour.prev.element));
                        alternativeNext.put(n.duplicate(), getUnvisitedNeighborCount(contour, contourVisited, neigh, n, circContour.prev.element));
                    }
                }
            }
            if (!alternativeNext.isEmpty()) { // get non-deadend voxel with least unvisited neighbors
                if (verbose) logger.debug("get non-dead voxel among: {}", alternativeNext);
                Voxel ref = current.element;
                Entry<Voxel, Integer> entry = alternativeNext.entrySet().stream().filter(e->e.getValue()>0).min((e1, e2)->{
                    int c = Integer.compare(e1.getValue(), e2.getValue());
                    if (c==0) return Double.compare(e1.getKey().getDistanceSquareXY(ref), e2.getKey().getDistanceSquareXY(ref));
                    return c;
                }).orElse(null);
                if (entry == null) entry = alternativeNext.entrySet().stream().findFirst().get(); // only deadends, try one
                lastIntersection = current;
                alternativeNext.clear();
                currentNext = entry.getKey();
            } 
            if (currentNext!=null) {
                current = current.setNext(currentNext);
                contourVisited.add(currentNext);
                currentNext = null;
            } else if (count<contourSize && lastIntersection!=null) { // got stuck by weird contour structure -> go back to previous voxel with several solutions? 
                if (verbose) logger.debug("dead-end: {} go back to: {}", current.element, lastIntersection.element);
                current = lastIntersection;
                current.next=null;
            } else break;
        }
        /*if (count<contourSize) {
            if (verbose) ImageWindowManagerFactory.showImage(new Region(contour, 1, true, 1, 1).getMaskAsImageInteger());
            if (verbose) ImageWindowManagerFactory.showImage(drawSpine(new Region(contour, 1, true, 1, 1).getMask(), null, circContour, 1));
            throw new RuntimeException("unable to create circular contour");
        }*/
        // 3) close the contour
        Voxel n = new Voxel(0, 0, circContour.element.z);
        for (int i = 0; i<neigh.getSize(); ++i) {
            n.x = current.element.x + neigh.dx[i];
            n.y = current.element.y + neigh.dy[i];
            if (n.equals(circContour.prev.element)) { // get first point's previous
                current.setNext(circContour.prev);
                break;
            }
        }
        if (current.next == null) {
            if (verbose) ImageWindowManagerFactory.showImage(new Region(contour, 1, true, 1, 1).getMaskAsImageInteger());
            if (verbose) ImageWindowManagerFactory.showImage(drawSpine(new Region(contour, 1, true, 1, 1).getMask(), null, circContour, 1));
            logger.error("unable to close contour: {}/{}, first: {} first'sprev: {}, current: {}", count, contourSize, circContour.element, circContour.prev.element, current.element);
            throw new RuntimeException("unable to close circular contour: current size: "+count+", contour size: "+ contour.size());
        }
        return circContour;
    }
    private static int getUnvisitedNeighborCount(Set<Voxel> contour, Set<Voxel> contourVisited, EllipsoidalNeighborhood neigh, Voxel v, Voxel first) {
        int count = 0;
        Voxel n = new Voxel(0, 0, v.z);
        for (int i = 0; i<neigh.getSize(); ++i) {
            n.x = v.x + neigh.dx[i];
            n.y = v.y + neigh.dy[i];
            if (contour.contains(n) && (n.equals(first) || !contourVisited.contains(n))) ++count;
        }
        return count;
    }
    public static PointContainer2<Vector, Double>[] createSpine(ImageMask mask, Set<Voxel> contour, CircularNode<Voxel> circContour, Point center) {
        
        // 1) getFollowing initial spList point: contour point closest to the center 
        Voxel startSpineVox1 = contour.stream().min((v1, v2)-> Double.compare(v1.getDistanceSquareXY(center.get(0), center.get(1)), v2.getDistanceSquareXY(center.get(0), center.get(1)))).get();
        CircularNode<Voxel> startSpine1 = circContour.getInNext(startSpineVox1);
        // 2) getFollowing opposed circularContour point: point of the contour in direction of nearest point - center, with local min distance to circContour point
        Vector spDir = new Vector((float)(center.get(0)-startSpineVox1.x), (float)(center.get(1)-startSpineVox1.y)).normalize();
        Point curSP2 = center.duplicate().translate(spDir);
        while(mask.containsWithOffset(curSP2.xMin(), curSP2.yMin(), mask.zMin()) && mask.insideMaskWithOffset(curSP2.xMin(), curSP2.yMin(), mask.zMin())) curSP2.translate(spDir);
        Voxel startSpineVox2 = contour.stream().min((v1, v2)->Double.compare(v1.getDistanceSquareXY(curSP2.get(0), curSP2.get(1)), v2.getDistanceSquareXY(curSP2.get(0), curSP2.get(1)))).get();
        CircularNode<Voxel> startSpine2 = circContour.getInNext(startSpineVox2);
        if (startSpine2==null) {
            if (verbose) ImageWindowManagerFactory.showImage(TypeConverter.toCommonImageType(mask));
            if (verbose) ImageWindowManagerFactory.showImage(BacteriaSpineFactory.drawSpine(mask, null, circContour, 1));
            throw new RuntimeException("oposite voxel ("+startSpineVox2.duplicate().translate(new SimpleOffset(mask).reverseOffset())+") from center ("+center.duplicate().translate(new SimpleOffset(mask).reverseOffset())+") not found in circular contour (nearst voxel: "+startSpineVox1.duplicate().translate(new SimpleOffset(mask).reverseOffset())+")");
        }
        // get local minimum around startSpine2
        double minDist = startSpine2.element.getDistanceSquareXY(startSpineVox1);
        CircularNode<Voxel> start2L = startSpine2; // search in next direction
        while(start2L.next.element.getDistanceSquareXY(startSpineVox1)<minDist) {
            start2L = start2L.next;
            minDist = start2L.element.getDistanceSquareXY(startSpineVox1);
        }
        CircularNode<Voxel> start2R = startSpine2; // search in prev direction
        while(start2R.prev.element.getDistanceSquareXY(startSpineVox1)<minDist) {
            start2R = start2R.prev;
            minDist = start2R.element.getDistanceSquareXY(startSpineVox1);
        }
        startSpine2 = start2R.equals(startSpine2) ? start2L : start2R; // if start2R was modified, a closer vox was found in this direction than in the other one
        if (verbose) logger.debug("center: {}, startSpine1: {} startSpine2: {}", center, startSpineVox1, startSpineVox2);
        // 3) start getting the spList in one direction and the other
        List<PointContainer2<Vector, Double>> spList = getHalfSpine(mask, startSpine1, startSpine2, true, contour);
        spList = Utils.reverseOrder(spList);
        spList.addAll(getHalfSpine(mask, startSpine1, startSpine2, false, contour));
        if (verbose) logger.debug("spine: total points: {}", spList.size());
        
        // make shure first point is closer to the upper-leftmost point
        if (circContour.element.getDistanceSquareXY(spList.get(0).get(0), spList.get(0).get(1))>circContour.element.getDistanceSquareXY(spList.get(spList.size()-1).get(0), spList.get(spList.size()-1).get(1))) {
            spList = Utils.reverseOrder(spList);
            for (PointContainer2<Vector, Double> p : spList) p.getContent1().reverseOffset();
        }
        
        PointContainer2<Vector, Double>[] spine = spList.toArray(new PointContainer2[spList.size()]);
        // compute distances from first poles
        for (int i = 1; i<spine.length; ++i) spine[i].setContent2((spine[i-1].getContent2() + spine[i].dist(spine[i-1])));
        if (verbose) ImageWindowManagerFactory.showImage(drawSpine(mask, spine, circContour, 5));
        return spine;
    }
    
    private static List<PointContainer2<Vector, Double>> getHalfSpine(ImageMask mask, CircularNode<Voxel> s1, CircularNode<Voxel> s2, boolean firstNext, Set<Voxel> contour) {
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
                Point other1  =getNearestPoint(next.key, next.value, bucketSecond);
                dir = new Vector(other1.get(0)-next.key.element.x, other1.get(1)-next.key.element.y);
                newPoint = new Point((other1.get(0)+next.key.element.x)/2f, (other1.get(1)+next.key.element.y)/2f);

                Point other2  =getNearestPoint(next.value, next.key, bucketFirst);
                Vector dir2 = new Vector(next.value.element.x-other2.get(0), next.value.element.y-other2.get(1));
                Point newPoint2 = new Point((other2.get(0)+next.value.element.x)/2f, (other2.get(1)+next.value.element.y)/2f);
                boolean push1 = true;
                boolean push2 = true;
                if (newPoint!=newPoint2) { // keep point closest to both borders
                    double d1 = dir.norm();
                    double d2 = dir2.norm();
                    if (d1==d2) { // average 2 points & dir 
                        newPoint.averageWith(newPoint2);
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
            // STOP CONDITIONS
            Vector spineDir = lastDir.get().duplicate().normalize().rotateXY90();
            if (!firstNext) spineDir.reverseOffset();
            // stop condition: reach contour
            if (contour.contains(newPoint.asVoxel())) {
                adjustPointToContour(sp.get(sp.size()-1), spineDir, s1.getInFollowing(newPoint.asVoxel(), firstNext), bucketFirst);
                return sp;
            } else { // other stop condition: reach contour in direction of spine
                Point nextPoint = newPoint.duplicate().translate(spineDir);
                Voxel nextPointV = nextPoint.asVoxel();
                if (contour.contains(nextPointV)) {
                    sp.add(PointContainer2.fromPoint(nextPoint, dir.duplicate(), 0d));
                    adjustPointToContour(sp.get(sp.size()-1), spineDir, s1.getInFollowing(nextPointV, firstNext), bucketFirst);
                    return sp;
                }
                else if (!mask.containsWithOffset(nextPointV.x, nextPointV.y, 0)) return sp; // cannot be adjusted to contour -> not found in contour;
            }
            
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
    private static Point getNearestPoint(CircularNode<Voxel> reference, CircularNode<Voxel> firstSearchPoint, List<CircularNode<Voxel>> bucket) {
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
    /**
     * Sets the coordinates of {@param p} to the nearest point on the contour, in the direction of {@param dir}
     * @param p
     * @param dir
     * @param firstSearchPoint nearest or close to nearest contour point from {@param p} in the direction {@param dir}
     * @param bucket 
     */
    private static void adjustPointToContour(Point p, Vector dir, CircularNode<Voxel> firstSearchPoint, List<CircularNode<Voxel>> bucket) {
        addTwoLocalNearestPoints(p, firstSearchPoint, bucket);
        //logger.debug("adjust to contour: closest points: {}, dir: {}, start point: {}", Utils.toStringList(bucket, b->b.element.toString()), dir, p);
        if (bucket.size()==1) {
            p.setData(bucket.get(0).element.x, bucket.get(0).element.y);
        } else {
            Point inter = Point.intersect2D(p, p.duplicate().translate(dir), Point.asPoint2D(bucket.get(0).element), Point.asPoint2D(bucket.get(1).element));
            //logger.debug("adjust to contour: intersection: {}", inter);
            if (inter!=null) p.setData(inter);
        }
    }
    /**
     * Local min distance search from {@param ref} starting from {@param firstSearchPoint}
     * @param ref
     * @param firstSearchPoint 
     * @param bucket recieve 2 closest point or only one if the 2 other neighbors have same distance
     */
    private static void addTwoLocalNearestPoints(Point ref, CircularNode<Voxel> firstSearchPoint, List<CircularNode<Voxel>> bucket) {
        bucket.clear();
        Function<Voxel, Double> dist = v->v.getDistanceSquareXY(ref.get(0), ref.get(1));
        double dMin = dist.apply(firstSearchPoint.element);
        CircularNode<Voxel> p = firstSearchPoint.prev();
        CircularNode<Voxel> n = firstSearchPoint.next();
        double dMinP = dist.apply(p.element);
        double dMinN = dist.apply(n.element);
        // if both are inferior -> put both points. 
        if (dMinP<dMin && dMinN<dMin) {
            bucket.add(p);
            bucket.add(n);
        } else if (dMinP<dMin) { // search in prev direction
            while(dMinP<dMin) {
                dMin = dMinP;
                p = p.prev();
                dMinP = dist.apply(p.element);
            }
            p = p.next(); // local min
            bucket.add(p);
            dMinN = dist.apply(p.next().element);
            if (dMinN<dMinP) bucket.add(p.next());
            else if (dMinP<dMinN) bucket.add(p.prev());
        } else if (dMinN<dMin) { // search in next direction
            while(dMinN<dMin) {
                dMin = dMinN;
                n = n.next();
                dMinN = dist.apply(n.element);
            }
            n = n.prev(); // local min
            bucket.add(n);
            dMinP = dist.apply(n.prev().element);
            if (dMinP<dMinN) bucket.add(n.prev());
            else if (dMinN<dMinP) bucket.add(n.next());
        } else {
            bucket.add(firstSearchPoint);
            if (dMinP<dMinN) bucket.add(p);
            else if (dMinP>dMinN) bucket.add(n);
        }
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
