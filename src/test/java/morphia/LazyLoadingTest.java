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
package morphia;

import com.mongodb.MongoClient;
import static junit.framework.Assert.assertEquals;
import org.junit.Test;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.query.Query;

/**
 *
 * @author jollion
 */
public class LazyLoadingTest {
    @Test
    public void testLazyLoading() {
        MongoClient mongo=new MongoClient();
        Morphia morphia = new Morphia();
        morphia.map(SimpleEntity.class);
        Datastore ds = morphia.createDatastore(mongo, "my_database");
        ds.delete(ds.createQuery(SimpleEntity.class));
        
        SimpleEntity s1 = new SimpleEntity(1);
        SimpleEntity s2 = new SimpleEntity(2);
        s1.ref=s2;
        
        ds.save(s2);
        ds.save(s1);
        
        SimpleEntity s1Fetched = ds.find(SimpleEntity.class).filter("value", 1).get();
        SimpleEntity s2Fetched = ds.find(SimpleEntity.class).filter("value", 2).get();
        assertEquals("s1 fetch", 1, s1Fetched.value);
        assertEquals("s2 fetch", 2, s2Fetched.value);
        
        SimpleEntity s2Lazy = s1Fetched.ref;
        assertEquals("s2 lazy", 2, s2Lazy.value);
    }
}
