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

import plugin.dummyPlugins.DummySegmenter;
import core.Processor;
import dataStructure.configuration.ChannelImage;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.ExperimentDAO;
import dataStructure.configuration.Structure;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectPostProcessing;
import dataStructure.objects.StructureObjectRoot;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.RootObjectDAO;
import dataStructure.objects.StructureObjectAbstract;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import image.BlankMask;
import image.ImageByte;
import image.ImageFormat;
import image.ImageWriter;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;
import static junit.framework.Assert.assertEquals;
import org.junit.Test;
import plugins.PluginFactory;
import plugins.trackers.TrackerObjectIdx;

/**
 *
 * @author jollion
 */
public class StructureObjectTest {
    @Test
    public void StructureObjectTest() {
        try {
            // set-up experiment structure
            Experiment xp = new Experiment("test");
            ChannelImage image = xp.getChannelImages().createChildInstance();
            xp.getChannelImages().insert(image);
            xp.getStructures().removeAllElements();
            Structure microChannel = xp.getStructures().createChildInstance("MicroChannel");
            Structure bacteries = xp.getStructures().createChildInstance("Bacteries");
            xp.getStructures().insert(microChannel, bacteries);
            bacteries.setParentStructure(0);
            
            // set-up processing chain
            PluginFactory.findPlugins("plugin.dummyPlugins");
            microChannel.getProcessingChain().setSegmenter(new DummySegmenter(true, 2));
            bacteries.getProcessingChain().setSegmenter(new DummySegmenter(false, 3));
            
            // set-up traking
            PluginFactory.findPlugins("plugins.trackers");
            microChannel.setTracker(new TrackerObjectIdx());
            bacteries.setTracker(new TrackerObjectIdx());
            
            MorphiumConfig cfg = new MorphiumConfig();
            cfg.setDatabase("testdb");
            cfg.addHost("localhost", 27017);
            Morphium m=new Morphium(cfg);
            m.clearCollection(Experiment.class);
            m.clearCollection(StructureObjectRoot.class);
            m.clearCollection(StructureObject.class);
            m.store(xp);
            RootObjectDAO rootDAO = new RootObjectDAO(m);
            ObjectDAO objectDAO = new ObjectDAO(m);
            
            StructureObjectRoot[] root = new StructureObjectRoot[3];
            BlankMask mask = new BlankMask("rootMask", 50,50, 2);
            for (int t = 0; t<root.length; ++t) root[t] = new StructureObjectRoot("test", t, xp, mask, rootDAO, objectDAO);
            Processor.trackRoot(xp, root);
            rootDAO.store(root); // attention si passage aux lazy refs -> necesaire de faire en deux temps
            
            for (int s : xp.getStructuresInHierarchicalOrderAsArray()) {
                for (int t = 0; t<root.length; ++t) Processor.processStructure(s, root[t], xp, true); // process
                for (StructureObjectAbstract o : root[0].getAllParentObjects(xp.getPathToRoot(s))) Processor.track(xp, xp.getStructure(s).getTracker(), o, s, true); // structure
            }
            utils.Utils.waitForWrites(m);
            
            StructureObjectRoot rootFetch = rootDAO.getObject(root[0].getId());
            assertEquals("root fetch @t=0", root[0].getId(), rootFetch.getId());
            // retrieve
            m=new Morphium(cfg);
            rootDAO = new RootObjectDAO(m);
            objectDAO = new ObjectDAO(m);
            
            rootFetch = rootDAO.getObject(root[0].getId());
            assertEquals("root fetch @t=0 (2)", root[0].getId(), rootFetch.getId());
            
            //for (int t = 0; t<root.length; ++t) root[t]=rootDAO.getRoot("test", t, xp, null, objectDAO); //attention si pas getById -> pas la même instance. résolut dans le cas des lazyLoading
            for (int t = 0; t<root.length; ++t) {
                root[t]=rootDAO.getObject(root[t].getId());
                root[t].setUp(xp, rootDAO, objectDAO);
            }
            for (int t = 1; t<root.length; ++t) {
                assertEquals("root track:"+(t-1)+"->"+t, root[t], root[t-1].getNext());
                assertEquals("root track:"+(t)+"->"+(t-1), root[t-1], root[t].getPrevious());
            }
            StructureObject[][] microChannels = new StructureObject[root.length][];
            for (int t = 0; t<root.length; ++t) microChannels[t] = root[t].getChildObjects(0);
            for (int t = 0; t<root.length; ++t) assertEquals("number of microchannels @t:"+t, 2, microChannels[t].length);
            for (int i = 0; i<microChannels[0].length; ++i) {
                for (int t = 1; t<root.length; ++t) {
                    assertEquals("mc:"+i+" track:"+(t-1)+"->"+t, microChannels[t][i],  microChannels[t-1][i].getNext());
                    assertEquals("mc:"+i+" track:"+(t)+"->"+(t-1), microChannels[t-1][i], microChannels[t][i].getPrevious());
                }
            }
            for (int i = 0; i<microChannels.length; ++i) {
                StructureObject[][] bactos = new StructureObject[root.length][];
                for (int t = 0; t<root.length; ++t) bactos[t] = microChannels[t][i].getChildObjects(1);
                for (int t = 0; t<root.length; ++t) assertEquals("number of bacteries @t:"+t+" @mc:"+i, 3, bactos[t].length);
                for (int b = 0; b<bactos[0].length; ++b) {
                    for (int t = 1; t<root.length; ++t) {
                        assertEquals("mc: "+i+ " bact:"+b+" track:"+(t-1)+"->"+t, bactos[t][i],  bactos[t-1][i].getNext());
                        assertEquals("mc: "+i+ " bact:"+b+" track:"+(t)+"->"+(t-1), bactos[t-1][i], bactos[t][i].getPrevious());
                    }
                }
            }
            // creation des images @t0: 
            ImageByte maskMC = getMask(root[0], xp.getPathToRoot(0));
            ImageWriter.writeToFile(maskMC, "/tmp", "mask MC t0", ImageFormat.PNG);
            ImageByte maskBactos = getMask(root[0], xp.getPathToRoot(1));
            ImageWriter.writeToFile(maskBactos, "/tmp", "mask Bactos t0", ImageFormat.PNG);
            
        } catch (UnknownHostException ex) {
            Logger.getLogger(StructureObjectTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    private static ImageByte getMask(StructureObjectRoot root, int[] pathToRoot) {
        ImageByte mask = new ImageByte("mask", root.getMask());
        int startLabel = 1;
        for (StructureObject o : root.getAllObjects(pathToRoot)) mask.appendBinaryMasks(startLabel++, o.getMask());
        return mask;
    }
}
