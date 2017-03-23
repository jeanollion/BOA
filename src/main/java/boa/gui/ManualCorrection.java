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
package boa.gui;

import static boa.gui.GUI.logger;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageObjectInterfaceKey;
import boa.gui.imageInteraction.ImageWindowManager;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.gui.objects.ObjectNode;
import boa.gui.objects.StructureNode;
import boa.gui.selection.SelectionUtils;
import dataStructure.configuration.Experiment;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.Selection;
import dataStructure.objects.StructureObject;
import static dataStructure.objects.StructureObject.correctionMerge;
import static dataStructure.objects.StructureObject.correctionSplit;
import static dataStructure.objects.StructureObject.trackErrorNext;
import static dataStructure.objects.StructureObject.trackErrorPrev;
import dataStructure.objects.StructureObjectUtils;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.TypeConverter;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import plugins.ManualSegmenter;
import plugins.ObjectSplitter;
import utils.Pair;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class ManualCorrection {
    private static List<StructureObject> getNext(StructureObject o) {
        if (o.getParent().getNext()==null) return Collections.EMPTY_LIST;
        List<StructureObject> res = new ArrayList(o.getParent().getNext().getChildren(o.getStructureIdx()));
        res.removeIf(e -> e.getPrevious()!=o && o.getNext()!=e);
        //logger.debug("next of : {} = {}", o, res);
        return res;
    }
    private static List<StructureObject> getPrevious(StructureObject o) {
        if (o.getParent().getPrevious()==null) return Collections.EMPTY_LIST;
        List<StructureObject> res = new ArrayList(o.getParent().getPrevious().getChildren(o.getStructureIdx()));
        res.removeIf(e -> e.getNext()!=o && o.getPrevious()!=e);
        //logger.debug("prev of : {} = {}", o, res);
        return res;
    }
    public static void unlinkObject(StructureObject o, Collection<StructureObject> modifiedObjects) {
        for (StructureObject n : getNext(o) ) {
            n.resetTrackLinks(true, false);
            n.setTrackHead(n, true, true, modifiedObjects);
        }
        for (StructureObject p : getPrevious(o) ) if (p.getNext()==o) {
            p.resetTrackLinks(false, true);
            modifiedObjects.add(p);
        }
        o.resetTrackLinks(true, true);
        modifiedObjects.add(o);
        //logger.debug("unlinking: {}", o);
    }
    private static void removeError(StructureObject o, boolean next, boolean prev) {
        if (o.hasMeasurements()) {
            String value = null;
            if (prev) o.getMeasurements().setValue(trackErrorPrev, value);
            if (next) o.getMeasurements().setValue(trackErrorNext, value);
        }
    }
    public static void unlinkObjects(StructureObject prev, StructureObject next, Collection<StructureObject> modifiedObjects) {
        if (next.getFrame()<prev.getFrame()) unlinkObjects(next, prev, modifiedObjects);
        else {
            if (next.getPrevious()==prev) {
                next.resetTrackLinks(true, false);
                next.setTrackHead(next, true, true, modifiedObjects);
            }
            else prev.resetTrackLinks(false, prev.getNext()==next);
            //next.resetTrackLinks(next.getPrevious()==prev, false);
            getManualCorrectionSelection(prev).addElement(next);
            getManualCorrectionSelection(prev).addElement(prev);
            saveManualCorrectionSelection(prev);
            //logger.debug("unlinking.. previous: {}, previous's next: {}", sel.get(1).getPrevious(), sel.get(0).getNext());
            if (modifiedObjects!=null) {
                modifiedObjects.add(prev);
                modifiedObjects.add(next);
            }
            //logger.debug("unlinking: {} to {}", sel.get(0), sel.get(1));
        }
    }
    public static Selection getManualCorrectionSelection(StructureObject o) {
        return o.getDAO().getMasterDAO().getSelectionDAO().getOrCreate(o.getExperiment().getStructure(o.getStructureIdx()).getName()+"_ManualCorrection", true);
    }
    public static void saveManualCorrectionSelection(StructureObject o) {
        o.getDAO().getMasterDAO().getSelectionDAO().store(getManualCorrectionSelection(o));
    }
    public static void linkObjects(StructureObject prev, StructureObject next, Collection<StructureObject> modifiedObjects) {
        if (next.getFrame()<prev.getFrame()) linkObjects(next, prev, modifiedObjects);
        else {
            boolean allowMerge = prev.getExperiment().getStructure(prev.getStructureIdx()).allowMerge();
            boolean allowSplit = prev.getExperiment().getStructure(prev.getStructureIdx()).allowSplit();
            boolean doubleLink = true;
            if (allowMerge) {
                if (next.getPrevious()!=null && next.getPrevious()!=prev) { // mergeLink
                    doubleLink = false;
                    boolean allowMergeLink = true;
                    if (prev.getNext()!=null && prev.getNext()!=next) {
                        if (!allowSplit) {
                            unlinkObjects(prev, prev.getNext(), modifiedObjects);
                        } else allowMergeLink = false;
                    }
                    if (allowMergeLink && next!=prev.getNext()) {
                        prev.setTrackLinks(next, false, true);
                        prev.setAttribute(correctionMerge, true);
                        modifiedObjects.add(prev);
                        logger.debug("split link : {}+{}", prev, next);
                    }
                }
            }
            if (allowSplit) {
                if (prev.getNext()!=null && prev.getNext()!=next) { // split link
                    doubleLink=false;
                    boolean allowSplitLink = true;
                    if (next.getPrevious()!=null && next.getPrevious()!=prev) {
                        if (!allowMerge) {
                            unlinkObjects(next.getPrevious(), next, modifiedObjects);
                        } else allowSplitLink = false;
                    }
                    if (allowSplitLink && prev!=next.getPrevious()) {
                        prev.setTrackLinks(next, true, false);
                        prev.setAttribute(correctionSplit, true);
                        modifiedObjects.add(next);
                        logger.debug("split link : {}+{}", prev, next);
                    }
                }
            }
            if (doubleLink) {
                if (prev.getNext()!=null && prev.getNext()!=next) unlinkObjects(prev, prev.getNext(), modifiedObjects);
                if (next.getPrevious()!=null && next.getPrevious()!=prev) unlinkObjects(next.getPrevious(), next, modifiedObjects);
                //if (next!=prev.getNext() || prev!=next.getPrevious() || next.getTrackHead()!=prev.getTrackHead()) {
                    prev.setTrackLinks(next, true, true);
                    prev.setAttribute(correctionMerge, true);
                    next.setTrackHead(prev.getTrackHead(), false, true, modifiedObjects);
                    modifiedObjects.add(prev);
                    modifiedObjects.add(next);
                    logger.debug("double link : {}+{}, th:{}", prev, next, prev.getTrackHead());
                //}
            }
        }
    }
    
    public static void modifyObjectLinks(MasterDAO db, List<StructureObject> objects, boolean unlink, boolean updateDisplay) {
        StructureObjectUtils.keepOnlyObjectsFromSameMicroscopyField(objects);
        StructureObjectUtils.keepOnlyObjectsFromSameStructureIdx(objects);
        if (objects.size()<=1) return;
        
        if (updateDisplay) ImageWindowManagerFactory.getImageManager().removeTracks(StructureObjectUtils.getTrackHeads(objects));
        
        List<StructureObject> modifiedObjects = new ArrayList<StructureObject>();
        TreeMap<StructureObject, List<StructureObject>> objectsByParent = new TreeMap(StructureObjectUtils.splitByParent(objects)); // sorted by time point
        StructureObject prevParent = null;
        StructureObject prev = null;
        logger.debug("modify: unlink: {}, #objects: {}, #parents: {}", unlink, objects.size(), objectsByParent.keySet().size());
        for (StructureObject currentParent : objectsByParent.keySet()) {
            List<StructureObject> l = objectsByParent.get(currentParent);
            logger.debug("prevParent: {}, currentParent: {}, #objects: {}", prevParent, currentParent, l.size());
            if (l.size()==1 && (prevParent==null || prevParent.getFrame()<currentParent.getFrame())) {
                if (prev!=null) logger.debug("prev: {}, prevTh: {}, prevIsTh: {}, prevPrev: {}, prevNext: {}", prev, prev.getTrackHead(), prev.isTrackHead(), prev.getPrevious(), prev.getNext());
                if (prevParent!=null && prev!=null) {
                    StructureObject current = l.get(0);
                    logger.debug("current: {}, currentTh: {}, currentIsTh: {}, currentPrev: {}, currentNext: {}", current, current.getTrackHead(), current.isTrackHead(), current.getPrevious(), current.getNext());
                    if (unlink) {
                        if (current.getPrevious()==prev || prev.getNext()==current) { //unlink the 2 spots
                            ManualCorrection.unlinkObjects(prev, current, modifiedObjects);
                        } 
                    } else ManualCorrection.linkObjects(prev, current, modifiedObjects);

                } //else if (unlink) ManualCorrection.unlinkObject(l.get(0), modifiedObjects);
                prevParent=currentParent;
                prev = l.get(0);
            } else {
                prev=null;
                prevParent=null;
                //if (unlink) for (StructureObject o : l) ManualCorrection.unlinkObject(o, modifiedObjects);
            }   
        }
        //repairLinkInconsistencies(db, modifiedObjects, modifiedObjects);
        Utils.removeDuplicates(modifiedObjects, false);
        db.getDao(objects.get(0).getPositionName()).store(modifiedObjects, true);
        if (updateDisplay) {
            // reload track-tree and update selection toDelete
            int parentStructureIdx = objects.get(0).getParent().getStructureIdx();
            GUI.getInstance().trackTreeController.updateParentTracks(GUI.getInstance().trackTreeController.getTreeIdx(parentStructureIdx));
            //List<List<StructureObject>> tracks = this.trackTreeController.getGeneratorS().get(structureIdx).getSelectedTracks(true);
            // get unique tracks to display
            Set<StructureObject> uniqueTh = new HashSet<StructureObject>();
            for (StructureObject o : modifiedObjects) uniqueTh.add(o.getTrackHead());
            List<List<StructureObject>> trackToDisp = new ArrayList<List<StructureObject>>();
            for (StructureObject o : uniqueTh) trackToDisp.add(StructureObjectUtils.getTrack(o, true));
            // update current image
            ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
            if (!trackToDisp.isEmpty()) {
                iwm.displayTracks(null, null, trackToDisp, true);
                //GUI.updateRoiDisplayForSelections(null, null);
            }
        }
    }
    
    private static void repairLinkInconsistencies(MasterDAO db, Collection<StructureObject> objects, Collection<StructureObject> modifiedObjects) {
        Map<StructureObject, List<StructureObject>> objectsByParentTh = StructureObjectUtils.splitByParentTrackHead(objects);
        for (StructureObject parentTh : objectsByParentTh.keySet()) {
            Selection sel = null;
            String selName = "linkError_pIdx"+parentTh.getIdx()+"_Position"+parentTh.getPositionName();
            for (StructureObject o : objectsByParentTh.get(parentTh)) {
                if (o.getNext()!=null && o.getNext().getPrevious()!=o) {
                    if (o.getNext().getPrevious()==null) linkObjects(o, o.getNext(), modifiedObjects);
                    else {
                        if (sel ==null) sel = db.getSelectionDAO().getOrCreate(selName, false);
                        sel.addElement(o);
                        sel.addElement(o.getNext());
                    }
                }
                if (o.getPrevious()!=null && o.getPrevious().getNext()!=o) {
                    if (o.getPrevious().getNext()==null) linkObjects(o.getPrevious(), o, modifiedObjects);
                    else {
                        if (sel ==null) sel = db.getSelectionDAO().getOrCreate(selName, false);
                        sel.addElement(o);
                        sel.addElement(o.getPrevious());
                    }
                }
            }
            if (sel!=null) {
                sel.setIsDisplayingObjects(true);
                sel.setIsDisplayingTracks(true);
                db.getSelectionDAO().store(sel);
            }
        }
        GUI.getInstance().populateSelections();
    }
    
    
    public static void resetObjectLinks(MasterDAO db, List<StructureObject> objects, boolean updateDisplay) {
        StructureObjectUtils.keepOnlyObjectsFromSameMicroscopyField(objects);
        StructureObjectUtils.keepOnlyObjectsFromSameStructureIdx(objects);
        if (objects.isEmpty()) return;
        
        if (updateDisplay) ImageWindowManagerFactory.getImageManager().removeTracks(StructureObjectUtils.getTrackHeads(objects));
        
        List<StructureObject> modifiedObjects = new ArrayList<StructureObject>();
        for (StructureObject o : objects) ManualCorrection.unlinkObject(o, modifiedObjects);
        Utils.removeDuplicates(modifiedObjects, false);
        db.getDao(objects.get(0).getPositionName()).store(modifiedObjects, true);
        if (updateDisplay) {
            // reload track-tree and update selection toDelete
            int parentStructureIdx = objects.get(0).getParent().getStructureIdx();
            GUI.getInstance().trackTreeController.updateParentTracks(GUI.getInstance().trackTreeController.getTreeIdx(parentStructureIdx));
            Set<StructureObject> uniqueTh = new HashSet<StructureObject>();
            for (StructureObject o : modifiedObjects) uniqueTh.add(o.getTrackHead());
            List<List<StructureObject>> trackToDisp = new ArrayList<List<StructureObject>>();
            for (StructureObject o : uniqueTh) trackToDisp.add(StructureObjectUtils.getTrack(o, true));
            // update current image
            ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
            if (!trackToDisp.isEmpty()) {
                iwm.displayTracks(null, null, trackToDisp, true);
                GUI.updateRoiDisplayForSelections(null, null);
            }
        }
    }
    
    public static void manualSegmentation(MasterDAO db, Image image, boolean test) {
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        if (image==null) {
            Object im = iwm.getDisplayer().getCurrentImage();
            if (im!=null) image = iwm.getDisplayer().getImage(im);
            if (image==null) {
                logger.warn("No image found");
                return;
            }
        }
        ImageObjectInterfaceKey key =  iwm.getImageObjectInterfaceKey(image);
        if (key==null) {
            logger.warn("Current image is not registered");
            return;
        }
        int structureIdx = key.displayedStructureIdx;
        int parentStructureIdx = db.getExperiment().getStructure(structureIdx).getSegmentationParentStructure();
        ManualSegmenter segmenter = db.getExperiment().getStructure(structureIdx).getManualSegmenter();
        
        if (segmenter==null) {
            logger.warn("No manual segmenter found for structure: {}", structureIdx);
            return;
        }
        
        Map<StructureObject, List<int[]>> points = iwm.getParentSelectedPointsMap(image, parentStructureIdx);
        if (points!=null) {
            logger.debug("manual segment: {} distinct parents. Segmentation structure: {}, parent structure: {}", points.size(), structureIdx, parentStructureIdx);
            List<StructureObject> segmentedObjects = new ArrayList<StructureObject>();
            for (Map.Entry<StructureObject, List<int[]>> e : points.entrySet()) {
                segmenter = db.getExperiment().getStructure(structureIdx).getManualSegmenter();
                segmenter.setManualSegmentationVerboseMode(test);
                
                Image segImage = e.getKey().getRawImage(structureIdx);
                
                // generate image mask without old objects
                ImageByte mask = TypeConverter.cast(e.getKey().getMask(), new ImageByte("Manual Segmentation Mask", 0, 0, 0));
                List<StructureObject> oldChildren = e.getKey().getChildren(structureIdx);
                for (StructureObject c : oldChildren) c.getObject().draw(mask, 0, new BoundingBox(0, 0, 0));
                if (test) iwm.getDisplayer().showImage(mask, 0, 1);
                // remove seeds out of mask
                Iterator<int[]> it=e.getValue().iterator();
                while(it.hasNext()) {
                    int[] seed = it.next();
                    if (!mask.insideMask(seed[0], seed[1], seed[2])) it.remove();
                }
                ObjectPopulation seg = segmenter.manualSegment(segImage, e.getKey(), mask, structureIdx, e.getValue());
                //seg.filter(new ObjectPopulation.Size().setMin(2)); // remove seeds
                logger.debug("{} children segmented in parent: {}", seg.getObjects().size(), e.getKey());
                if (!test && !seg.getObjects().isEmpty()) {
                    List<StructureObject> newChildren = e.getKey().setChildrenObjects(seg, structureIdx);
                    segmentedObjects.addAll(newChildren);
                    newChildren.addAll(oldChildren);
                    ArrayList<StructureObject> modified = new ArrayList<StructureObject>();
                    e.getKey().relabelChildren(structureIdx, modified);
                    modified.addAll(newChildren);
                    Utils.removeDuplicates(modified, false);
                    db.getDao(e.getKey().getPositionName()).store(modified, true);
                    
                    //Update tree
                    /*ObjectNode node = GUI.getInstance().objectTreeGenerator.getObjectNode(e.getKey());
                    if (node!=null && node.getParent()!=null) {
                        node.getParent().createChildren();
                        GUI.getInstance().objectTreeGenerator.reload(node.getParent());
                    }*/
                    //Update all opened images & objectImageInteraction
                    ImageWindowManagerFactory.getImageManager().reloadObjects(e.getKey(), structureIdx, false);
                }
            }
            // selected newly segmented objects on image
            ImageObjectInterface i = iwm.getImageObjectInterface(image);
            if (i!=null) {
                iwm.displayObjects(image, i.pairWithOffset(segmentedObjects), Color.ORANGE, true, false);
                GUI.updateRoiDisplayForSelections(image, i);
            }
        }
    }
    public static void splitObjects(MasterDAO db, Collection<StructureObject> objects, boolean updateDisplay, boolean test) {
        splitObjects(db, objects, updateDisplay, test, null);
    }
    public static void splitObjects(MasterDAO db, Collection<StructureObject> objects, boolean updateDisplay, boolean test, ObjectSplitter defaultSplitter) {
        int structureIdx = StructureObjectUtils.keepOnlyObjectsFromSameStructureIdx(objects);
        if (objects.isEmpty()) return;
        if (db==null) test = true;
        Experiment xp = db!=null ? db.getExperiment() : objects.iterator().next().getExperiment();
        ObjectSplitter splitter = defaultSplitter==null ? xp.getStructure(structureIdx).getObjectSplitter() : defaultSplitter;
        if (splitter==null) {
            logger.warn("No splitter configured");
            return;
        }
        Map<String, List<StructureObject>> objectsByFieldName = StructureObjectUtils.splitByFieldName(objects);
        for (String f : objectsByFieldName.keySet()) {
            ObjectDAO dao = db==null? null : db.getDao(f);
            List<StructureObject> objectsToStore = new ArrayList<>();
            List<StructureObject> newObjects = new ArrayList<>();
            for (StructureObject objectToSplit : objectsByFieldName.get(f)) {
                if (defaultSplitter==null) splitter = xp.getStructure(structureIdx).getObjectSplitter();
                splitter.setSplitVerboseMode(test);
                if (test) splitter.splitObject(objectToSplit.getRawImage(objectToSplit.getStructureIdx()), objectToSplit.getObject());
                else {
                    StructureObject newObject = objectToSplit.split(splitter);
                    if (newObject==null) logger.warn("Object could not be splitted!");
                    else {
                        newObjects.add(newObject);
                        objectToSplit.setAttribute(StructureObject.correctionSplit, true);
                        StructureObject prev = objectToSplit.getPrevious();
                        if (prev!=null) unlinkObjects(prev, objectToSplit, objectsToStore);
                        List<StructureObject> nexts = getNext(objectToSplit);
                        for (StructureObject n : nexts) unlinkObjects(objectToSplit, n, objectsToStore);
                        StructureObject next = nexts.size()==1 ? nexts.get(0) : null;
                        objectToSplit.getParent().relabelChildren(objectToSplit.getStructureIdx(), objectsToStore);
                        newObject.setAttribute(StructureObject.correctionSplitNew, true);
                        /*if (prev!=null && objectToSplit.getExperiment().getStructure(objectToSplit.getStructureIdx()).allowSplit()) {
                            linkObjects(prev, objectToSplit, objectsToStore);
                            linkObjects(prev, newObject, objectsToStore);
                        }
                        if (next!=null && objectToSplit.getExperiment().getStructure(objectToSplit.getStructureIdx()).allowMerge()) {
                            linkObjects(objectToSplit, next, objectsToStore);
                            linkObjects(newObject, next, objectsToStore);
                        }*/
                        objectsToStore.add(newObject);
                        objectsToStore.add(objectToSplit);
                    }
                }
            }
            
            Utils.removeDuplicates(objectsToStore, false);
            if (!test && dao!=null) dao.store(objectsToStore, true);
            if (updateDisplay && !test) {
                // unselect
                ImageWindowManagerFactory.getImageManager().hideLabileObjects(null);
                ImageWindowManagerFactory.getImageManager().removeObjects(objects, true);
                
                Set<StructureObject> parents = StructureObjectUtils.getParents(newObjects);
                //if (GUI.hasInstance() && GUI.getInstance().objectTreeGenerator!=null) {
                    for (StructureObject p : parents) {
                        //Update tree
                        //StructureNode node = GUI.getInstance().objectTreeGenerator.getObjectNode(p).getStructureNode(structureIdx);
                        //node.createChildren();
                        //GUI.getInstance().objectTreeGenerator.reload(node);
                        //Update all opened images & objectImageInteraction
                        ImageWindowManagerFactory.getImageManager().reloadObjects(p, structureIdx, false);
                    }
                //}
                // update selection
                ImageObjectInterface i = ImageWindowManagerFactory.getImageManager().getImageObjectInterface(null, structureIdx);
                if (i!=null) {
                    newObjects.addAll(objects);
                    Utils.removeDuplicates(newObjects, false);
                    ImageWindowManagerFactory.getImageManager().displayObjects(null, i.pairWithOffset(newObjects), Color.orange, true, false);
                    GUI.updateRoiDisplayForSelections(null, null);
                }
                // update trackTree
                GUI.getInstance().trackTreeController.updateParentTracks();
            }
        }
    }
    private static StructureObject getPreviousObject(List<StructureObject> list) {
        if (list.isEmpty()) return null;
        Iterator<StructureObject> it = list.iterator();
        StructureObject prev = it.next().getPrevious();
        if (prev==null) return null;
        while (it.hasNext()) {
            if (prev!=it.next().getPrevious()) return null;
        }
        return prev;
    }
    private static StructureObject getNextObject(List<StructureObject> list) {
        if (list.isEmpty()) return null;
        Iterator<StructureObject> it = list.iterator();
        StructureObject cur = it.next();
        StructureObject next = cur.getNext();
        if (next!=null && getNext(cur).size()>1) return null;
        while (it.hasNext()) {
            List<StructureObject> l = getNext(it.next());
            if (l.size()!=1 || l.get(0)!=next) return null;
        }
        return next;
    }
    public static void mergeObjects(MasterDAO db, Collection<StructureObject> objects, boolean updateDisplay) {
        int structureIdx = StructureObjectUtils.keepOnlyObjectsFromSameStructureIdx(objects);
        String fieldName = StructureObjectUtils.keepOnlyObjectsFromSameMicroscopyField(objects);
        if (objects.isEmpty()) return;
        ObjectDAO dao = db.getDao(fieldName);
        Map<StructureObject, List<StructureObject>> objectsByParent = StructureObjectUtils.splitByParent(objects);
        List<StructureObject> newObjects = new ArrayList<StructureObject>();
        for (StructureObject parent : objectsByParent.keySet()) {
            List<StructureObject> objectsToMerge = objectsByParent.get(parent);
            if (objectsToMerge.size()<=1) logger.warn("Merge Objects: select several objects from same parent!");
            else {
                StructureObject prev = getPreviousObject(objectsToMerge); // previous object if all objects have same previous object
                StructureObject next = getNextObject(objectsToMerge); // next object if all objects have same next object
                Set<StructureObject> modifiedObjects = new HashSet<>();
                for (StructureObject o : objectsToMerge) unlinkObject(o, modifiedObjects);
                StructureObject res = objectsToMerge.remove(0);               
                for (StructureObject toMerge : objectsToMerge) res.merge(toMerge);
                
                if (prev!=null) linkObjects(prev, res, modifiedObjects);
                if (next!=null) linkObjects(res, next, modifiedObjects);
                newObjects.add(res);
                res.setAttribute(correctionMerge, true);
                res.setAttribute(trackErrorNext, null);
                res.setAttribute(trackErrorPrev, null);
                if (res.getPrevious()!=null) res.getPrevious().setAttribute(trackErrorNext, null);
                if (res.getNext()!=null) res.getNext().setAttribute(trackErrorPrev, null);
                dao.delete(objectsToMerge, true, true, true);
                modifiedObjects.removeAll(objectsToMerge);
                modifiedObjects.add(res);
                Utils.removeDuplicates(modifiedObjects, false);
                dao.store(modifiedObjects, true);
            }
        }
        if (updateDisplay) {
            ImageWindowManagerFactory.getImageManager().hideLabileObjects(null);
            ImageWindowManagerFactory.getImageManager().removeObjects(objects, true);
            /*for (StructureObject newObject: newObjects) {
                //Update object tree
                ObjectNode node = GUI.getInstance().objectTreeGenerator.getObjectNode(newObject);
                node.getParent().createChildren();
                GUI.getInstance().objectTreeGenerator.reload(node.getParent());
            }*/
            Set<StructureObject> parents = StructureObjectUtils.getParentTrackHeads(newObjects);
            //Update all opened images & objectImageInteraction
            for (StructureObject p : parents) ImageWindowManagerFactory.getImageManager().reloadObjects(p, structureIdx, false);
            // update selection
            ImageObjectInterface i = ImageWindowManagerFactory.getImageManager().getImageObjectInterface(null, structureIdx);
            if (i!=null) {
                ImageWindowManagerFactory.getImageManager().displayObjects(null, i.pairWithOffset(newObjects), Color.orange, true, false);
                GUI.updateRoiDisplayForSelections(null, null);
            }
            // update trackTree
            GUI.getInstance().trackTreeController.updateParentTracks();
        }
    }
    public static void deleteObjects(MasterDAO db, Collection<StructureObject> objects, boolean updateDisplay) {
        String fieldName = StructureObjectUtils.keepOnlyObjectsFromSameMicroscopyField(objects);
        ObjectDAO dao = db.getDao(fieldName);
        Map<Integer, List<StructureObject>> objectsByStructureIdx = StructureObjectUtils.splitByStructureIdx(objects);
        for (int structureIdx : objectsByStructureIdx.keySet()) {
            List<StructureObject> toDelete = objectsByStructureIdx.get(structureIdx);
            Set<StructureObject> modifiedObjects = new HashSet<StructureObject>();
            for (StructureObject o : toDelete) {
                unlinkObject(o, modifiedObjects);
                o.getParent().getChildren(o.getStructureIdx()).remove(o);
            }
            Set<StructureObject> parents = StructureObjectUtils.getParents(toDelete);
            Set<StructureObject> parentTH = StructureObjectUtils.getParentTrackHeads(toDelete);
            logger.info("Deleting {} objects, from {} parents", toDelete.size(), parents.size());
            dao.delete(toDelete, true, true, true);
            modifiedObjects.removeAll(toDelete); // avoid storing deleted objects!!!
            dao.store(modifiedObjects, true);
            if (updateDisplay) {
                //Update selection on opened image
                //ImageWindowManagerFactory.getImageManager().hideLabileObjects(null);
                ImageWindowManagerFactory.getImageManager().removeObjects(toDelete, true);
                List<StructureObject> selTh = ImageWindowManagerFactory.getImageManager().getSelectedLabileTrackHeads(null);
                //Update object tree
                /*for (StructureObject s : parents) {
                    if (GUI.getInstance().objectTreeGenerator.getObjectNode(s)==null) {
                        //logger.error("Node not found for parent object: {}", s);
                    }
                    else {
                        StructureNode node = GUI.getInstance().objectTreeGenerator.getObjectNode(s).getStructureNode(structureIdx);
                        node.createChildren();
                        GUI.getInstance().objectTreeGenerator.reload(node);
                    }
                }*/
                //Update all opened images & objectImageInteraction
                for (StructureObject p : parentTH) ImageWindowManagerFactory.getImageManager().reloadObjects(p, structureIdx, false);
                ImageWindowManagerFactory.getImageManager().displayTracks(null, null, StructureObjectUtils.getTracks(selTh, true), true);
                GUI.updateRoiDisplayForSelections(null, null);
                
                // update trackTree
                GUI.getInstance().trackTreeController.updateParentTracks();
            }
        }
    }
    
    public static void repairLinksForXP(MasterDAO db, int structureIdx) {
        for (String f : db.getExperiment().getPositionsAsString()) repairLinksForField(db, f, structureIdx);
    }
    public static void repairLinksForField(MasterDAO db, String fieldName, int structureIdx) {
        logger.debug("repairing field: {}", fieldName);
        boolean allowSplit = db.getExperiment().getStructure(structureIdx).allowSplit();
        boolean allowMerge = db.getExperiment().getStructure(structureIdx).allowMerge();
        logger.debug("allow Split: {}, allow merge: {}", allowSplit, allowMerge);
        int count = 0, countUncorr=0, count2=0, countUncorr2=0, countTh=0;
        ObjectDAO dao = db.getDao(fieldName);
        List<StructureObject> modifiedObjects = new ArrayList<StructureObject>();
        List<StructureObject> uncorrected = new ArrayList<StructureObject>();
        for (StructureObject root : dao.getRoots()) {
            for (StructureObject o : root.getChildren(structureIdx)) {
                if (o.getNext()!=null) {
                    if (o.getNext().getPrevious()!=o) {
                        logger.debug("inconsitency: o: {}, next: {}, next's previous: {}", o, o.getNext(), o.getNext().getPrevious());
                        if (o.getNext().getPrevious()==null) ManualCorrection.linkObjects(o, o.getNext(), modifiedObjects);
                        else if (!allowMerge) {
                            uncorrected.add(o);
                            uncorrected.add(o.getNext());
                            countUncorr++;
                        }
                        ++count;
                    } else if (!allowMerge && o.getNext().getTrackHead()!=o.getTrackHead()) {
                        logger.debug("inconsitency on TH: o: {}, next: {}, o's th: {}, next's th: {}", o, o.getNext(), o.getTrackHead(), o.getNext().getTrackHead());
                        countTh++;
                        o.getNext().setTrackHead(o.getTrackHead(), false, true, modifiedObjects);
                    }
                    
                }
                if (o.getPrevious()!=null) {
                    if (o.getPrevious().getNext()!=o) {
                        if (o.getPrevious().getNext()==null) ManualCorrection.linkObjects(o.getPrevious(), o, modifiedObjects);
                        else if (!allowSplit) {
                            uncorrected.add(o);
                            uncorrected.add(o.getPrevious());
                            countUncorr2++;
                        }
                        logger.debug("inconsitency: o: {}, previous: {}, previous's next: {}", o, o.getPrevious(), o.getPrevious().getNext());
                        ++count2;
                    } else if (!allowSplit && o.getPrevious().getTrackHead()!=o.getTrackHead()) {
                        logger.debug("inconsitency on TH: o: {}, previous: {}, o's th: {}, preivous's th: {}", o, o.getPrevious(), o.getTrackHead(), o.getPrevious().getTrackHead());
                        countTh++;
                        o.setTrackHead(o.getPrevious().getTrackHead(), false, true, modifiedObjects);
                    }
                }
            }
        }
        logger.debug("total errors: type 1 : {}, uncorrected: {}, type 2: {}, uncorrected: {}, type trackHead: {}", count, countUncorr, count2, countUncorr2, countTh);
        Map<StructureObject, List<StructureObject>> uncorrByParentTH = StructureObjectUtils.splitByParentTrackHead(uncorrected);
        
        // create selection of uncorrected
        for (StructureObject parentTh : uncorrByParentTH.keySet()) {
            String selectionName = "linkError_pIdx"+parentTh.getIdx()+"_Position"+fieldName;
            Selection sel = db.getSelectionDAO().getOrCreate(selectionName, false);
            sel.addElements(uncorrByParentTH.get(parentTh));
            sel.setIsDisplayingObjects(true);
            sel.setIsDisplayingTracks(true);
            db.getSelectionDAO().store(sel);
        }
        Utils.removeDuplicates(modifiedObjects, false);
        dao.store(modifiedObjects, true);
    }
    
    public static void deleteAllObjectsFromFrame(MasterDAO db, boolean after) {
        List<StructureObject> selList = ImageWindowManagerFactory.getImageManager().getSelectedLabileObjects(null);
        if (!selList.isEmpty()) {
            StructureObject first = Collections.min(selList, (o1, o2) -> Integer.compare(o1.getFrame(), o2.getFrame()));
            List<StructureObject> toDelete = Pair.unpairKeys(ImageWindowManagerFactory.getImageManager().getCurrentImageObjectInterface().getObjects());
            if (after) toDelete.removeIf(o -> o.getFrame()<first.getFrame());
            else toDelete.removeIf(o -> o.getFrame()>first.getFrame());
            deleteObjects(db, toDelete, true);
        }
    }
    
}
