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

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import org.bson.types.ObjectId;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Reference;
import org.mongodb.morphia.query.Query;

/**
 *
 * @author jollion
 */
public class TestLazyMorphiaEx extends TestBase{
    @After
    public void tearDown() {
        turnOffProfilingAndDropProfileCollection();
        super.tearDown();
    }

    private void turnOffProfilingAndDropProfileCollection() {
        getDb().command(new BasicDBObject("profile", 0));
        DBCollection profileCollection = getDb().getCollection("system.profile");
        profileCollection.drop();
    }
    
    @Test
    public void testQueryOverLazyReference() throws Exception {
        final ContainsPic cpk = new ContainsPic();
        final Pic p = new Pic();
        p.setName("picTest");
        getDs().save(p);
        cpk.lazyPic = p;
        cpk.pic=p;
        getDs().save(cpk);
        final Query<ContainsPic> query = getDs().createQuery(ContainsPic.class);
        assertEquals(1, query.field("lazyPic").equal(p).asList().size());
        assertEquals("test ref pic: field=name" , "picTest", query.get().pic.name);
        assertEquals("test lazy loading pic: field=name" , "picTest", query.get().lazyPic.name);
    }
    @Entity
    public static class ContainsPic {

        @Id
        private ObjectId id;
        private String name = "test";
        @Reference
        private Pic pic;
        @Reference(lazy = true)
        private Pic lazyPic;

        public ObjectId getId() {
            return id;
        }

        public void setId(final ObjectId id) {
            this.id = id;
        }

        public Pic getLazyPic() {
            return lazyPic;
        }

        public void setLazyPic(final Pic lazyPic) {
            this.lazyPic = lazyPic;
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public Pic getPic() {
            return pic;
        }

        public void setPic(final Pic pic) {
            this.pic = pic;
        }
    }
    
    @Entity
    public static class Pic {

        @Id
        private ObjectId id;
        private String name;

        public Pic() {
        }

        public Pic(final String name) {
            this.name = name;
        }

        public ObjectId getId() {
            return id;
        }

        public void setId(final ObjectId id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }
    }
}
