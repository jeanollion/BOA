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
package boa.core;

import boa.gui.GUI;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.configuration.parameters.TransformationPluginParameter;
import boa.configuration.experiment.Experiment;
import boa.configuration.experiment.MicroscopyField;
import boa.configuration.experiment.PreProcessingChain;
import boa.data_structure.dao.ImageDAO;
import boa.data_structure.input_image.InputImagesImpl;
import boa.data_structure.image_container.MultipleImageContainer;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.ObjectDAO;
import boa.data_structure.Selection;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.image.Image;
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
import boa.measurement.MeasurementKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import boa.plugins.Measurement;
import boa.plugins.ObjectSplitter;
import boa.plugins.ProcessingScheme;
import boa.plugins.Segmenter;
import boa.plugins.TrackCorrector;
import boa.plugins.Tracker;
import boa.plugins.Transformation;
import boa.plugins.plugins.processing_scheme.SegmentOnly;
import boa.utils.MultipleException;
import boa.utils.Pair;
import boa.utils.ThreadRunner;
import boa.utils.ThreadRunner.ThreadAction;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class Processor {
    public static final Logger logger = LoggerFactory.getLogger(Processor.class);
    /*public static int getRemainingMemory() {
        
    }*/
    public static void importFiles(Experiment xp, boolean relink, ProgressCallback pcb, String... selectedFiles) {
        List<MultipleImageContainer> images = ImageFieldFactory.importImages(selectedFiles, xp, pcb);
        int count=0, relinkCount=0;
        for (MultipleImageContainer c : images) {
            MicroscopyField f = xp.createPosition(c.getName());
            if (c.getScaleXY()==1 || c.getScaleXY()==0) {
                if (pcb!=null) pcb.log("Warning: no scale set for position: "+f.getName());
                logger.info("no scale set for positon: "+f.getName());
            }
            logger.debug("image: {} scale: {}, scaleZ: {} frame: {}", c.getName(), c.getScaleXY(), c.getScaleZ(), c.getCalibratedTimePoint(1, 0, 0));
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
        if (pcb!=null) pcb.log("#"+images.size()+" fields found, #"+count+" created, #"+relinkCount+" relinked. From files: "+Utils.toStringArray(selectedFiles));
    }
    
    // preProcessing-related methods
    
    public static void preProcessImages(MasterDAO db, boolean computeConfigurationData)  throws Exception {
        Experiment xp = db.getExperiment();
        for (int i = 0; i<xp.getPositionCount(); ++i) {
            preProcessImages(xp.getPosition(i), db.getDao(xp.getPosition(i).getName()), false, computeConfigurationData, null);
        }
    }
    
    public static void preProcessImages(MicroscopyField field, ObjectDAO dao, boolean deleteObjects, boolean computeConfigurationData, ProgressCallback pcb)  throws Exception {
        if (!dao.getPositionName().equals(field.getName())) throw new IllegalArgumentException("field name should be equal");
        InputImagesImpl images = field.getInputImages();
        if (images==null || images.getImage(0, images.getDefaultTimePoint())==null) {
            if (pcb!=null) pcb.log("Error: no input images found for position: "+field.getName());
            throw new RuntimeException("No images found for position "+field.getName());
        }
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
                //tpp.setConfigurationData(transfo.getConfigurationData());
            }
            images.addTransformation(tpp.getInputChannel(), tpp.getOutputChannels(), transfo);
        }
    }
    // processing-related methods
    
    public static List<StructureObject> getOrCreateRootTrack(ObjectDAO dao) {
        List<StructureObject> res = dao.getRoots();
        if (res==null || res.isEmpty()) {
            res = dao.getExperiment().getPosition(dao.getPositionName()).createRootObjects(dao);
            if (res!=null && !res.isEmpty()) {
                dao.store(res);
                dao.setRoots(res);
            }
        }
        if (res==null || res.isEmpty()) throw new RuntimeException("ERROR db: "+dao.getMasterDAO().getDBName()+" pos: "+dao.getPositionName()+ " no pre-processed image found");
        return res;
    }
    
    public static void processAndTrackStructures(MasterDAO db, boolean deleteObjects, int... structures) {
        Experiment xp = db.getExperiment();
        if (deleteObjects && structures.length==0) {
            db.deleteAllObjects();
            deleteObjects=false;
        }
        for (String fieldName : xp.getPositionsAsString()) {
            try {
            processAndTrackStructures(db.getDao(fieldName), deleteObjects, false, structures);
            } catch (MultipleException e) {
                  for (Pair<String, Exception> p : e.getExceptions()) logger.error(p.key, p.value);
            } catch (Exception e) {
                logger.error("error while processing", e);
            }
            db.getDao(fieldName).clearCache();
        }
    }
    public static void deleteObjects(ObjectDAO dao, int...structures) {
        Experiment xp = dao.getExperiment();
        if (structures.length==0 || structures.length==xp.getStructureCount()) dao.deleteAllObjects();
        else dao.deleteObjectsByStructureIdx(structures);
        ImageDAO imageDAO = xp.getImageDAO();
        if (structures.length==0) for (int s : xp.getStructuresInHierarchicalOrderAsArray()) imageDAO.deleteTrackImages(dao.getPositionName(), s);
        else for (int s : structures) imageDAO.deleteTrackImages(dao.getPositionName(), s);
    }
    public static void processAndTrackStructures(ObjectDAO dao, boolean deleteObjects, boolean trackOnly, int... structures) {
        Experiment xp = dao.getExperiment();
        if (deleteObjects) deleteObjects(dao, structures);
        List<StructureObject> root = getOrCreateRootTrack(dao);

        if (structures.length==0) structures=xp.getStructuresInHierarchicalOrderAsArray();
        for (int s: structures) {
            if (!trackOnly) logger.info("Segmentation & Tracking: Field: {}, Structure: {} available mem: {}/{}GB", dao.getPositionName(), s, (Runtime.getRuntime().freeMemory()/1000000)/1000d, (Runtime.getRuntime().totalMemory()/1000000)/1000d);
            else logger.info("Tracking: Field: {}, Structure: {}", dao.getPositionName(), s);
            executeProcessingScheme(root, s, trackOnly, false);
            System.gc();
        }
    }
    
    public static void executeProcessingScheme(List<StructureObject> parentTrack, final int structureIdx, final boolean trackOnly, final boolean deleteChildren) {
        if (parentTrack.isEmpty()) return ;
        final ObjectDAO dao = parentTrack.get(0).getDAO();
        Experiment xp = parentTrack.get(0).getExperiment();
        final ProcessingScheme ps = xp.getStructure(structureIdx).getProcessingScheme();
        int directParentStructure = xp.getStructure(structureIdx).getParentStructure();
        if (trackOnly && ps instanceof SegmentOnly) return  ;
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
        //ExecutorService subExecutor = Executors.newSingleThreadExecutor(); // TODO: see what's more efficient!
        ThreadAction<List<StructureObject>> ta = (List<StructureObject> pt, int idx) -> {
            execute(xp.getStructure(structureIdx).getProcessingScheme(), structureIdx, pt, trackOnly, deleteChildren, dao, subExecutor);
        };
        logger.debug("thread number: {}", GUI.getThreadNumber() );
        ExecutorService mainExecutor = Executors.newFixedThreadPool(GUI.getThreadNumber(), ThreadRunner.priorityThreadFactory(Thread.NORM_PRIORITY));
        ThreadRunner.execute(allParentTracks.values(), false, ta, mainExecutor, null);
        
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
    }
    
    private static void execute(ProcessingScheme ps, int structureIdx, List<StructureObject> parentTrack, boolean trackOnly, boolean deleteChildren, ObjectDAO dao, ExecutorService executor) {
        if (!trackOnly && deleteChildren) dao.deleteChildren(parentTrack, structureIdx);
        if (trackOnly) ps.trackOnly(structureIdx, parentTrack, executor);
        else {
            try {
                ps.segmentAndTrack(structureIdx, parentTrack, executor);
                logger.debug("ps executed on track: {}, structure: {}", parentTrack.get(0), structureIdx);
            } catch(Exception e) {
                throw e;
            } finally {
                for (StructureObject o : parentTrack) o.setPreFilteredImage(null, structureIdx); // erase preFiltered images
                logger.debug("prefiltered images erased: {} for structure: {}", parentTrack.get(0), structureIdx);
            }
        }
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
    
    public static void performMeasurements(final ObjectDAO dao, ProgressCallback pcb) {
        long t0 = System.currentTimeMillis();
        List<StructureObject> roots = dao.getRoots();
        logger.debug("{} number of roots: {}", dao.getPositionName(), roots.size());
        final Map<Integer, List<Measurement>> measurements = dao.getExperiment().getMeasurementsByCallStructureIdx();
        if (roots.isEmpty()) throw new RuntimeException("ERROR db: "+dao.getMasterDAO().getDBName()+" position: "+dao.getPositionName()+ " no root");
        Map<StructureObject, List<StructureObject>> rootTrack = new HashMap<>(1); rootTrack.put(roots.get(0), roots);
        boolean containsObjects=false;
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
            if (!actionPool.isEmpty()) containsObjects=true;
            ThreadRunner.execute(actionPool, false, (Pair<Measurement, StructureObject> p, int idx) -> p.key.performMeasurement(p.value));
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
        if (containsObjects && allModifiedObjects.isEmpty()) throw new RuntimeException("ERROR db: "+dao.getMasterDAO().getDBName()+" position: "+dao.getPositionName()+"No Measurement preformed");
    }
    
    
    private static Set<Integer> getOutputStructures(List<Measurement> mList) {
        Set<Integer> l = new HashSet<>(5);
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
                Image im = i.generatemage(childSIdx, false);
                int channelIdx = dao.getExperiment().getChannelImageIdx(childSIdx);
                imageDAO.writeTrackImage(track.get(0), channelIdx, im);
            }
        });
    }
}
