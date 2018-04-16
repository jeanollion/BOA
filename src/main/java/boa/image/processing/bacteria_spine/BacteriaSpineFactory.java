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
        //Point center = fromSkeleton ? Point.asPoint((Offset)skeleton.get(skeleton.size()/2)) : bacteria.getGeomCenter(false) ; 
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
        // 1) get contour pair for each skeleton point
        List<Pair<CircularNode<T>, CircularNode<T>>> contourPairs = mapToContourPair(skeleton, contour, circContour,new SimpleOffset(mask).reverseOffset());
        List<PointContainer2<Vector, Double>> spListSk = contourPairs.stream().map(v -> PointContainer2.fromPoint(Point.middle2D(v.key.element, v.value.element), Vector.vector2D(v.key.element, v.value.element), 0d)).collect(Collectors.toList());
        if (verbose) ImageWindowManagerFactory.showImage(drawSpine(mask, spListSk.toArray(new PointContainer2[spListSk.size()]), circContour, 5).setName("skeleton init spine"));
        // 2) start getting the spList in one direction and the other
        List<PointContainer2<Vector, Double>> spList = getSpineInDirection(mask, contourPairs.get(0).key, contourPairs.get(0).value, true, contour);
        spList = Utils.reverseOrder(spList);
        spList.addAll(spListSk);
        spList.addAll(getSpineInDirection(mask, contourPairs.get(contourPairs.size()-1).key, contourPairs.get(contourPairs.size()-1).value, false, contour));
        if (verbose) logger.debug("sk spine: total points: {}", spList.size());
        PointContainer2<Vector, Double>[] spine = spList.toArray(new PointContainer2[spList.size()]);
        // 3) compute distances from first poles
        for (int i = 1; i<spine.length; ++i) spine[i].setContent2((spine[i-1].getContent2() + spine[i].dist(spine[i-1])));
        //if (verbose) ImageWindowManagerFactory.showImage(drawSpine(mask, spine, circContour, 5).setName("skeleton spine"));
        smoothDirections(Arrays.asList(spine), 2);
        if (verbose) ImageWindowManagerFactory.showImage(drawSpine(mask, spine, circContour, 5).setName("skeleton Spine after smooth"));
        
        return spine;
    }
    public static PointContainer2<Vector, Double>[] createSpineFromCenter(ImageMask mask, Set<Localizable> contour, CircularNode<Localizable> circContour) {
        // 1) start @ center, determined as furthest point from poles (2 most distant points of contour)
        CircularNode<Localizable>[] startSpine = getStartSpine(mask, contour, circContour);
        if (startSpine==null) return new PointContainer2[0];
        // 2) start getting the spList in one direction and the other
        List<PointContainer2<Vector, Double>> spList = getSpineInDirection(mask, startSpine[0], startSpine[1], true, contour);
        spList = Utils.reverseOrder(spList);
        spList.add(PointContainer2.fromPoint(Point.middle2D(startSpine[0].element, startSpine[1].element),  Vector.vector2D(startSpine[0].element, startSpine[1].element), 0d)); // add this point only one time
        spList.addAll(getSpineInDirection(mask, startSpine[0], startSpine[1], false, contour));
        if (verbose) logger.debug("spine: total points: {}", spList.size());
        // 3) make shure first point is closer to the upper-leftmost point [un necessary]
        if (spList.get(0).distSqXY(circContour.element)>spList.get(spList.size()-1).distSqXY(circContour.element)) {
            spList = Utils.reverseOrder(spList);
            for (PointContainer2<Vector, Double> p : spList) p.getContent1().reverseOffset();
        }
        PointContainer2<Vector, Double>[] spine = spList.toArray(new PointContainer2[spList.size()]);
        // 4) compute distances from first poles
        for (int i = 1; i<spine.length; ++i) spine[i].setContent2((spine[i-1].getContent2() + spine[i].dist(spine[i-1])));
        if (verbose) ImageWindowManagerFactory.showImage(drawSpine(mask, spine, circContour, 5).setName("Spine"));
        
        return spine;
    }
    @Deprecated
    private static CircularNode<Localizable>[] getStartSpine(ImageMask mask, Set<Voxel> contour, CircularNode<Localizable> circContour, Point center) {
        
        // 1) getFollowing initial spList point: contour point closest to the center 
        Voxel startSpineVox1 = contour.stream().min((v1, v2)-> Double.compare(v1.getDistanceSquareXY(center.get(0), center.get(1)), v2.getDistanceSquareXY(center.get(0), center.get(1)))).get();
        CircularNode<Localizable> startSpine1 = circContour.getInNext(startSpineVox1);
        // 2) getFollowing opposed circularContour point: point of the contour in direction of nearest point - center, with local min distance to circContour point
        Vector spDir = new Vector((float)(center.get(0)-startSpineVox1.x), (float)(center.get(1)-startSpineVox1.y)).normalize();
        if (Double.isInfinite(spDir.get(0))) return null;
        Point curSP2 = center.duplicate().translate(spDir);
        while(mask.containsWithOffset(curSP2.xMin(), curSP2.yMin(), mask.zMin()) && mask.insideMaskWithOffset(curSP2.xMin(), curSP2.yMin(), mask.zMin())) curSP2.translate(spDir);
        Voxel startSpineVox2 = contour.stream().min((v1, v2)->Double.compare(v1.getDistanceSquareXY(curSP2.get(0), curSP2.get(1)), v2.getDistanceSquareXY(curSP2.get(0), curSP2.get(1)))).get();
        CircularNode<Localizable> startSpine2 = circContour.getInNext(startSpineVox2);
        if (startSpine2==null) {
            if (verbose) ImageWindowManagerFactory.showImage(TypeConverter.toCommonImageType(mask));
            if (verbose) ImageWindowManagerFactory.showImage(BacteriaSpineFactory.drawSpine(mask, null, circContour, 1));
            throw new RuntimeException("oposite voxel ("+startSpineVox2.duplicate().translate(new SimpleOffset(mask).reverseOffset())+") from center ("+center.duplicate().translate(new SimpleOffset(mask).reverseOffset())+") not found in circular contour (nearst voxel: "+startSpineVox1.duplicate().translate(new SimpleOffset(mask).reverseOffset())+")");
        }
        // get local minimum around startSpine2
        double minDist =GeomUtils.distSqXY(startSpine2.element, startSpineVox1);
        CircularNode<Localizable> start2L = startSpine2; // search in next direction
        while(center.distSq(start2L.next.element)<minDist) {
            start2L = start2L.next;
            minDist = center.distSq(start2L.element);
        }
        CircularNode<Localizable> start2R = startSpine2; // search in prev direction
        while(center.distSq(start2R.prev.element)<minDist) {
            start2R = start2R.prev;
            minDist = center.distSq(start2R.element);
        }
        startSpine2 = start2R.equals(startSpine2) ? start2L : start2R; // if start2R was modified, a closer vox was found in this direction than in the other one
        if (verbose) logger.debug("center: {}, startSpine1: {} startSpine2: {}", center, startSpineVox1, startSpineVox2);
        return new CircularNode[]{startSpine1, startSpine2};
    }
    private static CircularNode<Localizable>[] getStartSpine(ImageMask mask, Set<Localizable> contour, CircularNode<Localizable> circContour) {
        // 1) get 2 points more distant in contour = "poles"
        double d2Max = 0;
        List<Localizable> list = new ArrayList<>(contour);
        Localizable[] max = new Localizable[2];
        int voxCount = list.size();
        for (int i = 0; i<voxCount-1; ++i) {
            for (int j = i+1; j<voxCount; ++j) {
                double d2Temp = GeomUtils.distSqXY(list.get(i), list.get(j));
                if (d2Temp>d2Max) {
                    d2Max = d2Temp;
                    max[0] = list.get(i);
                    max[1] = list.get(j);
                }
            }
        }
        // 2) get furthest point from both
        Function<Localizable, Double> dist = v->GeomUtils.distSqXY(max[0], v)+GeomUtils.distSqXY(max[1], v);
        Localizable side1V = contour.stream().min((v1, v2)->Double.compare(dist.apply(v1), dist.apply(v2))).get();
        // 3) get voxel closest to start but on the other side of 2 poles
        CircularNode<Localizable> pole1 = circContour.getInFollowing(max[0], true);
        CircularNode<Localizable> pole2 = circContour.getInFollowing(max[1], true);
        CircularNode<Localizable> side1 = pole1.getInFollowing(side1V, true);
        boolean startBeforeSide1 = side1.compareTo(pole1)<0;
        CircularNode<Localizable> side2=null;
        CircularNode<Localizable> cn = pole1;
        double dMin = Double.POSITIVE_INFINITY;
        while(cn!=pole2) {
            cn=cn.getFollowing(startBeforeSide1);
            double d = GeomUtils.distSqXY(side1V, cn.element);
            if (d<dMin) {
                side2 = cn;
                dMin = d;
            }
        }
        if (verbose) {
            Offset off = new SimpleOffset(mask).reverseOffset();
            logger.debug("start points: {}-{} poles: {}-{}", Point.asPoint2D(side1.element).translate(off), Point.asPoint2D(side2.element).translate(off), Point.asPoint2D(pole1.element).translate(off), Point.asPoint2D(pole2.element).translate(off));
        }
        return new CircularNode[]{side1, side2};
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
        Pair<CircularNode<T>, CircularNode<T>> centerV = toContourPair(ver1, circContour.getInFollowing(closest, true), circContour.getInFollowing(closest2, true), bucket, logOff);
        if (verbose) logger.debug("sk->contour: first point {}", translateDuplicate(centerV, logOff));
        Pair<CircularNode<T>, CircularNode<T>> lastV = centerV;
        for (int i = centerIdx-1; i>=0; --i) {
            lastV = toContourPair(skeleton.get(i), lastV.key, lastV.value, bucket, logOff);
            res.add(lastV);
        }
        //if (res.size()>3) smoothLastContourPair(res, 2);
        
        if (!res.isEmpty()) res = Utils.reverseOrder(res);
        res.add(centerV);
        lastV =centerV;
        for (int i = centerIdx+1; i<skeleton.size(); ++i) {
            lastV = toContourPair(skeleton.get(i), lastV.key, lastV.value, bucket, logOff);
            res.add(lastV);
        }
        //if (res.size()>3) smoothLastContourPair(res, 2);
        if (verbose) logger.debug("to contour pair done");
        return res;
    }
    // TODO FIX: sometimes moves the point. Should be the 2 contour points that yield in the closest point & closest direction to smoothed vector....
    /**
     * Spine creation is highly sentitive on last contour pair
     * This method will smooth the direction of the last vector and update the contour pair acording to the smoothed direction
     * @param res 
     */
    private static <T extends Localizable> void smoothLastContourPair(List<Pair<CircularNode<T>, CircularNode<T>>> contourPairs, double sigma) {
        List<PointContainer2<Vector, Double>> lastPairs = IntStream.range(Math.max(0, contourPairs.size()-((int)(sigma*2.5)+1)), contourPairs.size()-1).mapToObj(i -> {
            Pair<CircularNode<T>, CircularNode<T>> v = contourPairs.get(i);
            return PointContainer2.fromPoint(Point.middle2D(v.key.element, v.value.element), Vector.vector2D(v.key.element, v.value.element), 0d);
        }).collect(Collectors.toList());
        for (int i = lastPairs.size()-2; i>=0; --i) lastPairs.get(i).setContent2((lastPairs.get(i+1).getContent2() + lastPairs.get(i).dist(lastPairs.get(i+1))));
        //logger.debug("smooth last contour pair: distance to last: {}", Utils.toStringList(lastPairs, p->p.getContent2()));
        PointSmoother<Vector> smoother = new PointSmoother(sigma);
        smoother.init(lastPairs.get(lastPairs.size()-1).getContent1(), true);
        for (int i = lastPairs.size()-2; i>=0; --i) {
            if (!smoother.add(lastPairs.get(i).getContent1(), lastPairs.get(i).getContent2())) break;
        }
        Vector newVect = smoother.getSmoothed();
        // now update contour points to fit vector
        Pair<CircularNode<T>, CircularNode<T>> lastP = contourPairs.get(contourPairs.size()-1);
        Point left = lastPairs.get(lastPairs.size()-1).duplicate().translate(newVect.duplicate().multiply(-0.5));
        //logger.debug("left before : target: {} contour point: {}", left, lastP.key.element);
        lastP.key = CircularContourFactory.getClosest(lastP.key, left);
        //logger.debug("left : target: {} contour point: {}", left, lastP.key.element);
        Point right = lastPairs.get(lastPairs.size()-1).duplicate().translate(newVect.duplicate().multiply(0.5));
        //logger.debug("right before : target: {} contour point: {}", right, lastP.value.element);
        lastP.value = CircularContourFactory.getClosest(lastP.value, right);
        //logger.debug("right : target: {} contour point: {}", right, lastP.value.element);
    }
    private static <T extends Localizable> Pair<CircularNode<T>, CircularNode<T>> toContourPair(Voxel vertebra, CircularNode<T> start1, CircularNode<T> start2, List<Pair<CircularNode<T>, CircularNode<T>>> bucket, Offset logOff) {
        ContourPairComparator<T> comp = new ContourPairComparator<>(vertebra, start1, start2, bucket);
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
        int pushWihtoutChangeCounter = 0; // allow to explore a little bit more the contour space
        while(pushWihtoutChangeCounter<=4 || comp.minAlign>ContourPairComparator.alignTolerance) {
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
        final Voxel vertebra;
        Pair<CircularNode<T>, CircularNode<T>> min, direct, indirect, nextDirect, nextIndirect;
        double minDist, minAlign;
        double minAlignND, minAlignNI; // for direct / indirect push -> record best aligned since last modification of indirect / direct
        
        final List<Pair<CircularNode<T>, CircularNode<T>>> bucket;
        final ToDoubleBiFunction<CircularNode<T>, CircularNode<T>> alignScore;
        final ToDoubleBiFunction<CircularNode<T>, CircularNode<T>> distScore = (p1, p2) ->GeomUtils.distSqXY(p1.element, p2.element);
        public ContourPairComparator(Voxel vertebra, CircularNode<T> start1, CircularNode<T> start2, List<Pair<CircularNode<T>, CircularNode<T>>> bucket) {
            min = new Pair<>(start1, start2);
            this.vertebra = vertebra;
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
            if (c1.equals(c2) || c2.next().equals(c1)) return false; // points cannot touch or cross
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
                if (nextDirect==null) return false;
                this.direct.key=this.nextDirect.key;
                this.direct.value=this.nextDirect.value;
                minAlignND = Double.POSITIVE_INFINITY;
                return true;
            } else {
                if (nextIndirect==null) return false;
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
    
    
    private static <T extends Localizable> List<PointContainer2<Vector, Double>> getSpineInDirection(ImageMask mask, CircularNode<T> s1, CircularNode<T> s2, boolean firstNext, Set<T> contour) {
        List<PointContainer2<Vector, Double>> sp = new ArrayList<>();
        SlidingVector lastDir  = new SlidingVector(5, Vector.vector2D(s1.element, s2.element));
        Point lastPoint;
        
        // to take into acount deforamations of the bacteria (is contour longer on a side than on the other) consider 3 next: 1 next & 2, 2next & 1, 1 next & 2 next & getFollowing the minimal scenario
        List<CircularNode<T>> bucketSecond=new ArrayList<>(4);
        List<CircularNode<T>> bucketFirst=new ArrayList<>(4);
        while(continueLoop(s1, s2, firstNext)) { // loop until contour points reach each other
            Pair<CircularNode<T>, CircularNode<T>> next = getNextUnAlignedCandidates(s1, s2, firstNext);
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
                Point other1  = getNearestPoint(next.key, next.value, bucketSecond, !firstNext);
                dir = Vector.vector2D(next.key.element, other1);
                newPoint = Point.middle2D(other1, next.key.element);
                Point other2  =getNearestPoint(next.value, next.key, bucketFirst, firstNext);
                Vector dir2 = Vector.vector2D(other2, next.value.element);
                Point newPoint2 = Point.middle2D(other2, next.value.element);
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
                } else if (!algorithmWithRadius) s1 = next.key;
                if (push1) {
                    bucketSecond.add(next.value);
                    Collections.sort(bucketSecond);
                    s2 = firstNext ? bucketSecond.get(0) : bucketSecond.get(bucketSecond.size()-1);
                } else if (!algorithmWithRadius) s2 = next.value;
            }
            
            lastDir.push(dir);
            sp.add(PointContainer2.fromPoint(newPoint, dir, 0d));
            
            // STOP CONDITIONS
            Vector spineDir = lastDir.get().duplicate().normalize().rotateXY90();
            if (!firstNext) spineDir.reverseOffset();
            // stop condition: reach contour
            if (contour.contains(newPoint.asVoxel())) { // case of contour containing voxels
                adjustPointToContour(sp.get(sp.size()-1), spineDir, s1.getInFollowing((T)newPoint.asVoxel(), firstNext), bucketFirst);
                return sp;
            } else { // other stop condition: out of mask in direction of spine
                Point nextPoint = newPoint.duplicate().translate(spineDir);
                Point nextPoint2 = nextPoint.duplicate().translate(spineDir);
                if (!mask.containsWithOffset(nextPoint2.getIntPosition(0), nextPoint2.getIntPosition(1), mask.zMin()) || !mask.insideMaskWithOffset(nextPoint2.getIntPosition(0), nextPoint2.getIntPosition(1), mask.zMin())) {
                    CircularNode<T> searchStart = CircularNode.searchForFirstCloseElement(nextPoint, 1, s1, firstNext, !firstNext);
                    if (searchStart == null) return sp;
                    sp.add(PointContainer2.fromPoint(nextPoint, dir.duplicate(), 0d));
                    adjustPointToContour(sp.get(sp.size()-1), spineDir, searchStart, bucketFirst);
                    return sp;
                }
                
            }
            
            lastPoint = newPoint;
            //if (verbose) logger.debug("contour: [{};{}] -> {}", s1.element, s2.element, sp.get(sp.size()-1));
            
        }
        if (sp.size()>1) sp.remove(sp.size()-1); // if stop with meet condition remove last point -> no direction
        return sp;
    }
    
    private static <T extends Localizable> PointContainer2<Vector, Double> getPointAndDirIfAligned(CircularNode<T> p1, CircularNode<T> p2) {
        Vector ref = Vector.vector2D(p1.element, p2.element).normalize();
        boolean aligned1 = Vector.vector2D(p1.prev.element, p2.element).normalize()==ref || Vector.vector2D(p1.next.element, p2.element).normalize()==ref;
        boolean aligned2 = Vector.vector2D(p1.element, p2.prev.element).normalize()==ref || Vector.vector2D(p1.element, p2.next.element).normalize()==ref;
        if (!aligned1 && !aligned2) return null;
        Point mid1, mid2;
        if (aligned1) { // middle point
            CircularNode<T> p1Prev = p1;
            while(Vector.vector2D(p1Prev.prev.element, p2.element).normalize()==ref) p1Prev=p1.prev;
            CircularNode<T> p1Next = p1;
            while(Vector.vector2D(p1Next.next.element, p2.element).normalize()==ref) p1Next=p1.next;
            mid1 = Point.middle2D(p1Prev.element, p1Next.element);
        } else mid1  = Point.asPoint2D(p1.element);
        if (aligned2) { // middle point
            CircularNode<T> p2Prev = p2;
            while(Vector.vector2D(p1.element, p2Prev.prev.element).normalize()==ref) p2Prev=p2.prev;
            CircularNode<T> p2Next = p2;
            while(Vector.vector2D(p1.element, p2Next.next.element).normalize()==ref) p2Next=p2.next;
            mid2 = Point.middle2D(p2Prev.element, p2Next.element);
        } else mid2  = Point.asPoint2D(p2.element);
        return PointContainer2.fromPoint(mid1.duplicate().averageWith(mid2), Vector.vector(mid1, mid2), 0d);
    }
    private static <T extends Localizable> Point getNearestPoint(CircularNode<T> reference, CircularNode<T> firstSearchPoint, List<CircularNode<T>> bucket, boolean searchNext) {
        Function<CircularNode<T>, Double> dist = other->GeomUtils.distSqXY(reference.element, other.element);
        bucket.clear();
        bucket.add(firstSearchPoint);
        if (algorithmWithRadius) {
            int d = 0;
            while(d++<maxSearchRadius) {
                firstSearchPoint = firstSearchPoint.getFollowing(searchNext);
                bucket.add(firstSearchPoint);
            }
        } else {
            bucket.add(firstSearchPoint.next);
            bucket.add(firstSearchPoint.prev);
        }
        bucket.sort((c1, c2)->Double.compare(dist.apply(c1), dist.apply(c2)));
        if (bucket.size()>=2) {
            while(bucket.size()>2) bucket.remove(bucket.size()-1);
            if (dist.apply(bucket.get(0))==dist.apply(bucket.get(1))) { // middle point
                return Point.middle2D(bucket.get(0).element, bucket.get(1).element);
            }
        } 
        while(bucket.size()>1) bucket.remove(bucket.size()-1);
        return Point.asPoint2D(bucket.get(0).element);
    }
    
    /**
     * Stop condition when s1 and s2 meet or cross each other
     * @param s1
     * @param s2
     * @param firstNext
     * @return 
     */
    private static boolean continueLoop(CircularNode s1, CircularNode s2, boolean firstNext) {
        return !s1.equals(s2) && (firstNext ? !s1.prev().equals(s2):!s1.next().equals(s2));
    }
    /**
     * Return the next candidate on the contour
        Ensures next candidate is not alignTest with previous vector
     * @param s1
     * @param s2
     * @param firstNext
     * @param move1
     * @param move2
     * @return 
     */
    private static <T extends Localizable> Pair<CircularNode<T>, CircularNode<T>> getNextUnAlignedCandidates(CircularNode<T> s1, CircularNode<T> s2, boolean firstNext) {
        Vector ref = Vector.vector2D(s1.element, s2.element).normalize();
        Pair<CircularNode<T>, CircularNode<T>> cand = new Pair( firstNext?s1.next():s1.prev(), firstNext?s2.prev():s2.next());
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
    private static <T extends Localizable> CircularNode<T> getNextUnAlignedCandidate(CircularNode<T> reference, CircularNode<T> toMove, boolean moveNext) {
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
            //if (verbose) logger.debug("current dir: {}", v);
            return v;
        }
        public int queueCount() {
            if (queue==null) return 1;
            else return queue.size();
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
