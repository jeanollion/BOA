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
import org.bson.types.ObjectId;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.dao.BasicDAO;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.QueryResults;

/**
 *
 * @author jollion
 */
public class ObjectDAO extends BasicDAO<StructureObject, ObjectId>{

    public ObjectDAO(Class<StructureObject> entityClass, MongoClient mongoClient, Morphia morphia) {
        super(entityClass, mongoClient, morphia, "Objects");
        this.ensureIndexes();
    }
    
    private Query<StructureObject> getQuery(ObjectId parentId, int structureIdx) {
        // voir si la query est optimisée pour index composé
        Query<StructureObject> query = this.getDatastore().createQuery(this.getEntityClass()).filter("parentId", parentId).filter("structureIdx", structureIdx);
        return query;
    }
    
    public StructureObject[] getObjects(ObjectId parentId, int structureIdx) {
        QueryResults<StructureObject> queryres = this.find(getQuery(parentId, structureIdx).order("idx"));
        return queryres.asList().toArray(new StructureObject[(int)queryres.countAll()]);
    }
    
    public void deleteChildren(ObjectId id, int structureIdx) {
        this.deleteByQuery(getQuery(id, structureIdx));
    }
}
