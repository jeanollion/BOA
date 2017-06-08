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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author jollion
 */
public class HashMapGetCreate<K, V> extends HashMap<K, V> {
    Factory<K, V> factory;
    public HashMapGetCreate(Factory<K, V> factory) {
        super();
        this.factory=factory;
    }
    public HashMapGetCreate(int initialCapacity, Factory<K, V> factory) {
        super(initialCapacity);
        this.factory=factory;
    }
    public V getAndCreateIfNecessary(K key) {
        V v = super.get(key);
        if (v==null) {
            v = factory.create(key);
            super.put(key, v);
        }
        return v;
    }
    public V getAndCreateIfNecessarySync(K key) {
        V v = super.get(key);
        if (v==null) {
            synchronized(this) {
                v = super.get(key);
                if (v==null) {
                    v = factory.create(key);
                    super.put(key, v);
                }
            }
        }
        return v;
    }
    public static interface Factory<K, V> {
        public V create(K key);
    }
    public static class ArrayListFactory<K, V> implements Factory<K, ArrayList<V>>{
        @Override public ArrayList<V> create(K key) {
            return new ArrayList<>();
        }
    }
    public static class ListFactory<K, V> implements Factory<K, List<V>>{
        @Override public List<V> create(K key) {
            return new ArrayList<>();
        }
    }
    public static class SetFactory<K, V> implements Factory<K, Set<V>>{
        @Override public Set<V> create(K key) {
            return new HashSet<>();
        }
    }
    public static class MapFactory<K, L, V> implements Factory<K, Map<L, V>>{
        @Override public Map<L, V> create(K key) {
            return new HashMap<>();
        }
    }
    public static class HashMapGetCreateFactory<K, L, V> implements Factory<K, HashMapGetCreate<L, V>> {
        final Factory<L, V> factory;
        public HashMapGetCreateFactory(Factory<L, V> factory) {
            this.factory=factory;
        }
        @Override public HashMapGetCreate<L, V> create(K key) {
            return new HashMapGetCreate<>(factory);
        }
    }
}
