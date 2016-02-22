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

import dataStructure.configuration.Experiment;
import dataStructure.configuration.ExperimentDAO;
import de.caluga.morphium.DereferencingListener;
import de.caluga.morphium.Morphium;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import utils.MorphiumUtils;

/**
 *
 * @author jollion
 */
public class MorphiumMasterDAO implements MasterDAO {
    final Morphium m;
    final ExperimentDAO xpDAO;
    final MorphiumSelectionDAO selectionDAO;
    DereferencingListener dl;
    final HashMap<String, MorphiumObjectDAO> DAOs;
    public MorphiumMasterDAO(Morphium m) {
        this.m=m;
        this.xpDAO=new ExperimentDAO(m);
        this.selectionDAO=new MorphiumSelectionDAO(this);
        this.DAOs=new HashMap<String, MorphiumObjectDAO>();
        if (dl!=null) m.removeDerrferencingListener(dl);
        dl=MorphiumUtils.addDereferencingListeners(m, xpDAO);
    }
    
    public MorphiumMasterDAO(String dbName) {
        this(MorphiumUtils.createMorphium(dbName));
    }

    @Override public String getDBName() {
        return m.getDatabase().getName();
    }
    
    @Override public void updateExperiment() {
        xpDAO.store(getExperiment());
    }
    
    @Override public Experiment getExperiment() {
        return xpDAO.getExperiment();
    }
    
    @Override public void deleteAllObjects() {
        this.DAOs.clear();
        Set<String> collections = m.getDatabase().getCollectionNames();
        for (String c : collections) {
            if (c.startsWith("objects_")) m.dropCollection(StructureObject.class, c, null);
            else if (c.startsWith("measurements_")) m.dropCollection(Measurements.class, c, null);
        }
    }
     
    
    @Override public void reset() {
        m.clearCollection(Experiment.class);
        this.xpDAO.clearCache();
        this.deleteAllObjects();
    }
    
    public Morphium getMorphium() {
        return m;
    }

    public MorphiumObjectDAO getDao(String fieldName) {
        MorphiumObjectDAO dao = DAOs.get(fieldName);
        if (dao==null) {
            dao = new MorphiumObjectDAO(this, fieldName);
            DAOs.put(fieldName, dao);
        }
        return dao;
    }
    
    @Override
    public void setExperiment(Experiment xp) {
        xpDAO.store(xp);
    }
    
    public ExperimentDAO getXpDAO() {return this.xpDAO;}

    public SelectionDAO getSelectionDAO() {
        return this.selectionDAO;
    }


}
