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
package plugins.plugins.trackers;

import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.ParameterUtils;
import configuration.parameters.PluginParameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectTracker;
import image.BlankMask;
import image.BoundingBox;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import plugins.Plugin;
import static plugins.Plugin.logger;
import plugins.Segmenter;
import plugins.TrackerSegmenter;
import plugins.plugins.segmenters.MicroChannelFluo2D;
import static plugins.plugins.trackers.ObjectIdxTracker.getComparator;
import static plugins.plugins.trackers.ObjectIdxTracker.getComparatorObject3D;
import static plugins.plugins.transformations.CropMicroChannelFluo2D.getBoundingBox;

/**
 *
 * @author jollion
 */
public class MicrochannelProcessor implements TrackerSegmenter {
    protected PluginParameter<Segmenter> segmenter = new PluginParameter<Segmenter>("Segmentation algorithm", Segmenter.class, new MicroChannelFluo2D(), false);
    NumberParameter number = new BoundedNumberParameter("Number of TimePoints", 0, 5, 1, null);
    Parameter[] parameters = new Parameter[]{segmenter, number};
    public static boolean debug;
    public MicrochannelProcessor(){
    }
    
    public MicrochannelProcessor(Segmenter segmenter){
        this.segmenter.setPlugin(segmenter);
    }
    
    public MicrochannelProcessor setTimePointNumber(int timePointNumber){
        this.number.setValue(timePointNumber);
        return this;
    }
    
    public void segmentAndTrack(int structureIdx, List<StructureObject> parentTrack) {
        if (debug) MicroChannelFluo2D.debug=true;
        int refTimePoint = 50;
        Segmenter segAlgo = segmenter.instanciatePlugin();
        if (segAlgo==null) throw new Error("Segmentation algorithm not found");
        ObjectPopulation pop=null;
        int numb = Math.min(number.getValue().intValue(), parentTrack.size()-2);
        double delta = (double)parentTrack.size() / (double)(numb+2);
        if (debug) logger.debug("n: {}, track: {} delta: {}", numb, parentTrack.size(), delta);
        if (numb>1) {
            for (int i = 1; i<=numb; ++i) {
                int idx = (int) (i * delta);
                StructureObject parent = parentTrack.get(idx);
                ObjectPopulation popTemp = segAlgo.runSegmenter(parent.getRawImage(structureIdx), structureIdx, parent);
                if (pop==null) pop = popTemp;
                else pop = combine(pop, popTemp);
                if (debug) logger.debug("time: {}, object number: {}",idx,  pop.getObjects().size());
            }
        } else {
            StructureObject ref = getRefTimePoint(refTimePoint, parentTrack);
            pop = segAlgo.runSegmenter(ref.getRawImage(structureIdx), structureIdx, ref);
        }
        
        
        StructureObject prev=null;
        for (StructureObject s : parentTrack) {
            s.setChildrenObjects(pop, structureIdx);
            if (prev!=null) assignPrevious(prev.getChildObjects(structureIdx), s.getChildObjects(structureIdx));
            prev=s;
        }
    }

    public void track(int structureIdx, List<StructureObject> parentTrack) {
        if (parentTrack.isEmpty()) return;
        ArrayList<StructureObject> previousChildren = new ArrayList<StructureObject>(parentTrack.get(0).getChildren(structureIdx));
        Collections.sort(previousChildren, getComparator(ObjectIdxTracker.IndexingOrder.XYZ));
        for (int i = 1; i<parentTrack.size(); ++i) {
            ArrayList<StructureObject> currentChildren = new ArrayList<StructureObject>(parentTrack.get(i).getChildren(structureIdx));
            Collections.sort(currentChildren, getComparator(ObjectIdxTracker.IndexingOrder.XYZ));
            assignPrevious(previousChildren, currentChildren);
            previousChildren = currentChildren;
        }
    }
    
    public void assignPrevious(ArrayList<? extends StructureObjectTracker> previous, ArrayList<? extends StructureObjectTracker> next) {
        int lim = Math.min(previous.size(), next.size());
        for (int i = 0; i<Math.min(previous.size(), next.size()); ++i) {
            next.get(i).setPreviousInTrack(previous.get(i), false);
            Plugin.logger.trace("assign previous {} to next {}", previous.get(i), next.get(i));
        }
        for (int i = lim; i<next.size(); ++i) next.get(i).resetTrackLinks();
    }
    
    private static StructureObject getRefTimePoint(int refTimePoint, List<StructureObject> track) {
        if (track.get(0).getTimePoint()>=refTimePoint) return track.get(0);
        else if (track.get(track.size()-1).getTimePoint()<=refTimePoint) return track.get(track.size()-1);
        for (StructureObject t: track) if (t.getTimePoint()==refTimePoint) return t;
        return track.get(0);
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    /**
     * 
     * @param pop1 (will be modified
     * @param pop2 (will be modified)
     * @return combination of the 2 object population: if objects overlap: mean X value & min yMin value, if not object is added
     */
    public static ObjectPopulation combine(ObjectPopulation pop1, ObjectPopulation pop2) {
        if (pop2==null) return pop1;
        else if (pop1==null) return pop2;
        ArrayList<Object3D> res = new ArrayList<Object3D>(Math.max(pop1.getObjects().size(), pop2.getObjects().size()));
        Iterator<Object3D> it1 = pop1.getObjects().iterator();
        Iterator<Object3D> it2 = pop2.getObjects().iterator();
        int yMin = Integer.MAX_VALUE;
        for (Object3D o : pop1.getObjects()) if (o.getBounds().getyMin()<yMin) yMin = o.getBounds().getyMin();
        for (Object3D o : pop2.getObjects()) if (o.getBounds().getyMin()<yMin) yMin = o.getBounds().getyMin();
        int oIdx = 1;
        while(it1.hasNext()) {
            Object3D o1 = it1.next();
            BoundingBox b1 = o1.getBounds();
            L2 : while(it2.hasNext()) {
                BoundingBox b2 = it2.next().getBounds();
                if (b1.hasIntersection(b2)) {
                    it1.remove();
                    it2.remove();
                    res.add(new Object3D(new BlankMask("mask of microchannel: "+oIdx, b1.getSizeX(), b1.getSizeY(), b1.getSizeZ(), (b1.getxMin()+b2.getxMin())/2, yMin, 0, o1.getScaleXY(), o1.getScaleZ()), oIdx));
                    ++oIdx;
                    break L2;
                }
            }
        }
        res.addAll(pop1.getObjects());
        res.addAll(pop2.getObjects());
        Collections.sort(res, getComparatorObject3D(ObjectIdxTracker.IndexingOrder.XZY));
        for (int i = 0; i<res.size(); ++i) res.get(i).setLabel(i+1);
        return new ObjectPopulation(res, pop1.getImageProperties());
    }
    
}
