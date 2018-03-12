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
package boa.utils;

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
    /**
     * synchronization is done on key so that if creation of V is long, the whole map is not blocked during creation of V
     * @param key
     * @return 
     */
    public V getAndCreateIfNecessarySyncOnKey(K key) {
        V v = super.get(key);
        if (v==null) {
            synchronized(key) {
                v = super.get(key);
                if (v==null) {
                    v = factory.create(key);
                    synchronized(this){
                        super.put(key, v);
                    }
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
