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
package boa.image.processing.bacteria_spine;

import boa.data_structure.Region;
import boa.data_structure.Voxel;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.image.BoundingBox;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import boa.image.ImageShort;
import boa.image.processing.ImageOperations;
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;
import boa.image.processing.neighborhood.Neighborhood;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Jean Ollion
 */
public class CleanContour {
    public static final Logger logger = LoggerFactory.getLogger(CleanContour.class);
    final Set<Voxel> contours;
    Map<Voxel, int[]> voxMapNeighAndLabels;
    final EllipsoidalNeighborhood neigh = new EllipsoidalNeighborhood(1.5, true);
    Map<Integer, Segment> segments=new HashMap<>();
    final TreeSet<Integer> availableLabels = new TreeSet<>(); // to avoid label overflow for large & complex contours
    int maxLabel = 0;
    public boolean verbose = false;
    /**
     * Ensures {@param contour} is only composed of 2-connected voxels
     * @param contour set of contour voxel of an object, ie voxel from the object in contact with the background
     * @return cleaned contour, same instance as {@param contour} for convinience 
     */
    public static Set<Voxel> cleanContour(Set<Voxel> contour) {
        return new CleanContour(contour, false).clean();
    }
    /**
     * See {@link #cleanContour(java.util.Set)}
     * @param contour
     * @param verbose intermediate states will be displayed, for debugging
     * @return 
     */
    public static Set<Voxel> cleanContour(Set<Voxel> contour, boolean verbose) {
        return new CleanContour(contour, verbose).clean();
    }
    private CleanContour(Set<Voxel> contour, boolean verbose) {
        this.verbose=verbose;
        this.contours=contour;
        voxMapNeighAndLabels = new HashMap<>(contour.size());
        contours.stream().forEach(v -> {
            int n = computeNeighbors(v);
            v.value = n;
            voxMapNeighAndLabels.put(v, new int[]{n, 0});
        });
        contours.stream().forEach(v->label(v));
        if (verbose) ImageWindowManagerFactory.showImage(draw(true).setName("neighbors before run"));
        if (verbose) ImageWindowManagerFactory.showImage(draw(false).setName("labels before run"));
    }
    
    private ImageInteger draw(boolean neigh) {
        ImageInteger map = new Region(contours, 1, false, 1, 1).getMaskAsImageInteger();
        ImageOperations.fill(map, 0, null);
        voxMapNeighAndLabels.entrySet().stream().forEach(e->map.setPixelWithOffset(e.getKey().x, e.getKey().y, e.getKey().z, e.getValue()[neigh?0:1]));
        return map;
    }
    private void keepOnlyLargestCluster() {
        List<Set<Segment>> clusters = getAllSegmentClusters();
        if (clusters.size()>1) { 
            if (verbose) logger.debug("clean contour: {} independent contours found! ", clusters.size()); 
            Function<Set<Segment>, Integer> clusterSize = s->s.stream().mapToInt(seg->seg.voxels.size()).sum();
            Set<Segment> max = clusters.stream().max((c1, c2)->Integer.compare(clusterSize.apply(c1), clusterSize.apply(c2))).get();
            clusters.remove(max);
            clusters.stream().forEach(s->s.stream().forEach(seg->seg.remove(true, false)));
            if (verbose) ImageWindowManagerFactory.showImage(draw(true).setName("neighbors after remove"));
            if (verbose) ImageWindowManagerFactory.showImage(draw(false).setName("labels after remove"));
        }
    }
    public Set<Voxel> clean() {
        keepOnlyLargestCluster(); // if there are several distinct objects OR holes inside : keep only largest contour -> erase all others
        if (segments.values().stream().filter(s->s.isJunction).findAny().orElse(null)==null) return contours;
        if (verbose) logger.debug("clean contour: {} segments", segments.size());
        // erase all branch segments that are conected to only one junction
        boolean change = true;
        while (change) {
            change = false;
            if (segments.size()>2) {
                // remove branches that are connected to only one junction by only one pixel
                for (Segment s : segments.values()) {
                    if (s.isJunction || s.connectedSegments.size()!=1 || s.getTouchingVoxels(s.connectedSegments.iterator().next()).count()!=1) continue;
                    s.remove(true, true);
                    Segment junction  = s.connectedSegments.stream().findAny().orElse(null);
                    junction.relabel();
                    change = true;
                    break;
                }
            }
            if (!change && segments.size()>1) { // if no change OR only 1 segment & 1 junction -> it means the junction wasn't removed by removing connected branches ->  there is a weird structure in the junction -> try to clean it
                Segment junction = segments.values().stream().filter(s->s.isJunction).findAny().orElse(null);
                if (junction==null) { // semgents were islated by process
                    keepOnlyLargestCluster();
                    change =true;
                } else {
                    if (verbose) {
                        ImageWindowManagerFactory.showImage(draw(true).setName("neighbors before clean junction:"+junction.label));
                        ImageWindowManagerFactory.showImage(draw(false).setName("labels after clean junction"+junction.label));
                    }
                    boolean cleanPerformed = cleanJunction(junction); // clean the junction : try to close the branch with minimal pixels of the junction
                    if (verbose) {
                        ImageWindowManagerFactory.showImage(draw(true).setName("neighbors after clean junction:"));
                        ImageWindowManagerFactory.showImage(draw(false).setName("labels after clean junction"));
                    }
                    if (!cleanPerformed) {
                        throw new RuntimeException("Unable to clean junction "+junction.label);
                        //break;
                    } else change = true;
                }
            }
        }
        if (verbose) {
            ImageWindowManagerFactory.showImage(draw(true).setName("neighbors after run"));
            ImageWindowManagerFactory.showImage(draw(false).setName("labels after run"));
        }
        return contours;
    }
    private void label(Voxel v) {
        Voxel temp = new Voxel(0, 0, 0);
        int currentLabel = 0;
        for (int i = 0; i<neigh.getSize(); ++i) {
            temp.x = v.x + neigh.dx[i];
            temp.y = v.y + neigh.dy[i];
            int[] temp_N_L = voxMapNeighAndLabels.get(temp);
            if (temp_N_L==null) continue; // out of contour
            if (temp_N_L[1]>0) { // neighbor already labelled
                if (v.value>2 == temp_N_L[0]>2) { // both are junction or both are branch
                    if (currentLabel == 0) { // current voxel not labelled -> add it to segment
                        segments.get(temp_N_L[1]).addVoxel(v);
                        currentLabel = temp_N_L[1];
                    } else { // current voxel already labelled merge its segments with the adjacent one
                        Segment res = segments.get(temp_N_L[1]).merge(segments.get(currentLabel));
                        currentLabel = res.label;
                    }
                } else { // current segment & other segment are of different type -> only add a link between them
                    if (currentLabel==0) { // label current
                        currentLabel = getNextLabel();
                        segments.put(currentLabel, new Segment(currentLabel, v));
                    }
                    Segment s1 = segments.get(temp_N_L[1]);
                    Segment s2 = segments.get(currentLabel);
                    s1.connectedSegments.add(s2);
                    s2.connectedSegments.add(s1);
                }
            }
        }
        if (currentLabel==0) {
            currentLabel = getNextLabel(); // label current
            segments.put(currentLabel, new Segment(currentLabel, v));
        }
    }
    private boolean cleanJunction(Segment junction) {
        // get branch pixels that are connected to junction
        Voxel[] branchEnds;
        switch (junction.connectedSegments.size()) {
            case 1: {
                branchEnds = junction.connectedSegments.iterator().next().getTouchingVoxels(junction).toArray(s->new Voxel[s]);
                if (branchEnds.length==1) {
                    if (verbose) {
                        ImageWindowManagerFactory.showImage(draw(true).setName("clean junction: only one branch voxel connected to junction #"+junction.label));
                        ImageWindowManagerFactory.showImage(draw(false).setName("clean junction: only one branch voxel connected to junction #"+junction.label));
                    }
                    junction.remove(true, true); // junction is weird structure at the end of branch -> erase it
                    return true;
                } else if (branchEnds.length>2) throw new RuntimeException("clean junction: more than 2 branch voxels connected to junction");
                break;
            }
            case 2: {
                Iterator<Segment> it = junction.connectedSegments.iterator();
                Segment b1 = it.next();
                Segment b2 = it.next();
                Voxel[] bEnds1 = b1.getTouchingVoxels(junction).toArray(s->new Voxel[s]);
                Voxel[] bEnds2 = b2.getTouchingVoxels(junction).toArray(s->new Voxel[s]);
                if (bEnds1.length==1 && bEnds2.length==1) {
                    branchEnds = new Voxel[]{bEnds1[0], bEnds2[0]};
                    break; // there are only 2 connected ends go to the proper clean branch section 
                } else { // EITHER one branche has 2 voxel on the junction the other only one -> erase the one with only one OR the 2 branches have 2 -> remove the smallest
                    Segment remove = bEnds1.length==1 || (bEnds2.length==2 && b1.voxels.size()<b2.voxels.size()) ? b1 : b2;
                    remove.remove(true, true);
                    if (remove.connectedSegments.size()==2) remove.connectedSegments.stream().filter(b->!b.equals(junction)).findAny().get().relabel();
                    junction.relabel();
                    return true;
                }
            }
            case 0: {
                junction.remove(true, true);
                return true;
                //throw new RuntimeException("cannot clean unconnected junction");
            }
            default: { 
                // more than 2 branches: erase all but largest (most probably implicated in the largest shortest path) & smallest
                // this is valid in most cases but not striclty correct. Instead the 2 remaning branches should be the 2 implicated in the largest shortest path.// TODO algorithm to find distance between 2 segments
                // case :
                //String dbName = "MutH_140115";
                //int postition= 3, frame=409, mc=13, b=0;
                if (verbose) {
                    ImageWindowManagerFactory.showImage(draw(true).setName("neigh for clean junction: >2 branch connected to junction #"+junction.label));
                    ImageWindowManagerFactory.showImage(draw(false).setName("labels for clean junction: >2 branch connected to junction #"+junction.label));
                }
                Segment largest = junction.connectedSegments.stream().max((s1, s2)->Integer.compare(s1.voxels.size(), s2.voxels.size())).get();
                Segment smallest = junction.connectedSegments.stream().min((s1, s2)->Integer.compare(s1.voxels.size(), s2.voxels.size())).get();
                junction.connectedSegments.stream().filter(s->!s.equals(largest)&&!s.equals(smallest)).collect(Collectors.toList()).forEach(s->{
                    s.remove(true, true);
                    if (s.connectedSegments.size()==2) s.connectedSegments.stream().filter(b->!b.equals(junction)).findAny().get().relabel();
                });
                junction.relabel();
                return true;
                //throw new RuntimeException("cannot clean junction connected to more than 2 branches");
            }
        }
        // case junction with only 2 connected ends (from 1 or 2 different branches)
        // selection of minimal voxels allowing to link the two ends
        Voxel[] endsPropagation = new Voxel[] {branchEnds[0], branchEnds[1]};
        Set<Voxel> pool = new HashSet<>(junction.voxels); //remaining junction voxels
        propagate(branchEnds, endsPropagation, pool);
        while(!endsPropagation[0].equals(endsPropagation[1]) && !isTouching(endsPropagation[0], endsPropagation[1])) {
            if (pool.isEmpty()) throw new IllegalArgumentException("could not clean junction");
            propagate(branchEnds, endsPropagation, pool);
        }
        // remove all other voxels
        if (pool.isEmpty()) return false; // no cleaning was performed
        pool.stream().forEach(v->voxMapNeighAndLabels.remove(v));
        contours.removeAll(pool);
        junction.voxels.removeAll(pool);
        junction.relabel();
        return true;
    }
    private void propagate(Voxel[] branchEnds, Voxel[] ends, Set<Voxel> pool) {
        Voxel c1 = pool.stream().filter(v->isTouching(v, ends[0])).min((vox1, vox2)->Double.compare(branchEnds[1].getDistanceSquareXY(vox1), branchEnds[1].getDistanceSquareXY(vox2))).get(); // closest voxel to v2 in contact with v1
        pool.remove(c1);
        ends[0] = c1;
        if (c1.equals(ends[1])|| isTouching(c1, ends[1])) return;
        Voxel c2 = pool.stream().filter(v->isTouching(v, ends[1])).min((vox1, vox2)->Double.compare(branchEnds[0].getDistanceSquareXY(vox1), branchEnds[0].getDistanceSquareXY(vox2))).get(); // closest voxel to v1 in contact with v2
        pool.remove(c2);
        ends[1] = c2;
    }
    private boolean isTouching(Voxel v, Set<Voxel> other) {
        Voxel temp = new Voxel(0, 0, v.z);
        for (int i = 0; i<neigh.getSize(); ++i) {
            temp.x = v.x + neigh.dx[i];
            temp.y = v.y + neigh.dy[i];
            if (other.contains(temp)) return true;
        }
        return false;
    }
    private boolean isTouching(Voxel v, Voxel other) {
        Voxel temp = new Voxel(0, 0, v.z);
        for (int i = 0; i<neigh.getSize(); ++i) {
            temp.x = v.x + neigh.dx[i];
            temp.y = v.y + neigh.dy[i];
            if (other.equals(temp)) return true;
        }
        return false;
    }
    private int getNextLabel() {
        if (this.availableLabels.isEmpty()) {
            maxLabel++;
            return maxLabel;
        }
        return availableLabels.pollFirst();
    }
    private int computeNeighbors(Voxel v) {
        int count = 0;
        Voxel temp = new Voxel(0, 0, v.z);
        for (int i = 0; i<neigh.getSize(); ++i) {
            temp.x = v.x + neigh.dx[i];
            temp.y = v.y + neigh.dy[i];
            if (contours.contains(temp)) ++count;
        }
        return count;
    }
    private List<Set<Segment>> getAllSegmentClusters() {
        if (segments.isEmpty()) return Collections.EMPTY_LIST;
        List<Set<Segment>> res = new ArrayList<>();
        Set<Segment> remaniningSegments = new HashSet<>(segments.values());
        while(!remaniningSegments.isEmpty()) {
            if (verbose) logger.debug("get all clusters: {}/{}, cluster nb : {}", remaniningSegments.size(), segments.size(), res.size());
            Segment seed = remaniningSegments.stream().findAny().get();
            Set<Segment> cluster = getAllConnectedSegments(seed);
            remaniningSegments.removeAll(cluster);
            res.add(cluster);
        }
        return res;
    }
    private static Set<Segment> getAllConnectedSegments(Segment start) {
        Set<Segment> res = new HashSet<>();
        res.add(start);
        LinkedList<Segment> queue = new LinkedList<>();
        queue.addAll(start.connectedSegments);
        //logger.debug("get cluster start: visited: {}, queue: {}", res.size(), queue.size());
        while(!queue.isEmpty()) {
            //logger.debug("get cluster: visited: {}, queue: {}", res.size(), queue.size());
            Segment s = queue.pollFirst();
            if (res.contains(s)) continue;
            res.add(s);
            s.connectedSegments.stream().filter((ss) -> !(res.contains(ss))).forEach((ss) -> queue.add(ss));
        }
        return res;
    }
    private class Segment {
        boolean isJunction;
        Set<Segment> connectedSegments=new HashSet<>(); // junctions if !isJunction, branch else
        Set<Voxel> voxels = new HashSet<>();
        final int label;
        public Segment(int label, Voxel v) {
            this.label=label;
            this.isJunction = v.value>2;
            addVoxel(v);
        }
        public void addVoxel(Voxel v) {
            voxels.add(v);
            voxMapNeighAndLabels.get(v)[1] = label;
        }
        public Segment merge(Segment other) {
            if (other.label==this.label) return this;
            if (other.label>this.label) return other.merge(this);
            other.voxels.forEach(v->voxMapNeighAndLabels.get(v)[1] = label);
            voxels.addAll(other.voxels);
            segments.remove(other.label);
            availableLabels.add(other.label);
            for (Segment s : other.connectedSegments) {
                s.connectedSegments.add(this);
                s.connectedSegments.remove(other);
            }
            connectedSegments.addAll(other.connectedSegments);
            return this;
        }
        
        public void remove(boolean fromContour, boolean fromConnected) {
            if (fromConnected) connectedSegments.forEach((connected) -> connected.connectedSegments.remove(this));
            segments.remove(label);
            availableLabels.add(label);
            if (fromContour) {
                contours.removeAll(voxels);
                voxels.forEach(v-> voxMapNeighAndLabels.remove(v));
            } else {
                voxels.forEach(v-> {
                    int[] N_L = voxMapNeighAndLabels.get(v);
                    N_L[1] = 0;
                    N_L[0] = 0;
                });
            }
        }
        public void relabel() {
            remove(false, true);
            voxels.forEach(v-> { // recompute neighbors
                int n = computeNeighbors(v);
                v.value = n;
                voxMapNeighAndLabels.get(v)[0] = n;
            });
            voxels.forEach(v->label(v));
        }
        public Stream<Voxel> getTouchingVoxels(Segment other) {
            return voxels.stream().filter(v->isTouching(v, other.voxels));
        }
    }

    
}
