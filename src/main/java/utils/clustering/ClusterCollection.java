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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.HashMapGetCreate;
import utils.Pair;
import utils.Utils;

/**
 *
 * @author jollion
 * @param <E> element data
 * @param <I> iterface data
 */
public class ClusterCollection<E, I extends Interface<E> > {
    public static boolean verbose;
    public static final Logger logger = LoggerFactory.getLogger(ClusterCollection.class);
    final Comparator<? super E> elementComparator;
    final Set<I> interfaces;
    final HashMapGetCreate<E, List<I>> elementInterfaces;
    Collection<E> allElements;
    InterfaceFactory<E, I> interfaceFactory;
    
    public ClusterCollection(Collection<E> elements, Comparator<? super E> clusterComparator, InterfaceFactory<E, I> interfaceFactory) {
        this.elementComparator=clusterComparator;
        elementInterfaces = new HashMapGetCreate<E, List<I>>(elements.size(), new HashMapGetCreate.ListFactory());
        this.allElements=elements;
        this.interfaces = new HashSet<I>();
        this.interfaceFactory=interfaceFactory;
    }
    
    /*public ClusterCollection(Collection<E> elements, Map<Pair<E, E>, I> interactions, Comparator<? super E> clusterComparator, InterfaceFactory<I> interfaceFactory) {
        this(elements, clusterComparator, interfaceFactory);
        for (Entry<Pair<E, E>, I> e : interactions.entrySet()) addInteraction(e.getKey().key, e.getKey().value, e.getValue());
    }*/
    
    public I addInteraction(I i) {
        interfaces.add(i);
        elementInterfaces.getAndCreateIfNecessary(i.getE1()).add(i);
        elementInterfaces.getAndCreateIfNecessary(i.getE2()).add(i);
        return i;
    }
    
    public I getInterface(E e1, E e2, boolean createIfNull) {
        List<I> l = elementInterfaces.getAndCreateIfNecessary(e1);
        for (I i : l) if (i.isInterfaceOf(e2)) return i;
        if (createIfNull && interfaceFactory!=null) return addInteraction(interfaceFactory.create(e1, e2, elementComparator));
        return null;
    }
    
    public List<Set<I>> getClusters() {
        List<Set<I>> clusters = new ArrayList<Set<I>>();
        Set<I> currentCluster;
        for (I i : interfaces) {
            currentCluster = null;
            List<I> l1 = elementInterfaces.getAndCreateIfNecessary(i.getE1());
            List<I> l2 = elementInterfaces.getAndCreateIfNecessary(i.getE2());
            if (clusters.isEmpty()) {
                currentCluster = new HashSet<I>(l1.size()+ l2.size()-1);
                currentCluster.addAll(l1);
                currentCluster.addAll(l2);
                clusters.add(currentCluster);
            } else {
                Iterator<Set<I>> it = clusters.iterator();
                while(it.hasNext()) {
                    Set<I> cluster = it.next();
                    if (cluster.contains(i)) {
                        cluster.addAll(l1);
                        cluster.addAll(l2);
                        if (currentCluster!=null) { // fusionInterface des clusters
                            currentCluster.addAll(cluster);
                            it.remove();
                        } else currentCluster=cluster;
                    }
                }
                if (currentCluster==null) {
                    currentCluster = new HashSet<I>(l1.size()+ l2.size()-1);
                    currentCluster.addAll(l1);
                    currentCluster.addAll(l2);
                    clusters.add(currentCluster);
                }
            }
        }
        return clusters;
    }
    
    /*public List<E> mergeSortCluster(Fusion<E, I> fusionInterface, InterfaceSortValue<E, I> interfaceSortValue) {
        List<Set<Interface<E, I>>> clusters = getClusters();
        // create one ClusterCollection per cluster and apply mergeSort
    }*/
    
    public List<E> mergeSort() {
        long t0 = System.currentTimeMillis();
        for (Interface i : interfaces) i.updateSortValue();
        List<I> currentInterfaces = new ArrayList<I>(interfaces);
        Collections.sort(currentInterfaces);
        //if (verbose) logger.debug("Tree set: {}, total: {}", currentInterfaces.size(), interfaces.size());
        Iterator<I> it = currentInterfaces.iterator(); // descending
        while (it.hasNext()) {
            I i = it.next();
            if (i.checkFusion(null)) {
                it.remove();
                this.interfaces.remove(i);
                allElements.remove(i.getE2());
                i.performFusion();
                if (updateInterfacesAfterFusion(i, currentInterfaces)) { // if any change in the interface treeset, recompute the iterator
                    Collections.sort(currentInterfaces);
                    it=currentInterfaces.iterator();
                } 
            } //else if (i.hasOneRegionWithNoOtherInteractant(this)) it.remove(); // won't be modified so no need to test once again
        }
        long t1 = System.currentTimeMillis();
        if (verbose) logger.debug("Merge sort: total time : {} total interfaces: {} after merge: {}", t1-t0, interfaces.size(), currentInterfaces.size());
        return new ArrayList<E>(elementInterfaces.keySet());
    }   

    /**
     * 
     * @param i
     * @return true if changes were made in the interfaces set
     */
    protected boolean updateInterfacesAfterFusion(I i, List<I> interfaces) {
        List<I> l1 = elementInterfaces.get(i.getE1());
        List<I> l2 = elementInterfaces.remove(i.getE2());
        if (l1!=null) l1.remove(i);
        boolean change = false;
        if (l2!=null) {
            for (I otherInterface : l2) { // appends interfaces of deleted region (e2) to new region (e1)
                if (!otherInterface.equals(i)) {
                    change=true;
                    
                    E otherElement = otherInterface.getOther(i.getE2());
                    I existingInterface=null;
                    if (l1!=null) {
                        for (I j : l1) {
                            if (j.isInterfaceOf(i.getE1(), otherElement)) {
                                existingInterface=j;
                                break;
                            }
                        }
                    }
                    if (existingInterface!=null) { // if interface is already present in e1, simply merge the interfaces
                        //interfaces.remove(existingInterface);
                        //if (l1!=null) l1.remove(existingInterface);
                        //otherRegionInterfaces.remove(existingInterface);
                        existingInterface.fusionInterface(otherInterface, elementComparator);
                        
                        interfaces.remove(otherInterface);
                        this.interfaces.remove(otherInterface);
                        elementInterfaces.get(otherElement).remove(existingInterface);
                    } else { // if not add a new interface
                        //otherRegionInterfaces.remove(otherInterface);
                        otherInterface.swichElements(i.getE1(), i.getE2(), elementComparator);
                        if (l1!=null) l1.add(otherInterface);
                    }
                }
            }
        }
        return change;
    }
    

    public static interface InterfaceFactory<E, I extends Interface<E>> {
        public I create(E e1, E e2, Comparator<? super E> elementComparator);
    }
    
}
