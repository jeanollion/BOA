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


import core.Core;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import plugins.Plugin;
import plugins.PluginFactory;

/**
 *
 * @author jollion
 */
public class Test_Plugin implements Plugin, PlugInFilter {
    
    
    public static void main(String[] args) {
		// set the plugins.dir property to make the plugin appear in the Plugins menu
		Class<?> clazz = Test_Plugin.class;
		String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
		String pluginsDir = url.substring(5, url.length() - clazz.getName().length() - 6);
		System.setProperty("plugins.dir", pluginsDir);
                System.out.println("Plugins dir: "+pluginsDir);
		// start ImageJ
		ImageJ IJI = new ImageJ();
                
		// open the Clown sample
		//ImagePlus image = IJ.openImage("http://imagej.net/images/clown.jpg");
		//image.show();

		// Load the plugin
		PluginFactory.findPluginsIJ();
                Plugin test = PluginFactory.getPlugin("Test Plugin");
                if(test!=null) Core.getLogger().info("plugin test instanciated!!");
                //IJI.dispose();
	}

    @Override
    public int setup(String arg, ImagePlus imp) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void run(ImageProcessor ip) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
