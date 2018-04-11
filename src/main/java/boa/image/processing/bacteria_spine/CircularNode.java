/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.image.processing.bacteria_spine;

import java.util.List;
import java.util.Objects;

/**
 *
 * @author jollion
 */
public class CircularNode<T> implements Comparable<CircularNode> {
    CircularNode<T> prev, next;
    T element;
    public CircularNode(T element) {
        this.element = element;
    }

    public T getElement() {
        return element;
    }

    public void setElement(T element) {
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
    public CircularNode<T> getFollowing(boolean next) {
        return next ? this.next : prev;
    }
    public CircularNode<T> getInFollowing(T element, boolean next) {
        return next ? getInNext(element) : getInPrev(element);
    }
    public CircularNode<T> getInNext(T element) {
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
     * Idem as getFollowing but searching in other direction
     * @param element
     * @return 
     */
    public CircularNode<T> getInPrev(T element) {
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

    @Override
    public int compareTo(CircularNode o) {
        if (this.equals(o)) return 0;
        if (this.prev.equals(o)) return 1;
        if (this.next.equals(o)) return -1;
        CircularNode p = o.prev;
        CircularNode n = o.next;
        while(!p.equals(n) || this.equals(p)) { // second condition if searched value is at the exaxt opposite of the contour
            if (this.equals(p)) return -1;
            if (this.equals(n)) return 1;
            p = p.prev;
            n = n.next;
        }
        throw new IllegalArgumentException("Circular Node not from the same list");
    }
    @Override
    public String toString() {
        return element.toString();
    }
}
