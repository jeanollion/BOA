/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins;

import boa.ui.GUI;
import com.sun.javafx.util.Utils;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.Vector;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jollion
 */
public class PluginFactory {

    private final static TreeMap<String, Class> PLUGINS = new TreeMap<>();
    private final static Logger logger = LoggerFactory.getLogger(PluginFactory.class);
    private final static Map<String, String> OLD_NAMES_MAP_NEW = new HashMap<String, String>(){{put("MutationTrackerSpine", "NestedSpotTracker");put("MutationSegmenter", "SpotSegmenter");}};
    public static void findPlugins(String packageName) {
        findPlugins(packageName, false);
    }
    public static void findPlugins(String packageName, boolean includeDev) {
        logger.info("looking for plugins in package: {}", packageName);
        try {
            for (Class c : getClasses(packageName)) {
                //Class<?> clazz = Class.forName(c);
                if (Plugin.class.isAssignableFrom(c) && !Modifier.isAbstract( c.getModifiers()) && (includeDev || !DevPlugin.class.isAssignableFrom(c)) ) { // ne check pas l'heritage indirect!!
                    if (!PLUGINS.containsKey(c.getSimpleName())) PLUGINS.put(c.getSimpleName(), c);
                    else {
                        Class otherC = PLUGINS.get(c.getSimpleName());
                        if (!otherC.equals(c)) {
                            logger.warn("Duplicate class name: {} & {}", otherC.getName(), c.getName());
                            GUI.log("Duplicate class name: "+otherC.getName()+" & "+c.getName());
                        }
                    }
                    //logger.debug("plugin found: "+c.getCanonicalName()+ " simple name:"+c.getSimpleName());
                } //else logger.trace("class is not a plugin: "+c.getCanonicalName()+ " simple name:"+c.getSimpleName());
            }
        } catch (ClassNotFoundException ex) {
            logger.warn("find plugins", ex);
        } catch (IOException ex) {
            logger.warn("find plugins", ex);
        }
        logger.info("total plugins found #{}", PLUGINS.size());
    }
    private static Iterator list(ClassLoader CL) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        Class CL_class = CL.getClass();
        while (CL_class != java.lang.ClassLoader.class) {
            CL_class = CL_class.getSuperclass();
        }
        java.lang.reflect.Field ClassLoader_classes_field = CL_class
                .getDeclaredField("classes");
        ClassLoader_classes_field.setAccessible(true);
        Vector classes = (Vector) ClassLoader_classes_field.get(CL);
        return classes.iterator();
    }
    
    // from : http://www.dzone.com/snippets/get-all-classes-within-package
    private static List<Class> getClasses(String packageName) throws ClassNotFoundException, IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        assert classLoader != null;
        
        if (packageName==null) { //look in classes that are already loaded
            List<Class> classes = new ArrayList<Class>();
            while (classLoader != null) {
                System.out.println("ClassLoader: " + classLoader);
                try {
                    for (Iterator iter = list(classLoader); iter.hasNext();) {
                        classes.add((Class)iter.next());
                    }
                } catch (Exception ex) {
                    logger.error("error while loading plugins", ex);
                }
                classLoader = classLoader.getParent();
                //logger.info("loaded classes : {}", classes.size());
            }
            return classes;
        } else {
        
            String path = packageName.replace('.', '/');

            Enumeration<URL> resources = classLoader.getResources(path);

            List<File> dirs = new ArrayList<>();
            List<String> pathToJars = new ArrayList<>();
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                String p = resource.getPath();
                if (p.contains("!")) {
                    p = p.substring(p.indexOf("file:")+5, p.indexOf("!"));
                    if (Utils.isUnix()) p = p.replaceAll("%20", " ");// space char replacement ... TODO test that this is valid for all unix system + test on windows
                    pathToJars.add(p);
                }
                else dirs.add(new File(resource.getFile()));
            }
            List<Class> classes = new ArrayList<>();
            for (File directory : dirs) findClasses(directory, packageName, classes);
            for (String pathToJar : pathToJars) findClassesFromJar(pathToJar, classes);
            //logger.info("looking for plugin in package: {}, path: {}, #dirs: {}, dir0: {}, #classes: {}", packageName, path, dirs.size(), !dirs.isEmpty()?dirs.get(0).getAbsolutePath():"", classes.size());
            return classes;
        }
    }
    
    private static void findClasses(File directory, String packageName, List<Class> classes) throws ClassNotFoundException {
        if (!directory.exists()) return;
        File[] files = directory.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                assert !file.getName().contains(".");
                findClasses(file, packageName + "." + file.getName(), classes);
            } else if (file.getName().endsWith(".class")) {
                //logger.debug("class: {}, from package: {}", file, packageName);
                Class c = null;
                try {
                   c = Class.forName(packageName + '.' + file.getName().substring(0, file.getName().length() - 6));
                } catch(Error e) { }
                if (c!=null) classes.add(c);
            }
        }
    }
    
    private static void findClassesFromJar(String pathToJar, List<Class> list) {
        try {
            //logger.info("loading classed from jar: {}", pathToJar);
            JarFile jarFile = new JarFile(pathToJar);
            Enumeration<JarEntry> e = jarFile.entries();
            URL[] urls = { new URL("jar:file:" + pathToJar+"!/") };
            URLClassLoader cl = URLClassLoader.newInstance(urls);
            while (e.hasMoreElements()) {
                try {
                    JarEntry je = e.nextElement();
                    if(je.isDirectory() || !je.getName().endsWith(".class")){
                        continue;
                    }
                    // -6 because of .class
                    String className = je.getName().substring(0,je.getName().length()-6);
                    className = className.replace('/', '.');
                    Class c = cl.loadClass(className);
                    list.add(c);
                } catch (ClassNotFoundException ex) {
                    logger.error("Error while loading classes from jar", ex);
                }

            }
        } catch (IOException ex) {
            logger.error("Error while loading classes from jar", ex);
        }
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
            
            logger.info("number of plugins found: " + PLUGINS.size());
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
                        PLUGINS.put(simpleName, c);
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
            if (PLUGINS.containsKey(s)) {
                res = PLUGINS.get(s).newInstance();
            } else if (OLD_NAMES_MAP_NEW.containsKey(s)) return getPlugin(OLD_NAMES_MAP_NEW.get(s));
            
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
    public static <T extends Plugin> Class<T> getPluginClass(Class<T> clazz, String className) {
        Class plugClass = PLUGINS.get(className);
        if (plugClass==null && OLD_NAMES_MAP_NEW.containsKey(className)) plugClass = PLUGINS.get(OLD_NAMES_MAP_NEW.get(className));
        return plugClass;
    }
    public static <T extends Plugin> T getPlugin(Class<T> clazz, String className) {
        try {
            Class plugClass = PLUGINS.get(className);
            if (plugClass==null && OLD_NAMES_MAP_NEW.containsKey(className)) plugClass = PLUGINS.get(OLD_NAMES_MAP_NEW.get(className));
            if (plugClass==null) {
                logger.error("plugin: {} of class: {} not found", className, clazz);
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
        for (Entry<String, Class> e : PLUGINS.entrySet()) {
            if (clazz.isAssignableFrom(e.getValue())) {
                res.add(e.getKey());
            }
        }
        return res;
    }
    
    public static boolean checkClass(String clazz) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            classLoader.loadClass(clazz);
        } catch (ClassNotFoundException ex) {
            return false;
        }
        return true;
    }
}
