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
import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.objects.DBConfiguration;
import core.Processor;
import dataStructure.configuration.ChannelImage;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.ExperimentDAO;
import dataStructure.configuration.Structure;
import dataStructure.containers.ObjectContainer;
import dataStructure.containers.RegionVoxelsDB;
import dataStructure.objects.Measurements;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import plugins.PluginFactory;
import plugins.plugins.measurements.ObjectInclusionCount;
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
    
    DBConfiguration db;
    
    /*public static void main(String[] args) throws IOException {
        new ImageDAOTest().testImageDAO();
    }*/
    
    @Test
    public void testImageDAO() throws IOException {
        db = new DBConfiguration("testImageDAO");
        db.clearObjectsInDB();
        
        // generate XP
        Experiment xp = new Experiment("test");
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
        int embVoxNb = ObjectContainer.MAX_VOX_3D_EMB;
        int size = (int)(Math.pow(embVoxNb, 1d/3d)+1.5);
        int old3DLimit = ObjectContainer.MAX_VOX_3D;
        ObjectContainer.MAX_VOX_3D=ObjectContainer.MAX_VOX_3D_EMB;
        generateImages("field1", inputImage.getAbsolutePath(), 1, 2, size);
        generateImages("field2", inputImage.getAbsolutePath(), 1, 2, size);
        generateImages("field3", inputImage.getAbsolutePath(), 1, 2, size);
        Processor.importFiles(xp, inputImage.getAbsolutePath());
        assertEquals("number fields", 3, xp.getMicrocopyFieldCount());
        xp.setOutputImageDirectory(testFolder.newFolder().getAbsolutePath());
        //xp.setOutputImageDirectory("/tmp/test"); new File(xp.getOutputImageDirectory()).mkdirs();
        // save to morphium
        db.getXpDAO().store(xp);
        long t0 = System.currentTimeMillis();
        // process
        assertEquals("number of files before preProcess", 0, countFiles(new File(xp.getOutputImageDirectory())));
        Processor.preProcessImages(xp, db.getDao(), true);
        assertEquals("number of files after preProcess", 6, countFiles(new File(xp.getOutputImageDirectory())));
        Processor.processAndTrackStructures(xp, db.getDao());
        db.getDao().waiteForWrites();
        ObjectContainer.MAX_VOX_3D=old3DLimit;
        int sleep = 1000;
        try {
            Thread.sleep(sleep); // wait that all images are written
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        assertEquals("number of files after process", 12, countFiles(new File(xp.getOutputImageDirectory())));
        db.getDao().waiteForWrites();
        StructureObject root = db.getDao().getRoot("field1", 0);
        StructureObject mc = db.getDao().getObjects(root.getId(), 0).get(0);
        db.getDao().deleteChildren(mc, 1);

        assertEquals("number of files after delete children", 11, countFiles(new File(xp.getOutputImageDirectory())));
        db.getDao().delete(mc);
        assertEquals("number of files after delete object", 10, countFiles(new File(xp.getOutputImageDirectory())));
        db.getDao().deleteObjectsFromField("field2");
        assertEquals("number of files after delete field", 8, countFiles(new File(xp.getOutputImageDirectory())));
        db.getDao().deleteAllObjects();
        assertEquals("number of files after delete all", 6, countFiles(new File(xp.getOutputImageDirectory())));
        long t1 = System.currentTimeMillis();
        
        // test object stored in other collection + measurements
        // limit set back -> objects stored in DB
        Processor.processAndTrackStructures(xp, db.getDao());
        xp.addMeasurement(new ObjectInclusionCount(1, 1, 50));
        Processor.performMeasurements(xp, db.getDao());
        db.getDao().waiteForWrites();
        assertEquals("number of files after process (limit set back)", 6, countFiles(new File(xp.getOutputImageDirectory())));
        root = db.getDao().getRoot("field1", 0);
        mc = db.getDao().getObjects(root.getId(), 0).get(0);
        assertEquals("number of stored objects ", 6, countObjects(db, RegionVoxelsDB.class));
        assertEquals("number of measurements ", 3, countObjects(db, Measurements.class));
        assertTrue("object voxels retrieved: ", mc.getObject().getVoxels().size()>1);

        db.getDao().deleteChildren(mc, 1);
        assertEquals("number of files after delete children", 5, countObjects(db, RegionVoxelsDB.class));
        assertEquals("number of measurements after delete children", 2, countObjects(db, Measurements.class));
        db.getDao().delete(mc);
        assertEquals("number of files after delete object", 4, countObjects(db, RegionVoxelsDB.class));
        assertEquals("number of measurements after delete object", 2, countObjects(db, Measurements.class));
        db.getDao().deleteObjectsFromField("field2");
        assertEquals("number of files after delete field", 2, countObjects(db, RegionVoxelsDB.class));
        assertEquals("number of measurements after delete field", 1, countObjects(db, Measurements.class));
        db.getDao().deleteAllObjects();
        assertEquals("number of files after delete all", 0, countObjects(db, RegionVoxelsDB.class));
        assertEquals("number of measurements after delete all", 0, countObjects(db, Measurements.class));
        long t2 = System.currentTimeMillis();
        logger.debug("ImageDAO TEST : time with file system: {}, time with db: {}", t1-t0-sleep, t2-t1);
    }
    
    private static int countObjects(DBConfiguration db, Class clazz) {
        return (int) db.getMorphium().createQueryFor(clazz).countAll();
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
