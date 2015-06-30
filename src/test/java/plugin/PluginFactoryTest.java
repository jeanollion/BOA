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
package plugin;

import configuration.parameters.PluginParameter;
import org.junit.Assert;
import plugins.Plugin;
import plugins.PluginFactory;
import plugins.Thresholder;

/**
 *
 * @author jollion
 */
public class PluginFactoryTest {
    
    @org.junit.Test
    public void testInternalPlugin() {
        String pluginName="DummyThresholder";
        PluginFactory.findPlugins("plugin.dummyPlugins");
        PluginParameter thresholder = new PluginParameter("Tresholder", Thresholder.class);
        Assert.assertTrue("Internal plugin search:", thresholder.getPluginNames().contains(pluginName));
        thresholder.setPlugin(pluginName);
        Plugin p =  thresholder.getPlugin();
        Assert.assertTrue("Internal plugin instanciation:", p instanceof Thresholder);
    }
}
