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

import com.mongodb.DBObject;
import dataStructure.configuration.Experiment;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.MorphiumMasterDAO;
import dataStructure.objects.StructureObject;
import de.caluga.morphium.AnnotationAndReflectionHelper;
import de.caluga.morphium.ObjectMapperImpl;
import de.caluga.morphium.writer.MorphiumWriterImpl;
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
import static utils.MorphiumUtils.createOfflineMorphium;
import static utils.MorphiumUtils.marshall;
import static utils.MorphiumUtils.unmarshall;
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
    
    @Test
    public void testStoreJSON() {
        String path = testFolder.newFolder("testDB").getAbsolutePath()+File.separator+"_testDB.db";
        String dbName = "boa_fluo170120_wt";
        MorphiumMasterDAO mdb = new MorphiumMasterDAO(dbName);
        String f = mdb.getExperiment().getPosition(1).getName();
        
        //ObjectMapperImpl mapper = new ObjectMapperImpl();
        //mapper.setMorphium(createOfflineMorphium()); // to be able to marshall maps
        AnnotationAndReflectionHelper annotationHelper = new AnnotationAndReflectionHelper(true);
        DBObject xpMash = marshall(mdb.getExperiment());
        StructureObject o  = mdb.getDao(f).getRoot(0).getChildren(1).get(0);
        logger.debug("object: {}, entity: {}", o, annotationHelper.isEntity(o));
        DBObject oMarsh = marshall(o);
        logger.debug("object: marsh {}", oMarsh); 
        DB db = DBMapUtils.createFileDB(path);
        HTreeMap<String, String> map = DBMapUtils.createHTreeMap(db, "xp");
        
        map.put(mdb.getExperiment().getName(), com.mongodb.util.JSON.serialize(xpMash));
        map.put("object", com.mongodb.util.JSON.serialize(oMarsh));
        db.commit();
        db.close();
        db = DBMapUtils.createFileDB(path);
        map = DBMapUtils.createHTreeMap(db, "xp");
        String xpString = map.get(mdb.getExperiment().getName());
        DBObject dboXP = (DBObject)com.mongodb.util.JSON.parse(xpString);
        DBObject dboOb = (DBObject)com.mongodb.util.JSON.parse(map.get("object"));
        Experiment xp = unmarshall(Experiment.class, dboXP);
        xp.postLoad();
        logger.info("xp create: {}, positions: {}", xp.getName(), xp.getPositionsAsString().length); 
        StructureObject o2 = unmarshall(StructureObject.class, dboOb);
        o2.postLoad();
        o2.setParent(o.getParent()); // for toString
        assertEquals("object id", o.getId(), o2.getId());
        assertArrayEquals("object center", o.getObject().getCenter(), o2.getObject().getCenter(), 0.0001);
        assertEquals("object voxels size", o.getObject().getVoxels().size(), o2.getObject().getVoxels().size());
        assertArrayEquals("object voxels", o.getObject().getVoxels().toArray(), o2.getObject().getVoxels().toArray());
        db.close();
    }
}
