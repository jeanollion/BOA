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
import image.BoundingBox;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
        //logger.debug("getAllObjects: path to structure: length: {}, elements: {}", pathToStructure.length, pathToStructure);
        if (pathToStructure.length==0) return new ArrayList<StructureObject>(0);
        ArrayList<StructureObject> currentChildren;
        currentChildren = new ArrayList<StructureObject>(referenceStructureObject.getChildren(pathToStructure[0]).size());
        currentChildren.addAll(referenceStructureObject.getChildObjects(pathToStructure[0]));
        //logger.debug("getAllObjects: current structure {} current number of objects: {}", pathToStructure[0], currentChildren.size());
        for (int i = 1; i<pathToStructure.length; ++i) {
            currentChildren = getAllChildren(currentChildren, pathToStructure[i]);
            //logger.debug("getAllObjects: current structure {} current number of objects: {}", pathToStructure[i], currentChildren.size());
        }
        return currentChildren;
    }
    
    private static ArrayList<StructureObject> getAllChildren(ArrayList<StructureObject> parents, int childrenStructureIdx) {
        ArrayList<StructureObject> res = new ArrayList<StructureObject>();
        for (StructureObject parent : parents) {
            //logger.debug("getAllChildren: current object {} childrenStructureIdx : {} number of objects: {}", parent,childrenStructureIdx, parent.getChildObjects(childrenStructureIdx)==null?"null": parent.getChildObjects(childrenStructureIdx).length);
            res.addAll(parent.getChildren(childrenStructureIdx)); // no loop because childrenStructureIdx is direct child of parent
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
        } else return getAllObjects(referenceStructutre, Arrays.copyOfRange(pathToStructure, 0, pathToStructure.length-1));
    }
    
    public static void assignChildren(ArrayList<StructureObject> parent, ArrayList<StructureObject> children) {
        if (children.isEmpty()) return;
        int childStructure = children.get(0).getStructureIdx();
        for (StructureObject p : parent) p.setChildren(new ArrayList<StructureObject>(), childStructure);
        for (StructureObject c : children) {
            BoundingBox b = c.getBounds();
            StructureObject currentParent=null;
            int currentIntersection=-1;
            for (StructureObject p : parent) {
                if (p.getBounds().hasIntersection(b)) {
                    if (currentParent==null) {
                        currentParent = p;
                    }
                    else { // in case of conflict: keep parent that interact most
                        if (currentIntersection==-1) currentIntersection = c.getObject().getIntersection(currentParent.getObject()).size();
                        int otherIntersection = c.getObject().getIntersection(p.getObject()).size();
                        if (otherIntersection>currentIntersection) {
                            currentIntersection=otherIntersection;
                            currentParent=p;
                        }
                    }
                }
            }
            if (currentParent!=null) currentParent.getChildren(childStructure).add(c);
            else logger.warn("{} counld not be assigned to any parent", c);
        }
    }
    public static int[] getIndexTree(StructureObject o) {
        if (o.isRoot()) return new int[0];
        ArrayList<Integer> al = new ArrayList<Integer>();
        al.add(o.getIdx());
        while(!o.getParent().isRoot()) {
            o=o.getParent();
            al.add(o.getIdx());
        }
        return Utils.toArray(al, true);
    }
    public static HashMap<StructureObject, ArrayList<StructureObject>> getAllTracks(List<StructureObject> parentTrack, int structureIdx) {
        HashMap<StructureObject, ArrayList<StructureObject>>  res = new HashMap<StructureObject, ArrayList<StructureObject>>();
        for (StructureObject p : parentTrack) {
            ArrayList<StructureObject> children = p.getChildren(structureIdx);
            for (StructureObject c : children) {
                ArrayList<StructureObject> l;
                if (c.isTrackHead()) {
                    l = new ArrayList<StructureObject>();
                    l.add(c);
                    res.put(c, l);
                } else {
                    l = res.get(c.getTrackHead());
                    if (l!=null) l.add(c);
                    else logger.error("getAllTracks: track not found for Object: {}, trackHead: {}", c, c.getTrackHead());
                }
            }
        }
        return res;
    }
}
