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
public class PointContainer2<T, U> extends PointContainer<T> {
    protected U o2;
    public PointContainer2(T o1, U o2, float... coords) {
        super(o1, coords);
        this.o2= o2;
    }
    public U get2() {
        return o2;
    }
    public PointContainer2<T, U> set2(U o) {
        this.o2 = o;
        return this;
    }
    public static <T, U>  PointContainer2<T, U> fromPoint(Point p, T o, U o2) {
        return new PointContainer2(o, o2, p.coords);
    }
}
