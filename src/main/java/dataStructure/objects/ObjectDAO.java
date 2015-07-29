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
import dataStructure.objects.StructureObject;
import de.caluga.morphium.DAO;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.query.Query;
import java.util.List;
import org.bson.types.ObjectId;

/**
 *
 * @author jollion
 */
public class ObjectDAO extends DAO<StructureObject>{
    Morphium morphium;
    public ObjectDAO(Morphium morphium) {
        super(morphium, StructureObject.class);
        morphium.ensureIndicesFor(StructureObject.class);
        this.morphium=morphium;
    }
    
    private Query<StructureObject> getQuery(ObjectId parentId, int structureIdx) {
        // voir si la query est optimisée pour index composé
        return super.getQuery().f("parentId").eq(parentId).f("structureIdx").eq(structureIdx);
    }
    
    public StructureObject[] getObjects(ObjectId parentId, int structureIdx) {
        List<StructureObject> list = this.getQuery(parentId, structureIdx).sort("idx").asList();
        return list.toArray(new StructureObject[(int)list.size()]);
    }
    
    public void deleteChildren(ObjectId id, int structureIdx) {
        morphium.delete(getQuery(id, structureIdx));
    }
    
    public void delete(StructureObject o) {
        morphium.delete(o);
    }
    
    public void store(StructureObject o) {
        morphium.store(o);
    }
    
    public void store(StructureObject[] objects) {
        for (StructureObject o : objects) morphium.store(o);
        for (StructureObject o : objects) updateNextId(o);
    }
    
    public void updateTrackLinks(StructureObject o) {
        boolean prev = o.previous!=null && o.previousId==null && o.previous.id!=null;
        boolean next = o.next!=null && o.nextId==null && o.next.id!=null;
        if (prev) o.previousId=o.previous.id;
        if (next) o.nextId=o.next.id;
        if (prev && next) morphium.updateUsingFields(o, "next_id", "previous_id");
        else if (prev) morphium.updateUsingFields(o, "previous_id");
        else if (next) morphium.updateUsingFields(o, "next_id");
    }
    
    private void updateNextId(StructureObject o) {
        if (o.next != null && o.nextId == null && o.next.id != null) {
            o.nextId = o.next.id;
            morphium.updateUsingFields(o, "next_id");
        }
    }
}
