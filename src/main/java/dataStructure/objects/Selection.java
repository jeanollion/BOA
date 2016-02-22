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

import static dataStructure.objects.StructureObject.logger;
import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Id;
import de.caluga.morphium.annotations.Transient;
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
    @Transient Map<String, List<StructureObject>> retrievedElements = new HashMap<String, List<StructureObject>>();
    @Transient MasterDAO mDAO;
    public Selection() {}
    public Selection(String name) {
        this.id=name;
    }
    public void setMasterDAO(MasterDAO mDAO) {
        this.mDAO=mDAO;
    }
    public List<StructureObject> retrieveElements(String fieldName) {
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
                    logger.warn("Object: {} was not found", indicies, pathToRoot.length);
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
        List<StructureObject> list = this.retrievedElements.get(elementToAdd.getFieldName());
        if (list==null) {
            list=new ArrayList<StructureObject>();
            retrievedElements.put(elementToAdd.getFieldName(), list);
        }
        if (!list.contains(elementToAdd)) list.add(elementToAdd);
    }
    public void addElements(ArrayList<StructureObject> elementsToAdd) {
        for (StructureObject o : elementsToAdd) addElement(o);
    }
    public boolean removeElement(StructureObject elementToRemove) {
        List<StructureObject> list = this.retrievedElements.get(elementToRemove.getFieldName());
        if (list!=null) return list.remove(elementToRemove);
        return false;
    }
    public void removeElements(ArrayList<StructureObject> elementsToRemove) {
        for (StructureObject o : elementsToRemove) removeElement(o);
    }
    
    @Override 
    public String toString() {
        return id;
    }
    public String getName() {
        return id;
    }

    public int compareTo(Selection o) {
        return this.id.compareTo(o.id);
    }
}
