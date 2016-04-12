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
package plugins.plugins.trackers.trackMate;

import dataStructure.objects.Object3D;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import static dataStructure.objects.StructureObjectUtils.getStructureObjectComparator;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import static plugins.Plugin.logger;
import plugins.plugins.trackers.trackMate.TrackLikelyhoodEstimator.SplitScenario;
import plugins.plugins.trackers.trackMate.TrackLikelyhoodEstimator.Track;
import utils.HashMapGetCreate;
import utils.HashMapGetCreate.Factory;
import utils.Pair;
import utils.clustering.ClusterCollection;
import utils.clustering.Interface;

/**
 *
 * @author jollion
 */
public class MutationTrackPostProcessing {
    final TreeMap<StructureObject, List<StructureObject>> trackHeadTrackMap; // sorted by timePoint
    final HashMap<Object3D, SpotWithinCompartment>  objectSpotMap;
    final Map<StructureObject, List<SpotWithinCompartment>> trackHeadSpotTrackMap;
    final HashMapGetCreate<List<SpotWithinCompartment>, Track> spotTrackMap;
    TrackLikelyhoodEstimator estimator;
    final SpotPopulation pop;
    final int spotStructureIdx;
    public MutationTrackPostProcessing(int structureIdx, List<StructureObject> parentTrack, SpotPopulation pop) {
        this.pop=pop;
        this.spotStructureIdx=structureIdx;
        trackHeadTrackMap = new TreeMap<StructureObject, List<StructureObject>>(getStructureObjectComparator());
        trackHeadTrackMap.putAll(StructureObjectUtils.getAllTracks(parentTrack, structureIdx));
        objectSpotMap = pop.getObjectSpotMap();
        trackHeadSpotTrackMap = new HashMap<StructureObject, List<SpotWithinCompartment>>(trackHeadTrackMap.size());
        for (Entry<StructureObject, List<StructureObject>> e : trackHeadTrackMap.entrySet()) {
            List<SpotWithinCompartment> l = new ArrayList<SpotWithinCompartment>(e.getValue().size());
            trackHeadSpotTrackMap.put(e.getKey(), l);
            for (StructureObject o : e.getValue()) l.add(objectSpotMap.get(o.getObject()));
        }
        spotTrackMap = new HashMapGetCreate<List<SpotWithinCompartment>, Track>(new Factory<List<SpotWithinCompartment>, Track>() {
            public Track create(List<SpotWithinCompartment> key) {return new Track(key);}
        });
        TrackLikelyhoodEstimator.ScoreFunction sf = new TrackLikelyhoodEstimator.HarmonicScoreFunction(new TrackLikelyhoodEstimator.DistributionFunction(new NormalDistribution(11.97, 1.76)).setNormalization(0.22667175022808675d), new TrackLikelyhoodEstimator.LinearTrimmedFunction(0.3, 0.7, 0.2, 1));
        estimator = new TrackLikelyhoodEstimator(sf, 7);
    }
    
    public void connectShortTracksByDeletingLQSpot(double maxDist) {
        Set<StructureObject> parentsToRelabel = new HashSet<StructureObject>();
        double maxSqDist = maxDist * maxDist;
        Iterator<List<StructureObject>> it = trackHeadTrackMap.values().iterator();
        while (it.hasNext()) {
            List<StructureObject> tailTrack = it.next();
            //if (tailTrack.size()>=maxTrackSize) continue;
            // cherche un spot s proche dans la même bactérie tq LQ(s) ou LQ(trackHead(track))
            if (tailTrack.size()==1) continue;
            StructureObject tailTrackHead = tailTrack.get(0);
            SpotWithinCompartment sTailTrackHead  = objectSpotMap.get(tailTrackHead.getObject());
            SpotWithinCompartment sTailTrackNext  = objectSpotMap.get(tailTrack.get(1).getObject());
            
            double minDist = Double.POSITIVE_INFINITY;
            StructureObject bestCandidate=null;
            boolean deleteTailTrackHead=false;
            
            for (StructureObject headTrackTail : sTailTrackHead.compartiment.object.getChildObjects(spotStructureIdx)) { // look in spots within same compartiment
                if (headTrackTail.getNext()!=null) continue; // look only within track ends
                if (headTrackTail.getPrevious()==null) continue; // look only wihtin tracks with >=1 element
                //if (trackHeadTrackMap.get(headTrackTail.getTrackHead()).size()>=maxTrackSize) continue;
                SpotWithinCompartment sHeadTrackTail  = (SpotWithinCompartment)objectSpotMap.get(headTrackTail.getObject());
                SpotWithinCompartment sHeadTrackPrev = (SpotWithinCompartment)objectSpotMap.get(headTrackTail.getPrevious().getObject());
                double dTailToHeadNext = sHeadTrackTail.squareDistanceTo(sTailTrackNext);
                double dTailPrevToHead = sHeadTrackPrev.squareDistanceTo(sTailTrackHead);
                if (dTailToHeadNext>maxSqDist && dTailPrevToHead>maxSqDist) continue;
                if (!sTailTrackHead.lowQuality && !sHeadTrackTail.lowQuality) continue;
                if (sTailTrackHead.lowQuality && sHeadTrackTail.lowQuality) { // 2 LQ spots: compare distances
                    if (dTailToHeadNext<dTailPrevToHead) {
                        if (bestCandidate==null || minDist>dTailToHeadNext) {
                            minDist = dTailToHeadNext;
                            deleteTailTrackHead = true;
                            bestCandidate = headTrackTail;
                        }
                    } else {
                        if (bestCandidate==null || minDist>dTailPrevToHead) {
                            minDist = dTailPrevToHead;
                            deleteTailTrackHead = false;
                            bestCandidate = headTrackTail;
                        }
                    }
                } else if (!sTailTrackHead.lowQuality && dTailPrevToHead<=maxSqDist) { // keep the high quality spot
                    if (bestCandidate==null || minDist>dTailPrevToHead) {
                        minDist = dTailPrevToHead;
                        deleteTailTrackHead = false;
                        bestCandidate = headTrackTail;
                    }
                } else if (!sHeadTrackTail.lowQuality && dTailToHeadNext<=maxSqDist) { // keep the high quality spot
                    if (bestCandidate==null || minDist>dTailToHeadNext) {
                        minDist = dTailToHeadNext;
                        deleteTailTrackHead = true;
                        bestCandidate = headTrackTail;
                    }
                }
            }
            if (bestCandidate!=null) { // link the 2 tracks
                StructureObject objectToRemove = null;
                it.remove();
                StructureObject headTrackHead = bestCandidate.getTrackHead();
                List<StructureObject> headTrack = this.trackHeadTrackMap.get(headTrackHead);
                List<SpotWithinCompartment> spotHeadTrack = trackHeadSpotTrackMap.get(headTrackHead);
                List<SpotWithinCompartment> spotTailTrack = trackHeadSpotTrackMap.remove(tailTrackHead);
                spotTrackMap.remove(spotHeadTrack);
                spotTrackMap.remove(spotTailTrack);
                StructureObject headTrackTail = null;
                if (deleteTailTrackHead) {
                    spotTailTrack.remove(0);
                    objectToRemove = tailTrack.remove(0);
                    headTrackTail = bestCandidate;
                } else {
                    spotHeadTrack.remove(spotHeadTrack.size()-1);
                    objectToRemove =  headTrack.remove(headTrack.size()-1);
                    headTrackTail = bestCandidate.getPrevious();
                }
                logger.debug("link Tracks: delete: {}, minDist: {}, delete tailTrackHead? {}", objectToRemove, minDist, deleteTailTrackHead);
                tailTrack.get(0).setPreviousInTrack(headTrackTail, false, StructureObject.TrackFlag.correctionMerge);
                for (StructureObject o : tailTrack) o.setTrackHead(headTrackHead, false, false);
                spotHeadTrack.addAll(spotTailTrack);
                headTrack.addAll(tailTrack);
                
                // remove object
                parentsToRelabel.add(objectToRemove.getParent());
                objectToRemove.resetTrackLinks();
                //pop.removeSpot(objectToRemove);
                //objectToRemove.getParent().getChildren(spotStructureIdx).remove(objectToRemove);
            }
        }
        for (StructureObject p : parentsToRelabel) p.relabelChildren(spotStructureIdx);
    }
    public void splitLongTracks() {
        Map<StructureObject, List<SpotWithinCompartment>> trackHeadSpotMapTemp = new HashMap<StructureObject, List<SpotWithinCompartment>>();
        for (Entry<StructureObject, List<SpotWithinCompartment>> e : trackHeadSpotTrackMap.entrySet()) {
            List<StructureObject> track = trackHeadTrackMap.get(e.getKey());
            SplitScenario s = estimator.splitTrack(spotTrackMap.getAndCreateIfNecessary(e.getValue()));
            List<List<StructureObject>> tracks = s.splitTrack(track);
            List<List<SpotWithinCompartment>> spotTracks = s.splitTrack(e.getValue());
            boolean modif = tracks.size()>1;
            for (int i = 0; i<tracks.size(); ++i) {
                List<StructureObject> subTrack = tracks.get(i);
                StructureObject th = subTrack.get(0);
                if (modif) {
                    trackHeadTrackMap.put(th, subTrack);
                    if (i!=tracks.size()-1) subTrack.get(subTrack.size()-1).setTrackFlag(StructureObject.TrackFlag.correctionSplit); // correction flag @ end
                }
                trackHeadSpotMapTemp.put(th, spotTracks.get(i));
                if (i!=0) {
                    //if (th.getNext()!=null) th.getNext().setTrackFlag(StructureObject.TrackFlag.correctionSplit); // correction flag @ start
                    for (StructureObject o : subTrack) o.setTrackHead(th, true, false);
                }
            }
        }
        trackHeadSpotTrackMap.clear();
        trackHeadSpotTrackMap.putAll(trackHeadSpotMapTemp);
    }
    
    public void groupTracks() {
        List<List<StructureObject>> trackHeadGroups = new ArrayList<List<StructureObject>>();
        /*
        1) compute interactions track to track
        2) group interacting tracks
        */
        ClusterCollection<StructureObject, TrackExchangeTimePoints> clusterCollection = new ClusterCollection<StructureObject, TrackExchangeTimePoints>(trackHeadTrackMap.keySet(), trackHeadTrackMap.comparator());
        Iterator<Entry<StructureObject, List<StructureObject>>> it1 = trackHeadTrackMap.entrySet().iterator();
        while(it1.hasNext()) {
            Entry<StructureObject, List<StructureObject>> e1 = it1.next();
            int lastTimePoint = e1.getValue().get(e1.getValue().size()-1).getTimePoint();
            SortedMap<StructureObject, List<StructureObject>> subMap = trackHeadTrackMap.tailMap(e1.getKey(), false);
            Iterator<Entry<StructureObject, List<StructureObject>>> it2 = subMap.entrySet().iterator();
            while(it2.hasNext()) {
                Entry<StructureObject, List<StructureObject>> e2 = it2.next();
                if (e2.getKey().getTimePoint()>lastTimePoint) break;
                TrackExchangeTimePoints t = getExchangeTimePoints(e1.getValue(), e2.getValue());
                if (t!=null) clusterCollection.addInteraction(e1.getKey(), e2.getKey(), t);
            }
        }
        List<Set<Interface<StructureObject, TrackExchangeTimePoints>>> clusters = clusterCollection.getClusters();
        
    }
    
    private class TrackExchangeTimePoints {
        List<StructureObject> track1;
        List<StructureObject> track2;
        List<Integer> exchangeTimePoints;
        
    }
    // return null if no exchange possible
    private TrackExchangeTimePoints getExchangeTimePoints(List<StructureObject> track1, List<StructureObject> track2) {
        throw new UnsupportedOperationException("Not supported yet.");
        //List<Integer> exchangeTimePoints = new ArrayList<Integer>();
        
    }
    
    private static boolean overlappingInTime(List<StructureObject> track1, List<StructureObject> track2) {
        if (track1.isEmpty() || track2.isEmpty()) return false;
        if (track1.get(0).getTimePoint()>track2.get(0).getTimePoint()) return overlappingInTime(track2, track1);
        return track1.get(track1.size()-1).getTimePoint()>=track2.get(0).getTimePoint();
    }
    
    
}
