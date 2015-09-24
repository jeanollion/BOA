/*
 * Copyright (C) 2015 jollion
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

/**
 *
 * @author jollion
 */
public class ArrayUtil {
    public static int max(float[] array) {
        return max(array, 0, array.length);
    }
    /**
     * 
     * @param array 
     * @param start start of search index, inclusive
     * @param stop end of search index, exclusive
     * @return index of maximum value
     */
    public static int max(float[] array, int start, int stop) {
        if (start<0) start=0;
        if (stop>array.length) stop=array.length;
        if (stop<=start) throw new IllegalArgumentException("max search: idx start >= idx stop");
        int idxMax = start;
        for (int i = start+1; i<stop; ++i) if (array[i]>array[idxMax]) idxMax=i;
        return idxMax;
    }
    public static int min(float[] array) {
        return min(array, 0, array.length);
    }
    public static int min(float[] array, int start, int stop) {
        if (start<0) start=0;
        if (stop>array.length) stop=array.length;
        if (stop<=start) throw new IllegalArgumentException("max search: idx start >= idx stop");
        int idxMin = start;
        for (int i = start+1; i<stop; ++i) if (array[i]<array[idxMin]) idxMin=i;
        return idxMin;
    }
}
