/*
 * Copyright (C) 2016 jollion
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
package utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author jollion
 */
public class Pair<K, V> {

    
    
    public K key;
    public V value;
    public Pair(K key, V value) {
        this.key=key;
        this.value=value;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + (this.key != null ? this.key.hashCode() : 0);
        hash = 29 * hash + (this.value != null ? this.value.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (getClass() == obj.getClass()) {
            final Pair<?, ?> other = (Pair<?, ?>) obj;
            if (key!=null && other.key!=null && !other.key.equals(key)) return false;
            if (value!=null && other.value!=null && !other.value.equals(value)) return false;
            return true;
        } else return false;
    }
    @Override 
    public String toString() {
        return "{"+(key==null?"null":key.toString())+"->"+(value==null?"null":value.toString())+"}";
    }
    public static <K, V> List<V> unpairValues(Collection<Pair<K, V>> list) {
        if (list == null) {
            return null;
        }
        List<V> res = new ArrayList<V>(list.size());
        for (Pair<K, V> p : list) {
            if (p.value != null) {
                res.add(p.value);
            }
        }
        return res;
    }

    public static <K, V> List<K> unpairKeys(Collection<Pair<K, V>> list) {
        if (list == null) {
            return null;
        }
        List<K> res = new ArrayList<K>(list.size());
        for (Pair<K, V> p : list) {
            if (p.key != null) {
                res.add(p.key);
            }
        }
        return res;
    }
    public static <K> List<Pair<K, ?>> pairAsKeys(Collection<K> list) {
        if (list == null) {
            return null;
        }
        List<Pair<K, ?>> res = new ArrayList<Pair<K, ?>>(list.size());
        for (K k : list) res.add(new Pair(k, null));
        return res;
    }
    public static <V> List<Pair<?, V>> pairAsValues(Collection<V> list) {
        if (list == null) {
            return null;
        }
        List<Pair<?, V>> res = new ArrayList<Pair<?, V>>(list.size());
        for (V v : list) res.add(new Pair(null, v));
        return res;
    }
}
