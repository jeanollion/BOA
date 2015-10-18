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
import de.caluga.morphium.async.AsyncOperationCallback;
import de.caluga.morphium.async.AsyncOperationType;
import de.caluga.morphium.query.Query;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import javax.swing.SwingUtilities;
import org.bson.types.ObjectId;
import utils.MorphiumUtils;

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
        logger.debug("object DAO creation: xpDAO null? {}, experiment null? {}, DAO already set? {}", xpDAO==null, xpDAO==null?"true":xpDAO.getExperiment()==null, xpDAO==null?"false":xpDAO.getExperiment()==null?"false":xpDAO.getExperiment().getObjectDAO()==null);
        if (xpDAO!=null && xpDAO.getExperiment()!=null && xpDAO.getExperiment().getObjectDAO()==null) xpDAO.getExperiment().setObjectDAO(this);
        
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
                //logger.trace("structure object {} of Id {} was NOT in cache", res, id);
            }
        } //else logger.trace("structure object {} of Id {} was already in cache", res, id);
        return res;
    }
    
    public StructureObject getFromCache(ObjectId id) {return idCache.get(id);}
    
    public void setToCache(StructureObject o) {
        if (xpDAO!=null) o.xp=xpDAO.getExperiment();
        idCache.put(o.getId(), o);
    }
    
    public StructureObject checkAgainstCache(StructureObject o) {
        if (o==null) return null;
        StructureObject res = idCache.get(o.getId());
        if (res==null)  {
            setToCache(o);
            return o;
        } else return res;
    }
    
    public void clearCache() {this.idCache=new HashMap<ObjectId, StructureObject>();}
    
    
    
    private ArrayList<StructureObject> checkAgainstCache(List<StructureObject> list) {
        ArrayList<StructureObject> res= new ArrayList<StructureObject>(list.size());
        for (StructureObject o : list) res.add(checkAgainstCache(o));
        return res;
    }
    
    public ArrayList<StructureObject> getObjects(ObjectId parentId, int structureIdx) {
        List<StructureObject> list = this.getQuery(parentId, structureIdx).sort("idx").asList();
        return checkAgainstCache(list);
    }
    
    public void deleteChildren(ObjectId parentId, int structureIdx) {
        morphium.delete(getQuery(parentId, structureIdx));
        // also delete in cache: 
        Iterator<Entry<ObjectId, StructureObject>> it = idCache.entrySet().iterator();
        while(it.hasNext()) {
            StructureObject cur = it.next().getValue();
            if (cur.getParent()!=null && cur.getParent().getId().equals(parentId)) it.remove();
        }
        // delete in ImageDAO
        this.xpDAO.getExperiment().getImageDAO().deleteChildren(getObject(parentId), structureIdx);
    }
    
    public void deleteObjectsFromField(String fieldName) {
        morphium.delete(super.getQuery().f("field_name").eq(fieldName));
        // delete in cache: 
        Iterator<Entry<ObjectId, StructureObject>> it = idCache.entrySet().iterator();
        while(it.hasNext()) if (it.next().getValue().fieldName.equals(fieldName)) it.remove();
        // delete in ImageDAO
        this.xpDAO.getExperiment().getImageDAO().deleteFieldMasks(xpDAO.getExperiment(), fieldName);
    }
    
    /*public void deleteObjectsFromFieldTP(String fieldName, int timePoint) {
        morphium.delete(super.getQuery().f("field_name").eq(fieldName).f("time_point").eq(timePoint));
        // also delete in cache: 
        Iterator<Entry<ObjectId, StructureObject>> it = idCache.entrySet().iterator();
        while(it.hasNext()) {
            StructureObject o = it.next().getValue();
            if (o.fieldName.equals(fieldName) && o.getTimePoint()==timePoint) it.remove();
        }
    }*/
    
    /*public void deleteObjectsFromFieldS(String fieldName, int structureIdx) {
        morphium.delete(super.getQuery().f("field_name").eq(fieldName).f("structure_idx").eq(structureIdx));
        // delete in cache: 
        Iterator<Entry<ObjectId, StructureObject>> it = idCache.entrySet().iterator();
        while(it.hasNext()) {
            StructureObject o = it.next().getValue();
            if (o.fieldName.equals(fieldName) && o.getStructureIdx()==structureIdx) it.remove();
        }
        
    }*/
    
    /*public void deleteObjectsFromField(String fieldName, int timePoint, int structureIdx) {
        morphium.delete(super.getQuery().f("field_name").eq(fieldName).f("time_point").eq(timePoint).f("structure_idx").eq(structureIdx));
        // also delete in cache: 
        Iterator<Entry<ObjectId, StructureObject>> it = idCache.entrySet().iterator();
        while(it.hasNext()) {
            StructureObject o = it.next().getValue();
            if (o.fieldName.equals(fieldName) && o.getTimePoint()==timePoint && o.getStructureIdx()==structureIdx) it.remove();
        }
    }*/
    
    public void deleteAllObjects() {
        morphium.clearCollection(StructureObject.class);
        idCache.clear();
        // delete in ImageDAO
        for (String fieldName : xpDAO.getExperiment().getFieldsAsString()) this.xpDAO.getExperiment().getImageDAO().deleteFieldMasks(xpDAO.getExperiment(), fieldName);
    }
    
    public void delete(StructureObject o) {
        morphium.delete(o);
        idCache.remove(o.getId());
        o.deleteMask();
    }
    
    public void store(StructureObject object) {
        object.updateObjectContainer();
        morphium.store(object);
        idCache.put(object.getId(), object);
    }
    
    public void store(boolean updateTrackAttributes, StructureObject... object) {
        if (object==null) return;
        if (object.length==0) return;
        if (object.length==1) store(object[0]);
        else store(Arrays.asList(object), updateTrackAttributes);
    }
    
    public void store(final List<StructureObject> objects, final boolean updateTrackAttributes) {
        if (objects==null) return;
        logger.debug("calling store metohd: nb of objects: {} updateTrack: {}", objects.size(), updateTrackAttributes);
        for (StructureObject o : objects) o.updateObjectContainer();
        for (StructureObject o : objects) {
            morphium.store(o);
            logger.debug("store in cache object: {} id: {}", o, o.getId());
            idCache.put(o.getId(), o);
        }
        if (updateTrackAttributes) updateTrackAttributes(objects);
        /*morphium.store(objects, new AsyncOperationCallback<StructureObject>() {
            public void onOperationSucceeded(AsyncOperationType type, Query<StructureObject> q, long duration, List<StructureObject> result, StructureObject entity, Object... param) {
                logger.debug("store succeded: duration: {} nb objects: {}, type: {}, query: {}, entity: {}, param: {}", duration, objects.size(), type, q, entity, param);
                for (StructureObject o : objects) {
                    logger.debug("store in cache object: {} id: {}", o, o.getId());
                    idCache.put(o.getId(), o);
                }
                if (updateTrackAttributes) updateTrackAttributes(objects);
            }
            public void onOperationError(AsyncOperationType type, Query<StructureObject> q, long duration, String error, Throwable t, StructureObject entity, Object... param) {
                logger.error("store error!");
            }
        });*/
        //if (waitForWrites) MorphiumUtils.waitForWrites(morphium);
        //MorphiumUtils.waitForWrites(morphium);
        
    }
    // track-specific methods
    
    /*public void updateParent(final List<StructureObject> objects) {
        // TODO update only parent field
        morphium.store(objects, new AsyncOperationCallback<StructureObject>() {
            public void onOperationSucceeded(AsyncOperationType type, Query<StructureObject> q, long duration, List<StructureObject> result, StructureObject entity, Object... param) {
                logger.trace("update parent succeded: duration: {} nb objects: {}", duration, objects.size());
            }
            public void onOperationError(AsyncOperationType type, Query<StructureObject> q, long duration, String error, Throwable t, StructureObject entity, Object... param) {
                logger.error("update parent error!");
            }
        });
        //if (waitForWrites) MorphiumUtils.waitForWrites(morphium);
        MorphiumUtils.waitForWrites(morphium);
    }*/
    /**
     * {@link ObjectDAO#updateTrackAttributes(java.util.List) }
     * @param track 
     */
    public void updateTrackAttributes(StructureObject... track) {
        if (track==null) return;
        else if (track.length==0) return;
        else updateTrackAttributes(Arrays.asList(track));
    }
    /**
     * Set and store trackHeadId & parentTrackHeadId attributes; next and previous are not concerned by this method
     * @param track list of objects. All objects of a given track should be present, sorted by incresing timepoint. objects from several tracks can be present;
     */
    public void updateTrackAttributes(final List<StructureObject> track) {
        if (track==null) return;
        //MorphiumUtils.waitForWrites(morphium);
        for (StructureObject o : track) { 
            if (o.getParent()!=null) o.setParentTrackHeadId(o.getParent().getTrackHeadId());
            if (o.getTrackHeadId()==null) {                
                if (!o.isTrackHead && o.getPrevious()!=null) { //for trackHeads -> automoatically set by getTrackHeadId Method
                    o.trackHeadId=o.previous.getTrackHeadId();
                    logger.debug("set track head of {} from previous: {}, trackHeadId: {}", o, o.getPrevious(), o.getTrackHeadId());
                }
            }
            if (o.getParent()!=null && o.getTrackHeadId()!=null) morphium.updateUsingFields(o, "parent_track_head_id", "track_head_id");
            else if (o.getParent()!=null) morphium.updateUsingFields(o, "parent_track_head_id");
            else if (o.getTrackHeadId()!=null) morphium.updateUsingFields(o, "track_head_id");
            //morphium.updateUsingFields(object, "next", "previous");
            //System.out.println("update track attribute:"+ o.timePoint+ " next null?"+(o.next==null)+ "previous null?"+(o.previous==null));
        }
        
        //Thread t = new Thread(new Runnable() { //TODO utiliser updateUsingFields quand bug resolu
            //public void run() {
                
                /*morphium.store(objects, new AsyncOperationCallback<StructureObject>() {
                    public void onOperationSucceeded(AsyncOperationType type, Query<StructureObject> q, long duration, List<StructureObject> result, StructureObject entity, Object... param) {
                        logger.trace("update succeded: duration: {} nb objects: {}", duration, objects.size());
                        for (StructureObject o : objects) idCache.put(o.getId(), o);
                    }
                    public void onOperationError(AsyncOperationType type, Query<StructureObject> q, long duration, String error, Throwable t, StructureObject entity, Object... param) {
                        logger.error("update error!");
                    }
                });*/
            //}
        //});
        //SwingUtilities.invokeLater(t);
        //MorphiumUtils.waitForWrites(morphium);
    }
    
    public ArrayList<StructureObject> getTrackHeads(StructureObject parentTrack, int structureIdx) {
        if (parentTrack==null) return new ArrayList<StructureObject>(0);
        List<StructureObject> list =  super.getQuery().f("is_track_head").eq(true).f("parent_track_head_id").eq(parentTrack.getTrackHeadId()).f("structure_idx").eq(structureIdx).sort("time_point", "idx").asList();
        logger.trace("track head query: parentTrack: {} structure: {} result length: {}", parentTrack.getTrackHeadId(), structureIdx, list.size());
        return this.checkAgainstCache(list);
    }
    
    public ArrayList<StructureObject> getTrack(StructureObject track) {
        List<StructureObject> list =  super.getQuery().f("track_head_id").eq(track.getTrackHeadId()).sort("time_point").asList();
        ArrayList<StructureObject> res  = checkAgainstCache(list);
        StructureObject prev = null;
        for (StructureObject o : res) {
            if (prev!=null) {
                o.previous=prev;
                prev.next=o;
            }
            prev = o;
        }
        return res;
    }
    
    public ArrayList<StructureObject> getTrackErrors(StructureObject parentTrack, int structureIdx) {
        List<StructureObject> list =  super.getQuery().f("parent_track_head_id").eq(parentTrack.getTrackHeadId()).f("structure_idx").eq(structureIdx).f("track_link_error").eq(true).asList();
        return this.checkAgainstCache(list);
    }
    
    public ArrayList<StructureObject> getTrackErrors(String fieldName, int structureIdx) {
        List<StructureObject> list =  super.getQuery().f("field_name").eq(fieldName).f("structure_idx").eq(structureIdx).f("track_link_error").eq(true).asList();
        return this.checkAgainstCache(list);
    }
    
    // root-specific methods
    
    private Query<StructureObject> getRootQuery(String fieldName, int timePoint) {
        if (timePoint<0) return super.getQuery().f("field_name").eq(fieldName).f("structure_idx").eq(-1).sort("time_point");
        else return super.getQuery().f("field_name").eq(fieldName).f("time_point").eq(timePoint).f("structure_idx").eq(-1);
    }
    /*private ObjectId getRootId(String fieldName, int timePoint) {
        Query<StructureObject> q = getRootQuery(fieldName, timePoint);
        q.setReturnedFields("_id");
        return q.get().id;
    }*/
    
    public StructureObject getRoot(String fieldName, int timePoint) {
        return this.checkAgainstCache(getRootQuery(fieldName, timePoint).get());
    }
    
    public ArrayList<StructureObject> getRoots(String fieldName) {
        return this.checkAgainstCache(getRootQuery(fieldName, -1).asList());
    }
}
