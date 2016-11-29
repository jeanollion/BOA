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

import com.google.common.collect.Sets;
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
import java.util.Set;
import java.util.TreeSet;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import static plugins.Plugin.logger;
/**
 *
 * @author jollion
 */
public class TrackMateInterface<S extends Spot> {
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
    public void addObjects(List<StructureObject> parentTrack, int structureIdx) {
        for (StructureObject p : parentTrack) {
            for (StructureObject c : p.getChildren(structureIdx)) addObject(c.getObject(), c.getFrame());
        }
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
    
    public boolean processGC(double distanceThreshold, int maxFrameGap) {
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
        }
        // Prepare settings object
        final Map< String, Object > slSettings = new HashMap<>();

        slSettings.put( KEY_ALLOW_GAP_CLOSING, maxFrameGap>1 );
        //slSettings.put( KEY_GAP_CLOSING_FEATURE_PENALTIES, settings.get( KEY_GAP_CLOSING_FEATURE_PENALTIES ) );
        slSettings.put( KEY_GAP_CLOSING_MAX_DISTANCE, distanceThreshold );
        slSettings.put( KEY_GAP_CLOSING_MAX_FRAME_GAP, maxFrameGap );

        slSettings.put( KEY_ALLOW_TRACK_SPLITTING, false );
        //slSettings.put( KEY_SPLITTING_FEATURE_PENALTIES, settings.get( KEY_SPLITTING_FEATURE_PENALTIES ) );
        slSettings.put( KEY_SPLITTING_MAX_DISTANCE, distanceThreshold );

        slSettings.put( KEY_ALLOW_TRACK_MERGING, false );
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
    
    public void setTrackLinks(List<StructureObject> parentTrack, int structureIdx) {
        if (graph==null) throw new RuntimeException("Graph not initialized");
        logger.debug("number of links: {}", graph.edgeSet().size());
        HashMap<Integer, StructureObject> parentT = new HashMap<>(parentTrack.size());
        for (StructureObject p : parentTrack) {
            parentT.put(p.getFrame(), p);
            for (StructureObject s : p.getChildren(structureIdx)) s.resetTrackLinks(true, true);
        }
        TreeSet<DefaultWeightedEdge> nextEdges = new TreeSet(new Comparator<DefaultWeightedEdge>() {
            public int compare(DefaultWeightedEdge arg0, DefaultWeightedEdge arg1) {
                return Double.compare(graph.getEdgeWeight(arg0), graph.getEdgeWeight(arg1));
            }
        });
        for (StructureObject parent : parentTrack) {
            for (StructureObject child : parent.getChildren(structureIdx)) {
                //logger.debug("settings links for: {}", child);
                S s = objectSpotMap.get(child.getObject());
                getSortedEdgesOf(s, graph, false, nextEdges);
                if (!nextEdges.isEmpty()) {
                    DefaultWeightedEdge nextEdge = nextEdges.last(); //main edge -> for previous.next
                    for (DefaultWeightedEdge e : nextEdges) {
                        S nextSpot = getOtherSpot(e, s, graph);
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
    
    private StructureObject getStructureObject(StructureObject parent, int structureIdx, S s) {
        List<StructureObject> children = parent.getChildren(structureIdx);
        Object3D o = spotObjectMap.get(s);
        for (StructureObject c : children) if (c.getObject() == o) return c;
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
            double[] center = o.getCenter(true);
            Spot s = new Spot(center[0], center[1], center[2], 1, 1);
            s.getFeatures().put(Spot.FRAME, (double)frame);
            return s;
        }
        @Override public Spot duplicate(Spot s) {
            Spot res =  new Spot(s.getFeature(Spot.POSITION_X), s.getFeature(Spot.POSITION_Y), s.getFeature(Spot.POSITION_Z), s.getFeature(Spot.RADIUS), s.getFeature(Spot.QUALITY));
            res.getFeatures().put(Spot.FRAME, s.getFeature(Spot.FRAME));
            return res;
        }
    }
}
