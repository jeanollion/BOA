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
package boa.image;

/**
 *
 * @author jollion
 */
public interface Offset<T> {
    public int xMin();
    public int yMin();
    public int zMin();
    public T resetOffset();
    public T reverseOffset();
    public T translate(Offset other);
    public static boolean offsetNull(Offset offset) {
        return offset.xMin()==0 && offset.yMin() == 0 && offset.zMin()==0;
    }
}
