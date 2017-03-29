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

import static dataStructure.objects.DBMapMasterDAO.logger;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import utils.DBMapUtils;

/**
 *
 * @author jollion
 */
public class DBMapSelectionDAO implements SelectionDAO {
    
    final String dir;
    final DBMapMasterDAO mDAO;
    DB db;
    HTreeMap<String, String> dbMap;
    private final Map<String, Selection> idCache = new HashMap<>();
    public DBMapSelectionDAO(DBMapMasterDAO mDAO, String dir) {
        this.mDAO=mDAO;
        this.dir= dir+File.separator+"Selections"+File.separator;
        new File(this.dir).mkdirs();
        makeDB();
    }
    private void makeDB() {
        db = DBMapUtils.createFileDB(getSelectionFile());
        dbMap = DBMapUtils.createHTreeMap(db, "selections");
    }
    
    private String getSelectionFile() {
        return dir+"selections.db";
    }
    @Override
    public Selection getOrCreate(String name, boolean clearIfExisting) {
        if (idCache.isEmpty()) retrieveAllSelections();
        Selection res = idCache.get(name);
        if (res==null) {
            res = new Selection(name, mDAO);
            idCache.put(name, res);
        } else if (clearIfExisting) {
            res.clear();
            // TODO: commit ?
        }
        return res;
    }
    
    private void retrieveAllSelections() {
        idCache.clear();
        if (db.isClosed()) makeDB();
        for (String s : DBMapUtils.getValues(dbMap)) {
            Selection sel = mDAO.unmarshall(Selection.class, s);
            idCache.put(sel.getName(), sel);
        }
    }
    @Override
    public List<Selection> getSelections() { // todo: inspect file in directory and generate selections
        if (idCache.isEmpty()) retrieveAllSelections();
        List<Selection> res = new ArrayList<>(idCache.values());
        Collections.sort(res);
        return res;
    }

    @Override
    public void store(Selection s) {
        idCache.put(s.getName(), s);
        if (db.isClosed()) makeDB();
        this.dbMap.put(s.getName(), mDAO.marshall(s));
        db.commit();
    }

    @Override
    public void delete(String id) {
        idCache.remove(id);
        if (db.isClosed()) makeDB();
        dbMap.remove(id);
        db.commit();
    }

    @Override
    public void delete(Selection o) {
        delete(o.getName());
    }

    @Override
    public void deleteAllObjects() {
        idCache.clear();
        if (db.isClosed()) makeDB();
        dbMap.clear();
        db.commit();
    }
    public void compact(boolean commit) {
        if (db.isClosed()) makeDB();
        if (commit) this.db.commit();
        this.db.compact();
    }
    private void close(boolean commit) {
        if (db.isClosed()) return;
        if (commit) this.db.commit();
        this.db.close();
    }

    @Override
    public void clearCache() {
        this.idCache.clear();
        close(true);
    }
}
