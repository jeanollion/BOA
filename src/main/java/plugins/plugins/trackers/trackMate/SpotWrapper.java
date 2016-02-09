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
    private final HashMap<Spot, Object3D>  spotObjectMap = new HashMap<Spot, Object3D>();
    private final HashMap<Object3D, Spot>  objectSpotMap = new HashMap<Object3D, Spot>();
    private final SpotCollection collection = new SpotCollection();
    /**
     * 
     * @param segmentedRegion
     * @param center in voxels number
     * @param quality
     * @param timePoint
     * @param centerOffset
     * @return 
     */
    public Spot wrap(Object3D segmentedRegion, double[] center, double quality, int timePoint, BoundingBox centerOffset) {
        if (centerOffset!=null) {
            center[0]-=centerOffset.getxMin();
            center[1]-=centerOffset.getyMin();
            center[2]-=centerOffset.getzMin();
        }
        center[0] *= segmentedRegion.getScaleXY();
        center[1] *= segmentedRegion.getScaleXY();
        center[2] *= segmentedRegion.getScaleZ();
        double radius = segmentedRegion.is3D() ? Math.pow(3 * segmentedRegion.getSize() / (4 * Math.PI) , 1d/3d) : Math.sqrt(segmentedRegion.getSize() / (2 * Math.PI)) ;
        Spot s = new Spot(center[0], center[1], center[2], radius, quality);
        s.getFeatures().put(Spot.FRAME, (double)timePoint);
        spotObjectMap.put(s, segmentedRegion);
        objectSpotMap.put(segmentedRegion, s);
        logger.debug("adding: spot: center: {}, radius: {}, quality: {}, timePoint:{}", center, radius, quality, timePoint);
        return s;
    }
    public Spot wrap(Object3D segmentedRegion, int timePoint, Image intensityMap, BoundingBox centerOffset) {
        double[] center = intensityMap!=null ? segmentedRegion.getCenter(intensityMap) : segmentedRegion.getCenter();
        double quality = intensityMap!=null ? segmentedRegion.isAbsoluteLandMark() ? intensityMap.getPixelWithOffset(center[0], center[1], center[2]) : intensityMap.getPixel(center[0], center[1], center[2]) : 1;
        return wrap(segmentedRegion, center, quality, timePoint, centerOffset);
    }
    public Object3D get(Spot spot) {
        return spotObjectMap.get(spot);
    }
    
    public SpotCollection getSpotCollection() {
        return this.collection;
    }
    
    public void addSpots(ObjectPopulation population, Image intensityMap, int timePoint, BoundingBox centerOffset) {
        logger.debug("adding: {} spots from timePoint: {}", population.getObjects().size(), timePoint);
        for (Object3D s : population.getObjects()) {
            collection.add(this.wrap(s, timePoint, intensityMap, centerOffset), timePoint);
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
                Spot s = objectSpotMap.get(child.getObject());
                TreeSet<DefaultWeightedEdge> nextEdges = getSortedEdgesOf(s, graph, false);
                if (nextEdges!=null && !nextEdges.isEmpty()) {
                    DefaultWeightedEdge nextEdge = nextEdges.last();
                    for (DefaultWeightedEdge e : nextEdges) {
                        Spot nextSpot = getOtherSpot(e, s, graph);
                        StructureObject nextSo = getStructureObject(parentT.get(nextSpot.getFeature(Spot.FRAME).intValue()), structureIdx, nextSpot);
                        if (nextSo.getPrevious()==null) nextSo.setPreviousInTrack(child, e!=nextEdge);
                        else logger.warn("SpotWrapper: next: {}, next of {}, has already a previous assigned: {}", nextSo, child, nextSo.getPrevious());
                    }
                }
            }
        }
    }
    
    private static TreeSet<DefaultWeightedEdge> getSortedEdgesOf(Spot spot, final SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph, boolean backward) {
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
    private static Spot getMaxLinkedSpot(Spot spot, final SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph, boolean backward) {
        Set<DefaultWeightedEdge> set = graph.edgesOf(spot);
        if (set.isEmpty()) return null;
        double max = -Double.MAX_VALUE;
        Spot maxSpot = null;
        Spot temp;
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
    
    private static Spot getOtherSpot(DefaultWeightedEdge e, Spot spot, SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph ) {
        Spot s = graph.getEdgeTarget(e);
        if (s==spot) return graph.getEdgeSource(e);
        else return s;
    }
    
    private StructureObject getStructureObject(StructureObject parent, int structureIdx, Spot s) {
        ArrayList<StructureObject> children = parent.getChildren(structureIdx);
        Object3D o = this.spotObjectMap.get(s);
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
