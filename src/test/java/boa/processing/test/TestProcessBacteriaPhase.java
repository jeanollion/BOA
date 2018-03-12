/*
 * Copyright (C) 2016 jollion
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
package boa.processing.test;

import static boa.test_utils.TestUtils.logger;
import boa.gui.GUI;
import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageDisplayer;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageWindowManager;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.core.Task;
import boa.configuration.experiment.MicroscopyField;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import ij.ImageJ;
import ij.process.AutoThresholder;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.image.processing.ImageOperations;
import boa.image.io.ImageReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import boa.plugins.PluginFactory;
import boa.plugins.ProcessingScheme;
import boa.plugins.Segmenter;
import boa.plugins.TrackParametrizable;
import boa.plugins.plugins.segmenters.BacteriaIntensity;
import boa.plugins.legacy.BacteriaShape;
import boa.plugins.plugins.post_filters.FitMicrochannelHeadToEdges;
import boa.utils.Utils;
import java.util.Map;
import java.util.TreeMap;
import boa.plugins.TrackParametrizable.TrackParametrizer;

/**
 *
 * @author jollion
 */
public class TestProcessBacteriaPhase {
    static double thld = Double.NaN;
    static boolean setMask = false;
    static boolean normalize = false;
    public static void main(String[] args) {
        PluginFactory.findPlugins("boa.plugins.plugins");
        new ImageJ();
        
        //String dbName = "MF1_170523";
        //String dbName = "MutD5_141209";
        //String dbName = "MutH_150324";
        //String dbName = "Aya2";
        //String dbName = "AyaWT_mmglu";
        String dbName = "170919_glyc_lac";
        //String dbName = "WT_150616";
        //String dbName = "TestThomasRawStacks";
        int field = 0;
        int microChannel =23;
        int[] time =new int[]{145, 145}; //22
        //setMask=true;
        //thld = 776;
        
        //testSegBacteriesFromXP(dbName, field, time, microChannel);
        testSegBacteriesFromXP(dbName, field, microChannel, time[0], time[1]);
        //testSplit(dbName, field, time, microChannel, 1, true);
    }
    
    public static void testSegBacteriesFromXP(String dbName, int fieldNumber, int microChannel, int timePointMin, int timePointMax) {
        MasterDAO mDAO = new Task(dbName).getDB();
        mDAO.setReadOnly(true);
        MicroscopyField f = mDAO.getExperiment().getPosition(fieldNumber);
        List<StructureObject> rootTrack = mDAO.getDao(f.getName()).getRoots();
        List<StructureObject> parentTrack = Utils.getFirst(StructureObjectUtils.getAllTracks(rootTrack, 0), o->o.getIdx()==microChannel);
        
        ProcessingScheme psc = mDAO.getExperiment().getStructure(1).getProcessingScheme();
        psc.getTrackPreFilters(true).filter(1, parentTrack, null);
        TrackParametrizer apply = TrackParametrizable.getTrackParametrizer(1, parentTrack, psc.getSegmenter(), null);
        parentTrack.removeIf(o -> o.getFrame()<timePointMin || o.getFrame()>timePointMax);
        
        for (StructureObject mc : parentTrack) {
            Image input = mc.getPreFilteredImage(1);
            if (input==null) throw new RuntimeException("no preFIltered image!!");
            Segmenter seg = psc.getSegmenter();
            if (apply!=null) apply.apply(mc, seg);
            if (parentTrack.size()==1) {
                if (seg instanceof BacteriaIntensity) ((BacteriaIntensity)seg).testMode=true;
                if (seg instanceof BacteriaShape) ((BacteriaShape)seg).testMode=true;
            }
            mc.setChildrenObjects(seg.runSegmenter(input, 1, mc), 1);
           
            logger.debug("seg: tp {}, #objects: {}", mc.getFrame(), mc.getChildren(1).size());
        }
        //if (true) return;
        GUI.getInstance(); // for hotkeys...
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        ImageObjectInterface i = iwm.getImageTrackObjectInterface(parentTrack, 1);
        Image im = i.generatemage(1, true);
        iwm.addImage(im, i, 1, true);
        for (StructureObject mc : parentTrack)  mc.setRawImage(1, mc.getPreFilteredImage(1));
        im = i.generatemage(1, true);
        iwm.addImage(im, i, 1, true);
        iwm.setInteractiveStructure(1);
        iwm.displayAllObjects(im);
    }
}
