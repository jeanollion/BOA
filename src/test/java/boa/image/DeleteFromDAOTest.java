/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.image;

import static boa.test_utils.GenerateSyntheticData.generateImages;
import static boa.test_utils.TestUtils.logger;
import boa.core.Processor;
import boa.core.Task;
import boa.configuration.experiment.ChannelImage;
import boa.configuration.experiment.Experiment;
import boa.configuration.experiment.Structure;
import boa.core.Processor.MEASUREMENT_MODE;
import boa.data_structure.region_container.RegionContainer;
import boa.data_structure.dao.BasicMasterDAO;
import boa.data_structure.dao.DBMapObjectDAO;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.Measurements;
import boa.data_structure.Region;
import boa.data_structure.dao.ObjectDAO;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.image.BlankMask;
import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import boa.plugins.PluginFactory;
import boa.plugins.plugins.measurements.ObjectInclusionCount;
import boa.plugins.plugins.processing_pipeline.SegmentThenTrack;
import boa.plugins.plugins.segmenters.SimpleThresholder;
import boa.plugins.plugins.thresholders.ConstantValue;
import boa.plugins.plugins.trackers.ObjectIdxTracker;


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
    
    //@Test 
    public void deleteTestMorphium() throws IOException {
        MasterDAO dao = new Task("testImageDAO").getDB();
        MasterDAO.deleteObjectsAndSelectionAndXP(dao);
        deleteTest(dao);
    }
    
    //@Test 
    public void deleteTestBasic() throws IOException {
        MasterDAO dao = new BasicMasterDAO();
        MasterDAO.deleteObjectsAndSelectionAndXP(dao);
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
        PluginFactory.findPlugins("boa.plugins.plugins");
        microChannel.setProcessingPipeline(new SegmentThenTrack(new SimpleThresholder(new ConstantValue(1)), new ObjectIdxTracker()));
        bacteria.setProcessingPipeline(new SegmentThenTrack(new SimpleThresholder(new ConstantValue(1)), new ObjectIdxTracker()));

        // set up I/O directory & create fields
        File inputImage = testFolder.newFolder();
        generateImages("field1", inputImage.getAbsolutePath(), 1, 2, 1);
        generateImages("field11", inputImage.getAbsolutePath(), 1, 2, 1);
        generateImages("field2", inputImage.getAbsolutePath(), 1, 2, 1);
        generateImages("field3", inputImage.getAbsolutePath(), 1, 2, 1);
        generateImages("field4", inputImage.getAbsolutePath(), 1, 2, 1);
        Processor.importFiles(xp, true, null, inputImage.getAbsolutePath());
        assertEquals("number fields", 5, xp.getPositionCount());
        xp.setOutputDirectory(testFolder.newFolder().getAbsolutePath());
        //xp.setOutputImageDirectory("/tmp/test"); new File(xp.getOutputImageDirectory()).mkdirs();
        // save to morphium
        masterDAO.setExperiment(xp);
        long t0 = System.currentTimeMillis();
        // process
        assertEquals("number of files before preProcess", 0, countFiles(new File(xp.getOutputImageDirectory())));
        try {
            Processor.preProcessImages(masterDAO);
        } catch (Exception ex) {
            assertTrue("failed to preProcess images", false);
        }
        assertEquals("number of files after preProcess",10, countFiles(new File(xp.getOutputImageDirectory())));
        Processor.processAndTrackStructures(masterDAO, true);
        
        xp.addMeasurement(new ObjectInclusionCount(1, 1, 50));
        Processor.performMeasurements(masterDAO, MEASUREMENT_MODE.ERASE_ALL, null);
        ObjectDAO dao = masterDAO.getDao("field1");
        ObjectDAO dao11 = masterDAO.getDao("field11");
        StructureObject root = dao.getRoots().get(0);
        StructureObject mc = dao.getChildren(root, 0).get(0);
        assertEquals(prefix+"number of stored objects ", 15, countObjects(masterDAO, StructureObject.class));
        assertEquals(prefix+"number of measurements ", 5, countObjects(masterDAO, Measurements.class));
        assertTrue(prefix+"object retrieved: ", mc.getRegion().getVoxels().size()>=1);
        logger.debug("before delete children");
        dao.deleteChildren(mc, 1);
        assertEquals(prefix+"number of objects after delete children", 14, countObjects(masterDAO, StructureObject.class));
        assertEquals(prefix+"number of measurements after delete children", 4, countObjects(masterDAO, Measurements.class));
        logger.debug("before delete ");
        dao.delete(root, true, false, false);
        assertEquals(prefix+"number of objects after delete root", 12, countObjects(masterDAO, StructureObject.class));
        assertEquals(prefix+"number of measurements after delete root", 4, countObjects(masterDAO, Measurements.class));
        logger.debug("before delete children2");
        dao11.deleteChildren(dao11.getRoots().get(0), 0);
        assertEquals(prefix+"number of objects after delete root's children", 10, countObjects(masterDAO, StructureObject.class));
        assertEquals(prefix+"number of measurements after delete root's children", 3, countObjects(masterDAO, Measurements.class));
        logger.debug("before delete all 1");
        dao11.deleteAllObjects();
        assertEquals(prefix+"number of objects after delete all objects", 9, countObjects(masterDAO, StructureObject.class));
        assertEquals(prefix+"number of measurements after delete all objects", 3, countObjects(masterDAO, Measurements.class));
        logger.debug("before delete all objects 2");
        masterDAO.getDao("field2").deleteAllObjects();
        assertEquals(prefix+"number of objects after delete field", 6, countObjects(masterDAO, StructureObject.class));
        assertEquals(prefix+"number of measurements after delete field", 2, countObjects(masterDAO, Measurements.class));
        logger.debug("before delete by structureIdx");
        masterDAO.getDao("field3").deleteObjectsByStructureIdx(0);
        assertEquals(prefix+"number of objects after delete by structureIdx", 4, countObjects(masterDAO, StructureObject.class));
        assertEquals(prefix+"number of measurements after by structureIdx", 1, countObjects(masterDAO, Measurements.class));        
        masterDAO.deleteAllObjects();
        assertEquals(prefix+"number of files after delete all", 0, countObjects(masterDAO, StructureObject.class));
        assertEquals(prefix+"number of measurements after delete all", 0, countObjects(masterDAO, Measurements.class));
        long t2 = System.currentTimeMillis();
    }
    
    @Test
    public void testDeleteMass() {
        MasterDAO db = new Task("testImageDAO").getDB();
        MasterDAO.deleteObjectsAndSelectionAndXP(db);
        String f = "testField";
        int[] count = new int[]{10, 10, 10};
        Experiment xp = new Experiment("");
        xp.createPosition(f);
        xp.getStructures().insert(new Structure("S0", -1, 0), new Structure("Sub1", 0, 0), new Structure("Sub2",1, 0));
        db.setExperiment(xp);
        
        ObjectDAO dao = db.getDao(f);
        StructureObject root = new StructureObject(0, new BlankMask(1, 1, 1), dao);
        Region o = new Region(new BlankMask(1, 1, 1), 1, false);
        List<StructureObject> s0 = new ArrayList<StructureObject>();
        for (int i = 0; i<count[0]; ++i) {
            StructureObject oi = new StructureObject(0, 0, i, o, root);
            s0.add(oi);
            List<StructureObject> s1 = new ArrayList<StructureObject>(count[1]);
            for (int j = 0; j<count[1]; ++j) {
                StructureObject oj = new StructureObject(0, 1, j, o, oi);
                s1.add(oj);
                List<StructureObject> s2 = new ArrayList<StructureObject>(count[2]);
                for (int k = 0; k<count[2]; ++k) {
                    StructureObject ok = new StructureObject(0, 2, k, o, oj);
                    s2.add(ok);
                    ok.getMeasurements().setValue("test", k);
                }
                oj.setChildren(s2, 2);
                oj.getMeasurements().setValue("test", j);
            }
            oi.setChildren(s1, 1);
            oi.getMeasurements().setValue("test", i);
        }
        root.setChildren(s0, 0);
        
        int n = 1;
        dao.store(root);
        assertEquals("store root ", 1, countObjects(db, StructureObject.class) );
        for (int i = 0; i<=2; ++i) { 
            dao.store(root.getChildren(i)); 
            n+=root.getChildren(i).size();

        }
        assertEquals("store children count structureObjects", n, countObjects(db, StructureObject.class) );
        assertEquals("store count measurements", n-1, countObjects(db, Measurements.class));
        dao.clearCache();
        root = dao.getRoots().get(0);
        n =1;
        for (int i = 0; i<=2; ++i) {
            n*=count[i];
            List<StructureObject> ci = root.getChildren(i);
            assertEquals("retrieve children : s:"+i, n, ci.size());
        }
        dao.delete(root.getChildren(0), true, true, true);
        assertEquals("delete children count structureObjects", 1, countObjects(db, StructureObject.class));
        assertEquals("delete count measurements", 0, countObjects(db, Measurements.class));
    }
    
    //@Test
    public void testDeleteAndRelabel() {
        MasterDAO db = new Task("testImageDAO").getDB();
        MasterDAO.deleteObjectsAndSelectionAndXP(db);
        String f = "testField";
        
        Experiment xp = new Experiment("");
        xp.getStructures().insert(new Structure("MicroChannel", -1, 0));
        xp.createPosition(f);
        db.setExperiment(xp);
        
        ObjectDAO dao = db.getDao(f);
        StructureObject root = new StructureObject(0, new BlankMask(1, 1, 1), dao);
        Region o = new Region(new BlankMask(1, 1, 1), 1, false);
        final StructureObject c1 = new StructureObject(0, 0, 0, o, root);
        final StructureObject c2 = new StructureObject(0, 0, 1, o, root);
        final StructureObject c3 = new StructureObject(0, 0, 2, o, root);
        final StructureObject c4 = new StructureObject(0, 0, 3, o, root);
        final StructureObject c5 = new StructureObject(0, 0, 4, o, root);
        List<StructureObject> children = new ArrayList<StructureObject>(){{add(c1);add(c2);add(c3);add(c4);add(c5);}};
        assertEquals("dao cleared", 0, countObjects(db, StructureObject.class));
        dao.store(root);
        assertEquals("root stored", 1, countObjects(db, StructureObject.class));
        dao.store(children);
        dao.clearCache();
        root = dao.getRoots().get(0);
        children = root.getChildren(0);
        assertEquals("retrieve children", 5, children.size());
        List<StructureObject> toDelete = new ArrayList(children.subList(1, 3));
        dao.delete(toDelete, true, true, true);
        assertEquals("delete list, from parent", 3, root.getChildren(0).size());
        assertEquals("delete list, relabel", true, root.getChildren(0).get(1).getIdx()==1);
        dao.clearCache();
        root = dao.getRoots().get(0);
        assertEquals("delete list, relabel stored", true, root.getChildren(0).get(1).getIdx()==1);
        
        // test with single object
        dao.delete(root.getChildren(0).get(0), true, true, true);
        assertEquals("delete single, from parent", 2, root.getChildren(0).size());
        assertEquals("delete single, relabel", true, root.getChildren(0).get(0).getIdx()==0);
        dao.clearCache();
        root = dao.getRoots().get(0);
        assertEquals("delete single, relabel stored", true, root.getChildren(0).get(0).getIdx()==0);
    }
    
    private static int countObjects(MasterDAO db, Class clazz) {
        if (db instanceof BasicMasterDAO) {
            ArrayList<StructureObject> allObjects = new ArrayList<StructureObject>();
            for (String f : db.getExperiment().getPositionsAsString()) {
                List<StructureObject> rootTrack = db.getDao(f).getRoots();
                allObjects.addAll(rootTrack);
                for (int s = 0; s<db.getExperiment().getStructureCount(); ++s) {
                    for (StructureObject r : rootTrack) {
                        allObjects.addAll(r.getChildren(s));
                    }
                }
            }
            if (clazz == StructureObject.class) {
                 return allObjects.size();
            } else if (clazz == Measurements.class) {
                int count = 0;
                for (StructureObject o : allObjects) if (!o.getMeasurements().getValues().isEmpty()) ++count;
                return count;
            }
        } else {
            db.clearCache();
            ArrayList<StructureObject> allObjects = new ArrayList<>();
            for (String p : db.getExperiment().getPositionsAsString()) {
                ObjectDAO dao = db.getDao(p);
                for (int s = 0; s<db.getExperiment().getStructureCount(); ++s) {
                    allObjects.addAll(StructureObjectUtils.getAllObjects(dao, s));
                }
            }
            if (StructureObject.class.equals(clazz)) return allObjects.size();
            else if (Measurements.class.equals(clazz)) {
                allObjects.removeIf(o->!o.hasMeasurements());
                return allObjects.size();
            }
        }
        
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
