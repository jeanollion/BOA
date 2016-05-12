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
import java.util.List;
import utils.clustering.ClusterCollection.InterfaceSortMethod;

/**
 *
 * @author jollion
 */
public class Interface<E, I> implements Comparable<Interface<E, I>> {
        public final E e1, e2;
        I data;
        double sortValue;
        
        public Interface(E e1, E e2, I data, Comparator<? super E> elementComparator) {
            this.data=data;
            if (elementComparator!=null) {
                if (elementComparator.compare(e1, e2)<=0) {
                    this.e1=e1;
                    this.e2=e2;
                } else {
                    this.e2=e1;
                    this.e1=e2;
                }
            } else {
                this.e1=e1;
                this.e2=e2;
            }
        }
        
        public I getData() {return data;}
        
        public void updateSortValue(InterfaceSortMethod<E, I> is) {
            sortValue=is.computeSortValue(this);
        }
        
        public E getOther(E e) {
            if (e==e1) return e2;
            else if (e==e2) return e1;
            else return null;
        }
        
        public boolean isInterfaceOf(E e1, E e2) {
            return this.e1==e1 && this.e2==e2 || this.e1==e2 && this.e2==e1;
        }
        
        public boolean isInterfaceOf(E e1) {
            return this.e1==e1 || this.e2==e1;
        }
        
        public E getCommonElement(Interface<E, I> other) {
            if (e1==other.e1 || e1==other.e2) return e1;
            else if (e2==other.e1 || e2==other.e2) return e2;
            else return null;
            
        }
        
        public boolean hasOneRegionWithNoOtherInteractant(ClusterCollection<E, I> c) {
            List<Interface<E, I>> l1 = c.elementInterfaces.get(e1);
            if (l1==null || l1.isEmpty() || (l1.size()==1 && l1.contains(this))) return true;
            List<Interface<E, I>> l2 = c.elementInterfaces.get(e2);
            return (l2==null || l2.isEmpty() || (l2.size()==1 && l2.contains(this)) );
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 37 * hash + (this.e1 != null ? this.e1.hashCode() : 0);
            hash = 37 * hash + (this.e2 != null ? this.e2.hashCode() : 0);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Interface other = (Interface) obj;
            if (this.e1 != other.e1 && (this.e1 == null || !this.e1.equals(other.e1))) {
                return false;
            }
            if (this.e2 != other.e2 && (this.e2 == null || !this.e2.equals(other.e2))) {
                return false;
            }
            return true;
        }

        public int compareTo(Interface<E, I> o) {
            return Double.compare(sortValue, o.sortValue);
        }
    }
