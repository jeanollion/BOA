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

import dataStructure.configuration.Experiment;
import dataStructure.configuration.MicroscopyField;
import dataStructure.containers.MultipleImageContainer;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectAbstract;
import dataStructure.objects.StructureObjectPostProcessing;
import dataStructure.objects.StructureObjectPreProcessing;
import dataStructure.objects.StructureObjectRoot;
import dataStructure.objects.Track;
import image.ImageInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import plugins.Tracker;
import processing.PluginSequenceRunner;

/**
 *
 * @author jollion
 */
public class Processor {
    public static void importFiles(String[] selectedFiles, Experiment xp) {
        ArrayList<MultipleImageContainer> images = ImageFieldFactory.importImages(selectedFiles, xp);
        for (MultipleImageContainer c : images) {
            if (!xp.getMicroscopyFields().containsElement(c.getName())) {
                MicroscopyField f = (MicroscopyField)xp.getMicroscopyFields().createChildInstance(c.getName());
                xp.getMicroscopyFields().insert(f);
                f.setImages(c);
            } else {
                Logger.getLogger(Processor.class.getName()).log(Level.WARNING, "Image: {0} already present in fields was no added", c.getName());
            }
        }
    }
    
    /*public static StructureObjectRoot initRoot(Experiment xp) {
        
    }*/
    
    public static void preProcessImages(StructureObjectRoot root, Experiment xp) {
        
    }
    
    
    public static void processStructure(int structureIdx, StructureObjectRoot root, Experiment xp) {
        // get all parent objects of the structure
        ArrayList<StructureObject> allParents = root.getAllObjects(xp.getPathToRoot(xp.getStructure(structureIdx).getParentStructure()));
        for (StructureObject parent : allParents) {
            segmentObjects(structureIdx, parent, xp);
        }
        
    }
    
    public static void segmentObjects(int structureIdx, StructureObject parent, Experiment xp) {
        // segmentation
        parent.createPreFilterImage(structureIdx, xp); // the filtered image is set in the objects as it can be further used
        ImageInteger seg = PluginSequenceRunner.segmentImage(parent.getFilteredImage(structureIdx), parent, xp.getStructure(structureIdx).getProcessingChain().getSegmenter());
        seg = PluginSequenceRunner.postFilterImage(seg, parent, xp.getStructure(structureIdx).getProcessingChain().getPostfilters());
        
        // creation des structureObjects
        
        
    }
    
    public static void trackRoot(Experiment xp, StructureObjectRoot[] rootsT) {
        for (int i = 1; i<rootsT.length; ++i) {
            rootsT[i].setParentTrack(rootsT[i-1], true);
        }
    }
    
    public static void track(Experiment xp, Tracker tracker, StructureObjectPostProcessing parentTrack, int structureIdx) {
        // TODO gestion de la memoire vive -> si trop ouvert, fermer les images & masques des temps précédents.
        // Multithread -> attention à l'interaction avec la gestion de la memoire..
        while(parentTrack.getChildTrack()!=null) {
            tracker.assignParents(parentTrack.getChildObjects(structureIdx), parentTrack.getChildTrack().getChildObjects(structureIdx));
            parentTrack = parentTrack.getChildTrack();
        }
    }
}
