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

import boa.gui.GUI;
import com.google.common.collect.Sets;
import configuration.parameters.Parameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_ALLOW_GAP_CLOSING;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_ALLOW_TRACK_MERGING;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_ALLOW_TRACK_SPLITTING;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_ALTERNATIVE_LINKING_COST_FACTOR;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_CUTOFF_PERCENTILE;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_GAP_CLOSING_MAX_DISTANCE;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_GAP_CLOSING_MAX_FRAME_GAP;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_LINKING_MAX_DISTANCE;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_MERGING_MAX_DISTANCE;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_SPLITTING_MAX_DISTANCE;
import java.util.HashMap;
import fiji.plugin.trackmate.tracking.sparselap.SparseLAPFrameToFrameTracker;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.slf4j.LoggerFactory;
import static plugins.Plugin.logger;
import utils.Utils;
/**
 *
 * @author jollion
 */
public class TrackMateInterface<S extends Spot> {
    public static final org.slf4j.Logger logger = LoggerFactory.getLogger(TrackMateInterface.class);
    public final HashMap<Object3D, S>  objectSpotMap = new HashMap<>();
    public final HashMap<S, Object3D>  spotObjectMap = new HashMap<>();
    private final SpotCollection collection = new SpotCollection();
    private Logger internalLogger = Logger.VOID_LOGGER;
    int numThreads=1;
    String errorMessage;
    private SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph;
    public final SpotFactory<S> factory;

    public TrackMateInterface(SpotFactory<S> factory) {
        this.factory = factory;
    }
    public void resetEdges() {
        graph=null;
    }
    public void removeObject(Object3D o, int frame) {
        S s = objectSpotMap.remove(o);
        if (s!=null) {
            if (graph!=null) graph.removeVertex(s);
            spotObjectMap.remove(s);
            collection.remove(s, frame);
        }
    }
    
    public void addObject(Object3D o, int frame) {
        S s = factory.toSpot(o, frame);
        objectSpotMap.put(o, s);
        spotObjectMap.put(s, o);
        collection.add(s, frame);
    }
    
    public void addObjects(Collection<Object3D> objects, int frame) {
        objects.stream().forEach((o) -> {
            addObject(o, frame);
        });
    }
    public void addObjects(Map<Integer, List<StructureObject>> objectsF) {
        for (StructureObject c : Utils.flattenMap(objectsF)) addObject(c.getObject(), c.getFrame());
    }
    
    public boolean processFTF(double distanceThreshold) {
        long t0 = System.currentTimeMillis();
        // Prepare settings object
        final Map< String, Object > ftfSettings = new HashMap<>();
        ftfSettings.put( KEY_LINKING_MAX_DISTANCE, distanceThreshold );
        ftfSettings.put( KEY_ALTERNATIVE_LINKING_COST_FACTOR, 1.05 );
        
        final SparseLAPFrameToFrameTrackerFromExistingGraph frameToFrameLinker = new SparseLAPFrameToFrameTrackerFromExistingGraph(collection, ftfSettings, graph );
        frameToFrameLinker.setNumThreads( numThreads );
        final Logger.SlaveLogger ftfLogger = new Logger.SlaveLogger( internalLogger, 0, 0.5 );
        frameToFrameLinker.setLogger( ftfLogger );

        if ( !frameToFrameLinker.checkInput() || !frameToFrameLinker.process()) {
                errorMessage = frameToFrameLinker.getErrorMessage();
                logger.error(errorMessage);
                return false;
        }
        graph = frameToFrameLinker.getResult();
        long t1 = System.currentTimeMillis();
        logger.debug("number of edges after FTF step: {}, nb of vertices: {}, processing time: {}", graph.edgeSet().size(), graph.vertexSet().size(), t1-t0);
        return true;
    }
    
    public boolean processGC(double distanceThreshold, int maxFrameGap, boolean allowSplitting, boolean allowMerging) {
        long t0 = System.currentTimeMillis();
        Set<S> unlinkedSpots;
        if (graph == null) {
            graph = new SimpleWeightedGraph<>( DefaultWeightedEdge.class );
            unlinkedSpots = new HashSet<>(spotObjectMap.keySet()); 
        } else {
            Set<Spot> linkedSpots = graph.vertexSet();
            unlinkedSpots = new HashSet<>(Sets.difference(spotObjectMap.keySet(), linkedSpots));
        }
        Map<Spot, Spot> clonedSpots = new HashMap<>();
        // duplicate unlinked spots to include them in the gap-closing part
        for (S s : unlinkedSpots) {
            Spot clone = factory.duplicate(s);
            graph.addVertex(s);
            graph.addVertex(clone);
            clonedSpots.put(clone, s);
            graph.addEdge(s,clone);
            //logger.debug("unlinked object: f={}, Idx={}", s.getFeature(Spot.FRAME), spotObjectMap.get(s).getLabel()-1);
        }
        // Prepare settings object
        final Map< String, Object > slSettings = new HashMap<>();

        slSettings.put( KEY_ALLOW_GAP_CLOSING, maxFrameGap>1 );
        //slSettings.put( KEY_GAP_CLOSING_FEATURE_PENALTIES, settings.get( KEY_GAP_CLOSING_FEATURE_PENALTIES ) );
        slSettings.put( KEY_GAP_CLOSING_MAX_DISTANCE, distanceThreshold );
        slSettings.put( KEY_GAP_CLOSING_MAX_FRAME_GAP, maxFrameGap );

        slSettings.put( KEY_ALLOW_TRACK_SPLITTING, allowSplitting );
        //slSettings.put( KEY_SPLITTING_FEATURE_PENALTIES, settings.get( KEY_SPLITTING_FEATURE_PENALTIES ) );
        slSettings.put( KEY_SPLITTING_MAX_DISTANCE, distanceThreshold );

        slSettings.put( KEY_ALLOW_TRACK_MERGING, allowMerging );
        //slSettings.put( KEY_MERGING_FEATURE_PENALTIES, settings.get( KEY_MERGING_FEATURE_PENALTIES ) );
        slSettings.put( KEY_MERGING_MAX_DISTANCE, distanceThreshold );

        slSettings.put( KEY_ALTERNATIVE_LINKING_COST_FACTOR, 1.05 );
        slSettings.put( KEY_CUTOFF_PERCENTILE, 1d );
        // Solve.
        final SparseLAPSegmentTracker segmentLinker = new SparseLAPSegmentTracker( graph, slSettings, new DistanceComputationParameters().setAlternativeDistance(distanceThreshold*1.05));
        //final fiji.plugin.trackmate.tracking.sparselap.SparseLAPSegmentTracker segmentLinker = new fiji.plugin.trackmate.tracking.sparselap.SparseLAPSegmentTracker( graph, slSettings);
        segmentLinker.setNumThreads(numThreads);
        final Logger.SlaveLogger slLogger = new Logger.SlaveLogger( internalLogger, 0.5, 0.5 );
        segmentLinker.setLogger( slLogger );
        if ( !segmentLinker.checkInput() || !segmentLinker.process() ) {
            errorMessage = segmentLinker.getErrorMessage();
            logger.error(errorMessage);
            return false;
        }
        for (Map.Entry<Spot, Spot> e : clonedSpots.entrySet()) transferLinks(e.getKey(), e.getValue());
        long t1 = System.currentTimeMillis();
        logger.debug("number of edges after GC step: {}, nb of vertices: {}, processing time: {}", graph.edgeSet().size(), graph.vertexSet().size(), t1-t0);
        return true;
    }
    
    private void transferLinks(Spot from, Spot to) {
        List<DefaultWeightedEdge> edgeList = new ArrayList<>(graph.edgesOf(from));
        for (DefaultWeightedEdge e : edgeList) {
            Spot target = graph.getEdgeTarget(e);
            boolean isSource = true;
            if (target==from) {
                target = graph.getEdgeSource(e);
                isSource=false;
            }
            graph.removeEdge(e);
            if (target!=to) graph.addEdge(isSource?to : target, isSource ? target : to, e);          
        }
        graph.removeVertex(from);
    }
    public void setTrackLinks(Map<Integer, List<StructureObject>> objectsF) {
        setTrackLinks(objectsF, null);
    }
    
    public void  removeCrossingLinksFromGraph(double spatialTolerence) {
        if (graph==null) return;
        long t0 = System.currentTimeMillis();
        HashSet<DefaultWeightedEdge> toRemove = new HashSet<>();
        HashSet<Spot> toRemSpot = new HashSet<>();
        for (DefaultWeightedEdge e1 : graph.edgeSet()) {
            for (DefaultWeightedEdge e2 : graph.edgeSet()) {
                if (intersect(e1, e2, spatialTolerence, toRemSpot)) {
                    toRemove.add(e1);
                    toRemove.add(e2);
                }
            }
        }
        graph.removeAllEdges(toRemove);
        
        for (Spot s : toRemSpot) { // also remove vertex that are not linked anymore
            if (graph.edgesOf(s).isEmpty()) graph.removeVertex(s);
        }
        long t1 = System.currentTimeMillis();
        logger.debug("number of edges after removing intersecting links: {}, nb of vertices: {}, processing time: {}", graph.edgeSet().size(), graph.vertexSet().size(), t1-t0);
    }
    private static String toString(Spot s) {
        return "F="+s.getFeature(Spot.FRAME)+";Idx="+s.getFeature("Idx");
    }

    private boolean intersect(DefaultWeightedEdge e1, DefaultWeightedEdge e2, double spatialTolerence, HashSet<Spot> toRemSpot) {
        if (e1.equals(e2)) return false;
        Spot s1 = graph.getEdgeSource(e1);
        Spot s2 = graph.getEdgeSource(e2);
        Spot t1 = graph.getEdgeTarget(e1);
        Spot t2 = graph.getEdgeTarget(e2);
        if (s1.equals(t1) || s2.equals(t2) || s1.equals(s2) || t1.equals(t2)) return false;
        if (!intersect(s1.getFeature(Spot.FRAME), t1.getFeature(Spot.FRAME), s2.getFeature(Spot.FRAME), t2.getFeature(Spot.FRAME), 0)) return false;
        for (String f : Spot.POSITION_FEATURES) {
            if (!intersect(s1.getFeature(f), t1.getFeature(f), s2.getFeature(f), t2.getFeature(f), spatialTolerence)) return false;
        }
        toRemSpot.add(s1);
        toRemSpot.add(s2);
        toRemSpot.add(t1);
        toRemSpot.add(t2);
        return true;
    }
    private static boolean intersect(double min1, double max1, double min2, double max2, double tolerance) {
        if (min1>max1) {
            double t = max1;
            max1=min1;
            min1=t;
        }
        if (min2>max2) {
            double t = max2;
            max2=min2;
            min2=t;
        }
        double min = Math.max(min1, min2);
        double max = Math.min(max1, max2);
        return max>min-tolerance;
    }
    
    public void setTrackLinks(Map<Integer, List<StructureObject>> objectsF, Collection<StructureObject> modifiedObjects) {
        if (objectsF==null || objectsF.isEmpty()) return;
        List<StructureObject> objects = Utils.flattenMap(objectsF);
        int minF = objectsF.keySet().stream().min((i1, i2)->Integer.compare(i1, i2)).get();
        int maxF = objectsF.keySet().stream().max((i1, i2)->Integer.compare(i1, i2)).get();
        for (StructureObject o : objects) o.resetTrackLinks(o.getFrame()>minF, o.getFrame()<maxF, false, modifiedObjects);
        if (graph==null) {
            logger.error("Graph not initialized!");
            return;
        }
        logger.debug("number of links: {}", graph.edgeSet().size());
        
        

        TreeSet<DefaultWeightedEdge> edgeBucket = new TreeSet(new Comparator<DefaultWeightedEdge>() {
            @Override public int compare(DefaultWeightedEdge arg0, DefaultWeightedEdge arg1) {
                return Double.compare(graph.getEdgeWeight(arg0), graph.getEdgeWeight(arg1));
            }
        });
        
        setEdges(objects, objectsF, false, edgeBucket, modifiedObjects);
        setEdges(objects, objectsF, true, edgeBucket, modifiedObjects);
    }
    private void setEdges(List<StructureObject> objects, Map<Integer, List<StructureObject>> objectsByF, boolean prev, TreeSet<DefaultWeightedEdge> edgesBucket, Collection<StructureObject> modifiedObjects) {
        for (StructureObject child : objects) {
            edgesBucket.clear();
            //logger.debug("settings links for: {}", child);
            S s = objectSpotMap.get(child.getObject());
            getSortedEdgesOf(s, graph, prev, edgesBucket);
            if (edgesBucket.size()==1) {
                DefaultWeightedEdge e = edgesBucket.first();
                S otherSpot = getOtherSpot(e, s, graph);
                StructureObject other = getStructureObject(objectsByF.get(otherSpot.getFeature(Spot.FRAME).intValue()), otherSpot);
                if (other!=null) {
                    if (prev) {
                        if (child.getPrevious()!=null && !child.getPrevious().equals(other)) {
                            logger.warn("warning: {} has already a previous assigned: {}, cannot assign: {}", child, child.getPrevious(), other);
                        } else StructureObjectUtils.setTrackLinks(other, child, true, false, modifiedObjects);
                    } else {
                        if (child.getNext()!=null && !child.getNext().equals(other)) {
                            logger.warn("warning: {} has already a next assigned: {}, cannot assign: {}", child, child.getNext(), other);
                        } else StructureObjectUtils.setTrackLinks(child, other, false, true, modifiedObjects);
                    }
                }
                //else logger.warn("SpotWrapper: next: {}, next of {}, has already a previous assigned: {}", nextSo, child, nextSo.getPrevious());
            }
        }
    }
    private void getSortedEdgesOf(S spot, final SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph, boolean backward, TreeSet<DefaultWeightedEdge> res) {
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
    
    private S getOtherSpot(DefaultWeightedEdge e, S spot, SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph ) {
        S s = (S)graph.getEdgeTarget(e);
        if (s==spot) return (S)graph.getEdgeSource(e);
        else return s;
    }
    
    private StructureObject getStructureObject(List<StructureObject> candidates, S s) {
        if (candidates==null || candidates.isEmpty()) return null;
        Object3D o = spotObjectMap.get(s);
        for (StructureObject c : candidates) if (c.getObject() == o) return c;
        return null;
    }
    
    public static DefaultObject3DSpotFactory defaultFactory() {
        return new DefaultObject3DSpotFactory();
    }
    public interface SpotFactory<S extends Spot> {
        public S toSpot(Object3D o, int frame);
        public S duplicate(S s);
    }

    public static class DefaultObject3DSpotFactory implements SpotFactory<Spot> {
        @Override
        public Spot toSpot(Object3D o, int frame) {
            double[] center = o.getCenter();
            if (center==null) center = o.getGeomCenter(true);
            Spot s = new Spot(center[0], center[1], center.length>=2 ? center[2] : 0, 1, 1);
            s.getFeatures().put(Spot.FRAME, (double)frame);
            s.getFeatures().put("Idx", (double)(o.getLabel()-1));
            return s;
        }
        @Override public Spot duplicate(Spot s) {
            Spot res =  new Spot(s.getFeature(Spot.POSITION_X), s.getFeature(Spot.POSITION_Y), s.getFeature(Spot.POSITION_Z), s.getFeature(Spot.RADIUS), s.getFeature(Spot.QUALITY));
            res.getFeatures().put(Spot.FRAME, s.getFeature(Spot.FRAME));
            res.getFeatures().put("Idx", s.getFeature("Idx"));
            return res;
        }
    }
}
