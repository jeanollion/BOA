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

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import dataStructure.configuration.*;
import dataStructure.configuration.Experiment;
import dataStructure.objects.StructureObject;
import static dataStructure.objects.StructureObject.logger;
import de.caluga.morphium.async.AsyncOperationCallback;
import de.caluga.morphium.async.AsyncOperationType;
import de.caluga.morphium.query.Query;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.SwingUtilities;
import org.bson.types.ObjectId;
import utils.MorphiumUtils;
import utils.ThreadRunner;
import utils.ThreadRunner.ThreadAction;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class MorphiumObjectDAO implements ObjectDAO {
    final MorphiumMasterDAO masterDAO;
    MeasurementsDAO measurementsDAO;
    ConcurrentHashMap<ObjectId, StructureObject> idCache;
    public final String fieldName, collectionName;
    public MorphiumObjectDAO(MorphiumMasterDAO masterDAO, String fieldName) {
        this.masterDAO=masterDAO;
        this.fieldName=fieldName;
        this.collectionName="objects_"+fieldName;
        masterDAO.m.ensureIndicesFor(StructureObject.class, collectionName);
        idCache = new ConcurrentHashMap<ObjectId, StructureObject>();
        measurementsDAO = new MeasurementsDAO(masterDAO, fieldName);
    }
    
    public String getFieldName() {
        return fieldName;
    }
    
    public Experiment getExperiment() {
        return masterDAO.getExperiment();
    }
    
    protected Query<StructureObject> getQuery() {
        Query<StructureObject> res =  masterDAO.m.createQueryFor(StructureObject.class); 
        res.setCollectionName(collectionName);
        return res;
    }
    
    protected Query<StructureObject> getChildrenQuery(StructureObject parent, int structureIdx) {
        // voir si la query est optimisée pour index composé
        return getQuery().f("parent").eq(parent.getId()).f("structure_idx").eq(structureIdx);
    }
    
    public StructureObject getById(ObjectId id) {
        if (id==null) return null;
        StructureObject res = idCache.get(id);
        if (res==null)  {
            res= getQuery().getById(id);
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
    
    public void clearCache() {
        this.idCache.clear();
    }
    
    protected ArrayList<StructureObject> checkAgainstCache(List<StructureObject> list) {
        ArrayList<StructureObject> res= new ArrayList<StructureObject>(list.size());
        for (StructureObject o : list) res.add(checkAgainstCache(o));
        return res;
    }
    
    public ArrayList<StructureObject> getChildren(StructureObject parent, int structureIdx) {
        List<StructureObject> list = this.getChildrenQuery(parent, structureIdx).sort("idx").asList();
        return checkAgainstCache(list);
    }
    
    public void deleteChildren(final StructureObject parent, int structureIdx) {
        if (parent == null) return;
        ArrayList<Integer> directChildren = this.getExperiment().getAllDirectChildStructures(structureIdx);
        // delete measurements
        List<StructureObject> children=null;
        Query<StructureObject> q=null;
        if (parent.hasChildren(structureIdx)) children = parent.getChildren(structureIdx);
        else if (parent.getId()!=null) { // get only minimal information
            q = getChildrenQuery(parent, structureIdx);
            q.addReturnedField("measurements_id");
            //q.addReturnedField("object_container");
            children = q.asList();
            for (StructureObject o : children) {o.dao=this; o.parent=parent;}
        }
        if (children!=null) {
            ThreadRunner.execute(children, new ThreadAction<StructureObject>() {
                public void run(StructureObject o, int idx) {
                    masterDAO.m.delete(o, collectionName, null);
                    //logger.debug("delete {}", o.getId());
                    if (o.measurementsId!=null) measurementsDAO.delete(o.measurementsId);
                    //if (o.objectContainer!=null) o.objectContainer.deleteObject();    
                }
            });
            for (StructureObject o : children) {
                this.idCache.remove(o.getId()); // delete in cache
                for (int s : directChildren) deleteChildren(o, s); // also delete all direct chilren (recursive call)
            }
        }
        //logger.debug("delete {} children. direct structures to delete: {}", children==null?0:children.size(), directChildren);
    }
    
    private static ArrayList<Integer> listAllStructureAndChildrenStructure(Experiment xp, int... structures) {
        ArrayList<Integer> toDelete = new ArrayList<Integer>();
        for (int s : structures) {
            toDelete.add(s);
            for (int subS : xp.getAllChildStructures(s)) toDelete.add(subS);
        }
        Utils.removeDuplicates(toDelete, false);
        Collections.sort(toDelete, new Comparator<Integer>() {
            public int compare(Integer arg0, Integer arg1) {
                return Integer.compare(arg1, arg0); // reverse order to be able to access parents if needed
            }
        });
        return toDelete;
    }
    
    public void deleteObjectsByStructureIdx(int... structures) {
        if (structures.length==0) return;
        ArrayList<Integer> toDelete = listAllStructureAndChildrenStructure(getExperiment(), structures);
        for (int s : toDelete ) {
            //getQuery().f("structure_idx").eq(s).delete();
            masterDAO.getMorphium().getDatabase().getCollection(collectionName).remove( new BasicDBObject("structure_idx", s));
            /*q.addReturnedField("measurements_id");
            //q.addReturnedField("object_container");
            List<StructureObject> children = q.asList();
            final MorphiumObjectDAO instance=this;
            ThreadRunner.execute(children, new ThreadAction<StructureObject>() {
                public void run(StructureObject o, int idx) {
                    o.dao=instance;
                    if (o.measurementsId!=null) measurementsDAO.delete(o.measurementsId);
                    //if (o.objectContainer!=null) o.objectContainer.deleteObject(); // in case object is contained in other collection
                }
            });
            for (StructureObject o : children) this.idCache.remove(o.getId()); // delete in cache:
            */
            this.measurementsDAO.deleteByStructureIdx(s);
            Iterator<Entry<ObjectId, StructureObject>> it = idCache.entrySet().iterator();
            while(it.hasNext()) {
                Entry<ObjectId, StructureObject> e = it.next();
                if (e.getValue().getStructureIdx()==s) it.remove();
            }
        }
    }
    
    public void deleteAllObjects() {
        //masterDAO.m.clearCollection(StructureObject.class, collectionName);
        masterDAO.m.getDatabase().getCollection(collectionName).drop();
        idCache.clear();
        measurementsDAO.deleteAllObjects();
    }
    
    public void delete(StructureObject o, boolean deleteChildren) {
        if (o==null) return;
        if (o.getId()==null) return;
        if (deleteChildren) for (int s : o.getExperiment().getAllDirectChildStructures(o.getStructureIdx())) deleteChildren(o, s);
        if (o.getId()!=null) {
            masterDAO.m.delete(o, collectionName, null);
            idCache.remove(o.getId());
        }
        measurementsDAO.delete(o.getMeasurements());
        //o.deleteMask();
    }
    
    public void delete(List<StructureObject> list, final boolean deleteChildren) {
        ThreadRunner.execute(list, new ThreadAction<StructureObject>() {
            public void run(StructureObject o, int idx) {
                delete(o, deleteChildren);
            }
        });
    }
    
    public void store(StructureObject object, boolean updateTrackAttributes) {
        object.updateObjectContainer();
        object.updateMeasurementsIfNecessary();
        if (object.getParent()!=null && object.getParent().id==null) {
            logger.error("parent unstored for object: {}, parent: {}", object, object.getParent());
            throw new Error("Parent unstored object");                
        }
        if (updateTrackAttributes) {
            object.getParentTrackHeadId();
            object.getTrackHeadId();
            if (object.getPrevious()!=null && object.getPrevious().id==null) {
                logger.error("previous unstored for object: {}, previous: {}", object, object.getPrevious());
                throw new Error("Previous unstored object");
            }
        }
        
        masterDAO.m.storeNoCache(object, collectionName, null);
        idCache.put(object.getId(), object); //thread-safe??
    }
    
    public void store(final List<StructureObject> objects, final boolean updateTrackAttributes) {
        ThreadRunner.execute(objects, new ThreadAction<StructureObject>() {
            public void run(StructureObject object, int idx) {
                store(object, updateTrackAttributes);
            }
        });
    }
    
    public void storeSequentially(final List<StructureObject> objects, final boolean updateTrackAttributes) {
        for (StructureObject o : objects) store(o, updateTrackAttributes);
    }
    
    // track-specific methods
    
    /*public void updateParent(final List<StructureObject> objects) {
        // TODO update only parent field
        morphium.storeLater(objects, new AsyncOperationCallback<StructureObject>() {
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
     * Set and storeLater trackHeadId & parentTrackHeadId attributes; next and previous are not concerned by this method
     * @param track list of objects. All objects of a given track should be present, sorted by incresing timepoint. objects from several tracks can be present;
     */
    public void updateTrackHeadFields(final List<? extends StructureObject> track) {
        if (track==null) return;
        //MorphiumUtils.waitForWrites(morphium);
        MorphiumObjectDAO.this.setTrackHeadIds(track);
        AsyncOperationCallback cb = null;
        for (StructureObject o : track) {
            if (o.getParentTrackHeadId()!=null && o.getTrackHeadId()!=null) masterDAO.m.updateUsingFields(o, collectionName, cb, "parent_track_head_id", "track_head_id");
            else if (o.getParentTrackHeadId()!=null) masterDAO.m.updateUsingFields(o, collectionName, cb, "parent_track_head_id");
            else if (o.getTrackHeadId()!=null) masterDAO.m.updateUsingFields(o, collectionName, cb, "track_head_id");
            //morphium.storeLater(o);
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
                
                /*morphium.storeLater(objects, new AsyncOperationCallback<StructureObject>() {
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
    
    
    // TODO for faster retrieve:  retrieve only: timepoint, idx, structureIdx, parent + set dao & set trackHead as this -> ATTENTION object retrieved incompletely..
    public ArrayList<StructureObject> getTrackHeads(StructureObject parentTrack, int structureIdx) {
        if (parentTrack==null) return new ArrayList<StructureObject>(0);
        Query<StructureObject> q = getQuery().f("is_track_head").eq(true).f("parent_track_head_id").eq(parentTrack.getTrackHeadId()).f("structure_idx").eq(structureIdx).sort("time_point", "idx");
        List<StructureObject> list =  q.asList();
        //logger.debug("track head query: parentTrack: {} structure: {} result length: {}, collectionName: {}, query: {}", parentTrack.getTrackHeadId(), structureIdx, list.size(), collectionName, q.toQueryObject());
        return this.checkAgainstCache(list);
    }
    
    public ArrayList<StructureObject> getTrack(StructureObject trackHead) {
        List<StructureObject> list =  getQuery().f("structure_idx").eq(trackHead.getStructureIdx()).f("track_head_id").eq(trackHead.getTrackHeadId()).sort("time_point").asList();
        if (list.isEmpty()) list.add(trackHead);
        else if (list.get(0)!=trackHead) list.add(0, trackHead); // track_head_id is not updated for trackHead
        ArrayList<StructureObject> res  = checkAgainstCache(list);
        setTrackLinks(res);
        //logger.debug("get track: from head: {}, number of objects {}", trackHead, res.size());
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
    
    /*public ArrayList<StructureObject> getTrackErrors(StructureObject parentTrack, int structureIdx) {
        List<StructureObject> list =  getQuery().f("parent_track_head_id").eq(parentTrack.getTrackHeadId()).f("structure_idx").eq(structureIdx).f("track_link_error").eq(true).asList();
        return this.checkAgainstCache(list);
    }
    
    public ArrayList<StructureObject> getTrackErrors(int structureIdx) {
        List<StructureObject> list =  getQuery().f("structure_idx").eq(structureIdx).f("track_link_error").eq(true).asList();
        return this.checkAgainstCache(list);
    }*/

    // root-specific methods
    
    protected Query<StructureObject> getRootQuery() {
        return getQuery().f("structure_idx").eq(-1).sort("time_point");
    }
    
    protected Query<StructureObject> getRootQuery(int timePoint) {
        if (timePoint<0) return getRootQuery();
        else return getQuery().f("time_point").eq(timePoint).f("structure_idx").eq(-1);
    }
    /*private ObjectId getRootId(String fieldName, int timePoint) {
        Query<StructureObject> q = getRootQuery(fieldName, timePoint);
        q.setReturnedFields("_id");
        return q.get().id;
    }*/
    
    public StructureObject getRoot(int timePoint) {
        return this.checkAgainstCache(getRootQuery(timePoint).get());
    }
    
    public ArrayList<StructureObject> getRoots() {
        ArrayList<StructureObject> res = this.checkAgainstCache(getRootQuery().asList());
        setTrackLinks(res);
        return res;
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
    
    public void upsertMeasurement(StructureObject o) {
        o.getMeasurements().updateObjectProperties(o);
        //if (o.getMeasurements().id!=null) measurementsDAO.delete(o.getMeasurements());
        this.measurementsDAO.store(o.getMeasurements()); // toDO -> partial update if already an ID
        
        
        //logger.debug("store meas: id: {}, id in object: {}: {}", o.measurements.id, o, o.measurementsId);
        if (!o.getMeasurements().getId().equals(o.measurementsId)) {
            o.measurementsId=o.getMeasurements().getId();
            
            // when morphium bug solved -> update
            DBObject find = new BasicDBObject("_id", o.getId());
            DBObject update = new BasicDBObject("measurements_id", o.measurementsId);
            update = new BasicDBObject("$set", update);
            this.masterDAO.m.getDatabase().getCollection(collectionName).update(find, update, false, false);
            /*AsyncOperationCallback cb = null;
            masterDAO.m.updateUsingFields(o, collectionName, cb, "measurements_id");*/
        
        }
    }
    
    
    public MeasurementsDAO getMeasurementsDAO() {return this.measurementsDAO;}

    public List<Measurements> getMeasurements(int structureIdx, String... measurements) {
        return measurementsDAO.getMeasurements(structureIdx, measurements);
    }
    
    
}
