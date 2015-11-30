/*
 * Copyright (C) 2015 jollion
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
package core;

import configuration.parameters.TransformationPluginParameter;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.MicroscopyField;
import dataStructure.configuration.PreProcessingChain;
import dataStructure.configuration.Structure;
import dataStructure.containers.InputImagesImpl;
import dataStructure.containers.MultipleImageContainer;
import dataStructure.containers.MultipleImageContainerSingleFile;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;
import static dataStructure.objects.StructureObject.TrackFlag.correctionMergeToErase;
import dataStructure.objects.StructureObjectTrackCorrection;
import dataStructure.objects.StructureObjectUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import measurement.MeasurementKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import plugins.Measurement;
import plugins.ObjectSplitter;
import plugins.Registration;
import plugins.TrackCorrector;
import plugins.Tracker;
import plugins.Transformation;
import utils.ThreadRunner;
import utils.ThreadRunner.ThreadAction;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class Processor {
    public static final Logger logger = LoggerFactory.getLogger(Processor.class);
    /*public static int getRemainingMemory() {
        
    }*/
    public static void importFiles(Experiment xp, String... selectedFiles) {
        ArrayList<MultipleImageContainer> images = ImageFieldFactory.importImages(selectedFiles, xp);
        int count=0;
        for (MultipleImageContainer c : images) {
            MicroscopyField f = xp.createMicroscopyField(c.getName());
            if (f!=null) {
                f.setImages(c);
                count++;
            } else {
                logger.warn("Image: {} already present in fields was no added", c.getName());
            }
        }
        logger.info("{} fields found int files: {}", count, selectedFiles);
    }
    
    
    /*public static StructureObjectRoot initRoot(Experiment xp) {
        
    }*/
    
    public static void preProcessImages(Experiment xp, ObjectDAO dao, boolean computeConfigurationData) {
        for (int i = 0; i<xp.getMicrocopyFieldCount(); ++i) {
            preProcessImages(xp.getMicroscopyField(i), dao, false, computeConfigurationData);
        }
        if (dao!=null) dao.deleteAllObjects();
    }
    
    public static void preProcessImages(MicroscopyField field, ObjectDAO dao, boolean deleteObjects, boolean computeConfigurationData) {
        setTransformations(field, computeConfigurationData);
        InputImagesImpl images = field.getInputImages();
        images.applyTranformationsSaveAndClose();
        if (deleteObjects) if (dao!=null && deleteObjects) dao.deleteObjectsFromField(field.getName());
    }
    
    public static void setTransformations(MicroscopyField field, boolean computeConfigurationData) {
        InputImagesImpl images = field.getInputImages();
        images.deleteFromDAO(); // delete images if existing in imageDAO
        PreProcessingChain ppc = field.getPreProcessingChain();
        for (TransformationPluginParameter<Transformation> tpp : ppc.getTransformations()) {
            if (tpp.isActivated()) {
                Transformation transfo = tpp.getPlugin();
                logger.debug("adding transformation: {} of class: {} to field: {}, input channel:{}, output channel: {}", transfo, transfo.getClass(), field.getName(), tpp.getInputChannel(), tpp.getOutputChannels());
                if (computeConfigurationData) {
                    transfo.computeConfigurationData(tpp.getInputChannel(), images);
                    tpp.setConfigurationData(transfo.getConfigurationData());
                }
                images.addTransformation(tpp.getInputChannel(), tpp.getOutputChannels(), transfo);
            }
        }
    }
    
    public static void processAndTrackStructures(Experiment xp, ObjectDAO dao) {
        if (dao!=null) dao.deleteAllObjects();
        for (int i = 0; i<xp.getMicrocopyFieldCount(); ++i) {
            logger.info("processing structures of Field: {}, total number of timePoint: {}...", xp.getMicroscopyField(i).getName(), xp.getMicroscopyField(i).getTimePointNumber());
            Processor.processAndTrackStructures(xp, xp.getMicroscopyField(i), dao, false, true);
            if (dao!=null) dao.clearCache();
        }
    }
    /**
     * 
     * @param xp
     * @param field
     * @param dao
     * @param deleteObjects
     * @param structures in hierarchical order
     */
    public static ArrayList<StructureObject> processAndTrackStructures(final Experiment xp, MicroscopyField field, final ObjectDAO dao, boolean deleteObjects, final boolean storeObjects, int... structures) {
        if (dao!=null && deleteObjects) dao.deleteObjectsFromField(field.getName());
        ArrayList<StructureObject> root = field.createRootObjects(dao);
        if (root==null) return null;
        Processor.trackRoot(root);
        if (dao!=null && storeObjects) dao.store(root, true, false);
        if (structures.length==0) structures=xp.getStructuresInHierarchicalOrderAsArray();
        for (final int s : structures) {
            final Structure structure = xp.getStructure(s);
            if (structure.hasSegmenter()) {
                logger.info("processing structure: {}...", s);
                ThreadRunner.execute(root, new ThreadAction<StructureObject>() {
                    @Override
                    public void run(StructureObject r) {
                        ArrayList<StructureObject> segmentedObjects=null;
                        if (!structure.hasTracker()) segmentedObjects = new ArrayList<StructureObject> ();
                        Processor.processChildren(s, r, dao, false, segmentedObjects);
                        if (!structure.hasTracker() && dao!=null && storeObjects) dao.store(segmentedObjects, false, false);
                    }
                });
                /*ArrayList<StructureObject> segmentedObjects=null;
                for (StructureObject r : root) { // segment
                    if (!structure.hasTracker()) segmentedObjects = new ArrayList<StructureObject> ();
                    Processor.processChildren(s, r, dao, false, segmentedObjects);
                    if (!structure.hasTracker() && dao!=null && storeObjects) dao.store(segmentedObjects, false, false);
                }*/
                
                if (structure.hasTracker()) { // track todo: multithread pour chaque parent
                    logger.info("tracking structure: {}...", s);
                    ArrayList<StructureObject> parents= StructureObjectUtils.getAllParentObjects(root.get(0), xp.getPathToRoot(s), dao);
                    /*for (StructureObject o : parents) {
                        ArrayList<StructureObject> trackedObjects = new ArrayList<StructureObject>();
                        Processor.trackChildren(structure.getTracker(), o, s, dao, structure.hasTrackCorrector()?null:trackedObjects);
                        if (structure.hasTrackCorrector()) {
                            Processor.correctTrackChildren(structure.getTrackCorrector(), structure.getObjectSplitter(), o, s, dao, false, null);
                            Processor.trackChildren(structure.getTracker(), o, s, dao, trackedObjects);
                        }
                        if (dao!=null && storeObjects) dao.store(trackedObjects, true, true);
                    }*/
                    ThreadRunner.execute(parents, new ThreadAction<StructureObject>() {
                        @Override
                        public void run(StructureObject o) {
                            ArrayList<StructureObject> trackedObjects = new ArrayList<StructureObject>();
                            Processor.trackChildren(structure.getTracker(), o, s, dao, structure.hasTrackCorrector()?null:trackedObjects);
                            if (structure.hasTrackCorrector()) {
                                Processor.correctTrackChildren(structure.getTrackCorrector(), structure.getObjectSplitter(), o, s, dao, false, null);
                                Processor.trackChildren(structure.getTracker(), o, s, dao, trackedObjects);
                            }
                            if (dao!=null && storeObjects) dao.store(trackedObjects, true, true);
                        }
                    });
                }
            }
        }
        
        return root;
    }
    
    public static void trackStructure(final int structureIdx, Experiment xp, MicroscopyField field, final ObjectDAO dao, final boolean updateTrackAttributes, List<StructureObject> parentObjects) { // objects are already stored -> have an ID
        if (xp.getStructure(structureIdx).hasTracker()) { // structure
            logger.info("tracking structure: {}...", structureIdx);
            final Structure structure = xp.getStructure(structureIdx);
            //ArrayList<StructureObject> modifiedObjectsCorrection = updateTrackAttributes&&structure.hasTrackCorrector()?new ArrayList<StructureObject>() : null;
            if (parentObjects==null) {
                StructureObject root0 = dao.getRoot(field.getName(), 0);
                parentObjects = StructureObjectUtils.getAllParentObjects(root0, xp.getPathToRoot(structureIdx), dao);
            }
            /*for (StructureObject o : parentObjects) {
                ArrayList<StructureObject> modifiedObjectsFromTracking = updateTrackAttributes?new ArrayList<StructureObject>() : null;
                Processor.trackChildren(structure.getTracker(), o, structureIdx, dao, structure.hasTracker()?null:modifiedObjectsFromTracking);
                if (structure.hasTrackCorrector()) {
                    Processor.correctTrackChildren(structure.getTrackCorrector(), structure.getObjectSplitter(), o, structureIdx, dao, dao!=null&&updateTrackAttributes, null);
                    Processor.trackChildren(structure.getTracker(), o, structureIdx, dao, modifiedObjectsFromTracking);                
                }
                if (updateTrackAttributes && dao!=null) {
                    dao.store(modifiedObjectsFromTracking, true, true);
                    //TODO update links, separate each case, when morphium bugs solved
                }
            }*/
            ThreadRunner.execute(parentObjects, new ThreadAction<StructureObject>() {
                @Override
                public void run(StructureObject o) {
                    ArrayList<StructureObject> modifiedObjectsFromTracking = updateTrackAttributes?new ArrayList<StructureObject>() : null;
                    Processor.trackChildren(structure.getTracker(), o, structureIdx, dao, structure.hasTracker()?null:modifiedObjectsFromTracking);
                    if (structure.hasTrackCorrector()) {
                        Processor.correctTrackChildren(structure.getTrackCorrector(), structure.getObjectSplitter(), o, structureIdx, dao, dao!=null&&updateTrackAttributes, null);
                        Processor.trackChildren(structure.getTracker(), o, structureIdx, dao, modifiedObjectsFromTracking);                
                    }
                    if (updateTrackAttributes && dao!=null) {
                        dao.store(modifiedObjectsFromTracking, true, true);
                        //TODO update links, separate each case, when morphium bugs solved
                    }
                }
            });
        } else logger.warn("no tracker for structure: {}", structureIdx);
    }
    
    public static void trackStructure(int structureIdx, Experiment xp, MicroscopyField field, ObjectDAO dao, boolean updateTrackAttributes, StructureObject... parentObjects) {
        trackStructure(structureIdx, xp, field, dao, updateTrackAttributes, parentObjects.length==0?null:Arrays.asList(parentObjects));
    }
    
    public static void correctTrackStructure(int structureIdx, Experiment xp, MicroscopyField field, ObjectDAO dao, boolean updateTrackAttributes, List<StructureObject> parentObjects) { // objects are already stored -> have an ID
        if (xp.getStructure(structureIdx).hasTrackCorrector()) { // structure
            logger.info("correcting track for structure: {}...", structureIdx);
            
            Structure structure = xp.getStructure(structureIdx);
            if (parentObjects==null) {
                StructureObject root0 = dao.getRoot(field.getName(), 0);
                parentObjects = StructureObjectUtils.getAllParentObjects(root0, xp.getPathToRoot(structureIdx), dao);
            }
            for (StructureObject o : parentObjects) {
                ArrayList<StructureObject> modifiedObjects = updateTrackAttributes?new ArrayList<StructureObject>() : null;
                Processor.correctTrackChildren(structure.getTrackCorrector(), structure.getObjectSplitter(), o, structureIdx, dao, dao!=null&&updateTrackAttributes, modifiedObjects);
                if (updateTrackAttributes && dao!=null) {
                    for (StructureObject oo : modifiedObjects) if (correctionMergeToErase.equals(oo.getTrackFlag())) logger.error("merged not erased! {}", oo);
                    dao.store(modifiedObjects, true, true); //TODO bug morphium update references... for "previous".
                }
            }            
        } else logger.warn("no trackCorrector for structure: {}", structureIdx);
    }
    public static void correctTrackStructure(int structureIdx, Experiment xp, MicroscopyField field, ObjectDAO dao, boolean updateTrackAttributes, StructureObject... parentObjects) {
        correctTrackStructure(structureIdx, xp, field, dao, updateTrackAttributes, parentObjects.length==0?null:Arrays.asList(parentObjects));
    }
    
    public static void processStructure(int structureIdx, Experiment xp, MicroscopyField field, ObjectDAO dao, List<StructureObject> parentObjects, ArrayList<StructureObject> segmentedObjects) {
        if (!xp.getStructure(structureIdx).hasSegmenter()) {
            logger.warn("no segmenter set for structure: {}", xp.getStructure(structureIdx).getName());
            return;
        }
        boolean automaticStore = false;
        if (segmentedObjects==null) {
            automaticStore= true;
            segmentedObjects = new ArrayList<StructureObject>();
        }
        if (parentObjects==null) {
            if (dao!=null) parentObjects=dao.getRoots(field.getName());
            if (parentObjects==null || parentObjects.isEmpty()) {
                if (xp.getStructure(structureIdx).getParentStructure()!=-1) throw new RuntimeException("No root objects detected, in order to segment structure: "+structureIdx+"one need to segment all its parent structures");
                else {
                    parentObjects = field.createRootObjects(dao);
                    Processor.trackRoot(parentObjects);
                    if (dao!=null) dao.store(parentObjects, true, false);
                }
            }
        }
        for (StructureObject parent : parentObjects) {
            ArrayList<StructureObject> allParents = StructureObjectUtils.getAllParentObjects(parent, parent.getExperiment().getPathToStructure(parent.getStructureIdx(), structureIdx), dao);
            logger.info("Segmenting structure: {} timePoint: {} number of parents: {}", structureIdx, parent.getTimePoint(), allParents.size());
            for (StructureObject localParent : allParents) {
                if (dao!=null) dao.deleteChildren(localParent, structureIdx);
                localParent.segmentChildren(structureIdx);
                segmentedObjects.addAll(localParent.getChildren(structureIdx));
                //if (dao!=null) dao.store(localParent.getChildren(structureIdx));
                if (logger.isDebugEnabled()) logger.debug("Segmenting structure: {} from parent: {} number of objects: {}", structureIdx, localParent, localParent.getChildObjects(structureIdx).size());
            }
        }
        if (automaticStore & dao !=null) dao.store(segmentedObjects, true, false);
    }
    
    public static void processChildren(int structureIdx, StructureObject parent, ObjectDAO dao, boolean deleteObjects, ArrayList<StructureObject> segmentedObjects) {
        //if (!parent.isRoot()) throw new IllegalArgumentException("this method only applies to root objects");
        // get all parent objects of the structure
        ArrayList<StructureObject> allParents = StructureObjectUtils.getAllParentObjects(parent, parent.getExperiment().getPathToStructure(parent.getStructureIdx(), structureIdx), dao);
        logger.info("Segmenting structure: {}, timePoint: {}, number of parents: {}...", structureIdx, parent.getTimePoint(), allParents.size());
        for (StructureObject localParent : allParents) {
            if (dao!=null && deleteObjects) dao.deleteChildren(localParent, structureIdx);
            localParent.segmentChildren(structureIdx);
            //if (dao!=null) dao.store(localParent.getChildren(structureIdx));
            if (segmentedObjects!=null) segmentedObjects.addAll(localParent.getChildren(structureIdx));
            if (logger.isDebugEnabled()) logger.debug("Segmented structure: {} from parent: {} number of objects: {}", structureIdx, localParent, localParent.getChildObjects(structureIdx).size());
        }
    }
    
    public static void trackRoot(List<StructureObject> rootsT) {
        //logger.debug("tracking root objects. dao==null? {}", dao==null);
        for (int i = 1; i<rootsT.size(); ++i) rootsT.get(i).setPreviousInTrack(rootsT.get(i-1), false);
        //if (dao!=null) dao.updateTrackAttributes(rootsT);
    }
    
    protected static void trackChildren(Tracker tracker, StructureObject parentTrack, int structureIdx, ObjectDAO dao, ArrayList<StructureObject> objects) {
        if (logger.isDebugEnabled()) logger.debug("tracking objects from structure: {} parentTrack: {} / Tracker: {} / dao==null? {}", structureIdx, parentTrack, tracker==null?"NULL":tracker.getClass(), dao==null);
        if (tracker==null) return;
        // TODO gestion de la memoire vive -> si trop ouvert, fermer les images & masques des temps précédents.
        for (StructureObject o : parentTrack.getChildObjects(structureIdx)) o.getParentTrackHeadId();
        while(parentTrack.getNext()!=null) {
            tracker.assignPrevious(parentTrack.getChildObjects(structureIdx), parentTrack.getNext().getChildObjects(structureIdx));
            //if (dao!=null) dao.updateTrackAttributes(parentTrack.getChildren(structureIdx));
            if (objects!=null) objects.addAll(parentTrack.getChildren(structureIdx));
            parentTrack = parentTrack.getNext();
        }
        if (objects!=null) objects.addAll(parentTrack.getChildren(structureIdx));
        //if (dao!=null) dao.updateTrackAttributes(parentTrack.getChildren(structureIdx)); // update the last one
    }
    
    protected static void correctTrackChildren(TrackCorrector trackCorrector, ObjectSplitter splitter, StructureObject parentTrack, int structureIdx, ObjectDAO dao, boolean removeMergedObjectFromDAO, ArrayList<StructureObject> modifiedObjects) {
        if (logger.isDebugEnabled()) logger.debug("correcting tracks from structure: {} parentTrack: {} / Tracker: {} / dao==null? {}", structureIdx, parentTrack, trackCorrector==null?"NULL":trackCorrector.getClass(), dao==null);
        if (trackCorrector==null) return;
        // TODO gestion de la memoire vive -> si trop ouvert, fermer les images & masques des temps précédents.
        
        ArrayList<StructureObjectTrackCorrection> localModifiedObjects = new ArrayList<StructureObjectTrackCorrection>();
        for (StructureObject o : parentTrack.getChildObjects(structureIdx)) o.getParentTrackHeadId(); //sets parentTrackHeadId
        while(parentTrack.getNext()!=null) {
            ArrayList<StructureObject> children = new ArrayList<StructureObject>(parentTrack.getChildObjects(structureIdx)); // to avoid concurrent modifications exception
            for (StructureObject child : children) if (child.getTrackFlag()!=correctionMergeToErase && child.isTrackHead()) trackCorrector.correctTrack(child, splitter, localModifiedObjects);
            parentTrack = parentTrack.getNext();
        }
        //int sizeBefore = modifiedObjects.size();
        Utils.removeDuplicates(localModifiedObjects, false);
        HashSet<StructureObject> parentsToRelabel = new HashSet<StructureObject>();
        Iterator<StructureObjectTrackCorrection> it = localModifiedObjects.iterator();
        while (it.hasNext()) {
            StructureObject o = (StructureObject)it.next();
            if (StructureObject.TrackFlag.correctionSplitNew.equals((o).getTrackFlag())) {
                if (o.getSiblings().indexOf(o)<o.getSiblings().size()-1) parentsToRelabel.add(o.getParent());
            }
            else if (StructureObject.TrackFlag.correctionMergeToErase.equals((o).getTrackFlag())) {
                if (o.getSiblings().indexOf(o)<o.getSiblings().size()-1) parentsToRelabel.add((StructureObject)o.getParent());
                if (dao!=null && removeMergedObjectFromDAO) { // delete merged objects before relabel to avoid collapse in case of objects stored in images...
                    if (o.getId()==null) dao.waiteForWrites();
                    logger.debug("removing object: {}, id: {}", o, o.getId());
                    dao.delete(o);
                    it.remove();
                }
            }
        }
        
        relabelParents(parentsToRelabel, structureIdx, modifiedObjects);
        if (modifiedObjects!=null) {
            modifiedObjects.ensureCapacity(modifiedObjects.size()+localModifiedObjects.size());
            for (StructureObjectTrackCorrection o : localModifiedObjects) modifiedObjects.add((StructureObject)o);
            Utils.removeDuplicates(modifiedObjects, false);
        }
    }
    protected static void relabelParents(HashSet<StructureObject> parentsToRelabel, int childStructureIdx, ArrayList<StructureObject> modifiedObjects) {
        for (StructureObject parent : parentsToRelabel) parent.relabelChildren(childStructureIdx, modifiedObjects);
    }
    
    // measurement-related methods
    
    
    
    public static void performMeasurements(StructureObject root, ObjectDAO dao) {
        Map<Integer, List<Measurement>> measurements = root.getExperiment().getMeasurementsByStructureIdx();
        Iterator<Entry<Integer, List<Measurement>>> it = measurements.entrySet().iterator();
        while(it.hasNext()) {
            Entry<Integer, List<Measurement>> e = it.next();
            int structureIdx = e.getKey();
            ArrayList<StructureObject> parents;
            if (e.getKey()==-1) {parents = new ArrayList<StructureObject>(1); parents.add(root);}
            else parents = root.getChildren(structureIdx);
            Set<StructureObject> modifiedObjects = new HashSet<StructureObject>();
            for (Measurement m : e.getValue()) {
                for (StructureObject o : parents) m.performMeasurement(o, modifiedObjects);
            }
            if (dao!=null && !modifiedObjects.isEmpty()) dao.updateMeasurements(new ArrayList<StructureObject>(modifiedObjects));
            it.remove(); // can save memory if the measurement instance stores data
        }
    }
    
    
}
