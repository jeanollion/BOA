/*
 * Copyright (C) 2017 jollion
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

import dataStructure.configuration.Experiment;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bson.types.ObjectId;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import utils.HashMapGetCreate;
import utils.Pair;

/**
 *
 * @author jollion
 */
public class DBMapObjectDAO implements ObjectDAO {
    final DBMapMasterDAO mDAO;
    final String positionName;
    final HashMapGetCreate<Pair<ObjectId, Integer>, Map<ObjectId, StructureObject>> cache = new HashMapGetCreate<>(new HashMapGetCreate.MapFactory()); // parent trackHead id -> id cache
    final Map<Pair<ObjectId, Integer>, HTreeMap<String, String>> dbMaps = new HashMap<>();
    final String dir;
    final Map<Integer, DB> dbS = new HashMap<>();
    public DBMapObjectDAO(DBMapMasterDAO mDAO, String positionName, String dir) {
        this.mDAO=mDAO;
        this.positionName=positionName;
        this.dir = dir+File.separator+"SegmentedObjects"+File.separator+positionName+File.separator;
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

    @Override
    public void clearCache() {
        cache.clear();
    }
    private String getDBFile(int structureIdx) {
        return dir+"objects_"+structureIdx+".db";
    }
    protected DB getDB(int structureIdx) {
        DB res = this.dbS.get(structureIdx);
        if (res==null) {
            synchronized(dbS) {
                if (!dbS.containsKey(structureIdx)) {
                    res = DBMaker.fileDB(getDBFile(structureIdx)).fileMmapEnable().transactionEnable().make();
                    dbS.put(structureIdx, res);
                } else {
                    res = dbS.get(structureIdx);
                }
            }
        }
        return res;
    }
    protected HTreeMap<String, String> getDBMap(ObjectId parentTrackHeadId, int structureIdx) {
        Pair<ObjectId, Integer> key = new Pair(parentTrackHeadId, structureIdx);
        HTreeMap<String, String> res = this.dbMaps.get(key);
        if (res==null) {
            synchronized(dbMaps) {
                if (dbMaps.containsKey(key)) res=dbMaps.get(key);
                else {
                    DB db = getDB(structureIdx);
                    res = db.hashMap(parentTrackHeadId.toString(), Serializer.STRING, Serializer.STRING).createOrOpen();
                    dbMaps.put(key, res);
                }
            }
        }
        return res;
    }
    protected Map<ObjectId, StructureObject> getChildren(ObjectId parentTrackHeadId, int structureIdx) {
        Pair<ObjectId, Integer> key = new Pair(parentTrackHeadId, structureIdx);
        if (cache.containsKey(key)) return cache.get(key);
        else {
            Map<ObjectId, StructureObject> objectMap = cache.getAndCreateIfNecessary(key);
            HTreeMap<String, String> dbm = getDBMap(parentTrackHeadId, structureIdx);
            for (String s : dbm.getValues()) {
                StructureObject o = this.mDAO.unmarshall(StructureObject.class, s);
                o.dao=this;
                objectMap.put(o.id, o);
            }
            // at this stage parents are not set !
            return objectMap;
        }
    }        
    @Override
    public List<StructureObject> getChildren(StructureObject parent, int structureIdx) {
        List<StructureObject> res = new ArrayList<>();
        for (StructureObject o : getChildren(parent.getParentTrackHeadId(), structureIdx).values()) {
            if (o.parentId.equals(parent.getId())) {
                o.setParent(parent);
                res.add(o);
            }
        }
        return res;
    }

    @Override
    public void deleteChildren(StructureObject parent, int structureIdx) {
        List<StructureObject> children = getChildren(parent, structureIdx);
        if (!children.isEmpty()) {
            Pair<ObjectId, Integer> key = new Pair(parent.getTrackHeadId(), structureIdx);
            Map<ObjectId, StructureObject> cacheMap = cache.get(key);
            HTreeMap<String, String> dbm = dbMaps.get(key);
            for (StructureObject o : children) {
                cacheMap.remove(o.getId());
                dbm.remove(o.getId().toString());
            }
            getDB(structureIdx).commit();
        }
    }

    public static Set<ObjectId> toIds(Collection<StructureObject> objects) {
        Set<ObjectId> res= new HashSet<>(objects.size());
        for (StructureObject o : objects) res.add(o.getId());
        return res;
    }
    
    @Override
    public void deleteChildren(Collection<StructureObject> parents, int structureIdx) {
        Map<StructureObject, List<StructureObject>> byTh = StructureObjectUtils.splitByTrackHead(parents);
        for (StructureObject pth : byTh.keySet()) {
            List<StructureObject> p = byTh.get(pth);
            Set<ObjectId> parentIds = toIds(p);
            Map<ObjectId, StructureObject> cacheMap = getChildren(pth.getId(), structureIdx);
            HTreeMap<String, String> dbMap = getDBMap(pth.getId(), structureIdx);
            Iterator<ObjectId> it = cacheMap.keySet().iterator();
            while(it.hasNext()) {
                ObjectId id = it.next();
                if (parentIds.contains(cacheMap.get(id).parentId)) {
                    it.remove();
                    dbMap.remove(id.toString());
                }
            }
        }
        getDB(structureIdx).commit();
    }

    @Override
    public synchronized void deleteObjectsByStructureIdx(int... structures) {
        for (int structureIdx : structures) {
            if (this.dbS.containsKey(structureIdx)) dbS.get(structureIdx).close();
            File f = new File(getDBFile(structureIdx));
            f.delete();
        }
        
    }

    @Override
    public void deleteAllObjects() {
        for (DB db : dbS.values()) db.close();
        File f = new File(dir);
        f.delete();
    }

    @Override
    public void delete(StructureObject o, boolean deleteChildren, boolean deleteFromParent, boolean relabelParent) {
        HTreeMap<String, String> dbMap = getDBMap(o.getParentTrackHeadId(), o.getStructureIdx());
        dbMap.remove(o.getId().toString());
        Pair<ObjectId, Integer> key = new Pair(o.getParentTrackHeadId(), o.getStructureIdx());
        if (cache.containsKey(key)) cache.get(key).remove(o.getId());
        
        if (deleteChildren) {
            .. if track head is removed and has children -> inconsistency -> check at each delete, if delete children -> delete whole collection if trackHead, if not dont do anything
            for (List<StructureObject> l : o.childrenSM.asList()) delete(l, deleteChildren, false, false);
        }
        if (deleteFromParent) {
            o.getParent().getChildren(o.getStructureIdx()).remove(o);
            if (relabelParent) {
                List<StructureObject> relabeled = new ArrayList<>();
                o.relabelChildren(o.getStructureIdx(), relabeled);
                store(relabeled, false, false);
            }   
        }
        getDB(o.getStructureIdx()).commit();
    }

    @Override
    public void delete(Collection<StructureObject> list, boolean deleteChildren, boolean deleteFromParent, boolean relabelParent) {
        Map<Pair<ObjectId, Integer>, List<StructureObject>> splitByPTH = splitByParentTrackHeadIdAndStructureIdx(list);
        for (Pair<ObjectId, Integer> key : splitByPTH.keySet()) {
            List<StructureObject> toRemove = splitByPTH.get(key);
            Set<ObjectId> toRemoveIds = toIds(toRemove);
            HTreeMap<String, String> dbMap = getDBMap(key.key, key.value);
            if (cache.containsKey(key)) for (StructureObject o : toRemove) cache.get(key).remove(o.getId());
            .. remove from dbmap
                    .. if track head is removed and has children -> inconsistency -> check at each delete, if delete children -> delete whole collection if trackHead, if not dont do anything
        }
    }
    
    
    @Override
    public void store(StructureObject object, boolean updateTrackAttributes) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    protected void store(Collection<StructureObject> objects, boolean updateTrackAttributes, boolean commit) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    @Override
    public void store(Collection<StructureObject> objects, boolean updateTrackAttributes) {
        store(objects, updateTrackAttributes, true);
    }

    @Override
    public List<StructureObject> getRoots() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setRoos(List<StructureObject> roots) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public StructureObject getRoot(int timePoint) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<StructureObject> getTrack(StructureObject trackHead) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<StructureObject> getTrackHeads(StructureObject parentTrack, int structureIdx) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void upsertMeasurements(Collection<StructureObject> objects) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void upsertMeasurement(StructureObject o) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<Measurements> getMeasurements(int structureIdx, String... measurements) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void deleteAllMeasurements() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    public static Map<Pair<ObjectId, Integer>, List<StructureObject>> splitByParentTrackHeadIdAndStructureIdx(Collection<StructureObject> list) {
        if (list.isEmpty()) return Collections.EMPTY_MAP;
        return list.stream().collect(Collectors.groupingBy(o -> new Pair(o.getParent().getTrackHeadId(), o.getStructureIdx())));
    }
}
