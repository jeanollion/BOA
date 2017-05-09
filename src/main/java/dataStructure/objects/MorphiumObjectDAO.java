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
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;
import core.Processor;
import dataStructure.configuration.*;
import dataStructure.configuration.Experiment;
import dataStructure.objects.StructureObject;
import static dataStructure.objects.StructureObject.logger;
import de.caluga.morphium.async.AsyncOperationCallback;
import de.caluga.morphium.async.AsyncOperationType;
import de.caluga.morphium.query.Query;
import de.caluga.morphium.query.QueryImpl;
import de.caluga.morphium.writer.MorphiumWriterImpl;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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
    MorphiumMeasurementsDAO measurementsDAO;
    ConcurrentHashMap<ObjectId, StructureObject> idCache;
    public List<StructureObject> roots;
    public final String fieldName, collectionName;
    public MorphiumObjectDAO(MorphiumMasterDAO masterDAO, String fieldName) {
        this.masterDAO=masterDAO;
        this.fieldName=fieldName;
        this.collectionName=getCollectionName(fieldName);
        masterDAO.m.ensureIndicesFor(StructureObject.class, collectionName);
        idCache = new ConcurrentHashMap<ObjectId, StructureObject>();
        //rootArray = new StructureObject[masterDAO.getExperiment().getMicroscopyField(positionName).getTimePointNumber(false)];
        measurementsDAO = new MorphiumMeasurementsDAO(masterDAO, fieldName);
    }
    
    public static String getCollectionName(String name) {
        return "objects_"+name;
    }
    
    public String getPositionName() {
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
        return getQuery().f("parent_id").eq(parent.getId()).f("structure_idx").eq(structureIdx);
    }
    @Override 
    public StructureObject getById(ObjectId pthId, int structureIdx, int frame, ObjectId id) {
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
    
    @Override
    public void clearCache() {
        this.idCache.clear();
        if (roots!=null) {
            roots.clear();
            roots=null;
        }
    }
    
    protected ArrayList<StructureObject> checkAgainstCache(Collection<StructureObject> list) {
        ArrayList<StructureObject> res= new ArrayList<StructureObject>(list.size());
        for (StructureObject o : list) res.add(checkAgainstCache(o));
        return res;
    }
    @Override
    public List<StructureObject> getChildren(StructureObject parent, int structureIdx) {
        List<StructureObject> list = this.getChildrenQuery(parent, structureIdx).sort("idx").asList();
        list = checkAgainstCache(list);
        //Collections.sort(list);
        return list;
    }
    @Override
    public void setAllChildren(List<StructureObject> parentTrack, int childStructureIdx) {
        for (StructureObject p : parentTrack) p.setChildren(getChildren(p, childStructureIdx), childStructureIdx);
    }
    @Override 
    public void deleteChildren(Collection<StructureObject> parents, int structureIdx) {
        deleteChildren(StructureObjectUtils.getIdList(parents), structureIdx, true);
        for (StructureObject p : parents) p.setChildren(null, structureIdx);
    }
    protected void deleteChildren(Collection<ObjectId> parentIds, int structureIdx, boolean alsoDeleteDirectChildren) {
        long t0 = System.currentTimeMillis();
        // need to retrieve ids in case there are measurements to delete
        DBObject query = QueryBuilder.start("parent_id").in(parentIds).put("structure_idx").is(structureIdx).get();
        ArrayList<Integer> directChildren = alsoDeleteDirectChildren ? this.getExperiment().getAllDirectChildStructures(structureIdx) : null;
        DBCursor cur = masterDAO.m.getDatabase().getCollection(collectionName).find(query, new BasicDBObject("_id", 1).append("measurements_id", 1));
        List<ObjectId> childrenIds = new ArrayList<ObjectId>();
        while (cur.hasNext()) {
            DBObject o = cur.next();
            childrenIds.add((ObjectId)o.get("_id"));
        }
        cur.close();
        masterDAO.m.getDatabase().getCollection(collectionName).remove(QueryBuilder.start("_id").in(childrenIds).get(), WriteConcern.ACKNOWLEDGED);
        long t1 = System.currentTimeMillis();
        logger.debug("delete {} objects of structure: {} from {} parents in : {}", childrenIds.size(), structureIdx, parentIds.size(), t1-t0);
        measurementsDAO.delete(childrenIds);
        if (alsoDeleteDirectChildren && !directChildren.isEmpty()) {
            for (int childStructure : directChildren) deleteChildren(childrenIds, childStructure, true);
        }
    }
    
    public void deleteChildren(final StructureObject parent, int structureIdx) {
        if (parent == null) return;
        ArrayList<Integer> directChildren = this.getExperiment().getAllDirectChildStructures(structureIdx);
        // delete measurements
        List<StructureObject> children=null;
        Query<StructureObject> q=null;
        if (parent.hasChildren(structureIdx)) {
            children = parent.getChildren(structureIdx);
            parent.setChildren(null, structureIdx);
        }
        else if (parent.getId()!=null) { // get only minimal information
            q = getChildrenQuery(parent, structureIdx);
            q.addReturnedField("measurements_id");
            //q.addReturnedField("object_container");
            children = q.asList();
            for (StructureObject o : children) {o.dao=this; o.parent=parent;}
        }
        if (children!=null) {
            for (StructureObject o : children) {
                masterDAO.m.delete(o, collectionName, null);
                measurementsDAO.delete(o.id);
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
    
    public void delete(StructureObject o, boolean deleteChildren, boolean deleteFromParent, boolean relabelSiblings) {
        if (o==null) return;
        if (deleteChildren) for (int s : o.getExperiment().getAllDirectChildStructures(o.getStructureIdx())) deleteChildren(o, s);
        if (o.getId()!=null) {
            //masterDAO.m.delete(o, collectionName, null);
            BasicDBObject db = new BasicDBObject("_id", o.getId());
            //WriteResult r = masterDAO.m.getDatabase().getCollection(collectionName).remove(db);
            WriteResult r =  masterDAO.m.getDatabase().getCollection(collectionName).remove(db, WriteConcern.ACKNOWLEDGED);
            if (masterDAO.m.getDatabase().getCollection(collectionName).findOne(db)!=null) logger.debug("undeletedObject: {}, write result: {}", o, r);
            idCache.remove(o.getId());
        }
        measurementsDAO.delete(o.id);
        if (deleteFromParent) {
            if (o.getParent().getChildren(o.getStructureIdx()).remove(o) && relabelSiblings) {
                List<StructureObject> modified = new ArrayList<StructureObject>(o.getParent().getChildren(o.getStructureIdx()).size());
                o.getParent().relabelChildren(o.getStructureIdx(), modified);
                Utils.removeDuplicates(modified, false);
                for (StructureObject m : modified) set(m, "idx", m.getIdx());
                //store(modified, true);
            }
        }
        //o.deleteMask();
    }
    
    protected void set(StructureObject o, String field, Object value) {
        masterDAO.m.set(o, collectionName, "idx", value, false, false, null);
    }
    
    public void delete(Collection<StructureObject> list, final boolean deleteChildren, boolean deleteFromParent, boolean relabelSiblings) {
        long t0=System.currentTimeMillis();
        masterDAO.m.getDatabase().getCollection(collectionName).remove(QueryBuilder.start("_id").in(StructureObjectUtils.getIdList(list)).get(), WriteConcern.ACKNOWLEDGED);
        getMeasurementsDAO().delete(StructureObjectUtils.getMeasurementIdList(list));
        if (deleteChildren) {
            Map<Integer, List<StructureObject>> objectsByStructure = StructureObjectUtils.splitByStructureIdx(list);
            for (int s : objectsByStructure.keySet()) {
                List<StructureObject> objects = objectsByStructure.get(s);
                for (int sChild : getExperiment().getAllDirectChildStructures(s)) {
                    deleteChildren(StructureObjectUtils.getIdList(objects), sChild, true);
                }
            }
        }
        if (deleteFromParent && relabelSiblings) {
            Map<Integer, List<StructureObject>> objectsByStructure = StructureObjectUtils.splitByStructureIdx(list);
            for (int sIdx : objectsByStructure.keySet()) {
                if (sIdx==-1) continue; // no parents
                List<StructureObject> l = objectsByStructure.get(sIdx);
                Set<StructureObject> parents = new HashSet<StructureObject>();
                for (StructureObject o : l) {
                    if (o.getParent().getChildren(sIdx).remove(o)) {
                        parents.add(o.getParent());
                    }
                }
                //logger.debug("number of parents with delete object from structure: {} = {}", sIdx, parents.size());
                List<StructureObject> relabeled = new ArrayList<StructureObject>();
                for (StructureObject p : parents) {
                    p.relabelChildren(sIdx, relabeled);
                }
                Utils.removeDuplicates(relabeled, false);
                for (StructureObject o : relabeled) set(o, "idx", o.getIdx());
                //store(modified, true);
            }
        } else if (deleteFromParent) {
            for (StructureObject o : list) {
                if (o.getParent()!=null) o.getParent().getChildren(o.getStructureIdx()).remove(o);
            }
        }
        long t1=System.currentTimeMillis();
        logger.debug("{} objects deleted in : {}ms", list.size(), t1-t0);
    }
    
    @Override
    public void store(StructureObject object, boolean updateTrackAttributes) {
        object.dao=this;
        object.updateObjectContainer();
        if (object.hasMeasurementModifications()) this.upsertMeasurement(object);
        if (updateTrackAttributes) {
            object.getParentTrackHeadId();
            object.getTrackHeadId();
            object.getPrevious();
            object.getNext();
        }
        if (object.getParent()!=null && object.getParent().id==null) {
            logger.error("parent unstored for object: {}, parent: {}", object, object.getParent());
            throw new Error("Parent unstored object");                
        }
        if (object.previous!=null && object.previous.id==null) {
            logger.error("previous unstored for object: {}, previous: {}", object, object.previous);
            throw new Error("Previous unstored object");
        }
        if (object.next!=null && object.next.id==null) {
            logger.error("next unstored for object: {}, next: {}", object, object.next);
            throw new Error("next unstored object");
        }
        masterDAO.m.storeNoCache(object, collectionName, null);
        idCache.put(object.getId(), object); //thread-safe??
    }
    
    @Override public void store(final Collection<StructureObject> objects, final boolean updateTrackAttributes) {
        /*ThreadRunner.execute(objects, new ThreadAction<StructureObject>() {
            public void run(StructureObject object, int idx, int threadIdx) {
                store(object, updateTrackAttributes);
            }
        });*/
        //for (StructureObject o : objects) store(object, updateTrackAttributes);
        storeSequentially(objects, updateTrackAttributes);
    }
    
    public void storeSequentially(final Collection<StructureObject> objects, final boolean updateTrackAttributes) {
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
    
    
    public List<StructureObject> getTrackHeads(StructureObject parentTrack, int structureIdx) {
        if (parentTrack==null) return new ArrayList<StructureObject>(0);
        Query<StructureObject> q = getQuery().f("is_track_head").eq(true).f("parent_track_head_id").eq(parentTrack.getTrackHeadId()).f("structure_idx").eq(structureIdx).sort("time_point", "idx");
        List<StructureObject> list =  q.asList();
        //logger.debug("track head query: parentTrack: {} structure: {} result length: {}, collectionName: {}, query: {}", parentTrack.getTrackHeadId(), structureIdx, list.size(), collectionName, q.toQueryObject());
        List<StructureObject> res = this.checkAgainstCache(list);
        logger.debug("getTrackHeads from TrackHead {} & Structure: {}, found: {}", parentTrack, structureIdx, res.size());
        return res;
    }
    
    public List<StructureObject> getTrack(StructureObject trackHead) {
        /*List<StructureObject> list =  getQuery().f("structure_idx").eq(trackHead.getStructureIdx()).f("track_head_id").eq(trackHead.getTrackHeadId()).sort("time_point").asList();
        if (list.isEmpty()) list.add(trackHead);
        //else if (list.get(0).id!=trackHead.id) list.add(0, trackHead); // track_head_id is not updated for trackHead
        ArrayList<StructureObject> res  = checkAgainstCache(list);
        setTrackLinks(res);
        //logger.debug("get track: from head: {}, number of objects {}", trackHead, res.size());
        return res;*/
        List<StructureObject> list = new ArrayList<StructureObject>();
        StructureObject o = trackHead;
        while(o!=null) {
            if (o.getTrackHead()!=trackHead) break;
            list.add(o);
            o = o.getNext();
        }
        return list;
    }
    
    protected static void setTrackLinks(ArrayList<StructureObject> track) {
        if (track.isEmpty()) return;
        StructureObject trackHead = track.get(0).getTrackHead();
        StructureObject prev = null;
        for (StructureObject o : track) {
            o.trackHead=trackHead;
            if (prev!=null) {
                o.setPrevious(prev);
                prev.setNext(o);
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
        if (timePoint<0) throw new IllegalArgumentException("TimePoint should be >=0");
        else return getQuery().f("time_point").eq(timePoint).f("structure_idx").eq(-1);
    }
    /*private ObjectId getRootId(String positionName, int timePoint) {
        Query<StructureObject> q = getRootQuery(positionName, timePoint);
        q.setReturnedFields("_id");
        return q.get().id;
    }*/
    
    @Override public StructureObject getRoot(int timePoint) {
        /*if (timePoint%100==0) {
            long t0 = System.currentTimeMillis();
            for (int i = 0; i<100; ++i) getRootQuery(timePoint).get();
            long t1 = System.currentTimeMillis();
            for (int i = 0; i<99; ++i) {
                Query<StructureObject> q = getRootQuery(timePoint);
                q.addReturnedField("_id");
                StructureObject r = q.get();
            }
            Query<StructureObject> q = getRootQuery(timePoint);
            q.addReturnedField("_id");
            StructureObject r = q.get();
            long t2 = System.currentTimeMillis();
            for (int i = 0; i<100; ++i) {
                StructureObject o = idCache.get(r.id);
            }
            long t3 = System.currentTimeMillis();
            StructureObject res;
            for (int i = 0; i<100; ++i) {
            for (StructureObject o : idCache.values()) {
                if (o.getStructureIdx()==-1 && o.getTimePoint()==timePoint) {
                    res = o;
                    break;
                }
            }
            }   
            long t4 = System.currentTimeMillis();
            logger.debug("tp: {}, get entire object: {}, idOnly: {} (id: {}) from cache: {}, loop cache: {}", timePoint, t1-t0, t2-t1, r.id, t3-t2, t4-t3);
        }
        if (rootArray[timePoint]==null) rootArray[timePoint] = 
        return rootArray[timePoint];*/
        return checkAgainstCache(getRootQuery(timePoint).get());
    }
    
    @Override public List<StructureObject> getRoots() {
        if (roots==null) {
            synchronized(this) {
                if (roots==null) {
                    ArrayList<StructureObject> res = checkAgainstCache(getRootQuery().asList());
                    setTrackLinks(res);
                    roots = res;
                    if (masterDAO.getExperiment()!=null && masterDAO.getExperiment().getPosition(fieldName)!=null) {
                        if (res.size()>0 && res.size()!=masterDAO.getExperiment().getPosition(fieldName).getTimePointNumber(false)) logger.error("Position: {} wrong root number: {} instead of {}", fieldName, res.size(), masterDAO.getExperiment().getPosition(fieldName).getTimePointNumber(false));
                    }
                }
            }
        }
        return roots;
    }
    
    @Override public void setRoos(List<StructureObject> roots) {
        this.roots = roots;
    }
    // measurement-specific methds

    
    @Override public void upsertMeasurements(Collection<StructureObject> objects) {
        if (objects.isEmpty()) return;
        long t1 = System.currentTimeMillis();
        if (!(objects instanceof Set)) Utils.removeDuplicates(objects, false);
        ThreadRunner.execute(objects, new ThreadRunner.ThreadAction<StructureObject>() {
            public void run(StructureObject object, int idx, int threadIdx) {
                upsertMeasurement(object);
            }
        });
        long t2 = System.currentTimeMillis();
        Processor.logger.debug("measurements of field: {}: upsert time: {} ({} objects)", getPositionName(), t2-t1, objects.size());
    }
    
    @Override public void upsertMeasurement(StructureObject o) {
        o.getMeasurements().updateObjectProperties(o);
        //if (o.getMeasurements().id!=null) measurementsDAO.delete(o.getMeasurements());
        try {
            this.measurementsDAO.store(o.getMeasurements());// toDO -> partial update if already an ID
        } catch (Exception e) {
            logger.debug("Error while storing measurement: {}", e);
            Measurements m = o.getMeasurements();
            logger.debug("Object: {}, meas: {}, {}, {}, {}", o, m.positionName, m.id, m.indices, m.values);
            return;
        }
        o.getMeasurements().modifications=false;
    }
    
    
    public MorphiumMeasurementsDAO getMeasurementsDAO() {return this.measurementsDAO;}
    @Override
    public List<Measurements> getMeasurements(int structureIdx, String... measurements) {
        return measurementsDAO.getMeasurements(structureIdx, measurements);
    }
    @Override
    public Measurements getMeasurements(StructureObject o) {
        return measurementsDAO.getObject(o.id);
    }
    @Override
    public void deleteAllMeasurements() {
        measurementsDAO.deleteAllObjects();
        for (StructureObject o : this.idCache.values()) { // TODO: need to update measurementId field in all objects?
            o.measurements=null;
        }
    }

    @Override
    public MasterDAO getMasterDAO() {
        return masterDAO;
    }
}
