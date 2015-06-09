package morphiaTest;

import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Field;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Index;
import org.mongodb.morphia.annotations.Indexed;
import org.mongodb.morphia.annotations.Indexes;
import org.mongodb.morphia.utils.IndexDirection;

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

/**
 *
 * @author jollion
 */
@Entity
public class ClassEntity {
    @Id public ObjectId id;
    @Indexed(value=IndexDirection.DESC, unique=true, dropDups=true)
    private String name;
    private ClassEntity(){}
    
    public ObjectId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ClassEmbedded getEm() {
        return em;
    }
    @Embedded
    private ClassEmbedded em;
    
    public ClassEntity(String name) {
        this.name = name;
        em = new ClassEmbedded(name+"0em");
        em.setEmb();
    }
}
