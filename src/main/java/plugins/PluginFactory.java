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
package plugins;

import core.Core;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.TreeMap;
import java.util.logging.Level;

/**
 *
 * @author jollion
 */
public class PluginFactory {
    static TreeMap<String, Class> plugins;
    public static void findPluginsIJ() {
        try {
            plugins = new TreeMap<String, Class>();
            Hashtable<String, String> table = ij.Menus.getCommands();
            ClassLoader loader = ij.IJ.getClassLoader();
            Enumeration ks = table.keys();
            while (ks.hasMoreElements()) {
                String command = (String) ks.nextElement();
                String className = table.get(command);
                testClassIJ(command, className, loader);
            }
            Core.getLogger().info("number of plugins found: "+plugins.size());
        } catch (Exception e) {
            Core.getLogger().log(Level.CONFIG, e.getMessage(), e);
        }
    }
    private static void testClassIJ(String command, String className, ClassLoader loader) {
        if (!className.startsWith("ij.")) {
            if (className.endsWith("\")")) {
                int argStart = className.lastIndexOf("(\"");
                className = className.substring(0, argStart);
            }
            try {
                Class c = loader.loadClass(className);
                if (Plugin.class.isAssignableFrom(c)) {
                    //String simpleName = c.getSimpleName();
                    String simpleName = command;
                    if (Plugin.class.isAssignableFrom(c)) {
                        plugins.put(simpleName, c);
                    }
                }
            } catch (ClassNotFoundException e) {
                Core.getLogger().log(Level.CONFIG, e.getMessage(), e);
            } catch (NoClassDefFoundError e) {
                int dotIndex = className.indexOf('.');
                if (dotIndex >= 0) {
                    testClassIJ(command, className.substring(dotIndex + 1), loader);
                }
            }
        }
    }
    
    public static Plugin getPlugin(String s) {
        if (s == null) {
            return null;
        }
        try {
            Object res = null;
            if (plugins.containsKey(s)) {
                res = plugins.get(s).newInstance();
            }
            if (res != null && res instanceof Plugin) {
                return ((Plugin) res);
            }
        } catch (InstantiationException e) {
            Core.getLogger().log(Level.CONFIG, e.getMessage(), e);
        } catch (IllegalAccessException e) {
            Core.getLogger().log(Level.CONFIG, e.getMessage(), e);
        }
        return null;
    }
}
