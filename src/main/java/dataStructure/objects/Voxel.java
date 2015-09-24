/*
 * Copyright (C) 2015 nasique
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
package dataStructure.objects;

import java.util.Comparator;

/**
 *
 * @author nasique
 */
public abstract class Voxel implements Comparable<Voxel>{
    public int x, y;
    public float value;
    public abstract int getZ();
    
    @Override
    public int compareTo(Voxel other) {
        if (other.value<value) return 1;
        else if (other.value>value) return -1;
        else return 0;
    }
    
    @Override
    public boolean equals(Object other) {
        if (other instanceof Voxel) {
            Voxel otherV = (Voxel)other;
            return x==otherV.x && y==otherV.y && getZ()==otherV.getZ();
        } else return false;
    }
    
    public static Comparator<Voxel> getInvertedComparator() {
        return new Comparator<Voxel>() {
            @Override
            public int compare(Voxel voxel, Voxel other) {
                if (voxel.value < other.value) {
                    return 1;
                } else if (voxel.value > other.value) {
                    return -1;
                } else {
                    return 0;
                }
            }
        };
    }
}
