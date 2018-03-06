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
import boa.utils.Utils;
import boa.utils.geom.Point;
import boa.utils.geom.PointContainer2;
import boa.utils.geom.Vector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
        List<PointContainer2<Vector, Double>> spList = new ArrayList<>(Arrays.asList(spine)); // KD tree shuffles elements
        this.spineKDTree = new KDTree<>(spList, spList);
        search = new KNearestNeighborSearchOnKDTree(spineKDTree, 2);
        length = spine[spine.length-1].getContent2();
    }

    public PointContainer2<Vector, Double>[] getSpine() {
        return spine;
    }

    public double getLength() {
        return length;
    }
    
    /*public static enum ReferencePole {
        FirstPole(0, 1), LastPole(1, -1), DivisionPoleAsSecondPole(2, -1), DivisionPoleAsFirstPole(3, 1);
        public final int index, reverse;
        private ReferencePole(int index, int reverse) {
            this.index = index;
            this.reverse=reverse;
        }
    };
    public static enum Compartment {
        WholeCell(0), FirstDaughter(1), SecondDaughter(2);
        public final int index;
        private Compartment(int index) {
            this.index = index;
        }
    };
    protected double toPole(double spineDist, ReferencePole pole) {
        switch(pole) {
            case FirstPole:
            default:
                return spineDist;
            case LastPole:
                return length[0] - spineDist;
            case DivisionPoleAsSecondPole:
                return length[1] - spineDist;
            case DivisionPoleAsFirstPole:
                return spineDist - length[1];
        }
    }
    protected double fromPole(double normalizedSplineDist, ReferencePole pole, Compartment comp) {
        switch(pole) {
            case FirstPole:
                if (comp.equals(Compartment.SecondDaughter)) throw new IllegalArgumentException("compartiment is second daugter and pole is first pole");
            default:
                return normalizedSplineDist * length[comp.index];
            case LastPole:
                if (comp.equals(Compartment.FirstDaughter)) throw new IllegalArgumentException("compartiment is first daugter and pole is last pole");
                return length[comp.index] * (1- normalizedSplineDist);
            case DivisionPoleAsSecondPole:
                if (!comp.equals(Compartment.FirstDaughter)) throw new IllegalArgumentException("div as second pole and not first daughter");
                return length[comp.index] * (1-normalizedSplineDist);
            case DivisionPoleAsFirstPole:
                if (!comp.equals(Compartment.SecondDaughter)) throw new IllegalArgumentException("div as first pole and not second daughter");
                return normalizedSplineDist * length[comp.index] + length[1];
        }
    }*/
    /**
     * 
     * @param p
     * @return {@link BacteriaSpineCoord} representation of {@param p} from {@param referencePole}
     */
    public BacteriaSpineCoord getCoord(RealLocalizable p) {
        this.search.search(p);
        if (testMode)  logger.debug("get spine coord for: {}: nearst point: {} & {}", p, search.getSampler(0), search.getSampler(1));
        
        PointContainer2<Vector, Double> r1 = search.getSampler(0).get();
        double d1Sq = search.getSquareDistance(0);
        PointContainer2<Vector, Double> r2 = search.getSampler(1).get();
        double d2Sq = search.getSquareDistance(1);
        double LSq = r1.distSq(r2);
        double L = Math.sqrt(LSq);
        BacteriaSpineCoord res = new BacteriaSpineCoord();
        double deltaSpineDist = (d1Sq-d2Sq+LSq) / (2*L);
        
        res.setSpineCoord( (r1.getContent2() + deltaSpineDist * (r1.getContent2()<r2.getContent2()?1:-1)) );
        res.setSpineLength(length);
        Vector spineDir = r1.getContent1().duplicate().weightedSum(r2.getContent1(),deltaSpineDist, L-deltaSpineDist);
        res.setSpineRadius(spineDir.norm());
        double sign = Math.signum(spineDir.dotProduct(Vector.vector(r1.duplicate().weightedSum(r2, deltaSpineDist, L-deltaSpineDist), p))); // on which side of the spine is the point ? 
        res.setDistFromSpine(sign * Math.sqrt(d1Sq - deltaSpineDist*deltaSpineDist));
        if (testMode) logger.debug("get coord : {}, delta spine dist: {}, spine dir: {}", res, deltaSpineDist, spineDir);
        return res;
    }
    public Point project(BacteriaSpineCoord coord) {
        Comparator<PointContainer2<Vector, Double>> comp = (p1, p2)->Double.compare(p1.getContent2(), p2.getContent2());
        Double spineCoord = coord.spineCoord(true) * length;
        PointContainer2<Vector, Double> searchKey = new PointContainer2<>(null, spineCoord);
        int idx = Arrays.binarySearch(spine, searchKey, comp);
        if (testMode) logger.debug("projecting : {}, spineCoord: {}, search idx: {} (ip: {})", coord, spineCoord, idx, (idx<0?-idx-1:idx));
        if (idx<0) {
            int ip = -idx-1;
            if (ip==spine.length) return projectFromVertebra(spine[spine.length-1],coord.distFromSpine(false));
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
    public static double distance(Point sourcePoint, Point otherPoint, StructureObject source, StructureObject destination, Map<StructureObject,BacteriaSpineLocalizer> localizerMap ) {
        Point proj = project(sourcePoint, source, destination, localizerMap);
        if (proj==null) return Double.POSITIVE_INFINITY;
        return otherPoint.dist(proj) * source.getScaleXY();
    }
    public static Point project(Point sourcePoint, StructureObject source, StructureObject destination, Map<StructureObject,BacteriaSpineLocalizer> localizerMap ) {
        if (destination.getPrevious()==source.getPrevious() && destination.getTrackHead()==source.getTrackHead()) return project(sourcePoint, localizerMap.get(source), localizerMap.get(destination));
        List<StructureObject> successiveContainers = new ArrayList<>(destination.getFrame()-source.getFrame()+1);
        StructureObject cur = destination;
        while (cur!=source) {
            successiveContainers.add(cur);
            cur = cur.getPrevious();
            if (cur.getFrame()<source.getFrame()) return null;
        }
        successiveContainers = Utils.reverseOrder(successiveContainers);
        Point curentProj = sourcePoint;
        for (StructureObject next : successiveContainers) {
            if (cur.getTrackHead()==next.getTrackHead()) {
                logger.debug("project: {}({}) -> {}({})", curentProj, project(curentProj, localizerMap.get(cur), localizerMap.get(next)));
                curentProj = project(curentProj, localizerMap.get(cur), localizerMap.get(next));
            }
            else { // division -> get division proportion
                List<StructureObject> sib = next.getDivisionSiblings(false);
                double totalLength = 0;
                for (StructureObject n : sib) totalLength += localizerMap.get(n).getLength();
                double curLength = localizerMap.get(next).getLength();
                double prop = curLength/(totalLength+curLength);
                boolean upperCell = next.getIdx() < sib.stream().mapToInt(o->o.getIdx()).min().getAsInt();
                logger.debug("project div: {}({}) -> {}({}), div prop: {}, upper cell: {}", curentProj, projectDiv(curentProj, localizerMap.get(cur), localizerMap.get(next), prop, upperCell));
                curentProj = projectDiv(curentProj, localizerMap.get(cur), localizerMap.get(next), prop, upperCell);
            }
            cur = next;
        }
        return curentProj;
    }
    public static Point project(Point sourcePoint, BacteriaSpineLocalizer source, BacteriaSpineLocalizer destination) {
        BacteriaSpineCoord c = source.getCoord(sourcePoint);
        return destination.project(c);
    }
    public static Point projectDiv(Point origin, BacteriaSpineLocalizer source, BacteriaSpineLocalizer destination, double divProportion, boolean upperCell) {
        BacteriaSpineCoord c = source.getCoord(origin);
        c.setDivisionPoint(divProportion, upperCell);
        return destination.project(c);
    }
}
