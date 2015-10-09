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
package images;

import static TestUtils.GenerateSyntheticData.generateImages;
import static boa.gui.GUI.logger;
import core.Processor;
import dataStructure.configuration.ChannelImage;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.ExperimentDAO;
import dataStructure.configuration.Structure;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import static org.junit.Assert.assertEquals;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import plugins.PluginFactory;
import plugins.plugins.segmenters.SimpleThresholder;
import plugins.plugins.thresholders.ConstantValue;
import plugins.plugins.trackers.ObjectIdxTracker;
import utils.MorphiumUtils;

/**
 *
 * @author jollion
 */
public class ImageDAOTest {
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();
    
    Experiment xp;
    Morphium m;
    ObjectDAO objectDAO;
    ExperimentDAO xpDAO;
    
    @Test
    public void testImageDAO() throws IOException {
        
        try {
            MorphiumConfig cfg = new MorphiumConfig();
            cfg.setGlobalLogLevel(3);
            cfg.setDatabase("testImageDAO");
            cfg.addHost("localhost", 27017);
            m=new Morphium(cfg);
            m.clearCollection(Experiment.class);
            m.clearCollection(StructureObject.class);
            xpDAO = new ExperimentDAO(m);
            objectDAO = new ObjectDAO(m, xpDAO);
            MorphiumUtils.addDereferencingListeners(m, objectDAO, xpDAO);
            
            // generate XP
            xp = new Experiment("test");
            ChannelImage cMic = new ChannelImage("ChannelImageMicroChannel");
            xp.getChannelImages().insert(cMic);
            ChannelImage cBact = new ChannelImage("ChannelImageBact");
            xp.getChannelImages().insert(cBact);
            xp.getStructures().removeAllElements();
            Structure microChannel = new Structure("MicroChannel", -1, 0);
            Structure bacteries = new Structure("Bacteries", 0, 1);
            xp.getStructures().insert(microChannel, bacteries);
            
            // processing chains
            PluginFactory.findPlugins("plugins.plugins");
            microChannel.getProcessingChain().setSegmenter(new SimpleThresholder(new ConstantValue(1)));
            bacteries.getProcessingChain().setSegmenter(new SimpleThresholder(new ConstantValue(1)));
            
            // set-up traking
            microChannel.setTracker(new ObjectIdxTracker());
            bacteries.setTracker(new ObjectIdxTracker());
            
            // set up I/O directory & create fields
            File inputImage = testFolder.newFolder();
            generateImages("field1", inputImage.getAbsolutePath(), 1, 2);
            generateImages("field2", inputImage.getAbsolutePath(), 1, 2);
            generateImages("field3", inputImage.getAbsolutePath(), 1, 2);
            Processor.importFiles(new String[]{inputImage.getAbsolutePath()}, xp);
            assertEquals("number fields", 3, xp.getMicrocopyFieldCount());
            File output = testFolder.newFolder();
            xp.setOutputImageDirectory(output.getAbsolutePath());
            //xp.setOutputImageDirectory("/tmp/test/");
            
            // save to morphium
            xpDAO.store(xp);
            
            // process
            assertEquals("number of files before preProcess", 0, countFiles(new File(xp.getOutputImageDirectory())));
            Processor.preProcessImages(xp, objectDAO, true);
            assertEquals("number of files after preProcess", 6, countFiles(new File(xp.getOutputImageDirectory())));
            Processor.processStructures(xp, objectDAO);
            assertEquals("number of files after preProcess", 12, countFiles(new File(xp.getOutputImageDirectory())));
            
            StructureObject root = objectDAO.getRoot("field1", 0);
            StructureObject mc = objectDAO.getObjects(root.getId(), 0)[0];
            objectDAO.deleteChildren(mc.getId(), 1);
            
            assertEquals("number of files after delete children", 11, countFiles(new File(xp.getOutputImageDirectory())));
            objectDAO.delete(mc);
            assertEquals("number of files after delete object", 10, countFiles(new File(xp.getOutputImageDirectory())));
            objectDAO.deleteObjectsFromField("field2");
            assertEquals("number of files after delete field", 8, countFiles(new File(xp.getOutputImageDirectory())));
            objectDAO.deleteAllObjects();
            assertEquals("number of files after delete all", 6, countFiles(new File(xp.getOutputImageDirectory())));
            
        } catch (UnknownHostException ex) {
            logger.error("db connection error", ex);
        }
    }
    

    
    private static int countFiles(File dir) {
        if (dir.isFile()) return 1;
        else {
            int count=0;
            for (File f : dir.listFiles()) count+=countFiles(f);
            return count;
        }
    }
}
