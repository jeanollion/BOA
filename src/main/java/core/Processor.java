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

import boa.gui.objects.DBConfiguration;
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
import java.util.Collections;
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
    
    // preProcessing-related methods
    
    public static void preProcessImages(Experiment xp, DBConfiguration db, boolean computeConfigurationData) {
        for (int i = 0; i<xp.getMicrocopyFieldCount(); ++i) {
            preProcessImages(xp.getMicroscopyField(i), db.getDao(xp.getMicroscopyField(i).getName()), false, computeConfigurationData);
        }
    }
    
    public static void preProcessImages(MicroscopyField field, ObjectDAO dao, boolean deleteObjects, boolean computeConfigurationData) {
        if (dao.fieldName!=field.getName()) throw new IllegalArgumentException("field name should be equal");
        setTransformations(field, computeConfigurationData);
        InputImagesImpl images = field.getInputImages();
        images.applyTranformationsSaveAndClose();
        if (dao!=null && deleteObjects) dao.deleteAllObjects();
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
    // processing-related methods
    
    public static List<StructureObject> getOrCreateRootTrack(ObjectDAO dao) {
        List<StructureObject> res = dao.getRoots();
        if (res==null || res.isEmpty()) {
            res = dao.getExperiment().getMicroscopyField(dao.getFieldName()).createRootObjects(dao);
            for (StructureObject o : res) dao.store(o, true);
        }
        return res;
    }
    
    public static void processAndTrackStructures(DBConfiguration db, boolean deleteObjects, int... structures) {
        Experiment xp = db.getExperiment();
        if (deleteObjects && structures.length==0) {
            db.deleteAllObjects();
            deleteObjects=false;
        }
        for (String fieldName : xp.getFieldsAsString()) {
            processAndTrackStructures(db.getDao(fieldName), deleteObjects, structures);
            db.getDao(fieldName);
        }
    }
    
    public static void processAndTrackStructures(ObjectDAO dao, boolean deleteObjects, int... structures) {
        Experiment xp = dao.getExperiment();
        if (deleteObjects) {
            if (structures.length==0) dao.deleteAllObjects();
            else dao.deleteObjectsByStructure(structures);
        } 
        List<StructureObject> root = getOrCreateRootTrack(dao);
        if (structures.length==0) structures=xp.getStructuresInHierarchicalOrderAsArray();
        for (int s: structures) executeProcessingScheme(root, s, false, false);
    }
    
    public static void executeProcessingScheme(List<StructureObject> parentTrack, final int structureIdx, final boolean trackOnly, final boolean deleteChildren) {
        if (parentTrack.isEmpty()) return;
        final ObjectDAO dao = parentTrack.get(0).getDAO();
        Experiment xp = parentTrack.get(0).getExperiment();
        final ProcessingScheme ps = xp.getStructure(structureIdx).getProcessingScheme();
        int parentStructure = xp.getStructure(structureIdx).getParentStructure();
        if (trackOnly && ps instanceof SegmentOnly) return;
        //ArrayList<ArrayList<StructureObject>> objectsToStore = new ArrayList<ArrayList<StructureObject>>();
        //List<ArrayList<StructureObject>> objectsToStoreSync = Collections.synchronizedList(objectsToStore);
        
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
        if (ps instanceof SegmentOnly) { // gather all objects and store
            ArrayList<StructureObject> children = new ArrayList<StructureObject>();
            for (StructureObject p : parentTrack) children.addAll(p.getChildren(structureIdx));
            dao.store(parentTrack, false);
        } else { // store by time point
            for (StructureObject p : parentTrack) {
                dao.store(p.getChildren(structureIdx), true);
            }
        }
    }
    
    private static void execute(ProcessingScheme ps, int structureIdx, List<StructureObject> parentTrack, boolean trackOnly, boolean deleteChildren, ObjectDAO dao) {
        if (!trackOnly && deleteChildren) for (StructureObject p : parentTrack) dao.deleteChildren(p, structureIdx);
        if (trackOnly) ps.trackOnly(structureIdx, parentTrack);
        else ps.segmentAndTrack(structureIdx, parentTrack);
    }
    
    // measurement-related methods
    
    public static void performMeasurements(Experiment xp, DBConfiguration db) {
        for (int i = 0; i<xp.getMicrocopyFieldCount(); ++i) {
            String fieldName = xp.getMicroscopyField(i).getName();
            performMeasurements(db.getDao(fieldName));
            //if (dao!=null) dao.clearCacheLater(xp.getMicroscopyField(i).getName());
            db.getDao(fieldName).clearCache();
        }
    }
    
    public static void performMeasurements(final ObjectDAO dao) {
        long t0 = System.currentTimeMillis();
        ArrayList<StructureObject> roots = dao.getRoots();
        final StructureObject[] rootArray = roots.toArray(new StructureObject[roots.size()]);
        roots=null; // saves memory
        logger.debug("{} number of roots: {}", dao.fieldName, rootArray.length);
        final Map<Integer, List<Measurement>> measurements = dao.getExperiment().getMeasurementsByCallStructureIdx();
        final List<StructureObject> allModifiedObjects = new ArrayList<StructureObject>();
        final List<StructureObject> allModifiedObjectsSync = Collections.synchronizedList(allModifiedObjects);
        
        ThreadRunner.execute(rootArray, true, new ThreadAction<StructureObject>() {
            @Override
            public void run(StructureObject root, int idx) {
                long t0 = System.currentTimeMillis();
                //logger.debug("running measurements on: {}", root);
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
                allModifiedObjectsSync.addAll(modifiedObjects);
                long t1 = System.currentTimeMillis();
                logger.debug("measurements on: {}, time elapsed: {}ms", root, t1-t0);
                //if (dao!=null && !modifiedObjects.isEmpty()) dao.upsertMeasurements(modifiedObjects);
            }
        });
        long t1 = System.currentTimeMillis();
        
        if (dao!=null && !allModifiedObjects.isEmpty()) dao.upsertMeasurements(allModifiedObjects);
        long t2 = System.currentTimeMillis();
        logger.debug("measurements on field: {}: time: {}, upsert time: {} ({} objects)", dao.fieldName, t1-t0, t2-t1, allModifiedObjects.size());
        
    }
}
