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
import dataStructure.containers.InputImagesImpl;
import dataStructure.containers.MultipleImageContainer;
import dataStructure.containers.MultipleImageContainerSingleFile;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import plugins.Registration;
import plugins.Tracker;
import plugins.Transformation;
import plugins.TransformationTimeIndependent;
import utils.MorphiumUtils;

/**
 *
 * @author jollion
 */
public class Processor {
    public static final Logger logger = LoggerFactory.getLogger(Processor.class);
    
    public static void importFiles(String[] selectedFiles, Experiment xp) {
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
    
    public static void preProcessImages(Experiment xp) {
        for (int i = 0; i<xp.getMicrocopyFieldNB(); ++i) {
            preProcessImages(xp.getMicroscopyField(i));
        }
    }
    
    public static void preProcessImages(MicroscopyField field) {
        setTransformationsAndComputeConfigurationData(field);
        InputImagesImpl images = field.getInputImages();
        images.applyTranformationsSaveAndClose();
    }
    
    public static void setTransformationsAndComputeConfigurationData(MicroscopyField field) {
        InputImagesImpl images = field.getInputImages();
        PreProcessingChain ppc = field.getPreProcessingChain();
        for (TransformationPluginParameter<Transformation> tpp : ppc.getTransformations()) {
            Transformation transfo = tpp.getPlugin();
            transfo.computeConfigurationData(tpp.getInputChannel(), images);
            tpp.setConfigurationData(transfo.getConfigurationData());
            images.addTransformation(tpp.getInputChannel(), tpp.getOutputChannels(), transfo);
        }
    }
    
    public static void processStructures(Experiment xp, ObjectDAO dao) {
        for (int i = 0; i<xp.getMicrocopyFieldNB(); ++i) {
            processStructures(xp, xp.getMicroscopyField(i), dao);
        }
    }
    
    public static void processStructures(Experiment xp, MicroscopyField field, ObjectDAO dao) {
        StructureObject[] root = field.createRootObjects();
        if (dao!=null) dao.store(root);
        Processor.trackRoot(root, dao);
        for (int s : xp.getStructuresInHierarchicalOrderAsArray()) {
            for (int t = 0; t<root.length; ++t) Processor.processStructure(s, root[t], dao); // process
            for (StructureObject o : StructureObjectUtils.getAllParentObjects(root[0], xp.getPathToRoot(s))) Processor.track(xp.getStructure(s).getTracker(), o, s, dao); // structure
        }
    }
    
    public static void processStructure(int structureIdx, StructureObject root, ObjectDAO dao) {
        if (!root.isRoot()) throw new IllegalArgumentException("this method only applies to root objects");
        
        //Logger.getLogger(Processor.class.getName()).log(Level.INFO, "Segmenting structure: "+structureIdx+ " timePoint: "+root.getTimePoint());
        // get all parent objects of the structure
        ArrayList<StructureObject> allParents = StructureObjectUtils.getAllParentObjects(root, root.getExperiment().getPathToRoot(structureIdx));
        if (logger.isDebugEnabled()) logger.debug("Segmenting structure: {} timePoint: {} number of parents: {}", structureIdx, root.getTimePoint(), allParents.size());
        for (StructureObject parent : allParents) {
            parent.segmentChildren(structureIdx);
            if (dao!=null) dao.store(parent.getChildObjects(structureIdx));
            if (logger.isDebugEnabled()) logger.debug("Segmenting structure: {} from parent: {} number of objects: {}", structureIdx, parent, parent.getChildObjects(structureIdx).length);
        }
    }
    
    public static void trackRoot(StructureObject[] rootsT, ObjectDAO dao) {
        logger.debug("tracking root objects. dao==null? {}", dao==null);
        for (int i = 1; i<rootsT.length; ++i) rootsT[i].setPreviousInTrack(rootsT[i-1], false);
        if (dao!=null) dao.updateTrackAttributes(rootsT);
    }
    
    public static void track(Tracker tracker, StructureObject parentTrack, int structureIdx, ObjectDAO dao) {
        if (logger.isDebugEnabled()) logger.debug("tracking objects from structure: {} parentTrack: {} / Tracker: {} / dao==null? {}", structureIdx, parentTrack, tracker==null?"NULL":tracker.getClass(), dao==null);
        if (tracker==null) return;
        // TODO gestion de la memoire vive -> si trop ouvert, fermer les images & masques des temps précédents.
        for (StructureObject o : parentTrack.getChildObjects(structureIdx)) o.setParentTrackHeadId(parentTrack.getTrackHeadId());
        while(parentTrack.getNext()!=null) {
            tracker.assignPrevious(parentTrack.getChildObjects(structureIdx), parentTrack.getNext().getChildObjects(structureIdx));
            if (dao!=null) dao.updateTrackAttributes(parentTrack.getChildObjects(structureIdx));
            parentTrack = parentTrack.getNext();
        }
        if (dao!=null) dao.updateTrackAttributes(parentTrack.getChildObjects(structureIdx)); // update the last one
    }
}
