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

import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Reference;
import org.mongodb.morphia.annotations.Transient;

/**
 *
 * @author jollion
 */
@Entity
public class DoublyLinkedList3 {
    @Id private ObjectId id;
    @Reference(lazy=true, idOnly=true) private DoublyLinkedList3 parent;
    @Reference(lazy=true, idOnly=true) private DoublyLinkedList3 child;
    private int value;
    private int dummy=1;
    public DoublyLinkedList3(int value) {
        this.value = value;
    }

    public DoublyLinkedList3 getParent() {
        return parent;
    }

    public DoublyLinkedList3 getChild() {
        return child;
    }

    public void setParent(DoublyLinkedList3 parent) {
        this.parent = parent;
    }

    public void setChild(DoublyLinkedList3 child) {
        this.child = child;
    }
    
    public ObjectId getId() {
        return id;
    }
    
    @Override
    public String toString() {
        return id+" "+value+ " parent: "+parent.getId()+ " child: "+child.getId();
    }

    public int getValue() {
        return value;
    }

    public int getDummy() {
        return dummy;
    }
    
    private DoublyLinkedList3(){};
}
