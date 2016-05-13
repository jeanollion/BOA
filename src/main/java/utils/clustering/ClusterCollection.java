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
public class ClusterCollection<E, I> {
    public static boolean verbose;
    public static final Logger logger = LoggerFactory.getLogger(ClusterCollection.class);
    final Comparator<? super E> elementComparator;
    final Set<Interface<E, I>> interfaces;
    final HashMapGetCreate<E, List<Interface<E, I>>> elementInterfaces;
    Collection<E> allElements;
    InterfaceDataFactory<I> interfaceDataFactory;
    public ClusterCollection(Collection<E> elements, Comparator<? super E> clusterComparator, InterfaceDataFactory<I> interfaceDataFactory) {
        this.elementComparator=clusterComparator;
        elementInterfaces = new HashMapGetCreate<E, List<Interface<E, I>>>(elements.size(), new HashMapGetCreate.ListFactory());
        this.allElements=elements;
        this.interfaces = new HashSet<Interface<E, I>>();
        this.interfaceDataFactory=interfaceDataFactory;
    }
    
    public ClusterCollection(Collection<E> elements, Map<Pair<E, E>, I> interactions, Comparator<? super E> clusterComparator, InterfaceDataFactory<I> interfaceDataFactory) {
        this(elements, clusterComparator, interfaceDataFactory);
        for (Entry<Pair<E, E>, I> e : interactions.entrySet()) addInteraction(e.getKey().key, e.getKey().value, e.getValue());
    }
    
    public Interface<E, I> addInteraction(E e1, E e2, I interaction) {
        Interface<E, I> i = new Interface<E, I>(e1, e2, interaction, elementComparator);
        interfaces.add(i);
        elementInterfaces.getAndCreateIfNecessary(e1).add(i);
        elementInterfaces.getAndCreateIfNecessary(e2).add(i);
        return i;
    }
    
    public Interface<E, I> getInterface(E e1, E e2, boolean createIfNull) {
        List<Interface<E, I>> l = elementInterfaces.getAndCreateIfNecessary(e1);
        for (Interface<E, I> i : l) if (i.isInterfaceOf(e2)) return i;
        if (createIfNull && interfaceDataFactory!=null) return addInteraction(e1, e2, interfaceDataFactory.create());
        return null;
    }
    
    public List<Set<Interface<E, I>>> getClusters() {
        List<Set<Interface<E, I>>> clusters = new ArrayList<Set<Interface<E, I>>>();
        Set<Interface<E, I>> currentCluster;
        for (Interface<E, I> i : interfaces) {
            currentCluster = null;
            List<Interface<E, I>> l1 = elementInterfaces.getAndCreateIfNecessary(i.e1);
            List<Interface<E, I>> l2 = elementInterfaces.getAndCreateIfNecessary(i.e2);
            if (clusters.isEmpty()) {
                currentCluster = new HashSet<Interface<E, I>>(l1.size()+ l2.size()-1);
                currentCluster.addAll(l1);
                currentCluster.addAll(l2);
                clusters.add(currentCluster);
            } else {
                Iterator<Set<Interface<E, I>>> it = clusters.iterator();
                while(it.hasNext()) {
                    Set<Interface<E, I>> cluster = it.next();
                    if (cluster.contains(i)) {
                        cluster.addAll(l1);
                        cluster.addAll(l2);
                        if (currentCluster!=null) { // fusion des clusters
                            currentCluster.addAll(cluster);
                            it.remove();
                        } else currentCluster=cluster;
                    }
                }
                if (currentCluster==null) {
                    currentCluster = new HashSet<Interface<E, I>>(l1.size()+ l2.size()-1);
                    currentCluster.addAll(l1);
                    currentCluster.addAll(l2);
                    clusters.add(currentCluster);
                }
            }
        }
        return clusters;
    }
    
    /*public List<E> mergeSortCluster(Fusion<E, I> fusion, InterfaceSortValue<E, I> interfaceSortValue) {
        List<Set<Interface<E, I>>> clusters = getClusters();
        // create one ClusterCollection per cluster and apply mergeSort
    }*/
    
    public List<E> mergeSort(Fusion<E, I> fusion, InterfaceSortMethod<E, I> interfaceSortMethod) {
        for (Interface i : interfaces) i.updateSortValue(interfaceSortMethod);
        List<Interface<E, I>> currentInterfaces = new ArrayList<Interface<E, I>>(interfaces);
        Collections.sort(currentInterfaces);
        if (verbose) logger.debug("Tree set: {}, total: {}", currentInterfaces.size(), interfaces.size());
        Iterator<Interface<E, I>> it = currentInterfaces.iterator(); // descending
        while (it.hasNext()) {
            Interface<E, I> i = it.next();
            if (verbose) logger.debug("Interface: {}+{}", i.e1, i.e2);
            if (fusion.checkFusion(i)) {
                it.remove();
                this.interfaces.remove(i);
                allElements.remove(i.e2);
                fusion.performFusion(i);
                if (updateInterfacesAfterFusion(i, currentInterfaces, fusion, interfaceSortMethod)) { // if any change in the interface treeset, recompute the iterator
                    Collections.sort(currentInterfaces);
                    it=currentInterfaces.iterator();
                } 
            } //else if (i.hasOneRegionWithNoOtherInteractant(this)) it.remove(); // won't be modified so no need to test once again
        }
        return new ArrayList<E>(elementInterfaces.keySet());
    }   

    /**
     * 
     * @param i
     * @return true if changes were made in the interfaces set
     */
    protected boolean updateInterfacesAfterFusion(Interface<E, I> i, List<Interface<E, I>> interfaces, Fusion<E, I> fusion, InterfaceSortMethod<E, I> interfaceSortValue) {
        List<Interface<E, I>> l1 = elementInterfaces.get(i.e1);
        List<Interface<E, I>> l2 = elementInterfaces.remove(i.e2);
        if (l1!=null) l1.remove(i);
        boolean change = false;
        if (l2!=null) {
            for (Interface<E, I> otherInterface : l2) { // appends interfaces of deleted region (e2) to new region (e1)
                if (!otherInterface.equals(i)) {
                    change=true;
                    interfaces.remove(otherInterface);
                    E otherRegion = otherInterface.getOther(i.e2);
                    List<Interface<E, I>> otherRegionInterfaces = elementInterfaces.get(otherRegion);
                    Interface<E, I> existingInterface=null;
                    if (l1!=null) {
                        for (Interface<E, I> j : l1) {
                            if (j.isInterfaceOf(i.e1, otherRegion)) {
                                existingInterface=j;
                                break;
                            }
                        }
                    }
                    if (existingInterface!=null) { // if interface is already present in e1, simply merge the interfaces
                        interfaces.remove(existingInterface);
                        if (l1!=null) l1.remove(existingInterface);
                        otherRegionInterfaces.remove(existingInterface);
                        Interface<E, I> newInterface = fusion.fusion(existingInterface, otherInterface);
                        newInterface.updateSortValue(interfaceSortValue);
                        interfaces.add(newInterface);
                        otherRegionInterfaces.add(newInterface);
                        if (l1!=null) l1.add(newInterface);
                    } else { // if not add a new interface
                        otherRegionInterfaces.remove(otherInterface);
                        Interface<E, I> newInterface = new Interface<E, I>(i.e1, otherRegion, otherInterface.data, elementComparator);
                        if (l1!=null) l1.add(newInterface);
                        newInterface.updateSortValue(interfaceSortValue);
                        interfaces.add(newInterface);
                        otherRegionInterfaces.add(newInterface);
                    }
                }
            }
        }
        return change;
    }
    
    public interface Fusion<E, I> {
        public boolean checkFusion(Interface<E, I> i); // returns true if the fusion criterion is reached
        public void performFusion(Interface<E, I> i); // update i.e1;
        public Interface<E, I> fusion(Interface<E, I> i1, Interface<E, I> i2);
    }
    public static abstract class FusionImpl<E, I> implements Fusion<E, I> {
        final InterfaceDataFusion<I> interfaceDataFusion;
        final Comparator<? super E> elementComparator;
        public FusionImpl(InterfaceDataFusion<I> interfaceDataFusion, Comparator<? super E> elementComparator) {
            this.interfaceDataFusion=interfaceDataFusion;
            this.elementComparator=elementComparator;
        }
        @Override public Interface<E, I> fusion(Interface<E, I> i1, Interface<E, I> i2) {
            E com = i1.getCommonElement(i2);
            if (com==null) throw new IllegalArgumentException("No common elements in "+i1+" and "+i2+" cannot merge");
            E o1 = i1.getOther(com);
            E o2 = i2.getOther(com);
            return new Interface<E, I>(o1, o2, interfaceDataFusion.fusion(i1.data, i2.data), elementComparator);
        }
    }
    public interface InterfaceDataFusion<I> {
        public I fusion(I i1, I i2);
    }
    public interface InterfaceDataFactory<I> {
        public I create();
    }
    public static class InterfaceDataFusionCollection<C extends Collection<T>, T> implements InterfaceDataFusion<C> {
        // modifies i1;
        public C fusion(C i1, C i2) {
            i1.addAll(i2);
            return i1;
        }
    }
    public interface InterfaceSortMethod<E, I> {
        public double computeSortValue(Interface<E, I> i);
    }
}
