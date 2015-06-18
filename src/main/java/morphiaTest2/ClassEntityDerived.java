/*
 * Copyright (C) 2015 ImageJ
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
package morphiaTest2;

import org.mongodb.morphia.annotations.Entity;

/**
 *
 * @author jollion
 */
@Entity(value = "ClassEntity", noClassnameStored = false)
public class ClassEntityDerived extends ClassEntity {
    int newIntParameter = 5;

    public int getNewIntParameter() {
        return newIntParameter;
    }
    protected ClassEntityDerived(){super();}
    public ClassEntityDerived(String name) {
        super(name);
    }
    
}
