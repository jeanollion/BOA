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
package utils.clustering;

import dataStructure.objects.Voxel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import utils.Pair;
import utils.Utils;

/**
 *
 * @author jollion
 * @param <E> element data
 * @param <I> iterface data
 */
public class ClusterCollection<E, I> {
    Comparator<? super E> elementComparator;
    Set<Interface> interfaces;
    List<Element> elements;
    
    public ClusterCollection(Collection<E> elements, Comparator<? super E> clusterComparator) {
        this.elementComparator=clusterComparator;
        this.elements = new ArrayList<Element>(elements.size());
        Utils.removeDuplicates(this.elements, false);
        for (E e : elements) this.elements.add(new Element(e));
        this.interfaces = new HashSet<Interface>();
    }
    
    public ClusterCollection(Collection<E> elements, Map<Pair<E, E>, I> interactions, Comparator<? super E> clusterComparator) {
        this(elements, clusterComparator);
        for (Entry<Pair<E, E>, I> e : interactions.entrySet()) addInteraction(e.getKey().key, e.getKey().value, e.getValue());
    }
    
    public void addInteraction(E e1, E e2, I interaction) {
        int idx1 = this.elements.indexOf(e1);
        if (idx1<0) throw new IllegalArgumentException("Element: "+e1.toString()+" present in interfaces and not in elements");
        int idx2 = this.elements.indexOf(e2);
        if (idx2<0) throw new IllegalArgumentException("Element: "+e2.toString()+" present in interfaces and not in elements");
        Interface<E, I> i = new Interface<E, I>(this.elements.get(idx1), this.elements.get(idx2), interaction, elementComparator);
        interfaces.add(i);
        i.e1.interfaces.add(i);
        i.e2.interfaces.add(i);
    }
    
    public List<Set<Interface>> getClusters() {
        ArrayList<Set<Interface>> clusters = new ArrayList<Set<Interface>>();
        Set<Interface> currentCluster;
        for (Interface i : interfaces) {
            currentCluster = null;
            if (clusters.isEmpty()) {
                currentCluster = new HashSet<Interface>(i.e1.interfaces.size()+ i.e2.interfaces.size()-1);
                currentCluster.addAll(i.e1.interfaces);
                currentCluster.addAll(i.e2.interfaces);
                clusters.add(currentCluster);
            } else {
                Iterator<Set<Interface>> it = clusters.iterator();
                while(it.hasNext()) {
                    Set<Interface> cluster = it.next();
                    if (cluster.contains(i)) {
                        cluster.addAll(i.e1.interfaces);
                        cluster.addAll(i.e2.interfaces);
                        if (currentCluster!=null) { // fusion des clusters
                            currentCluster.addAll(cluster);
                            it.remove();
                        } else currentCluster=cluster;
                    }
                }
                if (currentCluster==null) {
                    currentCluster = new HashSet<Interface>(i.e1.interfaces.size()+ i.e2.interfaces.size()-1);
                    currentCluster.addAll(i.e1.interfaces);
                    currentCluster.addAll(i.e2.interfaces);
                    clusters.add(currentCluster);
                }
            }
        }
        return clusters;
    }
    
}
