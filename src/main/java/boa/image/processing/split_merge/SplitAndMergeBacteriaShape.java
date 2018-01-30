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
import boa.image.processing.ImageOperations;
import boa.image.processing.clustering.ClusterCollection;
import boa.image.processing.clustering.InterfaceRegionImpl;
import boa.image.processing.clustering.RegionCluster;
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;
import boa.image.processing.neighborhood.Neighborhood;
import boa.image.processing.split_merge.SplitAndMergeBacteriaShape.InterfaceBT;
import static boa.plugins.Plugin.logger;
import static boa.plugins.plugins.segmenters.BacteriaTrans.debug;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.imglib2.KDTree;
import net.imglib2.Point;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;

/**
 *
 * @author jollion
 */
public class SplitAndMergeBacteriaShape extends SplitAndMerge<InterfaceBT> {
    public boolean splitVerbose=false;
    @Override
    public Image getWatershedMap() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected ClusterCollection.InterfaceFactory<Region, InterfaceBT> createFactory() {
        return (Region e1, Region e2, Comparator<? super Region> elementComparator) -> new InterfaceBT(e1, e2);
    }
    
    
    public class InterfaceBT extends InterfaceRegionImpl<InterfaceBT> implements RegionCluster.InterfaceVoxels<InterfaceBT> {
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
        public InterfaceBT(Region e1, Region e2) {
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

        public KDTree<Double> getCurvature() {
            //if (debug || ProcessingVariables.this.splitVerbose) logger.debug("interface: {}, contains curvature: {}", this, ProcessingVariables.this.curvatureMap.containsKey(e1));
            if (borderVoxels.isEmpty()) setBorderVoxels();
            return ProcessingVariables.this.curvatureMap.get(e1);
        }

        @Override public void updateSortValue() {
            if (voxels.size()<=-1) curvatureValue=Double.NEGATIVE_INFINITY; // when border is too small curvature may not be computable, but objects should not be merged
            else if (getCurvature()!=null) {
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
        public void fusionInterface(InterfaceBT otherInterface, Comparator<? super Region> elementComparator) {
            if (otherInterface.maxDistance>maxDistance) {
                this.maxDistance=otherInterface.maxDistance;
                this.maxVoxel=otherInterface.maxVoxel;
            }
            joinedMask=null;
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
            NearestNeighborSearchOnKDTree<Double> search = new NearestNeighborSearchOnKDTree(getCurvature());

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
                if (debug) logger.error("interface: {}, #{} sides found!!, {}", this, l.size(), input.getName());
            }
        }

        @Override public int compareTo(InterfaceBT t) { // decreasingOrder of curvature value
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
}
