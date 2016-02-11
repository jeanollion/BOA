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
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import image.BoundingBox;
import image.Image;
import image.ImageProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import static plugins.Plugin.logger;

/**
 *
 * @author jollion
 */
public class SpotWrapper {
    private final HashMap<StructureObject, SpotWithinCompartment>  objectSpotMap = new HashMap<StructureObject, SpotWithinCompartment>();
    private final SpotCollection collection = new SpotCollection();
        
    public SpotCollection getSpotCollection() {
        return this.collection;
    }
    
    public void addSpots(StructureObject container, int spotSturctureIdx, int compartmentStructureIdx) {
        ObjectPopulation population = container.getObjectPopulation(spotSturctureIdx);
        ArrayList<StructureObject> compartments = container.getChildren(compartmentStructureIdx);
        Image intensityMap = container.getRawImage(spotSturctureIdx);
        logger.debug("adding: {} spots from timePoint: {}", population.getObjects().size(), container.getTimePoint());
        for (StructureObject o : container.getChildObjects(spotSturctureIdx)) {
            StructureObject parent = StructureObjectUtils.getInclusionParent(o.getObject(), compartments, null);
            double[] center = intensityMap!=null ? o.getObject().getCenter(intensityMap, true) : o.getObject().getCenter(true);
            SpotWithinCompartment s = new SpotWithinCompartment(o, parent, center);
            collection.add(s, container.getTimePoint());
            objectSpotMap.put(o, s);
        }
    }
    
    public void setTrackLinks(List<StructureObject> parentTrack, int structureIdx, SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph) {
        logger.debug("number of links: {}", graph.edgeSet().size());
        HashMap<Integer, StructureObject> parentT = new HashMap<Integer, StructureObject>(parentTrack.size());
        for (StructureObject p : parentTrack) {
            parentT.put(p.getTimePoint(), p);
            for (StructureObject s : p.getChildren(structureIdx)) s.resetTrackLinks();
        }
        for (StructureObject parent : parentTrack) {
            for (StructureObject child : parent.getChildren(structureIdx)) {
                logger.debug("settings links for: {}", child);
                SpotWithinCompartment s = objectSpotMap.get(child);
                TreeSet<DefaultWeightedEdge> nextEdges = getSortedEdgesOf(s, graph, false);
                if (nextEdges!=null && !nextEdges.isEmpty()) {
                    DefaultWeightedEdge nextEdge = nextEdges.last();
                    for (DefaultWeightedEdge e : nextEdges) {
                        SpotWithinCompartment nextSpot = getOtherSpot(e, s, graph);
                        StructureObject nextSo = getStructureObject(parentT.get(nextSpot.getFeature(Spot.FRAME).intValue()), structureIdx, nextSpot);
                        if (nextSo.getPrevious()==null) nextSo.setPreviousInTrack(child, e!=nextEdge);
                        else logger.warn("SpotWrapper: next: {}, next of {}, has already a previous assigned: {}", nextSo, child, nextSo.getPrevious());
                    }
                }
            }
        }
    }
    
    private static TreeSet<DefaultWeightedEdge> getSortedEdgesOf(SpotWithinCompartment spot, final SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph, boolean backward) {
        if (!graph.containsVertex(spot)) return null;
        Set<DefaultWeightedEdge> set = graph.edgesOf(spot);
        if (set.isEmpty()) return null;
        Comparator<DefaultWeightedEdge> comp = new Comparator<DefaultWeightedEdge>() {
            public int compare(DefaultWeightedEdge arg0, DefaultWeightedEdge arg1) {
                return Double.compare(graph.getEdgeWeight(arg0), graph.getEdgeWeight(arg1));
            }
        };
        TreeSet<DefaultWeightedEdge> res = new TreeSet(comp);
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
        return res;
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
        ArrayList<StructureObject> children = parent.getChildren(structureIdx);
        Object3D o = s.object.getObject();
        for (StructureObject c : children) if (c.getObject() == o) return c;
        return null;
    }
    
    private static void setLink(StructureObject prev, StructureObject next) {
        if (prev.getTimePoint()>next.getTimePoint()) setLink(next, prev);
        else {
            next.setPreviousInTrack(prev, true);
        }
    }
    
    
}
