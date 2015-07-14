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
import java.util.List;
import org.bson.types.ObjectId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

/**
 *
 * @author jollion
 */
public class DoublyLinkedListTest {

    //@Test
    public void testDoublyLinkedList2() {
        MongoClient mongo=new MongoClient();
        Morphia morphia = new Morphia();
        morphia.map(DoublyLinkedList2.class);
        Datastore ds = morphia.createDatastore(mongo, "my_database");
        ds.delete(ds.createQuery(DoublyLinkedList2.class));
        
        DoublyLinkedList2 l1 = new DoublyLinkedList2(1);
        DoublyLinkedList2 l2 = new DoublyLinkedList2(2);
        
        l2.setParent(l1);
        l1.setChild(l2);
        
        ds.save(l1);
        ds.save(l2);
        
        Query<DoublyLinkedList2> res = ds.find(DoublyLinkedList2.class).filter("id", 2);
        DoublyLinkedList2 l2Fetched = res.get();
        DoublyLinkedList2 l1Fetched = l2Fetched.getParent();
        DoublyLinkedList2 l2Fetched2 = l1Fetched.getChild();

        assertEquals("test fetch l2 from query", 2, l2Fetched.id);
        assertEquals("test fetch l1 from lazyloading", 1, l1Fetched.id);
        assertEquals("test fetch l2 from lazy loading", 2, l2Fetched2.id);
        assertTrue("test instanciation", l2Fetched.equals(l2Fetched2));
        
    }
    
    //@Test
    public void testDoublyLinkedList3() {
        MongoClient mongo=new MongoClient();
        Morphia morphia = new Morphia();
        morphia.map(DoublyLinkedList3.class);
        Datastore ds = morphia.createDatastore(mongo, "my_database");
        ds.delete(ds.createQuery(DoublyLinkedList3.class));
        
        DoublyLinkedList3 l1 = new DoublyLinkedList3(1);
        DoublyLinkedList3 l2 = new DoublyLinkedList3(2);
        DoublyLinkedList3 l3 = new DoublyLinkedList3(3);
        
        ds.save(l1);
        ds.save(l2);
        ds.save(l3);
        ds.update(ds.createQuery(DoublyLinkedList3.class).field("_id").equal(l2.getId()), ds.createUpdateOperations(DoublyLinkedList3.class).set("dummy", 2));
        ds.update(ds.createQuery(DoublyLinkedList3.class).field("_id").equal(l2.getId()), ds.createUpdateOperations(DoublyLinkedList3.class). set("parent", l1).set("child", l3));
        ds.update(ds.createQuery(DoublyLinkedList3.class).field("_id").equal(l1.getId()), ds.createUpdateOperations(DoublyLinkedList3.class).set("child", l2));
        ds.update(ds.createQuery(DoublyLinkedList3.class).field("_id").equal(l3.getId()), ds.createUpdateOperations(DoublyLinkedList3.class).set("parent", l2));
        
        Query<DoublyLinkedList3>  res= ds.createQuery(DoublyLinkedList3.class).field("value").equal(2);
        DoublyLinkedList3 l2Fetched = res.get();
        System.out.println(l2Fetched.toString());
        res= ds.createQuery(DoublyLinkedList3.class).field("value").equal(1);
        DoublyLinkedList3 l1Fetched = res.get();
        System.out.println(l1Fetched.toString());
        DoublyLinkedList3 l1Fetched2 = l2Fetched.getParent();
        System.out.println("l1 lazy: "+l1Fetched2.toString());
        DoublyLinkedList3 l3Fetched = l2Fetched.getChild();
        DoublyLinkedList3 l2Fetched2 = l1Fetched2.getChild();
        DoublyLinkedList3 l2Fetched3 = l3Fetched.getParent();
        
        assertEquals("test fetch l2 from query", 2, l2Fetched.getValue());
        assertEquals("test update", 2, l2Fetched.getDummy());
        assertEquals("test fetch l1 from lazy loading after update", 1, l1Fetched.getValue());
        assertEquals("test fetch l3 from lazy 2 after update", 3, l3Fetched.getValue());
        assertEquals("test fetch l2 from lazy 1 after update", 2, l2Fetched2.getValue());
        assertEquals("test fetch l2 from lazy 3 after update", 2, l2Fetched3.getValue());
    }
    
    @Test
    public void testDoublyLinkedList() {
        MongoClient mongo=new MongoClient();
        Morphia morphia = new Morphia();
        morphia.map(DoublyLinkedList.class);
        Datastore ds = morphia.createDatastore(mongo, "my_database");
        ds.delete(ds.createQuery(DoublyLinkedList.class));
        
        DoublyLinkedList l1 = new DoublyLinkedList(1);
        DoublyLinkedList l2 = new DoublyLinkedList(2);
        
        l2.setParent(l1);
        l1.setChild(l2);
        
        ds.save(l1);
        ds.save(l2);
        
        Query<DoublyLinkedList> res = ds.find(DoublyLinkedList.class).filter("value", 2);
        DoublyLinkedList l2Fetched = res.get();
        res = ds.find(DoublyLinkedList.class).filter("value", 1);
        DoublyLinkedList l1Fetched = res.get();
        DoublyLinkedList l1Fetched2 = l2Fetched.getParent();
        DoublyLinkedList l2Fetched2 = l1Fetched2.getChild();

        assertEquals("test fetch l2 from query", 2, l2Fetched.id);
        assertEquals("test fetch l1 from query", 1, l1Fetched.id);
        assertEquals("test fetch l1 from lazy loading", 1, l1Fetched2.value);
        assertEquals("test fetch l2 from lazy loading", 2, l2Fetched2.id);
        assertTrue("test instanciation", l2Fetched.equals(l2Fetched2));
        
    }
    
    
}
