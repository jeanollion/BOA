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
import boa.image.processing.EDT;
import boa.image.processing.FillHoles2D;
import boa.image.processing.Filters;
import static boa.image.processing.bacteria_spine.CleanVoxelLine.cleanContour;
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;
import boa.image.processing.neighborhood.Neighborhood;
import boa.measurement.GeometricalMeasurements;
import boa.utils.ArrayUtil;
import boa.utils.Pair;
import boa.utils.Utils;
import boa.utils.geom.GeomUtils;
import boa.utils.geom.Point;
import boa.utils.geom.PointContainer2;
import boa.utils.geom.PointContainer4;
import boa.utils.geom.Vector;
import boa.utils.geom.PointSmoother;
import ij.ImagePlus;
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
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.imglib2.Localizable;
import net.imglib2.RealLocalizable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.skeletonize3D.Skeletonize3D_;

/**
 *
 * @author jollion
 */
public class BacteriaSpineFactory {
    public static final Logger logger = LoggerFactory.getLogger(BacteriaSpineFactory.class);
    public static boolean verbose = false;
    private static int maxSearchRadius = 2;
    private static boolean algorithmWithRadius = false; // using radius -> less able to find poles
    
    public static class SpineResult {
        public PointContainer2<Vector, Double>[] spine;
        public BoundingBox bounds;
        public Set<Localizable> contour;
        public CircularNode<Localizable> circContour;
        public List<Voxel> skeleton;
        public Image drawSpine(int zoomFactor) {
            return BacteriaSpineFactory.drawSpine(bounds, spine, circContour, zoomFactor).setName("Spine");
        }
        public Image drawSkeleton(int zoomFactor) {
            if (skeleton == null) throw new RuntimeException("Skeleton not initialized");
            return BacteriaSpineFactory.drawSpine(bounds, IntStream.range(0, skeleton.size()).mapToObj(i->new PointContainer2(new Vector(0, 0), i+1d, skeleton.get(i).x, skeleton.get(i).y)).toArray(l->new PointContainer2[l]), circContour, 1).setName("skeleton");
        }
    }
    public static SpineResult createSpine(Region bacteria) {
        return createSpine(bacteria, 1);
    }
    public static SpineResult createSpine(Region bacteria, double smoothSigma) {
        if (!bacteria.is2D()) throw new IllegalArgumentException("Only works on 2D regions");
        SpineResult res = new SpineResult();
        res.bounds = new SimpleBoundingBox(bacteria.getBounds());
        long t0 = System.currentTimeMillis();
        Set<Localizable> contour = (Set)bacteria.getContour();
        long t1 = System.currentTimeMillis();
        cleanContour((Set)contour);
        long t2 = System.currentTimeMillis();
        res.contour = (Set)contour;
        List<Voxel> skeleton = getSkeleton(getMaskFromContour((Set)contour));
        long t3 = System.currentTimeMillis();
        //Point center = fromSkeleton ? Point.asPoint((Offset)skeleton.getSum(skeleton.size()/2)) : bacteria.getGeomCenter(false) ; 
        CircularNode<Localizable> circContour;

        long t4 = System.currentTimeMillis();
        circContour = (CircularNode)CircularContourFactory.getCircularContour((Set)contour);
        long t5 = System.currentTimeMillis();
        circContour = (CircularNode)CircularContourFactory.smoothContour2D(circContour,smoothSigma);
        long t6 = System.currentTimeMillis();
        CircularContourFactory.resampleContour((CircularNode)circContour, 1);
        long t7 = System.currentTimeMillis();
        //CircularNode.apply(circContour, c->logger.debug("{} after smooth {}", count[0]++, c.element), true);
        contour = (Set)CircularContourFactory.getSet(circContour);
        long t8 = System.currentTimeMillis();
        res.circContour=circContour;
        res.contour = contour;

        if (circContour!=null) {
            PointContainer2<Vector, Double>[] spine = createSpineFromSkeleton(bacteria.getMask(), skeleton, (Set)contour, circContour);
                    //createSpineFromCenter(bacteria.getMask(), (Set)contour, circContour);
            res.spine = spine;
        }
        long t9 = System.currentTimeMillis();
        if (verbose) logger.debug("getContour: {}ms, clean contour: {}ms, get skeleton: {}ms, close contour: {}ms, smooth contour: {}ms, resample contour: {}ms, get contour set: {}ms, create spine: {}ms total: {}ms", t1-t0, t2-t1, t3-t2, t5-t4, t6-t5, t7-t6, t8-t7, t9-t8, t9-t0);
        return res;
    }
    
    private Voxel getEdtCenter(ImageMask mask) {
        Image edt = EDT.transform(mask, true, 1, 1, 1);
        Voxel[] max = new Voxel[1];
        ImageMask.loopWithOffset(mask, (x, y, z)-> {
            float edtV = edt.getPixelWithOffset(x, y, z);
            if (max[0]==null || edtV>max[0].value) max[0] = new Voxel(x, y, z, edtV);
        });
        return max[0];
    }
    public static ImageByte getMaskFromContour(Set<Voxel> contour) {
        ImageByte im = (ImageByte) new Region(contour, 1, true, 1, 1).getMaskAsImageInteger();
        FillHoles2D.fillHoles(im, 2);
        return im;
    }
    /**
     * Get largest shortest path from skeleton created as in {@link #Skeletonize3D_}
     * Each point has exactly 2 neighbors exepts the two ends that have only one
     * @param mask mask contaiing foreground. WARNING: will be modified
     * @return list of skeleton voxels, ordered from upper-left end
     */
    public static List<Voxel> getSkeleton(ImageByte mask) {
        Skeletonize3D_ skProc = new Skeletonize3D_();
        ImagePlus imp = IJImageWrapper.getImagePlus(mask);
        skProc.setup("", imp);
        skProc.run(imp.getProcessor());
        Set<Voxel> sk = new HashSet<>();
        ImageMask.loopWithOffset(mask, (x, y, z)-> sk.add(new Voxel(x, y, z)));
        //if (verbose) ImageWindowManagerFactory.showImage(new Region(sk, 1, true, 1, 1).getMaskAsImageInteger().setName("skeleton before clean"));
        return CleanVoxelLine.cleanSkeleton(sk, verbose);
    }
    
    public static <T extends Localizable> PointContainer2<Vector, Double>[] createSpineFromSkeleton(ImageMask mask, List<Voxel> skeleton, Set<T> contour, CircularNode<T> circContour) {
        if (verbose) ImageWindowManagerFactory.showImage(drawSpine(mask, IntStream.range(0, skeleton.size()).mapToObj(i->new PointContainer2(new Vector(0, 0), i+1d, skeleton.get(i).x, skeleton.get(i).y)).toArray(l->new PointContainer2[l]), circContour, 1).setName("skeleton"));
        // 1) getSum contour pair for each skeleton point
        Offset logOff = new SimpleOffset(mask).reverseOffset();
        List<Pair<CircularNode<T>, CircularNode<T>>> contourPairs = mapToContourPair(skeleton, contour, circContour,logOff);
        List<PointContainer2<Vector, Double>> spListSk = contourPairs.stream().map(v -> PointContainer2.fromPoint(Point.middle2D(v.key.element, v.value.element), Vector.vector2D(v.key.element, v.value.element), 0d)).collect(Collectors.toList());
        if (verbose) ImageWindowManagerFactory.showImage(drawSpine(mask, spListSk.toArray(new PointContainer2[spListSk.size()]), circContour, 5).setName("skeleton init spine"));
        // 2) start getting the spList in one direction and the other
        int persistanceRadius = Math.min(1+skeleton.size()/2, Math.max(4,(int)ArrayUtil.median(spListSk.stream().mapToDouble(v->v.getContent1().norm()).toArray())/2+1)); // persistence of skeleton direction
        if (verbose) logger.debug("persistance radius: {}", persistanceRadius);
        List<PointContainer2<Vector, Double>> spList = getSpineInSkeletonDirection(mask, contourPairs.get(0).key, contourPairs.get(0).value, spListSk, persistanceRadius, true, logOff);
        spList = Utils.reverseOrder(spList);
        spList.addAll(spListSk);
        spList.addAll(getSpineInSkeletonDirection(mask, contourPairs.get(contourPairs.size()-1).key, contourPairs.get(contourPairs.size()-1).value, spListSk, persistanceRadius, false, logOff));
        if (verbose) logger.debug("sk spine: total points: {}", spList.size());
        PointContainer2<Vector, Double>[] spine = spList.toArray(new PointContainer2[spList.size()]);
        // 3) compute distances from first poles
        for (int i = 1; i<spine.length; ++i) spine[i].setContent2((spine[i-1].getContent2() + spine[i].dist(spine[i-1])));
        //if (verbose) ImageWindowManagerFactory.showImage(drawSpine(mask, spine, circContour, 5).setName("skeleton spine"));
        // 4) smooth direction vectors in order to limit brutal direction change
        smoothDirections(Arrays.asList(spine), 1);
        if (verbose) ImageWindowManagerFactory.showImage(drawSpine(mask, spine, circContour, 5).setName("skeleton Spine after smooth"));
        // 5) TODO recompute center in actual smoothed direction, using intersection with contour function
        return spine;
    }
    
    public static <T extends Localizable> List<Pair<CircularNode<T>, CircularNode<T>>> mapToContourPair(List<Voxel> skeleton, Set<T> contour, CircularNode<T> circContour, Offset logOff) {
        if (skeleton.size()<=2) { // circular shape : convention: axis = X
            return skeleton.stream().map(vertebra -> {
                T left  = contour.stream().filter(v->v.getFloatPosition(0)<vertebra.x).min((v1, v2) -> Double.compare(Math.abs(vertebra.y-v1.getDoublePosition(1)), Math.abs(vertebra.y-v2.getDoublePosition(1)))).get();
                T right  = contour.stream().filter(v->v.getFloatPosition(0)>vertebra.x).min((v1, v2) -> Double.compare(Math.abs(vertebra.y-v1.getDoublePosition(1)), Math.abs(vertebra.y-v2.getDoublePosition(1)))).get();
                return new Pair<>(circContour.getInFollowing(left, false), circContour.getInFollowing(right, true));
            }).collect(Collectors.toList());
        } 
        // start from center and go to each direction
        int centerIdx = skeleton.size()/2;
        // first contour point is closest point
        Voxel ver1 = skeleton.get(centerIdx);
        T closest = contour.stream().min((v1, v2)->Double.compare(GeomUtils.distSqXY(v1, ver1), GeomUtils.distSqXY(v2, ver1))).get();
        // second voxel is closest to a point on the other side of the vertebra
        Point p = Vector.vector2D(closest, ver1).translate(ver1);
        T closest2 = contour.stream().min((v1, v2)->Double.compare(p.distSq(v1), p.distSq(v2))).get();
        while(closest2.equals(closest)) {
            p.translate(Vector.vector2D(closest, ver1));
            closest2 = contour.stream().min((v1, v2)->Double.compare(p.distSq(v1), p.distSq(v2))).get();
        }
        //if (verbose) logger.debug("sk->contour: init {}->{}->{} (dir: {}, 2nd point: {})", closest, ver1, closest2, Vector.vector2D(closest, ver1), p);
        if (Vector.crossProduct2D(Vector.vector2D(closest, ver1), Vector.vector2D(closest, skeleton.get(centerIdx+1)))<0) { // ensure that closest is on the left side compared to skeleton orientation
            T temp = closest; //swap
            closest = closest2;
            closest2 = temp;
        }
        if (verbose) logger.debug("sk->contour: middle of skeleton: {} first point start {}", ver1.duplicate().translate(logOff), translateDuplicate(new Pair<>(circContour.getInFollowing(closest, true), circContour.getInFollowing(closest2, true)), logOff));
        List<Pair<CircularNode<T>, CircularNode<T>>> res = new ArrayList<>(skeleton.size());
        List<Pair<CircularNode<T>, CircularNode<T>>> bucket = new ArrayList<>();
        Pair<CircularNode<T>, CircularNode<T>> centerV = toContourPair(ver1, circContour.getInFollowing(closest, true), circContour.getInFollowing(closest2, true), null, true, bucket, logOff);
        if (verbose) logger.debug("sk->contour: first point {}", translateDuplicate(centerV, logOff));
        Pair<CircularNode<T>, CircularNode<T>> lastV = centerV;
        for (int i = centerIdx-1; i>=0; --i) { // towards top
            lastV = toContourPair(skeleton.get(i), lastV.key, lastV.value, lastV, true, bucket, logOff);
            res.add(lastV);
        }
        if (!res.isEmpty()) res = Utils.reverseOrder(res);
        res.add(centerV);
        lastV =centerV;
        for (int i = centerIdx+1; i<skeleton.size(); ++i) { // towards bottom
            lastV = toContourPair(skeleton.get(i), lastV.key, lastV.value, lastV, false, bucket, logOff);
            res.add(lastV);
        }
        if (verbose) logger.debug("to contour pair done");
        return res;
    }
    private static <T extends Localizable> Pair<CircularNode<T>, CircularNode<T>> toContourPair(Localizable vertebra, CircularNode<T> start1, CircularNode<T> start2, Pair<CircularNode<T>, CircularNode<T>> limit, boolean limitFirstPrev, List<Pair<CircularNode<T>, CircularNode<T>>> bucket, Offset logOff) {
        ContourPairComparator<T> comp = new ContourPairComparator<>(vertebra, start1, start2, limit, limitFirstPrev, bucket);
        if (verbose) logger.debug("to CP start: {} (d={}, a={}/{})", translateDuplicate(comp.min, logOff), comp.minDist, comp.minAlign, ContourPairComparator.alignTolerance);
        // first search: BEST alignement while sliding along contour
        boolean change = true;
        while(change) {
            change = false;
            if (comp.compareToNext(true, true, true, ContourPairComparator.INCREMENT.DOWN)) change = true;
            else if (comp.compareToNext(true, true, true, ContourPairComparator.INCREMENT.UP)) change = true;
            if (verbose) logger.debug("to CP slide: {} (d={}, a={}/{}), direct: {}, bucket: {}", translateDuplicate(comp.min, logOff), comp.minDist, comp.minAlign, ContourPairComparator.alignTolerance, translateDuplicate(comp.direct, logOff), bucket.size());
        }
        if (verbose) logger.debug("to CP after slide: {} (d={}, a={}/{})", translateDuplicate(comp.min, logOff), comp.minDist, comp.minAlign, ContourPairComparator.alignTolerance);
        comp.indirect = new Pair(comp.min.key, comp.min.value);
        comp.direct = new Pair(comp.min.key, comp.min.value);
        // second search: rotation alignTest & minimize distance
        int pushWihtoutChangeCounter = 0; // allows to explore a little bit more the contour space when complex structures in contour
        while(pushWihtoutChangeCounter<=20 || comp.minAlign>ContourPairComparator.alignTolerance) {
            change = false;
            boolean push = false;
            // look in direct direction
            if (comp.compareToNext(true, false, true, ContourPairComparator.INCREMENT.OPPOSITE)) change = true;
            else if (comp.compareToNext(true, true, false, ContourPairComparator.INCREMENT.OPPOSITE)) change = true;
            else if (comp.compareToNext(true, true, true, ContourPairComparator.INCREMENT.OPPOSITE)) change = true;
            if (!change) if (comp.push(true)) push = true;
            boolean changeI = false;
            if (comp.compareToNext(false, false, true, ContourPairComparator.INCREMENT.OPPOSITE)) changeI = true;
            else if (comp.compareToNext(false, true, false, ContourPairComparator.INCREMENT.OPPOSITE)) changeI = true;
            else if (comp.compareToNext(false, true, true, ContourPairComparator.INCREMENT.OPPOSITE)) changeI = true;
            if (!changeI) {
                if (comp.push(false)) push = true;
            } else change = true;
            if (verbose) logger.debug("to CP: {} (d={}, a={}/{}), bucket:{}, direct:{}, indirect: {}, change: {} (I:{}) push: {}, push count: {} ", translateDuplicate(comp.min, logOff), comp.minDist, comp.minAlign, ContourPairComparator.alignTolerance, bucket.size(), translateDuplicate(comp.direct, logOff), translateDuplicate(comp.indirect, logOff), change, changeI, push, pushWihtoutChangeCounter );
            if (!change) {
                if (!push) break;
                ++pushWihtoutChangeCounter;
            } else pushWihtoutChangeCounter=0;
        }
        Pair<CircularNode<T>, CircularNode<T>> min;
        if (bucket.size()>1) { // choose most parallele with previous 
            ToDoubleFunction<Pair<CircularNode<T>, CircularNode<T>>> paralleleScore = p-> Vector.vector2D(p.key.element, p.value.element).dotProduct(Vector.vector2D(start1.element, start2.element));
            min = bucket.stream().max((p1, p2)->Double.compare(paralleleScore.applyAsDouble(p1), paralleleScore.applyAsDouble(p2))).get();
        } else min = comp.min;
        bucket.clear();
        if (verbose) logger.debug("to CP END: {} (d={}, a={}/{}), bucket:{}, direct:{}, indirect: {}", translateDuplicate(comp.min, logOff), comp.minDist, comp.minAlign, ContourPairComparator.alignTolerance, bucket.size(), translateDuplicate(comp.direct, logOff), translateDuplicate(comp.indirect, logOff) );
            
        return min;
    }
    private static class ContourPairComparator<T extends Localizable> {
        private enum INCREMENT {OPPOSITE, DOWN, UP};
        private static double alignTolerance = 1;//Math.cos(170d*Math.PI/180d);
        Pair<CircularNode<T>, CircularNode<T>> min, direct, indirect, nextDirect, nextIndirect;
        double minDist, minAlign;
        double minAlignND, minAlignNI; // for direct / indirect push -> record best aligned since last modification of indirect / direct
        final Pair<CircularNode<T>, CircularNode<T>> limit;
        final boolean limitFirstPrev;
        final List<Pair<CircularNode<T>, CircularNode<T>>> bucket;
        final ToDoubleBiFunction<CircularNode<T>, CircularNode<T>> alignScore;
        final ToDoubleBiFunction<CircularNode<T>, CircularNode<T>> distScore = (p1, p2) ->GeomUtils.distSqXY(p1.element, p2.element);
        public ContourPairComparator(Localizable vertebra, CircularNode<T> start1, CircularNode<T> start2,Pair<CircularNode<T>, CircularNode<T>> limit, boolean limitFirstPrev , List<Pair<CircularNode<T>, CircularNode<T>>> bucket) {
            this.limitFirstPrev = limitFirstPrev;
            this.limit=limit;
            min = new Pair<>(start1, start2);
            direct = new Pair<>(start1, start2);
            indirect = new Pair<>(start1, start2);
            this.bucket=bucket;
            alignScore = (v1, v2) -> {
                Vector ve1 = Vector.vector2D(vertebra, v1.element);
                Vector ve2 = Vector.vector2D(vertebra, v2.element);
                if (ve1.dotProduct(ve2)>=0) return Double.POSITIVE_INFINITY; // if vector are not in opposite direction -> not aligned
                return Math.abs(Vector.crossProduct2D(ve1, ve2)) * 0.5d * (1d/ve1.norm()+1d/ve2.norm()); // estimation of the mean delta
            };
            minDist = distScore.applyAsDouble(start1, start2);
            minAlign = alignScore.applyAsDouble(start1, start2);
            minAlignND = Double.POSITIVE_INFINITY;
            minAlignNI = Double.POSITIVE_INFINITY;
        }
        public boolean compareToNext(boolean direct, boolean incrementLeft, boolean incrementRight, INCREMENT incType) {
            Pair<CircularNode<T>, CircularNode<T>> current = direct ? this.direct : indirect;
            CircularNode<T> c1, c2;
            switch(incType) {
                case OPPOSITE:
                default:
                    c1 = incrementLeft ? current.key.getFollowing(direct) : current.key;
                    c2 = incrementRight ? current.value.getFollowing(direct) : current.value;
                    break;
                case DOWN:
                    c1=current.key.getFollowing(!direct);
                    c2=current.value.getFollowing(direct);
                    break;
                case UP:
                    c1=current.key.getFollowing(direct);
                    c2=current.value.getFollowing(!direct);
                    break;
            }
            if (limit!=null) {
                if (limitFirstPrev) {
                    if (c1==limit.key.prev) return false;
                    if (c2==limit.value.next) return false;
                } else {
                    if (c1==limit.key.next) return false;
                    if (c2==limit.value.prev) return false;
                }
            }
            if (c1.equals(c2) || c2.next().equals(c1)) return false; // points cannot touch or cross each other
            double dist = distScore.applyAsDouble(c1, c2);
            double align = alignScore.applyAsDouble(c1, c2);
            if (Double.isInfinite(align)) return false; // no in opposite directions
            if (direct) {
                if (align<minAlignND) {
                    nextDirect = new Pair(c1, c2);
                    minAlignND = align;
                }
            } else {
                if (align<minAlignNI) {
                    nextIndirect = new Pair(c1, c2);
                    minAlignNI = align;
                }
            }
            if (minAlign>=alignTolerance) { // minimize on align score
                if (align>minAlign || (align==minAlign && dist>=minDist)) return false;
            } else { // minimize on dist
                if (align>=alignTolerance || dist>minDist || (dist==minDist && align>=minAlign)) return false;
            }
            if (direct) {
                this.direct.key=c1;
                this.direct.value=c2;
                minAlignND = Double.POSITIVE_INFINITY;
            } else {
                this.indirect.key=c1;
                this.indirect.value = c2;
                minAlignNI = Double.POSITIVE_INFINITY;
            }
            if ((minAlign>=alignTolerance && (align<minAlign || align==minAlign && dist<minDist)) || (dist<minDist || (dist==minDist && align<minAlign))) { // update min values
                min.key = c1;
                min.value = c2;
                bucket.clear();
                bucket.add(min);
                minAlign = align;
                minDist = dist;
            } else bucket.add(direct ? new Pair(this.direct.key, this.direct.value) : new Pair(this.indirect.key, this.indirect.value)); // equality
            return true;
        }
        public boolean push(boolean direct) {
            if (direct) {
                if (nextDirect==null || (nextDirect.key.equals(this.direct.key) && nextDirect.value.equals(this.direct.value))) return false;
                this.direct.key=this.nextDirect.key;
                this.direct.value=this.nextDirect.value;
                minAlignND = Double.POSITIVE_INFINITY;
                return true;
            } else {
                if (nextIndirect==null || (nextIndirect.key.equals(this.indirect.key) && nextIndirect.value.equals(this.indirect.value))) return false;
                this.indirect.key=this.nextIndirect.key;
                this.indirect.value = this.nextIndirect.value;
                minAlignNI = Double.POSITIVE_INFINITY;
                return true;
            }
        }
        
    }
    private static <T extends Localizable> Pair<Point, Point> translateDuplicate(Pair<CircularNode<T>, CircularNode<T>> p , Offset off) {
        return new Pair<>(Point.asPoint2D(p.key.element).translate(off), Point.asPoint2D(p.value.element).translate(off));
    }
    
    private static <T extends Localizable> List<PointContainer2<Vector, Double>> getSpineInSkeletonDirection(ImageMask mask, CircularNode<T> s1, CircularNode<T> s2, List<PointContainer2<Vector, Double>> skeleton, int persistanceRadius, boolean firstNext, Offset logOff) {
        List<PointContainer2<Vector, Double>> sp = new ArrayList<>();
        Vector skDir = SlidingVector.getMeanVector2D(skeleton.stream().skip(firstNext?0:skeleton.size()-persistanceRadius).limit(persistanceRadius).map(p->p.getContent1()));
        Vector spineDir = skDir.duplicate().normalize().rotateXY90();
        if (!firstNext) spineDir.reverseOffset();
        Point lastPoint = skeleton.get(firstNext ? 0 : skeleton.size()-1);
        List<CircularNode<T>> bucketFirst = new ArrayList<>();
        while(true) { // loop point gets out of bacteria or mask
            Point nextPoint = lastPoint.duplicate().translate(spineDir);
            // get direction of current point according to contour
            Point c1 = getIntersectionWithContour(mask, nextPoint, skDir.duplicate().multiply(-1), s1, bucketFirst, logOff);
            s1 = bucketFirst.get(0); // push
            Point c2 = getIntersectionWithContour(mask, nextPoint, skDir.duplicate(), s2, bucketFirst, logOff);
            s2 = bucketFirst.get(0); // push
            PointContainer2<Vector, Double> next = PointContainer2.fromPoint(nextPoint, Vector.vector2D(c1, c2), 0d);
            sp.add(next); 
            // stop condition
            Point nextPoint2 = next.duplicate().translate(spineDir);
            Voxel nextVox2 = nextPoint2.asVoxel();
            if (!mask.containsWithOffset(nextVox2.x, nextVox2.y, mask.zMin()) || !mask.insideMaskWithOffset(nextVox2.x, nextVox2.y, mask.zMin())) { 
                adjustPointToContour(next, spineDir, s2, bucketFirst); // adjust to contour
                return sp;
            }
            lastPoint = next;
            //if (verbose) logger.debug("contour: [{};{}] -> {}", s1.element, s2.element, sp.getSum(sp.size()-1));   
        }
    }
    
    /**
     * Sets the coordinates of {@param p} to the nearest intersection point with the contour, in the direction of {@param dir}
     * @param p starting startPoint of p, can be away from contour
     * @param dir  norm should be about 2 times distance from {@param p} to contour
     * @param firstSearchPoint nearest or close to nearest contour p from {@param p} in the direction {@param dir}
     * @param bucket used to store contour points, will have one or 2 contour startPoint after execution 
     */
    private static <T extends Localizable> Point getIntersectionWithContour(ImageMask mask, Point startPoint, Vector dir, CircularNode<T> firstSearchPoint, List<CircularNode<T>> bucket, Offset logOff) {
        // get first p outside mask with a distance <1
        Point p = startPoint.duplicate();
        Voxel vox = p.asVoxel();
        boolean out1 = !mask.containsWithOffset(vox.x, vox.y, vox.z) || !mask.insideMaskWithOffset(vox.x, vox.y, vox.z);
        double dirNorm = dir.norm();
        if (dirNorm>=0.5) {
            dir.multiply(0.5);
            dirNorm*=0.5;
        }
        p.translate(dir);
        vox.x = p.getIntPosition(0);
        vox.y = p.getIntPosition(1);
        boolean out2 = !mask.containsWithOffset(vox.x, vox.y, vox.z) || !mask.insideMaskWithOffset(vox.x, vox.y, vox.z);
        while(!out2 || dirNorm>=0.5) {
            if (out1!=out2) dir.multiply(-1); // reverse dir
            if (dirNorm>=0.5) {
                dir.multiply(0.5);
                dirNorm*=0.5;
            }
            out1 = out2;
            p.translate(dir);
            vox.x = p.getIntPosition(0);
            vox.y = p.getIntPosition(1);
            out2 = !mask.containsWithOffset(vox.x, vox.y, vox.z) || !mask.insideMaskWithOffset(vox.x, vox.y, vox.z);
        }
        CircularNode.addTwoLocalNearestPoints(p, firstSearchPoint, bucket);
        if (verbose) logger.debug("adjust to contour: closest points: {}, dir: {} (norm: {}), start p: {}, closest p {}", Utils.toStringList(bucket, b->Point.asPoint2D(b.element).translate(logOff)), dir.normalize(), dirNorm, startPoint.duplicate().translate(logOff), p.duplicate().translate(logOff));
        if (bucket.size()==1) {
            p.setData(bucket.get(0).element.getFloatPosition(0), bucket.get(0).element.getFloatPosition(1));
        } else {
            if (p.equals(startPoint)) p.translate(dir.normalize().multiply(0.25));
            Point inter = Point.intersect2D(startPoint, p, Point.asPoint2D(bucket.get(0).element), Point.asPoint2D(bucket.get(1).element));
            if (verbose) logger.debug("adjust to contour: intersection: {}", inter.duplicate().translate(logOff));
            if (inter!=null) p.setData(inter);
        }
        return p;
    }
    /**
     * Sets the coordinates of {@param p} to the nearest intersection point with the contour, in the direction of {@param dir}
     * @param p
     * @param dir
     * @param firstSearchPoint nearest or close to nearest contour point from {@param p} in the direction {@param dir}
     * @param bucket 
     */
    private static <T extends Localizable> void adjustPointToContour(Point p, Vector dir, CircularNode<T> firstSearchPoint, List<CircularNode<T>> bucket) {
        CircularNode.addTwoLocalNearestPoints(p, firstSearchPoint, bucket);
        //logger.debug("adjust to contour: closest points: {}, dir: {}, start point: {}", Utils.toStringList(bucket, b->b.element.toString()), dir, p);
        if (bucket.size()==1) {
            p.setData(bucket.get(0).element.getFloatPosition(0), bucket.get(0).element.getFloatPosition(1));
        } else {
            Point inter = Point.intersect2D(p, p.duplicate().translate(dir), Point.asPoint2D(bucket.get(0).element), Point.asPoint2D(bucket.get(1).element));
            //logger.debug("adjust to contour: intersection: {}", inter);
            if (inter!=null) p.setData(inter);
        }
    }
    
    public static <T extends RealLocalizable> Image drawSpine(BoundingBox bounds, PointContainer2<Vector, Double>[] spine, CircularNode<T> circularContour, int zoomFactor) { 
        if (zoomFactor%2==0) throw new IllegalArgumentException("Zoom Factory should be uneven");
        int add = zoomFactor > 1 ? 1 : 0;
        ImageFloat spineImage = new ImageFloat("", new SimpleImageProperties(new SimpleBoundingBox(0, bounds.sizeX()*zoomFactor-1, 0, bounds.sizeY()*zoomFactor-1, 0, 0), 1, 1));
        spineImage.translate(bounds);
        Offset off = bounds;
        Voxel vox = new Voxel(0, 0, 0);
        // draw contour of bacteria
        int startLabel = spine==null ? 1: Math.max(spine[spine.length-1].getContent2().intValue(), spine.length) +10;
        if (circularContour!=null) {
            EllipsoidalNeighborhood neigh = new EllipsoidalNeighborhood(zoomFactor/2d, false);
            int[] lab = new int[]{startLabel};
            CircularNode.apply(circularContour, c->{
                for (int i = 0; i<neigh.getSize(); ++i) {
                    vox.x = (int)Math.round((c.element.getDoublePosition(0)-off.xMin())*zoomFactor+add+neigh.dx[i]);
                    vox.y = (int)Math.round((c.element.getDoublePosition(1)-off.yMin())*zoomFactor+add+neigh.dy[i]);
                    if (spineImage.contains(vox.x, vox.y, 0)) spineImage.setPixel(vox.x, vox.y, 0, lab[0]);
                }
                ++lab[0];
            }, true);
        }
        if (spine!=null) {
            int spineVectLabel = 1;
            for (PointContainer2<Vector, Double> p : spine) {
                double norm = p.getContent1().norm();
                if (norm==0) continue;
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
    private static class SlidingVector  {
        final int n;
        Vector sum;
        Queue<Vector> queue;
        public SlidingVector(int n, Vector initVector) {
            this.n = n;
            if (n>1) {
                queue =new LinkedList<>();
                if (initVector!=null) {
                    queue.add(initVector);
                    sum = initVector.duplicate();
                }
            } else sum=initVector;
            if (sum==null) sum = new Vector(0,0); // default -> 2D
        }
        public Vector getSum() {
            return sum;
        }
        public Vector getMean() {
            int n = queueCount();
            if (n==1) return sum;
            return sum.duplicate().multiply(1d/n);
        }
        public Vector push(Vector add) {
            if (queue!=null) {
                if (queue.size()==n) sum.add(queue.poll(), -1);
                queue.add(add);
                sum.add(add, 1);
            } else sum = add;
            //if (verbose) logger.debug("current dir: {}", sum);
            return sum;
        }
        public int queueCount() {
            if (queue==null) return 1;
            else return queue.size();
        }
        public static Vector getMeanVector2D(Stream<Vector> vectors) {
            Vector v = new Vector(0, 0);
            double[] n = new double[1];
            vectors.forEach(vect -> {v.add(vect, 1); ++n[0];});
            return v.multiply(1d/n[0]);
        }
    }
    private static void smoothDirections(List<PointContainer2<Vector, Double>> spine, double sigma) {
        PointSmoother<Vector> v = new PointSmoother(sigma);
        List<Vector> newVectors = new ArrayList<>(spine.size());
        for (int i = 0; i<spine.size(); ++i) {
            v.init(spine.get(i).getContent1(), true);
            double currentPos = spine.get(i).getContent2();
            int j = i-1;
            while (j>0 && v.add(spine.get(j).getContent1(), Math.abs(currentPos-spine.get(j).getContent2()))) {--j;}
            j = i+1;
            while (j<spine.size() && v.add(spine.get(j).getContent1(), Math.abs(currentPos-spine.get(j).getContent2()))) {++j;}
            newVectors.add(v.getSmoothed());
        }
        for (int i = 0; i<spine.size(); ++i) spine.get(i).setContent1(newVectors.get(i));
    }
}
