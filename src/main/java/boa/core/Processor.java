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
import boa.gui.image_interaction.InteractiveImage;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.configuration.parameters.TransformationPluginParameter;
import boa.configuration.experiment.Experiment;
import boa.configuration.experiment.Position;
import boa.configuration.experiment.PreProcessingChain;
import boa.data_structure.dao.ImageDAO;
import boa.data_structure.input_image.InputImagesImpl;
import boa.data_structure.image_container.MultipleImageContainer;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.ObjectDAO;
import boa.data_structure.Selection;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.gui.image_interaction.Kymograph;
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
import boa.plugins.ConfigurableTransformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import boa.plugins.Measurement;
import boa.plugins.MultiThreaded;
import boa.plugins.ObjectSplitter;
import boa.plugins.ProcessingScheme;
import boa.plugins.Segmenter;
import boa.plugins.TrackCorrector;
import boa.plugins.Tracker;
import boa.plugins.Transformation;
import boa.plugins.plugins.processing_scheme.SegmentOnly;
import boa.utils.MultipleException;
import boa.utils.Pair;
import boa.utils.StreamConcatenation;
import boa.utils.ThreadRunner;
import boa.utils.ThreadRunner.ThreadAction;
import boa.utils.Utils;
import java.util.stream.Stream;

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
            Position position = xp.createPosition(c.getName());
            if (c.getScaleXY()==1 || c.getScaleXY()==0) {
                if (pcb!=null) pcb.log("Warning: no scale set for position: "+position.getName());
                logger.info("no scale set for positon: "+position.getName());
            }
            logger.debug("image: {} scale: {}, scaleZ: {} frame: {}", c.getName(), c.getScaleXY(), c.getScaleZ(), c.getCalibratedTimePoint(1, 0, 0));
            if (position!=null) {
                position.setImages(c);
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
    
    public static void preProcessImages(MasterDAO db)  throws Exception {
        Experiment xp = db.getExperiment();
        for (int i = 0; i<xp.getPositionCount(); ++i) {
            preProcessImages(xp.getPosition(i), db.getDao(xp.getPosition(i).getName()), false, null);
        }
    }
    
    public static void preProcessImages(Position field, ObjectDAO dao, boolean deleteObjects, ProgressCallback pcb)  {
        if (!dao.getPositionName().equals(field.getName())) throw new IllegalArgumentException("field name should be equal");
        InputImagesImpl images = field.getInputImages();
        if (images==null || images.getImage(0, images.getDefaultTimePoint())==null) {
            if (pcb!=null) pcb.log("Error: no input images found for position: "+field.getName());
            throw new RuntimeException("No images found for position");
        }
        images.deleteFromDAO(); // eraseAll images if existing in imageDAO
        for (int s =0; s<dao.getExperiment().getStructureCount(); ++s) dao.getExperiment().getImageDAO().deleteTrackImages(field.getName(), s);
        setTransformations(field);
        logger.debug("applying all transformation, save & close. {} ", Utils.getMemoryUsage());
        images.applyTranformationsAndSave(false);
        if (deleteObjects) dao.deleteAllObjects();
    }
    
    public static void setTransformations(Position field)  {
        InputImagesImpl images = field.getInputImages();
        PreProcessingChain ppc = field.getPreProcessingChain();
        for (TransformationPluginParameter<Transformation> tpp : ppc.getTransformations(true)) {
            Transformation transfo = tpp.instanciatePlugin();
            logger.debug("adding transformation: {} of class: {} to field: {}, input channel:{}, output channel: {}", transfo, transfo.getClass(), field.getName(), tpp.getInputChannel(), tpp.getOutputChannels());
            if (transfo instanceof ConfigurableTransformation) {
                ConfigurableTransformation ct = (ConfigurableTransformation)transfo;
                logger.debug("before configuring: {}", Utils.getMemoryUsage());
                ct.computeConfigurationData(tpp.getInputChannel(), images);
                logger.debug("after configuring: {}", Utils.getMemoryUsage());
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
        } else dao.getExperiment().getPosition(dao.getPositionName()).setOpenedImageToRootTrack(res);
        if (res==null || res.isEmpty()) throw new RuntimeException("no pre-processed image found");
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
                  for (Pair<String, Throwable> p : e.getExceptions()) logger.error(p.key, p.value);
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
        
        MultipleException me=null;
        try { // execute sequentially, store what has been processed, and throw exception in the end
            ThreadRunner.executeAndThrowErrors(allParentTracks.values().stream(), pt -> execute(xp.getStructure(structureIdx).getProcessingScheme(), structureIdx, pt, trackOnly, deleteChildren, dao));
        } catch (MultipleException e) {
            me=e;
        }
        
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
        if (me!=null) throw me;
    }
    
    private static void execute(ProcessingScheme ps, int structureIdx, List<StructureObject> parentTrack, boolean trackOnly, boolean deleteChildren, ObjectDAO dao) {
        if (!trackOnly && deleteChildren) dao.deleteChildren(parentTrack, structureIdx);
        if (trackOnly) ps.trackOnly(structureIdx, parentTrack);
        else {
            try {
                ps.segmentAndTrack(structureIdx, parentTrack);
                logger.debug("ps {} executed on track: {}, structure: {}", ps.getClass(), parentTrack.get(0), structureIdx);
            } catch(Exception e) {
                parentTrack.forEach(p -> p.setChildren(Collections.EMPTY_LIST, structureIdx)); // remove segmented objects if present to avoid saving them in DAO
                throw e;
            } finally {
                parentTrack.stream().map((p) -> {
                    p.setPreFilteredImage(null, structureIdx); // erase preFiltered images
                    return p;
                }).filter((p) -> (p.hasChildren(structureIdx))).forEachOrdered((p) -> {
                    p.getChildren(structureIdx).stream().filter((c) -> (c.hasRegion())).forEachOrdered((c) -> c.getRegion().clearVoxels());
                }); 
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
        if (roots.isEmpty()) throw new RuntimeException("no root");
        Map<StructureObject, List<StructureObject>> rootTrack = new HashMap<>(1); rootTrack.put(roots.get(0), roots);
        boolean containsObjects=false;
        for(Entry<Integer, List<Measurement>> e : measurements.entrySet()) {
            Map<StructureObject, List<StructureObject>> allParentTracks;
            if (e.getKey()==-1) {
                allParentTracks= rootTrack;
            } else {
                allParentTracks = StructureObjectUtils.getAllTracks(roots, e.getKey());
            }
            if (pcb!=null) pcb.log("Executing #"+e.getValue().size()+" measurement"+(e.getValue().size()>1?"s":"")+" on Structure: "+e.getKey()+" (#"+allParentTracks.size()+" tracks): "+Utils.toStringList(e.getValue(), m->m.getClass().getSimpleName()));
            logger.debug("Executing: #{} measurements from parent: {} (#{} parentTracks) : {}", e.getValue().size(), e.getKey(), allParentTracks.size(), Utils.toStringList(e.getValue(), m->m.getClass().getSimpleName()));
            
            // start with non parallele measurements on tracks
            List<Pair<Measurement, StructureObject>> actionPool = new ArrayList<>();
            allParentTracks.keySet().forEach(pt -> {
                dao.getExperiment().getMeasurementsByCallStructureIdx(e.getKey()).get(e.getKey()).stream().filter(m->m.callOnlyOnTrackHeads() && !(m instanceof MultiThreaded)).forEach(m-> actionPool.add(new Pair<>(m, pt)));
            });
            if (pcb!=null && actionPool.size()>0) pcb.log("Executing: #"+actionPool.size()+" track measurements");
            if (!actionPool.isEmpty()) containsObjects=true;
            ThreadRunner.executeAndThrowErrors(actionPool.parallelStream(), p->p.key.performMeasurement(p.value));
            
            // parallele measurement on tracks
            int paralleleMeasCount = (int)e.getValue().stream().filter(m->m.callOnlyOnTrackHeads() && (m instanceof MultiThreaded)).count();
            if (pcb!=null && paralleleMeasCount>0) pcb.log("Executing: #"+ paralleleMeasCount * allParentTracks.size()+" multithreaded track measurements");
            allParentTracks.keySet().forEach(pt -> {
                dao.getExperiment().getMeasurementsByCallStructureIdx(e.getKey()).get(e.getKey()).stream().filter(m->m.callOnlyOnTrackHeads() && (m instanceof MultiThreaded)).forEach(m-> {
                    ((MultiThreaded)m).setMultithread(true);
                    m.performMeasurement(pt);
                });
            });
            int allObCount = allParentTracks.values().stream().mapToInt(t->t.size()).sum();
            // measurements on objects
            dao.getExperiment().getMeasurementsByCallStructureIdx(e.getKey()).get(e.getKey()).stream().filter(m->!m.callOnlyOnTrackHeads()).forEach(m-> {
                if (pcb!=null) pcb.log("Executing Measurement: "+m.getClass().getSimpleName()+" on #"+allObCount+" objects");
                ThreadRunner.executeAndThrowErrors(StreamConcatenation.concat((Stream<StructureObject>[])allParentTracks.values().stream().map(l->l.parallelStream()).toArray(s->new Stream[s])), o->m.performMeasurement(o));
            });
            if (!containsObjects && allObCount>0) containsObjects = e.getValue().stream().filter(m->!m.callOnlyOnTrackHeads()).findAny().orElse(null)!=null;
            
            //ThreadRunner.execute(actionPool, false, (Pair<Measurement, StructureObject> p, int idx) -> p.key.performMeasurement(p.value));
            
            //Stream<Pair<Measurement, StructureObject>> actions = allParentTracks.values().stream().flatMap(t-> dao.getExperiment().getMeasurements(e.getKey()).flatMap( m->m.callOnlyOnTrackHeads() ? Stream.generate(()-> new Pair<>(m, t.get(0)) ) : t.stream().map(o -> new Pair<>(m, o))) );
            //actions.parallel().forEach(p->p.key.performMeasurement(p.value));
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
        if (containsObjects && allModifiedObjects.isEmpty()) throw new RuntimeException("No Measurement preformed");
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
            InteractiveImage i = Kymograph.generateTrackMask(track, parentStructureIdx);
            for (int childSIdx : cSI) {
                //GUI.log("Generating Image for track:"+track.get(0)+", structureIdx:"+childSIdx+" ...");
                Image im = i.generatemage(childSIdx, false);
                int channelIdx = dao.getExperiment().getChannelImageIdx(childSIdx);
                imageDAO.writeTrackImage(track.get(0), channelIdx, im);
            }
        });
    }
}
