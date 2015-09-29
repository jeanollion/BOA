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
import static dataStructure.objects.StructureObject.logger;
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
            if (res!=null) {
                setToCache(res);
                logger.trace("structure object {} of Id {} was NOT in cache", res, id);
            }
        } else logger.trace("structure object {} of Id {} was already in cache", res, id);
        return res;
    }
    
    public StructureObject getFromCache(ObjectId id) {return idCache.get(id);}
    
    public void setToCache(StructureObject o) {idCache.put(o.getId(), o);}
    
    public StructureObject checkAgainstCache(StructureObject o) {
        if (o==null) return null;
        StructureObject res = idCache.get(o.getId());
        if (res==null)  {
            setToCache(o);
            return o;
        } else return res;
    }
    
    public void clearCache() {this.idCache=new HashMap<ObjectId, StructureObject>();}
    
    
    
    private StructureObject[] checkAgainstCache(List<StructureObject> list) {
        StructureObject[] res= new StructureObject[list.size()];
        int idx=0;
        for (StructureObject o : list) res[idx++] = checkAgainstCache(o);
        return res;
    }
    
    public StructureObject[] getObjects(ObjectId parentId, int structureIdx) {
        List<StructureObject> list = this.getQuery(parentId, structureIdx).sort("idx").asList();
        return checkAgainstCache(list);
    }
    
    public void deleteChildren(ObjectId parentId, int structureIdx) {
        morphium.delete(getQuery(parentId, structureIdx));
        // also delete in cache: 
        Iterator<Entry<ObjectId, StructureObject>> it = idCache.entrySet().iterator();
        while(it.hasNext()) if (it.next().getValue().getParent().getId().equals(parentId)) it.remove();
    }
    
    public void deleteObjectsFromField(String fieldName) {
        morphium.delete(super.getQuery().f("field_name").eq(fieldName));
        // also delete in cache: 
        Iterator<Entry<ObjectId, StructureObject>> it = idCache.entrySet().iterator();
        while(it.hasNext()) if (it.next().getValue().fieldName.equals(fieldName)) it.remove();
    }
    
    public void deleteObjectsFromFieldTP(String fieldName, int timePoint) {
        morphium.delete(super.getQuery().f("field_name").eq(fieldName).f("time_point").eq(timePoint));
        // also delete in cache: 
        Iterator<Entry<ObjectId, StructureObject>> it = idCache.entrySet().iterator();
        while(it.hasNext()) {
            StructureObject o = it.next().getValue();
            if (o.fieldName.equals(fieldName) && o.getTimePoint()==timePoint) it.remove();
        }
    }
    
    public void deleteObjectsFromFieldS(String fieldName, int structureIdx) {
        morphium.delete(super.getQuery().f("field_name").eq(fieldName).f("structure_idx").eq(structureIdx));
        // also delete in cache: 
        Iterator<Entry<ObjectId, StructureObject>> it = idCache.entrySet().iterator();
        while(it.hasNext()) {
            StructureObject o = it.next().getValue();
            if (o.fieldName.equals(fieldName) && o.getStructureIdx()==structureIdx) it.remove();
        }
    }
    
    public void deleteObjectsFromField(String fieldName, int timePoint, int structureIdx) {
        morphium.delete(super.getQuery().f("field_name").eq(fieldName).f("time_point").eq(timePoint).f("structure_idx").eq(structureIdx));
        // also delete in cache: 
        Iterator<Entry<ObjectId, StructureObject>> it = idCache.entrySet().iterator();
        while(it.hasNext()) {
            StructureObject o = it.next().getValue();
            if (o.fieldName.equals(fieldName) && o.getTimePoint()==timePoint && o.getStructureIdx()==structureIdx) it.remove();
        }
    }
    
    public void deleteAllObjects() {
        morphium.clearCollection(StructureObject.class);
        idCache.clear();
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
        for (StructureObject o : objects) { //TODO utiliser updateUsingFields quand bug resolu
            if (o.getParent()!=null) o.setParentTrackHeadId(o.getParent().getTrackHeadId());
            if (o.getTrackHeadId()==null) {
                if (o.isTrackHead) o.trackHeadId=o.getId();
                else if (o.getPrevious()!=null) o.trackHeadId=o.previous.getTrackHeadId();
            }
            //morphium.updateUsingFields(o, "parent_track_head_id", "track_head_id");
            //morphium.updateUsingFields(object, "next", "previous");
            //System.out.println("update track attribute:"+ o.timePoint+ " next null?"+(o.next==null)+ "previous null?"+(o.previous==null));
            morphium.store(o);
        }
    }
    
    public StructureObject[] getTrackHeads(StructureObject parentTrack, int structureIdx) {
        if (parentTrack==null) return new StructureObject[0];
        List<StructureObject> list =  super.getQuery().f("is_track_head").eq(true).f("parent_track_head_id").eq(parentTrack.getTrackHeadId()).f("structure_idx").eq(structureIdx).sort("time_point", "idx").asList();
        logger.trace("track head query: parentTrack: {} structure: {} result length: {}", parentTrack.getTrackHeadId(), structureIdx, list.size());
        return this.checkAgainstCache(list);
    }
    
    public StructureObject[] getTrack(StructureObject track) {
        List<StructureObject> list =  super.getQuery().f("track_head_id").eq(track.getTrackHeadId()).sort("time_point").asList();
        StructureObject[] res  = checkAgainstCache(list);
        for (int i = 1; i<res.length; ++i) {
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
        return ObjectDAO.this.checkAgainstCache(getRootQuery(fieldName, timePoint).get());
    }
}
