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



/**
 *
 * @author jollion
 */

//@NoCache
//@Entity
//@PartialUpdate
public class DoublyLinkedList {
    //ObjectId id; //@Id 
    int value;
    private DoublyLinkedList parent; //@Reference(lazyLoading=true) 
    private DoublyLinkedList child; //@Reference(lazyLoading=true) 
    public enum Fields {id, value, parent, child};
    
    //@PartialUpdate("parent")
    public void setParent(DoublyLinkedList parent) {
        this.parent = parent;
    }
    
    //@PartialUpdate("child")
    public void setChild(DoublyLinkedList child) {
        this.child = child;
    }
    
    public DoublyLinkedList(int value) {
        this.value = value;
    }

    public DoublyLinkedList getParent() {
        return parent;
    }

    public DoublyLinkedList getChild() {
        return child;
    }
    
    public DoublyLinkedList(){};
}
