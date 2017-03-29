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
import java.io.File;
import java.util.HashMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.DBMapUtils;
import utils.MorphiumUtils;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class DBMapMasterDAO implements MasterDAO {
    public static final Logger logger = LoggerFactory.getLogger(DBMapMasterDAO.class);
    protected final String configDir;
    
    protected final String dbName;
    final HashMap<String, DBMapObjectDAO> DAOs = new HashMap<>();
    protected HTreeMap<String, String> xpMap;
    protected DB xpDB;
    protected Experiment xp;
    DBMapSelectionDAO selectionDAO;
    
    public DBMapMasterDAO(String dir, String dbName) {
        if (dir==null) throw new IllegalArgumentException("Invalid directory: "+ dir);
        if (dbName==null) throw new IllegalArgumentException("Invalid DbName: "+ dbName);
        configDir = dir;
        new File(configDir).mkdirs();
        this.dbName = dbName;
        makeXPDB();
    }
    
    @Override
    public void delete() {
        String outputPath = getExperiment()!=null ? getExperiment().getOutputImageDirectory() : null;
        clearCache();
        Utils.deleteDirectory(outputPath);
        new File(this.getConfigFile(dbName)).delete();
        File f = new File(configDir).getParentFile();
        f.delete(); // deletes only if void. 
    }
    
    private void makeXPDB() {
        xpDB = DBMapUtils.createFileDB(getConfigFile(dbName));
        xpMap = DBMapUtils.createHTreeMap(xpDB, "experiment");
    }
    private String getConfigFile(String dbName) {
        return configDir + File.separator + dbName + "_config.db";
    }
    protected String marshall(Object o) {
        DBObject dbo = MorphiumUtils.marshall(o);
        return serialize(dbo);
    }
    protected <E> E unmarshall(Class<E> clazz, String s) {
        DBObject dbo = (DBObject)parse(s);
        E res= MorphiumUtils.unmarshall(clazz, dbo);
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
    public String getDir() {
        return this.configDir;
    }

    @Override
    public void deleteAllObjects() {
        for (String s : getExperiment().getPositionsAsString()) {
            getDao(s).deleteAllObjects(); // also deletes measurements
        }
    }

    @Override
    public void reset() {
        this.xpMap.clear();
        if (!xpDB.isClosed()) this.xpDB.commit();
        if (this.getOutputPath()!=null) {
            deleteAllObjects();
            getSelectionDAO().deleteAllObjects();
        }
    }
    
    @Override
    public void clearCache() {
        for (DBMapObjectDAO dao : DAOs.values()) dao.clearCache();
        DAOs.clear();
        if (selectionDAO!=null) {
            getSelectionDAO().clearCache();
            selectionDAO=null;
        }
        if (!xpDB.isClosed()) {
            xpDB.close();
            logger.debug("closing: {}", this.getConfigFile(dbName));
            this.xpMap=null;
        }
        this.xp=null;
    }
    
    public void compact() {
        for (String s : getExperiment().getPositionsAsString()) {
            getDao(s).compactDBs(true);
        }
        getSelectionDAO().compact(true);
        if (!xpDB.isClosed()) xpDB.compact();
    }

    @Override
    public Experiment getExperiment() {
        if (this.xp==null) {
            if (xpDB.isClosed()) makeXPDB();
            if (xpMap.isEmpty()) return null;
            String xpString = xpMap.get("config");
            xp = this.unmarshall(Experiment.class, xpString);
        }
        return xp;
    }

    @Override
    public void updateExperiment() {
        if (xpDB.isClosed()) makeXPDB();
        xpMap.clear();
        if (xp!=null) xpMap.put("config", marshall(xp));
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
    public DBMapSelectionDAO getSelectionDAO() {
        if (this.selectionDAO==null) {
            String op = getOutputPath();
            if (op!=null && new File(op).isDirectory()) {
                selectionDAO = new DBMapSelectionDAO(this, op);
            }
        }
        return selectionDAO;
    }
    
}
