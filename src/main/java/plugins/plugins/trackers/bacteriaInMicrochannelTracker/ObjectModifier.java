/*
 * Copyright (C) 2016 jollion
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
package plugins.plugins.trackers.bacteriaInMicrochannelTracker;

import dataStructure.objects.Object3D;
import dataStructure.objects.Voxel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import plugins.plugins.segmenters.BacteriaTrans;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.logger;
import utils.HashMapGetCreate;
import utils.Pair;

/**
 *
 * @author jollion
 */
public abstract class ObjectModifier extends CorrectionScenario {
    protected final Map<Object3D, Split> splitMap = new HashMap<>();
    protected final Map<Pair<Object3D, Object3D>, Merge> mergeMap = new HashMap<>();
    protected final Map<Integer, List<Object3D>> objects = new HashMap<>();
    public ObjectModifier(int frameMin, int frameMax, BacteriaClosedMicrochannelTrackerLocalCorrections tracker) {
        super(frameMin, frameMax, tracker);
    }
    public List<Object3D> getObjects(int frame) {
        return objects.get(frame);
    }
    
    protected Split getSplit(int frame, Object3D o) {
        Split res = splitMap.get(o);
        if (res==null) {
            res = new Split(frame, o);
            splitMap.put(o, res);
            Pair<Object3D, Object3D> pair = res.pairValue();
            mergeMap.put(pair, new Merge(frame, pair, res.source, -res.cost));
        }
        return res;
    }
    protected Merge getMerge(int frame, Pair<Object3D, Object3D> o) {
        Merge res = mergeMap.get(o);
        if (res==null) {
            res = new Merge(frame, o);
            mergeMap.put(o, res);
            splitMap.put(res.value, new Split(frame, res.value, res.listSource(), -res.cost));
        }
        return res;
    }
    protected abstract class Correction implements Comparable<Correction> {
        final int frame;
        double cost = Double.NaN;
        Correction(int frame) {this.frame=frame;}
        protected double getCost() {return cost;}
        protected abstract void apply(List<Object3D> list);
        @Override public int compareTo(Correction other) {
            return Double.compare(cost, other.cost);
        }
    }
    protected class Split extends Correction {
        List<Object3D> values;
        final Object3D source;

        protected Split(int frame, Object3D source) {
            super(frame);
            this.source=source;
            values = new ArrayList(2);
            cost = tracker.getSegmenter(frame, false).split(tracker.getImage(frame), source, values);
            if (Double.isInfinite(cost) || Double.isNaN(cost) || values.size()!=2) {
                cost = Double.POSITIVE_INFINITY;
                values.clear();
            }
        }
        protected Split(int frame, Object3D source, List<Object3D> values, double cost) {
            super(frame);
            this.source=source;
            this.values = values;
            this.cost=cost;
        }

        @Override
        protected void apply(List<Object3D> currentObjects) {
            int i = currentObjects.indexOf(source);
            if (i<0) throw new Error("add split "+(frame)+" object not found");
            currentObjects.set(i, values.get(0));
            currentObjects.add(i+1, values.get(1)); 
        }
        
        public Pair<Object3D, Object3D> pairValue() {
            if (values.size()!=2) return new Pair(null, null);
            return new Pair(values.get(0), values.get(1));
        }
    }
    protected class Merge extends Correction {
        final Pair<Object3D, Object3D> source; 
        final Object3D value;

        public Merge(int frame, Pair<Object3D, Object3D> source) {
            super(frame);
            this.source = source;
            cost = tracker.getSegmenter(frame, false).computeMergeCost(tracker.getImage(frame), listSource());
            List<Voxel> vox = new ArrayList(source.key.getVoxels().size()+source.value.getVoxels().size());
            vox.addAll(source.key.getVoxels()); vox.addAll(source.value.getVoxels());
            value =new Object3D(vox, source.key.getLabel(), source.key.getScaleXY(), source.key.getScaleZ());
        }
        public Merge(int frame, Pair<Object3D, Object3D> source, Object3D value, double cost) {
            super(frame);
            this.source=source;
            this.value= value;
            this.cost=cost;
        }
        private List<Object3D> listSource() {
            return new ArrayList<Object3D>(2){{add(source.key);add(source.value);}};
        }

        @Override
        protected void apply(List<Object3D> currentObjects) {
            int i = currentObjects.indexOf(source.key);
            int i2 = currentObjects.indexOf(source.value);
            if (i<0) throw new Error("ObjectModifier Error: frame:"+(frame)+" object 1 not found");
            if (i2<0) throw new Error("ObjectModifier Error: frame:"+(frame)+" object 2 not found");
            currentObjects.set(Math.min(i, i2), value);
            currentObjects.remove(Math.max(i, i2));
        }
    }
}
