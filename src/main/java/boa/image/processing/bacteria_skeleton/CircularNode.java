/*
 * Copyright (C) 2018 jollion
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
package boa.image.processing.bacteria_skeleton;

import java.util.List;
import java.util.Objects;

/**
 *
 * @author jollion
 */
public class CircularNode<T> {
    CircularNode<T> prev, next;
    T element;
    public CircularNode(T element) {
        this.element = element;
    }
    public void setPrev(CircularNode<T> prev) {
        this.prev = prev;
        prev.next= this;
    }
    public void setNext(CircularNode<T> next) {
        this.next = next;
        next.prev= this;
    }
    public CircularNode<T> setPrev(T element) {
        this.prev = new CircularNode(element);
        this.prev.next = this;
        return this.prev;
    }
    public CircularNode<T> setNext(T element) {
        this.next = new CircularNode(element);
        this.next.prev = this;
        return this.next;
    }
    public CircularNode<T> next() {
        return next;
    }
    public CircularNode<T> prev() {
        return prev;
    }
    public CircularNode<T> get(T element) {
        if (element==null) return null;
        if (element.equals(this.element)) return this;
        CircularNode<T> search = this.next;
        while(!search.equals(this)) {
            if (element.equals(search.element)) return search;
            search = search.next;
        }
        return null;
    }
    /**
     * Idem as get but searching in other direction
     * @param element
     * @return 
     */
    public CircularNode<T> get2(T element) {
        if (element==null) return null;
        if (element.equals(this.element)) return this;
        CircularNode<T> search = this.prev;
        while(!search.equals(this)) {
            if (element.equals(search.element)) return search;
            search = search.prev;
        }
        return null;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.element);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CircularNode<?> other = (CircularNode<?>) obj;
        if (!Objects.equals(this.element, other.element)) {
            return false;
        }
        return true;
    }
    
}
