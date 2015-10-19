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
package dataStructure;

import TestUtils.Utils;
import static TestUtils.Utils.showImageIJ;
import configuration.parameters.NumberParameter;
import testPlugins.dummyPlugins.DummySegmenter;
import core.Processor;
import dataStructure.configuration.ChannelImage;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.ExperimentDAO;
import dataStructure.configuration.MicroscopyField;
import dataStructure.configuration.Structure;
import dataStructure.containers.ImageDAO;
import dataStructure.containers.MultipleImageContainer;
import dataStructure.objects.StructureObject;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObjectUtils;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import image.BlankMask;
import image.Image;
import image.ImageByte;
import image.ImageFormat;
import image.ImageWriter;
import image.TypeConverter;
import images.ImageIOTest;
import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import plugins.PluginFactory;
import plugins.Segmenter;
import plugins.plugins.trackers.ObjectIdxTracker;
import plugins.plugins.transformations.SimpleTranslation;
import utils.MorphiumUtils;

/**
 *
 * @author jollion
 */
public class ProcessingTest {
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();
    
    public static ImageByte[][] createDummyImagesTC(int sizeX, int sizeY, int sizeZ, int timePointNumber, int channelNumber) {
        ImageByte[][] images = new ImageByte[timePointNumber][channelNumber];
        for (int t = 0; t<timePointNumber; t++) {
            for (int c = 0; c<channelNumber;c++) {
                images[t][c] = new ImageByte("t"+t+"c"+c, sizeX, sizeY, sizeZ);
                images[t][c].setPixel(t, c, c, 1);
                images[t][c].setCalibration(0.1f, 0.23f);
            }
        }
        return images;
    }
    
    @Test
    public void importFieldTest() {
        // creation de l'image de test
        String title = "imageTestMultiple";
        ImageFormat format = ImageFormat.OMETIF;
        File folder = testFolder.newFolder("TestImages");
        int timePoint = 3;
        int channel = 2;
        ImageByte[][] images = createDummyImagesTC(6, 5 ,4,  timePoint, channel);
        ImageByte[][] images2 = createDummyImagesTC(6, 5 ,4, timePoint, channel+1);
        ImageByte[][] images3 = createDummyImagesTC(6, 5 ,4, timePoint+1, channel);
        
        ImageWriter.writeToFile(folder.getAbsolutePath(), title, format, images);
        File folder2 = new File(folder.getAbsolutePath()+File.separator+"subFolder");
        folder2.mkdir();
        ImageWriter.writeToFile(folder2.getAbsolutePath(), title, format, images);
        ImageWriter.writeToFile(folder2.getAbsolutePath(), title+"2", format, images, images, images2, images3);
        
        Experiment xp = new Experiment("testXP", new Structure("structure"));
        xp.getChannelImages().insert(new ChannelImage("channel1"), new ChannelImage("channel2"));
        
        String[] files = new String[]{folder.getAbsolutePath()};
        Processor.importFiles(files, xp);
        assertEquals("number of fields detected", 6-1-1, xp.getMicrocopyFieldCount()); // 6 - 1 (unique title) - 1 (channel number)
        Utils.assertImage(images[0][0], xp.getMicroscopyField(title).getInputImages().getImage(0, 0), 0);
    }
    
    @Test
    public void testImportFieldKeyWord() {
        // creation de l'image de test
        String title = "imageTestMultiple";
        ImageFormat format = ImageFormat.OMETIF;
        File folder = testFolder.newFolder("TestImages");
        int timePoint = 3;
        ImageByte[][] images = createDummyImagesTC(6, 5 ,4,  timePoint, 1);
        ImageByte[][] images2 = createDummyImagesTC(6, 5 ,4,  timePoint+1, 1);
        
        ImageWriter.writeToFile(folder.getAbsolutePath(), title+"_c1", format, images);
        ImageWriter.writeToFile(folder.getAbsolutePath(), title+"_c2", format, images);
        ImageWriter.writeToFile(folder.getAbsolutePath(), title+"1_c1", format, images);
        ImageWriter.writeToFile(folder.getAbsolutePath(), title+"1_c2", format, images);
        ImageWriter.writeToFile(folder.getAbsolutePath(), title+"2_c1", format, images);
        ImageWriter.writeToFile(folder.getAbsolutePath(), title+"3_c1", format, images);
        ImageWriter.writeToFile(folder.getAbsolutePath(), title+"3_c2", format, images2);
        
        File folder2 = new File(folder.getAbsolutePath()+File.separator+"subFolder");
        folder2.mkdir();
        ImageWriter.writeToFile(folder2.getAbsolutePath(), title+"_c1", format, images);
        ImageWriter.writeToFile(folder2.getAbsolutePath(), title+"_c2", format, images);
        ImageWriter.writeToFile(folder2.getAbsolutePath(), title+"4_c1", format, images);
        ImageWriter.writeToFile(folder2.getAbsolutePath(), title+"4_c2", format, images);
        
        Experiment xp = new Experiment("testXP", new Structure("structure"));
        xp.setImportImageMethod(Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD);
        xp.getChannelImages().insert(new ChannelImage("channel1", "_c1"), new ChannelImage("channel2", "_c2"));
        
        String[] files = new String[]{folder.getAbsolutePath()};
        Processor.importFiles(files, xp);
        assertEquals("number of fields detected", 6-1-1-1, xp.getMicrocopyFieldCount()); // 6 - 1 (unique title) - 1 (channel number)-1(timepoint number)
        Utils.assertImage(images[0][0], xp.getMicroscopyField(title).getInputImages().getImage(0, 0), 0);
    }
    
    @Test
    public void preProcessingTest() {
        // set-up XP
        File daoFolder = testFolder.newFolder("TestPreProcessingDAOFolder");
        Experiment xp = new Experiment("test");
        ChannelImage ci1 = xp.getChannelImages().createChildInstance();
        ChannelImage ci2 = xp.getChannelImages().createChildInstance();
        xp.getChannelImages().insert(ci1, ci2);
        xp.setOutputImageDirectory(daoFolder.getAbsolutePath());
        //xp.setOutputImageDirectory("/tmp");
        xp.setImageDAOType(Experiment.ImageDAOTypes.LocalFileSystem);
        
        // import fields
        ImageByte[][] images = createDummyImagesTC(6, 5 ,4, 3, 2);
        images[0][0].setPixel(0, 0, 0, 1);
        File folder = testFolder.newFolder("TestImagesPreProcessing");
        ImageWriter.writeToFile(folder.getAbsolutePath(), "field1", ImageFormat.OMETIF, images);
        Processor.importFiles(new String[]{folder.getAbsolutePath()}, xp);
        MicroscopyField f = xp.getMicroscopyField(0);
        assertEquals("number of fields detected", 1, xp.getMicrocopyFieldCount());
        
        //set-up pre-processing chains
        PluginFactory.findPlugins("plugins.plugins.transformations");
        SimpleTranslation t = new SimpleTranslation(1, 0, 0);
        f.getPreProcessingChain().addTransformation(0, null, t);
        SimpleTranslation t2 = new SimpleTranslation(0, 1, 0);
        f.getPreProcessingChain().addTransformation(0, null, t2);
        
        //pre-process
        Processor.preProcessImages(xp, null, true);
        
        // passage through morphium
        MorphiumConfig cfg = new MorphiumConfig();
        cfg.setGlobalLogLevel(3);
        cfg.setDatabase("testdb");
        try {
            cfg.addHost("localhost", 27017);
        } catch (UnknownHostException ex) {
            Utils.logger.error("create morphium", ex);
        }
        Morphium m=new Morphium(cfg);
        m.clearCollection(Experiment.class);
        m.store(xp);
        m.clearCachefor(Experiment.class);
        xp = m.createQueryFor(Experiment.class).getById(xp.getId());
        
        // test 
        ImageDAO dao = xp.getImageDAO();
        Image image = dao.openPreProcessedImage(0, 0, "field1");
        assertTrue("Image saved in DAO", image!=null);
        SimpleTranslation tInv = new SimpleTranslation(-1, -1, 0);
        Image imageInv = tInv.applyTransformation(0, 0, image);
        Utils.assertImage(images[0][0], TypeConverter.toByte(imageInv, null), 0);
    }
    
    private static ImageByte getMask(StructureObject root, int[] pathToRoot) {
        ImageByte mask = new ImageByte("mask", root.getMask());
        int startLabel = 1;
        for (StructureObject o : StructureObjectUtils.getAllObjects(root, pathToRoot)) mask.appendBinaryMasks(startLabel++, o.getMask().addOffset(o.getRelativeBoundingBox(null)));
        return mask;
    }
    
    @Test
    public void StructureObjectTestStore() {
        MorphiumConfig cfg = new MorphiumConfig();
        cfg.setGlobalLogLevel(3);
        cfg.setDatabase("testdb");
        try {
            cfg.addHost("localhost", 27017);
        } catch (UnknownHostException ex) {
            Utils.logger.error("create morphium", ex);
        }
        Morphium m=new Morphium(cfg);
        m.clearCollection(StructureObject.class);
        m.clearCollection(Experiment.class);
        Experiment xp = new Experiment("test");
        ExperimentDAO xpDAO= new ExperimentDAO(m);
        xpDAO.store(xp);
        StructureObject r = new StructureObject("test", 0, new BlankMask("", 1, 2, 3, 0, 0, 0, 1, 1), xp);
        StructureObject r2 = new StructureObject("test", 1, new BlankMask("", 1, 2, 3, 0, 0, 0, 1, 1), xp);
        StructureObject r3 = new StructureObject("test", 2, new BlankMask("", 1, 2, 3, 0, 0, 0, 1, 1), xp);
        ObjectDAO dao = new ObjectDAO(m, xpDAO);
        r2.setPreviousInTrack(r, true, false);
        r3.setPreviousInTrack(r2, true, false);
        dao.store(true, r, r2, r3);
        MorphiumUtils.waitForWrites(m);
        MorphiumUtils.addDereferencingListeners(m, dao, xpDAO);
        r2 = dao.getObject(r2.getId());
        r = dao.getObject(r.getId());
        assertTrue("r2 retrieved", r!=null);
        assertEquals("r unique instanciation", r, r2.getPrevious());
        assertEquals("xp unique instanciation", r.getExperiment(), r2.getExperiment());
        dao.clearCache();
        r2 = dao.getObject(r2.getId());
        assertTrue("r2 retrieved", r!=null);
        assertEquals("r retrieved 2", "test", r2.getFieldName());
        //assertEquals("r previous ", r.getId(), r2.getPrevious().getId()); // not lazy anymore
        
        assertEquals("r unique instanciation query from fieldName & time point", r2, dao.getRoot("test", 1));
    }
    
    @Test
    public void StructureObjectTest() {

        // set-up experiment structure
        Experiment xp = new Experiment("test");
        ChannelImage image = new ChannelImage("ChannelImage");
        xp.getChannelImages().insert(image);
        xp.getStructures().removeAllElements();
        Structure microChannel = new Structure("MicroChannel", -1, 0);
        Structure bacteries = new Structure("Bacteries", 0, 0);
        bacteries.setParentStructure(0);
        xp.getStructures().insert(microChannel, bacteries);


        // set-up processing chain
        PluginFactory.findPlugins("testPlugins.dummyPlugins");

        microChannel.getProcessingChain().setSegmenter(new DummySegmenter(true, 2));
        bacteries.getProcessingChain().setSegmenter(new DummySegmenter(false, 3));
        assertTrue("segmenter set", microChannel.getProcessingChain().getSegmenter() instanceof DummySegmenter);
        assertEquals("segmenter set (2)", 2, ((NumberParameter)microChannel.getProcessingChain().getSegmenter().getParameters()[0]).getValue().intValue());
        // set-up traking
        PluginFactory.findPlugins("plugins.plugins.trackers");
        microChannel.setTracker(new ObjectIdxTracker());
        bacteries.setTracker(new ObjectIdxTracker());

        // set up fields
        ImageByte[][] images = createDummyImagesTC(50, 50, 1, 3, 1);
        images[0][0].setPixel(12, 12, 0, 2);
        File folder = testFolder.newFolder("TestInputImagesStructureObject");
        ImageWriter.writeToFile(folder.getAbsolutePath(), "field1", ImageFormat.OMETIF, images);
        Processor.importFiles(new String[]{folder.getAbsolutePath()}, xp);
        File outputFolder = testFolder.newFolder("TestOutputImagesStructureObject");
        xp.setOutputImageDirectory(outputFolder.getAbsolutePath());
        xp.setOutputImageDirectory("/tmp");
        //save to morphium
        Morphium m=MorphiumUtils.createMorphium("testdb");
        m.clearCollection(Experiment.class);
        m.clearCollection(StructureObject.class);
        ExperimentDAO xpDAO= new ExperimentDAO(m);
        xpDAO.store(xp);
        ObjectDAO dao = new ObjectDAO(m, xpDAO);

        Processor.preProcessImages(xp, dao, true);
        ArrayList<StructureObject> root = xp.getMicroscopyField(0).createRootObjects();
        assertEquals("root object creation: number of objects", 3, root.size());
        Processor.trackRoot(root);
        ArrayList<StructureObject> segO = new ArrayList<StructureObject>();
        segO.addAll(root);
        for (int s : xp.getStructuresInHierarchicalOrderAsArray()) {
            for (int t = 0; t<root.size(); ++t) Processor.process(s, root.get(t), dao, false, segO); // process
            if (!root.isEmpty()) for (StructureObject o : StructureObjectUtils.getAllParentObjects(root.get(0), xp.getPathToRoot(s))) Processor.track(xp.getStructure(s).getTracker(), o, s, dao, null); // structure
        }
        dao.store(segO, true);
        //MorphiumUtils.waitForWrites(m);

        StructureObject rootFetch = dao.getObject(root.get(0).getId());
        assertEquals("root fetch @t=0", root.get(0).getId(), rootFetch.getId());
        // retrieve
        dao.clearCache();

        rootFetch = dao.getObject(root.get(0).getId());
        assertTrue("root fetch @t=0 not null", rootFetch!=null);
        assertEquals("root fetch @t=0 (2)", root.get(0).getId(), rootFetch.getId());

        root = dao.getTrack(dao.getObject(root.get(0).getId()));
        for (int t = 0; t<root.size(); ++t) {
            //root[t]=dao.getObject(root.get(t).getId());
            for (int s : xp.getStructuresInHierarchicalOrderAsArray()) {
                for (StructureObject parent : StructureObjectUtils.getAllParentObjects(root.get(t), xp.getPathToRoot(s))) parent.setChildObjects(dao.getObjects(parent.getId(), s), s);
            }
        }

        for (int t = 1; t<root.size(); ++t) {
            Utils.logger.trace("root track: {}->{} / expected: {} / actual: {}", t-1, t, root.get(t), root.get(t-1).getNext());
            assertEquals("root track:"+(t-1)+"->"+t, root.get(t), root.get(t-1).getNext());
            assertEquals("root track:"+(t)+"->"+(t-1), root.get(t-1), root.get(t).getPrevious());
        }
        StructureObject[][] microChannels = new StructureObject[root.size()][];
        assertEquals("number of track heads for microchannels", 2, dao.getTrackHeads(root.get(0), 0).size());
        for (int t = 0; t<root.size(); ++t) microChannels[t] = root.get(t).getChildObjects(0).toArray(new StructureObject[0]);
        for (int t = 0; t<root.size(); ++t) assertEquals("number of microchannels @t:"+t, 2, microChannels[t].length);
        for (int i = 0; i<microChannels[0].length; ++i) {
            for (int t = 1; t<root.size(); ++t) {
                assertEquals("mc:"+i+" trackHead:"+t, microChannels[0][i].getId(),  microChannels[t][i].getTrackHeadId());
                assertEquals("mc:"+i+" parenttrackHead:"+t, root.get(0).getId(),  microChannels[t][i].getParentTrackHeadId());
            }
        }
        for (int i = 0; i<microChannels[0].length; ++i) {
            assertEquals("number of track heads for bacteries @ mc:"+i, 3, dao.getTrackHeads(microChannels[0][i], 1).size());
            StructureObject[][] bactos = new StructureObject[root.size()][];
            for (int t = 0; t<root.size(); ++t) bactos[t] = microChannels[t][i].getChildObjects(1).toArray(new StructureObject[0]);
            for (int t = 0; t<root.size(); ++t) assertEquals("number of bacteries @t:"+t+" @mc:"+i, 3, bactos[t].length);
            for (int b = 0; b<bactos[0].length; ++b) {
                for (int t = 1; t<root.size(); ++t) {
                    assertEquals("mc:"+i+ " bact:"+b+" trackHead:"+t, bactos[0][i].getId(),  bactos[t][i].getTrackHeadId());
                    assertEquals("mc:"+i+ " bact:"+b+" parenttrackHead:"+t, microChannels[0][i].getId(),  bactos[t][i].getParentTrackHeadId());
                }
            }
        }
        // creation des images @t0: 
        //ImageByte maskMC = getMask(root[0], xp.getPathToRoot(0));
        //ImageByte maskBactos = getMask(root[0], xp.getPathToRoot(1));
        //return new ImageByte[]{maskMC, maskBactos};

    }
    /*public static void main(String[] args) throws IOException {
        ProcessingTest t = new ProcessingTest();
        t.testFolder.create();
        Image[] images = t.StructureObjectTest();
        for (Image i : images) showImageIJ(i);
    }*/
}
