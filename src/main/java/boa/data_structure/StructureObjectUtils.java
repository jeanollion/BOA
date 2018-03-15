/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.data_structure;

import boa.data_structure.dao.BasicObjectDAO;
import boa.data_structure.dao.ObjectDAO;
import boa.data_structure.dao.BasicMasterDAO;
import static boa.data_structure.StructureObject.logger;
import boa.image.MutableBoundingBox;
import boa.image.Offset;
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
import boa.utils.HashMapGetCreate;
import boa.utils.Pair;
import boa.utils.Utils;

/**
 *
 * @author nasique
 */
public class StructureObjectUtils {
    
    public static List<StructureObject> getObjectsAtNextDivision(StructureObject o) {
        List<StructureObject> bucket = new ArrayList<>();
        StructureObject parent = o.getParent();
        if (parent==null || parent.getNext()==null) return Collections.EMPTY_LIST;
        while(parent.getNext()!=null && bucket.size()<2) {
            bucket.clear();
            parent = parent.getNext();
            for (StructureObject n : parent.getChildren(o.getStructureIdx())) if (n.getPrevious().getTrackHead()==o.getTrackHead()) bucket.add(n);
            if (bucket.isEmpty()) return Collections.EMPTY_LIST;
        }
        return bucket;
    }
    public static List<StructureObject> getObjectsAtPrevDivision(StructureObject o) {
        List<StructureObject> bucket = new ArrayList<>();
        StructureObject prev= o;
        if (prev.getParent()==null) {
            //logger.debug("prevDiv: {} no parent", o);
            return Collections.EMPTY_LIST;
        }
        while(prev!=null && prev.getPrevious()!=null) {
            //logger.debug("prevDiv: {} prev: {}", o, prev);
            if (!prev.getTrackHead().equals(o.getTrackHead())) return Collections.EMPTY_LIST;
            for (StructureObject n : prev.getParent().getChildren(o.getStructureIdx())) if (!n.equals(prev) && prev.getPrevious().equals(n.getPrevious())) {
                if (bucket.isEmpty()) bucket.add(prev);
                bucket.add(n);
            }
            if (!bucket.isEmpty()) return bucket;
            prev = prev.getPrevious();
        }
        return bucket;
    }
    
    public static List<StructureObject> getDaugtherObjectsAtNextFrame(StructureObject o, List<StructureObject> bucket) { // look only in next timePoint
        if (bucket==null) bucket=new ArrayList<>();
        else bucket.clear();
        if (o.getParent()==null) {
            //if (o.getNext()!=null) bucket.add(o.getNext());
            return bucket;
        }
        StructureObject nextParent = o.getParent().getNext();
        if (nextParent==null) return bucket;
        for (StructureObject n : nextParent.getChildren(o.getStructureIdx())) if (o.equals(n.getPrevious())) bucket.add(n);
        return bucket;
    }
    public static void setTrackLinks(StructureObject previous, StructureObject next, boolean setPrevious, boolean setNext) {
        setTrackLinks(previous, next, setPrevious, setNext, null);
    }
    
    public static void setTrackLinks(StructureObject previous, StructureObject next, boolean setPrevious, boolean setNext, Collection<StructureObject> modifiedObjects) {
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
                next.setTrackHead(previous.getTrackHead(), false, modifiedObjects!=null, modifiedObjects);
            } else if (setPrevious) {
                next.setPrevious(previous);
                if (next.equals(previous.getNext())) next.setTrackHead(previous.getTrackHead(), false, true, modifiedObjects);
                else next.setTrackHead(next, false, false, null);
            } else if (setNext) {
                previous.setNext(next);
                if (previous.equals(next.getPrevious()))  next.setTrackHead(previous.getTrackHead(), false, true, modifiedObjects);
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
        if (pathToStructure.length==0) return new ArrayList<>(0);
        ArrayList<StructureObject> currentChildren;
        currentChildren = new ArrayList<>(referenceStructureObject.getChildren(pathToStructure[0]));
        //logger.debug("getAllObjects: current structure {} current number of objects: {}", pathToStructure[0], currentChildren.size());
        for (int i = 1; i<pathToStructure.length; ++i) {
            currentChildren = getAllChildren(currentChildren, pathToStructure[i]);
            //logger.debug("getAllObjects: current structure {} current number of objects: {}", pathToStructure[i], currentChildren.size());
        }
        return currentChildren;
    }
    
    private static ArrayList<StructureObject> getAllChildren(ArrayList<StructureObject> parents, int childrenStructureIdx) {
        ArrayList<StructureObject> res = new ArrayList<>();
        for (StructureObject parent : parents) {
            //logger.debug("getAllChildren: current object {} childrenStructureIdx : {} number of objects: {}", parent,childrenStructureIdx, parent.getChildObjects(childrenStructureIdx)==null?"null": parent.getChildObjects(childrenStructureIdx).size());
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
            StructureObject currentParent=null;
            int currentIntersection=-1;
            for (StructureObject p : parent) {
                if (p.getRegion().intersect(c.getRegion())) {
                    if (currentParent==null) {
                        currentParent = p;
                    }
                    else { // in case of conflict: keep parent that intersect most
                        if (currentIntersection==-1) currentIntersection = c.getRegion().getOverlapMaskMask(p.getRegion(),null,  null);
                        int otherIntersection = c.getRegion().getOverlapMaskMask(p.getRegion(),null,  null);
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
    
    public static List<StructureObject> getIncludedStructureObjects(List<StructureObject> candidates, StructureObject container) {
        ArrayList<StructureObject> res = new ArrayList<>();
        for (StructureObject c : candidates) if (c.getRegion().intersect(container.getRegion())) res.add(c); // strict inclusion?
        return res;
    }
    
    public static StructureObject getInclusionParent(Region children, Collection<StructureObject> parents, Offset offset) {
        if (parents.isEmpty() || children==null) return null;
        Map<Region, StructureObject> soOMap = parents.stream().collect(Collectors.toMap(o->o.getRegion(), o->o));
        Region parentObject = children.getContainer(soOMap.keySet(), offset, null); 
        return soOMap.get(parentObject);
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
            StructureObject i = getInclusionParent(oo.getRegion(), oo.getParent(closestParentStructureIdx).getChildren(inclusionStructureIdx), null);
            res.put(oo, i);
        }
        return res;
    }
    
    public static Map<StructureObject, StructureObject> getInclusionParentMap(Collection<StructureObject> objectsFromSameStructure, Collection<StructureObject> inclusionObjects) {
        if (objectsFromSameStructure.isEmpty()) return Collections.EMPTY_MAP;
        Map<StructureObject, StructureObject>  res= new HashMap<>();
        for (StructureObject oo : objectsFromSameStructure) {
            StructureObject i = getInclusionParent(oo.getRegion(), inclusionObjects, null);
            res.put(oo, i);
        }
        return res;
    }
    
    
    public static int[] getIndexTree(StructureObject o) {
        if (o.isRoot()) return new int[]{o.getFrame()};
        ArrayList<Integer> al = new ArrayList<>();
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
        if (parentTrack.isEmpty() || structureIdx == -1) return;
        ObjectDAO dao = parentTrack.get(0).getDAO();
        if (dao instanceof BasicObjectDAO) return;
        logger.debug("set all children: parent: {}, structure: {}", parentTrack.get(0).getTrackHead(), structureIdx);
        if (dao.getExperiment().isDirectChildOf(parentTrack.get(0).getStructureIdx(), structureIdx)) {
            List<StructureObject> parentWithNoChildren = new ArrayList<>(parentTrack.size());
            for (StructureObject p : parentTrack) if (!p.hasChildren(structureIdx)) parentWithNoChildren.add(p);
            logger.debug("parents with no children : {}", parentWithNoChildren.size());
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
    public static List<StructureObject> getAllObjects(ObjectDAO dao, int structureIdx) {
        List<StructureObject> roots= dao.getRoots();
        if (structureIdx == -1) return roots;
        setAllChildren(roots, structureIdx);
        return getAllChildren(roots, structureIdx);
    }
    public static List<StructureObject> getAllChildren(List<StructureObject> parentTrack, int structureIdx) {
        List<StructureObject> res = new ArrayList<>();
        for (StructureObject p : parentTrack) res.addAll(p.getChildren(structureIdx));
        return res;
    }
    public static Map<StructureObject, List<StructureObject>> getAllTracks(Collection<StructureObject> objects, boolean extend) {
        HashMapGetCreate<StructureObject, List<StructureObject>> allTracks = new HashMapGetCreate<>(new HashMapGetCreate.ListFactory());
        for (StructureObject o : objects) allTracks.getAndCreateIfNecessary(o.getTrackHead()).add(o);
        for (List<StructureObject> track : allTracks.values()) {
            Collections.sort(track, (o1, o2)->Integer.compare(o1.getFrame(), o2.getFrame()));
            if (extend) {
                if (track.get(0).getPrevious()!=null) track.add(0, track.get(0).getPrevious());
                if (track.get(track.size()-1).getNext()!=null) track.add(track.get(track.size()-1).getNext());
            }
        }
        return allTracks;
    }
    public static Map<StructureObject, List<StructureObject>> getAllTracks(List<StructureObject> parentTrack, int structureIdx) {
        return getAllTracks(parentTrack, structureIdx, true);
    }
    public static Map<StructureObject, List<StructureObject>> getAllTracks(List<StructureObject> parentTrack, int structureIdx, boolean allowSearchInPreviousFrames) {
        if (parentTrack==null || parentTrack.isEmpty()) return Collections.EMPTY_MAP;
        if (allowSearchInPreviousFrames && parentTrack.get(0).equals(parentTrack.get(0).getTrackHead())) allowSearchInPreviousFrames=false;
        HashMap<StructureObject, List<StructureObject>>  res = new HashMap<>();
        // set all children
        setAllChildren(parentTrack, structureIdx);
        for (StructureObject p : parentTrack) {
            List<StructureObject> children = p.getChildren(structureIdx);
            if (children==null) continue;
            for (StructureObject c : children) {
                List<StructureObject> l;
                if (c.isTrackHead()) {
                    l = new ArrayList<StructureObject>();
                    l.add(c);
                    res.put(c, l);
                } else {
                    l = res.get(c.getTrackHead());
                    if (l!=null) l.add(c);
                    else if (allowSearchInPreviousFrames) {
                        l = new ArrayList<>();
                        StructureObject th = c.getTrackHead();
                        while (c!=null && c.getTrackHead().equals(th)) {
                            l.add(c);
                            c=c.getPrevious();
                        }
                        Collections.sort(l, (o1, o2)->Integer.compare(o1.getFrame(), o2.getFrame()));
                        res.put(th, l);
                    }
                    else logger.error("getAllTracks: track not found for Object: {}, trackHead: {}", c, c.getTrackHead());
                }
            }
        }
        //for (List<StructureObject> l : res.values()) updateTrackLinksFromMap(l);
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
                if (track==null) {
                    logger.error("getAllTrackSPlitDiv: no track for: {}", o.getPrevious().getTrackHead());
                    continue;
                }
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
    
    public static Set<StructureObject> getTrackHeads(Collection<StructureObject> objects) {
        Set<StructureObject> res = new HashSet<>(objects.size());
        for (StructureObject o : objects) res.add(o.getTrackHead());
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
    
    public static List<String> getIdList(Collection<StructureObject> objects) {
        List<String> ids = new ArrayList<>(objects.size());
        for (StructureObject o : objects) ids.add(o.id);
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
    public static Map<Integer, StructureObject> splitByFrame(Collection<StructureObject> list) {
        Map<Integer, StructureObject> res= new HashMap<>(list.size());
        for (StructureObject o : list) res.put(o.getFrame(), o);
        return res;
    }
    
    public static Map<String, List<StructureObject>> splitByPosition(Collection<StructureObject> list) {
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

    public static Map<Integer, List<StructureObject>> getChildrenByFrame(List<StructureObject> parents, int structureIdx) {
        try {
            return parents.stream().collect(Collectors.toMap(StructureObject::getFrame, (StructureObject p) -> p.getChildren(structureIdx)));
        } catch (NullPointerException e) {
            return Collections.EMPTY_MAP;
        }
    }
    
    public static void setRelatives(Map<String, StructureObject> allObjects, boolean parent, boolean trackAttributes) {
        for (StructureObject o : allObjects.values()) {
            if (parent && o.parentId!=null) {
                StructureObject p = allObjects.get(o.parentId);
                if (p!=null) o.parent = p;
            }
            if (trackAttributes) {
                if (o.nextId!=null) {
                    StructureObject n = allObjects.get(o.nextId);
                    if (n!=null) o.next=n;
                }
                if (o.previousId!=null) {
                    StructureObject p = allObjects.get(o.previousId);
                    if (p!=null) o.previous=p;
                }
            }
        }
    }
    
    // duplicate objects 
    private static StructureObject duplicateWithChildrenAndParents(StructureObject o, ObjectDAO newDAO, Map<String, StructureObject> sourceToDupMap, boolean children, boolean parents) {
        o.loadAllChildren(false);
        StructureObject res=o.duplicate(false, true);
        if (sourceToDupMap!=null) sourceToDupMap.put(o.getId(), res);
        if (children) {
            for (int cIdx : o.getExperiment().getAllDirectChildStructures(o.structureIdx)) {
                List<StructureObject> c = o.childrenSM.get(cIdx);
                if (c!=null) res.setChildren(Utils.transform(c, oo->duplicateWithChildrenAndParents(oo, newDAO, sourceToDupMap, true, false)), cIdx);
            }
        }
        if (parents && !o.isRoot() && res.getParent()!=null) {
            StructureObject current = o;
            StructureObject currentDup = res;
            while (!current.isRoot() && current.getParent()!=null) {
                StructureObject pDup = current.getParent().duplicate(false, true);
                if (sourceToDupMap!=null) sourceToDupMap.put(current.getParent().getId(), pDup);
                pDup.dao=newDAO;
                currentDup.setParent(pDup);
                ArrayList<StructureObject> pCh = new ArrayList<>(1);
                pCh.add(currentDup);
                pDup.setChildren(pCh, currentDup.structureIdx);
                current = current.getParent();
                currentDup = pDup;
            }
        }
        res.dao=newDAO;
        res.setAttribute("DAOType", newDAO.getClass().getSimpleName());
        return res;
    }
    
    public static Map<String, StructureObject> duplicateRootTrackAndChangeDAO(boolean includeChildren, StructureObject... rootTrack) {
        return createGraphCut(Arrays.asList(rootTrack), includeChildren);
    }
    public static Map<String, StructureObject> createGraphCut(List<StructureObject> track, boolean includeChildren) {
        if (track==null) return null;
        if (track.isEmpty()) return Collections.EMPTY_MAP;
        // load trackImages if existing (on duplicated objects trackHead can be changed and trackImage won't be loadable anymore)
        List<StructureObject> objectsWithParentsAndChildren = new ArrayList<>();
        objectsWithParentsAndChildren.addAll(track);
        for (StructureObject o : track) {
            StructureObject p = o.getParent();
            while(p!=null) {objectsWithParentsAndChildren.add(p); p=p.getParent();}
            if (includeChildren) {
                for (int sIdx : o.getExperiment().getAllChildStructures(o.getStructureIdx())) objectsWithParentsAndChildren.addAll(o.getChildren(sIdx));
            }
        }
        if (includeChildren) {
            for (StructureObject o : objectsWithParentsAndChildren) {
                for (int sIdx : o.getExperiment().getAllDirectChildStructures(o.getStructureIdx())) o.getTrackImage(sIdx);
            }
        }
        // create basic dao for duplicated objects
        Map<String, StructureObject> dupMap = new HashMap<>();
        BasicMasterDAO mDAO = new BasicMasterDAO();
        mDAO.setExperiment(track.get(0).getExperiment());
        BasicObjectDAO dao = mDAO.getDao(track.get(0).getPositionName());
        
        List<StructureObject> dup = Utils.transform(track, oo->duplicateWithChildrenAndParents(oo, dao, dupMap, includeChildren, true));
        List<StructureObject> rootTrack = Utils.transform(dup, o->o.getRoot());
        Utils.removeDuplicates(rootTrack, false);
        Collections.sort(rootTrack);
        dao.setRoots(rootTrack);
        
        // update links
        for (StructureObject o : dupMap.values()) {
            if (o.getPrevious()!=null) o.previous=dupMap.get(o.getPrevious().getId());
            if (o.getNext()!=null) o.next=dupMap.get(o.getNext().getId());
            //o.trackHead=dupMap.get(o.getTrackHead());
        }
        // update trackHeads && trackImages
        for (StructureObject o : dupMap.values()) {
            if (o.isTrackHead()) {
                o.trackHead=o;
                continue;
            }
            StructureObject th = o;
            while(!th.isTrackHead && th.getPrevious()!=null) th=th.getPrevious();
            th.trackImagesC=th.getTrackHead().trackImagesC.duplicate();
            o.setTrackHead(th, false);
            //th.setTrackHead(th, false);
        }
        // update parent trackHeads
        for (StructureObject o : dupMap.values()) {
            if (o.parent!=null) o.parentTrackHeadId=o.parent.trackHeadId;
        }
        return dupMap;
    }
    
}
