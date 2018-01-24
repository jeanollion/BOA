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
package boa.data_structure.dao;

import boa.configuration.experiment.Experiment;
import java.util.HashMap;

/**
 *
 * @author jollion
 */
public class BasicMasterDAO implements MasterDAO {
    final HashMap<String, BasicObjectDAO> DAOs;
    Experiment xp;
    public BasicMasterDAO() {
        this.DAOs = new HashMap<String, BasicObjectDAO>();
    }
    
    public BasicMasterDAO(Experiment xp) {
        this();
        this.xp=xp;
    }
    
    public BasicObjectDAO getDao(String fieldName) {
        BasicObjectDAO dao = DAOs.get(fieldName);
        if (dao==null) {
            dao = new BasicObjectDAO(this, fieldName);
            DAOs.put(fieldName, dao);
        }
        return dao;
    }
    
    @Override
    public void eraseAll() {}
    
    public String getDBName() {
        return "VirtualDB";
    }
    @Override
    public String getDir() {
        return "VirtualDB";
    }

    public void deleteAllObjects() {
        for (BasicObjectDAO d : DAOs.values()) d.deleteAllObjects();
    }

    public void reset() {
        deleteAllObjects();
        this.xp=null;
    }

    public Experiment getExperiment() {
        return xp;
    }

    public void updateExperiment() {
    }

    @Override 
    public boolean experimentChanged() {
        return false;
    }
    
    public void setExperiment(Experiment xp) {
        this.xp=xp;
    }

    public SelectionDAO getSelectionDAO() {
        return null;
    }

    @Override
    public void clearCache(String position) {
        this.DAOs.remove(position);
        getExperiment().getPosition(position).flushImages(true, true);
    }
    
    @Override
    public void clearCache() {
        //this.DAOs.clear();
        //this.xp=null;
    }

    @Override
    public void deleteExperiment() {
        xp=null;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }
    @Override 
    public boolean setReadOnly(boolean readOnly) {
        return false;
    }
}