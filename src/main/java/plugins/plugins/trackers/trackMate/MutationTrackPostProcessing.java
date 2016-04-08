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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
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
    List<List<StructureObject>> trackHeadGroups;
    final HashMap<Object3D, SpotWithinCompartment>  objectSpotMap;
    final Map<StructureObject, List<SpotWithinCompartment>> trackHeadSpotMap;
    final HashMapGetCreate<List<SpotWithinCompartment>, Track> spotTrackMap;
    final double maxExchangeDistance;
    TrackLikelyhoodEstimator estimator;
    
    public MutationTrackPostProcessing(int structureIdx, List<StructureObject> parentTrack, SpotPopulation pop, double maxExchangeDistance) {
        trackHeadTrackMap = new TreeMap<StructureObject, List<StructureObject>>(getStructureObjectComparator());
        trackHeadTrackMap.putAll(StructureObjectUtils.getAllTracks(parentTrack, structureIdx));
        objectSpotMap = pop.getObjectSpotMap();
        trackHeadSpotMap = new HashMap<StructureObject, List<SpotWithinCompartment>>(trackHeadTrackMap.size());
        for (Entry<StructureObject, List<StructureObject>> e : trackHeadTrackMap.entrySet()) {
            List<SpotWithinCompartment> l = new ArrayList<SpotWithinCompartment>(e.getValue().size());
            trackHeadSpotMap.put(e.getKey(), l);
            for (StructureObject o : e.getValue()) l.add(objectSpotMap.get(o.getObject()));
        }
        spotTrackMap = new HashMapGetCreate<List<SpotWithinCompartment>, Track>(new Factory<List<SpotWithinCompartment>, Track>() {
            public Track create(List<SpotWithinCompartment> key) {return new Track(key);}
        });
        this.maxExchangeDistance=maxExchangeDistance;
        estimator = new TrackLikelyhoodEstimator(new NormalDistribution(11.97, 1.76), new BetaDistribution(0.735, 12.69), 6);
    }
    
    public void connectShortTracksByDeletingLQSpot() {
        //
    }
    public void splitLongTracks() {
        Map<StructureObject, List<SpotWithinCompartment>> trackHeadSpotMapTemp = new HashMap<StructureObject, List<SpotWithinCompartment>>();
        for (Entry<StructureObject, List<SpotWithinCompartment>> e : trackHeadSpotMap.entrySet()) {
            List<StructureObject> track = trackHeadTrackMap.get(e.getKey());
            SplitScenario s = estimator.splitTrack(spotTrackMap.getAndCreateIfNecessary(e.getValue()));
            List<List<StructureObject>> tracks = s.splitTrack(track);
            List<List<SpotWithinCompartment>> spotTracks = s.splitTrack(e.getValue());
            if (tracks!=null) {
                for (int i = 0; i<tracks.size(); ++i) {
                    List<StructureObject> subTrack = tracks.get(i);
                    StructureObject th = subTrack.get(0);
                    trackHeadTrackMap.put(th, subTrack);
                    trackHeadSpotMapTemp.put(th, spotTracks.get(i));
                    for (StructureObject o : subTrack) o.setTrackHead(th, true);
                }
            } else trackHeadSpotMapTemp.put(track.get(0), e.getValue());
        }
        trackHeadSpotMap.clear();
        trackHeadSpotMap.putAll(trackHeadSpotMapTemp);
    }
    
    public void groupTracks() {
        trackHeadGroups = new ArrayList<List<StructureObject>>();
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
