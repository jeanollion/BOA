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

import com.mongodb.DBObject;
import static com.mongodb.util.JSON.parse;
import static com.mongodb.util.JSON.serialize;
import configuration.parameters.PostLoadable;
import dataStructure.configuration.Experiment;
import de.caluga.morphium.ObjectMapper;
import de.caluga.morphium.ObjectMapperImpl;
import java.io.File;
import java.util.HashMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap.KeySet;
import org.mapdb.Serializer;
import static utils.MorphiumUtils.createOfflineMorphium;

/**
 *
 * @author jollion
 */
public class DBMapMasterDAO implements MasterDAO {

    protected final String configDir;
    protected final ObjectMapper mapper;
    protected final DB xpDB;
    protected final String dbName;
    final HashMap<String, DBMapObjectDAO> DAOs = new HashMap<>();
    protected final KeySet<String> xpSet;
    protected Experiment xp;
    DBMapSelectionDAO selectionDAO;
    
    public DBMapMasterDAO(String dir, String dbName) {
        configDir = dir;
        this.dbName = dbName;
        mapper = new ObjectMapperImpl();
        mapper.setMorphium(createOfflineMorphium());
        xpDB = DBMaker.fileDB(configDir+"/config.db").transactionEnable().make();
        xpSet = xpDB.hashSet("experiment", Serializer.STRING).createOrOpen();
    }
    
    protected String marshall(Object o) {
        DBObject dbo = mapper.marshall(o);
        return serialize(dbo);
    }
    protected <E> E unmarshall(Class<E> clazz, String s) {
        DBObject dbo = (DBObject)parse(s);
        E res= mapper.unmarshall(clazz, dbo);
        if (res instanceof PostLoadable) ((PostLoadable)res).postLoad();
        return res;
    }
    
    @Override
    public DBMapObjectDAO getDao(String fieldName) {
        DBMapObjectDAO res = this.DAOs.get(fieldName);
        if (res==null) {
            String op = getOutputPath();
            if (op==null) return null;
            res = new DBMapObjectDAO(this, fieldName, op);
            DAOs.put(fieldName, res);
        }
        return res;
    }

    @Override
    public String getDBName() {
        return dbName;
    }

    @Override
    public void deleteAllObjects() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void reset() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Experiment getExperiment() {
        if (this.xp==null) {
            if (xpSet.isEmpty()) return null;
            String xpString = xpSet.iterator().next();
            xp = this.unmarshall(Experiment.class, xpString);
        }
        return xp;
    }

    @Override
    public void updateExperiment() {
        xpSet.clear();
        xpSet.add(marshall(xp));
        xpDB.commit();
    }

    @Override
    public void setExperiment(Experiment xp) {
        this.xp=xp;
        updateExperiment();
    }

    protected String getOutputPath() {
        getExperiment();
        if (xp==null) return null;
        String res = xp.getOutputImageDirectory();
        File f = new File(res);
        if (f.exists() && f.isDirectory()) return res;
        else {
            return null;
        }
        
    }
    
    @Override
    public SelectionDAO getSelectionDAO() {
        if (this.selectionDAO==null) {
            String op = getOutputPath();
            if (op!=null) {
                selectionDAO = new DBMapSelectionDAO(this, op);
            }
        }
        return selectionDAO;
    }
    
}
