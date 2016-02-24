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

import static boa.gui.selection.SelectionMouseAdapterUtil.colors;
import static dataStructure.objects.StructureObject.logger;
import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Id;
import de.caluga.morphium.annotations.Transient;
import de.caluga.morphium.annotations.lifecycle.Lifecycle;
import de.caluga.morphium.annotations.lifecycle.PostLoad;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import utils.Utils;

/**
 *
 * @author jollion
 */
@Entity
public class Selection implements Comparable<Selection> {
    @Id String id;
    int structureIdx;
    Map<String, List<int[]>> elements;
    String color="magenta";
    
    @Transient Map<String, List<StructureObject>> retrievedElements;
    @Transient MasterDAO mDAO;
    @Transient Color col;
    
    public Selection(String name) {
        this.id=name;
        this.structureIdx=-1;
        retrievedElements = new HashMap<String, List<StructureObject>>();
        elements = new HashMap<String, List<int[]>>();
    }
    
    public Color getColor() {
        if (col ==null) col = colors.get(color);
        return col;
    }
    
    public void setColor(String color) {
        this.color=color;
        col = colors.get(color);
    }
    
    public void setMasterDAO(MasterDAO mDAO) {
        this.mDAO=mDAO;
    }
    
    public int getStructureIdx() {
        return structureIdx;
    }
    
    public List<StructureObject> getElements(String fieldName) {
        if (retrievedElements==null) retrievedElements = new HashMap<String, List<StructureObject>>(elements.size());
        List<StructureObject> res =  retrievedElements.get(fieldName);
        if (res==null && elements.containsKey(fieldName)) return retrieveElements(fieldName);
        return res;
    }
    
    protected List<StructureObject> retrieveElements(String fieldName) {
        if (fieldName==null) throw new IllegalArgumentException("FieldName cannot be null");
        List<int[]> indiciesList = elements.get(fieldName);
        if (indiciesList==null) {
            return null;
        }
        ObjectDAO dao = mDAO.getDao(fieldName);
        int[] pathToRoot = mDAO.getExperiment().getPathToRoot(structureIdx);
        List<StructureObject> res = new ArrayList<StructureObject>(indiciesList.size());
        retrievedElements.put(fieldName, res);
        for (int[] indicies : indiciesList) {
            if (indicies.length-1!=pathToRoot.length) {
                logger.warn("Object: {} has wrong number of indicies (expected: {})", indicies, pathToRoot.length);
                continue;
            }
            StructureObject elem = dao.getRoot(indicies[0]);
            for (int i= 1; i<indicies.length; ++i) {
                if (elem.getChildren(pathToRoot[i-1]).size()>=indicies[i]) {
                    logger.warn("Object: {} was not found {}", indicies, pathToRoot.length);
                    continue;
                }
                elem = elem.getChildren(pathToRoot[i-1]).get(indicies[i]);
            }
            res.add(elem);
        }
        return res;
    }
    
    public void updateElementList(String fieldName) {
        if (fieldName==null) throw new IllegalArgumentException("FieldName cannot be null");
        List<StructureObject> objectList = retrievedElements.get(fieldName);
        if (objectList==null) {
            elements.remove(fieldName);
            return;
        }
        Utils.removeDuplicates(objectList, true);
        List<int[]> indiciesList = elements.get(fieldName);
        if (indiciesList==null) {
            indiciesList = new ArrayList<int[]>(objectList.size());
            elements.put(fieldName, indiciesList);
        } else indiciesList.clear();
        for (StructureObject o : objectList) indiciesList.add(StructureObjectUtils.getIndexTree(o));
    }
    
    public void addElement(StructureObject elementToAdd) {
        if (this.structureIdx==-1) structureIdx=elementToAdd.getStructureIdx();
        else if (structureIdx!=elementToAdd.getStructureIdx()) return;
        List<StructureObject> list = getElements(elementToAdd.getFieldName());
        if (list==null) {
            list=new ArrayList<StructureObject>();
            retrievedElements.put(elementToAdd.getFieldName(), list);
        }
        if (!list.contains(elementToAdd)) {
            list.add(elementToAdd);
            List<int[]> els = elements.get(elementToAdd.getFieldName());
            if (els==null) {
                els = new ArrayList<int[]>();
                elements.put(elementToAdd.getFieldName(), els);
            }
            els.add(StructureObjectUtils.getIndexTree(elementToAdd));
            if (els.size()!=list.size()) logger.error("unconsitancy in selection: {}, {} vs: {}", this.toString(), list.size(), els.size());
        }
    }
    public void addElements(List<StructureObject> elementsToAdd) {
        for (StructureObject o : elementsToAdd) addElement(o);
    }
    
    public boolean removeElement(StructureObject elementToRemove) {
        List<StructureObject> list = getElements(elementToRemove.getFieldName());
        if (list!=null) {
            int idx = list.indexOf(elementToRemove);
            if (idx>=0) {
                list.remove(idx);
                List<int[]> els = elements.get(elementToRemove.getFieldName());
                els.remove(idx);
                if (els.size()!=list.size()) logger.error("unconsitancy in selection: {}, {} vs: {}", this.toString(), list.size(), els.size());
            }
        }
        return false;
    }
    public void removeElements(List<StructureObject> elementsToRemove) {
        for (StructureObject o : elementsToRemove) removeElement(o);
    }
    public void clear() {
        elements.clear();
        if (retrievedElements!=null) retrievedElements.clear();
    }
    @Override 
    public String toString() {
        return id+" (s:"+structureIdx+")";
    }
    public String getName() {
        return id;
    }

    public int compareTo(Selection o) {
        return this.id.compareTo(o.id);
    }
    
    // morphium
    public Selection() {}

}
