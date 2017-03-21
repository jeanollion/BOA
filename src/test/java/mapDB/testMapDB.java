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
package mapDB;

import java.util.concurrent.ConcurrentMap;
import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jollion
 */
public class testMapDB {
    public static final Logger logger = LoggerFactory.getLogger(testMapDB.class);
    public static void main(String[] args) {
        testFileDB();
    }
    // 
    // concurrency
    // multiple indexes
    public static void testFileDB() {
        DB db = DBMaker.fileDB("/home/jollion/Documents/LJP/DataLJP/testFileDB/file.db").fileMmapEnable().make();
        BTreeMap<String, String> map = db.treeMap("collection", Serializer.STRING, Serializer.STRING).createOrOpen();
        map.put("something", "here");
        logger.info("read before commit: {}", map.get("something"));
        db.commit();
        logger.info("read after commit: {}", map.get("something"));
        db.close();
        db = DBMaker.fileDB("/home/jollion/Documents/LJP/DataLJP/testFileDB/file.db").fileMmapEnable().make();
        map = db.treeMap("collection", Serializer.STRING, Serializer.STRING).createOrOpen();
        logger.info("read: {}", map.get("something"));
    }

}
