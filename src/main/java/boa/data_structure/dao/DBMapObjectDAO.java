/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.data_structure.dao;

import boa.configuration.experiment.Experiment;
import boa.data_structure.Measurements;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import java.io.EOFException;
import java.io.File;
import java.io.IOError;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.json.simple.JSONObject;
import org.mapdb.DB;
import org.mapdb.HTreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import boa.utils.DBMapUtils;
import static boa.utils.DBMapUtils.createFileDB;
import static boa.utils.DBMapUtils.getEntrySet;
import static boa.utils.DBMapUtils.getValues;
import boa.utils.HashMapGetCreate;
import boa.utils.JSONUtils;
import static boa.utils.JSONUtils.parse;
import boa.utils.Pair;
import boa.utils.Utils;
import java.util.function.Consumer;

/**
 *
 * @author jollion
 */
public class DBMapObjectDAO implements ObjectDAO {
    public static final Logger logger = LoggerFactory.getLogger(DBMapObjectDAO.class);
    final DBMapMasterDAO mDAO;
    final String positionName;
    //List<StructureObject> rootCache;
    final HashMapGetCreate<Pair<String, Integer>, Map<String, StructureObject>> cache = new HashMapGetCreate<>(new HashMapGetCreate.MapFactory()); // parent trackHead id -> id cache
    final HashMapGetCreate<Pair<String, Integer>, Boolean> allObjectsRetrievedInCache = new HashMapGetCreate<>(p -> false);
    final Map<Pair<String, Integer>, HTreeMap<String, String>> dbMaps = new HashMap<>();
    final String dir;
    final Map<Integer, DB> dbS = new HashMap<>();
    final Map<Integer, Pair<DB, HTreeMap<String, String>>> measurementdbS = new HashMap<>();
    private final boolean readOnly;
    public DBMapObjectDAO(DBMapMasterDAO mDAO, String positionName, String dir, boolean readOnly) {
        this.mDAO=mDAO;
        this.positionName=positionName;
        this.dir = dir+File.separator+positionName+File.separator+"segmented_objects"+File.separator;
        new File(this.dir).mkdirs();
        this.readOnly=readOnly;
    }
    
    @Override
    public MasterDAO getMasterDAO() {
        return mDAO;
    }

    @Override
    public Experiment getExperiment() {
        return mDAO.getExperiment();
    }

    @Override
    public String getPositionName() {
        return positionName;
    }

    
    private String getDBFile(int structureIdx) {
        String res = dir+"objects_"+structureIdx+".db";
        //logger.debug("db file: {}", res);
        return res;
    }
    
    protected DB getDB(int structureIdx) {
        DB res = this.dbS.get(structureIdx);
        if (res==null) {
            synchronized(dbS) {
                if (!dbS.containsKey(structureIdx)) {
                    //logger.debug("creating db: {} (From DAO: {}), readOnly: {}", getDBFile(structureIdx), this.hashCode(), readOnly);
                    try {
                        res = createFileDB(getDBFile(structureIdx), readOnly);
                        dbS.put(structureIdx, res);
                    } catch (org.mapdb.DBException ex) {
                        logger.error("Could not create DB readOnly: "+readOnly, ex);
                        return null;
                    }
                } else {
                    res = dbS.get(structureIdx);
                }
            }
        }
        return res;
    }

    protected HTreeMap<String, String> getDBMap(Pair<String, Integer> key) {
        HTreeMap<String, String> res = this.dbMaps.get(key);
        if (res==null) {
            synchronized(dbMaps) {
                if (dbMaps.containsKey(key)) res=dbMaps.get(key);
                else {
                    DB db = getDB(key.value);
                    if (db!=null) {
                        res = DBMapUtils.createHTreeMap(db, key.key!=null? key.key : "root");
                        if (res!=null || readOnly) dbMaps.put(key, res); // readonly case && not already created -> null
                    }
                }
            }
        }
        return res;
    }
    protected Map<String, StructureObject> getChildren(Pair<String, Integer> key) {
        if (cache.containsKey(key) && allObjectsRetrievedInCache.getOrDefault(key, false)) return cache.get(key);
        else {
            synchronized(this) {
                if (cache.containsKey(key) && allObjectsRetrievedInCache.getOrDefault(key, false)) return cache.get(key);
                HTreeMap<String, String> dbm = getDBMap(key);
                if (cache.containsKey(key) && !cache.get(key).isEmpty()) {
                    long t0 = System.currentTimeMillis();
                    Map<String, StructureObject> objectMap = cache.get(key);
                    Map<String, StructureObject> objectMapToAdd = getEntrySet(dbm).parallelStream()
                            .filter((e) -> (!objectMap.containsKey(e.getKey())))
                            .map((e) -> JSONUtils.parse(StructureObject.class, e.getValue())).map((o) -> {
                                o.setDAO(this);
                                return o;
                            }).collect(Collectors.toMap(o->o.getId(), o->o));
                    objectMap.putAll(objectMapToAdd);
                    long t1 = System.currentTimeMillis();
                    //logger.debug("#{} (already: {}) objects from structure: {}, time {}", objectMap.size(), alreadyInCache.size(), key.value, t1-t0);
                } else {
                    long t0 = System.currentTimeMillis();
                    try {
                        Collection<String> allStrings = getValues(dbm);
                        allStrings.size();
                        long t1 = System.currentTimeMillis();
                        Map<String, StructureObject> objectMap = allStrings.parallelStream()
                                .map((s) -> JSONUtils.parse(StructureObject.class, s))
                                .map((o) -> {
                                    o.setDAO(this);
                                    return o;
                                }).collect(Collectors.toMap(o->o.getId(), o->o));
                        cache.put(key, objectMap);
                        long t2 = System.currentTimeMillis();
                        //logger.debug("#{} objects from structure: {}, time to retrieve: {}, time to parse: {}", allStrings.size(), key.value, t1-t0, t2-t1);
                    } catch(IOError|AssertionError|Exception e) {
                        logger.error("Corrupted DATA for structure: "+key.value+" parent: "+key.key, e);
                        allObjectsRetrievedInCache.put(key, true);
                        return new HashMap<>();
                    }
                    
                }
                allObjectsRetrievedInCache.put(key, true);
                // set prev, next & trackHead
                Map<String, StructureObject> objectMap = cache.get(key);
                for (StructureObject o : objectMap.values()) {
                    if (o.getNextId()!=null) o.setNext(objectMap.get(o.getNextId()));
                    if (o.getPreviousId()!=null) o.setPrevious(objectMap.get(o.getPreviousId()));
                    if (o.getTrackHeadIdIfPresent()!=null) o.setTrackHead(objectMap.get(o.getTrackHeadIdIfPresent()), false);
                }
                // set to parents ? 
                if (key.value>=0) {
                    int parentStructureIdx = mDAO.getExperiment().getStructure(key.value).getParentStructure();
                    Map<String, StructureObject> parents = this.getCacheContaining(key.key, parentStructureIdx);
                    if (parents!=null) {
                        for (StructureObject o : objectMap.values()) o.setParent(parents.get(o.getParentId()));
                        Map<StructureObject, List<StructureObject>> byP = StructureObjectUtils.splitByParent(objectMap.values());
                        for (StructureObject p : byP.keySet()) {
                            List<StructureObject> children = byP.get(p);
                            Collections.sort(children);
                            p.setChildren(children, key.value);
                        }
                    }
                }
                return objectMap;
            }
        }
    }
    private void setParents(Collection<StructureObject> objects, Pair<String, Integer> parentKey) {
        Map<String, StructureObject> allParents = getChildren(parentKey);
        for (StructureObject o : objects) if (!o.isParentSet()) o.setParent(allParents.get(o.getParentId()));
    }
    @Override
    public StructureObject getById(String parentTrackHeadId, int structureIdx, int frame, String id) {
        // parentTrackHeadId can be null in case of parent call -> frame not null
        // frame can be < 
        if (parentTrackHeadId!=null || structureIdx==-1) {
            logger.debug("getById: sIdx={} f={}, allChilldren: {}", structureIdx, frame, getChildren(new Pair(parentTrackHeadId, structureIdx)).size());
            return ((Map<String, StructureObject>)getChildren(new Pair(parentTrackHeadId, structureIdx))).get(id);
        }
        else { // search in all parentTrackHeadId
            Map<String, StructureObject> cacheMap = getCacheContaining(id, structureIdx);
            if (cacheMap!=null) return cacheMap.get(id);
        }
        return null;
    }
    
    private Map<String, StructureObject> getCacheContaining(String id, int structureIdx) {
        if (structureIdx==-1) { //getExperiment().getStructure(structureIdx).getParentStructure()==-1
            Map<String, StructureObject> map = getChildren(new Pair(null, structureIdx));
            if (map.containsKey(id)) return map;
        } else {
            for (String parentTHId : DBMapUtils.getNames(getDB(structureIdx))) {
                Map<String, StructureObject> map = getChildren(new Pair(parentTHId, structureIdx)); //"root".equals(parentTHId) ?  null : 
                if (map.containsKey(id)) return map;
            }
        }
        return null;
    }
    @Override
    public void setAllChildren(List<StructureObject> parentTrack, int childStructureIdx) {
        if (parentTrack.isEmpty()) return;
        Map<String, StructureObject> children = getChildren(new Pair(parentTrack.get(0).getTrackHeadId(), childStructureIdx));
        logger.debug("setting: {} children to {} parents", children.size(), parentTrack.size());
        StructureObjectUtils.splitByParent(children.values()).forEach((parent, c) -> {
            if (c==null) return;
            Collections.sort(c);
            parent.setChildren(c, childStructureIdx);
        });
    }
    
    @Override
    public List<StructureObject> getChildren(StructureObject parent, int structureIdx) {
        List<StructureObject> res = new ArrayList<>();
        Map<String, StructureObject> children = getChildren(new Pair(parent.getTrackHeadId(), structureIdx));
        if (children==null) {
            logger.error("null children for: {} @ structure: {}", parent, structureIdx);
            return new ArrayList<>();
        }
        for (StructureObject o : children.values()) {
            if (parent.getId().equals(o.getParentId())) {
                //o.parent=parent;
                res.add(o);
            }
        }
        Collections.sort(res);
        return res;
    }
    
    /*public List<StructureObject> getChildren(Collection<StructureObject> parents, int structureIdx) {
        List<StructureObject> res = new ArrayList<>();
        Map<ObjectId, StructureObject> parentIds = toIdMap(parents);
        Map<StructureObject, List<StructureObject>> byParentTH = StructureObjectUtils.splitByParentTrackHead(parents);
        for (StructureObject pth : byParentTH.keySet()) {
            for (StructureObject o : getChildren(pth.getId(), structureIdx).values()) {
                if (parentIds.containsKey(o.parentId)) {
                    o.setParent(parentIds.get(o.parentId));
                    res.add(o);
                }
            }
        }
        return res;
    }*/

    @Override
    public void deleteChildren(StructureObject parent, int structureIdx) {
        if (readOnly) return;
        List<StructureObject> children = DBMapObjectDAO.this.getChildren(parent, structureIdx);
        if (!children.isEmpty()) {
            Pair<String, Integer> key = new Pair(parent.getTrackHeadId(), structureIdx);
            Map<String, StructureObject> cacheMap = cache.get(key);
            HTreeMap<String, String> dbm = getDBMap(key);
            for (StructureObject o : children) {
                if (cacheMap!=null) cacheMap.remove(o.getId());
                dbm.remove(o.getId());
            }
            getDB(structureIdx).commit();
        }
    }

    public static Set<String> toIds(Collection<StructureObject> objects) {
        Set<String> res= new HashSet<>(objects.size());
        for (StructureObject o : objects) res.add(o.getId());
        return res;
    }

    public static Map<String, StructureObject> toIdMap(Collection<StructureObject> objects) {
        Map<String, StructureObject> res= new HashMap<>(objects.size());
        for (StructureObject o : objects) res.put(o.getId(), o);
        return res;
    }
    
    @Override
    public void deleteChildren(Collection<StructureObject> parents, int structureIdx) {
        if (readOnly) return;
        Map<StructureObject, List<StructureObject>> byTh = StructureObjectUtils.splitByTrackHead(parents);
        for (StructureObject pth : byTh.keySet()) deleteChildren(byTh.get(pth), structureIdx, pth.getId(), false);
        getDB(structureIdx).commit();
    }
    protected void deleteChildren(Collection<StructureObject> parents, int structureIdx, String parentThreackHeadId, boolean commit) {
        if (readOnly) return;
        Pair<String, Integer> key = new Pair(parentThreackHeadId, structureIdx);
        Set<String> parentIds = toIds(parents);
        Map<String, StructureObject> cacheMap = getChildren(key);
        HTreeMap<String, String> dbMap = getDBMap(key);
        Iterator<String> it = cacheMap.keySet().iterator();
        while(it.hasNext()) {
            String id = it.next();
            StructureObject o = cacheMap.get(id);
            if (o==null) {
                logger.warn("DBMap remove error: structure: {}, parents {}", structureIdx, Utils.toStringList(new ArrayList<>(parents)));
                continue;
            }
            else if (o.getParentId()==null) logger.warn("DBMap remove error: no parent for: {}", o);
            if (parentIds.contains(o.getParentId())) {
                it.remove();
                dbMap.remove(id);
            }
        }
        if (commit) getDB(structureIdx).commit();
    }
    

    @Override
    public synchronized void deleteObjectsByStructureIdx(int... structures) {
        if (readOnly) return;
        for (int structureIdx : structures) {
            if (this.dbS.containsKey(structureIdx)) {
                dbS.remove(structureIdx).close();
                dbMaps.entrySet().removeIf(k -> k.getKey().value==structureIdx);
            }
            DBMapUtils.deleteDBFile(getDBFile(structureIdx));
        }
    }
    @Override
    public void applyOnAllOpenedObjects(Consumer<StructureObject> function) {
        for (Map<String, StructureObject> obs : cache.values()) {
            for (StructureObject so : obs.values()) function.accept(so);
        }
    }
    @Override
    public void clearCache() {
        logger.debug("clearing cache for Dao: {} / objects: {}, measurements: {}", this.positionName, this.dbS.keySet(), this.measurementdbS.keySet());
        applyOnAllOpenedObjects(o->{
            o.flushImages();
            if (o.hasRegion()) o.getRegion().clearVoxels();
        }); // free memory in case objects are stored elsewhere (eg selection, tack mask...)
        cache.clear();
        allObjectsRetrievedInCache.clear();
        closeAllFiles(true);
    }
    
    @Override
    public synchronized void deleteAllObjects() {
        closeAllObjectFiles(false);
        closeAllMeasurementFiles(false);
        cache.clear();
        allObjectsRetrievedInCache.clear();
        if (readOnly) return;
        File f = new File(dir);
        if (f.exists() && f.isDirectory()) for (File subF : f.listFiles())  subF.delete();
    }
    private synchronized void closeAllObjectFiles(boolean commit) {
        for (DB db : dbS.values()) {
            if (!readOnly && commit&&!db.isClosed()) db.commit();
            //logger.debug("closing object file : {} ({})", db, Utils.toStringList(Utils.getKeys(dbS, db), i->this.getDBFile(i)));
            db.close();
        }
        dbS.clear();
        dbMaps.clear();
        //cache.clear();
        //allObjectsRetrieved.clear();
    }
    public void closeAllFiles(boolean commit) {
        closeAllObjectFiles(commit);
        closeAllMeasurementFiles(commit);
    }
    public synchronized void compactDBs(boolean onlyOpened) {
        if (readOnly) return;
        compactObjectDBs(onlyOpened);
        compactMeasurementDBs(onlyOpened);
    }
    public synchronized void compactObjectDBs(boolean onlyOpened) {
        if (readOnly) return;
        if (onlyOpened) {
            for (DB db : this.dbS.values()) {
                db.commit();
                //db.compact();
            }
        } else {
            for (int s = -1; s<mDAO.getExperiment().getStructureCount(); ++s) {
                if (dbS.keySet().contains(s)) {
                    dbS.get(s).commit();
                    //dbS.get(s).compact();
                } //else if (new File(getDBFile(s)).exists()) getDB(s).compact();
            }
        }
    }
    public synchronized void compactMeasurementDBs(boolean onlyOpened) {
        if (readOnly) return;
        if (onlyOpened) {
            for (Pair<DB, ?> p : this.measurementdbS.values()) {
                p.key.commit();
                //p.key.compact();
            }
        } else {
            for (int s = -1; s<mDAO.getExperiment().getStructureCount(); ++s) {
                if (measurementdbS.keySet().contains(s)) {
                    measurementdbS.get(s).key.commit();
                    //measurementdbS.get(s).key.compact();
                } //else if (new File(this.getMeasurementDBFile(s)).exists()) this.getMeasurementDB(s).key.compact();
            }
        }
    }
    @Override
    public void delete(StructureObject o, boolean deleteChildren, boolean deleteFromParent, boolean relabelSiblings) {
        if (readOnly) return;
        Pair<String, Integer> key = new Pair(o.getParentTrackHeadId(), o.getStructureIdx());
        HTreeMap<String, String> dbMap = getDBMap(key);
        dbMap.remove(o.getId());
        if (cache.containsKey(key)) cache.get(key).remove(o.getId());
        if (deleteChildren) {
            for (int s : o.getExperiment().getAllDirectChildStructures(o.getStructureIdx())) deleteChildren(o, s);
            //.. if track head is removed and has children -> inconsistency -> check at each eraseAll, if eraseAll children -> eraseAll whole collection if trackHead, if not dont do anything
        }
        if (deleteFromParent) {
            if (o.getParent().getChildren(o.getStructureIdx()).remove(o) && relabelSiblings) {
                List<StructureObject> relabeled = new ArrayList<>(o.getParent().getChildren(o.getStructureIdx()).size());
                o.relabelChildren(o.getStructureIdx(), relabeled);
                store(relabeled, false);
            }   
        }
        getDB(o.getStructureIdx()).commit();
    }

    @Override
    public void delete(Collection<StructureObject> list, boolean deleteChildren, boolean deleteFromParent, boolean relabelSiblings) {
        if (readOnly) return;
        Map<Pair<String, Integer>, List<StructureObject>> splitByPTH = splitByParentTrackHeadIdAndStructureIdx(list);
        Set<Integer> allModifiedStructureIdx = new HashSet<>(Pair.unpairValues(splitByPTH.keySet()));
        for (Pair<String, Integer> key : splitByPTH.keySet()) {
            List<StructureObject> toRemove = splitByPTH.get(key);
            HTreeMap<String, String> dbMap = getDBMap(key);
            for (StructureObject o : toRemove) dbMap.remove(o.getId());
            if (cache.containsKey(key)) {
                Map<String, StructureObject> cacheMap = cache.get(key);
                for (StructureObject o : toRemove) cacheMap.remove(o.getId());
            }
            if (deleteChildren) {
                for (int sChild : getExperiment().getAllDirectChildStructures(key.value)) {
                    allModifiedStructureIdx.add(sChild);
                    deleteChildren(toRemove, sChild, key.key, false);
                }
            }
            //TODO if track head is removed and has children -> inconsistency -> check at each eraseAll, if eraseAll children -> eraseAll whole collection if trackHead, if not dont do anything
            if (deleteFromParent && relabelSiblings) {
                if (key.value==-1) continue; // no parents
                Set<StructureObject> parents = new HashSet<StructureObject>();
                for (StructureObject o : toRemove) {
                    if (o.getParent().getChildren(key.value).remove(o)) {
                        parents.add(o.getParent());
                    }
                }
                //logger.debug("number of parents with eraseAll object from structure: {} = {}", sIdx, parents.size());
                List<StructureObject> relabeled = new ArrayList<StructureObject>();
                for (StructureObject p : parents) {
                    p.relabelChildren(key.value, relabeled);
                }
                Utils.removeDuplicates(relabeled, false);
                store(relabeled, false);
            } else if (deleteFromParent) {
                for (StructureObject o : toRemove) {
                    if (o.getParent()!=null) o.getParent().getChildren(o.getStructureIdx()).remove(o);
                }
            }
        }
        for (int i : allModifiedStructureIdx) getDB(i).commit();
    }
    
    
    @Override
    public void store(StructureObject object) {
        if (readOnly) return;
        Pair<String, Integer> key = new Pair(object.getParentTrackHeadId(), object.getStructureIdx());
        if (object.hasMeasurementModifications()) upsertMeasurement(object);
        object.updateRegionContainer();
        // get parent/pTh/next/prev ids ? 
        cache.getAndCreateIfNecessary(key).put(object.getId(), object);
        getDBMap(key).put(object.getId(), JSONUtils.serialize(object));
        getDB(object.getStructureIdx()).commit();
    }
    protected void store(Collection<StructureObject> objects, boolean commit) {
        if (readOnly) return;
        if (objects==null || objects.isEmpty()) return;
        //logger.debug("storing: {} commit: {}", objects.size(), commit);
        List<StructureObject> upserMeas = new ArrayList<>(objects.size());
        for (StructureObject o : objects) o.setDAO(this);
        Map<Pair<String, Integer>, List<StructureObject>> splitByPTH = splitByParentTrackHeadIdAndStructureIdx(objects);
        //logger.debug("storing: {} under #keys: {} commit: {}", objects.size(), splitByPTH.size(), commit);
        for (Pair<String, Integer> key : splitByPTH.keySet()) {
            List<StructureObject> toStore = splitByPTH.get(key);
            //logger.debug("storing: {} objects under key: {}", toStore.size(), key.toString());
            Map<String, StructureObject> cacheMap = cache.getAndCreateIfNecessary(key);
            HTreeMap<String, String> dbMap = getDBMap(key);
            long t0 = System.currentTimeMillis();
            Map<String, String> toStoreMap = toStore.parallelStream().map(o->{o.updateRegionContainer(); return o;}).collect(Collectors.toMap(o->o.getId(), o->JSONUtils.serialize(o)));
            long t1 = System.currentTimeMillis();
            dbMap.putAll(toStoreMap);
            long t2 = System.currentTimeMillis();
            logger.debug("storing: #{} objects of structure: {} to: {} in {}ms ({}ms+{}ms)",toStoreMap.size(), key.value, objects.iterator().next().getParent()==null ? "" : objects.iterator().next().getParent().getTrackHead(), t2-t0, t1-t0, t2-t1);
            toStore.stream().map((object) -> {
                if (object.hasMeasurementModifications()) upserMeas.add(object);
                return object;
            }).forEachOrdered((object) -> {
                cacheMap.put(object.getId(), object);
            });
            if (commit) this.getDB(key.value).commit();            
        }
        upsertMeasurements(upserMeas);
    }
    @Override
    public void store(Collection<StructureObject> objects) {
        store(objects, true);
    }

    @Override
    public List<StructureObject> getRoots() {
        // todo: root cache list to avoid sorting each time getRoot is called?
        List<StructureObject> res =  new ArrayList<>(getChildren(new Pair(null, -1)).values());
        Collections.sort(res, (o1, o2) -> Integer.compare(o1.getFrame(), o2.getFrame()));
        return res;
    }

    @Override
    public void setRoots(List<StructureObject> roots) {
        this.store(roots, true);
    }

    @Override
    public StructureObject getRoot(int timePoint) {
        List<StructureObject> roots = getRoots();
        if (roots.size()<=timePoint) return null;
        return roots.get(timePoint);
    }

    @Override
    public List<StructureObject> getTrack(StructureObject trackHead) {
        Map<String, StructureObject> allObjects = getChildren(new Pair(trackHead.getParentTrackHeadId(), trackHead.getStructureIdx()));
        return allObjects.values().stream()
                .filter(o->o.getTrackHeadId().equals(trackHead.getId()))
                .sorted((o1, o2)-> Integer.compare(o1.getFrame(), o2.getFrame()))
                .collect(Collectors.toList());
        // TODO: parents may no be set !
    }

    @Override
    public List<StructureObject> getTrackHeads(StructureObject parentTrack, int structureIdx) {
        long t0 = System.currentTimeMillis();
        Map<String, StructureObject> allObjects = getChildren(new Pair(parentTrack.getId(), structureIdx));
        long t1 = System.currentTimeMillis();
        logger.debug("parent: {}, structure: {}, #{} objects retrieved in {}ms", parentTrack, structureIdx, allObjects.size(), t1-t0);
        List<StructureObject> list = allObjects.values().stream().filter(o->o.isTrackHead()).sorted().collect(Collectors.toList());
        setParents(list, new Pair(parentTrack.getParentTrackHeadId(), parentTrack.getStructureIdx()));
        return list;
    }

    // measurements
    // store by structureIdx in another folder. Id = same as objectId
    private String getMeasurementDBFile(int structureIdx) {
        return dir+"measurements_"+structureIdx+".db";
    }
    protected Pair<DB, HTreeMap<String, String>> getMeasurementDB(int structureIdx) {
        Pair<DB, HTreeMap<String, String>> res = this.measurementdbS.get(structureIdx);
        if (res==null) {
            synchronized(measurementdbS) {
                if (!measurementdbS.containsKey(structureIdx)) {
                    try {
                        //logger.debug("opening measurement DB for structure: {}: file {} readONly: {}",structureIdx, getMeasurementDBFile(structureIdx), readOnly);
                        DB db = DBMapUtils.createFileDB(getMeasurementDBFile(structureIdx), readOnly);
                        //logger.debug("opening measurement DB for structure: {}: file {} readONly: {}: {}",structureIdx, getMeasurementDBFile(structureIdx), readOnly, db);
                        HTreeMap<String, String> dbMap = DBMapUtils.createHTreeMap(db, "measurements");
                        res = new Pair(db, dbMap);
                        measurementdbS.put(structureIdx, res);
                    }  catch (org.mapdb.DBException ex) {
                        logger.error("Couldnot create DB: readOnly:"+readOnly, ex);
                        return null;
                    }
                } else {
                    res = measurementdbS.get(structureIdx);
                }
            }
        }
        return res;
    }
    
    @Override
    public void upsertMeasurements(Collection<StructureObject> objects) {
        if (readOnly) return;
        Map<Integer, List<StructureObject>> bySIdx = StructureObjectUtils.splitByStructureIdx(objects);
        for (int i : bySIdx.keySet()) {
            Pair<DB, HTreeMap<String, String>> mDB = getMeasurementDB(i);
            for (StructureObject o : bySIdx.get(i)) {
                o.getMeasurements().updateObjectProperties(o);
                mDB.value.put(o.getId(), JSONUtils.serialize(o.getMeasurements()));
                o.getMeasurements().modifications=false;
            }
            mDB.key.commit();
        }
    }

    @Override
    public void upsertMeasurement(StructureObject o) {
        if (readOnly) return;
        o.getMeasurements().updateObjectProperties(o);
        Pair<DB, HTreeMap<String, String>> mDB = getMeasurementDB(o.getStructureIdx());
        mDB.value.put(o.getId(), JSONUtils.serialize(o.getMeasurements()));
        mDB.key.commit();
        o.getMeasurements().modifications=false;
    }

    @Override
    public List<Measurements> getMeasurements(int structureIdx, String... measurements) {
        Pair<DB, HTreeMap<String, String>> mDB = getMeasurementDB(structureIdx);
        List<Measurements> res = new ArrayList<>();
        for (String s : getValues(mDB.value)) {
            Measurements m = new Measurements(parse(s), this.positionName);
            res.add(m);
        }
        return res;
    }
    @Override
    public Measurements getMeasurements(StructureObject o) {
        Pair<DB, HTreeMap<String, String>> mDB = getMeasurementDB(o.getStructureIdx());
        if (mDB==null) return null;
        try {
            String mS = mDB.value.get(o.getId());
            if (mS==null) return null;
            Measurements m = new Measurements(parse(mS), this.positionName);
            return m;
        } catch (IOError e) {
            
        }
        return null;
        
    }
    
    public void retrieveMeasurements(Collection<StructureObject> objects) {
        Map<Integer, List<StructureObject>> bySIdx = StructureObjectUtils.splitByStructureIdx(objects);
        for (int i : bySIdx.keySet()) {
            Pair<DB, HTreeMap<String, String>> mDB = getMeasurementDB(i);
            for (StructureObject o : bySIdx.get(i)) {
                String mS = mDB.value.get(o.getId());
                o.setMeasurements(new Measurements(parse(mS), this.positionName));
            }
        }
    }

    @Override
    public void deleteAllMeasurements() {
        closeAllMeasurementFiles(false);
        deleteMeasurementsFromOpenObjects(); // also in opened structureObjects
        if (readOnly) return;
        for (int s = 0; s<getExperiment().getStructureCount(); ++s) DBMapUtils.deleteDBFile(getMeasurementDBFile(s));
    }
    
    private void deleteMeasurementsFromOpenObjects() {
        for (Map<String, StructureObject> m : cache.values()) {
            for (StructureObject o : m.values()) o.setMeasurements(null);
        }
    }
    private synchronized void closeAllMeasurementFiles(boolean commit) {
        for (Pair<DB, HTreeMap<String, String>> p : this.measurementdbS.values()) {
            if (!readOnly&&commit&&!p.key.isClosed()) p.key.commit();
            //logger.debug("closing measurement DB: {} ({})",p.key, Utils.toStringList(Utils.getKeys(measurementdbS, p), i->getMeasurementDBFile(i)));
            p.key.close();
        }
        measurementdbS.clear();
    }
    
    public static Map<Pair<String, Integer>, List<StructureObject>> splitByParentTrackHeadIdAndStructureIdx(Collection<StructureObject> list) {
        if (list.isEmpty()) return Collections.EMPTY_MAP;
        return list.stream().collect(Collectors.groupingBy(o -> new Pair(o.isRoot()? null : o.getParentTrackHeadId(), o.getStructureIdx())));
    }

}
