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
import boa.utils.geom.Point;
import boa.utils.geom.PointContainer2;
import boa.utils.geom.Vector;
import java.util.Collections;
import java.util.Comparator;
import net.imglib2.KDTree;
import net.imglib2.neighborsearch.KNearestNeighborSearchOnKDTree;

/**
 *
 * @author jollion
 */
public class BacteriaSpineLocalizer extends BacteriaSpine {
    KDTree<PointContainer2<Vector, Double>> spineKDTree;
    KNearestNeighborSearchOnKDTree<PointContainer2<Vector, Double>> search;
    double[] length;
    public BacteriaSpineLocalizer(Region bacteria) {
        super(bacteria);
        this.spineKDTree = new KDTree<>(spine, spine);
        search = new KNearestNeighborSearchOnKDTree(spineKDTree, 2);
        length = new double[]{super.spineLength, Double.NaN, Double.NaN};
    }
    public void setDivisionPoint(double normalizedSpineCoord) {
        length[1] = spineLength * normalizedSpineCoord;
        length[2] = spineLength * (1-normalizedSpineCoord);
    }
    public static enum ReferencePole {
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
    public double toPole(double spineDist, ReferencePole pole) {
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
    public double fromPole(double normalizedSplineDist, ReferencePole pole, Compartment comp) {
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
    }
    /**
     * 
     * @param p
     * @param referencePole
     * @return {@link BacteriaSpineCoord} representation of {@param p} from {@param referencePole}
     */
    protected BacteriaSpineCoord getCoord(Point p, ReferencePole referencePole, Compartment comp) {
        this.search.search(p);
        boolean firstPointIsCloserToPole = toPole(search.getSampler(0).get().get2(), referencePole)<=toPole(search.getSampler(1).get().get2(), referencePole);
        PointContainer2<Vector, Double> r1 = search.getSampler(firstPointIsCloserToPole?0:1).get();
        double spineDist1 = toPole(r1.get2(), referencePole);
        double d1Sq = search.getDistance(firstPointIsCloserToPole?0:1);
        PointContainer2<Vector, Double> r2 = search.getSampler(firstPointIsCloserToPole?1:0).get();
        double d2Sq = search.getDistance(firstPointIsCloserToPole?1:0);
        double LSq = r1.distSq(r2);
        double L = Math.sqrt(LSq);
        BacteriaSpineCoord res = new BacteriaSpineCoord();
        double deltaSpineDist = (d1Sq-d2Sq+LSq) / L;
        res.absoluteDistanceFromSpine = (d1Sq - deltaSpineDist*deltaSpineDist);
        res.normalizedSpineCoordinate = (spineDist1 + deltaSpineDist)/length[comp.index];
        res.pole=referencePole;
        res.comp=comp;
        Vector spineDir = Vector.weightedSum(r1.get1(), r2.get1(),deltaSpineDist, L-deltaSpineDist);
        Vector pointDir = Vector.vector(r1.duplicate().averageWith(r2), p);
        res.left = spineDir.dotProduct(pointDir)>0;
        return res;
    }
    public Point project(BacteriaSpineCoord coord) {
        Comparator<PointContainer2<Vector, Double>> comp = (p1, p2)->Double.compare(p1.get2(), p2.get2());
        double spineCoord = fromPole(coord.normalizedSpineCoordinate, coord.pole, coord.comp);
        PointContainer2<Vector, Double> searchKey = new PointContainer2<>(null, spineCoord);
        int idx = Collections.binarySearch(spine, searchKey, comp);
        if (idx<0) {
            int ip = -idx-1;
            if (ip==spine.size()) return projectFromVertebra(spine.get(spine.size()-1),coord.absoluteDistanceFromSpine, coord.left);
            // project from 2 adjacent vertebras
            PointContainer2<Vector, Double> v1 = spine.get(ip-1);
            PointContainer2<Vector, Double> v2 = spine.get(ip);
            return projectFrom2Vertebra(v1, v2, spineCoord, coord.absoluteDistanceFromSpine, coord.left);
        } else return projectFromVertebra(spine.get(idx),coord.absoluteDistanceFromSpine, coord.left);
    }
    private Point projectFromVertebra(PointContainer2<Vector, Double> vertebra, double distanceFromSpine, boolean left) {
        return vertebra.duplicate().translate(vertebra.get1().duplicate().multiply(distanceFromSpine*(left?1:-1)));
    }
    private Point projectFrom2Vertebra(PointContainer2<Vector, Double> v1, PointContainer2<Vector, Double> v2, double spineCoord, double distanceFromSpine, boolean left) {
        double interVertebraDist = v2.get2()-v1.get2();
        double w1 = 1-(spineCoord-v1.get2())/interVertebraDist;
        double w2 = 1-(v2.get2()-spineCoord)/interVertebraDist;
        Point v1v2 = Vector.weightedSum(v1, v2, w1, w2);
        Vector dir = Vector.weightedSum(v1.get1(), v2.get1(), w1, w2).normalize().multiply(distanceFromSpine*(left?1:-1));
        return v1v2.translate(dir);
    }
    
}
