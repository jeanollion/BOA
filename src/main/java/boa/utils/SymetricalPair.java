/*
 * Copyright (C) 2017 jollion
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
package boa.utils;

/**
 *
 * @author jollion
 */
public class SymetricalPair<E> extends Pair<E, E> {

    public SymetricalPair(E e1, E e2) {
        super(e1, e2);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Pair) {
            final Pair<?, ?> other = (Pair<?, ?>) obj;
            if (key!=null && value!=null) return key.equals(other.key) && value.equals(other.value) || key.equals(other.value) && value.equals(other.key);
            else if (key==null && value!=null) return other.key==null && value.equals(other.value) || other.value==null && value.equals(other.key);
            else if (value==null && key!=null) return other.key==null && key.equals(other.value) || other.value==null && key.equals(other.key);
            else return other.key==null && other.value==null;
        } else return false;
    }

    @Override
    public int hashCode() {
        return (this.key != null ? this.key.hashCode() : 0) ^ (this.value != null ? this.value.hashCode() : 0);
    }
}
