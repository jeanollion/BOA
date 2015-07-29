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
import dataStructure.configuration.Experiment;
import dataStructure.configuration.Structure;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectPostProcessing;
import dataStructure.objects.StructureObjectRoot;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.RootObjectDAO;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import image.BlankMask;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;
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
            m.clearCollection(StructureObjectRoot.class);
            m.clearCollection(StructureObject.class);
            RootObjectDAO rootDAO = new RootObjectDAO(m);
            ObjectDAO objectDAO = new ObjectDAO(m);
            
            StructureObjectRoot[] root = new StructureObjectRoot[3];
            for (int t = 0; t<root.length; ++t) root[t] = new StructureObjectRoot(t, xp, new BlankMask("rootMask", 50,50, 2), null);
            Processor.trackRoot(xp, root);
            rootDAO.store(root);
            
            for (int s : xp.getStructuresInHierarchicalOrderAsArray()) {
                for (int t = 0; t<root.length; ++t) Processor.processStructure(s, root[t], xp); // process
                for (StructureObjectPostProcessing o : root[0].getAllParentObjects(xp.getPathToRoot(s))) Processor.track(xp, xp.getStructure(s).getTracker(), o, s); // structure
                save la generation (ou petit a petit au cours du processing + update des tracks ?)
            }
        } catch (UnknownHostException ex) {
            Logger.getLogger(StructureObjectTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
