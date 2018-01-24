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
package boa.plugins;

import boa.configuration.parameters.PluginParameter;
import org.junit.Assert;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import boa.plugins.Plugin;
import boa.plugins.PluginFactory;
import boa.plugins.Segmenter;
import boa.plugins.Thresholder;
import boa.dummy_plugins.DummySegmenter;

/**
 *
 * @author jollion
 */
public class PluginFactoryTest {
    
    @Test
    public void testInternalPlugin() {
        String pluginName="DummyThresholder";
        PluginFactory.findPlugins("boa.dummy_plugins");
        assertTrue("dummy thresholder found", PluginFactory.getPlugin(Thresholder.class, pluginName) instanceof Thresholder);
        Plugin pp = PluginFactory.getPlugin("DummySegmenter");
        if (pp==null) System.out.println("Dummy Segmenter not found ");
        else System.out.println("Dummy Segmenter search: "+pp.getClass());
        assertTrue("dummy segmenter found", PluginFactory.getPlugin(Segmenter.class, "DummySegmenter") instanceof Segmenter);
        PluginParameter<Thresholder> thresholder = new PluginParameter<Thresholder>("Tresholder", Thresholder.class, true);
        Assert.assertTrue("Internal plugin search:", thresholder.getPluginNames().contains(pluginName));
        thresholder.setPlugin(pluginName);
        Plugin p =  thresholder.instanciatePlugin();
        Assert.assertTrue("Internal plugin instanciation:", p instanceof Thresholder);
        
        
    }
}
