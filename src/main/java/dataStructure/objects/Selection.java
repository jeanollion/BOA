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
package dataStructure.objects;

import static boa.gui.selection.SelectionUtils.colors;
import static boa.gui.selection.SelectionUtils.colorsImageDisplay;
import com.google.common.collect.Sets;
import static dataStructure.objects.StructureObject.logger;
import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Id;
import de.caluga.morphium.annotations.Transient;
import de.caluga.morphium.annotations.lifecycle.Lifecycle;
import de.caluga.morphium.annotations.lifecycle.PostLoad;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import utils.Utils;

/**
 *
 * @author jollion
 */
@Entity
public class Selection implements Comparable<Selection> {
    @Id String id;
    int structureIdx;
    Map<String, List<String>> elements; // stored as list for simplicity
    String color="Green";
    boolean displayingTracks=false;
    boolean displayingObjects=false;
    boolean highlightingTracks=false;
    
    @Transient public final static String indexSeparator ="-";
    @Transient Map<String, Set<StructureObject>> retrievedElements= new HashMap<>();
    @Transient Map<String, Set<StructureObject>> retrievedTrackHeads = new HashMap<>();
    @Transient MasterDAO mDAO;
    @Transient boolean elementsTypeChecked=false;
    
    public Selection(String name, MasterDAO mDAO) {
        this.id=name;
        this.structureIdx=-1;
        elements = new HashMap<String, List<String>>();
        this.mDAO=mDAO;
    }
    public Set<String> getAllPositions() {
        return elements.keySet();
    }
    public String getNextPosition(String position, boolean next) {
        List<String> p = new ArrayList<>(elements.keySet());
        Collections.sort(p);
        int idx = p.indexOf(position) + (next?1:-1);
        if (idx==-1 || idx==p.size()) return null;
        else return p.get(idx);
    }
    
    public Color getColor(boolean imageDisplay) {
        if (imageDisplay) return colorsImageDisplay.get(color);
        else return colors.get(color);
    }
    
    public boolean isDisplayingTracks() {
        return displayingTracks;
    }
    
    public void setIsDisplayingTracks(boolean displayingTracks) {
        this.displayingTracks=displayingTracks;
    }
    
    public boolean isDisplayingObjects() {
        return displayingObjects;
    }
    
    public void setIsDisplayingObjects(boolean displayingObjects) {
        this.displayingObjects=displayingObjects;
    }

    public boolean isHighlightingTracks() {
        return highlightingTracks;
    }

    public void setHighlightingTracks(boolean highlightingTracks) {
        this.highlightingTracks = highlightingTracks;
    }
    
    public void setColor(String color) {
        this.color=color;
    }
    
    protected void setMasterDAO(MasterDAO mDAO) {
        this.mDAO=mDAO;
    }
    
    public int getStructureIdx() {
        return structureIdx;
    }
    
    public Set<StructureObject> getAllElements() {
        Set<StructureObject> res = new HashSet<StructureObject>();
        for (String f : elements.keySet()) res.addAll(getElements(f));
        return res;
    }
    
    public Set<StructureObject> getElements(String fieldName) {
        Set<StructureObject> res =  retrievedElements.get(fieldName);
        if (res==null && elements.containsKey(fieldName)) {
            synchronized(retrievedElements) {
                res =  retrievedElements.get(fieldName);
                if (res==null) return retrieveElements(fieldName);
                else return res;
            }
        }
        return res;
    }
    
    public Set<StructureObject> getTrackHeads(String fieldName) {
        Set<StructureObject> res = this.retrievedTrackHeads.get(fieldName);
        if (res==null) {
            Set<StructureObject> els = getElements(fieldName);
            if (els!=null) {
                res = new HashSet<StructureObject>(els.size());
                for (StructureObject o : els) res.add(o.getTrackHead());
                retrievedTrackHeads.put(fieldName, res);
            }
        }
        return res;
    }
    protected Collection<String> get(String fieldName, boolean createIfNull) {
        Object indiciesList = elements.get(fieldName);
        if (indiciesList==null) {
            if (createIfNull) {
                synchronized(elements) {
                    indiciesList = elements.get(fieldName);
                    if (indiciesList==null) {
                        indiciesList = new ArrayList<String>();
                        elements.put(fieldName, (List)indiciesList);
                    }
                }
            } else return null;
        }
        else if (indiciesList instanceof Set) { // retro-compatibility
            indiciesList = new ArrayList<String>((Set)indiciesList);
            elements.put(fieldName, (List)indiciesList);
        } else if (indiciesList instanceof String) { // case of one single object stored by R
            ArrayList<String> l = new ArrayList<String>();
            l.add((String)indiciesList);
            elements.put(fieldName, l);
            return l;
        }
        return (Collection<String>)indiciesList;
    }
    protected synchronized Set<StructureObject> retrieveElements(String fieldName) {
        if (fieldName==null) throw new IllegalArgumentException("FieldName cannot be null");
        Collection<String> indiciesList = get(fieldName, false);
        if (indiciesList==null) {
            return null;
        }
        ObjectDAO dao = mDAO.getDao(fieldName);
        int[] pathToRoot = mDAO.getExperiment().getPathToRoot(structureIdx);
        Set<StructureObject> res = new HashSet<StructureObject>(indiciesList.size());
        retrievedElements.put(fieldName, res);
        retrievedTrackHeads.remove(fieldName);
        List<StructureObject> roots = dao.getRoots();
        long t0 = System.currentTimeMillis();
        for (String s : indiciesList) {
            int[] indicies = parseIndicies(s);
            if (indicies.length-1!=pathToRoot.length) {
                logger.warn("Selection: Object: {} has wrong number of indicies (expected: {})", indicies, pathToRoot.length);
                continue;
            }
            StructureObject elem = getObject(indicies, pathToRoot, roots);
            if (elem!=null) res.add(elem);
            else logger.warn("Selection: object not found: {}", indicies);
        }
        long t1 = System.currentTimeMillis();
        logger.debug("Selection: {}, #{} elements retrieved in: {}", this.id, res.size(), t1-t0);
        return res;
    }
    
    private StructureObject getObject(int[] indices, int[] pathToRoot, List<StructureObject> roots) {
        if (roots==null || roots.size()<=indices[0]) return null;
        StructureObject elem = roots.get(indices[0]);
        for (int i= 1; i<indices.length; ++i) {
            /*if (elem.getChildren(pathToRoot[i-1]).size()<=indices[i]) {
                logger.warn("Selection: Object: {} was not found @ idx {}, last parent: {}", indices, i, elem);
                return null;
            }
            elem = elem.getChildren(pathToRoot[i-1]).get(indices[i]);*/
            elem = getChild(elem.getChildren(pathToRoot[i-1]), indices[i]); // in case relabel was not performed -> safer method but slower
            if (elem == null) {
                //logger.warn("Selection: Object: {} was not found @ idx {}", indices, i);
                return null;
            }
        }
        return elem;
    }
    private static StructureObject getChild(List<StructureObject> list, int idx) {
        for (StructureObject o : list) if (o.getIdx()==idx) return o;
        return null;
    }
    
    public static int[] parseIndicies(String indicies) {
        String[] split = indicies.split(indexSeparator);
        int[] res = new int[split.length];
        for (int i = 0; i<res.length; ++i) res[i] = Integer.parseInt(split[i]);
        return res;
    }
    
    public static String indiciesToString(int[] indicies) {
        return Utils.toStringArray(indicies, "", "", indexSeparator);
    }
    
    public synchronized void updateElementList(String fieldName) {
        if (fieldName==null) throw new IllegalArgumentException("FieldName cannot be null");
        Set<StructureObject> objectList = retrievedElements.get(fieldName);
        if (objectList==null) {
            elements.remove(fieldName);
            return;
        }
        Collection<String> indiciesList = get(fieldName, true);
        indiciesList.clear();
        for (StructureObject o : objectList) indiciesList.add(indiciesToString(StructureObjectUtils.getIndexTree(o)));
    }
    
    public void addElement(StructureObject elementToAdd) {
        if (this.structureIdx==-1) structureIdx=elementToAdd.getStructureIdx();
        else if (structureIdx!=elementToAdd.getStructureIdx()) return;
        
        Set<StructureObject> list = getElements(elementToAdd.getFieldName());
        if (list==null) {
            list=new HashSet<StructureObject>();
            retrievedElements.put(elementToAdd.getFieldName(), list);
        }
        if (!list.contains(elementToAdd)) {
            list.add(elementToAdd);
            // update trackHeads
            if (retrievedTrackHeads.containsKey(elementToAdd.getFieldName())) {
                Set<StructureObject> th = this.getTrackHeads(elementToAdd.getFieldName());
                if (!th.contains(elementToAdd.getTrackHead())) th.add(elementToAdd.getTrackHead());
            }
            // update DB refs
            Collection<String> els = get(elementToAdd.getFieldName(), true);
            els.add(indiciesToString(StructureObjectUtils.getIndexTree(elementToAdd)));
            if (false && els.size()!=list.size()) {
                logger.error("unconsitancy in selection: {}, {} vs: {}", this.toString(), list.size(), els.size());
                if (els.size()<=10) {
                    for (StructureObject o : list) logger.debug("bact: {},  object: {}", StructureObjectUtils.getIndexTree(o), o);
                    for (String elt : els) logger.debug("elt: {}", elt);
                }
            }
        }
    }
    public synchronized void addElements(Collection<StructureObject> elementsToAdd) {
        if (elementsToAdd==null || elementsToAdd.isEmpty()) return;
        for (StructureObject o : elementsToAdd) addElement(o);
    }
    
    public boolean removeElement(StructureObject elementToRemove) {
        Set<StructureObject> list = getElements(elementToRemove.getFieldName());
        if (list!=null) {
            list.remove(elementToRemove);
            String ref= indiciesToString(StructureObjectUtils.getIndexTree(elementToRemove));
            Collection<String> els = get(elementToRemove.getFieldName(), false);
            if (els!=null) els.remove(ref);
        }
        return false;
    }
    public synchronized void removeElements(List<StructureObject> elementsToRemove) {
        if (elementsToRemove==null || elementsToRemove.isEmpty()) return;
        for (StructureObject o : elementsToRemove) removeElement(o);
    }
    public synchronized void removeChildrenOf(List<StructureObject> parents) {
        Map<String, List<StructureObject>> parentsByPosition = StructureObjectUtils.splitByFieldName(parents);
        for (String position : parentsByPosition.keySet()) {
            Set<StructureObject> allElements = getElements(position);
            Map<StructureObject, List<StructureObject>> elementsByParent = StructureObjectUtils.splitByParent(allElements);
            List<StructureObject> toRemove = new ArrayList<>();
            for (StructureObject parent : parentsByPosition.get(position)) if (elementsByParent.containsKey(parent)) toRemove.addAll(elementsByParent.get(parent));
            removeElements(toRemove);
        }
    }
    public synchronized void clear() {
        elements.clear();
        if (retrievedElements!=null) retrievedElements.clear();
        if (retrievedTrackHeads!=null) retrievedTrackHeads.clear();
    }
    @Override 
    public String toString() {
        return id+" (s:"+structureIdx+"; n="+count()+")";
    }
    public int count() {
        int c = 0;
        for (String k : elements.keySet()) c+=get(k, true).size();
        return c;
    }
    public int count(String position) {
        return get(position, true).size();
    }
    public String getName() {
        return id;
    }

    @Override public int compareTo(Selection o) {
        return this.id.compareTo(o.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Selection) {
            return ((Selection)obj).id.equals(id);
        } else return false;
    }
    // morphium
    public Selection() {}

}
