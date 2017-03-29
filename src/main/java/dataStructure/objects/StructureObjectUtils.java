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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.imglib2.KDTree;
import net.imglib2.RealPoint;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import org.bson.types.ObjectId;
import utils.HashMapGetCreate;
import utils.Pair;
import utils.Utils;

/**
 *
 * @author nasique
 */
public class StructureObjectUtils {
    
    public static List<StructureObject> getAllNext(StructureObject o, List<StructureObject> bucket) { // look only in next timePoint
        if (bucket==null) bucket=new ArrayList<>();
        if (o.getParent()==null) {
            logger.error("parent null for: {}", o);
            return bucket;
        }
        StructureObject nextParent = o.getParent().getNext();
        if (nextParent==null) return bucket;
        for (StructureObject n : nextParent.getChildren(o.getStructureIdx())) if (n.getPrevious()==o) bucket.add(n);
        return bucket;
    }
    
    public static void setTrackLinks(StructureObject previous, StructureObject next, boolean setPrevious, boolean setNext) {
        if (previous==null && next==null) return;
        else if (previous==null && next!=null) {
            next.resetTrackLinks(setPrevious, false);
        } else if (previous!=null && next==null) {
            previous.resetTrackLinks(false, setNext);
        }
        else if (next.getFrame()<=previous.getFrame()) throw new RuntimeException("setLink should be of time>= "+(previous.getFrame()+1) +" but is: "+next.getFrame()+ " current: "+previous+", next: "+next);
        else {
            if (setPrevious && setNext) { // double link: set trackHead
                    previous.setNext(next);
                    next.setPrevious(previous);
                    next.setTrackHead(previous.getTrackHead(), false, false, null);
            } else if (setPrevious) {
                next.setPrevious(previous);
                next.setTrackHead(next, false, false, null);
            } else if (setNext) {
                previous.setNext(next);
            }
        }
    }
    
    public static void setTrackLinks(List<StructureObject> track) {
        if (track.isEmpty()) return;
        StructureObject trackHead = track.get(0).getTrackHead();
        StructureObject prev = null;
        for (StructureObject o : track) {
            o.setTrackHead(trackHead, false);
            if (prev!=null) {
                o.setPrevious(prev);
                prev.setNext(o);
            }
            prev = o;
        }
    }
    
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
    
    public static ArrayList<StructureObject> getAllParentObjects(StructureObject referenceStructutre, int[] pathToStructure, ObjectDAO dao) {
        if (pathToStructure.length==0) return new ArrayList<StructureObject>(0);
        else if (pathToStructure.length==1) {
            ArrayList<StructureObject> res = new ArrayList<StructureObject>(1);
            res.add(referenceStructutre);
            return res;
        } else return getAllObjects(referenceStructutre, Arrays.copyOfRange(pathToStructure, 0, pathToStructure.length-1));
    }
    
    public static void assignChildren(List<StructureObject> parent, List<StructureObject> children) {
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
    
    public static List<StructureObject> getIncludedObjects(List<StructureObject> candidates, StructureObject container) {
        ArrayList<StructureObject> res = new ArrayList<StructureObject>();
        BoundingBox containerBox = container.getBounds();
        for (StructureObject c : candidates) if (c.getBounds().hasIntersection(containerBox)) res.add(c); // strict inclusion?
        return res;
    }
    
    public static Object3D getInclusionParent(Object3D children, List<Object3D> parents, BoundingBox offset, BoundingBox offsetParent) {
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
    
    public static StructureObject getInclusionParent(Object3D children, Collection<StructureObject> parents, BoundingBox offset) {
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
    
    public static Map<StructureObject, StructureObject> getInclusionParentMap(Collection<StructureObject> objectsFromSameStructure, int inclusionStructureIdx) {
        if (objectsFromSameStructure.isEmpty()) return Collections.EMPTY_MAP;
        StructureObject o = objectsFromSameStructure.iterator().next();
        Map<StructureObject, StructureObject>  res= new HashMap<>();
        if (o.getExperiment().isChildOf(inclusionStructureIdx, o.getStructureIdx())) {
            for (StructureObject oo : objectsFromSameStructure) res.put(oo, oo.getParent(inclusionStructureIdx));
            return res;
        }
        int closestParentStructureIdx = o.getExperiment().getFirstCommonParentStructureIdx(o.getStructureIdx(), inclusionStructureIdx);
        for (StructureObject oo : objectsFromSameStructure) {
            StructureObject i = getInclusionParent(oo.getObject(), oo.getParent(closestParentStructureIdx).getChildren(inclusionStructureIdx), null);
            res.put(oo, i);
        }
        return res;
    }
    
    public static Map<StructureObject, StructureObject> getInclusionParentMap(Collection<StructureObject> objectsFromSameStructure, Collection<StructureObject> inclusionObjects) {
        if (objectsFromSameStructure.isEmpty()) return Collections.EMPTY_MAP;
        Map<StructureObject, StructureObject>  res= new HashMap<>();
        for (StructureObject oo : objectsFromSameStructure) {
            StructureObject i = getInclusionParent(oo.getObject(), inclusionObjects, null);
            res.put(oo, i);
        }
        return res;
    }
    
    
    public static int[] getIndexTree(StructureObject o) {
        if (o.isRoot()) return new int[]{o.getFrame()};
        ArrayList<Integer> al = new ArrayList<Integer>();
        al.add(o.getIdx());
        while(!o.getParent().isRoot()) {
            o=o.getParent();
            al.add(o.getIdx());
        }
        al.add(o.getFrame());
        return Utils.toArray(al, true);
    }
    public static String getIndices(StructureObject o) {
        return Selection.indicesToString(getIndexTree(o));
    }
    public static void setAllChildren(List<StructureObject> parentTrack, int structureIdx) {
        ObjectDAO dao = parentTrack.get(0).getDAO();
        //logger.debug("set all children: parent: {}, structure: {}", parentTrack.get(0).getTrackHead(), structureIdx);
        if (dao.getExperiment().isDirectChildOf(parentTrack.get(0).getStructureIdx(), structureIdx)) {
            List<StructureObject> parentWithNoChildren = new ArrayList<>(parentTrack.size());
            for (StructureObject p : parentTrack) if (!p.childrenSM.has(structureIdx)) parentWithNoChildren.add(p);
            if (parentWithNoChildren.isEmpty()) return;
            dao.setAllChildren(parentWithNoChildren, structureIdx);
        }
        else if (!dao.getExperiment().isChildOf(parentTrack.get(0).getStructureIdx(), structureIdx)) return;
        else { // indirect child
            int pIdx = dao.getExperiment().getStructure(structureIdx).getParentStructure();
            setAllChildren(parentTrack, pIdx);
            Map<StructureObject, List<StructureObject>> allParentTrack = getAllTracks(parentTrack, pIdx);
            for (List<StructureObject> pTrack: allParentTrack.values()) setAllChildren(pTrack, structureIdx);
        }
    }
    public static Map<StructureObject, List<StructureObject>> getAllTracks(List<StructureObject> parentTrack, int structureIdx) {
        if (parentTrack.isEmpty()) return Collections.EMPTY_MAP;
        HashMap<StructureObject, List<StructureObject>>  res = new HashMap<StructureObject, List<StructureObject>>();
        // set all children
        setAllChildren(parentTrack, structureIdx);
        for (StructureObject p : parentTrack) {
            List<StructureObject> children = p.getChildren(structureIdx);
            for (StructureObject c : children) {
                List<StructureObject> l;
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
        //for (List<StructureObject> l : res.values()) setTrackLinks(l);
        return res;
    }
    public static Map<StructureObject, List<StructureObject>> getAllTracksSplitDiv(List<StructureObject> parentTrack, int structureIdx) {
        Map<StructureObject, List<StructureObject>> res = getAllTracks(parentTrack, structureIdx);
        TreeMap<StructureObject, List<StructureObject>>  tm = new TreeMap(res);
        for (StructureObject o : tm.descendingKeySet()) {
            if (o.getPrevious()==null || o.getPrevious().getNext()==null) continue;
            StructureObject th = o.getPrevious().getNext();
            if (!res.containsKey(th)) {
                List<StructureObject> track = res.get(o.getPrevious().getTrackHead());
                int i = track.indexOf(th);
                if (i>=0) {
                    res.put(th, track.subList(i, track.size()));
                    res.put(o.getPrevious().getTrackHead(), track.subList(0, i));
                }
            }
        }
        return res;
    }
    
    public static Set<String> getPositions(Collection<StructureObject> l) {
        Set<String> res = new HashSet<>();
        for (StructureObject o : l) res.add(o.getPositionName());
        return res;
    }
    
    public static List<StructureObject> getTrack(StructureObject trackHead, boolean extend) {
        if (trackHead==null) return Collections.EMPTY_LIST;
        trackHead = trackHead.getTrackHead();
        ArrayList<StructureObject> track = new ArrayList<StructureObject>();
        if (extend && trackHead.getPrevious()!=null) track.add(trackHead.getPrevious());
        StructureObject o = trackHead;
        while(o!=null && o.getTrackHead()==trackHead) {
            track.add(o);
            o = o.getNext();
        } 
        if (extend && track.get(track.size()-1).getNext()!=null) track.add(track.get(track.size()-1).getNext());
        return track;
    }
    public static List<List<StructureObject>> getTracks(Collection<StructureObject> trackHeads, boolean extend) {
        List<List<StructureObject>> res = new ArrayList<List<StructureObject>>(trackHeads.size());
        for (StructureObject o : trackHeads) res.add(getTrack(o, extend));
        return res;
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
    
    public static List<StructureObject> getTrackHeads(Collection<StructureObject> objects) {
        List<StructureObject> res = new ArrayList<StructureObject>(objects.size());
        for (StructureObject o : objects) res.add(o.getTrackHead());
        Utils.removeDuplicates(res, false);
        return res;
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
    
    public static List<ObjectId> getIdList(Collection<StructureObject> objects) {
        List<ObjectId> ids = new ArrayList<ObjectId>(objects.size());
        for (StructureObject o : objects) ids.add(o.id);
        return ids;
    }
    public static List<ObjectId> getMeasurementIdList(Collection<StructureObject> objects) {
        List<ObjectId> ids = new ArrayList<ObjectId>(objects.size());
        for (StructureObject o : objects) if (o.measurementsId!=null) ids.add(o.measurementsId);
        return ids;
    }

    public static Map<StructureObject, List<StructureObject>> splitByParent(Collection<StructureObject> list) {
        if (list.isEmpty()) return Collections.EMPTY_MAP;
        return list.stream().collect(Collectors.groupingBy(o -> o.getParent()));
    }
    
    public static Map<StructureObject, List<StructureObject>> splitByParentTrackHead(Collection<StructureObject> list) {
        if (list.isEmpty()) return Collections.EMPTY_MAP;
        return list.stream().collect(Collectors.groupingBy(o -> o.getParent().getTrackHead()));
    }
    
    
    
    public static Map<StructureObject, List<StructureObject>> splitByTrackHead(Collection<StructureObject> list) {
        if (list.isEmpty()) return Collections.EMPTY_MAP;
        return list.stream().collect(Collectors.groupingBy(o -> o.getTrackHead()));
    }
    
    public static Map<Integer, List<StructureObject>> splitByStructureIdx(Collection<StructureObject> list) {
        if (list.isEmpty()) return Collections.EMPTY_MAP;
        return list.stream().collect(Collectors.groupingBy(o -> o.getStructureIdx()));
    }
    
    public static Map<Integer, List<StructureObject>> splitByIdx(Collection<StructureObject> list) {
        if (list.isEmpty()) return Collections.EMPTY_MAP;
        return list.stream().collect(Collectors.groupingBy(o -> o.getIdx()));
    }
    
    
    public static Map<String, List<StructureObject>> splitByFieldName(Collection<StructureObject> list) {
        if (list.isEmpty()) return Collections.EMPTY_MAP;
        return list.stream().collect(Collectors.groupingBy(o -> o.getPositionName()));
    }
        
    public static StructureObject keepOnlyObjectsFromSameParent(Collection<StructureObject> list, StructureObject... parent) {
        if (list.isEmpty()) return null;
        StructureObject p = parent.length>=1 ? parent[0] : list.iterator().next().getParent();
        list.removeIf(o -> o.getParent()!=p);
        return p;
    }
    public static int keepOnlyObjectsFromSameStructureIdx(Collection<StructureObject> list, int... structureIdx) {
        if (list.isEmpty()) return -2;
        int sIdx = structureIdx.length>=1 ? structureIdx[0] : list.iterator().next().getStructureIdx();
        list.removeIf(o -> o.getStructureIdx()!=sIdx);
        return sIdx;
    }
    public static String keepOnlyObjectsFromSameMicroscopyField(Collection<StructureObject> list, String... fieldName) {
        if (list.isEmpty()) return null;
        String fName = fieldName.length>=1 ? fieldName[0] : list.iterator().next().getPositionName();
        list.removeIf(o -> !o.getPositionName().equals(fName));
        return fName;
    }
    
    public static Set<StructureObject> getParents(Collection<StructureObject> objects) {
        if (objects==null || objects.isEmpty()) return Collections.EMPTY_SET;
        Set<StructureObject> res = new HashSet<>();
        for (StructureObject o : objects) res.add(o.getParent());
        return res;
    }
    
    public static Set<StructureObject> getParents(Collection<StructureObject> objects, int parentStructureIdx, boolean strictParent) {
        if (objects==null || objects.isEmpty()) return Collections.EMPTY_SET;
        Set<StructureObject> res = new HashSet<>();
        for (StructureObject o : objects) {
            if (strictParent && o.getStructureIdx()==parentStructureIdx) continue;
            StructureObject p = o.getParent(parentStructureIdx);
            if (p!=null) res.add(p);
        }
        return res;
    }
    
    public static Set<StructureObject> getParentTrackHeads(Collection<StructureObject> objects, int parentStructureIdx, boolean strictParent) {
        if (objects==null || objects.isEmpty()) return Collections.EMPTY_SET;
        Set<StructureObject> res = new HashSet<>();
        for (StructureObject o : objects) {
            if (strictParent && o.getStructureIdx()==parentStructureIdx) continue;
            if (parentStructureIdx>o.getStructureIdx()) continue;
            StructureObject p = o.getParent(parentStructureIdx);
            if (p!=null) res.add(p.getTrackHead());
        }
        return res;
    }
    
    public static Set<StructureObject> getParentTrackHeads(Collection<StructureObject> objects) {
        if (objects==null || objects.isEmpty()) return Collections.EMPTY_SET;
        Set<StructureObject> res = new HashSet<>();
        for (StructureObject o : objects) res.add(o.getParent().getTrackHead());
        return res;
    }
    
    public static Comparator<StructureObject> getStructureObjectComparator() {
        return new Comparator<StructureObject>() {
            public int compare(StructureObject arg0, StructureObject arg1) {
                int comp = Integer.compare(arg0.getFrame(), arg1.getFrame());
                if (comp == 0) {
                    comp = Integer.compare(arg0.getStructureIdx(), arg1.getStructureIdx());
                    if (comp == 0) {
                        if (arg0.getParent() != null && arg1.getParent() != null) {
                            comp = compare(arg0.getParent(), arg1.getParent());
                            if (comp != 0) {
                                return comp;
                            }
                        }
                        return Integer.compare(arg0.getIdx(), arg1.getIdx());
                    } else {
                        return comp;
                    }
                } else {
                    return comp;
                }
            }
        };
    }
    static Comparator<StructureObject> frameComparator = new Comparator<StructureObject>() {
        public int compare(StructureObject arg0, StructureObject arg1) {
            return  Integer.compare(arg0.getFrame(), arg1.getFrame());
        }
    };
    public static Comparator<StructureObject> frameComparator() {
        return frameComparator;
    }
    
}
