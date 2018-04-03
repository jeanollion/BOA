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
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;
import boa.image.processing.neighborhood.Neighborhood;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author Jean Ollion
 */
public class CleanContour {
    final Set<Voxel> contours;
    ImageInteger labels, neighbors;
    final EllipsoidalNeighborhood neigh = new EllipsoidalNeighborhood(1.5, true);
    Map<Integer, Segment> segments=new HashMap<>();
    final TreeSet<Integer> availableLabels = new TreeSet<>();
    int maxLabel = 0;
    public boolean verbose = false;
    public static Set<Voxel> cleanContour(Set<Voxel> contour) {
        return new CleanContour(contour, false).run();
    }
    public static Set<Voxel> cleanContour(Set<Voxel> contour, boolean verbose) {
        return new CleanContour(contour, verbose).run();
    }
    private CleanContour(Set<Voxel> contour, boolean verbose) {
        this.verbose=verbose;
        this.contours=contour;
        neighbors = new Region(contour, 1, true, 1, 1).getMaskAsImageInteger().setName("number of neighbors");
        labels = new ImageByte("labels", neighbors);
        contours.stream().forEach(v -> {
            int n = computeNeighbors(v);
            v.value = n;
            neighbors.setPixelWithOffset(v.x, v.y, v.z, n);
        });
        contours.stream().forEach(v->label(v));
        if (verbose) ImageWindowManagerFactory.showImage(labels.duplicate("labels before run"));
        if (verbose) ImageWindowManagerFactory.showImage(neighbors.duplicate("neighbors before run"));
    }
    
    
    public Set<Voxel> run() {
        // TODO check number of independent graphs are keep only largest
        
        if (segments.values().stream().filter(s->s.isJunction).findAny().orElse(null)==null) return contours;
        boolean change = true;
        while (change) {
            change = false;
            for (Segment s : segments.values()) {
                if (s.isJunction || s.connectedSegments.size()!=1) continue;
                s.removeSegment(true);
                Segment junction  = s.connectedSegments.stream().findAny().orElse(null);
                junction.removeSegment(false);
                junction.relabel();
                change = true;
                break;
            }
        }
        if (verbose) ImageWindowManagerFactory.showImage(labels.duplicate("labels after run"));
        if (verbose) ImageWindowManagerFactory.showImage(neighbors.duplicate("neighbors after run"));
        return contours;
    }
    private void label(Voxel v) {
        Voxel temp = new Voxel(0, 0, 0);
        int label = 0;
        for (int i = 0; i<neigh.getSize(); ++i) {
            temp.x = v.x + neigh.dx[i];
            temp.y = v.y + neigh.dy[i];
            if (!labels.containsWithOffset(temp.x, temp.y, temp.z)) continue;
            int l = labels.getPixelIntWithOffset(temp.x, temp.y, temp.z);
            if (l>0) {
                if (v.value>2 == neighbors.getPixelIntWithOffset(temp.x, temp.y, temp.z)>2) { // both are junction or both are branch
                    if (label == 0) { // add to segment
                        segments.get(l).addVoxel(v);
                        label = l;
                    } else { // merge segments
                        Segment res = segments.get(l).merge(segments.get(label));
                        label = res.label;
                    }
                } else { // add a link between current and other 
                    if (label==0) {
                        label = getNextLabel();
                        segments.put(label, new Segment(label, v));
                    }
                    Segment s1 = segments.get(l);
                    Segment s2 = segments.get(label);
                    s1.connectedSegments.add(s2);
                    s2.connectedSegments.add(s1);
                }
            }
        }
        if (label==0) {
            label = getNextLabel();
            segments.put(label, new Segment(label, v));
        }
        if (maxLabel==255) { // saturation
            ImageInteger newLabelImage = new ImageShort("labels", labels);
            BoundingBox.loop(labels.getBoundingBox().resetOffset(), (x, y, z)-> newLabelImage.setPixel(x, y, z, labels.getPixelInt(x, y, z)));
            labels = newLabelImage;
        }
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
        Voxel temp = new Voxel(0, 0, 0);
        for (int i = 0; i<neigh.getSize(); ++i) {
            temp.x = v.x + neigh.dx[i];
            temp.y = v.y + neigh.dy[i];
            if (contours.contains(temp)) ++count;
        }
        return count;
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
            labels.setPixelWithOffset(v.x, v.y, v.z, label);
        }
        public Segment merge(Segment other) {
            if (other.label==this.label) return this;
            if (other.label>this.label) return other.merge(this);
            other.voxels.forEach(v->labels.setPixelWithOffset(v.x, v.y, v.z, label));
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
        
        public void removeSegment(boolean fromContour) {
            connectedSegments.forEach((connected) -> connected.connectedSegments.remove(this));
            voxels.forEach(v-> {
                labels.setPixelWithOffset(v.x, v.y, v.z, 0);
                neighbors.setPixelWithOffset(v.x, v.y, v.z, 0);
            });
            segments.remove(label);
            availableLabels.add(label);
            if (fromContour) contours.removeAll(voxels);
        }
        public void relabel() {
            voxels.forEach(v-> {
                int n = computeNeighbors(v);
                v.value = n;
                neighbors.setPixelWithOffset(v.x, v.y, v.z, n);
            });
            voxels.forEach(v->label(v));
        }
    }

    
}
