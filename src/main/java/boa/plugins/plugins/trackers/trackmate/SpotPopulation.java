/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins.plugins.trackers.trackmate;

import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import static boa.plugins.Plugin.logger;
import boa.utils.HashMapGetCreate;
import boa.utils.HashMapGetCreate.Factory;
import boa.utils.Pair;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class SpotPopulation {
    public final HashMap<Region, SpotWithinCompartment>  objectSpotMap = new HashMap<Region, SpotWithinCompartment>();
    private final SpotCollection collectionHQ = new SpotCollection();
    private final SpotCollection collectionLQ = new SpotCollection();
    private final SpotCollection collection = new SpotCollection();
    public final DistanceComputationParameters distanceParameters;
    public SpotPopulation(DistanceComputationParameters distanceParameters) {
        this.distanceParameters=distanceParameters;
    }
    public SpotCollection getSpotCollection(boolean includeHighQuality, boolean includeLowQuality) {
        if (includeHighQuality && includeLowQuality) return collection;
        else if (includeHighQuality) return  collectionHQ;
        else if (includeLowQuality) return collectionLQ;
        else throw new IllegalArgumentException("No spots included");
    }
    public Set<SpotWithinCompartment> getSpotSet(boolean includehighQuality, boolean includelowQuality) {
        Set<SpotWithinCompartment> res =  new HashSet<SpotWithinCompartment>();
        for (SpotWithinCompartment s : objectSpotMap.values()) {
            if ((includelowQuality&&s.lowQuality) || (includehighQuality&&!s.lowQuality)) res.add(s);
        }
        return res;
    }
    public int[] getMinMaxFrame() {
        int max = 0;
        int min = Integer.MAX_VALUE;
        for (SpotWithinCompartment s : objectSpotMap.values()) {
            if (s.frame>max) max = s.frame;
            if (s.frame<min) min = s.frame;
        }
        return new int[]{min, max};
    }
    public HashMap<Region, SpotWithinCompartment> getObjectSpotMap() {
        return objectSpotMap;
    }
    public void addSpots(StructureObject container, int spotSturctureIdx, List<Region> objects, int compartmentStructureIdx) {
        //ObjectPopulation population = container.getObjectPopulation(spotSturctureIdx);
        List<StructureObject> compartments = container.getChildren(compartmentStructureIdx);
        Image intensityMap = null;// = container.getRawImage(spotSturctureIdx);
        //logger.debug("adding: {} spots from timePoint: {}", population.getObjects().size(), container.getTimePoint());
        HashMapGetCreate<StructureObject, SpotCompartiment> compartimentMap = new HashMapGetCreate<>((StructureObject s) -> new SpotCompartiment(s));
        for (Region o : objects) {
            StructureObject parent = StructureObjectUtils.getInclusionParent(o, compartments, null); 
            if (parent==null) {
                //logger.warn("no parent found for object @ center {}, parents: {}", o.getCenter(true), compartments);
                continue;
            }
            SpotCompartiment compartiment = compartimentMap.getAndCreateIfNecessary(parent);
            double[] center = o.getCenter();
            if (center==null) {
                if (intensityMap==null) intensityMap = container.getRawImage(spotSturctureIdx);
                center = intensityMap!=null ? o.getMassCenter(intensityMap, true) : o.getGeomCenter(true);
            } else {
                center[0]*=o.getScaleXY();
                center[1]*=o.getScaleXY();
                if (center.length>2) center[2]*=o.getScaleZ();
            }
            SpotWithinCompartment s = new SpotWithinCompartment(o, container.getFrame(), compartiment, center, distanceParameters);
            collection.add(s, container.getFrame());
            if (!s.lowQuality) collectionHQ.add(s, container.getFrame());
            else collectionLQ.add(s, container.getFrame());
            objectSpotMap.put(o, s);
        }
    }
    public void setTrackLinks(List<StructureObject> parentTrack, int structureIdx, final SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph) {
        logger.debug("number of links: {}", graph.edgeSet().size());
        HashMap<Integer, StructureObject> parentT = new HashMap<Integer, StructureObject>(parentTrack.size());
        for (StructureObject p : parentTrack) {
            parentT.put(p.getFrame(), p);
            for (StructureObject s : p.getChildren(structureIdx)) {
                s.resetTrackLinks(true, true);
                s.setAttribute("Quality", s.getObject().getQuality());
            }
        }
        TreeSet<DefaultWeightedEdge> nextEdges = new TreeSet(new Comparator<DefaultWeightedEdge>() {
            public int compare(DefaultWeightedEdge arg0, DefaultWeightedEdge arg1) {
                return Double.compare(graph.getEdgeWeight(arg0), graph.getEdgeWeight(arg1));
            }
        });
        for (StructureObject parent : parentTrack) {
            for (StructureObject child : parent.getChildren(structureIdx)) {
                //logger.debug("settings links for: {}", child);
                SpotWithinCompartment s = objectSpotMap.get(child.getObject());
                getSortedEdgesOf(s, graph, false, nextEdges);
                if (!nextEdges.isEmpty()) {
                    DefaultWeightedEdge nextEdge = nextEdges.last(); //main edge -> for previous.next
                    for (DefaultWeightedEdge e : nextEdges) {
                        SpotWithinCompartment nextSpot = getOtherSpot(e, s, graph);
                        StructureObject nextSo = getStructureObject(parentT.get(nextSpot.getFeature(Spot.FRAME).intValue()), structureIdx, nextSpot);
                        if (nextSo.getPrevious()==null) {
                            StructureObjectUtils.setTrackLinks(child, nextSo, true, e==nextEdge);
                            //nextSo.setPreviousInTrack(child, e!=nextEdge);
                        }
                        else logger.warn("SpotWrapper: next: {}, next of {}, has already a previous assigned: {}", nextSo, child, nextSo.getPrevious());
                    }
                } 
                nextEdges.clear();
            }
        }
    }
    
    public void removeLQSpotsUnlinkedToHQSpots(List<StructureObject> parentTrack, int structureIdx, boolean removeFromSpotPopulation) { // PERFORM AFTER LINKS HAVE BEEN SET
        Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(parentTrack, structureIdx);
        Set<StructureObject> parentsToRelabel = new HashSet<StructureObject>();
        int eraseCount = 0;
        for (List<StructureObject> list : allTracks.values()) {
            boolean hQ = false;
            for (StructureObject o : list) {
                if (o.getObject().getQuality()>=this.distanceParameters.qualityThreshold && (list.size()>1 || objectSpotMap.containsKey(o.getObject()))) {
                    hQ = true;
                    break;
                }
            }
            if (!hQ) { // erase track
                for (StructureObject o : list) {
                    o.getParent().getChildren(structureIdx).remove(o);
                    if (removeFromSpotPopulation) {
                        Spot s = objectSpotMap.remove(o.getObject());
                        if (s!=null) {
                            collection.remove(s, o.getFrame());
                            collectionLQ.remove(s, o.getFrame());
                        }
                    }
                    parentsToRelabel.add(o.getParent());
                    eraseCount++;
                }
            }
        }
        for (StructureObject p : parentsToRelabel) p.relabelChildren(structureIdx);
        logger.debug("# of tracks before LQ filter: {}, after: {}, nb of removed spots: {}", allTracks.size(), StructureObjectUtils.getAllTracks(parentTrack, structureIdx).size(), eraseCount);
    }
    
    public void removeSpot(StructureObject o) {
        SpotWithinCompartment s = this.objectSpotMap.remove(o.getObject());
        if (s!=null) {
            collection.remove(s, o.getFrame());
            if (s.lowQuality) collectionLQ.remove(s, o.getFrame());
            else collectionHQ.remove(s, o.getFrame());
        }
    }
    
    private static void getSortedEdgesOf(SpotWithinCompartment spot, final SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph, boolean backward, TreeSet<DefaultWeightedEdge> res) {
        if (!graph.containsVertex(spot)) return;
        Set<DefaultWeightedEdge> set = graph.edgesOf(spot);
        if (set.isEmpty()) return;
        // remove backward or foreward links
        double tp = spot.getFeature(Spot.FRAME);
        if (backward) {
            for (DefaultWeightedEdge e : set) {
                if (getOtherSpot(e, spot, graph).getFeature(Spot.FRAME)<tp) res.add(e);
            }
        } else {
            for (DefaultWeightedEdge e : set) {
                if (getOtherSpot(e, spot, graph).getFeature(Spot.FRAME)>tp) res.add(e);
            }
        }
    }
    private static SpotWithinCompartment getMaxLinkedSpot(SpotWithinCompartment spot, final SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph, boolean backward) {
        Set<DefaultWeightedEdge> set = graph.edgesOf(spot);
        if (set.isEmpty()) return null;
        double max = -Double.MAX_VALUE;
        SpotWithinCompartment maxSpot = null;
        SpotWithinCompartment temp;
        double tempT, tempV;
        // remove backward or foreward links
        double tp = spot.getFeature(Spot.FRAME);
        if (backward) {
            for (DefaultWeightedEdge e : set) {
                temp = getOtherSpot(e, spot, graph);
                tempT = temp.getFeature(Spot.FRAME);
                if ((backward && tempT<tp) || (!backward && tempT>tp)) {
                    tempV = graph.getEdgeWeight(e);
                    if (tempV>max) {
                        maxSpot = temp;
                        max= tempV;
                    }
                }
            }
        }
        return maxSpot;
    }
    
    private static SpotWithinCompartment getOtherSpot(DefaultWeightedEdge e, SpotWithinCompartment spot, SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph ) {
        SpotWithinCompartment s = (SpotWithinCompartment)graph.getEdgeTarget(e);
        if (s==spot) return (SpotWithinCompartment)graph.getEdgeSource(e);
        else return s;
    }
    
    private StructureObject getStructureObject(StructureObject parent, int structureIdx, SpotWithinCompartment s) {
        List<StructureObject> children = parent.getChildren(structureIdx);
        Region o = s.object;
        for (StructureObject c : children) if (c.getObject() == o) return c;
        return null;
    }
    
}
