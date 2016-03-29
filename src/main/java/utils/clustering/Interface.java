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
public class Interface<E, I> {
        Element<E> e1, e2;
        I data;
        public Interface(Element<E> e1, Element<E> e2, I data, Comparator<? super E> elementComparator) {
            this.data=data;
            if (elementComparator!=null) {
                if (elementComparator.compare(e1.data, e2.data)<=0) {
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
    }
