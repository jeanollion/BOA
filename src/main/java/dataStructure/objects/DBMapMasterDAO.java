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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
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
    protected Experiment xp;
    java.nio.channels.FileLock xpFileLock;
    RandomAccessFile cfg;
    DBMapSelectionDAO selectionDAO;
    boolean readOnly = false;
    public DBMapMasterDAO(String dir, String dbName) {
        if (dir==null) throw new IllegalArgumentException("Invalid directory: "+ dir);
        if (dbName==null) throw new IllegalArgumentException("Invalid DbName: "+ dbName);
        logger.debug("create DBMAPMASTERDAO: dir: {}, dbName: {}", dir, dbName);
        configDir = dir;
        new File(configDir).mkdirs();
        this.dbName = dbName;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }
    
    @Override
    public void eraseAll() {
        if (readOnly) return;
        String outputPath = getExperiment()!=null ? getExperiment().getOutputDirectory() : null;
        String outputImagePath = getExperiment()!=null ? getExperiment().getOutputImageDirectory() : null;
        clearCache();
        Utils.deleteDirectory(outputPath);
        Utils.deleteDirectory(outputImagePath);
        deleteExperiment();
        new File(configDir).delete(); // deletes XP directory only if void. 
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
            res = new DBMapObjectDAO(this, fieldName, op, readOnly);
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
        if (readOnly) return;
        if (getExperiment()==null) return;
        for (String s : getExperiment().getPositionsAsString()) {
            getDao(s).deleteAllObjects(); // also deletes measurements
        }
    }
    
    @Override
    public void deleteExperiment() {
        if (readOnly) return;
        unlockXP();
        File cfg = new File(getConfigFile(dbName, false));
        if (cfg.isFile()) cfg.delete();
    }

    private synchronized void lockXP() {
        if (xpFileLock!=null) return;
        try {
            logger.debug("locking file: {} (xp null? {})", getConfigFile(dbName, false), xp==null);
            cfg = new RandomAccessFile(new File(getConfigFile(dbName, false)), "rw");
            xpFileLock = cfg.getChannel().tryLock();
            //logger.debug("lock at creation: {}, for file: {}", xpFileLock, getConfigFile(dbName, false));
        } catch (FileNotFoundException ex) {
            logger.debug("no config file found!");
        } catch (OverlappingFileLockException e) {
            logger.debug("file already locked", e);
        } catch (IOException ex) {
            logger.debug("File could not be locked", ex);
        }
    }
    private synchronized void unlockXP() {
        if (this.xpFileLock!=null) {
            try {
                logger.debug("realising lock: {}", xpFileLock);
                xpFileLock.release();
                xpFileLock = null;
            } catch (IOException ex) {
                logger.debug("error realeasing xp lock", ex);
            }
        }
        if (cfg!=null) {
            try {
                cfg.close();
            } catch (IOException ex) {
                logger.debug("could not close config file", ex);
            }
        }
        
    }
    
    @Override
    public void clearCache() {
        logger.debug("clearing cache...");
        clearCache(true, true , true);
    }
    @Override 
    public void clearCache(String position) {
        DBMapObjectDAO dao = DAOs.remove(position);
        if (dao!=null) dao.clearCache();
        getExperiment().getPosition(position).flushImages(true, true);
    }
    public synchronized void clearCache(boolean xpDAO, boolean objectDAO, boolean selectionDAO) {
        if (objectDAO) {
            for (DBMapObjectDAO dao : DAOs.values()) {
                if (getExperiment().getPosition(dao.getPositionName())!=null) getExperiment().getPosition(dao.getPositionName()).flushImages(true, true); // input images
                dao.clearCache();
            }
            DAOs.clear();
        }
        
        if (selectionDAO && this.selectionDAO!=null) {
            getSelectionDAO().clearCache();
            this.selectionDAO=null;
        }
        if (xpDAO) {
            this.unlockXP();
            this.xp=null;
        }
    }
    
    public void compact() {
        for (String s : getExperiment().getPositionsAsString()) {
            GUI.log("Compacting position: "+s);
            getDao(s).compactDBs(true);
        }
        if (getSelectionDAO()!=null) getSelectionDAO().compact(true);
    }

    @Override
    public Experiment getExperiment() {
        if (this.xp==null) {
            synchronized(this) {
                if (xp==null) {
                    if (!new File(this.getConfigFile(dbName, false)).exists()) return null;
                    this.lockXP();
                    if (xpFileLock==null) {
                        logger.warn(dbName+ ": Config file could not be locked. Experiment already opened ? Experiment will be opened in ReadOnly mode");
                        GUI.log(dbName+ ": Config file could not be locked. Experiment already opened ? Experiment will be opened in ReadOnly mode");
                        readOnly = true;
                        //return null;
                    }
                    xp = getXPFromFile();

                    // check output dir & set default if necessary
                    boolean modified = checkOutputDirectories(true);
                    modified = checkOutputDirectories(false) || modified;
                    if (modified) updateExperiment();
                } else return xp;
            }
        }
        return xp;
    }

    private Experiment getXPFromFile() {
        if (cfg==null) return null;
        String xpString;
        try {
            cfg.seek(0);
            xpString = cfg.readLine();
        } catch (IOException ex) {
            logger.debug("couldnot read config file: ", ex);
            return null;
        }
        Experiment xp = new Experiment();
        xp.initFromJSONEntry(JSONUtils.parse(xpString));
        return xp;
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
        if (xp==null || readOnly) return;
        //updateXPDB();
        updateXPFile();
    }
    private void updateXPFile() {
        logger.debug("updating xp file..");
        if (xp!=null && cfg!=null) {
            try {
                FileIO.write(cfg, xp.toJSONEntry().toJSONString(), false);
            } catch (IOException ex) {
                logger.error("Could not update experiment", ex);
            }
        }
        
        //FileIO.writeToFile(getConfigFile(dbName, false), Arrays.asList(new Experiment[]{xp}), o->o.toJSONEntry().toJSONString());
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
                selectionDAO = new DBMapSelectionDAO(this, op, readOnly);
            }
        }
        return selectionDAO;
    }
    
}
