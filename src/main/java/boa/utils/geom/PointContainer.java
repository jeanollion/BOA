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
