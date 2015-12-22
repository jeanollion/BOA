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
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import static dataStructure.objects.StructureObject.TrackFlag.correctionMergeToErase;
import dataStructure.objects.StructureObjectTrackCorrection;
import dataStructure.objects.StructureObjectUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import plugins.Measurement;
import plugins.ObjectSplitter;
import plugins.ProcessingScheme;
import plugins.Segmenter;
import plugins.TrackCorrector;
import plugins.Tracker;
import plugins.Transformation;
import plugins.plugins.processingScheme.SegmentOnly;
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
            Transformation transfo = tpp.instanciatePlugin();
            logger.debug("adding transformation: {} of class: {} to field: {}, input channel:{}, output channel: {}", transfo, transfo.getClass(), field.getName(), tpp.getInputChannel(), tpp.getOutputChannels());
            if (computeConfigurationData) {
                transfo.computeConfigurationData(tpp.getInputChannel(), images);
                tpp.setConfigurationData(transfo.getConfigurationData());
            }
            images.addTransformation(tpp.getInputChannel(), tpp.getOutputChannels(), transfo);
        }
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
                Segmenter s = localParent.getExperiment().getStructure(structureIdx).getProcessingChain().getSegmenter();
                ObjectPopulation o = s.runSegmenter(localParent.getRawImage(structureIdx), structureIdx, localParent);
                localParent.setChildren(o, structureIdx);
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
        //logger.info("Segmenting structure: {}, timePoint: {}, number of parents: {}...", structureIdx, parent.getTimePoint(), allParents.size());
        
        // multithread a ce niveau? 
        for (StructureObject localParent : allParents) {
            if (dao!=null && deleteObjects) dao.deleteChildren(localParent, structureIdx);
            Segmenter s = localParent.getExperiment().getStructure(structureIdx).getProcessingChain().getSegmenter();
            ObjectPopulation o = s.runSegmenter(localParent.getRawImage(structureIdx), structureIdx, localParent);
            localParent.setChildren(o, structureIdx);
            //if (dao!=null) dao.store(localParent.getChildren(structureIdx));
            if (segmentedObjects!=null) segmentedObjects.addAll(localParent.getChildren(structureIdx));
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
        for (StructureObject o : parentTrack.getChildren(structureIdx)) {
            o.getParentTrackHeadId();
            o.resetTrackLinks();
        }
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
                    dao.delete(o, false);
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
    
    // processing-related methods
    
    public static List<StructureObject> getOrCreateRootTrack(ObjectDAO dao, String fieldName) {
        List<StructureObject> res = dao.getRoots(fieldName);
        if (res==null || res.isEmpty()) {
            res = dao.getExperiment().getMicroscopyField(fieldName).createRootObjects(dao);
            dao.store(res, true, false);
        }
        return res;
    }
    
    public static void processAndTrackStructures(ObjectDAO dao, boolean deleteObjects, int... structures) {
        Experiment xp = dao.getExperiment();
        if (deleteObjects && structures.length==0) {
            dao.deleteAllObjects();
            deleteObjects=false;
        }
        for (String fieldName : xp.getFieldsAsString()) {
            processAndTrackStructures(fieldName, dao, deleteObjects, structures);
            dao.clearCache(); // todo : clear cache later..
        }
    }
    
    public static void processAndTrackStructures(String fieldName, ObjectDAO dao, boolean deleteObjects, int... structures) {
        Experiment xp = dao.getExperiment();
        if (deleteObjects) {
            if (dao!=null) {
                if (structures.length==0) dao.deleteObjectsFromField(fieldName);
                else dao.deleteObjectsFromFieldByStructure(fieldName, structures);
            }
        } 
        List<StructureObject> root = getOrCreateRootTrack(dao, fieldName);
        if (structures.length==0) structures=xp.getStructuresInHierarchicalOrderAsArray();
        for (int s: structures) executeProcessingScheme(root, s, false, false);
    }
    
    public static void executeProcessingScheme(List<StructureObject> parentTrack, final int structureIdx, final boolean trackOnly, final boolean deleteChildren) {
        if (parentTrack.isEmpty()) return;
        final ObjectDAO dao = parentTrack.get(0).getDAO();
        Experiment xp = parentTrack.get(0).getExperiment();
        final ProcessingScheme ps = xp.getStructure(structureIdx).getProcessingScheme();
        int parentStructure = xp.getStructure(structureIdx).getParentStructure();
        if (parentStructure==-1 || parentTrack.get(0).getStructureIdx()==parentStructure) { // parents = roots
            execute(ps, structureIdx, parentTrack, trackOnly, deleteChildren, dao);
        } else {
            HashMap<StructureObject, ArrayList<StructureObject>> allParentTracks = StructureObjectUtils.getAllTracks(parentTrack, parentStructure);
            logger.debug("ex ps: structure: {}, allParentTracks: {}", structureIdx, allParentTracks.size());
            // one thread per track
            ThreadAction<ArrayList<StructureObject>> ta = new ThreadAction<ArrayList<StructureObject>>() {
                public void run(ArrayList<StructureObject> pt, int idx) {execute(ps, structureIdx, pt, trackOnly, deleteChildren, dao);}
            };
            ThreadRunner.execute(new ArrayList<ArrayList<StructureObject>> (allParentTracks.values()), ta);
        }
    }
    
    private static void execute(ProcessingScheme ps, int structureIdx, List<StructureObject> parentTrack, boolean trackOnly, boolean deleteChildren, ObjectDAO dao) {
        if (!trackOnly && deleteChildren) for (StructureObject p : parentTrack) dao.deleteChildren(p, structureIdx);
        if (trackOnly) ps.trackOnly(structureIdx, parentTrack);
        else ps.segmentAndTrack(structureIdx, parentTrack);
        for (StructureObject p : parentTrack) dao.store(p.getChildren(structureIdx), !(ps instanceof SegmentOnly), false);
    }
    
    // measurement-related methods
    
    public static void performMeasurements(Experiment xp, ObjectDAO dao) {
        for (int i = 0; i<xp.getMicrocopyFieldCount(); ++i) {
            performMeasurements(xp.getMicroscopyField(i).getName(), dao);
            //if (dao!=null) dao.clearCacheLater(xp.getMicroscopyField(i).getName());
            dao.clearCache();
        }
    }
    
    public static void performMeasurements(String fieldName, final ObjectDAO dao) {
        long t0 = System.currentTimeMillis();
        ArrayList<StructureObject> roots = dao.getRoots(fieldName);
        final StructureObject[] rootArray = roots.toArray(new StructureObject[roots.size()]);
        roots=null; // saves memory
        logger.debug("{} number of roots: {}", fieldName, rootArray.length);
        final Map<Integer, List<Measurement>> measurements = dao.getExperiment().getMeasurementsByCallStructureIdx();

        ThreadRunner.execute(rootArray, true, new ThreadAction<StructureObject>() {
            @Override
            public void run(StructureObject root, int idx) {
                long t0 = System.currentTimeMillis();
                logger.debug("running measurements on: {}", root);
                List<StructureObject> modifiedObjects = new ArrayList<StructureObject>();
                for(Entry<Integer, List<Measurement>> e : measurements.entrySet()) {
                    int structureIdx = e.getKey();
                    ArrayList<StructureObject> parents;
                    if (structureIdx==-1) {parents = new ArrayList<StructureObject>(1); parents.add(root);}
                    else parents = root.getChildren(structureIdx);
                    for (Measurement m : e.getValue()) {
                        for (StructureObject o : parents) {
                            if (!m.callOnlyOnTrackHeads() || o.isTrackHead()) m.performMeasurement(o, modifiedObjects);
                        }
                    }
                }
                long t1 = System.currentTimeMillis();
                logger.debug("running measurements on: {}, time elapsed: {}ms", root, t1-t0);
                if (dao!=null && !modifiedObjects.isEmpty()) dao.upsertMeasurements(modifiedObjects);
            }
        });
        long t1 = System.currentTimeMillis();
        logger.debug("measurements on field: {}: time: {}", fieldName, t1-t0);
        dao.waiteForWrites();
        dao.clearCache();
    }
}
