/*
 * Copyright (C) 2015 jollion
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
package processing;

import boa.gui.imageInteraction.IJImageDisplayer;
import static core.Processor.logger;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.Voxel;
import image.BlankMask;
import image.Image;
import image.ImageByte;
import image.ImageInteger;
import image.ImageLabeller;
import image.ImageMask;
import image.ImageOperations;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import processing.neighborhood.EllipsoidalNeighborhood;
import processing.neighborhood.Neighborhood;

/**
 *
 * @author jollion
 */
public class WatershedTransform {
    final protected TreeSet<Voxel> heap;
    final protected Spot[] spots; // map label -> spot (spots[0]==null)
    protected int spotNumber;
    final protected Image watershedMap;
    protected Image priorityMap;
    final protected ImageInteger segmentedMap;
    final protected ImageMask mask;
    final boolean is3D;
    public final boolean decreasingPropagation;
    protected boolean lowConnectivity = false;
    PropagationCriterion propagationCriterion;
    FusionCriterion fusionCriterion;
    public static List<Object3D> duplicateSeeds(List<Object3D> seeds) {
        List<Object3D> res = new ArrayList<>(seeds.size());
        for (Object3D o : seeds) res.add(new Object3D(new ArrayList<Voxel>(o.getVoxels()), o.getLabel(), o.is2D(), o.getScaleXY(), o.getScaleZ()));
        return res;
    }
    public static List<Object3D> createSeeds(List<Voxel> seeds, boolean is2D, float scaleXY, float scaleZ) {
        List<Object3D> res = new ArrayList<>(seeds.size());
        int label = 1;
        for (Voxel v : seeds) res.add(new Object3D(new ArrayList<Voxel>(){{add(v);}}, label++, is2D, scaleXY, scaleZ));
        return res;
    }
    public static ObjectPopulation watershed(Image watershedMap, ImageMask mask, boolean decreasingPropagation, PropagationCriterion propagationCriterion, FusionCriterion fusionCriterion, boolean lowConnectivity) {
        ImageByte seeds = Filters.localExtrema(watershedMap, null, decreasingPropagation, Filters.getNeighborhood(1.5, 1.5, watershedMap));
        if (mask!=null) ImageOperations.and(seeds, mask, seeds); // no offset
        //new IJImageDisplayer().showImage(seeds.setName("seeds"));
        return watershed(watershedMap, mask, ImageLabeller.labelImageList(seeds), decreasingPropagation, propagationCriterion,fusionCriterion, lowConnectivity);
    }
    /**
     * 
     * @param watershedMap
     * @param mask
     * @param regionalExtrema CONTAINED OBJECT3D WILL BE MODIFIED
     * @param decreasingPropagation
     * @param propagationCriterion
     * @param fusionCriterion
     * @return 
     */
    public static ObjectPopulation watershed(Image watershedMap, ImageMask mask, List<Object3D> regionalExtrema, boolean decreasingPropagation, PropagationCriterion propagationCriterion, FusionCriterion fusionCriterion, boolean lowConnectivity) {
        WatershedTransform wt = new WatershedTransform(watershedMap, mask, regionalExtrema, decreasingPropagation, propagationCriterion, fusionCriterion).setLowConnectivity(lowConnectivity);
        wt.run();
        return wt.getObjectPopulation();
    }
    
    public static ObjectPopulation watershed(Image watershedMap, ImageMask mask, ImageMask seeds, boolean invertWatershedMapValues, boolean lowConnectivity) {
        return watershed(watershedMap, mask, Arrays.asList(ImageLabeller.labelImage(seeds)), invertWatershedMapValues, null, null, lowConnectivity);
    }
    public static ObjectPopulation watershed(Image watershedMap, ImageMask mask, ImageMask seeds, boolean decreasingPropagation, PropagationCriterion propagationCriterion, FusionCriterion fusionCriterion, boolean lowConnectivity) {
        return watershed(watershedMap, mask, Arrays.asList(ImageLabeller.labelImage(seeds)), decreasingPropagation, propagationCriterion, fusionCriterion, lowConnectivity);
    }
    /**
     * 
     * @param watershedMap
     * @param mask
     * @param regionalExtrema CONTAINED OBJECT3D WILL BE MODIFIED
     * @param decreasingPropagation
     * @param propagationCriterion
     * @param fusionCriterion 
     */
    public WatershedTransform(Image watershedMap, ImageMask mask, List<Object3D> regionalExtrema, boolean decreasingPropagation, PropagationCriterion propagationCriterion, FusionCriterion fusionCriterion) {
        if (mask==null) mask=new BlankMask("", watershedMap);
        this.decreasingPropagation = decreasingPropagation;
        heap = decreasingPropagation ? new TreeSet<>(Voxel.getInvertedComparator()) : new TreeSet<>();
        //heap = decreasingPropagation ? new PriorityQueue<>(Voxel.getInvertedComparator()) : new PriorityQueue<>();
        this.mask=mask;
        this.watershedMap=watershedMap;
        spots = new Spot[regionalExtrema.size()+1];
        spotNumber=regionalExtrema.size();
        segmentedMap = ImageInteger.createEmptyLabelImage("segmentationMap", spots.length, watershedMap);
        for (int i = 0; i<regionalExtrema.size(); ++i) spots[i+1] = new Spot(i+1, regionalExtrema.get(i).getVoxels()); // do modify seed objects
        logger.trace("watershed transform: number of seeds: {} segmented map type: {}", regionalExtrema.size(), segmentedMap.getClass().getSimpleName());
        is3D=watershedMap.getSizeZ()>1;   
        if (propagationCriterion==null) setPropagationCriterion(new DefaultPropagationCriterion());
        else setPropagationCriterion(propagationCriterion);
        if (fusionCriterion==null) setFusionCriterion(new DefaultFusionCriterion());
        else setFusionCriterion(fusionCriterion);
    }
    public WatershedTransform setLowConnectivity(boolean lowConnectivity) {
        this.lowConnectivity = lowConnectivity;
        return this;
    }
    public WatershedTransform setFusionCriterion(FusionCriterion fusionCriterion) {
        this.fusionCriterion=fusionCriterion;
        fusionCriterion.setUp(this);
        return this;
    }
    
    public WatershedTransform setPropagationCriterion(PropagationCriterion propagationCriterion) {
        this.propagationCriterion=propagationCriterion;
        propagationCriterion.setUp(this);
        return this;
    }
    public WatershedTransform setPriorityMap(Image priorityMap) {
        if (!watershedMap.sameSize(priorityMap)) throw new IllegalArgumentException("PriorityMap should have same dimensions as watershed map");
        this.priorityMap=priorityMap;
        return this;
    }
    
    public void run() {
        double rad = lowConnectivity ? 1 : 1.5;
        EllipsoidalNeighborhood neigh = watershedMap.getSizeZ()>1?new EllipsoidalNeighborhood(rad, rad, true) : new EllipsoidalNeighborhood(rad, true);
        
        for (Spot s : spots) {
            if (s!=null) {
                for (Voxel v : s.voxels) {
                    for (int i = 0; i<neigh.getSize(); ++i) {
                        Voxel n = new Voxel(v.x+neigh.dx[i], v.y+neigh.dy[i], v.z+neigh.dz[i]) ;
                        if (segmentedMap.contains(n.x, n.y, n.z) && mask.insideMask(n.x, n.y, n.z)) heap.add(n);
                    }
                }
            }
        }
        Score score = generateScore();
        List<Voxel> nextProp  = new ArrayList<>(neigh.getSize());
        Set<Integer> surroundingLabels = fusionCriterion==null || fusionCriterion instanceof DefaultFusionCriterion ? null : new HashSet<>(neigh.getSize());
        while (!heap.isEmpty()) {
            //Voxel v = heap.poll();
            Voxel v = heap.pollFirst();
            if (segmentedMap.getPixelInt(v.x, v.y, v.z)>0) continue;
            score.setUp(v);
            for (int i = 0; i<neigh.getSize(); ++i) {
                Voxel n = new Voxel(v.x+neigh.dx[i], v.y+neigh.dy[i], v.z+neigh.dz[i]) ;
                if (segmentedMap.contains(n.x, n.y, n.z) && mask.insideMask(n.x, n.y, n.z)) {
                    int nextLabel = segmentedMap.getPixelInt(n.x, n.y, n.z);
                    if (nextLabel>0) {
                        if (surroundingLabels!=null) surroundingLabels.add(nextLabel);
                        score.add(n, nextLabel);
                    } else {
                        n.value = watershedMap.getPixel(n.x, n.y, n.z);
                        nextProp.add(n);
                    }
                }
            }
            int currentLabel = score.getLabel();
            spots[currentLabel].addVox(v);
            // check propagation criterion
            nextProp.removeIf(n->!propagationCriterion.continuePropagation(v, n));
            heap.addAll(nextProp);
            nextProp.clear();
            // check fusion criterion
            if (surroundingLabels!=null) {
                surroundingLabels.remove(currentLabel);
                if (!surroundingLabels.isEmpty()) {
                    Spot currentSpot = spots[currentLabel];
                    for (int otherLabel : surroundingLabels) {
                        if (fusionCriterion.checkFusionCriteria(currentSpot, spots[otherLabel], v)) {
                            currentSpot = currentSpot.fusion(spots[otherLabel]);
                        }
                    }
                    surroundingLabels.clear();
                }
            }
        }
    }
    private Score generateScore() {
        if (this.priorityMap!=null) {
            //return new MinDiffPriorityMap();
            return new MaxPriority();
            //return new MaxSeedPriority();
        } 
        return new MaxDiffWsMap();
    }
    private interface Score {
        public abstract void setUp(Voxel center);
        public abstract void add(Voxel v, int label);
        public abstract int getLabel();
    }
    private class MaxDiffWsMap implements Score {
        double centerV = 0;
        double curDiff = -Double.MAX_VALUE;
        int curLabel;
        @Override
        public void add(Voxel v, int label) {
            //double diff=!decreasingPropagation ? watershedMap.getPixel(v.x, v.y, v.z) : -watershedMap.getPixel(v.x, v.y, v.z);
            double diff = Math.abs(watershedMap.getPixel(v.x, v.y, v.z)-centerV);
            if (diff>curDiff) {
                curDiff=diff;
                curLabel = label;
            }
        }
        @Override
        public int getLabel() {
            return curLabel;
        }
        @Override
        public void setUp(Voxel center) {
            centerV = center.value;
            curDiff = -Double.MAX_VALUE; // reset
            curLabel=0;
        }
    }
    private class MinDiffPriorityMap implements Score {
        Voxel center;
        double centerP;
        double curDiff = Double.MAX_VALUE;;
        int curLabel;
        @Override
        public void add(Voxel v, int label) {
            double diff=Math.abs(priorityMap.getPixel(v.x, v.y, v.z)-centerP) ;
            if (diff<curDiff) {
                curDiff=diff;
                curLabel = label;
            }
        }
        @Override
        public int getLabel() {
            return curLabel;
        }
        @Override
        public void setUp(Voxel center) {
            centerP = priorityMap.getPixel(center.x, center.y, center.z);
            curDiff = Double.MAX_VALUE; // reset
            curLabel=0;
        }
    }
    private class MaxPriority implements Score {
        Voxel center;
        double priority = -Double.MAX_VALUE;;
        int curLabel;
        @Override
        public void add(Voxel v, int label) {
            double p = priorityMap.getPixel(v.x, v.y, v.z);
            if (p>priority) {
                priority = p;
                curLabel = label;
            }
        }
        @Override
        public int getLabel() {
            return curLabel;
        }
        @Override
        public void setUp(Voxel center) {
            this.center=center;
            priority = -Double.MAX_VALUE; // reset
            curLabel=0;
        }
    }
    
    
    public Image getWatershedMap() {
        return watershedMap;
    }
    
    public ImageInteger getLabelImage() {return segmentedMap;}
    
    public ObjectPopulation getObjectPopulation() {
        //int nb = 0;
        //for (Spot s : wt.spots) if (s!=null) nb++;
        ArrayList<Object3D> res = new ArrayList<Object3D>(spotNumber);
        int label = 1;
        for (Spot s : spots) if (s!=null) res.add(s.toObject3D(label++));
        return new ObjectPopulation(res, watershedMap).setConnectivity(lowConnectivity);
    }
    public Spot[] getSpotArray() {
        return spots;
    }
    public Collection<Voxel> getHeap() {
        return heap;
    }
    
    
    public class Spot {
        public List<Voxel> voxels;
        int label;
        float priorityValue;
        //Voxel seed;
        /*public Spot(int label, Voxel seed) {
            this.label=label;
            this.voxels=new ArrayList<Voxel>();
            voxels.add(seed);
            seed.value=watershedMap.getPixel(seed.x, seed.y, seed.getZ());
            heap.add(seed);
            this.seed=seed;
            segmentedMap.setPixel(seed.x, seed.y, seed.getZ(), label);
        }*/
        public Spot(int label, List<Voxel> voxels) {
            this.label=label;
            this.voxels=voxels;
            for (Voxel v : voxels) {
                v.value=watershedMap.getPixel(v.x, v.y, v.z);
                if (priorityMap!=null) {
                    //v = PrioriyVoxel.fromVoxel(v);
                    //((PrioriyVoxel)v).priority = priorityMap.getPixel(v.x, v.y, v.z);
                    float p = priorityMap.getPixel(v.x, v.y, v.z);
                    if (p>priorityValue) priorityValue = p;
                }
                heap.add(v);
                segmentedMap.setPixel(v.x, v.y, v.z, label);
            }
            //this.seed=seeds.get(0);
            //logger.debug("spot: {} seed size: {} seed {}",label, seeds.size(), seed);
            
        }
        
        public void setLabel(int label) {
            this.label=label;
            for (Voxel v : voxels) segmentedMap.setPixel(v.x, v.y, v.z, label);
        }

        public Spot fusion(Spot spot) {
            if (spot.label<label) return spot.fusion(this);
            spots[spot.label]=null;
            spotNumber--;
            spot.setLabel(label);
            this.voxels.addAll(spot.voxels); // pas besoin de check si voxels.contains(v) car les spots ne se recouvrent pas            //update seed: lowest seedIntensity
            //if (watershedMap.getPixel(seed.x, seed.y, seed.getZ())>watershedMap.getPixel(spot.seed.x, spot.seed.y, spot.seed.getZ())) seed=spot.seed;
            return this;
        }
        
        public void addVox(Voxel v) {
            if (!voxels.contains(v)) {
                voxels.add(v);
                segmentedMap.setPixel(v.x, v.y, v.z, label);
            }
        }
        
        public Object3D toObject3D(int label) {
            return new Object3D(voxels, label, segmentedMap.getSizeZ()==1, mask.getScaleXY(), mask.getScaleZ()).setQuality(getQuality());
        }
        
        public double getQuality() {
            if (decreasingPropagation) {
                double max = Double.NEGATIVE_INFINITY;
                for (Voxel v: voxels) if (v.value>max) max = v.value;
                return max;
            } else {
                double min = Double.POSITIVE_INFINITY;
                for (Voxel v: voxels) if (v.value<min) min = v.value;
                return -min;
            }
        }
        
    }
    public interface PropagationCriterion {
        public void setUp(WatershedTransform instance);
        public boolean continuePropagation(Voxel currentVox, Voxel nextVox);
    }
    public static class DefaultPropagationCriterion implements PropagationCriterion {
        @Override public boolean continuePropagation(Voxel currentVox, Voxel nextVox) {
            return true;
        }
        @Override public void setUp(WatershedTransform instance) {}
    }
    public static class MonotonalPropagation implements PropagationCriterion {
        boolean decreasingPropagation;
        @Override public void setUp(WatershedTransform instance) {
            setPropagationDirection(instance.decreasingPropagation);
        }
        public MonotonalPropagation setPropagationDirection(boolean decreasingPropagation) {
            this.decreasingPropagation=decreasingPropagation;
            return this;
        }
        @Override public boolean continuePropagation(Voxel currentVox, Voxel nextVox) {
            if (decreasingPropagation) return (nextVox.value<=currentVox.value);
            else return (nextVox.value>=currentVox.value);
        }
    }
    public static class ThresholdPropagation implements PropagationCriterion {
        Image image;
        double threshold;
        boolean stopWhenInferior;
        public ThresholdPropagation(Image image, double threshold, boolean stopWhenInferior) {
            this.image=image;
            this.threshold=threshold;
            this.stopWhenInferior=stopWhenInferior;
        }
        @Override public void setUp(WatershedTransform instance) {}
        @Override public boolean continuePropagation(Voxel currentVox, Voxel nextVox) {
            return stopWhenInferior?image.getPixel(nextVox.x, nextVox.y, nextVox.z)>threshold:image.getPixel(nextVox.x, nextVox.y, nextVox.z)<threshold;
        }
    }
    public static class ThresholdPropagationOnWatershedMap implements PropagationCriterion {
        boolean stopWhenInferior;
        double threshold;
        @Override public void setUp(WatershedTransform instance) {
            stopWhenInferior = instance.decreasingPropagation;
        }
        public ThresholdPropagationOnWatershedMap(double threshold) {
            this.threshold=threshold;
        }
        public boolean continuePropagation(Voxel currentVox, Voxel nextVox) {
            return stopWhenInferior?nextVox.value>threshold:nextVox.value<threshold;
        }
        
    }
    public static class MultiplePropagationCriteria implements PropagationCriterion {
        PropagationCriterion[] criteria;
        public MultiplePropagationCriteria(PropagationCriterion... criteria) {
            this.criteria=criteria;
        } 
        @Override public void setUp(WatershedTransform instance) {
            for (PropagationCriterion c : criteria) c.setUp(instance);
        }
        public boolean continuePropagation(Voxel currentVox, Voxel nextVox) {
            for (PropagationCriterion p : criteria) if (!p.continuePropagation(currentVox, nextVox)) return false;
            return true;
        }
    }
    
    public interface FusionCriterion {
        public void setUp(WatershedTransform instance);
        public boolean checkFusionCriteria(Spot s1, Spot s2, Voxel currentVoxel);
    }
    public static class DefaultFusionCriterion implements FusionCriterion {
        @Override public boolean checkFusionCriteria(Spot s1, Spot s2, Voxel currentVoxel) {
            return false;
        }
        @Override public void setUp(WatershedTransform instance) {}
    }
    public static class SizeFusionCriterion implements FusionCriterion {
        int minimumSize;
        public SizeFusionCriterion(int minimumSize) {
            this.minimumSize=minimumSize;
        }
        @Override public void setUp(WatershedTransform instance) {}
        @Override public boolean checkFusionCriteria(Spot s1, Spot s2, Voxel currentVoxel) {
            return s1.voxels.size()<minimumSize || s2.voxels.size()<minimumSize;
        }
    }
    public static class ThresholdFusionOnWatershedMap implements FusionCriterion {
        double threshold;
        WatershedTransform instance;
        public ThresholdFusionOnWatershedMap(double threshold) {
            this.threshold=threshold;
        }
        @Override public void setUp(WatershedTransform instance) {this.instance=instance;}
        @Override public boolean checkFusionCriteria(Spot s1, Spot s2, Voxel currentVoxel) {
            return instance.decreasingPropagation ? currentVoxel.value>threshold : currentVoxel.value<threshold;
        }
    }
    public static class NumberFusionCriterion implements FusionCriterion {
        int numberOfSpots;
        WatershedTransform instance;
        public NumberFusionCriterion(int minNumberOfSpots) {
            this.numberOfSpots=minNumberOfSpots;
        }
        @Override public void setUp(WatershedTransform instance) {this.instance=instance;}
        @Override public boolean checkFusionCriteria(Spot s1, Spot s2, Voxel currentVoxel) {
            return instance.spotNumber>numberOfSpots;
        }
    }
    public static class MultipleFusionCriteriaAnd implements FusionCriterion {
        FusionCriterion[] criteria;
        public MultipleFusionCriteriaAnd(FusionCriterion... criteria) {
            this.criteria=criteria;
        } 
        @Override public void setUp(WatershedTransform instance) {
            for (FusionCriterion f : criteria) f.setUp(instance);
        }
        @Override public boolean checkFusionCriteria(Spot s1, Spot s2, Voxel currentVoxel) {
            if (criteria.length==0) return false;
            for (FusionCriterion c : criteria) if (!c.checkFusionCriteria(s1, s2, currentVoxel)) return false;
            return true;
        }
    }
    public static class MultipleFusionCriteriaOr implements FusionCriterion {
        FusionCriterion[] criteria;
        public MultipleFusionCriteriaOr(FusionCriterion... criteria) {
            this.criteria=criteria;
        } 
        @Override public void setUp(WatershedTransform instance) {
            for (FusionCriterion f : criteria) f.setUp(instance);
        }
        @Override public boolean checkFusionCriteria(Spot s1, Spot s2, Voxel currentVoxel) {
            if (criteria.length==0) return false;
            for (FusionCriterion c : criteria) if (c.checkFusionCriteria(s1, s2, currentVoxel)) return true;
            return false;
        }
    }
    
    /*public static class PrioriyVoxel extends Voxel {
        float priority;
        public PrioriyVoxel(int x, int y, int z) {
            super(x, y, z);
        }
        public static PrioriyVoxel fromVoxel(Voxel v) {
            PrioriyVoxel res = new PrioriyVoxel(v.x, v.y, v.z);
            res.value=v.value;
            return res;
        }
        @Override
        public int compareTo(Voxel other) {
            if (value < other.value) {
                return -1;
            } else if (value > other.value) {
                return 1;
            } 
            if (other instanceof PrioriyVoxel) { // inverted order: larger value has priority
                PrioriyVoxel otherV = (PrioriyVoxel)other;
                if (priority>otherV.priority) return -1;
                else if (priority<otherV.priority) return 1;
            }
            // consistancy with equals method    
            if (x<other.x) return -1;
            else if (x>other.x) return 1;
            else if (y<other.y) return -1;
            else if (y>other.y) return 1;
            else if (z<other.z) return -1;
            else if (z>other.z) return 1;
            else return 0;
        }
    }*/
}
