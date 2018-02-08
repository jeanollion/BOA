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
package boa.image.processing.clustering;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.data_structure.Voxel;
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
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import boa.utils.HashMapGetCreate;
import boa.utils.Pair;
import boa.utils.Utils;
import java.util.function.Predicate;

/**
 *
 * @author jollion
 * @param <E> element data
 * @param <I> iterface data
 */
public class ClusterCollection<E, I extends Interface<E, I> > {
    public static boolean verbose;
    public static final Logger logger = LoggerFactory.getLogger(ClusterCollection.class);
    final Comparator<? super E> elementComparator;
    Set<I> interfaces;
    final HashMapGetCreate<E, Set<I>> interfaceByElement;
    Collection<E> allElements;
    InterfaceFactory<E, I> interfaceFactory;
    
    public ClusterCollection(Collection<E> elements, Comparator<? super E> clusterComparator, InterfaceFactory<E, I> interfaceFactory) {
        this.elementComparator=clusterComparator;
        interfaceByElement = new HashMapGetCreate<E, Set<I>>(elements.size(), new HashMapGetCreate.SetFactory());
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
        interfaceByElement.getAndCreateIfNecessary(i.getE1()).add(i);
        interfaceByElement.getAndCreateIfNecessary(i.getE2()).add(i);
        return i;
    }
    
    public Set<I> getInterfaces(Collection<E> elements) {
        Set<I> res = new HashSet<>();
        elements.stream().filter((e) -> (interfaceByElement.containsKey(e))).forEach((e) -> {
            res.addAll(interfaceByElement.get(e));
        });
        return res;
    }
    public Set<I> getAllInterfaces() {
        return interfaces;
    }
    
    public Set<I> getInterfaces(E e) {
        if (interfaceByElement.containsKey(e)) return interfaceByElement.get(e);
        else return Collections.EMPTY_SET;
    }
    
    public Set<E> getInteractants(E e) {
        Set<I> inter =getInterfaces(e);
        Set<E> res = new HashSet<>(inter.size());
        for (I i : inter) res.add(i.getOther(e));
        return res;
    }
    
    public I getInterface(E e1, E e2, boolean createIfNull) {
        Collection<I> l = interfaceByElement.getAndCreateIfNecessary(e1);
        for (I i : l) if (i.isInterfaceOf(e2)) return i;
        if (createIfNull && interfaceFactory!=null) return addInteraction(interfaceFactory.create(e1, e2, elementComparator));
        return null;
    }
    
    public List<Set<E>> getClusters() {
        List<Set<I>> interfaceClusters = new ArrayList<>();
        Set<I> currentCluster;
        //logger.debug("get interfaceClusters: # of interfaces {}", interfaces.size());
        for (I i : interfaces) {
            currentCluster = null;
            Collection<I> l1 = interfaceByElement.getAndCreateIfNecessary(i.getE1());
            Collection<I> l2 = interfaceByElement.getAndCreateIfNecessary(i.getE2());
            if (interfaceClusters.isEmpty()) {
                currentCluster = new HashSet<>(l1.size()+ l2.size()-1);
                currentCluster.addAll(l1);
                currentCluster.addAll(l2);
                interfaceClusters.add(currentCluster);
            } else {
                Iterator<Set<I>> it = interfaceClusters.iterator();
                while(it.hasNext()) {
                    Set<I> cluster = it.next();
                    if (cluster.contains(i)) {
                        cluster.addAll(l1);
                        cluster.addAll(l2);
                        if (currentCluster!=null) { // fusionInterface des interfaceClusters
                            currentCluster.addAll(cluster);
                            it.remove();
                        } else currentCluster=cluster;
                    }
                }
                if (currentCluster==null) {
                    currentCluster = new HashSet<I>(l1.size()+ l2.size());
                    currentCluster.addAll(l1);
                    currentCluster.addAll(l2);
                    interfaceClusters.add(currentCluster);
                }
            }
        }
        // creation des clusters d'objets 
        List<Set<E>> clusters = new ArrayList<>();
        for (Set<I> iSet : interfaceClusters) {
            Set<E> eSet = new HashSet<>();
            for (I i : iSet) {
                eSet.add(i.getE1());
                eSet.add(i.getE2());
            }
            clusters.add(eSet);
        }
        // ajout des elements isolés
        for (E e : allElements) if (!interfaceByElement.containsKey(e) || interfaceByElement.get(e).isEmpty()) clusters.add(new HashSet<E>(){{add(e);}});
        return clusters;
    }
    
    
    
    /*public List<E> mergeSortCluster(Fusion<E, I> fusionInterface, InterfaceSortValue<E, I> interfaceSortValue) {
        List<Set<Interface<E, I>>> interfaceClusters = getClusters();
        // create one ClusterCollection per cluster and apply mergeSort
    }*/
    Predicate<I> forbidFusion = null;
    public void addForbidFusionPredicate(Predicate<I> forbidFusion) {
        if (forbidFusion==null) return;
        if (this.forbidFusion!=null) this.forbidFusion = this.forbidFusion.or(forbidFusion);
        else this.forbidFusion=forbidFusion;
    }
    public List<E> mergeSort(boolean checkCriterion, int numberOfInterfacesToKeep, int numberOfElementsToKeep) {
        if (verbose) logger.debug("MERGE SORT check: {}, interfacesToKeep: {}, elements to keep: {}", checkCriterion, numberOfInterfacesToKeep, numberOfElementsToKeep);
        long t0 = System.currentTimeMillis();
        for (I i : interfaces) i.updateInterface();
        int interSize = interfaces.size();
        interfaces = new TreeSet(interfaces);
        if (verbose) {
            for (I i : interfaces) logger.debug("interface: {}", i);
            for (E e : interfaceByElement.keySet()) logger.debug("Element: {}, interfaces: {}", e, interfaceByElement.get(e));
        }
        if (interSize!=interfaces.size()) throw new RuntimeException("Error INCONSITENCY BETWEEN COMPARE AND EQUALS METHOD FOR INTERFACE CLASS: "+interfaces.iterator().next().getClass().getSimpleName());
        //List<I> interfaces = new ArrayList<>(this.interfaces);
        //Collections.sort(interfaces);
        Iterator<I> it = interfaces.iterator();
        while (it.hasNext() && interfaces.size()>numberOfInterfacesToKeep && allElements.size()>numberOfElementsToKeep) {
            I i = it.next();
            if (forbidFusion!=null && forbidFusion.test(i)) continue; // do not remove interface as the test could change after fusions
            if (!checkCriterion || i.checkFusion() ) {
                if (verbose) logger.debug("fusion {}", i);
                it.remove();
                allElements.remove(i.getE2());
                i.performFusion();
                if (updateInterfacesAfterFusion(i, interfaces)) { // if any change in the interface treeset, recompute the iterator
                    //Collections.sort(interfaces);
                    it=interfaces.iterator();
                } 
                if (false && verbose) {
                    logger.debug("bilan/");
                    for (I ii : interfaces) logger.debug("interface: {}", ii);
                    for (E e : interfaceByElement.keySet()) logger.debug("Element: {}, interfaces: {}", e, interfaceByElement.get(e));
                    logger.debug("/bilan");
                }
            } //else if (i.hasOneRegionWithNoOtherInteractant(this)) it.remove(); // won't be modified so no need to test once again
        }
        long t1 = System.currentTimeMillis();
        if (verbose) logger.debug("Merge sort: total time : {} total interfaces: {} after merge: {}", t1-t0, interfaces.size(), interfaces.size());
        
        return new ArrayList<>(interfaceByElement.keySet());
    }   
    protected void removeInterface(I i) {
        Collection<I> l1 = interfaceByElement.get(i.getE1());
        Collection<I> l2 = interfaceByElement.remove(i.getE2());
        if (l1!=null) l1.remove(i);
        if (l2!=null) l2.remove(i);
    }
    /**
     * Update all connected interface after a fusion
     * @param i
     * @return true if changes were made in the interfaces set
     */
    protected boolean updateInterfacesAfterFusion(I i, Collection<I> interfaces) {
        Collection<I> l1 = interfaceByElement.get(i.getE1());
        Collection<I> l2 = interfaceByElement.remove(i.getE2());
        boolean change = false;
        
        if (l2!=null) {
            for (I otherInterface : l2) { // appends interfaces of deleted region (e2) to new region (e1)
                if (!otherInterface.equals(i)) {
                    change = true;
                    E otherElement = otherInterface.getOther(i.getE2());
                    I existingInterface=null;
                    if (l1!=null) existingInterface = Utils.getFirst(l1, j->j.isInterfaceOf(i.getE1(), otherElement)); // look for existing interface between e1 and otherElement
                    if (existingInterface!=null) { // if interface is already present in e1, simply merge the interfaces
                        if (verbose) logger.debug("merge {} with {}", existingInterface, otherInterface);
                        remove(interfaces, otherInterface);
                        if (interfaces instanceof Set) remove(interfaces, existingInterface);// sort value will change 
                        Collection<I> otherInterfaces = interfaceByElement.get(otherElement);
                        if (otherInterfaces!=null) otherInterfaces.remove(otherInterface); // will be replaced by existingInterface
                        
                        existingInterface.fusionInterface(otherInterface, elementComparator);
                        existingInterface.updateInterface();
                        if (interfaces instanceof Set) interfaces.add(existingInterface); // sort value changed 
                        // no need to add and remove from interfaces of e1 and otherElement beause hashCode hasnt changed
                        
                    } else { // if not add a new interface between E1 and otherElement
                        if (verbose) logger.debug("switch {}", otherInterface);
                        if (interfaces instanceof Set) remove(interfaces,otherInterface); // hashCode will change because of switch
                        Collection<I> otherInterfaces = interfaceByElement.get(otherElement);
                        if (otherInterfaces!=null) otherInterfaces.remove(otherInterface); // hashCode will change because of switch
                        
                        otherInterface.swichElements(i.getE1(), i.getE2(), elementComparator);
                        otherInterface.updateInterface(); // TODO should be called in the method only if necessary, depending on interface ? 
                        if (interfaces instanceof Set) interfaces.add(otherInterface);
                        if (otherInterfaces!=null) otherInterfaces.add(otherInterface);
                        if (l1!=null) l1.add(otherInterface);
                    }
                }
            }
        }
        if (l1!=null) {  // e1 has change so update all his interfaces // perform updates after removing interfaces, if not may not be removed
            l1.remove(i);
            if (!l1.isEmpty()) change = true; 
            for (I otherI : l1) otherI.updateInterface();
        }
        return change;
    }
    private static  <I> void remove(Collection<I> col, I i) {
        boolean r = col.remove(i);
        if (!r) col.removeIf(in->in.equals(i));
    }

    public static interface InterfaceFactory<E, T extends Interface<E, T>> {
        public T create(E e1, E e2, Comparator<? super E> elementComparator);
    }
    
}
