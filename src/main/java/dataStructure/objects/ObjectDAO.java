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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import org.bson.types.ObjectId;

/**
 *
 * @author jollion
 */
public class ObjectDAO extends DAO<StructureObject>{
    Morphium morphium;
    ExperimentDAO xpDAO;
    HashMap<ObjectId, StructureObject> idCache;
    public ObjectDAO(Morphium morphium, ExperimentDAO xpDAO) {
        super(morphium, StructureObject.class);
        morphium.ensureIndicesFor(StructureObject.class);
        this.morphium=morphium;
        this.xpDAO=xpDAO;
        idCache = new HashMap<ObjectId, StructureObject>();
    }
    
    private Query<StructureObject> getQuery(ObjectId parentId, int structureIdx) {
        // voir si la query est optimisée pour index composé
        return super.getQuery().f("parent").eq(parentId).f("structure_idx").eq(structureIdx);
    }
    
    public StructureObject getObject(ObjectId id) {
        StructureObject res = idCache.get(id);
        if (res==null)  {
            res= super.getQuery().getById(id);
            if (res!=null) setToCache(res);
        }
        return res;
    }
    
    public StructureObject getFromCache(ObjectId id) {return idCache.get(id);}
    
    public void setToCache(StructureObject o) {idCache.put(o.getId(), o);}
    
    private StructureObject checkCache(StructureObject o) {
        StructureObject res = idCache.get(o.getId());
        if (res==null)  {
            setToCache(o);
            return o;
        } else return res;
    }
    
    public void clearCache() {this.idCache=new HashMap<ObjectId, StructureObject>();}
    
    
    
    private StructureObject[] checkCache(List<StructureObject> list) {
        StructureObject[] res= new StructureObject[list.size()];
        int idx=0;
        for (StructureObject o : list) res[idx++] = checkCache(o);
        return res;
    }
    
    public StructureObject[] getObjects(ObjectId parentId, int structureIdx) {
        List<StructureObject> list = this.getQuery(parentId, structureIdx).sort("idx").asList();
        return checkCache(list);
    }
    
    public void deleteChildren(ObjectId parentId, int structureIdx) {
        morphium.delete(getQuery(parentId, structureIdx));
        // also delete in cache: 
        Iterator<Entry<ObjectId, StructureObject>> it = idCache.entrySet().iterator();
        while(it.hasNext()) if (it.next().getValue().getParent().getId().equals(parentId)) it.remove();
    }
    
    public void delete(StructureObject o) {
        morphium.delete(o);
        idCache.remove(o.getId());
    }
    
    public void store(StructureObject...objects) {
        if (objects==null) return;
        for (StructureObject o : objects) {
            o.updateObjectContainer();
            morphium.store(o);
            idCache.put(o.getId(), o);
        }
    }
    // track-specific methods
    
    public void updateTrackAttributes(StructureObject... objects) {
        if (objects==null) return;
        for (StructureObject o : objects) { //TODO utiliser update quand bug resolu
            //morphium.updateUsingFields(o, "parent_track_head_id", "track_head_id");
            //morphium.updateUsingFields(object, "next", "previous");
            //System.out.println("update track attribute:"+ o.timePoint+ " next null?"+(o.next==null)+ "previous null?"+(o.previous==null));
            morphium.store(o);
        }
    }
    
    public StructureObject[] getTrackHeads(StructureObject parentTrack) {
        List<StructureObject> list =  super.getQuery().f("is_track_head").eq(true).f("parent_track_head_id").eq(parentTrack.getTrackHeadId()).sort("time_point", "idx").asList();
        return this.checkCache(list);
    }
    
    public StructureObject[] getTrack(StructureObject track) {
        List<StructureObject> list =  super.getQuery().f("track_head_id").eq(track.getTrackHeadId()).sort("time_point").asList();
        StructureObject[] res  = checkCache(list);
        for (int i = 1; i<list.size(); ++i) {
            res[i].previous=res[i-1];
            res[i-1].next=res[i];
        }
        return res;
    }
    
    // root-specific methods
    
    private Query<StructureObject> getRootQuery(String fieldName, int timePoint) {
        return super.getQuery().f("field_name").eq(fieldName).f("time_point").eq(timePoint).f("structure_idx").eq(-1);
    }
    /*private ObjectId getRootId(String fieldName, int timePoint) {
        Query<StructureObject> q = getRootQuery(fieldName, timePoint);
        q.setReturnedFields("_id");
        return q.get().id;
    }*/
    
    public StructureObject getRoot(String fieldName, int timePoint) {
        return checkCache(getRootQuery(fieldName, timePoint).get());
    }
}
