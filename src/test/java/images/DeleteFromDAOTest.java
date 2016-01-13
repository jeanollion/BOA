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
import dataStructure.objects.MorphiumMasterDAO;
import core.Processor;
import dataStructure.configuration.ChannelImage;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.ExperimentDAO;
import dataStructure.configuration.Structure;
import dataStructure.containers.ObjectContainer;
import dataStructure.objects.BasicMasterDAO;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.Measurements;
import dataStructure.objects.MorphiumObjectDAO;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import de.caluga.morphium.query.Query;
import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import plugins.PluginFactory;
import plugins.plugins.measurements.ObjectInclusionCount;
import plugins.plugins.processingScheme.SegmentThenTrack;
import plugins.plugins.segmenters.SimpleThresholder;
import plugins.plugins.thresholders.ConstantValue;
import plugins.plugins.trackers.ObjectIdxTracker;
import testPlugins.dummyPlugins.DummySegmenter;
import utils.MorphiumUtils;

/**
 *
 * @author jollion
 */
public class DeleteFromDAOTest {
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();
    
    /*public static void main(String[] args) throws IOException {
        new DeleteFromDAOTest().deleteTest();
    }*/
    
    @Test 
    public void deleteTestMorphium() throws IOException {
        MasterDAO dao = new MorphiumMasterDAO("testImageDAO");
        dao.reset();
        deleteTest(dao);
    }
    
    //@Test 
    public void deleteTestBasic() throws IOException {
        MasterDAO dao = new BasicMasterDAO();
        dao.reset();
        deleteTest(dao);
        // probleme store: concurent modification: les children sont déjà set lorsque store est apellée... ajouter un test?
    }
    
    
    public void deleteTest(MasterDAO masterDAO) throws IOException {
        String prefix = "DAO type: "+masterDAO.getClass().getSimpleName()+"; ";
        // generate XP
        Experiment xp = new Experiment("test");
        ChannelImage cMic = new ChannelImage("ChannelImageMicroChannel");
        xp.getChannelImages().insert(cMic);
        ChannelImage cBact = new ChannelImage("ChannelImageBact");
        xp.getChannelImages().insert(cBact);
        xp.getStructures().removeAllElements();
        Structure microChannel = new Structure("MicroChannel", -1, 0);
        Structure bacteria = new Structure("Bacteries", 0, 1);
        xp.getStructures().insert(microChannel, bacteria);

        // processing chains
        PluginFactory.findPlugins("plugins.plugins");
        microChannel.setProcessingScheme(new SegmentThenTrack(new SimpleThresholder(new ConstantValue(1)), new ObjectIdxTracker()));
        bacteria.setProcessingScheme(new SegmentThenTrack(new SimpleThresholder(new ConstantValue(1)), new ObjectIdxTracker()));

        // set up I/O directory & create fields
        File inputImage = testFolder.newFolder();
        generateImages("field1", inputImage.getAbsolutePath(), 1, 2, 1);
        generateImages("field11", inputImage.getAbsolutePath(), 1, 2, 1);
        generateImages("field2", inputImage.getAbsolutePath(), 1, 2, 1);
        generateImages("field3", inputImage.getAbsolutePath(), 1, 2, 1);
        generateImages("field4", inputImage.getAbsolutePath(), 1, 2, 1);
        Processor.importFiles(xp, inputImage.getAbsolutePath());
        assertEquals("number fields", 5, xp.getMicrocopyFieldCount());
        xp.setOutputImageDirectory(testFolder.newFolder().getAbsolutePath());
        //xp.setOutputImageDirectory("/tmp/test"); new File(xp.getOutputImageDirectory()).mkdirs();
        // save to morphium
        masterDAO.setExperiment(xp);
        long t0 = System.currentTimeMillis();
        // process
        assertEquals("number of files before preProcess", 0, countFiles(new File(xp.getOutputImageDirectory())));
        Processor.preProcessImages(masterDAO, true);
        assertEquals("number of files after preProcess",10, countFiles(new File(xp.getOutputImageDirectory())));
        Processor.processAndTrackStructures(masterDAO, true);
        
        xp.addMeasurement(new ObjectInclusionCount(1, 1, 50));
        Processor.performMeasurements(masterDAO);
        ObjectDAO dao = masterDAO.getDao("field1");
        ObjectDAO dao11 = masterDAO.getDao("field11");
        StructureObject root = dao.getRoot(0);
        StructureObject mc = dao.getChildren(root, 0).get(0);
        assertEquals(prefix+"number of stored objects ", 15, countObjects(masterDAO, StructureObject.class));
        assertEquals(prefix+"number of measurements ", 5, countObjects(masterDAO, Measurements.class));
        assertTrue(prefix+"object retrieved: ", mc.getObject().getVoxels().size()>=1);
        //revoir les fonctions deletes avec la gestions des enfant directs et indirects.. la fonction delete doit elle appeller deleteChildren?
        dao.deleteChildren(mc, 1);
        assertEquals(prefix+"number of objects after delete children", 14, countObjects(masterDAO, StructureObject.class));
        assertEquals(prefix+"number of measurements after delete children", 4, countObjects(masterDAO, Measurements.class));
        dao.delete(root, true);
        assertEquals(prefix+"number of objects after delete root", 12, countObjects(masterDAO, StructureObject.class));
        assertEquals(prefix+"number of measurements after delete root", 4, countObjects(masterDAO, Measurements.class));
        dao11.deleteChildren(dao11.getRoot(0), 0);
        assertEquals(prefix+"number of objects after delete root's children", 10, countObjects(masterDAO, StructureObject.class));
        assertEquals(prefix+"number of measurements after delete root's children", 3, countObjects(masterDAO, Measurements.class));
        dao11.deleteAllObjects();
        assertEquals(prefix+"number of objects after delete all objects", 9, countObjects(masterDAO, StructureObject.class));
        assertEquals(prefix+"number of measurements after delete all objects", 3, countObjects(masterDAO, Measurements.class));
        
        masterDAO.getDao("field2").deleteAllObjects();
        assertEquals(prefix+"number of objects after delete field", 6, countObjects(masterDAO, StructureObject.class));
        assertEquals(prefix+"number of measurements after delete field", 2, countObjects(masterDAO, Measurements.class));
        masterDAO.getDao("field3").deleteObjectsByStructureIdx(0);
        assertEquals(prefix+"number of objects after delete by structureIdx", 4, countObjects(masterDAO, StructureObject.class));
        assertEquals(prefix+"number of measurements after by structureIdx", 1, countObjects(masterDAO, Measurements.class));        
        masterDAO.deleteAllObjects();
        assertEquals(prefix+"number of files after delete all", 0, countObjects(masterDAO, StructureObject.class));
        assertEquals(prefix+"number of measurements after delete all", 0, countObjects(masterDAO, Measurements.class));
        long t2 = System.currentTimeMillis();
    }
    
    private static int countObjects(MasterDAO db, Class clazz) {
        if (db instanceof MorphiumMasterDAO) {
            MorphiumMasterDAO m = (MorphiumMasterDAO)db;
            int count = 0; 
            for (String f : db.getExperiment().getFieldsAsString()) {
                Query q = m.getMorphium().createQueryFor(clazz);
                if (clazz==StructureObject.class) q.setCollectionName(m.getDao(f).collectionName);
                else if (clazz == Measurements.class) q.setCollectionName(m.getDao(f).getMeasurementsDAO().collectionName);
                else throw new IllegalArgumentException("Class: "+clazz+" no managed by test");
                count+=q.countAll();
            }
            return count;
        } else if (db instanceof BasicMasterDAO) {
            ArrayList<StructureObject> allObjects = new ArrayList<StructureObject>();
            for (String f : db.getExperiment().getFieldsAsString()) {
                ArrayList<StructureObject> rootTrack = db.getDao(f).getRoots();
                allObjects.addAll(rootTrack);
                for (int s = 0; s<db.getExperiment().getStructureCount(); ++s) for (StructureObject r : rootTrack) allObjects.addAll(r.getChildren(s));
            }
            if (clazz == StructureObject.class) {
                 return allObjects.size();
            } else if (clazz == Measurements.class) {
                int count = 0;
                for (StructureObject o : allObjects) if (o.hasMeasurements()) ++count;
                return count;
            }
        } else throw new IllegalArgumentException("MasterDAO of class: "+db.getClass()+" no managed by test");
        return -1;
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
