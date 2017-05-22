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
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import dataStructure.objects.MasterDAO;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import utils.MorphiumUtils;

/**
 *
 * @author jollion
 */
public class DBUtil {
    public static void dropMongoDatabase(String dbName, String hostName) {
        MongoClient mongoClient = new MongoClient(hostName, 27017);
        mongoClient.dropDatabase(dbName);
        
    }
    public static void dropLocaldatabase(String dbName, String dir) {
        
    }
    public static List<String> listCollections(String dbName, String hostName) {
        MongoClient mongoClient = new MongoClient(hostName, 27017);
        MongoDatabase db = mongoClient.getDatabase(dbName);
        List<String> res = new ArrayList<String>();
        for (String s : db.listCollectionNames()) if (!s.equals("system.indexes")) res.add(s);
        return res;
    }
    public static List<String> getDBNames(String hostName, String filterPrefix) {
        try {
            long t0 = System.currentTimeMillis();
            MongoClientOptions.Builder optionsBuilder = new MongoClientOptions.Builder(); // TODO Timeout not taken into acount
            optionsBuilder.connectTimeout(5000);
            optionsBuilder.socketTimeout(5000);
            optionsBuilder.maxConnectionIdleTime(5000);
            optionsBuilder.maxConnectionLifeTime(5000);
            optionsBuilder.maxWaitTime(5000);
            
            MongoClient c = new MongoClient(hostName, optionsBuilder.build());
            
            MongoIterable<String> dbs = c.listDatabaseNames();
            ArrayList<String> res = new ArrayList<String>();
            for (String s : dbs) res.add(s);
            Collections.sort(res);
            long t1 = System.currentTimeMillis();
            GUI.logger.info("{} db names retrieved in: {}ms", res.size(), t1 - t0);
            //if (filterPrefix!=null) filter(res, filterPrefix);
            return res;
        } catch (Exception e) {
            logger.error("DB connection error: check hostname or DB server status", e);
        }
        return null;
    }
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
    public static Map<String, File> listExperiments(String path) {
        File f = new File(path);
        Map<String, File> configs = new HashMap<>();
        if (f.exists() && f.isDirectory()) {
            //addConfig(f, configs);
            File[] sub = f.listFiles(subF -> subF.isDirectory());
            for (File subF : sub) addConfig(subF, configs);
        }
        return configs;
    }
    public static void addConfig(File f, Map<String, File> configs) {
        File[] dbs = f.listFiles(subF -> subF.getName().endsWith("_config.db"));
        if (dbs==null) return;
        for (File c : dbs) configs.put(removeConfig(c.getName()), c);
    }
    private static String removeConfig(String name) {
        return name.substring(0, name.length()-10);
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
