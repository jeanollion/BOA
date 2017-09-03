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

import boa.gui.GUI;
import dataStructure.configuration.Experiment;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.json.simple.JSONObject;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.DBMapUtils;
import utils.FileIO;
import utils.JSONUtils;
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
        logger.debug("create DBMAPMASTERDAO: dir: {}, dbName: {}", dir, dbName);
        configDir = dir;
        new File(configDir).mkdirs();
        this.dbName = dbName;
    }
    
    @Override
    public void eraseAll() {
        String outputPath = getExperiment()!=null ? getExperiment().getOutputDirectory() : null;
        String outputImagePath = getExperiment()!=null ? getExperiment().getOutputImageDirectory() : null;
        clearCache();
        Utils.deleteDirectory(outputPath);
        Utils.deleteDirectory(outputImagePath);
        deleteExperiment();
        new File(configDir).delete(); // deletes XP directory only if void. 
    }
    
    private void makeXPDB() {
        xpDB = DBMapUtils.createFileDB(getConfigFile(dbName, true));
        xpMap = DBMapUtils.createHTreeMap(xpDB, "experiment");
    }
    private String getConfigFile(String dbName, boolean db) {
        if (db) return configDir + File.separator + dbName + "_config.db";
        else return configDir + File.separator + dbName + "_config.txt";
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
        if (getExperiment()==null) return;
        for (String s : getExperiment().getPositionsAsString()) {
            getDao(s).deleteAllObjects(); // also deletes measurements
        }
    }
    
    @Override
    public void deleteExperiment() {
        if (xpDB!=null) {
            if (!xpDB.isClosed()) xpDB.close();
            xpDB=null;
            this.xpMap=null;
        }
        DBMapUtils.deleteDBFile(getConfigFile(dbName, true));
        File cfg = new File(getConfigFile(dbName, false));
        if (cfg.isFile()) cfg.delete();
    }
    
    @Override
    public void clearCache() {
        clearCache(true, true , true);
    }
    @Override 
    public void clearCache(String position) {
        DBMapObjectDAO dao = DAOs.remove(position);
        if (dao!=null) dao.clearCache();
        getExperiment().getPosition(position).flushImages(true, true);
    }
    public void clearCache(boolean xpDAO, boolean objectDAO, boolean selectionDAO) {
        if (objectDAO) {
            for (DBMapObjectDAO dao : DAOs.values()) {
                getExperiment().getPosition(dao.getPositionName()).flushImages(true, true); // input images
                dao.clearCache();
            }
            DAOs.clear();
        }
        
        if (selectionDAO && this.selectionDAO!=null) {
            getSelectionDAO().clearCache();
            this.selectionDAO=null;
        }
        if (xpDAO) {
            if (xpDB!=null) {
                if (!xpDB.isClosed()) {
                    xpDB.close();
                    logger.debug("closing: {}", this.getConfigFile(dbName, true));
                    this.xpMap=null;
                }
            }
            this.xp=null;
        }
    }
    
    public void compact() {
        for (String s : getExperiment().getPositionsAsString()) {
            GUI.log("Compacting position: "+s);
            getDao(s).compactDBs(true);
        }
        if (getSelectionDAO()!=null) getSelectionDAO().compact(true);
        if (xpDB!=null && !xpDB.isClosed()) xpDB.compact();
    }

    @Override
    public Experiment getExperiment() {
        if (this.xp==null) {
            if (new File(this.getConfigFile(dbName, false)).exists()) xp = getXPFromFile();
            else xp = getXPFromDB();
            
            // check output dir
            boolean modified = checkOutputDirectories(true);
            modified = checkOutputDirectories(false) || modified;
            if (modified) updateExperiment();
        }
        return xp;
    }
    private Experiment getXPFromDB() {
        if (xpDB==null || xpDB.isClosed()) makeXPDB();
        if (xpMap.isEmpty()) {
            logger.warn("Empty map");
            return null;
        }
        String xpString = xpMap.get("config");
        Experiment xpr = JSONUtils.parse(Experiment.class, xpString);

        Experiment xp = new Experiment(xpr.getName());
        xp.setContentFrom(xpr);
        return xp;
    }
    private Experiment getXPFromFile() {
        String file = getConfigFile(dbName, false);
        List<Experiment> xps = FileIO.readFromFile(file, o-> {
            Experiment xp = new Experiment();
            xp.initFromJSONEntry(JSONUtils.parse(o));
            return xp;
        });
        logger.debug("get xp from file: size {}", xps.size());
        if (xps.size()==1) return xps.get(0);
        else return null;
    }
    
    public boolean checkOutputDirectories(boolean image) {
        String outS = image ? xp.getOutputImageDirectory() : xp.getOutputDirectory();
        File out = outS!=null ? new File(outS) : null;
        if (out==null || !out.exists() || !out.isDirectory()) { // look for default output dir and set it up if exists
            out = new File(configDir + File.separator + "Output");
            out.mkdirs();
            if (out.isDirectory()) {
                if (image) xp.setOutputImageDirectory(out.getAbsolutePath());
                else xp.setOutputDirectory(out.getAbsolutePath());
                logger.info("Output {} directory was: {} is now : {}", image? "Image" : "",  outS, out.getAbsolutePath());
                return true;
            }
            logger.debug("default output dir: {}, exists: {}, is Dir: {}", out.getAbsolutePath(), out.exists(), out.isDirectory());
        } 
        if (!out.exists() || !out.isDirectory()) { // warn
            String message = "No "+(image?"Image":"")+" Output Directory Found, Please configure it";
            logger.warn(message);
            GUI.log(message);
        }
        return false;
    }
    
    
    @Override
    public void updateExperiment() {
        if (xp==null) return;
        //updateXPDB();
        updateXPFile();
    }
    private void updateXPFile() {
        FileIO.writeToFile(getConfigFile(dbName, false), Arrays.asList(new Experiment[]{xp}), o->o.toJSONEntry().toJSONString());
        if (new File(getConfigFile(dbName, true)).exists()) DBMapUtils.deleteDBFile(getConfigFile(dbName, true));
    }
    private void updateXPDB() {
        if (xpDB==null || xpDB.isClosed()) makeXPDB();
        xpMap.clear();
        xpMap.put("config", JSONUtils.serialize(xp));
        xpDB.commit();
        if (xpMap.isEmpty()) { // safty
            logger.error("Experiment: {} could not be saved, will try a second time");
            xpMap.put("config", JSONUtils.serialize(xp));
            xpDB.commit();
            if (xpMap.isEmpty()) {
                logger.error("Experiment: {} could not be saved, will store on separated file");
                String xpS = JSONUtils.serialize(xp);
                FileIO.writeToFile(xpS, new ArrayList<Experiment>(1){{add(xp);}}, o->JSONUtils.serialize(o));
            }
        } else logger.debug("changes saved");
    }

    @Override
    public void setExperiment(Experiment xp) {
        this.xp=xp;
        updateExperiment();
    }

    protected String getOutputPath() {
        getExperiment();
        if (xp==null) return null;
        String res = xp.getOutputDirectory();
        if (res==null) return null;
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
            if (op!=null) {
                selectionDAO = new DBMapSelectionDAO(this, op);
            }
        }
        return selectionDAO;
    }
    
}
