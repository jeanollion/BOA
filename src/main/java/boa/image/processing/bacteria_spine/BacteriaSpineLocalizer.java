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
package boa.image.processing.bacteria_spine;

import boa.data_structure.Region;
import boa.data_structure.StructureObject;
import boa.image.Image;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.imglib2.KDTree;
import net.imglib2.RealLocalizable;
import net.imglib2.neighborsearch.KNearestNeighborSearchOnKDTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jollion
 */
public class BacteriaSpineLocalizer {
    public static final Logger logger = LoggerFactory.getLogger(BacteriaSpineLocalizer.class);
    public boolean testMode;
    final Region bacteria;
    PointContainer2<Vector, Double>[] spine;
    KDTree<PointContainer2<Vector, Double>> spineKDTree;
    KNearestNeighborSearchOnKDTree<PointContainer2<Vector, Double>> search;
    double length;
    public BacteriaSpineLocalizer(Region bacteria) {
        this.bacteria=bacteria;
        spine = BacteriaSpineFactory.createSpine(bacteria);
        List<PointContainer2<Vector, Double>> spList = new ArrayList<>(Arrays.asList(spine)); // KD tree shuffles elements -> new list
        this.spineKDTree = new KDTree<>(spList, spList);
        search = new KNearestNeighborSearchOnKDTree(spineKDTree, 2);
        length = spine[spine.length-1].getContent2();
    }

    public BacteriaSpineLocalizer setTestMode(boolean testMode) {
        this.testMode = testMode;
        return this;
    }
    
    public PointContainer2<Vector, Double>[] getSpine() {
        return spine;
    }

    public double getLength() {
        return length;
    }
    public Image draw() {
        return BacteriaSpineFactory.drawSpine(bacteria.getBounds(), spine, BacteriaSpineFactory.getCircularContour(bacteria.getContour(), bacteria.getGeomCenter(false)));
    }

    /**
     * 
     * @param p xy point
     * @return {@link BacteriaSpineCoord} representation of {@param p} from {@param referencePole} or null if it could not be computed
     */
    public BacteriaSpineCoord getSpineCoord(RealLocalizable p) {
        Point source = Point.wrap(p);
        this.search.search(p);
        if (testMode)  logger.debug("get spine coord for: {}: nearst point: {} & {}", p, search.getSampler(0), search.getSampler(1));
        
        PointContainer2<Vector, Double> r0 = search.getSampler(0).get();
        PointContainer2<Vector, Double> r1 = search.getSampler(1).get();
        BacteriaSpineCoord res = new BacteriaSpineCoord().setSpineLength(length);
        // specific cases
        if (source.equals(r0)) { // source is r0
            res.setSpineCoord(r0.getContent2());
            res.setSpineRadius(r0.getContent1().norm());
            return res;
        }
        if (source.equals(r1)) { // source is r1
            res.setSpineCoord(r1.getContent2());
            res.setSpineRadius(r1.getContent1().norm());
            return res;
        }
        Vector v0 = r0.getContent1().duplicate().normalize();
        Vector v1 = r1.getContent1().duplicate().normalize();
        Vector A = Vector.vector(r0, source);
        Vector Aa = Vector.vector(r1, source);
        if (A.duplicate().normalize().equals(v0)) { // point is in direction of r0
            res.setSpineCoord(r0.getContent2());
            res.setSpineRadius(r0.getContent1().norm());
            res.setDistFromSpine(A.norm());
            return res;
        }
        if (Aa.duplicate().normalize().equals(v1)) { // point is in direction of r1
            res.setSpineCoord(r1.getContent2());
            res.setSpineRadius(r1.getContent1().norm());
            res.setDistFromSpine(Aa.norm());
            return res;
        }
        if (v0.equals(v1)) { // simple intersection of p & dir 
            if (testMode) logger.debug("vertabra with colinear direction case");
            Point inter = Point.intersect2D(source, source.duplicate().translateRev(v0), r0, r1);
            double alpha = Vector.vector(r0, inter).norm() / Vector.vector(r0, r1).norm();
            double w = 1-alpha;
            Vector spineDir = r0.getContent1().duplicate().weightedSum(r1.getContent1(), w, 1-w);
            res.setSpineRadius(spineDir.norm());
            Vector sourceDir = Vector.vector(inter, source);
            double sign = Math.signum(sourceDir.dotProduct(spineDir));
            res.setDistFromSpine(sourceDir.norm() * sign);
            res.setSpineCoord((1-w) * r0.getContent2() + w*r1.getContent2());
            return res;
        }
        if (testMode) logger.debug("general case");
        // need to solve alpha & d = distance from spine in the direction spineDir = weighted sum of v0 & v1
        A.reverseOffset(); //  not null  & not colinear to V0 
        Vector B = Vector.vector(r0, r1); 
        Vector C = v1;
        Vector D = v0.duplicate().weightedSum(v1, 1, -1); // not null
        // vector walk: source -> r0 -> inter -> source. "inter" begin intersection point of source point and r0r1. direction inter-source is weighted sum of direction ro & direction r1
        // equation to solve is A + alpha*B + d * C + alpha*d * D = 0 (1)
        // first stip eliminate the non linear term -> get a linear relation between 
        double a = Vector.crossProduct2D(D, A);
        double b = Vector.crossProduct2D(D, B);
        double c = Vector.crossProduct2D(C, D); // never null -> check in specific cases
        // a + alpha * b  = d * c (2)
        
        Vector AA = D.duplicate().multiply(b/c);
        Vector BB = B.duplicate().add(C, b/c).add(D, a/c);
        Vector CC = A.duplicate().add(C, a/c);
        // (1) & (2) -> system of quadratic equation (colinear -> one single equation, take the positive root in ]0:1[ )
        // alpha2 * AA + alpha  * BB + C = 0
        double alpha = solveQuadratic(AA, BB, CC);
        double w = 1-alpha;
        if (testMode) logger.debug("weight: {}, d: {}", 1-alpha, a/c + alpha * b/c);
        if (Double.isNaN(alpha)) return null;
        double d = a/c + alpha * b/c; // relative value -> can be negative
        
        Vector spineDir = r0.getContent1().duplicate().weightedSum(r1.getContent1(), a, 1-w);
        res.setSpineRadius(spineDir.norm());
        res.setDistFromSpine(d);
        res.setSpineCoord(w * r0.getContent2() + (1-w)*r1.getContent2());
        return res;
        
    }
    /**
     * 
     * @param coord coordinate to project on this spine
     * @param proj projection type
     * @return and xy coordinate of {@param coord} in the current spine
     */
    public Point project(BacteriaSpineCoord coord, PROJECTION proj) {
        Comparator<PointContainer2<Vector, Double>> comp = (p1, p2)->Double.compare(p1.getContent2(), p2.getContent2());
        Double spineCoord = coord.getProjectedSpineCoord(length, proj);
        if (spineCoord<=-1 || spineCoord>=length+1) return null; //border cases allow only 1 pixel outside
        PointContainer2<Vector, Double> searchKey = new PointContainer2<>(null, spineCoord);
        int idx = Arrays.binarySearch(spine, searchKey, comp);
        if (testMode) logger.debug("projecting : {}, spineCoord: {}, search idx: {} (ip: {})", coord, spineCoord, idx, (idx<0?-idx-1:idx));
        if (idx<0) {
            int ip = -idx-1;
            // border cases check if point is inside bacteria
            if (ip==spine.length) {
                Point p = projectFromVertebra(spine[spine.length-1],coord.distFromSpine(false));
                return p;
                //if (bacteria.contains(p.asVoxel())) return p;
                //else return null;
            } 
            if (ip == 0) {
                Point p = projectFromVertebra(spine[0],coord.distFromSpine(false));
                return p;
                //if (bacteria.contains(p.asVoxel())) return p;
                //else return null;
            }
            // project from 2 adjacent vertebras
            PointContainer2<Vector, Double> v1 = spine[ip-1];
            PointContainer2<Vector, Double> v2 = spine[ip];
            return projectFrom2Vertebra(v1, v2, spineCoord, coord.distFromSpine(false));
        } else return projectFromVertebra(spine[idx],coord.distFromSpine(false));
    }
    private Point projectFromVertebra(PointContainer2<Vector, Double> vertebra, double distanceFromSpine) {
        if (testMode) logger.debug("projecting from single vertebra: {}", vertebra);
        return vertebra.duplicate().translate(vertebra.getContent1().duplicate().multiply(distanceFromSpine));
    }
    private Point projectFrom2Vertebra(PointContainer2<Vector, Double> v1, PointContainer2<Vector, Double> v2, double spineCoord, double distanceFromSpine) {
        double interVertebraDist = v2.getContent2()-v1.getContent2();
        double w = 1-(spineCoord-v1.getContent2())/interVertebraDist;
        Point v1v2 = v1.duplicate().weightedSum(v2, w, 1-w);
        Vector dir = v1.getContent1().duplicate().weightedSum(v2.getContent1(), w, 1-w).normalize().multiply(distanceFromSpine);
        if (testMode) logger.debug("projecting from vertebra: {}(w:{}) & {}(w:{}), spine point: {} dir: {}", v1, w, v2, 1-w,  v1v2.duplicate(), dir);
        return v1v2.translate(dir);
    }
    private static double solveQuadratic(Vector AA, Vector BB, Vector CC) {
        double[] rootsX = BacteriaSpineLocalizer.solveQuadratic(AA.get(0), BB.get(0), CC.get(0));
        if (rootsX.length==0) return Double.NaN;
        if (rootsX[0]>0 && rootsX[0]<1) return rootsX[0];
        if (rootsX.length==1) return Double.NaN;
        if (rootsX[1]>=0 && rootsX[1]<=1) return rootsX[1];
        return Double.NaN;
    }
    private static double[] solveQuadratic(double a, double b, double c) {
        if (a == 0) return new double[]{-c/b};
        double d = b*b-4*a*c;
        if (d<0) return new double[0];
        if (d==0) return new double[]{-b/(2*a)};
        d = Math.sqrt(d);
        return new double[]{(-b+d)/(2*a), (-b-d)/(2*a)};
    }
    // HELPER METHODS
    public static double distance(Point sourcePoint, Point otherPoint, StructureObject source, StructureObject destination, PROJECTION projType, Map<StructureObject,BacteriaSpineLocalizer> localizerMap ) {
        Point proj = project(sourcePoint, source, destination, projType, localizerMap);
        if (proj==null) return Double.POSITIVE_INFINITY;
        return otherPoint.dist(proj) * source.getScaleXY();
    }
    public static Point project(Point sourcePoint, StructureObject source, StructureObject destination, PROJECTION proj, Map<StructureObject,BacteriaSpineLocalizer> localizerMap ) {
        boolean testMode = false;
        if (destination.getPrevious()==source && destination.getTrackHead()==source.getTrackHead()) return project(sourcePoint, localizerMap.get(source), localizerMap.get(destination), proj);
        List<StructureObject> successiveContainers = new ArrayList<>(destination.getFrame()-source.getFrame()+1);
        StructureObject cur = destination;
        while (cur!=source) {
            successiveContainers.add(cur);
            //logger.debug("successive containers: {}", successiveContainers);
            cur = cur.getPrevious();
            if (cur.getFrame()<source.getFrame()) return null;
        }
        successiveContainers = Utils.reverseOrder(successiveContainers);
        if (testMode) logger.debug("successive containers: {}", successiveContainers);
        Point curentProj = sourcePoint;
        for (StructureObject next : successiveContainers) {
            if (cur.getTrackHead()==next.getTrackHead()) {
                if (testMode) logger.debug("project: {} -> {}", cur, next);
                curentProj = project(curentProj, localizerMap.get(cur), localizerMap.get(next), proj);
            }
            else { // division -> get division proportion
                List<StructureObject> sib = next.getDivisionSiblings(false);
                double totalLength = 0;
                for (StructureObject n : sib) totalLength += localizerMap.get(n).getLength();
                double curLength = localizerMap.get(next).getLength();
                double prop = curLength/(totalLength+curLength);
                boolean upperCell = next.getIdx() < sib.stream().mapToInt(o->o.getIdx()).min().getAsInt();
                if (testMode) logger.debug("project div: {}({}) -> {}({}), div prop: {}, upper cell: {}", cur, next, prop, upperCell);
                curentProj = projectDiv(curentProj, localizerMap.get(cur), localizerMap.get(next), prop, upperCell, proj);
            }
            if (curentProj==null) return null;
            cur = next;
        }
        return curentProj;
    }
    public static Point project(Point sourcePoint, BacteriaSpineLocalizer source, BacteriaSpineLocalizer destination, PROJECTION proj) {
        BacteriaSpineCoord c = source.getSpineCoord(sourcePoint);
        Point res = destination.project(c, proj);
        //logger.debug("projecting: {} -> {}", sourcePoint, res);
        //logger.debug("proj: {} -> {}", c, res==null ? null : destination.getSpineCoord(res));
        return res;
    }
    public static Point projectDiv(Point origin, BacteriaSpineLocalizer source, BacteriaSpineLocalizer destination, double divProportion, boolean upperCell, PROJECTION proj) {
        BacteriaSpineCoord c = source.getSpineCoord(origin);
        c.setDivisionPoint(divProportion, upperCell);
        Point res = destination.project(c, proj);
        //logger.debug("proj div: {} -> {}", c, res==null ? null : destination.getSpineCoord(res));
        return res;
    }
    public static enum PROJECTION {PROPORTIONAL, NEAREST_POLE};
}
