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
import dataStructure.objects.MorphiumMasterDAO;
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
import dataStructure.objects.BasicMasterDAO;
import dataStructure.objects.StructureObject;
import dataStructure.objects.MorphiumObjectDAO;
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
import java.util.List;
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
import plugins.plugins.processingScheme.SegmentThenTrack;
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
        
        Processor.importFiles(xp, true, folder.getAbsolutePath());
        assertEquals("number of fields detected", 6-1-1, xp.getMicrocopyFieldCount()); // 6 - 1 (unique title) - 1 (channel number)
        assertTrue("field non null", xp.getMicroscopyField(title)!=null);
        assertTrue("images non null", xp.getMicroscopyField(title).getInputImages()!=null);
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
        
        Processor.importFiles(xp, true, folder.getAbsolutePath());
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
        Processor.importFiles(xp, true, folder.getAbsolutePath());
        MicroscopyField f = xp.getMicroscopyField(0);
        assertEquals("number of fields detected", 1, xp.getMicrocopyFieldCount());
        
        //set-up pre-processing chains
        PluginFactory.findPlugins("plugins.plugins.transformations");
        SimpleTranslation t = new SimpleTranslation(1, 0, 0);
        f.getPreProcessingChain().addTransformation(0, null, t);
        SimpleTranslation t2 = new SimpleTranslation(0, 1, 0);
        f.getPreProcessingChain().addTransformation(0, null, t2);
        
        //pre-process
        BasicMasterDAO masterDAO = new BasicMasterDAO(xp);
        Processor.preProcessImages(masterDAO, true);
       
        // test 
        ImageDAO dao = xp.getImageDAO();
        Image image = dao.openPreProcessedImage(0, 0, "field1");
        assertTrue("Image saved in DAO", image!=null);
        SimpleTranslation tInv = new SimpleTranslation(-1, -1, 0);
        Image imageInv = tInv.applyTransformation(0, 0, image);
        Utils.assertImage(images[0][0], TypeConverter.toByte(imageInv, null), 0);
    }
    
    /*private static ImageByte getMask(StructureObject root, int[] pathToRoot) {
        ImageByte mask = new ImageByte("mask", root.getMask());
        int startLabel = 1;
        for (StructureObject o : StructureObjectUtils.getAllObjects(root, pathToRoot)) mask.appendBinaryMasks(startLabel++, o.getMask().addOffset(o.getRelativeBoundingBox(null)));
        return mask;
    }*/
    
    @Test
    public void StructureObjectTestStore() {
        MorphiumMasterDAO db = new MorphiumMasterDAO("testdb");
        db.reset();
        Experiment xp = new Experiment("test");
        db.getXpDAO().store(xp);
        String f = "test";
        StructureObject r = new StructureObject(0, new BlankMask("", 1, 2, 3, 0, 0, 0, 1, 1), db.getDao(f));
        StructureObject r2 = new StructureObject(1, new BlankMask("", 1, 2, 3, 0, 0, 0, 1, 1), db.getDao(f));
        StructureObject r3 = new StructureObject(2, new BlankMask("", 1, 2, 3, 0, 0, 0, 1, 1), db.getDao(f));
        r2.setPreviousInTrack(r, true);
        r3.setPreviousInTrack(r2, true);
        db.getDao(f).store(r, true);
        db.getDao(f).store(r2, true);
        db.getDao(f).store(r3, true);
        r2 = db.getDao(f).getById(r2.getId());
        r = db.getDao(f).getById(r.getId());
        assertTrue("r2 retrieved", r!=null);
        assertEquals("r unique instanciation", r, r2.getPrevious());
        assertEquals("xp unique instanciation", r.getExperiment(), r2.getExperiment());
        db.getDao(f).clearCache();
        r2 = db.getDao(f).getById(r2.getId());
        assertTrue("r2 retrieved", r2!=null);
        assertEquals("r retrieved 2", "test", r2.getFieldName());
        //assertEquals("r previous ", r.getId(), r2.getPrevious().getId()); // not lazy anymore
        
        assertEquals("r unique instanciation query from fieldName & time point", r2, db.getDao(f).getRoot(1));
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
        String fieldName = "field1";

        // set-up processing scheme
        PluginFactory.findPlugins("testPlugins.dummyPlugins");
        PluginFactory.findPlugins("plugins.plugins");
        microChannel.setProcessingScheme(new SegmentThenTrack(new DummySegmenter(true, 2), new ObjectIdxTracker()));
        bacteries.setProcessingScheme(new SegmentThenTrack(new DummySegmenter(true, 3), new ObjectIdxTracker()));
        Segmenter seg = ((SegmentThenTrack)microChannel.getProcessingScheme()).getSegmenter();
        assertTrue("segmenter set", seg instanceof DummySegmenter);
        assertEquals("segmenter set (2)", 2, ((NumberParameter)seg.getParameters()[0]).getValue().intValue());
        
        // set up fields
        ImageByte[][] images = createDummyImagesTC(50, 50, 1, 3, 1);
        images[0][0].setPixel(12, 12, 0, 2);
        File folder = testFolder.newFolder("TestInputImagesStructureObject");
        ImageWriter.writeToFile(folder.getAbsolutePath(), fieldName, ImageFormat.OMETIF, images);
        Processor.importFiles(xp, true, folder.getAbsolutePath());
        File outputFolder = testFolder.newFolder("TestOutputImagesStructureObject");
        xp.setOutputImageDirectory(outputFolder.getAbsolutePath());
        xp.setOutputImageDirectory("/tmp");
        //save to morphium
        MorphiumMasterDAO db = new MorphiumMasterDAO("testdb");
        db.reset();
        db.setExperiment(xp);
        MorphiumObjectDAO dao = db.getDao(fieldName);
        
        Processor.preProcessImages(db, true);
        List<StructureObject> rootTrack = xp.getMicroscopyField(0).createRootObjects(dao);
        assertEquals("root object creation: number of objects", 3, rootTrack.size());
        Processor.processAndTrackStructures(db, true);
        dao.clearCache();

        StructureObject rootFetch = dao.getRoot(0);
        assertTrue("root fetch", rootFetch!=null);
        rootTrack = dao.getTrack(rootFetch);
        for (int t = 0; t<rootTrack.size(); ++t) {
            //root[t]=dao.getById(root.get(t).getId());
            for (int sIdx : xp.getStructuresInHierarchicalOrderAsArray()) {
                for (StructureObject parent : StructureObjectUtils.getAllParentObjects(rootTrack.get(t), xp.getPathToRoot(sIdx), dao)) parent.setChildren(dao.getChildren(parent, sIdx), sIdx);
            }
        }

        for (int t = 1; t<rootTrack.size(); ++t) {
            Utils.logger.trace("root track: {}->{} / expected: {} / actual: {}", t-1, t, rootTrack.get(t), rootTrack.get(t-1).getNext());
            assertEquals("root track:"+(t-1)+"->"+t, rootTrack.get(t), rootTrack.get(t-1).getNext());
            assertEquals("root track:"+(t)+"->"+(t-1), rootTrack.get(t-1), rootTrack.get(t).getPrevious());
        }
        StructureObject[][] microChannels = new StructureObject[rootTrack.size()][];
        assertEquals("number of track heads for microchannels", 2, dao.getTrackHeads(rootTrack.get(0), 0).size());
        for (int t = 0; t<rootTrack.size(); ++t) microChannels[t] = rootTrack.get(t).getChildObjects(0).toArray(new StructureObject[0]);
        for (int t = 0; t<rootTrack.size(); ++t) assertEquals("number of microchannels @t:"+t, 2, microChannels[t].length);
        for (int i = 0; i<microChannels[0].length; ++i) {
            for (int t = 1; t<rootTrack.size(); ++t) {
                assertEquals("mc:"+i+" trackHead:"+t, microChannels[0][i].getId(),  microChannels[t][i].getTrackHeadId());
                assertEquals("mc:"+i+" parenttrackHead:"+t, rootTrack.get(0).getId(),  microChannels[t][i].getParentTrackHeadId());
            }
        }
        for (int i = 0; i<microChannels[0].length; ++i) {
            assertEquals("number of track heads for bacteries @ mc:"+i, 3, dao.getTrackHeads(microChannels[0][i], 1).size());
            StructureObject[][] bactos = new StructureObject[rootTrack.size()][];
            for (int t = 0; t<rootTrack.size(); ++t) bactos[t] = microChannels[t][i].getChildObjects(1).toArray(new StructureObject[0]);
            for (int t = 0; t<rootTrack.size(); ++t) assertEquals("number of bacteries @t:"+t+" @mc:"+i, 3, bactos[t].length);
            for (int b = 0; b<bactos[0].length; ++b) {
                for (int t = 1; t<rootTrack.size(); ++t) {
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
