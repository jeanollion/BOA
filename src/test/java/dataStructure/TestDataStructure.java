/*
 * Copyright (C) 2017 jollion
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
import configuration.parameters.NumberParameter;
import core.Processor;
import static dataStructure.ProcessingTest.createDummyImagesTC;
import dataStructure.configuration.ChannelImage;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.Structure;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.MasterDAOFactory;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import static dataStructure.objects.StructureObjectUtils.setTrackLinks;
import image.BlankMask;
import image.ImageByte;
import image.ImageFormat;
import image.ImageWriter;
import java.io.File;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import plugins.PluginFactory;
import plugins.Segmenter;
import plugins.plugins.processingScheme.SegmentThenTrack;
import plugins.plugins.trackers.ObjectIdxTracker;
import testPlugins.dummyPlugins.DummySegmenter;
import static utils.Utils.toStringArray;

/**
 *
 * @author jollion
 */
public class TestDataStructure {
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();
    
    static MasterDAOFactory.DAOType daoType = MasterDAOFactory.DAOType.DBMap;
    
    @Test
    public void StructureObjectTestStore() {
        MasterDAO db = MasterDAOFactory.createDAO("testdb", testFolder.newFolder("testDB").getAbsolutePath(), daoType);
        db.reset();
        Experiment xp = new Experiment("test");
        xp.setOutputDirectory(testFolder.newFolder("testDB").getAbsolutePath());
        db.setExperiment(xp);
        String f = "test";
        StructureObject r = new StructureObject(0, new BlankMask("", 1, 2, 3, 0, 0, 0, 1, 1), db.getDao(f));
        StructureObject r2 = new StructureObject(1, new BlankMask("", 1, 2, 3, 0, 0, 0, 1, 1), db.getDao(f));
        StructureObject r3 = new StructureObject(2, new BlankMask("", 1, 2, 3, 0, 0, 0, 1, 1), db.getDao(f));
        setTrackLinks(r, r2, true, true);
        setTrackLinks(r2, r3, true, true);
        //r2.setPreviousInTrack(r, true);
        //r3.setPreviousInTrack(r2, true);
        db.getDao(f).store(r, true);
        db.getDao(f).store(r2, true);
        db.getDao(f).store(r3, true);
        r2 = db.getDao(f).getById(null, -1, r2.getFrame(), r2.getId());
        r = db.getDao(f).getById(null, -1, r.getFrame(), r.getId());
        assertTrue("r2 retrieved", r!=null);
        assertEquals("r unique instanciation", r, r2.getPrevious());
        assertEquals("xp unique instanciation", r.getExperiment(), r2.getExperiment());
        db.getDao(f).clearCache();
        r2 = db.getDao(f).getById(null, -1, r2.getFrame(), r2.getId());
        assertTrue("r2 retrieved", r2!=null);
        assertEquals("r retrieved 2", "test", r2.getPositionName());
        //assertEquals("r previous ", r.getId(), r2.getPrevious().getId()); // not lazy anymore
        
        assertEquals("r unique instanciation query from fieldName & time point", r2, db.getDao(f).getRoot(1));
    }

    @Test
    public void StructureObjectTest() {
        MasterDAO db = MasterDAOFactory.createDAO("testdb", testFolder.newFolder("testDB").getAbsolutePath(), daoType);
        
        // set-up experiment structure
        Experiment xp = new Experiment("test");
        xp.setOutputDirectory(testFolder.newFolder("testDB").getAbsolutePath());
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
        xp.setOutputDirectory(outputFolder.getAbsolutePath());
        xp.setOutputDirectory("/tmp");
        //save to morphium
        
        db.reset();
        db.setExperiment(xp);
        ObjectDAO dao = db.getDao(fieldName);
        
        Processor.preProcessImages(db, true);
        List<StructureObject> rootTrack = xp.getPosition(0).createRootObjects(dao);
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
        for (StructureObject mcTh : dao.getTrackHeads(rootTrack.get(0), 0)) {
            Utils.logger.debug("mc TH: {}  parent: {}", mcTh, mcTh.getParent());
        }
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
            for (int t = 0; t<rootTrack.size(); ++t) {
                bactos[t] = microChannels[t][i].getChildObjects(1).toArray(new StructureObject[0]);
                Utils.logger.debug("parent: {}, children: {}, trackHead: {}", microChannels[t][i], toStringArray(bactos[t], o->o.toString()+"/"+o.getId().toHexString()), toStringArray(bactos[t], o->o.getTrackHead().toString()+"/"+o.getTrackHeadId().toHexString()));
            }
            for (int t = 0; t<rootTrack.size(); ++t) assertEquals("number of bacteries @t:"+t+" @mc:"+i, 3, bactos[t].length);
            for (int b = 0; b<bactos[0].length; ++b) {
                for (int t = 1; t<rootTrack.size(); ++t) {
                    assertEquals("mc:"+i+ " bact:"+b+" trackHead:"+t, bactos[0][i].getId(),  bactos[t][i].getTrackHeadId());
                    assertEquals("mc:"+i+ " bact:"+b+" parenttrackHead:"+t, microChannels[0][i].getId(),  bactos[t][i].getParentTrackHeadId());
                }
            }
        }
    }
}
