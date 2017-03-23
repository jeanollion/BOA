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
import java.util.HashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import org.apache.commons.lang.ArrayUtils;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static utils.MorphiumUtils.createOfflineMorphium;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class testMapDB {
    public static final Logger logger = LoggerFactory.getLogger(testMapDB.class);
    public static void main(String[] args) {
        //testFileDB();
        //testStoreJSON();
        acceptNullKey();
    }
    
    public static void acceptNullKey() {
        HashMap<String, String> test = new HashMap<>();
        test.put(null, "a");
        test.put("b", "c");
        logger.debug("null->{}, b: {}", test.get(null), test.get("b"));
    }
    // 
    // concurrency
    // multiple indexes
    public static void testFileDB(boolean close) {
        DB db = DBMaker.fileDB("/home/jollion/Documents/LJP/DataLJP/testFileDB/file.db").fileMmapEnable().make();
        BTreeMap<String, String> map = db.treeMap("collection", Serializer.STRING, Serializer.STRING).createOrOpen();
        map.put("something", "here");
        logger.info("read before commit: {}", map.get("something"));
        db.commit();
        logger.info("read after commit: {}", map.get("something"));
        if (close) db.close();
        db = DBMaker.fileDB("/home/jollion/Documents/LJP/DataLJP/testFileDB/file.db").fileMmapEnable().make();
        map = db.treeMap("collection", Serializer.STRING, Serializer.STRING).createOrOpen();
        logger.info("read: {}", map.get("something"));
    }
    public static void testStoreJSON() {
        String dbName = "boa_fluo170120_wt";
        MorphiumMasterDAO mdb = new MorphiumMasterDAO(dbName);
        String f = mdb.getExperiment().getPosition(1).getName();
        
        ObjectMapperImpl mapper = new ObjectMapperImpl();
        mapper.setMorphium(createOfflineMorphium()); // to be able to marshall maps
        AnnotationAndReflectionHelper annotationHelper = new AnnotationAndReflectionHelper(true);
        DBObject xpMash = mapper.marshall(mdb.getExperiment());
        StructureObject o  = mdb.getDao(f).getRoot(0).getChildren(2).get(0);
        logger.debug("object: {}, entity: {}", o, annotationHelper.isEntity(o));
        DBObject oMarsh = mapper.marshall(o);
        logger.debug("object: marsh {}", oMarsh); 
        DB db = DBMaker.fileDB("/home/jollion/Documents/LJP/DataLJP/testFileDB/file.db").fileMmapEnable().transactionEnable().make();
        BTreeMap<String, String> map = db.treeMap("xp", Serializer.STRING, Serializer.STRING).createOrOpen();
        
        map.put(mdb.getExperiment().getName(), com.mongodb.util.JSON.serialize(xpMash));
        map.put("object", com.mongodb.util.JSON.serialize(oMarsh));
        db.commit();
        db.close();
        db = DBMaker.fileDB("/home/jollion/Documents/LJP/DataLJP/testFileDB/file.db").fileMmapEnable().make();
        map = db.treeMap("xp", Serializer.STRING, Serializer.STRING).createOrOpen();
        String xpString = map.get(mdb.getExperiment().getName());
        DBObject dboXP = (DBObject)com.mongodb.util.JSON.parse(xpString);
        DBObject dboOb = (DBObject)com.mongodb.util.JSON.parse(map.get("object"));
        Experiment xp = mapper.unmarshall(Experiment.class, dboXP);
        xp.postLoad();
        logger.info("xp create: {}, positions: {}", xp.getName(), xp.getPositionsAsString().length); 
        StructureObject o2 = mapper.unmarshall(StructureObject.class, dboOb);
        o2.postLoad();
        o2.setParent(o.getParent()); // for toString
        logger.info("object create: {} ({}), id: {} ({}), equals: {}", o2, o, o2.getId(), o.getId(), o.equals(o2));
        logger.info("object center: {} ({})", ArrayUtils.toString(o2.getObject().getCenter()), ArrayUtils.toString(o.getObject().getCenter()));
        db.close();
    }
}
