/*
 * Copyright (C) 2016 jollion
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
package utils.clustering;

import java.util.Comparator;

/**
 *
 * @author jollion
 */
public interface Interface<E, T extends Interface<E, T>> extends Comparable<T> {
    public E getE1();
    public E getE2();
    public boolean isInterfaceOf(E e1, E e2);
    public boolean isInterfaceOf(E e1);
    public E getOther(E e);
    
    public void swichElements(E newE, E oldE, Comparator<? super E> elementComparator);
    public void performFusion();
    public boolean checkFusion();
    public void fusionInterface(T otherInterface, Comparator<? super E> elementComparator);
    public void updateSortValue();
}
