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
public class PointContainer2<T, U> extends PointContainer<T> {
    protected U content2;
    public PointContainer2(T o1, U o2, float... coords) {
        super(o1, coords);
        this.content2= o2;
    }
    public U getContent2() {
        return content2;
    }
    public PointContainer2<T, U> setContent2(U o) {
        this.content2 = o;
        return this;
    }
    public static <T, U>  PointContainer2<T, U> fromPoint(Point p, T o, U o2) {
        return new PointContainer2(o, o2, p.coords);
    }
    @Override public String toString() {
        return super.toString() + "["+content2.toString()+"]";
    }
}
