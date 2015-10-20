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
import dataStructure.objects.StructureObjectTrackCorrection;
import dataStructure.objects.StructureObjectUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import plugins.ObjectSplitter;
import plugins.Registration;
import plugins.TrackCorrector;
import plugins.Tracker;
import plugins.Transformation;

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
            Transformation transfo = tpp.getPlugin();
            logger.trace("adding transformation: {} of class: {} to field: {}", transfo, transfo.getClass(), field.getName());
            if (computeConfigurationData) {
                transfo.computeConfigurationData(tpp.getInputChannel(), images);
                tpp.setConfigurationData(transfo.getConfigurationData());
            }
            images.addTransformation(tpp.getInputChannel(), tpp.getOutputChannels(), transfo);
        }
    }
    
    public static void processStructures(Experiment xp, ObjectDAO dao) {
        if (dao!=null) dao.deleteAllObjects();
        for (int i = 0; i<xp.getMicrocopyFieldCount(); ++i) {
            logger.info("processing structures of Field: {}, total number of timePoint: {}...", xp.getMicroscopyField(i).getName(), xp.getMicroscopyField(i).getTimePointNumber());
            processAndTrackStructures(xp, xp.getMicroscopyField(i), dao, false);
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
    public static ArrayList<StructureObject> processAndTrackStructures(final Experiment xp, MicroscopyField field, final ObjectDAO dao, boolean deleteObjects, int... structures) {
        if (dao!=null && deleteObjects) dao.deleteObjectsFromField(field.getName());
        ArrayList<StructureObject> root = field.createRootObjects();
        Processor.trackRoot(root);
        if (dao!=null) dao.store(root, true);
        if (structures==null || structures.length==0) structures=xp.getStructuresInHierarchicalOrderAsArray();
        for (int s : structures) {
            Structure structure = xp.getStructure(s);
            if (structure.hasSegmenter()) {
                logger.info("processing structure: {}...", s);
                ArrayList<StructureObject> segmentedObjects = new ArrayList<StructureObject> ();
                for (int t = 0; t<root.size(); ++t) { // segment
                    Processor.process(s, root.get(t), dao, false, segmentedObjects);
                }
                ArrayList<StructureObjectTrackCorrection> correctedObjects=null;
                if (structure.hasTracker()) { // track
                    logger.info("tracking structure: {}...", s);
                    ArrayList<StructureObject> parents= StructureObjectUtils.getAllParentObjects(root.get(0), xp.getPathToRoot(s));
                    for (StructureObject o : parents) {
                        Processor.track(structure.getTracker(), o, s, dao, null);
                        if (structure.hasTrackCorrector()) {
                            correctedObjects = new ArrayList<StructureObjectTrackCorrection>();
                            Processor.correctTrack(structure.getTrackCorrector(), structure.getObjectSplitter(), o, s, dao, false, correctedObjects);
                        }
                    }
                    /*final int sIdx = s;
                    ThreadRunner.execute(StructureObjectUtils.getAllParentObjects(root[0], xp.getPathToRoot(s)), new ThreadAction<StructureObject>() {
                        @Override
                        public void run(StructureObject object) {
                            Processor.track(xp.getStructure(sIdx).getTracker(), object, sIdx, dao);
                        }
                    });*/
                }
                if (dao!=null) {
                    if (structure.hasTrackCorrector()) { // remove merged objects
                        Iterator<StructureObject> it = segmentedObjects.iterator();
                        while(it.hasNext()) if (StructureObject.TrackFlag.correctionMergeToErase.equals(it.next().getTrackFlag())) it.remove();
                    }
                    if (correctedObjects!=null) { // add split objects
                        for (StructureObjectTrackCorrection o : correctedObjects) if (StructureObject.TrackFlag.correctionSplitNew.equals(((StructureObject)o).getTrackFlag())) segmentedObjects.add((StructureObject)o);
                    }
                    dao.store(segmentedObjects, xp.getStructure(s).hasTracker());
                }
            }
        }
        return root;
    }
    
    public static void trackStructure(int structureIdx, Experiment xp, MicroscopyField field, ObjectDAO dao, boolean updateTrackAttributes) { // objects are already stored -> have an ID
        if (xp.getStructure(structureIdx).hasTracker()) { // structure
            logger.info("tracking structure: {}...", structureIdx);
            StructureObject root0 = dao.getRoot(field.getName(), 0);
            Structure structure = xp.getStructure(structureIdx);
            ArrayList<StructureObject> modifiedObjects = updateTrackAttributes?new ArrayList<StructureObject>() : null;
            ArrayList<StructureObjectTrackCorrection> modifiedObjectsCorrection = updateTrackAttributes&&structure.hasTrackCorrector()?new ArrayList<StructureObjectTrackCorrection>() : null;
            
            for (StructureObject o : StructureObjectUtils.getAllParentObjects(root0, xp.getPathToRoot(structureIdx), dao)) {
                Processor.track(structure.getTracker(), o, structureIdx, dao, modifiedObjects);
                if (structure.hasTrackCorrector()) Processor.correctTrack(structure.getTrackCorrector(), structure.getObjectSplitter(), o, structureIdx, dao, dao!=null&&updateTrackAttributes, modifiedObjectsCorrection);
            }
            if (updateTrackAttributes && dao!=null) {
                if (structure.hasTrackCorrector()) {
                    ArrayList<StructureObject> newObjects = new ArrayList<StructureObject>();
                    Iterator it = modifiedObjectsCorrection.iterator();
                    while(it.hasNext()) {
                        StructureObject o = (StructureObject)it.next();
                        if (StructureObject.TrackFlag.correctionSplitNew.equals(o.getTrackFlag())) newObjects.add(o);
                    }
                    dao.store(newObjects, false);
                }
                dao.setTrackAttributes(modifiedObjects);
                dao.store(modifiedObjects, false); //TODO bug morphium update references... for "previous"
            }
        } else logger.warn("no tracker for structure: {}", structureIdx);
    }
    
    public static void correctTrackStructure(int structureIdx, Experiment xp, MicroscopyField field, ObjectDAO dao, boolean updateTrackAttributes) { // objects are already stored -> have an ID
        if (xp.getStructure(structureIdx).hasTrackCorrector()) { // structure
            logger.info("correcting track for structure: {}...", structureIdx);
            StructureObject root0 = dao.getRoot(field.getName(), 0);
            ArrayList<StructureObjectTrackCorrection> modifiedObjects = updateTrackAttributes?new ArrayList<StructureObjectTrackCorrection>() : null;
            Structure structure = xp.getStructure(structureIdx);
            for (StructureObject o : StructureObjectUtils.getAllParentObjects(root0, xp.getPathToRoot(structureIdx), dao)) {
                Processor.correctTrack(structure.getTrackCorrector(), structure.getObjectSplitter(), o, structureIdx, dao, dao!=null&&updateTrackAttributes, modifiedObjects);
            }
            if (updateTrackAttributes && dao!=null) {
                ArrayList<StructureObject> modifiedObjectsS = new ArrayList<StructureObject>(modifiedObjects.size());
                ArrayList<StructureObject> newObjects = new ArrayList<StructureObject>();
                Iterator it = modifiedObjects.iterator();
                while(it.hasNext()) {
                    StructureObject o = (StructureObject)it.next();
                    if (StructureObject.TrackFlag.correctionMergeToErase.equals(o.getTrackFlag())) continue;
                    else {
                        modifiedObjectsS.add(o);
                        if (StructureObject.TrackFlag.correctionSplitNew.equals(o.getTrackFlag())) newObjects.add(o);
                    }
                }
                dao.store(newObjects, false);
                dao.setTrackAttributes(modifiedObjectsS);
                dao.store(modifiedObjectsS, false); //TODO bug morphium update references... for "previous".
            }
        } else logger.warn("no trackCorrector for structure: {}", structureIdx);
    }
    
    public static void processStructure(int structureIdx, Experiment xp, MicroscopyField field, int startTime, int stopTime, ObjectDAO dao, ArrayList<StructureObject> segmentedObjects) {
        boolean automaticStore = false;
        if (segmentedObjects==null) {
            automaticStore= true;
            segmentedObjects = new ArrayList<StructureObject>();
        }
        ArrayList<StructureObject> roots=null;
        if (dao!=null) roots=dao.getRoots(field.getName());
        if (roots==null || roots.isEmpty()) {
            if (xp.getStructure(structureIdx).getParentStructure()!=-1) throw new RuntimeException("No root objects detected, in order to segment structure: "+structureIdx+"one need to segment all its parent structures");
            else {
                roots = field.createRootObjects();
                Processor.trackRoot(roots);
                if (dao!=null) dao.store(roots, true);
            }
        }
        if (startTime<0) startTime=0;
        if (stopTime<0) stopTime = roots.size();
        if (startTime>stopTime || startTime>=field.getTimePointNumber() || stopTime>field.getTimePointNumber()) throw new IllegalArgumentException(" start time "+startTime+", and stop time: "+stopTime+" invalids (timepoint number: "+field.getTimePointNumber()+")");
        // get all parent objects of the structure
        //StructureObject parent = roots[0]; // for testing
        for (int t = startTime; t<stopTime; ++t) {
            StructureObject parent = roots.get(t);
            ArrayList<StructureObject> allParents = StructureObjectUtils.getAllParentObjects(parent, parent.getExperiment().getPathToStructure(parent.getStructureIdx(), structureIdx), dao);
            logger.info("Segmenting structure: {} timePoint: {} number of parents: {}", structureIdx, parent.getTimePoint(), allParents.size());
            for (StructureObject localParent : allParents) {
                if (dao!=null) dao.deleteChildren(localParent.getId(), structureIdx);
                localParent.segmentChildren(structureIdx);
                segmentedObjects.addAll(localParent.getChildren(structureIdx));
                //if (dao!=null) dao.store(localParent.getChildren(structureIdx));
                if (logger.isDebugEnabled()) logger.debug("Segmenting structure: {} from parent: {} number of objects: {}", structureIdx, localParent, localParent.getChildObjects(structureIdx).size());
                
            }
        }
        if (automaticStore & dao !=null) dao.store(segmentedObjects, true);
    }
    
    public static void process(int structureIdx, StructureObject parent, ObjectDAO dao, boolean deleteObjects, ArrayList<StructureObject> segmentedObjects) {
        //if (!parent.isRoot()) throw new IllegalArgumentException("this method only applies to root objects");
        // get all parent objects of the structure
        ArrayList<StructureObject> allParents = StructureObjectUtils.getAllParentObjects(parent, parent.getExperiment().getPathToStructure(parent.getStructureIdx(), structureIdx));
        logger.info("Segmenting structure: {}, timePoint: {}, number of parents: {}...", structureIdx, parent.getTimePoint(), allParents.size());
        for (StructureObject localParent : allParents) {
            if (dao!=null && deleteObjects) dao.deleteChildren(localParent.getId(), structureIdx);
            localParent.segmentChildren(structureIdx);
            //if (dao!=null) dao.store(localParent.getChildren(structureIdx));
            segmentedObjects.addAll(localParent.getChildren(structureIdx));
            if (logger.isDebugEnabled()) logger.debug("Segmented structure: {} from parent: {} number of objects: {}", structureIdx, localParent, localParent.getChildObjects(structureIdx).size());
        }
    }
    
    public static void trackRoot(List<StructureObject> rootsT) {
        //logger.debug("tracking root objects. dao==null? {}", dao==null);
        for (int i = 1; i<rootsT.size(); ++i) rootsT.get(i).setPreviousInTrack(rootsT.get(i-1), false, false);
        //if (dao!=null) dao.updateTrackAttributes(rootsT);
    }
    
    public static void track(Tracker tracker, StructureObject parentTrack, int structureIdx, ObjectDAO dao, ArrayList<StructureObject> objects) {
        if (logger.isDebugEnabled()) logger.debug("tracking objects from structure: {} parentTrack: {} / Tracker: {} / dao==null? {}", structureIdx, parentTrack, tracker==null?"NULL":tracker.getClass(), dao==null);
        if (tracker==null) return;
        // TODO gestion de la memoire vive -> si trop ouvert, fermer les images & masques des temps précédents.
        for (StructureObject o : parentTrack.getChildObjects(structureIdx, dao, false)) o.getParentTrackHeadId();
        while(parentTrack.getNext()!=null) {
            tracker.assignPrevious(parentTrack.getChildObjects(structureIdx, dao, false), parentTrack.getNext().getChildObjects(structureIdx, dao, false));
            //if (dao!=null) dao.updateTrackAttributes(parentTrack.getChildren(structureIdx));
            if (objects!=null) objects.addAll(parentTrack.getChildren(structureIdx));
            parentTrack = parentTrack.getNext();
        }
        if (objects!=null) objects.addAll(parentTrack.getChildren(structureIdx));
        //if (dao!=null) dao.updateTrackAttributes(parentTrack.getChildren(structureIdx)); // update the last one
    }
    
    public static void correctTrack(TrackCorrector trackCorrector, ObjectSplitter splitter, StructureObject parentTrack, int structureIdx, ObjectDAO dao, boolean removeMergedObjectFromDAO, ArrayList<StructureObjectTrackCorrection> modifiedObjects) {
        if (logger.isDebugEnabled()) logger.debug("tracking objects from structure: {} parentTrack: {} / Tracker: {} / dao==null? {}", structureIdx, parentTrack, trackCorrector==null?"NULL":trackCorrector.getClass(), dao==null);
        if (trackCorrector==null) return;
        // TODO gestion de la memoire vive -> si trop ouvert, fermer les images & masques des temps précédents.
        if (modifiedObjects==null) modifiedObjects = new ArrayList<StructureObjectTrackCorrection>();
        for (StructureObject o : parentTrack.getChildObjects(structureIdx, dao, false)) o.getParentTrackHeadId(); //sets parentTrackHeadId
        while(parentTrack.getNext()!=null) {
            ArrayList<StructureObject> children = parentTrack.getChildObjects(structureIdx, dao, false);
            for (StructureObject child : children) if (child.isTrackHead()) trackCorrector.correctTrack(child, splitter, modifiedObjects);
            // remove merged objects
            Iterator<StructureObject> it = children.iterator();
            while(it.hasNext()) {
                StructureObject child = it.next();
                if (StructureObject.TrackFlag.correctionMergeToErase.equals(child.getTrackFlag())) {
                    it.remove();
                    if (dao!=null && removeMergedObjectFromDAO) dao.delete(child);
                }
            }
            parentTrack = parentTrack.getNext();
        }
        // add split objects to parents
        for (StructureObjectTrackCorrection o : modifiedObjects) if (StructureObject.TrackFlag.correctionSplitNew.equals(((StructureObject)o).getTrackFlag())) ((StructureObject)o).getParent().getChildren(structureIdx).add((StructureObject)o);
    }
}
