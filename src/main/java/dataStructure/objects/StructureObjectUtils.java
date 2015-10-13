/*
 * Copyright (C) 2015 nasique
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
import java.util.ArrayList;
import java.util.Collections;
import utils.Utils;

/**
 *
 * @author nasique
 */
public class StructureObjectUtils {
    /**
     * 
     * @param referenceStructureObject
     * @param pathToStructure array of structure indices, in hierachical order, from the root to the given structure
     * @return all the objects of the last structure of the path
     */
    public static ArrayList<StructureObject> getAllObjects(StructureObject referenceStructureObject, int[] pathToStructure) {
        return getAllObjects(referenceStructureObject, pathToStructure, null);
    }
    
    /**
     * 
     * @param referenceStructureObject
     * @param pathToStructure array of structure indices, in hierachical order, from the root to the given structure
     * @return all the objects of the last structure of the path
     */
    public static ArrayList<StructureObject> getAllObjects(StructureObject referenceStructureObject, int[] pathToStructure, ObjectDAO dao) {
        //logger.debug("getAllObjects: path to structure: length: {}, elements: {}", pathToStructure.length, pathToStructure);
        if (pathToStructure.length==0) return new ArrayList<StructureObject>(0);
        ArrayList<StructureObject> currentChildren;
        if (dao==null) currentChildren = new ArrayList<StructureObject>(referenceStructureObject.getChildObjects(pathToStructure[0]).size());
        else currentChildren = new ArrayList<StructureObject>(referenceStructureObject.getChildObjects(pathToStructure[0], dao, false).size()); // will load the objects
        currentChildren.addAll(referenceStructureObject.getChildObjects(pathToStructure[0]));
        //logger.debug("getAllObjects: current structure {} current number of objects: {}", pathToStructure[0], currentChildren.size());
        for (int i = 1; i<pathToStructure.length; ++i) {
            currentChildren = getAllChildren(currentChildren, pathToStructure[i], dao);
            //logger.debug("getAllObjects: current structure {} current number of objects: {}", pathToStructure[i], currentChildren.size());
        }
        return currentChildren;
    }
    
    private static ArrayList<StructureObject> getAllChildren(ArrayList<StructureObject> parents, int childrenStructureIdx, ObjectDAO dao) {
        ArrayList<StructureObject> res = new ArrayList<StructureObject>();
        for (StructureObject parent : parents) {
            //logger.debug("getAllChildren: current object {} childrenStructureIdx : {} number of objects: {}", parent,childrenStructureIdx, parent.getChildObjects(childrenStructureIdx)==null?"null": parent.getChildObjects(childrenStructureIdx).length);
            if (dao==null) res.addAll(parent.getChildObjects(childrenStructureIdx));
            else res.addAll(parent.getChildObjects(childrenStructureIdx, dao, false));
        }
        return res;
    } 
    
    public static ArrayList<StructureObject> getAllParentObjects(StructureObject referenceStructutre, int[] pathToStructure) {
        return getAllParentObjects(referenceStructutre, pathToStructure, null);
    }
    
    public static ArrayList<StructureObject> getAllParentObjects(StructureObject referenceStructutre, int[] pathToStructure, ObjectDAO dao) {
        if (pathToStructure.length==0) return new ArrayList<StructureObject>(0);
        else if (pathToStructure.length==1) {
            ArrayList<StructureObject> res = new ArrayList<StructureObject>(1);
            res.add(referenceStructutre);
            return res;
        } else {
            int[] pathToRoot2 = new int[pathToStructure.length-1];
            System.arraycopy(pathToStructure, 0, pathToRoot2, 0, pathToRoot2.length);
            return getAllObjects(referenceStructutre, pathToRoot2, dao);
        }
    }
    
}
