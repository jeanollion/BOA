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
import image.ImageInteger;
import image.ImageLabeller;
import image.ImageMask;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
    final protected Image watershedMap;
    final protected ImageInteger segmentedMap;
    final protected ImageMask mask;
    final boolean is3D;
    
    public static ObjectPopulation watershed(Image watershedMap, ImageMask mask, Object3D[] regionalExtrema, boolean invertWatershedMapValues) {
        WatershedTransform wt = new WatershedTransform(watershedMap, mask, regionalExtrema, invertWatershedMapValues);
        wt.run();
        //new IJImageDisplayer().showImage(wt.segmentedMap);
        
        int nb = 0;
        for (Spot s : wt.spots) if (s!=null) nb++;
        ArrayList<Object3D> res = new ArrayList<Object3D>(nb);
        int label = 1;
        for (Spot s : wt.spots) if (s!=null) res.add(s.toObject3D(label++));
        return new ObjectPopulation(res, watershedMap);
    }
    
    public static ObjectPopulation watershed(Image watershedMap, ImageMask mask, ImageMask seeds, boolean invertWatershedMapValues) {
        return watershed(watershedMap, mask, ImageLabeller.labelImage(seeds), invertWatershedMapValues);
    }
    
    protected WatershedTransform(Image watershedMap, ImageMask mask, Object3D[] regionalExtrema, boolean invertWatershedMapValues) {
        if (mask==null) mask=new BlankMask("", watershedMap);
        heap = invertWatershedMapValues ? new TreeSet<Voxel>(Voxel.getInvertedComparator()) : new TreeSet<Voxel>();
        this.mask=mask;
        this.watershedMap=watershedMap;
        spots = new Spot[regionalExtrema.length+1];
        segmentedMap = ImageInteger.createEmptyLabelImage("segmentationMap", spots.length, watershedMap);
        for (int i = 0; i<regionalExtrema.length; ++i) spots[i+1] = new Spot(i+1, regionalExtrema[i].getVoxels());
        logger.trace("watershed transform: number of seeds: {} segmented map type: {}", regionalExtrema.length, segmentedMap.getClass().getSimpleName());
        is3D=watershedMap.getSizeZ()>1;    
    }
    
    
    
    protected void run() {
        for (Spot s : spots) {
            if (s!=null) for (Voxel v : s.voxels) heap.add(v);
        }
        while (!heap.isEmpty()) {
            Voxel v = heap.pollFirst();
            Spot currentSpot = spots[segmentedMap.getPixelInt(v.x, v.y, v.z)];
            EllipsoidalNeighborhood neigh = watershedMap.getSizeZ()>1?new EllipsoidalNeighborhood(1.5, 1.5, true) : new EllipsoidalNeighborhood(1.5, true);
            Voxel next;
            for (int i = 0; i<neigh.getSize(); ++i) {
                next = new Voxel(v.x+neigh.dx[i], v.y+neigh.dy[i], v.z+neigh.dz[i]);
                //logger.trace("voxel: {} next: {}, mask contains: {}, insideMask: {}",v, next, mask.contains(next.x, next.y, next.getZ()) , mask.insideMask(next.x, next.y, next.getZ()));
                if (mask.contains(next.x, next.y, next.z) && mask.insideMask(next.x, next.y, next.z)) propagate(currentSpot,v, next);
            }
        }
    }
    
    protected Spot propagate(Spot currentSpot, Voxel currentVoxel, Voxel nextVox) { /// nextVox.value = 0 at this step
        int label = segmentedMap.getPixelInt(nextVox.x, nextVox.y, nextVox.z);
        if (label!=0) {
            if (label!=currentSpot.label) {
                Spot s2 = spots[label];
                if (checkFusionCriteria(currentSpot, s2, currentVoxel)) return currentSpot.fusion(s2);
                else heap.remove(nextVox); // FIXME ??et dans les autres directions?
            }
        } else if (continuePropagation(currentVoxel, nextVox)) {
            nextVox.value=watershedMap.getPixel(nextVox.x, nextVox.y, nextVox.z);
            currentSpot.addVox(nextVox);
            heap.add(nextVox);
        }
        return currentSpot;
    }
    
    public boolean checkFusionCriteria(Spot s1, Spot s2, Voxel currentVoxel) {
        return false;
    }
    
    protected boolean continuePropagation(Voxel currentVox, Voxel nextVox) {
        return true;
    }
    
    protected class Spot {
        public ArrayList<Voxel> voxels;
        int label;
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
        public Spot(int label, ArrayList<Voxel> voxels) {
            this.label=label;
            this.voxels=voxels;
            for (Voxel v :voxels) {
                v.value=watershedMap.getPixel(v.x, v.y, v.z);
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
            return new Object3D(voxels, label, watershedMap.getScaleXY(), watershedMap.getScaleZ());
        }
        
    }
}
