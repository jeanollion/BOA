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
package morphiumTest;

import static TestUtils.Utils.logger;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import de.caluga.morphium.MorphiumSingleton;
import de.caluga.morphium.annotations.ReadPreferenceLevel;
import de.caluga.morphium.async.AsyncOperationCallback;
import de.caluga.morphium.async.AsyncOperationType;
import de.caluga.morphium.query.Query;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import utils.MorphiumUtils;

/**
 *
 * @author jollion
 */
public class SimpleEntityTest {
    static boolean duration0 = false;
    static int massInsertCount = 0;
    static boolean objectIdNull = false;
    static boolean listInsertFail=false;
    //@Test
    public void updateRefTest() {
        Morphium m = MorphiumUtils.createMorphium("testUpdateRef");
        m.clearCollection(SimpleEntity.class);
        SimpleEntity e1 = new SimpleEntity(1);
        SimpleEntity e2 = new SimpleEntity(2);
        m.store(e1);
        m.store(e2);
        e1.setRef(e2);
        logger.debug("ref: {}", e1.getRef().value);
        m.updateUsingFields(e1, "ref");
        e1.ref=null;
        SimpleEntity e1Fetched = m.createQueryFor(SimpleEntity.class).getById(e1.id);
        assertEquals("fetch e1: ", e1.id, e1Fetched.getRef().id);
        assertEquals("updated ref: ", e2.id, e1Fetched.getRef().id);
    }
    
    //@Test
    public void massInsertTest() {
        Morphium m = MorphiumUtils.createMorphium("testUpdateRef");
        m.clearCollection(SimpleEntity.class);
        ArrayList<SimpleEntity> list = new ArrayList<SimpleEntity>(500);
        for (int i = 0; i<500; ++i) list.add( new SimpleEntity(i));
        long t0 = System.currentTimeMillis();
        m.store(list, new AsyncOperationCallback<SimpleEntity>() {

            public void onOperationSucceeded(AsyncOperationType aot, Query<SimpleEntity> query, long duration, List<SimpleEntity> list, SimpleEntity t, Object... os) {
                if (duration==0) return;
                if (duration==0) duration0 = true;
                incrementMassCounter();
                for (SimpleEntity e : list) if (e.id==null) {
                    objectIdNull= true;
                    return;
                }
            }

            public void onOperationError(AsyncOperationType aot, Query<SimpleEntity> query, long l, String string, Throwable thrwbl, SimpleEntity t, Object... os) {
                listInsertFail = true;
            }
        });
        long t1= System.currentTimeMillis();
        MorphiumUtils.waitForWrites(m);
        long t2 = System.currentTimeMillis();
        m.clearCollection(SimpleEntity.class);
        list = new ArrayList<SimpleEntity>(500);
        for (int i = 0; i<500; ++i) list.add( new SimpleEntity(i));
        long t3 = System.currentTimeMillis();
        for (SimpleEntity o : list) m.store(o);
        long t4 = System.currentTimeMillis();
        
        logger.info("mass insert: {} wait for writes: {}, normal insert: {}", t1-t0, t2-t1, t4-t3);
        
        assertTrue("no id null", !objectIdNull);
        assertEquals("number of operations", 1, massInsertCount);
        assertEquals("duration 0", false, duration0);
    }
    
    //@Test
    public void listInsertTest() {
        int n = 10000;
        Morphium m = MorphiumUtils.createMorphium("testUpdateRef");
        m.clearCollection(SimpleEntity.class);
        ArrayList<SimpleEntity> list = new ArrayList<SimpleEntity>(n);
        
        for (int i = 0; i<n; ++i) list.add( new SimpleEntity(i));
        long tInitLoop = System.currentTimeMillis();
        for (SimpleEntity o : list) m.store(o);
        long tEndLoop = System.currentTimeMillis();
        m.clearCollection(SimpleEntity.class);
        list.clear();
        for (int i = 0; i<n; ++i) list.add( new SimpleEntity(i));
        long tInitMass = System.currentTimeMillis();
        m.storeList(list);
        long tEndMass= System.currentTimeMillis();
        
        // 3.0.6 n = 10.000 -> normal = 5.2 / list = 114s
        // 3.0.7 n = 10.000 -> normal = 6.1 / list = 117.7 
        logger.info("number of objects: {} list insert: {}, normal insert: {}", n, tEndMass-tInitMass, tEndLoop-tInitLoop);
        
    }
    
    public synchronized static void incrementMassCounter() {
        massInsertCount++;
    }
    //@Test
    public void morphiumMassInsertTest() {
        if (!MorphiumSingleton.isConfigured()) configure();
        Morphium morphium = MorphiumSingleton.get();
        logger.debug("max write batch size {}" , morphium.getMaxWriteBatchSize());
        int n = 999;
        morphium.dropCollection(UncachedObject.class);
        logger.info("Creating objects sequentially...");
        long start = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            UncachedObject uc = new UncachedObject();
            uc.setCounter(i);
            uc.setValue("V" + i);
            morphium.store(uc);
            if (i % 100 == 0) {
                logger.info("got " + i);
            }
        }
        long dur = System.currentTimeMillis() - start;
        logger.info("Took: " + dur + "ms");

        logger.info("Creating objects randomly..");
        start = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            UncachedObject uc = new UncachedObject();
            int c = (int) (Math.random() * 100000.0);
            uc.setCounter(c);
            uc.setValue("V" + c);
            morphium.store(uc);
            if (i % 100 == 0) {
                logger.info("got " + i);
            }

        }
        dur = System.currentTimeMillis() - start;
        logger.info("Took: " + dur + "ms");

        logger.info("Block writing...");
        List<UncachedObject> buffer = new ArrayList<UncachedObject>();
        start = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            UncachedObject uc = new UncachedObject();
            int c = (int) (Math.random() * 100000.0);
            uc.setCounter(c);
            uc.setValue("n" + c);
            buffer.add(uc);
        }
        morphium.storeList(buffer);
        dur = System.currentTimeMillis() - start;
        logger.info("Took: " + dur + "ms");
    }
    
    public static void configure() {
                
        try {
            MorphiumConfig cfg = new MorphiumConfig("morphium_test", 2055, 50000, 5000);
            cfg.addHost("localhost", 27017);
            cfg.setWriteCacheTimeout(100);
            cfg.setConnectionTimeout(10000);
            cfg.setMaxWaitTime(20000);
            cfg.setMaxAutoReconnectTime(500);
            cfg.setMaxConnectionLifeTime(60000);
            cfg.setMaxConnectionIdleTime(30000);
            cfg.setMaxConnections(2000);
            cfg.setMinConnectionsPerHost(1);
            cfg.setAutoreconnect(true);
            cfg.setMaximumRetriesBufferedWriter(1000);
            cfg.setMaximumRetriesWriter(1000);
            cfg.setMaximumRetriesAsyncWriter(1000);
            cfg.setRetryWaitTimeAsyncWriter(1000);
            cfg.setRetryWaitTimeWriter(1000);
            cfg.setRetryWaitTimeBufferedWriter(1000);
            cfg.setGlobalFsync(false);
            cfg.setGlobalJ(false);
            cfg.setGlobalW(1);
            cfg.setMaxAutoReconnectTime(5000);
            cfg.setDefaultReadPreference(ReadPreferenceLevel.NEAREST);
            cfg.setBlockingThreadsMultiplier(100);
            cfg.setGlobalLogLevel(3);
            MorphiumSingleton.setConfig(cfg);
            Morphium m = MorphiumSingleton.get();
            m.readMaximums();
            
        } catch (UnknownHostException ex) {
            Logger.getLogger(SimpleEntityTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
