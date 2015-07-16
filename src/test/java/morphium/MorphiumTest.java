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
package morphium;


import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Id;
import de.caluga.morphium.annotations.Reference;
import de.caluga.morphium.query.Query;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;
import static junit.framework.Assert.assertEquals;
import org.bson.types.ObjectId;
import org.junit.Test;

/**
 *
 * @author jollion
 */
public class MorphiumTest {
    @Test
    public void testMorphium() {
        try {
            MorphiumConfig cfg = new MorphiumConfig();
            cfg.setDatabase("testdb");
            cfg.addHost("localhost", 27017);
            Morphium m=new Morphium(cfg);
            m.clearCollection(SimpleEntity.class);
            
            SimpleEntity s1 = new SimpleEntity(1);
            SimpleEntity s2 = new SimpleEntity(2);
            SimpleEntity s3 = new SimpleEntity(3);
            
            m.store(s1);
            s2.ref=s1;
            s2.lazyRef=s3;
            m.store(s2);
            
            SimpleEntity s1Fetched = m.createQueryFor(SimpleEntity.class).f("value").eq(1).get();
            assertEquals("fetched s1:", 1, s1Fetched.value); 
            SimpleEntity s2Fetched = m.createQueryFor(SimpleEntity.class).f("value").eq(2).get();
            assertEquals("fetched s2:", 2, s2Fetched.value); 
            SimpleEntity s3Fetched = m.createQueryFor(SimpleEntity.class).f("value").eq(3).get();
            assertEquals("fetched s3:", 3, s3Fetched.value); 
            
            assertEquals("ref s1:", 1, s2Fetched.value); 
            assertEquals("lazy s3:", 3, s2Fetched.lazyRef.value); 
            
        } catch (UnknownHostException ex) {
            Logger.getLogger(MorphiumTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
