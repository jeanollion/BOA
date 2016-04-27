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
import dataStructure.objects.MasterDAO;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.TypeConverter;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import plugins.ManualSegmenter;
import plugins.ObjectSplitter;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class ManualCorrection {
    public static void unlinkObject(StructureObject o) {
        if (o.getNext()!=null) o.getNext().setTrackHead(o.getNext(), true, true);
        o.resetTrackLinks();
        //logger.debug("unlinking: {}", o);
    }
    public static void unlinkObjects(StructureObject prev, StructureObject next, List<List<StructureObject>> modifiedTracks) {
        if (next.getTimePoint()<prev.getTimePoint()) unlinkObjects(next, prev, modifiedTracks);
        else {
            next.setTrackHead(next, true, true);
            //logger.debug("unlinking.. previous: {}, previous's next: {}", sel.get(1).getPrevious(), sel.get(0).getNext());
            prev.setTrackFlag(StructureObject.TrackFlag.correctionSplit);
            next.setTrackFlag(StructureObject.TrackFlag.correctionSplit);
            if (modifiedTracks!=null) {
                modifiedTracks.add(StructureObjectUtils.getTrack(prev.getTrackHead(), true));
                modifiedTracks.add(StructureObjectUtils.getTrack(next, true));
            }
            //logger.debug("unlinking: {} to {}", sel.get(0), sel.get(1));
        }
        
    }
    public static void linkObjects(StructureObject prev, StructureObject next, List<List<StructureObject>> modifiedTracks) {
        if (next.getTimePoint()<prev.getTimePoint()) linkObjects(next, prev, modifiedTracks);
        else {
            if (prev.getNext()==next || next.getPrevious()==prev) {
                logger.warn("spots are already linked");
                return;
            }
            // unlinking each of the two spots
            if (next.getPrevious()!=null) unlinkObjects(next.getPrevious(), next, modifiedTracks);
            if (prev.getNext()!=null) unlinkObjects(prev, prev.getNext(), modifiedTracks);
            next.setPreviousInTrack(prev, false);
            next.setTrackHead(prev.getTrackHead(), false, true);
            next.setTrackFlag(StructureObject.TrackFlag.correctionMerge);
            modifiedTracks.add(StructureObjectUtils.getTrack(prev.getTrackHead(), true));
            //logger.debug("linking: {} to {}", prev, next);
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
        int structureIdx = key.childStructureIdx;
        int parentStructureIdx = db.getExperiment().getStructure(structureIdx).getParentStructure();
        ManualSegmenter segmenter = db.getExperiment().getStructure(structureIdx).getManualSegmenter();
        
        if (segmenter==null) {
            logger.warn("No manual segmenter found for structure: {}", structureIdx);
            return;
        }
        segmenter.setManualSegmentationVerboseMode(test);
        Map<StructureObject, List<int[]>> points = iwm.getParentSelectedPointsMap(image, parentStructureIdx);
        if (points!=null) {
            logger.debug("manual segment: {} distinct parents. Segmentation structure: {}, parent structure: {}", points.size(), structureIdx, parentStructureIdx);
            List<StructureObject> segmentedObjects = new ArrayList<StructureObject>();
            for (Map.Entry<StructureObject, List<int[]>> e : points.entrySet()) {
                Image segImage = e.getKey().getRawImage(structureIdx);
                
                // generate image mask without old objects
                ImageByte mask = TypeConverter.cast(e.getKey().getMask(), new ImageByte("Manual Segmentation Mask", 0, 0, 0));
                ArrayList<StructureObject> oldChildren = e.getKey().getChildren(structureIdx);
                for (StructureObject c : oldChildren) c.getObject().draw(mask, 0, new BoundingBox(0, 0, 0));
                if (test) iwm.getDisplayer().showImage(mask, 0, 1);
                
                ObjectPopulation seg = segmenter.manualSegment(segImage, e.getKey(), mask, structureIdx, e.getValue());
                seg.filter(new ObjectPopulation.Size().setMin(2)); // remove seeds
                logger.debug("{} children segmented in parent: {}", seg.getObjects().size(), e.getKey());
                if (!test && !seg.getObjects().isEmpty()) {
                    ArrayList<StructureObject> newChildren = e.getKey().setChildrenObjects(seg, structureIdx);
                    segmentedObjects.addAll(newChildren);
                    newChildren.addAll(oldChildren);
                    ArrayList<StructureObject> modified = new ArrayList<StructureObject>();
                    e.getKey().relabelChildren(structureIdx, modified);
                    modified.addAll(newChildren);
                    Utils.removeDuplicates(modified, false);
                    db.getDao(e.getKey().getFieldName()).store(modified, true);
                    
                    //Update tree
                    ObjectNode node = GUI.getInstance().objectTreeGenerator.getObjectNode(e.getKey());
                    node.getParent().createChildren();
                    GUI.getInstance().objectTreeGenerator.reload(node.getParent());
                    
                    //Update all opened images & objectImageInteraction
                    ImageWindowManagerFactory.getImageManager().reloadObjects(e.getKey(), structureIdx, false);
                }
            }
            // selected newly segmented objects on image
            ImageObjectInterface i = iwm.getImageObjectInterface(image);
            if (i!=null) iwm.displayObjects(image, i.pairWithOffset(segmentedObjects), Color.ORANGE, true, false);
        }
    }
    public static void splitObjects(MasterDAO db, Collection<StructureObject> objects, boolean updateDisplay) {
        int structureIdx = StructureObjectUtils.keepOnlyObjectsFromSameStructureIdx(objects);
        if (objects.isEmpty()) return;
        ObjectSplitter splitter = db.getExperiment().getStructure(structureIdx).getObjectSplitter();
        Map<String, List<StructureObject>> objectsByFieldName = StructureObjectUtils.splitByFieldName(objects);
        for (String f : objectsByFieldName.keySet()) {
            ObjectDAO dao = db.getDao(f);
            List<StructureObject> objectsToStore = new ArrayList<StructureObject>();
            List<StructureObject> newObjects = new ArrayList<StructureObject>();
            for (StructureObject objectToSplit : objectsByFieldName.get(f)) {
                StructureObject newObject = objectToSplit.split(splitter);
                if (newObject==null) logger.warn("Object could not be splitted!");
                else {
                    newObjects.add(newObject);
                    objectToSplit.getParent().relabelChildren(objectToSplit.getStructureIdx(), objectsToStore);
                    objectsToStore.add(newObject);
                    objectsToStore.add(objectToSplit);
                }
            }
            
            Utils.removeDuplicates(objectsToStore, false);
            dao.store(objectsToStore, true);
            if (updateDisplay) {
                // unselect
                ImageWindowManagerFactory.getImageManager().hideLabileObjects(null);
                ImageWindowManagerFactory.getImageManager().removeObjects(objects, true);
                
                Set<StructureObject> parents = StructureObjectUtils.getParents(newObjects);
                for (StructureObject p : parents) {
                    //Update tree
                    StructureNode node = GUI.getInstance().objectTreeGenerator.getObjectNode(p).getStructureNode(structureIdx);
                    node.createChildren();
                    GUI.getInstance().objectTreeGenerator.reload(node);
                    //Update all opened images & objectImageInteraction
                    ImageWindowManagerFactory.getImageManager().reloadObjects(p, structureIdx, false);
                }
                // update selection
                ImageObjectInterface i = ImageWindowManagerFactory.getImageManager().getImageObjectInterface(null, structureIdx);
                if (i!=null) {
                    newObjects.addAll(objects);
                    ImageWindowManagerFactory.getImageManager().displayObjects(null, i.pairWithOffset(newObjects), Color.orange, true, false);
                }
                // update trackTree
                GUI.getInstance().trackTreeController.updateParentTracks();
            }
        }
    }
    public static void mergeObjects(MasterDAO db, Collection<StructureObject> objects, boolean updateDisplay) {
        int structureIdx = StructureObjectUtils.keepOnlyObjectsFromSameStructureIdx(objects);
        String fieldName = StructureObjectUtils.keepOnlyObjectsFromSameMicroscopyField(objects);
        if (objects.isEmpty()) return;
        ObjectDAO dao = db.getDao(fieldName);
        Map<StructureObject, List<StructureObject>> objectsByParent = StructureObjectUtils.splitByParent(objects);
        List<StructureObject> newObjects = new ArrayList<StructureObject>();
        List<StructureObject> objectsToRemove = new ArrayList<StructureObject>();
        for (StructureObject parent : objectsByParent.keySet()) {
            List<StructureObject> objectsToMerge = objectsByParent.get(parent);
            if (objectsToMerge.size()<=1) logger.warn("Merge Objects: select several objects from same parent!");
            else {
                StructureObject res = objectsToMerge.remove(0);
                objectsToRemove.addAll(objectsToMerge);
                for (StructureObject o : objectsToMerge) unlinkObject(o);
                ArrayList<StructureObject> siblings = res.getParent().getChildren(res.getStructureIdx());
                List<StructureObject> modified = new ArrayList<StructureObject>(siblings.size());
                for (StructureObject toMerge : objectsToMerge) {
                    res.merge(toMerge);
                    siblings.remove(toMerge);
                    unlinkObject(toMerge);
                }
                newObjects.add(res);
                modified.add(res);
                res.getParent().relabelChildren(structureIdx, modified);
                dao.delete(objectsToMerge, false);
                dao.store(modified, true);
            }
        }
        if (updateDisplay) {
            ImageWindowManagerFactory.getImageManager().hideLabileObjects(null);
            ImageWindowManagerFactory.getImageManager().removeObjects(objects, true);
            for (StructureObject newObject: newObjects) {
                //Update object tree
                ObjectNode node = GUI.getInstance().objectTreeGenerator.getObjectNode(newObject);
                node.getParent().createChildren();
                GUI.getInstance().objectTreeGenerator.reload(node.getParent());
            }
            Set<StructureObject> parents = StructureObjectUtils.getParents(newObjects);
            //Update all opened images & objectImageInteraction
            for (StructureObject p : parents) ImageWindowManagerFactory.getImageManager().reloadObjects(p, structureIdx, false);
            // update selection
            ImageObjectInterface i = ImageWindowManagerFactory.getImageManager().getImageObjectInterface(null, structureIdx);
            if (i!=null) {
                ImageWindowManagerFactory.getImageManager().displayObjects(null, i.pairWithOffset(newObjects), Color.orange, true, false);
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
            List<StructureObject> list = objectsByStructureIdx.get(structureIdx);
            for (StructureObject o : list) {
                unlinkObject(o);
                o.getParent().getChildren(o.getStructureIdx()).remove(o);
            }
            Set<StructureObject> parents = StructureObjectUtils.getParents(list);
            logger.info("Deleting {} objects, from {} parents", list.size(), parents.size());
            dao.delete(list, true);
            ArrayList<StructureObject> modified = new ArrayList<StructureObject>();
            for (StructureObject p : parents) p.relabelChildren(structureIdx, modified);
            dao.store(modified, true);
            
            if (updateDisplay) {
                //Update selection on opened image
                ImageWindowManagerFactory.getImageManager().hideLabileObjects(null);
                ImageWindowManagerFactory.getImageManager().removeObjects(list, true);
                //Update object tree
                for (StructureObject s : parents) {
                    StructureNode node = GUI.getInstance().objectTreeGenerator.getObjectNode(s).getStructureNode(structureIdx);
                    node.createChildren();
                    GUI.getInstance().objectTreeGenerator.reload(node);
                }
                //Update all opened images & objectImageInteraction
                for (StructureObject p : parents) ImageWindowManagerFactory.getImageManager().reloadObjects(p, structureIdx, false);
                // update trackTree
                GUI.getInstance().trackTreeController.updateParentTracks();
            }
        }
    }
}
