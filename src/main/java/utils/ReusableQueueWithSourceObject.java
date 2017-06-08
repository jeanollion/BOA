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
package utils;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jollion
 */
public class ReusableQueueWithSourceObject<T, S> {
    //public static final org.slf4j.Logger logger = LoggerFactory.getLogger(ReusableQueueWithSourceObject.class);
    protected final Queue<Pair<S, T>> queue = new LinkedList<>();
    protected final Factory<T, S> factory;
    protected final Reset<T, S> reset;
    protected boolean search;
    public ReusableQueueWithSourceObject(Factory<T, S> factory, Reset<T, S> reset, boolean search) {
        this.factory=factory;
        this.reset=reset;
        this.search=search;
    }
    public synchronized T poll(S sourceObject) {
        Pair<S, T> res = search&&sourceObject!=null ? searchInQueue(sourceObject) : null;
        if (res==null) res = queue.poll();
        if (res==null) return factory.create(sourceObject);
        else if (reset!=null) return reset.reset(res.value, sourceObject);
        return res.value;
    }
    public synchronized void push(T object, S source) {
        queue.add(new Pair(source, object));
        //logger.debug("queue size: {} (type: {})", queue.size(), object.getClass().getSimpleName());
    }
    public static interface Factory<T, S> {
        public T create(S sourceObject);
    }
    public static interface Reset<T, S> {
        public T reset(T object, S sourceObject);
    }
    protected Pair<S, T> searchInQueue(S source) {
        Iterator<Pair<S, T>> it = queue.iterator();
        while(it.hasNext()) {
            Pair<S, T> p = it.next();
            if (source.equals(p.key)) {
                it.remove();
                //logger.debug("Queue search: found {} for {}, queue size: {} (type: {})", p.value, source, queue.size(), p.value.getClass().getSimpleName());
                return p;
            }
        }
        return null;
    }
}
