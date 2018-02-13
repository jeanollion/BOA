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
import boa.image.BoundingBox;
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
import boa.plugins.TrackParametrizable.ApplyToSegmenter;
import boa.plugins.plugins.segmenters.BacteriaIntensity;
import boa.plugins.legacy.BacteriaShape;
import boa.plugins.legacy.BacteriaTrans;
import java.util.Map;
import java.util.TreeMap;

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
        String dbName = "MutH_150324";
        //String dbName = "WT_150616";
        //String dbName = "TestThomasRawStacks";
        int field = 0;
        int microChannel =5;
        int[] time =new int[]{459, 459}; //22
        //setMask=true;
        //thld = 776;
        
        //testSegBacteriesFromXP(dbName, field, time, microChannel);
        testSegBacteriesFromXP(dbName, field, microChannel, time[0], time[1]);
        //testSplit(dbName, field, time, microChannel, 1, true);
    }
    
    public static void testSplit(String dbName, int position, int timePoint, int microChannel, int oIdx, boolean useSegmentedObjectsFromDB) {
        MasterDAO mDAO = new Task(dbName).getDB();
        MicroscopyField f = mDAO.getExperiment().getPosition(position);
        StructureObject root = mDAO.getDao(f.getName()).getRoots().get(timePoint);
        logger.debug("field name: {}, root==null? {}", f.getName(), root==null);
        StructureObject mc = root.getChildren(0).get(microChannel);
        RegionPopulation pop;
        Image input = mc.getRawImage(1);
        if (useSegmentedObjectsFromDB) {
            pop=mc.getObjectPopulation(1);
            pop.translate(pop.getObjectOffset().reverseOffset(), false); // translate object to relative landmark
        } else {
            BacteriaTrans seg = new BacteriaTrans();
            if (!Double.isNaN(thld)) seg.setThresholdValue(thld);
            pop = seg.runSegmenter(input, 1, mc);
            seg.setSplitVerboseMode(true);
        }
        
        List<Region> res = new ArrayList<>();
        //pop.translate(input.getBoundingBox(), true);
        ImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(pop.getRegions().get(oIdx).getMask().crop(input.getBoundingBox().translateToOrigin()));
        //seg.split(input.resetOffset().crop(pop.getObjects().get(oIdx).getBounds()), pop.getObjects().get(oIdx), res);
        BacteriaTrans seg = new BacteriaTrans();
        seg.setSplitVerboseMode(true);
        seg.split(input, pop.getRegions().get(oIdx), res);
        
        ImageByte splitMap = new ImageByte("splitted objects", pop.getLabelMap());
        int label=1;
        for (Region o : res) o.draw(splitMap, label++);
        disp.showImage(splitMap);
    }
    
    public static void testSegBacteriesFromXP(String dbName, int fieldNumber, int timePoint, int microChannel) {
        MasterDAO mDAO = new Task(dbName).getDB();
        MicroscopyField f = mDAO.getExperiment().getPosition(fieldNumber);
        StructureObject root = mDAO.getDao(f.getName()).getRoots().get(timePoint);
        logger.debug("field name: {}, root==null? {}", f.getName(), root==null);
        StructureObject mc = root.getChildren(0).get(microChannel);
        Image input = mc.getRawImage(1);
        BacteriaTrans.debug=true;
        
        BacteriaTrans seg = new BacteriaTrans();
        if (mDAO.getExperiment().getStructure(1).getProcessingScheme().getSegmenter() instanceof BacteriaTrans) {
            seg = (BacteriaTrans) mDAO.getExperiment().getStructure(1).getProcessingScheme().getSegmenter();
        }
        
        if (!Double.isNaN(thld)) seg.setThresholdValue(thld);
        RegionPopulation pop = seg.runSegmenter(input, 1, mc);
        ImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(pop.getLabelMap());
    }
    
    public static void testSegBacteriesFromXP(String dbName, int fieldNumber, int microChannel, int timePointMin, int timePointMax) {
        MasterDAO mDAO = new Task(dbName).getDB();
        mDAO.setReadOnly(true);
        MicroscopyField f = mDAO.getExperiment().getPosition(fieldNumber);
        List<StructureObject> rootTrack = mDAO.getDao(f.getName()).getRoots();
        Map<StructureObject, List<StructureObject>> allMCTracks = StructureObjectUtils.getAllTracks(rootTrack, 0);
        allMCTracks.entrySet().removeIf(o->o.getKey().getIdx()!=microChannel);
        List<StructureObject> parentTrack = allMCTracks.entrySet().iterator().next().getValue();
        //parentTrack.removeIf(o -> o.getFrame()<1 || o.getFrame()>200); // GRANDE DIFFERENCE POUR SUBBACK -> vient de saturate?
        ProcessingScheme psc = mDAO.getExperiment().getStructure(1).getProcessingScheme();
        psc.getTrackPreFilters(true).filter(0, parentTrack, null);
        ApplyToSegmenter apply = TrackParametrizable.getApplyToSegmenter(timePointMax, parentTrack, psc.getSegmenter(), null);
        parentTrack.removeIf(o -> o.getFrame()<timePointMin || o.getFrame()>timePointMax);
        
        for (StructureObject mc : parentTrack) {
            Image input = mc.getPreFilteredImage(timePointMax);
            Segmenter seg = psc.getSegmenter();
            if (apply!=null) apply.apply(mc, seg);
            if (parentTrack.size()==1) {
                if (seg instanceof BacteriaIntensity) ((BacteriaIntensity)seg).testMode=true;
                if (seg instanceof BacteriaShape) ((BacteriaShape)seg).testMode=true;
            }
            mc.setChildrenObjects(seg.runSegmenter(input, 1, mc), 1);
            //mc.setRawImage(0, input);
            logger.debug("seg: tp {}, #objects: {}", mc.getFrame(), mc.getChildren(1).size());
        }
        //if (true) return;
        GUI.getInstance(); // for hotkeys...
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        ImageObjectInterface i = iwm.getImageTrackObjectInterface(parentTrack, 1);
        Image im = i.generateRawImage(1, true);
        iwm.addImage(im, i, 1, true);
        iwm.setInteractiveStructure(1);
        iwm.displayAllObjects(im);
    }
}
