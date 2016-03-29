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

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author jollion
 */
public class Element<E> { // éventuellement supprimer la classe Element et mettre une map E -> List<Interface>
        E data;
        List<Interface> interfaces;
        public Element(E data) {
            this.data=data;
            this.interfaces=new ArrayList<Interface>();
        }
        @Override
        public int hashCode() {
            int hash = 7;
            hash = 47 * hash + (this.data != null ? this.data.hashCode() : 0);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj==null) return false;
            if (obj instanceof Element) return ((Element)obj).data.equals(data);
            if (obj.getClass()==data.getClass()) return ((E)obj).equals(data);
            return false;
        }
    }
