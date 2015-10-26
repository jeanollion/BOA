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
import de.caluga.morphium.async.AsyncOperationCallback;
import de.caluga.morphium.async.AsyncOperationType;
import de.caluga.morphium.query.Query;
import java.util.ArrayList;
import java.util.List;
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
        Morphium m = MorphiumUtils.createMorphium("testUpdateRef");
        m.clearCollection(SimpleEntity.class);
        ArrayList<SimpleEntity> list = new ArrayList<SimpleEntity>(1000);
        
        for (int i = 0; i<1000; ++i) list.add( new SimpleEntity(i));
        long tInitLoop = System.currentTimeMillis();
        for (SimpleEntity o : list) m.store(o);
        long tEndLoop = System.currentTimeMillis();
        m.clearCollection(SimpleEntity.class);
        list.clear();
        for (int i = 0; i<1000; ++i) list.add( new SimpleEntity(i));
        long tInitMass = System.currentTimeMillis();
        m.storeList(list);
        long tEndMass= System.currentTimeMillis();
        
        
        logger.info("list insert: {}, normal insert: {}", tEndMass-tInitMass, tEndLoop-tInitLoop);
        
    }
    
    public synchronized static void incrementMassCounter() {
        massInsertCount++;
    }
}
