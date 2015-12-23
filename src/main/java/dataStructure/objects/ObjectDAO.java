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
import dataStructure.containers.ObjectContainerVoxelsDB;
import dataStructure.objects.StructureObject;
import static dataStructure.objects.StructureObject.logger;
import de.caluga.morphium.DAO;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.async.AsyncOperationCallback;
import de.caluga.morphium.async.AsyncOperationType;
import de.caluga.morphium.query.Query;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.SwingUtilities;
import org.bson.types.ObjectId;
import utils.MorphiumUtils;
import utils.ThreadRunner;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class ObjectDAO extends DAO<StructureObject>{
    Morphium morphium;
    ExperimentDAO xpDAO;
    MeasurementsDAO measurementsDAO;
    RegionDAO regionDAO;
    ConcurrentHashMap<ObjectId, StructureObject> idCache;
    final ObjectStoreAgent agent;
    
    public ObjectDAO(Morphium morphium, ExperimentDAO xpDAO) {
        super(morphium, StructureObject.class);
        morphium.ensureIndicesFor(StructureObject.class);
        this.morphium=morphium;
        this.xpDAO=xpDAO;
        idCache = new ConcurrentHashMap<ObjectId, StructureObject>();
        agent = new ObjectStoreAgent(this);
        measurementsDAO = new MeasurementsDAO(morphium);
        regionDAO = new RegionDAO(morphium);
    }
    
    protected Query<StructureObject> getQuery(ObjectId parentId, int structureIdx) {
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
        o.dao=this;
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
    
    public void clearCacheLater(String fieldName) {
        agent.clearCache(fieldName);
    }
    
    void clearCacheNow(String fieldName) {
        Iterator<Entry<ObjectId, StructureObject>> it = idCache.entrySet().iterator();
        while(it.hasNext()) {
            Entry<ObjectId, StructureObject> e = it.next();
            if (e.getValue().fieldName.equals(fieldName)) idCache.remove(e.getKey());
        }
    }
    
    public void clearCache() {
        this.idCache.clear();
    }
    
    protected ArrayList<StructureObject> checkAgainstCache(List<StructureObject> list) {
        ArrayList<StructureObject> res= new ArrayList<StructureObject>(list.size());
        for (StructureObject o : list) res.add(checkAgainstCache(o));
        return res;
    }
    
    public ArrayList<StructureObject> getObjects(ObjectId parentId, int structureIdx) {
        List<StructureObject> list = this.getQuery(parentId, structureIdx).sort("idx").asList();
        return checkAgainstCache(list);
    }
    
    public void deleteChildren(StructureObject parent, int structureIdx) {
        this.waiteForWrites();
        ArrayList<Integer> directChildren = this.getExperiment().getAllDirectChildren(structureIdx);
        // delete measurements
        List<StructureObject> children=null;
        if (parent !=null && parent.hasChildren(structureIdx)) children = parent.getChildren(structureIdx);
        else if (parent!=null && parent.getId()!=null) { // get only minimal information
            Query<StructureObject> q = getQuery(parent.getId(), structureIdx);
            q.addReturnedField("measurements_id");
            q.addReturnedField("object_container");
            children = q.asList();
        }
        if (children!=null) {
            for (StructureObject o : children) {
                logger.debug("delete children: id {}, mes id {}", o.id, o.measurementsId);
                o.dao=this; // in case it was retrieved from this method
                o.parent=parent;
                if (o.measurementsId!=null) measurementsDAO.delete(o.measurementsId);
                if (o.objectContainer!=null && o.objectContainer instanceof ObjectContainerVoxelsDB) o.objectContainer.deleteObject();
                for (int s : directChildren) deleteChildren(o, s); // also delete all direct chilren
                this.idCache.remove(o.getId()); // delete in cache:
            }
        }
        if (parent.getId()!=null) morphium.delete(getQuery(parent.getId(), structureIdx));
        // also delete in cache: 
        /*Iterator<Entry<ObjectId, StructureObject>> it = idCache.entrySet().iterator();
        while(it.hasNext()) {
            StructureObject cur = it.next().getValue();
            if (cur.getStructureIdx()==structureIdx && parent.getId().equals(cur.getParent().getId())) it.remove();
        }*/
        // delete in ImageDAO
        this.xpDAO.getExperiment().getImageDAO().deleteChildren(parent, structureIdx);
    }
    
    public void deleteObjectsFromFieldByStructure(String fieldName, int... structures) {
        ArrayList<Integer> toDelete = new ArrayList<Integer>();
        for (int s : structures) toDelete.addAll(this.getExperiment().getAllDirectChildren(s));
        Utils.removeDuplicates(toDelete, false);
        Collections.sort(toDelete, new Comparator<Integer>() {
            public int compare(Integer arg0, Integer arg1) {
                return Integer.compare(arg1, arg0); // reverse order
            }
        }); 
        for (int s : toDelete ) {
            Query<StructureObject> q = super.getQuery().f("field_name").eq(fieldName).f("structure_idx").eq(s);
            q.addReturnedField("measurements_id");
            q.addReturnedField("object_container");
            q.addReturnedField("parent"); // for objects stored in localFileSystemDAO
            List<StructureObject> children = q.asList();
            for (StructureObject o : children) {
                logger.debug("delete children: id {}, mes id {}", o.id, o.measurementsId);
                o.dao=this; // in case it was retrieved from this method
                if (o.measurementsId!=null) measurementsDAO.delete(o.measurementsId);
                if (o.objectContainer!=null && o.objectContainer instanceof ObjectContainerVoxelsDB) o.objectContainer.deleteObject();
                this.idCache.remove(o.getId()); // delete in cache:
            }
        }
    }
    
    public void deleteObjectsFromField(String fieldName) {
        this.waiteForWrites();
        morphium.delete(super.getQuery().f("field_name").eq(fieldName));
        // delete in cache: 
        Iterator<Entry<ObjectId, StructureObject>> it = idCache.entrySet().iterator();
        while(it.hasNext()) if (it.next().getValue().fieldName.equals(fieldName)) it.remove();
        // delete in ImageDAO
        this.xpDAO.getExperiment().getImageDAO().deleteFieldMasks(xpDAO.getExperiment(), fieldName);
        regionDAO.deleteObjectsFromField(fieldName);
        //delete measurements
        measurementsDAO.deleteObjectsFromField(fieldName);
    }
    
    public void deleteAllObjects() {
        this.waiteForWrites(); //TODO interrupt
        morphium.clearCollection(StructureObject.class);
        idCache.clear();
        // delete in ImageDAO
        for (String fieldName : xpDAO.getExperiment().getFieldsAsString()) {
            this.xpDAO.getExperiment().getImageDAO().deleteFieldMasks(xpDAO.getExperiment(), fieldName);
        }
        regionDAO.deleteAllObjects();
        // delete measurements
        measurementsDAO.deleteAllObjects();
    }
    
    public void delete(StructureObject o, boolean deleteChildren) {
        if (o==null) return;
        if (o.getId()==null) this.waiteForWrites(); 
        if (deleteChildren) for (int s : o.getExperiment().getChildStructures(o.getStructureIdx())) this.deleteChildren(o, s);
        if (o.getId()!=null) {
            morphium.delete(o);
            idCache.remove(o.getId());
            //logger.debug("deleting: {}", o);
            
        }
        measurementsDAO.delete(o.getMeasurements());
        o.deleteMask();
    }
    
    public void delete(ArrayList<StructureObject> list, boolean deleteChildren) {
        for (StructureObject o : list ) delete(o, deleteChildren); // TODO see if morphium has optimized batched operation, or do on another thread;
    }
    
    public void store(StructureObject object) {
        object.updateObjectContainer();
        object.updateMeasurementsIfNecessary();
        morphium.store(object);
        idCache.put(object.getId(), object);
    }
    
    public void store(boolean updateTrackAttributes, StructureObject... object) {
        if (object==null) return;
        if (object.length==0) return;
        if (object.length==1) store(object[0]);
        else store(Arrays.asList(object), updateTrackAttributes, false);
    }
    public void waiteForWrites() {
        //logger.debug("wait for writes...");
        agent.join();
        //logger.debug("wait for writes done.");
    }
    public void store(final List<StructureObject> objects, final boolean updateTrackAttributes, boolean removeDuplicatesAndSortIfNecessary) {
        if (objects==null || objects.isEmpty()) return;
        if (removeDuplicatesAndSortIfNecessary) {
            Utils.removeDuplicates(objects, false);
            if (updateTrackAttributes) Collections.sort(objects, Utils.getStructureObjectComparator());
        }
        agent.storeObjects(objects, updateTrackAttributes);
        //storeNow(objects, updateTrackAttributes);
    }
    public void storeNow(final List<StructureObject> objects, final boolean updateTrackAttributes) {
        if (objects==null) return;
        
        //logger.debug("calling store metohd: nb of objects: {} updateTrack: {}", objects.size(), updateTrackAttributes);
        
        boolean updateTrackHead = false;
        for (StructureObject o : objects) {
            o.updateObjectContainer();
            o.updateMeasurementsIfNecessary();
            if (updateTrackAttributes) {
                updateTrackHead = o.getTrackHeadId()==null && o.isTrackHead; // getTrackHeadId method should always be called
                o.getParentTrackHeadId();
            }
            if (o.getPrevious()!=null && o.getPrevious().id==null) {
                logger.error("previous unstored: object: idx: {} {} previous: {}",objects.indexOf(o.getPrevious()), o, o.getPrevious());
                throw new Error("Previous unstored object");
                //o.previous=null;
                /*o.isTrackHead=true;
                o.trackHeadId=null;
                o.trackHead=null;
                o.getTrackHeadId();*/
                //store(o.getPrevious());
            }
            if (o.getParent()!=null && o.getParent().id==null) {
                logger.error("parent unstored: object: idx: {} {} parent: {}",objects.indexOf(o.getParent()), o, o.getParent());
                throw new Error("Parent unstored object");                
                //o.parent=null;
                /*o.isTrackHead=true;
                o.trackHeadId=null;
                o.trackHead=null;
                o.getTrackHeadId();*/
                //store(o.getPrevious());
            }
            morphium.store(o);
            idCache.put(o.getId(), o);
            if (updateTrackHead && o.getTrackHeadId()!=null) {
                morphium.updateUsingFields(o, "track_head_id");
                updateTrackHead=false;
            }
        }
        //TODO: only update for trackHeads
        
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
     * {@link ObjectDAO#updateTrackHeadFields(java.util.List) }
     * @param track 
     */
    public void setTrackHeadIds(StructureObject... track) {
        if (track==null) return;
        else if (track.length==0) return;
        else updateTrackHeadFields(Arrays.asList(track));
    }
    
    /**
     * Set trackHeadId & parentTrackHeadId attributes; next and previous are not concerned by this method
     * @param track list of objects. All objects of a given track should be present, sorted by incresing timepoint. objects from several tracks can be present;
     */
    public void setTrackHeadIds(final List<? extends StructureObject> track) {
        if (track==null) return;
        //MorphiumUtils.waitForWrites(morphium);
        for (StructureObject o : track) { 
            o.getParentTrackHeadId(); //sets parentTrackHeadId
            o.getTrackHeadId(); //sets trackHeadId
            /*if (o.getTrackHeadId()==null) {                
                if (!o.isTrackHead && o.getPrevious()!=null) { //for trackHeads -> automoatically set by getTrackHeadId Method
                    o.trackHeadId=o.previous.getTrackHeadId();
                    logger.debug("set track head of {} from previous: {}, trackHeadId: {}", o, o.getPrevious(), o.getTrackHeadId());
                }
            }*/
        }
    }
    
    /**
     * Set and store trackHeadId & parentTrackHeadId attributes; next and previous are not concerned by this method
     * @param track list of objects. All objects of a given track should be present, sorted by incresing timepoint. objects from several tracks can be present;
     */
    public void updateTrackHeadFields(final List<? extends StructureObject> track) {
        if (track==null) return;
        //MorphiumUtils.waitForWrites(morphium);
        ObjectDAO.this.setTrackHeadIds(track);
        for (StructureObject o : track) {
            if (o.getParentTrackHeadId()!=null && o.getTrackHeadId()!=null) morphium.updateUsingFields(o, "parent_track_head_id", "track_head_id");
            else if (o.getParentTrackHeadId()!=null) morphium.updateUsingFields(o, "parent_track_head_id");
            else if (o.getTrackHeadId()!=null) morphium.updateUsingFields(o, "track_head_id");
            //morphium.store(o);
            /*if (o.getTrackFlag()==null) {
                if (o.getParentTrackHeadId()!=null && o.getTrackHeadId()!=null) morphium.updateUsingFields(o, "parent_track_head_id", "track_head_id");
                else if (o.getParentTrackHeadId()!=null) morphium.updateUsingFields(o, "parent_track_head_id");
                else if (o.getTrackHeadId()!=null) morphium.updateUsingFields(o, "track_head_id");
            } else {
                if (o.getParentTrackHeadId()!=null && o.getTrackHeadId()!=null) morphium.updateUsingFields(o, "parent_track_head_id", "track_head_id", "flag");
                else if (o.getParentTrackHeadId()!=null) morphium.updateUsingFields(o, "parent_track_head_id", "flag");
                else if (o.getTrackHeadId()!=null) morphium.updateUsingFields(o, "track_head_id", "flag");
            }
            */
            
            
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
    
    public ArrayList<ArrayList<StructureObject>> getAllTracks(StructureObject parentTrack, int structureIdx) {
        ArrayList<StructureObject> trackHeads = getTrackHeads(parentTrack, structureIdx);
        ArrayList<ArrayList<StructureObject>> res = new ArrayList<ArrayList<StructureObject>>(trackHeads.size());
        for (StructureObject head : trackHeads) res.add(getTrack(head));
        return res;
    }
    
    public ArrayList<StructureObject> getTrack(StructureObject track) {
        List<StructureObject> list =  super.getQuery().f("track_head_id").eq(track.getTrackHeadId()).sort("time_point").asList();
        if (list.isEmpty()) return null;
        ArrayList<StructureObject> res  = checkAgainstCache(list);
        setTrackLinks(res);
        return res;
    }
    
    protected static void setTrackLinks(ArrayList<StructureObject> track) {
        if (track.isEmpty()) return;
        StructureObject trackHead = track.get(0).getTrackHead();
        StructureObject prev = null;
        for (StructureObject o : track) {
            o.trackHead=trackHead;
            if (prev!=null) {
                o.previous=prev;
                prev.next=o;
            }
            prev = o;
        }
    }
    
    public ArrayList<StructureObject> getTrackErrors(StructureObject parentTrack, int structureIdx) {
        List<StructureObject> list =  super.getQuery().f("parent_track_head_id").eq(parentTrack.getTrackHeadId()).f("structure_idx").eq(structureIdx).f("track_link_error").eq(true).asList();
        return this.checkAgainstCache(list);
    }
    
    public ArrayList<StructureObject> getTrackErrors(String fieldName, int structureIdx) {
        List<StructureObject> list =  super.getQuery().f("field_name").eq(fieldName).f("structure_idx").eq(structureIdx).f("track_link_error").eq(true).asList();
        return this.checkAgainstCache(list);
    }

    // measurement-specific methds
    public void upsertMeasurements(List<StructureObject> objects) {
        Utils.removeDuplicates(objects, false);
        //this.agent.upsertMeasurements(objects);
        ThreadRunner.execute(objects, new ThreadRunner.ThreadAction<StructureObject>() {
            public void run(StructureObject object, int idx) {
                upsertMeasurement(object);
            }
        });
    }
    protected void upsertMeasurementsNow(List<StructureObject> objects) {
        for (StructureObject o : objects) upsertMeasurement(o);
    }
    
    protected void upsertMeasurement(StructureObject o) {
        o.getMeasurements().updateObjectProperties(o);
        //if (o.getMeasurements().id!=null) measurementsDAO.delete(o.getMeasurements());
        this.measurementsDAO.store(o.getMeasurements()); // toDO -> partial update if already an ID

        if (!o.getMeasurements().getId().equals(o.measurementsId)) {
            o.measurementsId=o.getMeasurements().getId();
            morphium.updateUsingFields(o, "measurements_id");
        }
    }
    
    
    // root-specific methods
    
    protected Query<StructureObject> getRootQuery(String fieldName) {
        return super.getQuery().f("field_name").eq(fieldName).f("structure_idx").eq(-1).sort("time_point");
    }
    
    protected Query<StructureObject> getRootQuery(String fieldName, int timePoint) {
        if (timePoint<0) return getRootQuery(fieldName);
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
        ArrayList<StructureObject> res = this.checkAgainstCache(getRootQuery(fieldName).asList());
        setTrackLinks(res);
        return res;
    }
    
    public MeasurementsDAO getMeasurementsDAO() {return this.measurementsDAO;}
    
    public RegionDAO getRegionDAO() {return this.regionDAO;}
    
    public Experiment getExperiment() {
        return this.xpDAO.getExperiment();
    }
}
