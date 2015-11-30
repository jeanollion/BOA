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
package dataStructure.objects;

import dataStructure.configuration.*;
import com.mongodb.MongoClient;
import dataStructure.configuration.Experiment;
import dataStructure.containers.RegionVoxelsDB;
import de.caluga.morphium.DAO;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.query.Query;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.bson.types.ObjectId;

/**
 *
 * @author jollion
 */
public class RegionDAO extends DAO<RegionVoxelsDB>{
    Morphium morphium;
    public RegionDAO(Morphium morphium) {
        super(morphium, RegionVoxelsDB.class);
        morphium.ensureIndicesFor(RegionVoxelsDB.class);
        this.morphium=morphium;
    }
    
    public RegionVoxelsDB getObject(ObjectId id) {
        return super.getQuery().getById(id);
    }
    
    public void store(RegionVoxelsDB o) {
        morphium.store(o);
    }
    
    public void delete(ObjectId id) {
        morphium.delete(super.getQuery().f("_id").eq(id));
    }
    
    public void delete(RegionVoxelsDB o) {
        if (o.getId()!=null) morphium.delete(o);
    }
    
    public void deleteAllObjects() {
        morphium.clearCollection(RegionVoxelsDB.class);
    }
    
    public void deleteObjectsFromField(String fieldName) {
        morphium.delete(super.getQuery().f("field_name").eq(fieldName));
    }
}
