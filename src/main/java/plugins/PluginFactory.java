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
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jollion
 */
public class PluginFactory {

    private final static TreeMap<String, Class> plugins = new TreeMap<String, Class>();
    private final static Logger logger = LoggerFactory.getLogger(PluginFactory.class);
    
    public static void findPlugins(String packageName) {
        try {
            for (Class c : getClasses(packageName)) {
                //Class<?> clazz = Class.forName(c);
                if (Plugin.class.isAssignableFrom(c) && !Modifier.isAbstract( c.getModifiers() )) { // ne check pas l'heritage indirect!!
                    plugins.put(c.getSimpleName(), c);
                    //logger.debug("plugin found: "+c.getCanonicalName()+ " simple name:"+c.getSimpleName());
                } //else logger.trace("class is not a plugin: "+c.getCanonicalName()+ " simple name:"+c.getSimpleName());
            }
        } catch (ClassNotFoundException ex) {
            logger.warn("find plugins", ex);
        } catch (IOException ex) {
            logger.warn("find plugins", ex);
        }            
    }
    
    // from : http://www.dzone.com/snippets/get-all-classes-within-package
    private static Class[] getClasses(String packageName) throws ClassNotFoundException, IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        assert classLoader != null;
        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = classLoader.getResources(path);
        List<File> dirs = new ArrayList<File>();
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            dirs.add(new File(resource.getFile()));
        }
        ArrayList<Class> classes = new ArrayList<Class>();
        for (File directory : dirs) {
            classes.addAll(findClasses(directory, packageName));
        }
        return classes.toArray(new Class[classes.size()]);
    }
    
    private static List<Class> findClasses(File directory, String packageName) throws ClassNotFoundException {
        List<Class> classes = new ArrayList<Class>();
        if (!directory.exists()) {
            return classes;
        }
        File[] files = directory.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                assert !file.getName().contains(".");
                classes.addAll(findClasses(file, packageName + "." + file.getName()));
            } else if (file.getName().endsWith(".class")) {
                classes.add(Class.forName(packageName + '.' + file.getName().substring(0, file.getName().length() - 6)));
            }
        }
        return classes;
    }

    public static void findPluginsIJ() { // a tester...
        try {
            Hashtable<String, String> table = ij.Menus.getCommands();
            ClassLoader loader = ij.IJ.getClassLoader();
            Enumeration ks = table.keys();
            while (ks.hasMoreElements()) {
                String command = (String) ks.nextElement();
                String className = table.get(command);
                testClassIJ(command, className, loader);
            }
            
            logger.info("number of plugins found: " + plugins.size());
        } catch (Exception ex) {
            logger.warn("find plugins IJ", ex);
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
            } catch (ClassNotFoundException ex) {
                logger.warn("test class IJ", ex);
            } catch (NoClassDefFoundError ex) {
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
        } catch (InstantiationException ex) {
            logger.warn("getPlugin", ex);
        } catch (IllegalAccessException ex) {
            logger.warn("test class IJ", ex);
        }
        return null;
    }

    public static <T extends Plugin> T getPlugin(Class<T> clazz, String className) {
        try {
            Class plugClass = plugins.get(className);
            if (plugClass==null) {
                logger.error("plugin :{} of class: {} not found", className, clazz);
                return null;
            }
            T instance = (T) plugClass.newInstance();
            return instance;
        } catch (InstantiationException ex) {
            logger.error("plugin :{} of class: {} could not be instanciated, missing null constructor?", className, clazz, ex);
        } catch (IllegalAccessException ex) {
            logger.error("plugin :{} of class: {} could not be instanciated", className, clazz, ex);
        }
        return null;
    }

    public static <T extends Plugin> ArrayList<String> getPluginNames(Class<T> clazz) {
        ArrayList<String> res = new ArrayList<String>();
        for (Entry<String, Class> e : plugins.entrySet()) {
            if (clazz.isAssignableFrom(e.getValue())) {
                res.add(e.getKey());
            }
        }
        return res;
    }
}
