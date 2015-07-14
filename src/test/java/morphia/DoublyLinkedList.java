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
public class DoublyLinkedList {
    @Id int id;
    @Reference(lazy=true, idOnly=true) private DoublyLinkedList parent;
    @Reference(lazy=true, idOnly=true) private DoublyLinkedList child;
    Object[] test = new Object[]{12.5, new int[]{1,1,1}, "string"};
    public void setParent(DoublyLinkedList parent) {
        this.parent = parent;
    }

    public void setChild(DoublyLinkedList child) {
        this.child = child;
    }
    int value;
    public DoublyLinkedList(int value) {
        this.id = value;
        this.value=value;
    }

    public DoublyLinkedList getParent() {
        return parent;
    }

    public DoublyLinkedList getChild() {
        return child;
    }
    
    private DoublyLinkedList(){};
}
