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

import boa.gui.GUI;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import configuration.parameters.TransformationPluginParameter;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.MicroscopyField;
import dataStructure.configuration.PreProcessingChain;
import dataStructure.containers.ImageDAO;
import dataStructure.containers.InputImagesImpl;
import dataStructure.containers.MultipleImageContainer;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.Selection;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import image.Image;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import measurement.MeasurementKey;
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
import utils.Pair;
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
    public static void importFiles(Experiment xp, boolean relink, String... selectedFiles) {
        List<MultipleImageContainer> images = ImageFieldFactory.importImages(selectedFiles, xp);
        int count=0, relinkCount=0;
        for (MultipleImageContainer c : images) {
            MicroscopyField f = xp.createPosition(c.getName());
            if (f!=null) {
                f.setImages(c); // TODO: bug when eraseAll from gui just after creation
                count++;
            } else if (relink) {
                xp.getPosition(c.getName()).setImages(c);
                ++relinkCount;
            } else {
                logger.warn("Image: {} already present in fields was no added", c.getName());
            }
        }
        logger.info("#{} fields found, #{} created, #{} relinked. From files: {}", images.size(), count, relinkCount, selectedFiles);
    }
    
    // preProcessing-related methods
    
    public static void preProcessImages(MasterDAO db, boolean computeConfigurationData)  throws Exception {
        Experiment xp = db.getExperiment();
        for (int i = 0; i<xp.getPositionCount(); ++i) {
            preProcessImages(xp.getPosition(i), db.getDao(xp.getPosition(i).getName()), false, computeConfigurationData);
        }
    }
    
    public static void preProcessImages(MicroscopyField field, ObjectDAO dao, boolean deleteObjects, boolean computeConfigurationData)  throws Exception {
        if (!dao.getPositionName().equals(field.getName())) throw new IllegalArgumentException("field name should be equal");
        InputImagesImpl images = field.getInputImages();
        images.deleteFromDAO(); // eraseAll images if existing in imageDAO
        for (int s =0; s<dao.getExperiment().getStructureCount(); ++s) dao.getExperiment().getImageDAO().deleteTrackImages(field.getName(), s);
        setTransformations(field, computeConfigurationData);
        images.applyTranformationsSaveAndClose();
        if (deleteObjects) dao.deleteAllObjects();
    }
    
    public static void setTransformations(MicroscopyField field, boolean computeConfigurationData)  throws Exception {
        InputImagesImpl images = field.getInputImages();
        PreProcessingChain ppc = field.getPreProcessingChain();
        for (TransformationPluginParameter<Transformation> tpp : ppc.getTransformations(true)) {
            Transformation transfo = tpp.instanciatePlugin();
            logger.debug("adding transformation: {} of class: {} to field: {}, input channel:{}, output channel: {}, isConfigured?: {}", transfo, transfo.getClass(), field.getName(), tpp.getInputChannel(), tpp.getOutputChannels(), transfo.isConfigured(images.getChannelNumber(), images.getFrameNumber()));
            if (computeConfigurationData || !transfo.isConfigured(images.getChannelNumber(), images.getFrameNumber())) {
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
            res = dao.getExperiment().getPosition(dao.getPositionName()).createRootObjects(dao);
            dao.store(res);
            dao.setRoots(res);
        }
        return res;
    }
    
    public static void processAndTrackStructures(MasterDAO db, boolean deleteObjects, int... structures) {
        Experiment xp = db.getExperiment();
        if (deleteObjects && structures.length==0) {
            db.deleteAllObjects();
            deleteObjects=false;
        }
        for (String fieldName : xp.getPositionsAsString()) {
            processAndTrackStructures(db.getDao(fieldName), deleteObjects, false, structures);
            db.getDao(fieldName).clearCache();
            db.getExperiment().getPosition(fieldName).flushImages(true, true);
        }
    }
    
    public static List<Pair<String, Exception>> processAndTrackStructures(ObjectDAO dao, boolean deleteObjects, boolean trackOnly, int... structures) {
        List<Pair<String, Exception>> errors = new ArrayList<>();
        Experiment xp = dao.getExperiment();
        if (deleteObjects) {
            if (structures.length==0 || structures.length==xp.getStructureCount()) dao.deleteAllObjects();
            else dao.deleteObjectsByStructureIdx(structures);
        } 
        List<StructureObject> root = getOrCreateRootTrack(dao);
        if (root==null || root.isEmpty()) {
            logger.error("Field: {} no pre-processed image found", dao.getPositionName());
            errors.add(new Pair("db: "+dao.getMasterDAO().getDBName()+" pos: "+dao.getPositionName(), new Exception("no pre-processed image found")));
            return errors;
        }
        if (structures.length==0) structures=xp.getStructuresInHierarchicalOrderAsArray();
        for (int s: structures) {
            if (!trackOnly) logger.info("Segmentation & Tracking: Field: {}, Structure: {}", dao.getPositionName(), s);
            else logger.info("Tracking: Field: {}, Structure: {}", dao.getPositionName(), s);
            List<Pair<String, Exception>> e = executeProcessingScheme(root, s, trackOnly, false);
            errors.addAll(e);
        }
        return errors;
    }
    
    public static List<Pair<String, Exception>> executeProcessingScheme(List<StructureObject> parentTrack, final int structureIdx, final boolean trackOnly, final boolean deleteChildren) {
        if (parentTrack.isEmpty()) return Collections.EMPTY_LIST;
        final ObjectDAO dao = parentTrack.get(0).getDAO();
        Experiment xp = parentTrack.get(0).getExperiment();
        final ProcessingScheme ps = xp.getStructure(structureIdx).getProcessingScheme();
        int directParentStructure = xp.getStructure(structureIdx).getParentStructure();
        if (trackOnly && ps instanceof SegmentOnly) return Collections.EMPTY_LIST;
        StructureObjectUtils.setAllChildren(parentTrack, structureIdx);
        Map<StructureObject, List<StructureObject>> allParentTracks;
        if (directParentStructure==-1 || parentTrack.get(0).getStructureIdx()==directParentStructure) { // parents = roots or parentTrack is parent structure
            allParentTracks = new HashMap<>(1);
            allParentTracks.put(parentTrack.get(0), parentTrack);
        } else {
            allParentTracks = StructureObjectUtils.getAllTracks(parentTrack, directParentStructure);
        }
        logger.debug("ex ps: structure: {}, allParentTracks: {}", structureIdx, allParentTracks.size());
        // one thread per track + common executor for processing scheme
        ExecutorService subExecutor = Executors.newFixedThreadPool(ThreadRunner.getMaxCPUs(), ThreadRunner.priorityThreadFactory(Thread.MAX_PRIORITY));
        //ExecutorService subExecutor = Executors.newFixedThreadPool(ThreadRunner.getMaxCPUs());
        //ExecutorService subExecutor = Executors.newSingleThreadExecutor(); // TODO: see what's more effective!
        final List[] ex = new List[allParentTracks.size()];
        ThreadAction<List<StructureObject>> ta = (List<StructureObject> pt, int idx) -> {
            ex[idx] = execute(xp.getStructure(structureIdx).getProcessingScheme(), structureIdx, pt, trackOnly, deleteChildren, dao, subExecutor);
        };
        List<Pair<String, Exception>> exceptions = ThreadRunner.execute(allParentTracks.values(), false, ta);
        for (List l : ex) if (l!=null) exceptions.addAll(l);
        
        // store in DAO
        List<StructureObject> children = new ArrayList<>();
        for (StructureObject p : parentTrack) children.addAll(p.getChildren(structureIdx));
        dao.store(children);
        logger.debug("total objects: {}, dao type: {}", children.size(), dao.getClass().getSimpleName());
        
        // create error selection
        if (dao.getMasterDAO().getSelectionDAO()!=null) {
            Selection errors = dao.getMasterDAO().getSelectionDAO().getOrCreate(dao.getExperiment().getStructure(structureIdx).getName()+"_TrackingErrors", false);
            boolean hadObjectsBefore=errors.count(dao.getPositionName())>0;
            if (hadObjectsBefore) {
                int nBefore = errors.count(dao.getPositionName());
                errors.removeChildrenOf(parentTrack);
                logger.debug("remove childre: count before: {} after: {}", nBefore, errors.count(dao.getPositionName()));
            } // if selection already exists: remove children of parentTrack
            children.removeIf(o -> !o.hasTrackLinkError(true, true));
            logger.debug("errors: {}", children.size());
            if (hadObjectsBefore || !children.isEmpty()) {
                errors.addElements(children);
                dao.getMasterDAO().getSelectionDAO().store(errors);
            }
        }
        return exceptions;
    }
    
    private static List<Pair<String, Exception>> execute(ProcessingScheme ps, int structureIdx, List<StructureObject> parentTrack, boolean trackOnly, boolean deleteChildren, ObjectDAO dao, ExecutorService executor) {
        if (!trackOnly && deleteChildren) dao.deleteChildren(parentTrack, structureIdx);
        if (trackOnly) return ps.trackOnly(structureIdx, parentTrack, executor);
        else return ps.segmentAndTrack(structureIdx, parentTrack, executor);
    }
    
    // measurement-related methods
    
    public static void performMeasurements(MasterDAO db, ProgressCallback pcb) {
        Experiment xp = db.getExperiment();
        for (int i = 0; i<xp.getPositionCount(); ++i) {
            String fieldName = xp.getPosition(i).getName();
            performMeasurements(db.getDao(fieldName), pcb);
            //if (dao!=null) dao.clearCacheLater(xp.getMicroscopyField(i).getName());
            db.getDao(fieldName).clearCache();
            db.getExperiment().getPosition(fieldName).flushImages(true, true);
        }
    }
    
    public static List<Pair<String, Exception>> performMeasurements(final ObjectDAO dao, ProgressCallback pcb) {
        long t0 = System.currentTimeMillis();
        List<StructureObject> roots = dao.getRoots();
        logger.debug("{} number of roots: {}", dao.getPositionName(), roots.size());
        final Map<Integer, List<Measurement>> measurements = dao.getExperiment().getMeasurementsByCallStructureIdx();
        List<Pair<String, Exception>> errors = new ArrayList<>();
        if (roots.isEmpty()) {
            errors.add(new Pair("db: "+dao.getMasterDAO().getDBName()+" position: "+dao.getPositionName(), new Exception("no root")));
            return errors;
        }
        Map<StructureObject, List<StructureObject>> rootTrack = new HashMap<>(1); rootTrack.put(roots.get(0), roots);
        
        for(Entry<Integer, List<Measurement>> e : measurements.entrySet()) {
            Map<StructureObject, List<StructureObject>> allParentTracks;
            if (e.getKey()==-1) {
                allParentTracks= rootTrack;
            } else {
                allParentTracks = StructureObjectUtils.getAllTracks(roots, e.getKey());
            }
            if (pcb!=null) pcb.log("Performing #"+e.getValue().size()+" measurement"+(e.getValue().size()>1?"s":"")+" on Structure: "+e.getKey()+" (#"+allParentTracks.size()+" tracks): "+Utils.toStringList(e.getValue(), m->m.getClass().getSimpleName()));
            logger.debug("performing: #{} measurements from parent: {} (#{} parentTracks) : {}", e.getValue().size(), e.getKey(), allParentTracks.size(), Utils.toStringList(e.getValue(), m->m.getClass().getSimpleName()));
            
            List<Pair<Measurement, StructureObject>> actionPool = new ArrayList<>();
            for (List<StructureObject> parentTrack : allParentTracks.values()) {
                for (Measurement m : dao.getExperiment().getMeasurementsByCallStructureIdx(e.getKey()).get(e.getKey())) {
                    if (m.callOnlyOnTrackHeads()) actionPool.add(new Pair<>(m, parentTrack.get(0)));
                    else for (StructureObject o : parentTrack) actionPool.add(new Pair<>(m, o));
                }
            }
            if (pcb!=null) pcb.log("Executing: #"+actionPool.size()+" measurements");
            List<Pair<String, Exception>> errorsLocal = ThreadRunner.execute(actionPool, false, (Pair<Measurement, StructureObject> p, int idx) -> p.key.performMeasurement(p.value));
            errors.addAll(errorsLocal);

        }
        long t1 = System.currentTimeMillis();
        final Set<StructureObject> allModifiedObjects = new HashSet<>();
        for (List<Measurement> lm : measurements.values()) {
            for (int sOut : getOutputStructures(lm)) {
                for (StructureObject root : roots) {
                    for (StructureObject o : root.getChildren(sOut)) if (o.getMeasurements().modified()) allModifiedObjects.add(o);
                }
            }
        }
        logger.debug("measurements on field: {}: computation time: {}, #modified objects: {}", dao.getPositionName(), t1-t0, allModifiedObjects.size());
        dao.upsertMeasurements(allModifiedObjects);
        if (allModifiedObjects.isEmpty()) errors.add(new Pair(dao.getPositionName(), new Error("No Measurement preformed")));
        return errors;
    }
    
    
    private static Set<Integer> getOutputStructures(List<Measurement> mList) {
        Set<Integer> l = new HashSet<Integer>(5);
        for (Measurement m : mList) for (MeasurementKey k : m.getMeasurementKeys()) l.add(k.getStoreStructureIdx());
        return l;
    }
    
    public static void generateTrackImages(ObjectDAO dao, int parentStructureIdx, ProgressCallback pcb, int... childStructureIdx) {
        if (dao==null || dao.getExperiment()==null) return;
        if (childStructureIdx==null || childStructureIdx.length==0) {
            List<Integer> childStructures =dao.getExperiment().getAllDirectChildStructures(parentStructureIdx);
            childStructures.add(parentStructureIdx);
            Utils.removeDuplicates(childStructures, sIdx -> dao.getExperiment().getStructure(sIdx).getChannelImage());
            childStructureIdx = Utils.toArray(childStructures, false);
        }
        final int[] cSI = childStructureIdx;
        ImageDAO imageDAO = dao.getExperiment().getImageDAO();
        imageDAO.deleteTrackImages(dao.getPositionName(), parentStructureIdx);
        Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(dao.getRoots(), parentStructureIdx);
        if (pcb!=null) pcb.log("Generating Image for structure: "+parentStructureIdx+". #tracks: "+allTracks.size()+", child structures: "+Utils.toStringArray(childStructureIdx));
        ThreadRunner.execute(allTracks.values(), false, (List<StructureObject> track, int idx) -> {
            ImageObjectInterface i = ImageWindowManagerFactory.getImageManager().generateTrackMask(track, parentStructureIdx);
            for (int childSIdx : cSI) {
                //GUI.log("Generating Image for track:"+track.get(0)+", structureIdx:"+childSIdx+" ...");
                Image im = i.generateRawImage(childSIdx, false);
                int channelIdx = dao.getExperiment().getChannelImageIdx(childSIdx);
                imageDAO.writeTrackImage(track.get(0), channelIdx, im);
            }
        });
    }
}
