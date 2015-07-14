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
public class DoublyLinkedList2 {
    @Id long id;
    @Reference(lazy=true, idOnly=true) private DoublyLinkedList2 parent;
    @Reference(lazy=true, idOnly=true) private DoublyLinkedList2 child;
    @Transient private boolean parentFetched=false;
    @Transient private boolean childFetched=false;
    
    public DoublyLinkedList2(int value) {
        this.id = value;
    }
    
    public DoublyLinkedList2 getParent() {
        if (parentFetched) return parent;
        else if (childFetched && child.isParentFetched()) {
            parent=child.getParent();
            parentFetched=true;
            return parent;
        } else {
            parentFetched=true;
            parent.setChild(this);
            return parent;
        }
    }
    
    public DoublyLinkedList2 getChild() {
        if (childFetched) return child;
        else if (parentFetched && parent.isChildFetched()) {
            child=parent.getChild();
            childFetched=true;
            return child;
        } else {
            childFetched=true;
            child.setParent(this);
            return child;
        }
    }

    public void setParent(DoublyLinkedList2 parent) {
        this.parent = parent;
        this.parentFetched=true;
    }

    public void setChild(DoublyLinkedList2 child) {
        this.child = child;
        this.childFetched=true;
    }

    public boolean isParentFetched() {
        return parentFetched;
    }

    public boolean isChildFetched() {
        return childFetched;
    }
    
    private DoublyLinkedList2(){}
}
