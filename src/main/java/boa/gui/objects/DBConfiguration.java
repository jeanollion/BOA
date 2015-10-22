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
package boa.gui.objects;

import dataStructure.configuration.Experiment;
import dataStructure.configuration.ExperimentDAO;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;
import de.caluga.morphium.DereferencingListener;
import de.caluga.morphium.Morphium;
import utils.MorphiumUtils;

/**
 *
 * @author jollion
 */
public class DBConfiguration {
    Morphium m;
    ExperimentDAO xpDAO;
    ObjectDAO dao;
    DereferencingListener dl;
    public DBConfiguration(Morphium m) {
        this.m=m;
        generateDAOs();
    }
    public void generateDAOs() {
        this.xpDAO=new ExperimentDAO(m);
        this.dao=new ObjectDAO(m, xpDAO);
        if (dl!=null) m.removeDerrferencingListener(dl);
        dl=MorphiumUtils.addDereferencingListeners(m, dao, xpDAO);
    }

    public ExperimentDAO getXpDAO() {
        if (xpDAO==null) generateDAOs();
        return xpDAO;
    }
    public Experiment getExperiment() {
        if (xpDAO==null) generateDAOs();
        return xpDAO.getExperiment();
    }
    public ObjectDAO getDao() {
        if (dao==null) generateDAOs();
        return dao;
    }
    public void clearObjectsInDB() {
        m.clearCollection(Experiment.class);
        m.clearCollection(StructureObject.class);
    }
}
