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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
        currentChildren = new ArrayList<StructureObject>(referenceStructureObject.getChildren(pathToStructure[0]));
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
    
    public static ArrayList<StructureObject> getAllParentObjects(StructureObject referenceStructutre, int[] pathToStructure, MorphiumObjectDAO dao) {
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
                    else { // in case of conflict: keep parent that intersect most
                        if (currentIntersection==-1) currentIntersection = c.getObject().getIntersectionCountMaskMask(p.getObject(),null,  null);
                        int otherIntersection = c.getObject().getIntersectionCountMaskMask(p.getObject(),null,  null);
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
    
    public static ArrayList<StructureObject> getIncludedObjects(ArrayList<StructureObject> candidates, StructureObject container) {
        ArrayList<StructureObject> res = new ArrayList<StructureObject>();
        BoundingBox containerBox = container.getBounds();
        for (StructureObject c : candidates) if (c.getBounds().hasIntersection(containerBox)) res.add(c); // strict inclusion?
        return res;
    }
    
    public static Object3D getInclusionParent(Object3D children, ArrayList<Object3D> parents, BoundingBox offset, BoundingBox offsetParent) {
        if (parents.isEmpty() || children==null) return null;
        Object3D currentParent=null;
        int currentIntersection=-1;
        for (Object3D p : parents) {
            int inter = children.getIntersectionCountMaskMask(p, offset, offsetParent);
            if (inter>0) {
                if (currentParent==null) {
                    currentParent = p;
                    currentIntersection = inter;
                } else if (inter>currentIntersection) { // in case of conflict: keep parent that intersect most
                    currentIntersection=inter;
                    currentParent=p;
                }
            }
        }
        return currentParent;
    }
    
    public static StructureObject getInclusionParent(Object3D children, ArrayList<StructureObject> parents, BoundingBox offset) {
        if (parents.isEmpty() || children==null) return null;
        StructureObject currentParent=null;
        int currentIntersection=-1;
        for (StructureObject p : parents) {
            int inter = children.getIntersectionCountMaskMask(p.getObject(), offset, null);
            if (inter>0) {
                if (currentParent==null) {
                    currentParent = p;
                    currentIntersection = inter;
                } else if (inter>currentIntersection) { // in case of conflict: keep parent that intersect most
                    currentIntersection=inter;
                    currentParent=p;
                }
            }
        }
        return currentParent;
    }
    
    
    
    public static int[] getIndexTree(StructureObject o) {
        if (o.isRoot()) return new int[]{o.getTimePoint()};
        ArrayList<Integer> al = new ArrayList<Integer>();
        al.add(o.getIdx());
        while(!o.getParent().isRoot()) {
            o=o.getParent();
            al.add(o.getIdx());
        }
        al.add(o.getTimePoint());
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
        for (ArrayList<StructureObject> l : res.values()) setTrackLinks(l);
        return res;
    }
    
    protected static void setTrackLinks(ArrayList<StructureObject> track) {
        if (track.isEmpty()) return;
        StructureObject trackHead = track.get(0).getTrackHead();
        StructureObject prev = null;
        for (StructureObject o : track) {
            o.trackHead=trackHead;
            if (prev!=null) {
                o.previous=prev;
                prev.next=o;
            }
            prev = o;
        }
    }
    
    public static List<StructureObject> getTrack(StructureObject trackHead, boolean extend) {
        if (trackHead==null) return Collections.EMPTY_LIST;
        StructureObject head = trackHead.getTrackHead();
        ArrayList<StructureObject> track = new ArrayList<StructureObject>();
        if (extend && head.getPrevious()!=null) track.add(head.getPrevious());
        while(trackHead!=null && trackHead.getTrackHead()==head) {
            track.add(trackHead);
            trackHead = trackHead.getNext();
        } 
        if (extend && track.get(track.size()-1).getNext()!=null) track.add(track.get(track.size()-1).getNext());
        return track;
    }
    
    /*public static Map<Integer, List<StructureObject>> mapByStructureIdx(List<StructureObject> objects) {
        Map<Integer, List<StructureObject>> res = new HashMap<Integer, List<StructureObject>>();
        
    }*/
    
    /**
     * 
     * @param objects
     * @return return the common structureIdx if all objects from {@param objects} or -2 if at least 2 objects have a different structureIdx or {@param objects} is emplty
     */
    public static int getStructureIdx(List<StructureObject> objects) {
        int structureIdx = -2; 
        for (StructureObject o : objects) {
            if (structureIdx == -2 ) structureIdx = o.getStructureIdx();
            else if (structureIdx!=o.getStructureIdx()) return -2;
        }
        return structureIdx;
    }

    public static List<StructureObject> extendTrack(List<StructureObject> track) {
        ArrayList<StructureObject> res = new ArrayList<StructureObject>(track.size() + 2);
        StructureObject prev = track.get(0).getPrevious();
        if (prev != null) {
            res.add(prev);
        }
        res.addAll(track);
        StructureObject next = track.get(track.size() - 1).getNext();
        if (next != null) {
            res.add(next);
        }
        return res;
    }
}
