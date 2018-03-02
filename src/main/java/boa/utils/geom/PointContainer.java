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
package boa.utils.geom;


/**
 *
 * @author jollion
 */
public class PointContainer<T> extends Point {
    public T content1;
    public PointContainer(T o, float... coords) {
        super(coords);
        this.content1= o;
    }
    public T getContent1() {
        return content1;
    }
    public PointContainer<T> setContent1(T o) {
        this.content1 = o;
        return this;
    }
    public static <I>  PointContainer<I> fromPoint(Point p, I o) {
        return new PointContainer(o, p.coords);
    }
    @Override public String toString() {
        return super.toString() + "["+content1.toString()+"]";
    }
}
