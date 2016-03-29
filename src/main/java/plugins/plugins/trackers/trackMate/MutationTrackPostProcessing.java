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
import utils.Pair;
import utils.clustering.ClusterCollection;
import utils.clustering.ClusterCollection.Interface;

/**
 *
 * @author jollion
 */
public class MutationTrackPostProcessing {
    final TreeMap<StructureObject, List<StructureObject>> trackHeadTrackMap; // sorted by timePoint
    List<List<StructureObject>> trackHeadGroups;
    final HashMap<Object3D, SpotWithinCompartment>  objectSpotMap;
    final double maxExchangeDistance;
    
    public MutationTrackPostProcessing(int structureIdx, List<StructureObject> parentTrack, SpotPopulation pop, double maxExchangeDistance) {
        trackHeadTrackMap = new TreeMap<StructureObject, List<StructureObject>>(getStructureObjectComparator());
        trackHeadTrackMap.putAll(StructureObjectUtils.getAllTracks(parentTrack, structureIdx));
        objectSpotMap = pop.getObjectSpotMap();
        this.maxExchangeDistance=maxExchangeDistance;
    }

    
    public void groupTracks() {
        trackHeadGroups = new ArrayList<List<StructureObject>>();
        /*
        1) compute interactions track to track
        2) group interacting tracks
        */
        ClusterCollection<StructureObject, TrackExchangeTimePoints> clusterCollection = new ClusterCollection<StructureObject, TrackExchangeTimePoints>(trackHeadTrackMap.keySet(), interactions, trackHeadTrackMap.comparator());
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
        
        List<Integer> exchangeTimePoints = new ArrayList<Integer>();
        
    }
    private static boolean overlappingInTime(List<StructureObject> track1, List<StructureObject> track2) {
        if (track1.isEmpty() || track2.isEmpty()) return false;
        if (track1.get(0).getTimePoint()>track2.get(0).getTimePoint()) return overlappingInTime(track2, track1);
        return track1.get(track1.size()-1).getTimePoint()>=track2.get(0).getTimePoint();
    }
}
