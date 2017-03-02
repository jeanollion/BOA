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
import static dataStructure.objects.StructureObject.logger;
import de.caluga.morphium.DAO;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.query.Query;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.types.ObjectId;

/**
 *
 * @author jollion
 */
public class MorphiumSelectionDAO implements SelectionDAO {
    final MorphiumMasterDAO masterDAO;
    public final String collectionName;
    private final Map<String, Selection> idCache;
    public MorphiumSelectionDAO(MorphiumMasterDAO masterDAO) {
        this.collectionName="selections";
        this.masterDAO=masterDAO;
        masterDAO.m.ensureIndicesFor(Selection.class, collectionName);
        this.idCache=new HashMap<>();
    }
    
    protected Query<Selection> getQuery() {
        Query<Selection> res =  masterDAO.m.createQueryFor(Selection.class); 
        res.setCollectionName(collectionName);
        return res;
    }
    
    @Override public synchronized List<Selection> getSelections() {
        idCache.clear();
        List<Selection> sel = getQuery().asList();
        Collections.sort(sel);
        for (Selection s : sel) s.setMasterDAO(masterDAO);
        return sel;
    }
    
    @Override public synchronized Selection getObject(String id) {
        Selection s = idCache.get(id);
        if (s==null) {
            s =  getQuery().getById(id);
            if (s!=null) {
                s.setMasterDAO(masterDAO);
                idCache.put(id, s);
            }
        }
        return s;
        
    }
    
    @Override public synchronized void store(Selection s) {
        s.setMasterDAO(masterDAO);
        long t0 = System.currentTimeMillis();
        masterDAO.m.storeNoCache(s, collectionName, null);
        long t1 = System.currentTimeMillis();
        logger.debug("Stored selection: {} in {}ms", s.getName(), t1-t0);
        idCache.remove(s.getName());
    }
    
    @Override public synchronized void delete(String id) {
        //masterDAO.m.delete(getQuery().f("id").eq(id));
        BasicDBObject db = new BasicDBObject().append("_id", id);
        //logger.debug("delete meas by id: {}, from colleciton: {}", db, collectionName);
        masterDAO.m.getDatabase().getCollection(collectionName).remove(db);
        idCache.remove(id);
    }
    
    @Override public synchronized void delete(Selection o) {
        if (o==null) return;
        masterDAO.m.delete(o, collectionName, null);
        idCache.remove(o.getName());
        //logger.debug("delete meas: {}, from colleciton: {}", o.getId(), collectionName);
    }
    
    @Override public synchronized void deleteAllObjects() {
        idCache.clear();
        masterDAO.m.getDatabase().getCollection(collectionName).drop();
        //masterDAO.m.clearCollection(Measurements.class, collectionName);
    }

    @Override public synchronized Selection getOrCreate(String name, boolean clearIfExisting) {
        if (idCache.containsKey(name)) return idCache.get(name);
        List<Selection> sels = getSelections();
        Selection res = null;
        for (Selection s : sels) {
            if (s.getName().equals(name)) {
                res = s;
                break;
            }
        }
        if (res!=null) {
            if (clearIfExisting) res.clear();
        } else {
            res = new Selection(name, masterDAO);
        }
        idCache.put(name, res);
        return res;
    }
    
}
