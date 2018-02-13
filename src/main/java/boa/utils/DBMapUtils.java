/*
 * Copyright (C) 2017 jollion
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
package boa.utils;

import static boa.data_structure.dao.DBMapMasterDAO.logger;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.Set;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

/**
 *
 * @author jollion
 */
public class DBMapUtils {
    public static DB createFileDB(String path, boolean readOnly) {
        //logger.debug("creating file db: {}, is dir: {}, exists: {}", path, new File(path).isDirectory(),new File(path).exists());
        if (readOnly) return DBMaker.fileDB(path).transactionEnable().fileLockDisable().readOnly().closeOnJvmShutdown().make();
        return DBMaker.fileDB(path).transactionEnable().closeOnJvmShutdown().make(); //  https://jankotek.gitbooks.io/mapdb/performance/
        
    }
    public static HTreeMap<String, String> createHTreeMap(DB db, String key) {
        try {
            return db.hashMap(key, Serializer.STRING, Serializer.STRING).createOrOpen(); 
        } catch (UnsupportedOperationException e) { // read-only case
            return null;
        }
    }
    public static <K, V> Set<Entry<K, V>> getEntrySet(HTreeMap<K, V> map) {
        if (map==null) return Collections.EMPTY_SET; // read-only case
        return map.getEntries(); 
    }
    public static <V> Collection<V> getValues(HTreeMap<?, V> map) {
        if (map==null) return Collections.EMPTY_SET;
        return map.getValues(); 
    }
    public static Iterable<String> getNames(DB db) {
        return db.getAllNames(); 
    }
    public static void deleteDBFile(String path) {
        new File(path).delete();
    }
}
