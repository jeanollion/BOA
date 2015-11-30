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

import de.caluga.morphium.DAO;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.query.Query;
import java.util.Collections;
import java.util.List;
import org.bson.types.ObjectId;

/**
 *
 * @author jollion
 */
public class MeasurementsDAO extends DAO<Measurements>{
    Morphium morphium;
    public MeasurementsDAO(Morphium morphium) {
        super(morphium, Measurements.class);
        morphium.ensureIndicesFor(Measurements.class);
        this.morphium=morphium;
    }
    
    public Measurements getObject(ObjectId id) {
        return super.getQuery().getById(id);
    }
    
    public void store(Measurements o) {
        morphium.store(o);
    }
    
    public void delete(ObjectId id) {
        morphium.delete(super.getQuery().f("_id").eq(id));
    }
    
    public void delete(Measurements o) {
        if (o.getId()!=null) morphium.delete(o);
    }
    
    public void deleteAllObjects() {
        morphium.clearCollection(Measurements.class);
    }
    
    public void deleteObjectsFromField(String fieldName) {
        morphium.delete(super.getQuery().f("field_name").eq(fieldName));
    }
    
    protected Query<Measurements> getQuery(String fieldName, int structureIdx, String... measurements) {
        Query<Measurements> q= super.getQuery().f("field_name").eq(fieldName).f("structure_idx").eq(structureIdx).sort("time_point");
        if (measurements.length>0) q.setReturnedFields(Measurements.getReturnedFields(measurements));
        return q;
    }
    public List<Measurements> getMeasurements(String fieldName, int structureIdx, String... measurements) {
        List<Measurements> res = getQuery(fieldName, structureIdx, measurements).asList();
        Collections.sort(res);
        return res;
    }
}
