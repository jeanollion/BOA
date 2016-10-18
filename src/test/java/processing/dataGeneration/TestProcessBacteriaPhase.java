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
package processing.dataGeneration;

import static TestUtils.Utils.logger;
import boa.gui.GUI;
import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageDisplayer;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageWindowManager;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import dataStructure.configuration.ExperimentDAO;
import dataStructure.configuration.MicroscopyField;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.MorphiumMasterDAO;
import dataStructure.objects.MorphiumObjectDAO;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import de.caluga.morphium.Morphium;
import ij.ImageJ;
import ij.process.AutoThresholder;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageMask;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import plugins.PluginFactory;
import plugins.plugins.preFilter.IJSubtractBackground;
import plugins.plugins.segmenters.BacteriaTrans;
import plugins.plugins.segmenters.BacteriaFluo;
import plugins.plugins.thresholders.ConstantValue;
import plugins.plugins.thresholders.IJAutoThresholder;
import utils.MorphiumUtils;

/**
 *
 * @author jollion
 */
public class TestProcessBacteriaPhase {
    static double thld = Double.NaN;
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        new ImageJ();
        //int time =31;
        int time =959;
        int microChannel =9;
        int field = 0;
        thld = 252.9;
        //String dbName = "boa_mutH_140115";
        //String dbName = "boa_phase140115mutH";
        String dbName = "boa_phase150616wt";
        testSegBacteriesFromXP(dbName, field, time, microChannel);
        //testSegBacteriesFromXP(dbName, field, microChannel, 0, 400);
        //testSplit(dbName, field, time, microChannel, 4);
    }
    
    public static void testSplit(String dbName, int position, int timePoint, int microChannel, int oIdx) {
        MasterDAO mDAO = new MorphiumMasterDAO(dbName);
        MicroscopyField f = mDAO.getExperiment().getPosition(position);
        StructureObject root = mDAO.getDao(f.getName()).getRoots().get(timePoint);
        logger.debug("field name: {}, root==null? {}", f.getName(), root==null);
        StructureObject mc = root.getChildren(0).get(microChannel);
        Image input = mc.getRawImage(1);
        BacteriaTrans seg = new BacteriaTrans();
        if (!Double.isNaN(thld)) seg.setThresholdValue(thld);
        ObjectPopulation pop = seg.runSegmenter(input, 1, mc);
        seg.setSplitVerboseMode(true);
        List<Object3D> res = new ArrayList<>();
        //pop.translate(input.getBoundingBox(), true);
        new IJImageDisplayer().showImage(pop.getObjects().get(oIdx).getMask().crop(input.getBoundingBox().translateToOrigin()));
        //seg.split(input.resetOffset().crop(pop.getObjects().get(oIdx).getBounds()), pop.getObjects().get(oIdx), res);
        seg.split(input, pop.getObjects().get(oIdx), res);
        ImageDisplayer disp = new IJImageDisplayer();
        ImageByte splitMap = new ImageByte("splitted objects", pop.getLabelMap());
        int label=1;
        for (Object3D o : res) o.draw(splitMap, label++);
        disp.showImage(splitMap);
    }
    
    public static void testSegBacteriesFromXP(String dbName, int fieldNumber, int timePoint, int microChannel) {
        MasterDAO mDAO = new MorphiumMasterDAO(dbName);
        MicroscopyField f = mDAO.getExperiment().getPosition(fieldNumber);
        StructureObject root = mDAO.getDao(f.getName()).getRoots().get(timePoint);
        logger.debug("field name: {}, root==null? {}", f.getName(), root==null);
        StructureObject mc = root.getChildren(0).get(microChannel);
        Image input = mc.getRawImage(1);
        BacteriaTrans.debug=true;
        BacteriaTrans seg = new BacteriaTrans();
        if (!Double.isNaN(thld)) seg.setThresholdValue(thld);
        ObjectPopulation pop = seg.runSegmenter(input, 1, mc);
        ImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(pop.getLabelMap());
    }
    
    public static void testSegBacteriesFromXP(String dbName, int fieldNumber, int microChannel, int timePointMin, int timePointMax) {
        MasterDAO mDAO = new MorphiumMasterDAO(dbName);
        MicroscopyField f = mDAO.getExperiment().getPosition(fieldNumber);
        List<StructureObject> rootTrack = mDAO.getDao(f.getName()).getRoots();
        rootTrack.removeIf(o -> o.getTimePoint()<timePointMin || o.getTimePoint()>timePointMax);
        List<StructureObject> parentTrack = new ArrayList<StructureObject>();
        for (StructureObject root : rootTrack) {
            StructureObject mc = root.getChildren(0).get(microChannel);
            parentTrack.add(mc);
            Image input = mc.getRawImage(1);
            BacteriaTrans.debug=false;
            BacteriaTrans seg = new BacteriaTrans();
            if (!Double.isNaN(thld)) seg.setThresholdValue(thld);
            mc.setChildrenObjects(seg.runSegmenter(input, 1, mc), 1);
            logger.debug("seg: tp {}, #objects: {}", mc.getTimePoint(), mc.getChildren(1).size());
        }
        GUI.getInstance(); // for hotkeys...
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        ImageObjectInterface i = iwm.getImageTrackObjectInterface(parentTrack, 1);
        Image im = i.generateRawImage(1);
        iwm.addImage(im, i, false, true);
        iwm.setInteractiveStructure(1);
        iwm.displayAllObjects(im);
    }
}
