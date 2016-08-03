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
import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageDisplayer;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import dataStructure.configuration.ExperimentDAO;
import dataStructure.configuration.MicroscopyField;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.MorphiumMasterDAO;
import dataStructure.objects.MorphiumObjectDAO;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import de.caluga.morphium.Morphium;
import ij.process.AutoThresholder;
import image.Image;
import image.ImageMask;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import plugins.PluginFactory;
import plugins.plugins.segmenters.BacteriaTrans;
import plugins.plugins.segmenters.BacteriaFluo;
import plugins.plugins.thresholders.IJAutoThresholder;
import utils.MorphiumUtils;

/**
 *
 * @author jollion
 */
public class TestProcessBacteriaPhase {
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        //int time =31;
        int time =556;
        int microChannel =0;
        int field = 0;
        //String dbName = "boa_mutd5_141209";
        String dbName = "boa_mutH_140115";
        testSegBacteriesFromXP(dbName, field, time, microChannel);
        //testSegBacteriesFromXP(dbName, field, microChannel, 201, 400);
    }
    
    public static void testSegBacteriesFromXP(String dbName, int fieldNumber, int timePoint, int microChannel) {
        MasterDAO mDAO = new MorphiumMasterDAO(dbName);
        MicroscopyField f = mDAO.getExperiment().getMicroscopyField(fieldNumber);
        StructureObject root = mDAO.getDao(f.getName()).getRoots().get(timePoint);
        logger.debug("field name: {}, root==null? {}", f.getName(), root==null);
        StructureObject mc = root.getChildren(0).get(microChannel);
        Image input = mc.getRawImage(1);
        BacteriaTrans.debug=true;
        BacteriaTrans seg = new BacteriaTrans();
        ObjectPopulation pop = seg.runSegmenter(input, 1, mc);
        ImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(pop.getLabelMap());
        
    }
    public static void testSegBacteriesFromXP(String dbName, int fieldNumber, int microChannel, int timePointMin, int timePointMax) {
        MasterDAO mDAO = new MorphiumMasterDAO(dbName);
        MicroscopyField f = mDAO.getExperiment().getMicroscopyField(fieldNumber);
        List<StructureObject> rootTrack = mDAO.getDao(f.getName()).getRoots();
        Iterator<StructureObject> it = rootTrack.iterator();
        while(it.hasNext()) {
            StructureObject o = it.next();
            if (o.getTimePoint()<timePointMin) it.remove();
            if (o.getTimePoint()>timePointMax) it.remove();
        }
        List<StructureObject> parentTrack = new ArrayList<StructureObject>();
        for (StructureObject root : rootTrack) {
            StructureObject mc = root.getChildren(0).get(microChannel);
            parentTrack.add(mc);
            Image input = mc.getRawImage(1);
            BacteriaTrans.debug=false;
            BacteriaTrans seg = new BacteriaTrans();
            logger.debug("seg: tp {}", mc.getTimePoint());
            root.setChildrenObjects(seg.runSegmenter(input, 1, mc), 1);
        }
        ImageWindowManagerFactory.getImageManager().setInteractiveStructure(1);
        ImageObjectInterface i = ImageWindowManagerFactory.getImageManager().getImageTrackObjectInterface(parentTrack, 1);
        Image im = i.generateRawImage(1);
        ImageWindowManagerFactory.getImageManager().addImage(im, i, false, true);
        
    }
}
