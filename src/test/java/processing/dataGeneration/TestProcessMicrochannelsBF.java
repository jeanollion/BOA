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
import boa.gui.imageInteraction.IJImageWindowManager;
import boa.gui.imageInteraction.ImageDisplayer;
import boa.gui.imageInteraction.ImageObjectInterface;
import configuration.parameters.PostFilterSequence;
import configuration.parameters.PreFilterSequence;
import dataStructure.configuration.ExperimentDAO;
import dataStructure.configuration.MicroscopyField;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.MorphiumMasterDAO;
import dataStructure.objects.MorphiumObjectDAO;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import de.caluga.morphium.Morphium;
import image.Image;
import image.ImageMask;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import plugins.PluginFactory;
import plugins.plugins.segmenters.BacteriaFluo;
import plugins.plugins.segmenters.MicroChannelBF2D;
import plugins.plugins.segmenters.MicroChannelFluo2D;
import plugins.plugins.trackers.MicrochannelProcessor;
import static processing.dataGeneration.TestLAPTrackerMutations.mutationIdx;
import utils.MorphiumUtils;

/**
 *
 * @author jollion
 */
public class TestProcessMicrochannelsBF {
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        int time =0;
        int field = 0;
        String dbName = "testBF";
        //testSegMicrochannelsFromXP(dbName, field, time);
        testSegAndTrackMicrochannelsFromXP(dbName, field, 0, 54);
    }
    
    public static void testSegMicrochannelsFromXP(String dbName, int fieldNumber, int timePoint) {
        MasterDAO mDAO = new MorphiumMasterDAO(dbName);
        MicroscopyField f = mDAO.getExperiment().getMicroscopyField(fieldNumber);
        StructureObject root = mDAO.getDao(f.getName()).getRoot(timePoint);
        if (root==null) root = f.createRootObjects(mDAO.getDao(f.getName())).get(timePoint);
        logger.debug("field name: {}, root==null? {}", f.getName(), root==null);
        Image input = root.getRawImage(0);
        MicroChannelBF2D.debug=true;
        ObjectPopulation pop = MicroChannelBF2D.run(input, 26, 0.15, 2, 6, 6);
        //ObjectPopulation pop = MicroChannelFluo2D.run2(input, 355, 40, 20);
        ImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(input);
        disp.showImage(pop.getLabelImage());
        
        // test split
        //ObjectPopulation popSplit = testObjectSplitter(intensityMap, pop.getChildren().get(0));
        //disp.showImage(popSplit.getLabelImage());
    }
    
    public static void testSegAndTrackMicrochannelsFromXP(String dbName, int fieldNumber, int timePointMin, int timePointMax) {
        MasterDAO mDAO = new MorphiumMasterDAO(dbName);
        MicroscopyField f = mDAO.getExperiment().getMicroscopyField(fieldNumber);
        ArrayList<StructureObject> rootTrack = mDAO.getDao(f.getName()).getRoots();
        Iterator<StructureObject> it = rootTrack.iterator();
        while(it.hasNext()) {
            StructureObject o = it.next();
            if (o.getTimePoint()<timePointMin) it.remove();
            if (o.getTimePoint()>timePointMax) it.remove();
        }
        MicrochannelProcessor.debug=true;
        MicrochannelProcessor mp = new MicrochannelProcessor(new MicroChannelBF2D()).setTimePointNumber(5);
        mp.segmentAndTrack(0, rootTrack, new PreFilterSequence(""), new PostFilterSequence(""));
        
        ObjectPopulation pop  = rootTrack.get(0).getObjectPopulation(0);
        new IJImageDisplayer().showImage(pop.getLabelImage());
        
    }
}
