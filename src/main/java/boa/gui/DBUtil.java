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
package boa.gui;

import static boa.gui.GUI.logger;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import dataStructure.objects.MasterDAO;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class DBUtil {
    
    
    
    public static void filter(List<String> list, String prefix) {
        Iterator<String> it = list.iterator();
        while(it.hasNext()) {
            if (!it.next().startsWith(prefix)) it.remove();
        }
    }
    public static String removePrefix(String name, String prefix) {
        while (name.startsWith(prefix)) name= name.substring(prefix.length(), name.length());
        return name;
    }
    public static String addPrefix(String name, String prefix) {
        if (name==null) return null;
        if (!name.startsWith(prefix)) name= prefix+name;
        return name;
    }
    public static String searchForLocalDir(String dbName) {
        String defPath = PropertyUtils.get(PropertyUtils.LOCAL_DATA_PATH);
        String d = null;
        if (defPath!=null) d = searchLocalDirForDB(dbName, defPath);
        //logger.debug("searching db: {} in path: {}, res: {}", dbName, defPath, d );
        if (d==null) {
            for (String path : PropertyUtils.getStrings(PropertyUtils.LOCAL_DATA_PATH)) {
                if (path.equals(defPath)) continue;
                d = searchLocalDirForDB(dbName, path);
                //logger.debug("searching db: {} in path: {}, res: {}", dbName, path, d );
                if (d!=null) return d;
            }
        }
        return d;
    }
    public static String searchLocalDirForDB(String dbName, String dir) {
        File config = Utils.seach(dir, dbName+"_config.db", 2);
        if (config==null) config = Utils.seach(dir, dbName+"_config.txt", 2); //TODO retrop compatibilité
        if (config!=null) return config.getParent();
        else {
            config = Utils.seach(new File(dir).getParent(), dbName+"_config.db", 2);
            if (config==null) config = Utils.seach(new File(dir).getParent(), dbName+"_config.txt", 2); //TODO retrop compatibilité
            if (config!=null) return config.getParent();
            else return null;
        }
    }
    public static Map<String, File> listExperiments(String path) {
        File f = new File(path);
        Map<String, File> configs = new HashMap<>();
        if (f.exists() && f.isDirectory()) { // only in directories included in path
            File[] sub = f.listFiles(subF -> subF.isDirectory());
            for (File subF : sub) addConfig(subF, configs);
        }
        return configs;
    }
    public static void addConfig(File f, Map<String, File> configs) {
        File[] dbs = f.listFiles(subF -> subF.getName().endsWith("_config.db") || subF.getName().endsWith("_config.txt")); // TODO retro-compatibility
        if (dbs==null) return;
        for (File c : dbs) configs.put(removeConfig(c.getName()), c);
    }
    private static String removeConfig(String name) {
        if (name.endsWith("db")) return name.substring(0, name.length()-10); // TODO retro-compatibility
        else return name.substring(0, name.length()-11);
    }
    static long minMem = 2000000000;
    public static void checkMemoryAndFlushIfNecessary(String... exceptPositions) {
        long freeMem= Runtime.getRuntime().freeMemory();
        long usedMem = Runtime.getRuntime().totalMemory();
        long totalMem = freeMem + usedMem;
        if (freeMem<minMem || usedMem>2*minMem) {
            
        }
    }
    public static void clearMemory(MasterDAO db, String... excludedPositions) {
        db.getSelectionDAO().clearCache();
        ImageWindowManagerFactory.getImageManager().flush();
        db.getExperiment().flushImages(true, true, excludedPositions);
        List<String> positions = new ArrayList<>(Arrays.asList(db.getExperiment().getPositionsAsString()));
        positions.removeAll(Arrays.asList(excludedPositions));
        for (String p : positions) db.getDao(p).clearCache();
    }
}
