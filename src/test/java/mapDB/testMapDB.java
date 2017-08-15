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

import core.Task;
import dataStructure.configuration.Experiment;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.StructureObject;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.DBMapUtils;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class testMapDB {
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();
    public static final Logger logger = LoggerFactory.getLogger(testMapDB.class);
    
    public static void acceptNullKey() {
        HashMap<String, String> test = new HashMap<>();
        test.put(null, "a");
        test.put("b", "c");
        logger.debug("null->{}, b: {}", test.get(null), test.get("b"));
    }
    // 
    // concurrency
    // multiple indexes
    //@Test
    public void testFileDB() {
        boolean close = true;
        String path = testFolder.newFolder("testDB").getAbsolutePath()+File.separator+"_testDB.db";
        DB db = DBMapUtils.createFileDB(path);
        HTreeMap<String, String> map = DBMapUtils.createHTreeMap(db, "collection");
        map.put("something", "here");
        logger.info("read before commit: {}", map.get("something"));
        db.commit();
        logger.info("read after commit: {}", map.get("something"));
        if (close) db.close();
        db = DBMapUtils.createFileDB(path);
        map = DBMapUtils.createHTreeMap(db, "collection");
        logger.info("read: {}", map.get("something"));
        db.close();
    }
    
}
