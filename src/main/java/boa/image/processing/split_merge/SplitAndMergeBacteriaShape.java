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
package boa.image.processing.split_merge;

import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.Voxel;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.ImageMask;
import boa.image.processing.Curvature;
import boa.image.processing.EDT;
import boa.image.processing.Filters;
import boa.image.processing.ImageOperations;
import boa.image.processing.WatershedTransform;
import boa.image.processing.clustering.ClusterCollection;
import boa.image.processing.clustering.InterfaceRegionImpl;
import boa.image.processing.clustering.RegionCluster;
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;
import boa.image.processing.neighborhood.Neighborhood;
import boa.image.processing.split_merge.SplitAndMergeBacteriaShape.InterfaceLocalShape;
import boa.measurement.GeometricalMeasurements;
import static boa.plugins.Plugin.logger;
import static boa.plugins.plugins.segmenters.BacteriaTrans.debug;
import boa.plugins.plugins.trackers.ObjectIdxTracker;
import static boa.plugins.plugins.trackers.ObjectIdxTracker.getComparatorRegion;
import boa.utils.HashMapGetCreate;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import net.imglib2.KDTree;
import net.imglib2.Point;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;

/**
 *
 * @author jollion
 */
public class SplitAndMergeBacteriaShape extends SplitAndMerge<InterfaceLocalShape> {
    protected final HashMap<Region, KDTree<Double>> curvatureMap = new HashMap<>();
    protected Image distanceMap;
    protected ImageMask mask;
    // TODO uncalibrated parameters
    public int minSizeFusion=0;
    public int curvatureScale=6;
    public double curvatureSearchScale=2.5;
    public double curvatureThreshold=-1, curvatureThreshold2=-.6;
    public double relativeThicknessThreshold=0.7, relativeThicknessMaxDistance=1;
    public boolean ignoreEndOfChannelSmallObjects;
    public boolean curvaturePerCluster = true;
    public boolean splitVerbose=false;
    public int minInterfaceSize = -1;
    private final static double maxMergeDistanceBB = 0; // distance in pixel for merging small objects during main process // was 3
    
    private double yLimLastObject = Double.NaN;
    public BiFunction<? super InterfaceLocalShape, ? super InterfaceLocalShape, Integer> compareMethod=null;
    
    @Override
    public Image getWatershedMap() {
        return distanceMap;
    }

    @Override
    protected ClusterCollection.InterfaceFactory<Region, InterfaceLocalShape> createFactory() {
        return (Region e1, Region e2, Comparator<? super Region> elementComparator) -> new InterfaceLocalShape(e1, e2);
    }
    
    private Image getEDM() {
        return distanceMap;
    }
    private void setDistanceMap(ImageMask mask) {
        distanceMap = EDT.transform(mask, true, 1, mask.getScaleZ()/mask.getScaleXY(), 1);
    }
    
    @Override
    public RegionPopulation merge(RegionPopulation popWS, int minSize, int objectMergeLimit) {
        if (distanceMap == null) setDistanceMap(popWS.getLabelMap()); // REVOIR BESOIN DE DISTANCE MAP A CE STADE. UTILISATION DE MAX DISTANCE POUR RELATIVE THICKNESS A REVIOR
        smoothRegions(popWS, true, true);
        RegionCluster<InterfaceLocalShape> c = new RegionCluster(popWS, false, true, getFactory());
        RegionCluster.verbose=testMode;
        if (minSize>0) c.mergeSmallObjects(minSize, objectMergeLimit, null);
        if (ignoreEndOfChannelSmallObjects && !popWS.getObjects().isEmpty()) yLimLastObject = Collections.max(popWS.getObjects(), (o1, o2)->Double.compare(o1.getBounds().getyMax(), o2.getBounds().getyMax())).getBounds().getyMax();
        if (curvaturePerCluster) updateCurvature(c.getClusters());
        c.mergeSort(objectMergeLimit<=1, 0, objectMergeLimit);
        if (minSize>0) {
            BiFunction<Region, Set<Region>, Region> noInterfaceCase = (smallO, set) -> {
                if (set.isEmpty()) return null;
                Region closest = Collections.min(set, (o1, o2) -> Double.compare(o1.getBounds().getDistance(smallO.getBounds()), o2.getBounds().getDistance(smallO.getBounds())));
                double d = GeometricalMeasurements.getDistanceBB(closest, smallO, false);
                if (debug) logger.debug("merge small objects with no interface: min distance: {} to {} = {}", smallO.getLabel(), closest.getLabel(), d);
                if (d<maxMergeDistanceBB) return closest;
                else return null;
            }; 
            c.mergeSmallObjects(minSize, objectMergeLimit, noInterfaceCase);
        }
        Collections.sort(popWS.getObjects(), getComparatorRegion(ObjectIdxTracker.IndexingOrder.YXZ)); // sort by increasing Y position
        popWS.relabel(true);
        distanceMap = null;
        return popWS;
    }
    @Override
    public RegionPopulation splitAndMerge(ImageInteger segmentationMask, int minSizePropagation, int minSize, int objectMergeLimit) {
        setDistanceMap(segmentationMask);
        WatershedTransform.SizeFusionCriterion sfc = minSizePropagation>1 ? new WatershedTransform.SizeFusionCriterion(minSizePropagation) : null;
        ImageByte seeds = Filters.localExtrema(getEDM(), null, true, segmentationMask, Filters.getNeighborhood(3, 3, getEDM())); // TODO seed radius -> parameter ? 
        RegionPopulation popWS =  WatershedTransform.watershed(getEDM(), segmentationMask, ImageLabeller.labelImageList(seeds), true, null, sfc, true);
        if (testMode) {
            popWS.sortBySpatialOrder(ObjectIdxTracker.IndexingOrder.YXZ);
            ImageWindowManagerFactory.showImage(getWatershedMap());
            ImageWindowManagerFactory.showImage(popWS.getLabelMap().duplicate("seg map before merge"));
        }
        return merge(popWS, minSize, objectMergeLimit);
    }
    
    protected void updateCurvature(List<Set<Region>> clusters) { // need to be called in order to use curvature in InterfaceBT
        curvatureMap.clear();
        ImageByte clusterMap = new ImageByte("cluster map", mask).resetOffset(); // offset is added if getCurvature method
        Iterator<Set<Region>> it = clusters.iterator();
        while(it.hasNext()) {
            Set<Region> clust = it.next();
            for (Region o : clust) o.draw(clusterMap, 1);
            //Filters.binaryOpen(clusterMap, clusterMap, Filters.getNeighborhood(1, 1, clusterMap)); // avoid funny values // done at segmentation step
            KDTree<Double> curv = Curvature.computeCurvature(clusterMap, curvatureScale);
            /*if (debug) {
                logger.debug("curvature map: {}", curv.size());
                try {
                    Image c = Curvature.getCurvatureMask(clusterMap, curv);
                    if (c!=null) ImageWindowManagerFactory.showImage(c);
                } catch(Exception e) {
                    logger.debug("error curv map show", e);
                }
            }*/
            for (Region o : clust) {
                curvatureMap.put(o, curv);
                if (it.hasNext()) o.draw(clusterMap, 0);
            }
        }
    }
    
    
    
    public class InterfaceLocalShape extends InterfaceRegionImpl<InterfaceLocalShape> implements RegionCluster.InterfaceVoxels<InterfaceLocalShape> {
        double maxDistance=Double.NEGATIVE_INFINITY;
        double curvatureValue=Double.POSITIVE_INFINITY;
        double curvL=Double.NaN, curvR=Double.NaN;
        double relativeThickNess = Double.NEGATIVE_INFINITY;
        Voxel maxVoxel=null;
        double value=Double.NaN;
        private final Set<Voxel> borderVoxels = new HashSet<>(), borderVoxels2 = new HashSet<>();
        private final Set<Voxel> voxels = new HashSet<>();
        private final Neighborhood borderNeigh = new EllipsoidalNeighborhood(1.5, true);
        private ImageInteger joinedMask;
        private KDTree<Double> localCurvatureMap;
        public InterfaceLocalShape(Region e1, Region e2) {
            super(e1, e2);
        }
        @Override public Collection<Voxel> getVoxels() {
            return voxels;
        }
        private ImageInteger getJoinedMask() {
            if (joinedMask==null) {
                // getJoinedMask of 2 objects
                ImageInteger m1 = e1.getMask();
                ImageInteger m2 = e2.getMask();
                BoundingBox joinBox = m1.getBoundingBox(); 
                joinBox.expand(m2.getBoundingBox());
                ImageByte mask = new ImageByte("joinedMask:"+e1.getLabel()+"+"+e2.getLabel(), joinBox.getImageProperties()).setCalibration(m1);
                ImageOperations.pasteImage(m1, mask, m1.getBoundingBox().translate(mask.getBoundingBox().reverseOffset()));
                ImageOperations.orWithOffset(m2, mask, mask);
                joinedMask = mask;
            }
            return joinedMask;
        }

        public KDTree<Double> getCurvatureMap() {
            //if (debug || ProcessingVariables.this.splitVerbose) logger.debug("interface: {}, contains curvature: {}", this, ProcessingVariables.this.curvatureMap.containsKey(e1));
            if (borderVoxels.isEmpty()) setBorderVoxels();
            if (curvaturePerCluster) return curvatureMap.get(e1);
            else {
                if (localCurvatureMap==null) {
                    localCurvatureMap = Curvature.computeCurvature(getJoinedMask(), curvatureScale);
                    if (testMode) ImageWindowManagerFactory.showImage(Curvature.getCurvatureMask(mask, localCurvatureMap));
                }
                return localCurvatureMap;
            }
        }

        @Override public void updateSortValue() {
            if (voxels.size()<=minInterfaceSize) curvatureValue=Double.NEGATIVE_INFINITY; // when border is too small curvature may not be computable, but objects should not be merged
            else if (getCurvatureMap()!=null) {
                curvatureValue = getMeanOfMinCurvature();
            } //else logger.debug("no curvature found for: {}", this);
            if (Double.isNaN(curvatureValue)) curvatureValue = Double.NEGATIVE_INFINITY; // curvature cannot be computed for objects too small
            //else logger.debug("curvature null");
        }
        @Override
        public void performFusion() {
            super.performFusion();
        }
        @Override 
        public void fusionInterface(InterfaceLocalShape otherInterface, Comparator<? super Region> elementComparator) {
            if (otherInterface.maxDistance>maxDistance) {
                this.maxDistance=otherInterface.maxDistance;
                this.maxVoxel=otherInterface.maxVoxel;
            }
            joinedMask=null;
            localCurvatureMap=null;
            voxels.addAll(otherInterface.voxels);
            setBorderVoxels();
        }

        @Override
        public boolean checkFusion() {
            if (maxVoxel==null) return false;
            if (this.voxels.isEmpty()) return false;
            // criterion on size
            if ((this.e1.getSize()<minSizeFusion && (Double.isNaN(yLimLastObject) || e1.getBounds().getyMax()<yLimLastObject)) || (this.e2.getSize()<minSizeFusion&& (Double.isNaN(yLimLastObject) || e2.getBounds().getyMax()<yLimLastObject))) return true; // fusion of small objects, except for last objects

            // criterion on curvature
            // curvature has been computed @ upadateSortValue
            if (debug| splitVerbose) logger.debug("interface: {}+{}, Mean curvature: {} ({} & {}), Threshold: {} & {}", e1.getLabel(), e2.getLabel(), curvatureValue, curvL, curvR, curvatureThreshold, curvatureThreshold2);
            if (curvatureValue<curvatureThreshold || (curvL<curvatureThreshold2 && curvR<curvatureThreshold2)) return false;
            //else if (true) return true;
            double max1 = Double.NEGATIVE_INFINITY;
            double max2 = Double.NEGATIVE_INFINITY;
            for (Voxel v : e1.getVoxels()) if (v.value>max1 && v.getDistance(maxVoxel, e1.getScaleXY(), e1.getScaleZ())<relativeThicknessMaxDistance) max1 = v.value;
            for (Voxel v : e2.getVoxels()) if (v.value>max2 && v.getDistance(maxVoxel, e1.getScaleXY(), e1.getScaleZ())<relativeThicknessMaxDistance) max2 = v.value;

            double norm = Math.min(max1, max2);
            value = maxDistance/norm;
            if (debug| splitVerbose) logger.debug("Thickness criterioninterface: {}+{}, norm: {} maxInter: {}, criterion value: {} threshold: {} fusion: {}, scale: {}", e1.getLabel(), e2.getLabel(), norm, maxDistance,value, relativeThicknessThreshold, value>relativeThicknessThreshold, e1.getScaleXY() );
            return  value>relativeThicknessThreshold;
        }

        private double getMinCurvature(Collection<Voxel> voxels) { // returns negative infinity if no border
            if (voxels.isEmpty()) return Double.NEGATIVE_INFINITY;
            //RadiusNeighborSearchOnKDTree<Double> search = new RadiusNeighborSearchOnKDTree(getCurvature());
            NearestNeighborSearchOnKDTree<Double> search = new NearestNeighborSearchOnKDTree(getCurvatureMap());

            double min = Double.POSITIVE_INFINITY;
            for (Voxel v : voxels) {
                search.search(new Point(new int[]{v.x, v.y}));
                double d = search.getSampler().get();
                if (d<min) min = d;
            }

            /*double searchScale = curvatureSearchScale;
            double searchScaleLim = 2 * curvatureSearchScale;
            while(Double.isInfinite(min) && searchScale<searchScaleLim) { // curvature is smoothed thus when there are angles the neerest value might be far away. progressively increment search scale in order not to reach the other side too easily
                for(Voxel v : voxels) {

                    search.search(new Point(new int[]{v.x, v.y}), searchScale, true);
                    if (search.numNeighbors()>=1) min=search.getSampler(0).get();
                    //for (int i = 0; i<search.numNeighbors(); ++i) {
                    //    Double d = search.getSampler(i).get();
                    //    if (min>d) min = d;
                    //}
                }
                ++searchScale;
            }*/
            if (Double.isInfinite(min)) return Double.NEGATIVE_INFINITY;
            return min;
        }
        public double getMeanOfMinCurvature() {
            curvL=Double.NaN;
            curvR=Double.NaN;
            if (borderVoxels.isEmpty() && borderVoxels2.isEmpty()) {
                if (debug| splitVerbose) logger.debug("{} : NO BORDER VOXELS");
                if (voxels.isEmpty()) return Double.NEGATIVE_INFINITY;
                else return Double.POSITIVE_INFINITY;
            }
            else {    
                if (borderVoxels2.isEmpty()) {
                    if (debug| splitVerbose) logger.debug("{}, GET CURV: {}, borderVoxels: {}", this, getMinCurvature(borderVoxels), borderVoxels.size());
                    return getMinCurvature(borderVoxels);
                }
                else {
                    //logger.debug("mean of min: b1: {}, b2: {}", getMinCurvature(borderVoxels), getMinCurvature(borderVoxels2));
                    //return 0.5 * (getMinCurvature(borderVoxels)+ getMinCurvature(borderVoxels2));
                    double min1 = getMinCurvature(borderVoxels);
                    double min2 = getMinCurvature(borderVoxels2);
                    this.curvL=min1;
                    this.curvR=min2;
                    double res;
                    if ((Math.abs(min1-min2)>2*Math.abs(curvatureThreshold))) { // when one side has a curvature very different from the other -> hole -> do not take into acount // TODO: check generality of criterion. put parameter? 
                        res = Math.max(min1, min2);
                    } else res = 0.5 * (min1 + min2); 
                    if (debug | splitVerbose) logger.debug("{}, GET CURV: {}&{} -> {} , borderVoxels: {}&{}", this, min1, min2, res, borderVoxels.size(), borderVoxels2.size());
                    return res;
                }
            }
        }

        @Override
        public void addPair(Voxel v1, Voxel v2) {
            addVoxel(getEDM(), v1);
            addVoxel(getEDM(), v2);
        }
        private void addVoxel(Image image, Voxel v) {
            double pixVal =image.getPixel(v.x, v.y, v.z);
            if (pixVal>maxDistance) {
                maxDistance = pixVal;
                maxVoxel = v;
            }
            voxels.add(v);
            //v.value=(float)pixVal;
        }
        private void setBorderVoxels() {
            borderVoxels.clear();
            borderVoxels2.clear();
            if (voxels.isEmpty()) return;
            // add border voxel
            ImageInteger mask = getJoinedMask();
            Set<Voxel> allBorderVoxels = new HashSet<>();
            for (Voxel v : voxels) if (borderNeigh.hasNullValue(v.x-mask.getOffsetX(), v.y-mask.getOffsetY(), v.z-mask.getOffsetZ(), mask, true)) allBorderVoxels.add(v);
            if ((debug| splitVerbose) && allBorderVoxels.isEmpty()) ImageWindowManagerFactory.showImage(mask.duplicate("joindedMask "+this));
            //logger.debug("all border voxels: {}", allBorderVoxels.size());
            populateBoderVoxel(allBorderVoxels);
        }

        private void populateBoderVoxel(Collection<Voxel> allBorderVoxels) {
            if (allBorderVoxels.isEmpty()) return;

            BoundingBox b = new BoundingBox();
            for (Voxel v : allBorderVoxels) b.expand(v);
            ImageByte mask = new ImageByte("", b.getImageProperties());
            for (Voxel v : allBorderVoxels) mask.setPixelWithOffset(v.x, v.y, v.z, 1);
            RegionPopulation pop = new RegionPopulation(mask, false);
            pop.translate(b, false);
            List<Region> l = pop.getObjects();
            if (l.isEmpty()) logger.error("interface: {}, no side found", this);
            else if (l.size()>=1) { // case of small borders -> only one distinct side
                borderVoxels.addAll(l.get(0).getVoxels());   
                if (l.size()==2) borderVoxels2.addAll(l.get(1).getVoxels());} 
            else {
                if (debug) logger.error("interface: {}, #{} sides found!!", this, l.size());
            }
        }

        @Override public int compareTo(InterfaceLocalShape t) { // decreasingOrder of curvature value
            if (compareMethod!=null) return compareMethod.apply(this, t);
            //return Double.compare(t.maxDistance, maxDistance);
            int c = Double.compare(t.curvatureValue, curvatureValue);
            if (c==0) return super.compareElements(t, RegionCluster.regionComparator); // consitency with equals method
            else return c;
        }

        @Override
        public String toString() {
            return "Interface: " + e1.getLabel()+"+"+e2.getLabel()+ " sortValue: "+curvatureValue;
        }
    }
    
    public final static  BiFunction<? super InterfaceLocalShape, ? super InterfaceLocalShape, Integer> compareBySize = (i1, i2) -> { // compare by cluster size
            int[] maxMin1 = i1.getE1().getSize()>i1.getE2().getSize() ? new int[]{i1.getE1().getSize(), i1.getE2().getSize()} : new int[]{i1.getE2().getSize(), i1.getE1().getSize()};
            int[] maxMin2 = i2.getE1().getSize()>i2.getE2().getSize() ? new int[]{i2.getE1().getSize(), i2.getE2().getSize()} : new int[]{i2.getE2().getSize(), i2.getE1().getSize()};
            int c = Integer.compare(maxMin1[0], maxMin2[0]);
            if (c!=0) return c;
            return Integer.compare(maxMin1[1], maxMin2[1]);
        };
}
