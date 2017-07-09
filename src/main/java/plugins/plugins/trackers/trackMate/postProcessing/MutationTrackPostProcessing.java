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
package plugins.plugins.trackers.trackMate.postProcessing;

import plugins.plugins.trackers.trackMate.postProcessing.TrackLikelyhoodEstimator;
import dataStructure.objects.Object3D;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import static dataStructure.objects.StructureObjectUtils.getStructureObjectComparator;
import static dataStructure.objects.StructureObjectUtils.setTrackLinks;
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
import plugins.plugins.trackers.trackMate.SpotPopulation;
import plugins.plugins.trackers.trackMate.SpotWithinCompartment;
import static plugins.Plugin.logger;
import plugins.plugins.trackers.trackMate.postProcessing.TrackLikelyhoodEstimator.SplitScenario;
import plugins.plugins.trackers.trackMate.postProcessing.TrackLikelyhoodEstimator.Track;
import utils.HashMapGetCreate;
import utils.HashMapGetCreate.Factory;
import utils.Pair;
import utils.clustering.ClusterCollection;
import utils.clustering.InterfaceImpl;

/**
 *
 * @author jollion
 */
public class MutationTrackPostProcessing {
    final TreeMap<StructureObject, List<StructureObject>> trackHeadTrackMap; // sorted by timePoint
    final Map<Object3D, SpotWithinCompartment>  objectSpotMap;
    final Map<StructureObject, List<SpotWithinCompartment>> trackHeadSpotTrackMap;
    final HashMapGetCreate<List<SpotWithinCompartment>, Track> spotTrackMap;
    final RemoveObjectCallBact removeObject;
    final int spotStructureIdx;
    public MutationTrackPostProcessing(int structureIdx, List<StructureObject> parentTrack, Map<Object3D, SpotWithinCompartment> objectSpotMap, RemoveObjectCallBact removeObject) {
        this.removeObject=removeObject;
        this.spotStructureIdx=structureIdx;
        trackHeadTrackMap = new TreeMap<StructureObject, List<StructureObject>>(getStructureObjectComparator());
        trackHeadTrackMap.putAll(StructureObjectUtils.getAllTracks(parentTrack, structureIdx));
        this.objectSpotMap = objectSpotMap;
        trackHeadSpotTrackMap = new HashMap<StructureObject, List<SpotWithinCompartment>>(trackHeadTrackMap.size());
        for (Entry<StructureObject, List<StructureObject>> e : trackHeadTrackMap.entrySet()) {
            List<SpotWithinCompartment> l = new ArrayList<SpotWithinCompartment>(e.getValue().size());
            trackHeadSpotTrackMap.put(e.getKey(), l);
            for (StructureObject o : e.getValue()) l.add(objectSpotMap.get(o.getObject()));
        }
        spotTrackMap = new HashMapGetCreate<List<SpotWithinCompartment>, Track>(new Factory<List<SpotWithinCompartment>, Track>() {
            public Track create(List<SpotWithinCompartment> key) {return new Track(key);}
        });
        
    }
    public static interface RemoveObjectCallBact {
        public void removeObject(StructureObject object);
    }
    
    public void connectShortTracksByDeletingLQSpot(double maxDist) {
        Set<StructureObject> parentsToRelabel = new HashSet<StructureObject>();
        double maxSqDist = maxDist * maxDist;
        Iterator<List<StructureObject>> it = trackHeadTrackMap.values().iterator();
        while (it.hasNext()) {
            List<StructureObject> nextTrack = it.next();
            //if (tailTrack.size()>=maxTrackSize) continue;
            // cherche un spot s proche dans la même bactérie tq LQ(s) ou LQ(trackHead(track))
            if (nextTrack.size()==1) continue;
            StructureObject nextTrackTH = nextTrack.get(0);
            SpotWithinCompartment sNextTrackTH  = objectSpotMap.get(nextTrackTH.getObject());
            SpotWithinCompartment sNextTrackN  = objectSpotMap.get(nextTrack.get(1).getObject());
            
            double minDist = Double.POSITIVE_INFINITY;
            StructureObject bestPrevTrackEnd=null;
            boolean deleteNextTrackTH=false;
            if (sNextTrackTH==null) {
                logger.debug("no spot found for: {}, tl : {}", nextTrackTH, nextTrack.size());
                continue;
            }
            for (StructureObject prevTrackEnd : sNextTrackTH.compartiment.object.getChildren(spotStructureIdx)) { // look in spots within same compartiment
                if (prevTrackEnd.getNext()!=null) continue; // look only within track ends
                if (prevTrackEnd.getPrevious()==null) continue; // look only wihtin tracks with >=1 element
                //if (trackHeadTrackMap.get(headTrackTail.getTrackHead()).size()>=maxTrackSize) continue;
                SpotWithinCompartment sprevTrackEnd  = (SpotWithinCompartment)objectSpotMap.get(prevTrackEnd.getObject());
                SpotWithinCompartment sPrevTrackP = (SpotWithinCompartment)objectSpotMap.get(prevTrackEnd.getPrevious().getObject());
                if (!sNextTrackTH.lowQuality && !sprevTrackEnd.lowQuality) continue;
                double dEndToN = sprevTrackEnd.squareDistanceTo(sNextTrackN);
                double dPToTH = sPrevTrackP.squareDistanceTo(sNextTrackTH);
                if (dEndToN>maxSqDist && dPToTH>maxSqDist) continue;
                if (sNextTrackTH.lowQuality && sprevTrackEnd.lowQuality) { // 2 LQ spots: compare distances
                    if (dEndToN<dPToTH) {
                        if (bestPrevTrackEnd==null || minDist>dEndToN) {
                            minDist = dEndToN;
                            deleteNextTrackTH = true;
                            bestPrevTrackEnd = prevTrackEnd;
                        }
                    } else {
                        if (bestPrevTrackEnd==null || minDist>dPToTH) {
                            minDist = dPToTH;
                            deleteNextTrackTH = false;
                            bestPrevTrackEnd = prevTrackEnd;
                        }
                    }
                } else if (!sNextTrackTH.lowQuality && dPToTH<=maxSqDist) { // keep the high quality spot
                    if (bestPrevTrackEnd==null || minDist>dPToTH) {
                        minDist = dPToTH;
                        deleteNextTrackTH = false;
                        bestPrevTrackEnd = prevTrackEnd;
                    }
                } else if (!sprevTrackEnd.lowQuality && dEndToN<=maxSqDist) { // keep the high quality spot
                    if (bestPrevTrackEnd==null || minDist>dEndToN) {
                        minDist = dEndToN;
                        deleteNextTrackTH = true;
                        bestPrevTrackEnd = prevTrackEnd;
                    }
                }
                //logger.debug("link Tracks: candidate: d2 t->hn: {}, d2tp->h: {}", dTailToHeadNext, dTailPrevToHead);
            }
            if (bestPrevTrackEnd!=null) { // link the 2 tracks
                StructureObject objectToRemove = null;
                it.remove();
                StructureObject prevTrackTH = bestPrevTrackEnd.getTrackHead();
                List<StructureObject> prevTrack = this.trackHeadTrackMap.get(prevTrackTH);
                List<SpotWithinCompartment> spotPrevTrack = trackHeadSpotTrackMap.get(prevTrackTH);
                List<SpotWithinCompartment> spotNextTrack = trackHeadSpotTrackMap.remove(nextTrackTH);
                spotTrackMap.remove(spotPrevTrack);
                spotTrackMap.remove(spotNextTrack);
                if (deleteNextTrackTH) {
                    spotNextTrack.remove(0);
                    objectToRemove = nextTrack.remove(0);
                    setTrackLinks(prevTrack.get(prevTrack.size()-1), nextTrack.get(0), true, true);
                } else {
                    spotPrevTrack.remove(spotPrevTrack.size()-1);
                    objectToRemove =  prevTrack.remove(prevTrack.size()-1);
                    setTrackLinks(objectToRemove.getPrevious(), nextTrack.get(0), true, true);
                }
                for (StructureObject o : nextTrack) o.setTrackHead(prevTrackTH, false, false, null);
                spotPrevTrack.addAll(spotNextTrack);
                prevTrack.addAll(nextTrack);
                
                // remove object
                parentsToRelabel.add(objectToRemove.getParent());
                objectToRemove.resetTrackLinks(true, true);
                removeObject.removeObject(objectToRemove);
                objectToRemove.getParent().getChildren(spotStructureIdx).remove(objectToRemove);
            }
        }
        for (StructureObject p : parentsToRelabel) p.relabelChildren(spotStructureIdx);
    }
    public void splitLongTracks(int maximalSplitNumber, int minimalTrackLength, double distanceThreshold, double maxDistance, double maximalPenalty) {
        if (minimalTrackLength<1)minimalTrackLength=1;
        
        TrackLikelyhoodEstimator.ScoreFunction sf = new DistancePenaltyScoreFunction(new NormalDistribution(11.97, 1.76), new BetaDistribution(1.94, 7.66), distanceThreshold, maximalPenalty);
        TrackLikelyhoodEstimator estimator = new TrackLikelyhoodEstimator(sf, minimalTrackLength, maximalSplitNumber);
        logger.debug("distance function: 0={} 0.3={}, 0.4={}, 0.5={}, 0.6={}, 0.7={}, 1={}", sf.getDistanceFunction().y(0), sf.getDistanceFunction().y(0.3), sf.getDistanceFunction().y(0.4), sf.getDistanceFunction().y(0.5), sf.getDistanceFunction().y(0.6), sf.getDistanceFunction().y(0.7), sf.getDistanceFunction().y(1));
        
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
                    if (i!=tracks.size()-1) subTrack.get(subTrack.size()-1).setAttribute(StructureObject.correctionSplit, true); // correction flag @ end
                    if (i!=0 ) subTrack.get(0).setAttribute(StructureObject.correctionSplit, true); // correction flag @ start
                }
                trackHeadSpotMapTemp.put(th, spotTracks.get(i));
                if (i!=0) {
                    //if (th.getNext()!=null) th.getNext().setTrackFlag(StructureObject.TrackFlag.correctionSplit); // correction flag @ start
                    for (StructureObject o : subTrack) o.setTrackHead(th, true, false, null);
                }
            }
        }
        trackHeadSpotTrackMap.clear();
        trackHeadSpotTrackMap.putAll(trackHeadSpotMapTemp);
    }
    public void flagShortAndLongTracks(int shortTrackThreshold, int longTrackTreshold) {
        for (List<StructureObject> track : trackHeadTrackMap.values()) {
            int trackLength = track.get(track.size()-1).getFrame()-track.get(0).getFrame();
            if ((shortTrackThreshold>0 && trackLength<shortTrackThreshold) || (longTrackTreshold>0 && trackLength>longTrackTreshold)) {
                for (StructureObject o : track) o.setAttribute(StructureObject.trackErrorNext, true);
            }
        }
    }

    
    /*public void groupTracks() {
        List<List<StructureObject>> trackHeadGroups = new ArrayList<List<StructureObject>>();
        
        //1) compute interactions track to track
        //2) group interacting tracks
        
        ClusterCollection<StructureObject, TrackExchangeTimePoints> clusterCollection = new ClusterCollection<StructureObject, TrackExchangeTimePoints>(trackHeadTrackMap.keySet(), trackHeadTrackMap.comparator(), null);
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
        List<Set<InterfaceImpl<StructureObject, TrackExchangeTimePoints>>> clusters = clusterCollection.getClusters();
        
    }*/
    
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
        if (track1.get(0).getFrame()>track2.get(0).getFrame()) return overlappingInTime(track2, track1);
        return track1.get(track1.size()-1).getFrame()>=track2.get(0).getFrame();
    }
    
    
}
