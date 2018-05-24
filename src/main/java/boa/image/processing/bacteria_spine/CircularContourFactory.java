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
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;
import boa.utils.geom.GeomUtils;
import boa.utils.geom.Point;
import boa.utils.geom.PointSmoother;
import boa.utils.geom.Vector;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.imglib2.RealLocalizable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Jean Ollion
 */
public class CircularContourFactory {
    public static final Logger logger = LoggerFactory.getLogger(CircularContourFactory.class);
    public static <T extends RealLocalizable> CircularNode<T> getClosest(CircularNode<T> start, Point ref) {
        double min = ref.distSq(start.element);
        CircularNode<T> minN = start;
        CircularNode<T> prev = start.prev;
        while (ref.distSq(prev.element) < min) {
            min = ref.distSq(prev.element);
            minN = prev;
            prev = prev.prev;
        }
        CircularNode<T> next = start.next;
        while (ref.distSq(next.element) < min) {
            min = ref.distSq(next.element);
            minN = next;
            next = next.next;
        }
        return minN;
    }

    /**
     * Requires that each point of the contour has exactly 2 neighbours
     * @param contour
     * @return positively XY-oriented  contour
     */
    public static CircularNode<Voxel> getCircularContour(Set<Voxel> contour) {
        Set<Voxel> contourVisited = new HashSet<>(contour.size());
        EllipsoidalNeighborhood neigh = new EllipsoidalNeighborhood(1.5, true);
        CircularNode<Voxel> circContour = new CircularNode(contour.stream().min((Voxel v1, Voxel v2) -> Integer.compare(v1.x + v1.y, v2.x + v2.y)).get()); // circContour with upper-leftmost voxel
        contourVisited.add(circContour.element);
        int count = 1;
        CircularNode<Voxel> current = null;
        Voxel next = new Voxel(0, 0, circContour.element.z);
        // 1) get first neighbour with the positive orientation relative to the center
        Map<Voxel, Double> crossPMap = new HashMap<>( 2);
        for (int i = 0; i < neigh.getSize(); ++i) {
            next.x = circContour.element.x + neigh.dx[i];
            next.y = circContour.element.y + neigh.dy[i];
            if (contour.contains(next)) crossPMap.put(next.duplicate(), (double)(neigh.dx[i]-neigh.dy[i]));
        }
        if (crossPMap.isEmpty()) {
            throw new RuntimeException("circular contour: no first neighbor found");
        }
        if (crossPMap.size() == 1) {
            throw new RuntimeException("circular contour: first point is end point");
        }
        current = circContour.setNext(crossPMap.entrySet().stream().max((Map.Entry<Voxel, Double> e1, Map.Entry<Voxel, Double> e2) -> Double.compare(e1.getValue(), e2.getValue())).get().getKey());
        circContour.setPrev(crossPMap.entrySet().stream().min((Map.Entry<Voxel, Double> e1, Map.Entry<Voxel, Double> e2) -> Double.compare(e1.getValue(), e2.getValue())).get().getKey());
        count += 2;
        contourVisited.add(current.element);
        contourVisited.add(circContour.prev.element);
        //logger.debug("count: {}, source: {}, next: {}, prev: {}", count, circContour.element, circContour.next.element, circContour.prev.element);
        // 2) loop and get other points in the same direction. This requieres that each point of the contour has exactly 2 neighbours
        Map<Voxel, Integer> alternativeNext = new HashMap<>(neigh.getSize() - 2);
        CircularNode<Voxel> lastIntersection = null;
        Voxel currentNext = null;
        int contourSize = contour.size();
        while (count < contourSize) {
            //logger.debug("current: {}, prev: {}, prev.prev: {}, count: {}/{}", current.element, current.prev.element, current.prev.prev.element, count, contour.size());
            Voxel n = new Voxel(0, 0, circContour.element.z);
            for (int i = 0; i < neigh.getSize(); ++i) {
                n.x = current.element.x + neigh.dx[i];
                n.y = current.element.y + neigh.dy[i];
                if (contour.contains(n) && !contourVisited.contains(n)) {
                    // getFollowing unvisited point
                    if (currentNext == null) {
                        currentNext = n.duplicate();
                        ++count;
                    } else {
                        // a non visited neighbor was already added . Rare event because contour should have been cleaned before.  put in a list to compare all solutions
                        if (alternativeNext.isEmpty()) {
                            alternativeNext.put(currentNext, getUnvisitedNeighborCount(contour, contourVisited, neigh, currentNext, circContour.prev.element));
                        }
                        alternativeNext.put(n.duplicate(), getUnvisitedNeighborCount(contour, contourVisited, neigh, n, circContour.prev.element));
                    }
                }
            }
            if (!alternativeNext.isEmpty()) {
                // get non-deadend voxel with least unvisited neighbors
                if (BacteriaSpineFactory.verbose) {
                    BacteriaSpineFactory.logger.debug("get non-dead voxel among: {}", alternativeNext);
                }
                Voxel ref = current.element;
                Map.Entry<Voxel, Integer> entry = alternativeNext.entrySet().stream().filter((Map.Entry<Voxel, Integer> e) -> e.getValue() > 0).min((Map.Entry<Voxel, Integer> e1, Map.Entry<Voxel, Integer> e2) -> {
                    int c = Integer.compare(e1.getValue(), e2.getValue());
                    if (c == 0) {
                        return Double.compare(e1.getKey().getDistanceSquareXY(ref), e2.getKey().getDistanceSquareXY(ref));
                    }
                    return c;
                }).orElse(null);
                if (entry == null) {
                    entry = alternativeNext.entrySet().stream().findFirst().get(); // only deadends, try one
                }
                lastIntersection = current;
                alternativeNext.clear();
                currentNext = entry.getKey();
            }
            if (currentNext != null) {
                current = current.setNext(currentNext);
                contourVisited.add(currentNext);
                currentNext = null;
            } else if (count < contourSize && lastIntersection != null) {
                // got stuck by weird contour structure -> go back to previous voxel with several solutions?
                throw new RuntimeException("dead-end: unable to close contour");
            } else {
                break;
            }
        }
        /*if (count<contourSize) {
        if (verbose) ImageWindowManagerFactory.showImage(new Region(contour, 1, true, 1, 1).getMaskAsImageInteger());
        if (verbose) ImageWindowManagerFactory.showImage(drawSpine(new Region(contour, 1, true, 1, 1).getMask(), null, circContour, 1));
        throw new RuntimeException("unable to create circular contour");
        }*/
        // 3) close the contour
        Voxel n = new Voxel(0, 0, circContour.element.z);
        for (int i = 0; i < neigh.getSize(); ++i) {
            n.x = current.element.x + neigh.dx[i];
            n.y = current.element.y + neigh.dy[i];
            if (n.equals(circContour.prev.element)) {
                // get first point's previous
                current.setNext(circContour.prev);
                break;
            }
        }
        if (current.next == null) {
            if (BacteriaSpineFactory.verbose) {
                ImageWindowManagerFactory.showImage(new Region(contour, 1, true, 1, 1).getMaskAsImageInteger());
            }
            if (BacteriaSpineFactory.verbose) {
                ImageWindowManagerFactory.showImage(BacteriaSpineFactory.drawSpine(new Region(contour, 1, true, 1, 1).getMask(), null, circContour, 1));
            }
            BacteriaSpineFactory.logger.error("unable to close contour: {}/{}, first: {} first'sprev: {}, current: {}", count, contourSize, circContour.element, circContour.prev.element, current.element);
            throw new RuntimeException("unable to close circular contour");
        }
        return circContour;
    }

    private static int getUnvisitedNeighborCount(Set<Voxel> contour, Set<Voxel> contourVisited, EllipsoidalNeighborhood neigh, Voxel v, Voxel first) {
        int count = 0;
        Voxel n = new Voxel(0, 0, v.z);
        for (int i = 0; i < neigh.getSize(); ++i) {
            n.x = v.x + neigh.dx[i];
            n.y = v.y + neigh.dy[i];
            if (contour.contains(n) && (n.equals(first) || !contourVisited.contains(n))) {
                ++count;
            }
        }
        return count;
    }
    public static <T extends RealLocalizable> CircularNode<Point> smoothContour2D(CircularNode<T> circContour, double sigma) {
        PointSmoother smoother = new PointSmoother(sigma);
        CircularNode<Point> res = new CircularNode<>(smooth2D(circContour, smoother));
        CircularNode<Point> currentRes = res;
        CircularNode<T> current = circContour.next;
        while(current!=circContour) {
            currentRes = currentRes.setNext(smooth2D(current, smoother));
            current = current.next;
        }
        currentRes.setNext(res); // close loop
        return res;
    }
    private static <T extends RealLocalizable> Point smooth2D(CircularNode<T> point, PointSmoother smoother) {
        smoother.init(Point.asPoint2D(point.element), false);
        CircularNode<T> n = point.next;
        double cumDist = GeomUtils.distXY(point.element, n.element);
        while(n!=point && smoother.addRealLocalizable(n.element, cumDist)) {
            cumDist += GeomUtils.distXY(n.element, n.next.element);
            n = n.next;
        }
        CircularNode<T> p = point.prev;
        cumDist = GeomUtils.distXY(point.element, p.element);
        while(p!=point && smoother.addRealLocalizable(p.element, cumDist)) {
            cumDist += GeomUtils.distXY(p.element, p.prev.element);
            p = p.prev;
        }
        return smoother.getSmoothed();
    }
    public static <T> Set<T> getSet(CircularNode<T> circContour) {
        HashSet<T> res = new HashSet<>();
        CircularNode.apply(circContour, c->res.add(c.element), true);
        return res;
    }
    public static void resampleContour(CircularNode<Point> circContour, double d) {
        CircularNode<Point> current = circContour;
        while (current!=circContour.prev) current = moveNextPoint(current, d);
        // check last point
        double dN = circContour.element.distXY(circContour.prev.element);
        if (dN>d) { // simply add a point in between
            CircularNode<Point> p = circContour.prev;
            circContour.setPrev(Point.middle2D(circContour.element, p.element));
            circContour.prev.setPrev(p);
        }
    }
    private static CircularNode<Point> moveNextPoint(CircularNode<Point> circContour, double d) {
        double dN = circContour.element.distXY(circContour.next.element);
        if (dN>=2d) { // creates a point between the two
            CircularNode<Point> n = circContour.next;
            circContour.setNext(Point.middle2D(circContour.element, n.element));
            circContour.next.setNext(n);
            return circContour.next;
        }
        if (dN>d) { // next point moves closer
            circContour.next.element.translate(Vector.vector(circContour.next.element, circContour.element).normalize().multiply(dN-d));
            return circContour.next;
        }
        d-=dN;
        double dNN = circContour.next.element.dist(circContour.next.next.element);
        if (dNN<d) { // simply remove next element
            circContour.setNext(circContour.next.next);
            return circContour;
        }
        circContour.next.element.translate(Vector.vector(circContour.next.element, circContour.next.next.element).normalize().multiply(d));
        return circContour.next;
    }  
}
