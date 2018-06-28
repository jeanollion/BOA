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
import boa.data_structure.StructureObject;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.image.Image;
import static boa.image.processing.bacteria_spine.CleanVoxelLine.cleanContour;
import boa.utils.ArrayUtil;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import net.imglib2.KDTree;
import net.imglib2.RealLocalizable;
import net.imglib2.neighborsearch.KNearestNeighborSearchOnKDTree;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
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
    public final BacteriaSpineFactory.SpineResult spine;
    double length;
    public static double precision = 1E-4;
    public BacteriaSpineLocalizer(Region bacteria) {
        this.bacteria=bacteria;
        //long t0 = System.currentTimeMillis();
        spine = BacteriaSpineFactory.createSpine(bacteria);
        //long t1 = System.currentTimeMillis();
        if (spine==null || spine.spine == null || spine.spine.length==1) length = Double.NaN;
        else length = spine.spine[spine.spine.length-1].getContent2();
        //logger.debug("spine creation: {} ms", t1-t0);
    }

    public BacteriaSpineLocalizer setTestMode(boolean testMode) {
        this.testMode = testMode;
        return this;
    }
    
    public PointContainer2<Vector, Double>[] getSpine() {
        return spine.spine;
    }

    public double getLength() {
        return length;
    }
    public Image draw(int zoomFactor) {
        Image image =  BacteriaSpineFactory.drawSpine(bacteria.getBounds(), spine.spine, spine.circContour, zoomFactor, true);
        image.setCalibration(image.getScaleXY() * bacteria.getScaleXY(), bacteria.getScaleZ());
        return image;
    }
    public static void drawPoint(Point p, Image dest, int zoomFactor, float value) {
        dest.setPixel((int)((p.get(0)-dest.xMin())*zoomFactor+0.5)+1, (int)((p.get(1)-dest.yMin())*zoomFactor+0.5)+1, 0, value);
    }
    /**
     * 
     * @param p xy point
     * @return {@link BacteriaSpineCoord} representation of {@param p} from {@param referencePole} or null if it could not be computed
     */
    public BacteriaSpineCoord getSpineCoord(RealLocalizable p) {
        if (p==null) return null;
        //long t0 = System.currentTimeMillis();
        //search.search(p);
        //PointContainer2<Vector, Double> v = search.getSampler().get();
        //long t1 = System.currentTimeMillis();
        // get 2 adjacent other points
        //int idx = Arrays.binarySearch(spine, v, SPINE_COMPARATOR);
        //long t2 = System.currentTimeMillis();
        Point source = Point.wrap(p);
        int idx  = getNearestVertebra(source);
        PointContainer2<Vector, Double> v = spine.spine[idx];
        //long t3 = System.currentTimeMillis();
        //logger.debug("KDTree search: {}ms, idx search: {}ms, globalmin bin search: {}ms idx: {} idx2 {}, dist: {}, dist2: {}", t1-t0, t2-t1, t3-t2, idx, idx2, spine.spine[idx].distSq(source), spine.spine[idx2].distSq(source));
        if (testMode)  logger.info("get spine coord for: {}: nearst point: {}(d={}) prev: {}(d={}),  next: {}(d={})", p, spine.spine[idx], spine.spine[idx].distSq(source), idx>0?spine.spine[idx-1]:null, idx>0?spine.spine[idx-1].distSq(source):0, idx<spine.spine.length-1?spine.spine[idx+1]:null, idx<spine.spine.length-1?spine.spine[idx+1].distSq(source):0);
        
        PointContainer2<Vector, Double> vPrev = idx>0 ? spine.spine[idx-1] : null;
        PointContainer2<Vector, Double> vNext = idx<spine.spine.length-1 ? spine.spine[idx+1] : null;
        
       
        BacteriaSpineCoord c1 = vPrev==null? null:getSpineCoord(source, v, vPrev);
        BacteriaSpineCoord c2 = vNext==null? null:getSpineCoord(source, v, vNext);
        if (c1==null && c2==null) { // try with adjacent
            int idxPrev = idx-2;
            int idxNext = idx+2;
            while(c1==null && c2 == null && (idxPrev>=0 || idxNext<spine.spine.length)) {
                if (idxPrev>=0) c1 =  getSpineCoord(source, spine.spine[idxPrev+1],  spine.spine[idxPrev]);
                if (idxNext<spine.spine.length) c2 =  getSpineCoord(source, spine.spine[idxNext-1],  spine.spine[idxNext]);
                ++idxNext;
                --idxPrev;
            }
        }
        if (c1==null && c2!=null) return c2;
        if (c2==null && c1!=null) return c1;
        if (testMode && vPrev!=null && vNext!=null) logger.debug("compare two coords");
        if (c1==null && c2==null) return null; // unable to locate point -> concave contour ? 
        return Math.abs(c1.radialCoord(false))<Math.abs(c2.radialCoord(false)) ? c1 : c2; // conflict : return the closes to the spine
    }
    /**
     * 
     * @param source
     * @param r0 closest vertebra to {@param source}
     * @param r1
     * @return 
     */
    private BacteriaSpineCoord getSpineCoord(Point source, PointContainer2<Vector, Double> r0, PointContainer2<Vector, Double> r1) {
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
        Vector r0_src = Vector.vector(r0, source);
        Vector r1_src = Vector.vector(r1, source);
        if (r0_src.duplicate().normalize().equals(v0)) { // point is in direction of r0
            res.setSpineCoord(r0.getContent2());
            res.setSpineRadius(r0.getContent1().norm());
            res.setDistFromSpine(r0_src.norm());
            return res;
        }
        if (r1_src.duplicate().normalize().equals(v1)) { // point is in direction of r1
            res.setSpineCoord(r1.getContent2());
            res.setSpineRadius(r1.getContent1().norm());
            res.setDistFromSpine(r1_src.norm());
            return res;
        }
        Vector r0_r1 = Vector.vector(r0, r1); 
        // inside segment condition:
        if (Vector.crossProduct2D(r0_src, v0)*Vector.crossProduct2D(r1_src, v1)>0) { // out of bound case : point is outside segment: 
            double dotP = r0_src.dotProduct(r0_r1);
            //logger.debug("OUT OF BOUND CASE r0_src.r0_r1={}, r0 = {} r1 = {}", dotP, r0.equals(spine.spine[0])?"first":(r0.equals(spine.spine[spine.spine.length-1])?"last":"middle"), r1.equals(spine.spine[0])?"first":(r1.equals(spine.spine[spine.spine.length-1])?"last":"middle"));
            int dir = r0.equals(spine.spine[0]) ? -1 : ( r0.equals(spine.spine[spine.spine.length-1]) ? 1 : 0 );
            if ( dotP<0 && dir!=0) { //only allow if before rO & r0 = first, or after r0 & rO = last
                Point intersection = Point.intersect2D(source, source.duplicate().translateRev(v0), r0, r1);
                Vector r0_inter = Vector.vector(r0, intersection);
                double distFromR0 = r0_inter.norm();
                if (distFromR0>3) return null; // limit out-of-bound
                Vector spineDir = r0.getContent1();
                res.setSpineRadius(spineDir.norm());
                Vector sourceDir = Vector.vector(intersection, source);
                double sign = Math.signum(sourceDir.dotProduct(spineDir));
                res.setDistFromSpine(sourceDir.norm() * sign);
                res.setSpineCoord(r0.getContent2()+dir*distFromR0);
                return res;
            }
            return null;
        }
        
        if (v0.equals(v1, precision)) { // colinear vertebra: simple intersection of p & dir // IMPORTANT TO SET ACCURACY IF NOT MAY BE CONSIDERED AS GENERAL CASE AND MAY HAVE NO SOLUTION
            Point intersection = Point.intersect2D(source, source.duplicate().translateRev(v0), r0, r1);
            Vector sourceDir = Vector.vector(intersection, source);
            Vector r0_inter = Vector.vector(r0, intersection);
            if (r0_inter.dotProduct(Vector.vector(r1, intersection))>precision) { // case where intersection is not between 2 vertebra: only allow if there are no vertebra afterwards
                /*Image d = draw(7);
                drawPoint(source, d, 7, 1000);
                ImageWindowManagerFactory.showImage(d);
                throw new IllegalArgumentException("out-of-segment case should have been handled with cross-product tests! dp:"+r0_inter.dotProduct(Vector.vector(r1, intersection))+" point: "+source+" v1:"+r0+" v2:"+r1);
                */
                return null;
            } else {
                double alpha = r0_inter.norm() / Vector.vector(r0, r1).norm();
                double w = 1-alpha;
                Vector spineDir = r0.getContent1().duplicate().weightedSum(r1.getContent1(), w, 1-w);
                res.setSpineRadius(spineDir.norm());
                double sign = Math.signum(sourceDir.dotProduct(spineDir));
                res.setDistFromSpine(sourceDir.norm() * sign);
                res.setSpineCoord(w * r0.getContent2() + (1-w)*r1.getContent2());
                if (testMode) logger.debug("vertabra with colinear direction case weight: {}, intersection: {} res -> {}", w, intersection, res);
                return res;
            }
        }
        
        // need to solve alpha & d = distanceSq from spine in the direction spineDir = weighted sum of v0 & v1
        r0_src.reverseOffset(); //  not null  & not colinear to V0 
        
        Vector C = v0;
        Vector D = v1.duplicate().weightedSum(v0, 1, -1); // not null
        // vector walk: source -> r0 -> intersection -> source. "intersection" begin intersection point of source point and r0r1. direction inter-source is weighted sum of direction ro & direction r1
        // equation to solve is r0_src + alpha * r0_r1 + d * C + alpha*d * D = 0 (1)
        // first stip eliminate the non linear term -> get a linear relation between 
        double a = Vector.crossProduct2D(D, r0_src);
        double b = Vector.crossProduct2D(D, r0_r1);
        double c = Vector.crossProduct2D(C, D); // never null -> check in specific cases
        // a + alpha * b  = d * c (2)
        
        Vector AA = D.duplicate().multiply(b/c);
        Vector BB = r0_r1.duplicate().add(C, b/c).add(D, a/c);
        Vector CC = r0_src.duplicate().add(C, a/c);
        // (1) & (2) -> system of quadratic equation (colinear -> one single equation, take the positive root in ]0:1[ )
        // alpha2 * AA + alpha  * BB + C = 0
        double alpha = solveQuadratic(AA, BB, CC);
        
        if (Double.isNaN(alpha)) {
            if (testMode) logger.debug("general case: between: {} & {} no solution", r0, r1);
            return null;
        }
        double d = a/c + alpha * b/c; // relative value -> can be negative
        double w = 1-alpha;
        Vector spineDir = r0.getContent1().duplicate().weightedSum(r1.getContent1(), w, 1-w);
        res.setSpineRadius(spineDir.norm());
        res.setDistFromSpine(d);
        res.setSpineCoord(w * r0.getContent2() + (1-w)*r1.getContent2());
        if (testMode) logger.debug("general case. weight: {}, d: {}, coord: {}", w, d, res);
        return res;
        
    }
    public final static Comparator<PointContainer2<Vector, Double>> SPINE_COMPARATOR = (p1, p2)->Double.compare(p1.getContent2(), p2.getContent2());
    /**
     * 
     * @param coord coordinate to project on this spine
     * @param proj projection type
     * @return and xy coordinate of {@param coord} in the current spine
     */
    public Point project(BacteriaSpineCoord coord, PROJECTION proj) {
        if (coord==null) return null;
        Double spineCoord = coord.getProjectedSpineCoord(length, proj);
        if (spineCoord<=-1 || spineCoord>=length+1) return null; //border cases allow only 1 pixel outside
        PointContainer2<Vector, Double> searchKey = new PointContainer2<>(null, spineCoord);
        int idx = Arrays.binarySearch(spine.spine, searchKey, SPINE_COMPARATOR);
        if (testMode) logger.debug("projecting : {}, spineCoord: {}, search idx: {} (ip: {})", coord, spineCoord, idx, (idx<0?-idx-1:idx));
        if (idx<0) {
            int ip = -idx-1;
            // border cases check if point is inside bacteria
            if (ip==spine.spine.length) {
                Point p = projectFromVertebra(spine.spine[spine.spine.length-1],coord.radialCoord(false));
                return p;
                //if (bacteria.contains(p.asVoxel())) return p;
                //else return null;
            } 
            if (ip == 0) {
                Point p = projectFromVertebra(spine.spine[0],coord.radialCoord(false));
                return p;
                //if (bacteria.contains(p.asVoxel())) return p;
                //else return null;
            }
            // project from 2 adjacent vertebras
            PointContainer2<Vector, Double> v1 = spine.spine[ip-1];
            PointContainer2<Vector, Double> v2 = spine.spine[ip];
            return projectFrom2Vertebra(v1, v2, spineCoord, coord.radialCoord(false));
        } else return projectFromVertebra(spine.spine[idx],coord.radialCoord(false));
    }
    private Point projectFromVertebra(PointContainer2<Vector, Double> vertebra, double distanceFromSpine) {
        if (testMode) logger.debug("projecting from single vertebra: {}", vertebra);
        return vertebra.duplicate().translate(vertebra.getContent1().duplicate().multiply(distanceFromSpine));
    }
    private Point projectFrom2Vertebra(PointContainer2<Vector, Double> v0, PointContainer2<Vector, Double> v1, double spineCoord, double distanceFromSpine) {
        double interVertebraDist = v1.getContent2()-v0.getContent2();
        double w = 1-((spineCoord-v0.getContent2())/interVertebraDist);
        Point intersection = v0.duplicate().weightedSum(v1, w, 1-w);
        Vector dir = v0.getContent1().duplicate().weightedSum(v1.getContent1(), w, 1-w).normalize().multiply(distanceFromSpine);
        if (testMode) logger.debug("projecting from vertebra: {}(w:{}) & {}(w:{}), intervertebra dist: {}, spine point: {} dir: {}", v0, w, v1, 1-w,  interVertebraDist, intersection.duplicate(), dir);
        return intersection.translate(dir);
    }
    public int getNearestVertebra(Point p) {
        if (spine.spine.length==1) return 0;
        else if (spine.spine.length==2) return spine.spine[1].distSq(p)<spine.spine[0].distSq(p) ? 0 : 1;
        int low = 0;
        int lim = spine.spine.length-1;
        int high = lim;
        // we know there is only one global (regional) local minima. modifies binary search -> chooses the decreasing side
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (mid==0) return spine.spine[1].distSq(p)<spine.spine[0].distSq(p) ? 0 : 1;
            double dMid = spine.spine[mid].distSq(p);
            double dPrev = spine.spine[mid-1].distSq(p);
            //logger.debug("[{}-{}-{}] dPrev: [{}-{}-{}]", low, mid, high, dPrev, dMid, dNext);
            if (dPrev<=dMid)  {
                high = mid - 1;
            } else {
                if (mid==lim) return lim;
                double dNext = spine.spine[mid+1].distSq(p);
                if (dNext<dMid) low = mid + 1;
                else return mid;
            }
        }
        return low;
    
    }
    private static double solveQuadratic(Vector AA, Vector BB, Vector CC) {
        double[] rootsX = BacteriaSpineLocalizer.solveQuadratic(AA.get(0), BB.get(0), CC.get(0));
        //logger.debug("roots: {}, {}", rootsX, BacteriaSpineLocalizer.solveQuadratic(AA.get(1), BB.get(1), CC.get(1)));
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
    public static double distanceSq(Point sourcePoint, Point otherPoint, StructureObject source, StructureObject destination, PROJECTION projType, Map<StructureObject,BacteriaSpineLocalizer> localizerMap, boolean testMode ) {
        if (source==destination) return sourcePoint.dist(otherPoint)* source.getScaleXY();
        Point proj = project(sourcePoint, source, destination, projType, localizerMap, testMode);
        if (proj==null) return Double.POSITIVE_INFINITY;
        return otherPoint.distSq(proj) * Math.pow(source.getScaleXY(), 2); 
    }
    public static double distanceSq(BacteriaSpineCoord sourceCoord, Point otherPoint, StructureObject source, StructureObject destination, PROJECTION projType, Map<StructureObject,BacteriaSpineLocalizer> localizerMap, boolean testMode ) {
        if (source==destination) {
            Point sourcePoint = localizerMap.get(source).project(sourceCoord, projType);
            return sourcePoint.distSq(otherPoint) * Math.pow(source.getScaleXY(), 2); 
        }
        Point proj = project(sourceCoord, source, destination, projType, localizerMap, testMode);
        if (testMode) logger.debug("distance: coord: {}->proj: {}", sourceCoord, proj);
        if (proj==null) return Double.POSITIVE_INFINITY;
        return otherPoint.distSq(proj) * Math.pow(source.getScaleXY(), 2); 
    }
    public static Point project(Point sourcePoint, StructureObject source, StructureObject destination, PROJECTION proj, Map<StructureObject,BacteriaSpineLocalizer> localizerMap, boolean testMode ) {
        return project(localizerMap.get(source).getSpineCoord(sourcePoint), source, destination, proj, localizerMap, testMode);
    }
    public static Point project(BacteriaSpineCoord sourceCoord, StructureObject source, StructureObject destination, PROJECTION proj, Map<StructureObject,BacteriaSpineLocalizer> localizerMap, boolean testMode ) {
        if (sourceCoord==null) return null;
        if (source.getFrame()>=destination.getFrame()) throw new IllegalArgumentException("Source should be before destination");
        if (destination.getPrevious()==source && destination.getTrackHead()==source.getTrackHead()) return localizerMap.get(destination).project(sourceCoord, proj);
        List<StructureObject> successiveContainers = new ArrayList<>(destination.getFrame()-source.getFrame()+1);
        StructureObject cur = destination;
        while (cur!=source) {
            successiveContainers.add(cur);
            //logger.debug("successive containers: {}", successiveContainers);
            cur = cur.getPrevious();
            if (cur==null || cur.getFrame()<source.getFrame()) return null;
        }
        successiveContainers = Utils.reverseOrder(successiveContainers);
        if (testMode) logger.info("successive containers: {}", successiveContainers);
        Point curentProj=null;
        cur = source;
        for (StructureObject next : successiveContainers) {
            if (localizerMap.get(next)==null) throw new RuntimeException("bacteria: "+next+" not present in localizer map, to compute distance with : "+source);
            if (cur.getTrackHead()==next.getTrackHead()) {
                if (curentProj==null) curentProj = localizerMap.get(next).project(sourceCoord, proj); // first projection
                else curentProj = project(curentProj, localizerMap.get(cur), localizerMap.get(next), proj);
                if (testMode) logger.info("project: {} -> {}: {}", cur, next, curentProj);
            } else { // division -> get division proportion
                double divLength = getDivisionLength(next, localizerMap);
                if (Double.isNaN(divLength)) {
                    if (testMode) logger.debug("could not compute division length for: {}", next);
                    return null;
                }
                double prop = localizerMap.get(next).getLength()/divLength;
                List<StructureObject> sib = next.getDivisionSiblings(false);
                boolean nextIsUpperCell = sib.isEmpty() || next.getBounds().yMean() < sib.stream().mapToDouble(o->o.getBounds().yMean()).min().getAsDouble(); 
                if (testMode) logger.debug("project div: coord before proj {} spine: {} after set div point : {}", curentProj,  (source==cur?sourceCoord : localizerMap.get(cur).getSpineCoord(curentProj)), (source==cur?sourceCoord : localizerMap.get(cur).getSpineCoord(curentProj)).setDivisionPoint(prop, nextIsUpperCell)); //
                if (curentProj==null) curentProj = localizerMap.get(next).project(sourceCoord.duplicate().setDivisionPoint(prop, nextIsUpperCell), proj); // first projection
                else curentProj = projectDiv(curentProj, localizerMap.get(cur), localizerMap.get(next), prop, nextIsUpperCell, proj);
                if (testMode) logger.info("project div: {} -> {}, div prop: {}, upper cell: {} coord div: {} spine: {}", cur, next, prop, nextIsUpperCell, curentProj, localizerMap.get(next).getSpineCoord(curentProj));
            }
            if (curentProj==null) return null;
            cur = next;
        }
        return curentProj;
    }
    private static double getDivisionLength(StructureObject object, Map<StructureObject,BacteriaSpineLocalizer> localizerMap) {
        ToDoubleFunction<StructureObject> getLength = o -> localizerMap.get(o)==null ? Double.NaN : localizerMap.get(o).getLength();
        ToDoubleFunction<StructureObject> getDivLength = o -> {
            List<StructureObject> sib = o.getDivisionSiblings(true);
            if (sib.size()<=1) return Double.NaN;
            return sib.stream().mapToDouble(getLength).sum();
        };
        double divLength = getDivLength.applyAsDouble(object);
        if (!Double.isNaN(divLength)) return divLength;
        // missing siblings -> cannot compute divsion proportion -> try to estimate it
        // compute sizeRatio from previous objects
        List<Double> sizeRatios = new ArrayList<>();
        StructureObject current = object.getPrevious();
        while(current!=null && current.getPrevious()!=null && sizeRatios.size()<5) {
            StructureObject prev= current.getPrevious();
            if (prev.getTrackHead() == current.getTrackHead()) {
                double r=  getLength.applyAsDouble(current)/getLength.applyAsDouble(prev);
                if (Double.isNaN(r)) break;
                sizeRatios.add(r);
            }
            else {
                double nLength=getDivLength.applyAsDouble(current);
                if (nLength==0 || Double.isNaN(nLength)) break;
                double r = nLength/getLength.applyAsDouble(prev);
                if (Double.isNaN(r)) break;
                sizeRatios.add(r);
            }
            current = prev;
        }
        if (sizeRatios.isEmpty()) return Double.NaN;
        double sR = ArrayUtil.median(sizeRatios);
        return sR * getLength.applyAsDouble(object.getPrevious());
    }
    
    public static Point project(Point sourcePoint, BacteriaSpineLocalizer source, BacteriaSpineLocalizer destination, PROJECTION proj) {
        BacteriaSpineCoord c = source.getSpineCoord(sourcePoint);
        if (c==null) return null;
        Point res = destination.project(c, proj);
        //logger.debug("projecting: {} -> {}", sourcePoint, res);
        //logger.debug("proj: {} -> {}", c, res==null ? null : destination.getSpineCoord(res));
        return res;
    }
    public static Point projectDiv(Point origin, BacteriaSpineLocalizer source, BacteriaSpineLocalizer destination, double divProportion, boolean upperCell, PROJECTION proj) {
        BacteriaSpineCoord c = source.getSpineCoord(origin);
        if (c==null) return null;
        c.setDivisionPoint(divProportion, upperCell);
        Point res = destination.project(c, proj);
        //logger.debug("proj div: {} -> {}", c, res==null ? null : destination.getSpineCoord(res));
        return res;
    }
    public enum PROJECTION {PROPORTIONAL, NEAREST_POLE};
}
