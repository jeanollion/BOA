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
package boa.utils;

/**
 *
 * @author jollion
 */
public class HashMapGetCreateRedirected<K, V> extends HashMapGetCreate<K, V> {
    public enum Syncronization {NO_SYNC, SYNC_ON_KEY, SYNC_ON_MAP};
    final Syncronization sync;
    public HashMapGetCreateRedirected(HashMapGetCreate.Factory<K, V> factory, Syncronization sync) {
        super(factory);
        this.sync=sync;
    }
    public HashMapGetCreateRedirected(int initialCapacity, HashMapGetCreate.Factory<K, V> factory, Syncronization sync) {
        super(initialCapacity, factory);
        this.sync=sync;
    }
    @Override
    public V get(Object key) {
        switch(sync) {
            case NO_SYNC:
            default:
                return super.getAndCreateIfNecessary((K)key);
            case SYNC_ON_KEY:
                return super.getAndCreateIfNecessarySyncOnKey((K)key);
            case SYNC_ON_MAP:
                return super.getAndCreateIfNecessarySync((K)key);
        }
    }
}
