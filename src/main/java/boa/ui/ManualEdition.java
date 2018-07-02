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
package boa.ui;

import boa.ui.GUI;
import static boa.ui.GUI.logger;
import boa.gui.image_interaction.InteractiveImage;
import boa.gui.image_interaction.InteractiveImageKey;
import boa.gui.image_interaction.ImageWindowManager;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.gui.objects.ObjectNode;
import boa.gui.objects.StructureNode;
import boa.data_structure.SelectionUtils;
import boa.configuration.parameters.PreFilterSequence;
import boa.configuration.experiment.Experiment;
import boa.configuration.parameters.TrackPreFilterSequence;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.Region;
import boa.data_structure.dao.ObjectDAO;
import boa.data_structure.RegionPopulation;
import boa.data_structure.Selection;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import fiji.plugin.trackmate.Spot;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.image.ImageMask2D;
import boa.image.TypeConverter;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import boa.measurement.GeometricalMeasurements;
import boa.plugins.ManualSegmenter;
import boa.plugins.ObjectSplitter;
import boa.plugins.Segmenter;
import boa.plugins.plugins.trackers.trackmate.TrackMateInterface;
import boa.utils.HashMapGetCreate;
import boa.utils.Pair;
import boa.utils.Utils;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.function.BiPredicate;
import static boa.data_structure.StructureObject.TRACK_ERROR_NEXT;
import static boa.data_structure.StructureObject.TRACK_ERROR_PREV;
import boa.plugins.TrackConfigurable;
import boa.plugins.TrackConfigurable.TrackConfigurer;

/**
 *
 * @author jollion
 */
public class ManualEdition {

    public static final BiPredicate<StructureObject, StructureObject> NERVE_MERGE = (s1, s2)->false;
    public static final BiPredicate<StructureObject, StructureObject> ALWAYS_MERGE = (s1, s2)->true;
    public static final BiPredicate<StructureObject, StructureObject> MERGE_TRACKS_SIZE_COND = (prev, next)-> next.getRegion().size()>prev.getRegion().size()  * 0.8;
    
    private static List<StructureObject> getNext(StructureObject o) {
        StructureObject nextParent = o.getNext()==null ? o.getParent().getNext() : o.getNext().getParent();
        if (nextParent==null) return Collections.EMPTY_LIST;
        List<StructureObject> res = new ArrayList(nextParent.getChildren(o.getStructureIdx()));
        res.removeIf(e -> !o.equals(e.getPrevious()) && !e.equals(o.getNext()));
        //logger.debug("next of : {} = {}", o, res);
        return res;
    }
    private static List<StructureObject> getPrevious(StructureObject o) {
        StructureObject nextParent = o.getPrevious()==null ? o.getParent().getPrevious() : o.getPrevious().getParent();
        if (nextParent==null) return Collections.EMPTY_LIST;
        List<StructureObject> res = new ArrayList(nextParent.getChildren(o.getStructureIdx()));
        res.removeIf(e -> !o.equals(e.getNext()) && !e.equals(o.getPrevious()));
        //logger.debug("prev of : {} = {}", o, res);
        return res;
    }
    public static void unlinkObject(StructureObject o, BiPredicate<StructureObject, StructureObject> mergeTracks, Collection<StructureObject> modifiedObjects) {
        if (o==null) return;
        for (StructureObject n : getNext(o) ) unlinkObjects(o, n, mergeTracks, modifiedObjects);
        for (StructureObject p : getPrevious(o) ) unlinkObjects(p, o, mergeTracks, modifiedObjects);
        //o.resetTrackLinks(true, true);
        modifiedObjects.add(o);
        //logger.debug("unlinking: {}", o);
    }
    private static void removeError(StructureObject o, boolean next, boolean prev) {
        String value = null;
        if (prev) o.getMeasurements().setStringValue(TRACK_ERROR_PREV, value);
        if (next) o.getMeasurements().setStringValue(TRACK_ERROR_NEXT, value);
        
    }
    
    public static void unlinkObjects(StructureObject prev, StructureObject next, BiPredicate<StructureObject, StructureObject> mergeTracks, Collection<StructureObject> modifiedObjects) {
        if (next.getFrame()<prev.getFrame()) unlinkObjects(next, prev, mergeTracks, modifiedObjects);
        else {
            if (next.getPrevious()==prev) {
                next.resetTrackLinks(true, false);
                next.setAttribute(StructureObject.EDITED_LINK_PREV, true);
                if (next.getPrevious()!=null) next.getPrevious().setAttribute(StructureObject.EDITED_LINK_NEXT, true);
                next.setTrackHead(next, true, true, modifiedObjects);
            } else prev.resetTrackLinks(false, prev.getNext()==next);
            
            List<StructureObject> allNext = getNext(prev); 
            if (allNext.size()==1 && mergeTracks.test(prev, allNext.get(0))) { // set trackHead 
                unlinkObjects(prev, allNext.get(0), NERVE_MERGE, modifiedObjects);
                linkObjects(prev, allNext.get(0), true, modifiedObjects);
            }
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
    public static void updateObjects(List<Pair<StructureObject, Region>> objectsToUpdate, boolean updateDisplay) {
        if (objectsToUpdate==null || objectsToUpdate.isEmpty()) return;
        for (Pair<StructureObject, Region> p : objectsToUpdate) {
            p.key.setRegion(p.value);
            p.key.getDAO().store(p.key);
        }
        if (updateDisplay) updateDisplayAndSelectObjects(Pair.unpairKeys(objectsToUpdate));
    }
    public static void linkObjects(StructureObject prev, StructureObject next, boolean allowDoubleLink, Collection<StructureObject> modifiedObjects) {
        if (next.getFrame()<prev.getFrame()) linkObjects(next, prev, allowDoubleLink, modifiedObjects);
        else {
            boolean allowMerge = prev.getExperiment().getStructure(prev.getStructureIdx()).allowMerge();
            boolean allowSplit = prev.getExperiment().getStructure(prev.getStructureIdx()).allowSplit();
            boolean doubleLink = allowDoubleLink;
            List<StructureObject> allNext = getNext(prev);
            if (allowSplit) {
                if (allNext.contains(next)? allNext.size()>1 : !allNext.isEmpty()) doubleLink = false;
            }
            List<StructureObject> allPrev = getPrevious(next);
            if (allowMerge) {
                if (allPrev.contains(prev) ? allPrev.size()>1 : !allPrev.isEmpty()) doubleLink = false;
            }
            if (allowMerge && !doubleLink) { // mergeLink
                doubleLink = false;
                boolean allowMergeLink = true;
                if (!allNext.contains(next)) {
                    if (!allowSplit) {
                        for (StructureObject n : allNext) unlinkObjects(prev, n, ALWAYS_MERGE, modifiedObjects);
                    } else allowMergeLink = false;
                }
                if (allowMergeLink && !allNext.contains(next)) {
                    prev.setTrackLinks(next, false, true);
                    prev.setAttribute(StructureObject.EDITED_LINK_NEXT, true);
                    if (modifiedObjects!=null) modifiedObjects.add(prev);
                    //logger.debug("merge link : {}>{}", prev, next);
                }
                
            }
            if (allowSplit && !doubleLink) { // split link
                doubleLink=false;
                boolean allowSplitLink = true;
                if (!allPrev.contains(prev)) {
                    if (!allowMerge) {
                        for (StructureObject p : allPrev) unlinkObjects(p, next, ALWAYS_MERGE, modifiedObjects);
                    } else allowSplitLink = false;
                }
                if (allowSplitLink && !allPrev.contains(prev)) {
                    prev.setTrackLinks(next, true, false);
                    next.setAttribute(StructureObject.EDITED_LINK_PREV, true);
                    if (modifiedObjects!=null) modifiedObjects.add(next);
                    for (StructureObject o : allNext) {
                        o.setTrackHead(o, true, true, modifiedObjects);
                        o.setAttribute(StructureObject.EDITED_LINK_PREV, true);
                        if (modifiedObjects!=null) modifiedObjects.add(o);
                    }
                    //logger.debug("split link : {}>{}", prev, next);
                }
                
            }
            if (doubleLink) {
                if (prev.getNext()!=null && prev.getNext()!=next) unlinkObjects(prev, prev.getNext(), ALWAYS_MERGE, modifiedObjects);
                if (next.getPrevious()!=null && next.getPrevious()!=prev) unlinkObjects(next.getPrevious(), next, ALWAYS_MERGE, modifiedObjects);
                //if (next!=prev.getNext() || prev!=next.getPrevious() || next.getTrackHead()!=prev.getTrackHead()) {
                    prev.setTrackLinks(next, true, true);
                    prev.setAttribute(StructureObject.EDITED_LINK_NEXT, true);
                    next.setAttribute(StructureObject.EDITED_LINK_PREV, true);
                    next.setTrackHead(prev.getTrackHead(), false, true, modifiedObjects);
                    if (modifiedObjects!=null) modifiedObjects.add(prev);
                    if (modifiedObjects!=null) modifiedObjects.add(next);
                    //logger.debug("double link : {}+{}, th:{}", prev, next, prev.getTrackHead());
                //}
            }
        }
    }
    public static void prune(MasterDAO db, Collection<StructureObject> objects, BiPredicate<StructureObject, StructureObject> mergeTracks, boolean updateDisplay) {
        if (objects.isEmpty()) return;
        TreeSet<StructureObject> queue = new TreeSet<>(objects);
        Set<StructureObject> toDel = new HashSet<>();
        while(!queue.isEmpty()) {
            StructureObject o = queue.pollFirst();
            toDel.add(o);
            List<StructureObject> next = getNext(o);
            toDel.addAll(next);
            queue.addAll(next);
        }
        deleteObjects(db, toDel, mergeTracks, updateDisplay);
    }
    public static void modifyObjectLinks(MasterDAO db, List<StructureObject> objects, boolean unlink, boolean updateDisplay) {
        StructureObjectUtils.keepOnlyObjectsFromSameMicroscopyField(objects);
        StructureObjectUtils.keepOnlyObjectsFromSameStructureIdx(objects);
        if (objects.size()<=1) return;
        
        if (updateDisplay) ImageWindowManagerFactory.getImageManager().removeTracks(StructureObjectUtils.getTrackHeads(objects));
        int structureIdx = objects.get(0).getStructureIdx();
        boolean merge = db.getExperiment().getStructure(structureIdx).allowMerge();
        boolean split = db.getExperiment().getStructure(structureIdx).allowSplit();
        
        List<StructureObject> modifiedObjects = new ArrayList<>();
        TreeMap<StructureObject, List<StructureObject>> objectsByParent = new TreeMap(StructureObjectUtils.splitByParent(objects)); // sorted by time point
        StructureObject prevParent = null;
        List<StructureObject> prev = null;
        //logger.debug("modify: unlink: {}, #objects: {}, #parents: {}", unlink, objects.size(), objectsByParent.keySet().size());
        Map<Integer, List<StructureObject>> map = new HashMap<>();
        for (StructureObject currentParent : objectsByParent.keySet()) {
            List<StructureObject> current = objectsByParent.get(currentParent);
            Collections.sort(current);
            //logger.debug("prevParent: {}, currentParent: {}, #objects: {}", prevParent, currentParent, current.size());
            if (prevParent!=null && prevParent.getFrame()<currentParent.getFrame()) {
                //if (prev!=null) logger.debug("prev: {}, prevTh: {}, prevIsTh: {}, prevPrev: {}, prevNext: {}", Utils.toStringList(prev), Utils.toStringList(prev, o->o.getTrackHead()), Utils.toStringList(prev, o->o.isTrackHead()), Utils.toStringList(prev, o->o.getPrevious()), Utils.toStringList(prev, o->o.getNext()));
                //logger.debug("current: {}, currentTh: {}, currentIsTh: {}, currentPrev: {}, currentNext: {}", Utils.toStringList(current), Utils.toStringList(current, o->o.getTrackHead()), Utils.toStringList(current, o->o.isTrackHead()), Utils.toStringList(current, o->o.getPrevious()), Utils.toStringList(current, o->o.getNext()));
                if (prev.size()==1 && current.size()==1) {
                    if (unlink) {
                        if (current.get(0).getPrevious()==prev.get(0) || prev.get(0).getNext()==current) { //unlink the 2 spots
                            ManualEdition.unlinkObjects(prev.get(0), current.get(0), ALWAYS_MERGE, modifiedObjects);
                        } 
                    } else ManualEdition.linkObjects(prev.get(0), current.get(0), true, modifiedObjects);
                } else if (prev.size()==1 && split && !merge) {
                    for (StructureObject c : current) {
                        if (unlink) {
                            if (c.getPrevious()==prev.get(0) || prev.get(0).getNext()==c) { //unlink the 2 spots
                                ManualEdition.unlinkObjects(prev.get(0), c, ALWAYS_MERGE, modifiedObjects);
                            } 
                        } else ManualEdition.linkObjects(prev.get(0), c, false, modifiedObjects);
                    }
                } else if (current.size()==1 && !split && merge) {
                    for (StructureObject p : prev) {
                        if (unlink) {
                            if (current.get(0).getPrevious()==p || p.getNext()==current.get(0)) { //unlink the 2 spots
                                ManualEdition.unlinkObjects(p, current.get(0), ALWAYS_MERGE, modifiedObjects);
                            } 
                        } else ManualEdition.linkObjects(p, current.get(0), false, modifiedObjects);
                    }
                } else { // link closest object
                    map.put(prevParent.getFrame(), prev);
                    map.put(currentParent.getFrame(), current);
                    // unlink objects
                    for (StructureObject n : current) {
                        if (prev.contains(n.getPrevious())) ManualEdition.unlinkObjects(n.getPrevious(), n, ALWAYS_MERGE, modifiedObjects);
                    }
                    for (StructureObject p : prev) {
                        if (current.contains(p.getNext())) ManualEdition.unlinkObjects(p, p.getNext(), ALWAYS_MERGE, modifiedObjects);
                    }
                }
                
            } 
            prevParent=currentParent;
            prev = current;
        }
        if (!map.isEmpty() && !unlink) {
            List<StructureObject> allObjects = Utils.flattenMap(map);
            TrackMateInterface<Spot> tmi = new TrackMateInterface(TrackMateInterface.defaultFactory());
            tmi.addObjects(map);
            //double meanLength = allObjects.stream().mapToDouble( s->GeometricalMeasurements.getFeretMax(s.getRegion())).average().getAsDouble();
            //logger.debug("Mean size: {}", meanLength);
            tmi.processFTF(Math.sqrt(Double.MAX_VALUE)/100); // not Double.MAX_VALUE -> causes trackMate to crash possibly because squared.. 
            tmi.processGC(Math.sqrt(Double.MAX_VALUE)/100, 0, split, merge);
            tmi.setTrackLinks(map, modifiedObjects);
            modifiedObjects.addAll(allObjects);
        }
        //repairLinkInconsistencies(db, modifiedObjects, modifiedObjects);
        Utils.removeDuplicates(modifiedObjects, false);
        db.getDao(objects.get(0).getPositionName()).store(modifiedObjects);
        if (updateDisplay) {
            // reload track-tree and update selection toDelete
            int parentStructureIdx = objects.get(0).getParent().getStructureIdx();
            if (GUI.getInstance().trackTreeController!=null) GUI.getInstance().trackTreeController.updateParentTracks(GUI.getInstance().trackTreeController.getTreeIdx(parentStructureIdx));
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
    
    public static void createTracks(MasterDAO db, Collection<StructureObject> futureTrackHeads, boolean updateDisplay) {
        if (futureTrackHeads.isEmpty()) return;
        if (updateDisplay) ImageWindowManagerFactory.getImageManager().removeTracks(StructureObjectUtils.getTrackHeads(futureTrackHeads));
        for (Entry<String, List<StructureObject>> e : StructureObjectUtils.splitByPosition(futureTrackHeads).entrySet()) {
            Set<StructureObject> modifiedObjects = new HashSet<>();
            e.getValue().forEach(o->{
                o.setTrackHead(o, true, true, modifiedObjects);
                o.setAttribute(StructureObject.EDITED_LINK_PREV, true);
            });
            db.getDao(e.getKey()).store(modifiedObjects);
            if (updateDisplay) {
                int parentStructureIdx = futureTrackHeads.iterator().next().getParent().getStructureIdx();
                if (GUI.getInstance().trackTreeController!=null) GUI.getInstance().trackTreeController.updateParentTracks(GUI.getInstance().trackTreeController.getTreeIdx(parentStructureIdx));
                //List<List<StructureObject>> tracks = this.trackTreeController.getGeneratorS().get(structureIdx).getSelectedTracks(true);
                // get unique tracks to display
                Set<StructureObject> uniqueTh = new HashSet<>();
                for (StructureObject o : modifiedObjects) uniqueTh.add(o.getTrackHead());
                List<List<StructureObject>> trackToDisp = new ArrayList<>();
                for (StructureObject o : uniqueTh) trackToDisp.add(StructureObjectUtils.getTrack(o, true));
                // update current image
                ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
                if (!trackToDisp.isEmpty()) {
                    iwm.displayTracks(null, null, trackToDisp, true);
                    //GUI.updateRoiDisplayForSelections(null, null);
                }
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
                    if (o.getNext().getPrevious()==null) linkObjects(o, o.getNext(), true, modifiedObjects);
                    else {
                        if (sel ==null) sel = db.getSelectionDAO().getOrCreate(selName, false);
                        sel.addElement(o);
                        sel.addElement(o.getNext());
                    }
                }
                if (o.getPrevious()!=null && o.getPrevious().getNext()!=o) {
                    if (o.getPrevious().getNext()==null) linkObjects(o.getPrevious(), o, true, modifiedObjects);
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
        for (StructureObject o : objects) ManualEdition.unlinkObject(o, ALWAYS_MERGE, modifiedObjects);
        Utils.removeDuplicates(modifiedObjects, false);
        db.getDao(objects.get(0).getPositionName()).store(modifiedObjects);
        if (updateDisplay) {
            // reload track-tree and update selection toDelete
            int parentStructureIdx = objects.get(0).getParent().getStructureIdx();
            if (GUI.getInstance().trackTreeController!=null) GUI.getInstance().trackTreeController.updateParentTracks(GUI.getInstance().trackTreeController.getTreeIdx(parentStructureIdx));
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
        InteractiveImageKey key =  iwm.getImageObjectInterfaceKey(image);
        if (key==null) {
            logger.warn("Current image is not registered");
            return;
        }
        int structureIdx = key.displayedStructureIdx;
        int segmentationParentStructureIdx = db.getExperiment().getStructure(structureIdx).getSegmentationParentStructure();
        int parentStructureIdx = db.getExperiment().getStructure(structureIdx).getParentStructure();
        ManualSegmenter segInstance = db.getExperiment().getStructure(structureIdx).getManualSegmenter();
        
        if (segInstance==null) {
            logger.warn("No manual segmenter found for structure: {}", structureIdx);
            return;
        }
        
        Map<StructureObject, List<int[]>> points = iwm.getParentSelectedPointsMap(image, segmentationParentStructureIdx);
        if (points!=null && !points.isEmpty()) {
            String[] positions = points.keySet().stream().map(p->p.getPositionName()).distinct().toArray(i->new String[i]);
            if (positions.length>1) throw new IllegalArgumentException("All points should come from same parent");
            ensurePreFilteredImages(points.keySet().stream(), structureIdx, db.getExperiment(), db.getDao(positions[0]));
            ManualSegmenter s = db.getExperiment().getStructure(structureIdx).getManualSegmenter();
            HashMap<StructureObject, TrackConfigurer> parentThMapParam = new HashMap<>();
            if (s instanceof TrackConfigurable) {
                points.keySet().stream().map(p->p.getParent(parentStructureIdx)).map(p->p.getTrackHead()).distinct().forEach(p->parentThMapParam.put(p, TrackConfigurable.getTrackConfigurer(structureIdx, db.getDao(positions[0]).getTrack(p), s)));
                parentThMapParam.entrySet().removeIf(e->e.getValue()==null);
            }
            
            logger.debug("manual segment: {} distinct parents. Segmentation structure: {}, parent structure: {}", points.size(), structureIdx, segmentationParentStructureIdx);
            List<StructureObject> segmentedObjects = new ArrayList<>();
            
            for (Map.Entry<StructureObject, List<int[]>> e : points.entrySet()) {
                ManualSegmenter segmenter = db.getExperiment().getStructure(structureIdx).getManualSegmenter();
                if (!parentThMapParam.isEmpty()) parentThMapParam.get(e.getKey().getParent(parentStructureIdx).getTrackHead()).apply(e.getKey(), segmenter);
                segmenter.setManualSegmentationVerboseMode(test);
                StructureObject globalParent = e.getKey().getParent(parentStructureIdx);
                StructureObject subParent = e.getKey();
                boolean subSegmentation = !subParent.equals(globalParent);
                boolean ref2D = subParent.is2D() && globalParent.getRawImage(structureIdx).sizeZ()>1;
                
                Image input = globalParent.getPreFilteredImage(structureIdx);
                if (subSegmentation) input = input.cropWithOffset(ref2D?new MutableBoundingBox(subParent.getBounds()).copyZ(input):subParent.getBounds());
                
                // generate image mask without old objects
                ImageByte mask = e.getKey().getMask() instanceof ImageInteger ? TypeConverter.cast((ImageInteger)e.getKey().getMask(), new ImageByte("Manual Segmentation Mask", 0, 0, 0)) : TypeConverter.toByteMask(e.getKey().getMask(), null, 1);
                
                List<StructureObject> oldChildren = e.getKey().getChildren(structureIdx);
                for (StructureObject c : oldChildren) c.getRegion().draw(mask, 0, new MutableBoundingBox(0, 0, 0));
                if (test) iwm.getDisplayer().showImage((ImageByte)mask, 0, 1);
                // remove seeds out of mask
                ImageMask refMask =  ref2D ? new ImageMask2D(mask) : mask;
                Iterator<int[]> it=e.getValue().iterator();
                while(it.hasNext()) {
                    int[] seed = it.next();
                    if (!refMask.insideMask(seed[0], seed[1], seed[2])) it.remove();
                }
                RegionPopulation seg = segmenter.manualSegment(input, e.getKey(), refMask, structureIdx, e.getValue());
                //seg.filter(new RegionPopulation.Size().setMin(2)); // remove seeds
                logger.debug("{} children segmented in parent: {}", seg.getRegions().size(), e.getKey());
                if (!test && !seg.getRegions().isEmpty()) {
                    StructureObject parent = e.getKey().getParent(parentStructureIdx);
                    if (!parent.equals(e.getKey())) seg.translate(e.getKey().getRelativeBoundingBox(parent), false);
                    oldChildren = parent.getChildren(structureIdx);
                    List<StructureObject> newChildren = parent.setChildrenObjects(seg, structureIdx);
                    segmentedObjects.addAll(newChildren);
                    newChildren.addAll(0, oldChildren);
                    ArrayList<StructureObject> modified = new ArrayList<>();
                    parent.relabelChildren(structureIdx, modified);
                    modified.addAll(newChildren);
                    Utils.removeDuplicates(modified, false);
                    db.getDao(parent.getPositionName()).store(modified);
                    
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
            InteractiveImage i = iwm.getImageObjectInterface(image);
            if (i!=null) {
                iwm.displayObjects(image, i.pairWithOffset(segmentedObjects), Color.ORANGE, true, false);
                GUI.updateRoiDisplayForSelections(image, i);
            }
        }
    }
    public static void splitObjects(MasterDAO db, Collection<StructureObject> objects, boolean updateDisplay, boolean test) {
        splitObjects(db, objects, updateDisplay, test, null);
    }
    public static void ensurePreFilteredImages(Stream<StructureObject> parents, int structureIdx, Experiment xp , ObjectDAO dao) {
        int parentStructureIdx = xp.getStructure(structureIdx).getParentStructure();
        TrackPreFilterSequence tpf = xp.getStructure(structureIdx).getProcessingScheme().getTrackPreFilters(false);
        boolean needToComputeAllPreFilteredImage = !tpf.get().isEmpty() || xp.getStructure(structureIdx).getProcessingScheme().getSegmenter() instanceof TrackConfigurable;
        TrackPreFilterSequence tpfWithPF = xp.getStructure(structureIdx).getProcessingScheme().getTrackPreFilters(true);
        if (!needToComputeAllPreFilteredImage) { // only preFilters on current objects
            PreFilterSequence pf = xp.getStructure(structureIdx).getProcessingScheme().getPreFilters();
            parents.map(p->p.getParent(parentStructureIdx)).forEach(parent ->{
                if (parent.getPreFilteredImage(structureIdx)==null) {
                    parent.setPreFilteredImage(pf.filter(parent.getRawImage(structureIdx), parent.getMask()), structureIdx);
                }
            });
        } else {
            if (dao==null) throw new RuntimeException("Cannot compute pre-filtered images because track preFilters are present and DAO not preset");
            GUI.log("Computing track pre-filters...");
            parents.map(p->p.getParent(parentStructureIdx)).map(o->o.getTrackHead()).distinct().forEach(p->{
                logger.debug("tpf for : {}", p);
                if (p.getPreFilteredImage(structureIdx)==null) {
                    tpfWithPF.filter(structureIdx, dao.getTrack(p));
                }
            });
            GUI.log("Track pre-filters computed!");
        }
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
        Map<String, List<StructureObject>> objectsByPosition = StructureObjectUtils.splitByPosition(objects);
        for (String f : objectsByPosition.keySet()) {
            ObjectDAO dao = db==null? null : db.getDao(f);
            List<StructureObject> objectsToStore = new ArrayList<>();
            List<StructureObject> newObjects = new ArrayList<>();
            ensurePreFilteredImages(objectsByPosition.get(f).stream().map(o->o.getParent()), structureIdx, xp, dao);
            for (StructureObject objectToSplit : objectsByPosition.get(f)) {
                if (defaultSplitter==null) splitter = xp.getStructure(structureIdx).getObjectSplitter();
                splitter.setSplitVerboseMode(test);
                if (test) splitter.splitObject(objectToSplit.getParent(), objectToSplit.getStructureIdx(), objectToSplit.getRegion());
                else {
                    StructureObject newObject = objectToSplit.split(splitter);
                    if (newObject==null) logger.warn("Object could not be splitted!");
                    else {
                        newObjects.add(newObject);
                        objectToSplit.setAttribute(StructureObject.EDITED_SEGMENTATION, true);
                        StructureObject prev = objectToSplit.getPrevious();
                        if (prev!=null) unlinkObjects(prev, objectToSplit, ALWAYS_MERGE, objectsToStore);
                        List<StructureObject> nexts = getNext(objectToSplit);
                        for (StructureObject n : nexts) unlinkObjects(objectToSplit, n, ALWAYS_MERGE, objectsToStore);
                        StructureObject next = nexts.size()==1 ? nexts.get(0) : null;
                        objectToSplit.getParent().relabelChildren(objectToSplit.getStructureIdx(), objectsToStore);
                        newObject.setAttribute(StructureObject.EDITED_SEGMENTATION, true);
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
            if (!test && dao!=null) dao.store(objectsToStore);
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
                InteractiveImage i = ImageWindowManagerFactory.getImageManager().getImageObjectInterface(null, structureIdx);
                if (i!=null) {
                    newObjects.addAll(objects);
                    Utils.removeDuplicates(newObjects, false);
                    ImageWindowManagerFactory.getImageManager().displayObjects(null, i.pairWithOffset(newObjects), Color.orange, true, false);
                    GUI.updateRoiDisplayForSelections(null, null);
                }
                // update trackTree
                if (GUI.getInstance().trackTreeController!=null) GUI.getInstance().trackTreeController.updateParentTracks();
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
        String fieldName = StructureObjectUtils.keepOnlyObjectsFromSameMicroscopyField(objects);
        if (objects.isEmpty()) return;
        ObjectDAO dao = db.getDao(fieldName);
        Map<StructureObject, List<StructureObject>> objectsByParent = StructureObjectUtils.splitByParent(objects);
        List<StructureObject> newObjects = new ArrayList<>();
        for (StructureObject parent : objectsByParent.keySet()) {
            List<StructureObject> objectsToMerge = objectsByParent.get(parent);
            if (objectsToMerge.size()<=1) logger.warn("Merge Objects: select several objects from same parent!");
            else {
                StructureObject prev = getPreviousObject(objectsToMerge); // previous object if all objects have same previous object
                StructureObject next = getNextObject(objectsToMerge); // next object if all objects have same next object
                Set<StructureObject> modifiedObjects = new HashSet<>();
                for (StructureObject o : objectsToMerge) unlinkObject(o, ALWAYS_MERGE, modifiedObjects);
                StructureObject res = objectsToMerge.remove(0);               
                for (StructureObject toMerge : objectsToMerge) res.merge(toMerge);
                
                if (prev!=null) linkObjects(prev, res, true, modifiedObjects);
                if (next!=null) linkObjects(res, next, true, modifiedObjects);
                newObjects.add(res);
                res.setAttribute(StructureObject.EDITED_SEGMENTATION, true);
                res.setAttribute(StructureObject.TRACK_ERROR_NEXT, null);
                res.setAttribute(StructureObject.TRACK_ERROR_PREV, null);
                if (res.getPrevious()!=null) res.getPrevious().setAttribute(TRACK_ERROR_NEXT, null);
                if (res.getNext()!=null) res.getNext().setAttribute(TRACK_ERROR_PREV, null);
                dao.delete(objectsToMerge, true, true, true);
                parent.getChildren(res.getStructureIdx()).removeAll(objectsToMerge);
                parent.relabelChildren(res.getStructureIdx(), modifiedObjects);
                modifiedObjects.removeAll(objectsToMerge);
                modifiedObjects.add(res);
                Utils.removeDuplicates(modifiedObjects, false);
                dao.store(modifiedObjects);
            }
        }
        if (updateDisplay) updateDisplayAndSelectObjects(newObjects);
    }
    public static void updateDisplayAndSelectObjects(List<StructureObject> objects) {
        ImageWindowManagerFactory.getImageManager().hideLabileObjects(null);
        ImageWindowManagerFactory.getImageManager().removeObjects(objects, true);
        Map<Integer, List<StructureObject>> oBySidx = StructureObjectUtils.splitByStructureIdx(objects);
        for (Entry<Integer, List<StructureObject>> e : oBySidx.entrySet()) {
            
            /*for (StructureObject newObject: newObjects) {
                //Update object tree
                ObjectNode node = GUI.getInstance().objectTreeGenerator.getObjectNode(newObject);
                node.getParent().createChildren();
                GUI.getInstance().objectTreeGenerator.reload(node.getParent());
            }*/
            Set<StructureObject> parents = StructureObjectUtils.getParentTrackHeads(e.getValue());
            //Update all opened images & objectImageInteraction
            for (StructureObject p : parents) ImageWindowManagerFactory.getImageManager().reloadObjects(p, e.getKey(), false);
            // update selection
            InteractiveImage i = ImageWindowManagerFactory.getImageManager().getImageObjectInterface(null, e.getKey());
            logger.debug("display : {} objects from structure: {}, IOI null ? {}", e.getValue().size(), e.getKey(), i==null);
            if (i!=null) {
                ImageWindowManagerFactory.getImageManager().displayObjects(null, i.pairWithOffset(e.getValue()), Color.orange, true, false);
                GUI.updateRoiDisplayForSelections(null, null);
            }
        }
        // update trackTree
        if (GUI.getInstance().trackTreeController!=null) GUI.getInstance().trackTreeController.updateParentTracks();
    }
    public static void deleteObjects(MasterDAO db, Collection<StructureObject> objects, BiPredicate<StructureObject, StructureObject> mergeTracks, boolean updateDisplay) {
        String fieldName = StructureObjectUtils.keepOnlyObjectsFromSameMicroscopyField(objects);
        ObjectDAO dao = db!=null ? db.getDao(fieldName) : null;
        Map<Integer, List<StructureObject>> objectsByStructureIdx = StructureObjectUtils.splitByStructureIdx(objects);
        for (int structureIdx : objectsByStructureIdx.keySet()) {
            List<StructureObject> toDelete = objectsByStructureIdx.get(structureIdx);
            Set<StructureObject> modifiedObjects = new HashSet<>();
            for (StructureObject o : toDelete) {
                unlinkObject(o, mergeTracks, modifiedObjects);
                o.getParent().getChildren(o.getStructureIdx()).remove(o);
            }
            Set<StructureObject> parents = StructureObjectUtils.getParents(toDelete);
            for (StructureObject p : parents) p.relabelChildren(structureIdx, modifiedObjects); // relabel
            
            if (dao!=null) {
                logger.info("Deleting {} objects, from {} parents", toDelete.size(), parents.size());
                dao.delete(toDelete, true, true, true);
                modifiedObjects.removeAll(toDelete); // avoid storing deleted objects!!!
                dao.store(modifiedObjects);
            } else {
                //Collections.sort(toDelete);
                //logger.debug("Deleting {} objects, from {} parents", toDelete.size(), parents.size());
            }
            if (updateDisplay) {
                //Update selection on opened image
                //ImageWindowManagerFactory.getImageManager().hideLabileObjects(null);
                ImageWindowManagerFactory.getImageManager().removeObjects(toDelete, true);
                List<StructureObject> selTh = ImageWindowManagerFactory.getImageManager().getSelectedLabileTrackHeads(null);
                
                //Update all opened images & objectImageInteraction
                for (StructureObject p : StructureObjectUtils.getParentTrackHeads(toDelete) ) ImageWindowManagerFactory.getImageManager().reloadObjects(p, structureIdx, false);
                ImageWindowManagerFactory.getImageManager().displayTracks(null, null, StructureObjectUtils.getTracks(selTh, true), true);
                GUI.updateRoiDisplayForSelections(null, null);
                
                // update trackTree
                if (GUI.getInstance().trackTreeController!=null) GUI.getInstance().trackTreeController.updateParentTracks();
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
                        if (o.getNext().getPrevious()==null) ManualEdition.linkObjects(o, o.getNext(), true, modifiedObjects);
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
                        if (o.getPrevious().getNext()==null) ManualEdition.linkObjects(o.getPrevious(), o, true, modifiedObjects);
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
        dao.store(modifiedObjects);
    }
    
    public static void deleteAllObjectsFromFrame(MasterDAO db, boolean after) {
        List<StructureObject> selList = ImageWindowManagerFactory.getImageManager().getSelectedLabileObjects(null);
        if (!selList.isEmpty()) {
            StructureObject first = Collections.min(selList, (o1, o2) -> Integer.compare(o1.getFrame(), o2.getFrame()));
            List<StructureObject> toDelete = Pair.unpairKeys(ImageWindowManagerFactory.getImageManager().getCurrentImageObjectInterface().getObjects());
            if (after) toDelete.removeIf(o -> o.getFrame()<first.getFrame());
            else toDelete.removeIf(o -> o.getFrame()>first.getFrame());
            deleteObjects(db, toDelete, ALWAYS_MERGE, true);
        }
    }
    
}
