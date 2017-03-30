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
package core;

import dataStructure.configuration.Experiment;
import dataStructure.objects.DBMapMasterDAO;
import dataStructure.objects.DBMapObjectDAO;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.MasterDAOFactory;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.Selection;
import dataStructure.objects.SelectionDAO;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jollion
 */
public class DbConverter {
    public static final Logger logger = LoggerFactory.getLogger(DbConverter.class);
    public static void main(String[] args) {
        mongoToDBmap("boa_fluo151127", null, "/data/Images/Fluo/film151127_dbMap1");
    }
    public static void mongoToDBmap(String dbName, String hostname, String dir) {
        MasterDAO mongo = MasterDAOFactory.createDAO(dbName, hostname, MasterDAOFactory.DAOType.Morphium);
        if (dir==null) {
            String out = mongo.getExperiment().getOutputImageDirectory();
            dir = new File(out).getParent();
        }
        MasterDAO dbMap = MasterDAOFactory.createDAO(dbName, dir, MasterDAOFactory.DAOType.DBMap);
        copy(mongo, dbMap, true);
    }
    
    public static void copy(MasterDAO source, MasterDAO dest, boolean copyXP) {
        if (copyXP) {
            Experiment xp2 = source.getExperiment().duplicate();
            if (dest instanceof DBMapMasterDAO) xp2.setOutputDirectory(dest.getDir()+File.separator+"Output");
            dest.setExperiment(xp2);
        }
        SelectionDAO sourceSelDAO = source.getSelectionDAO();
        if (sourceSelDAO!=null) {
            SelectionDAO destSelDAO = dest.getSelectionDAO();
            for (Selection s : sourceSelDAO.getSelections()) destSelDAO.store(s);
        }
        long readTime = 0;
        long writeTime = 0;
        long objectCount = 0;
        for (String position : source.getExperiment().getPositionsAsString()) {
            ObjectDAO sourceDAO = source.getDao(position);
            ObjectDAO destDAO = dest.getDao(position);
            List<StructureObject> roots=sourceDAO.getRoots();
            destDAO.store(roots, false);
            for (int sIdx = 0; sIdx<source.getExperiment().getStructureCount(); ++sIdx) {
                long tr0 = System.currentTimeMillis();
                Collection<List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(roots, sIdx).values();
                long tr1 = System.currentTimeMillis();
                
                List<StructureObject> toWrite = new ArrayList<>();
                for (List<StructureObject> list : allTracks) toWrite.addAll(list);
                objectCount+=toWrite.size();
                for (StructureObject o : toWrite) {
                    o.getObject();
                    o.getMeasurements();
                }
                readTime+=tr1-tr0;
                long t0 = System.currentTimeMillis();
                destDAO.store(toWrite, true);
                destDAO.upsertMeasurements(toWrite);
                long t1 = System.currentTimeMillis();
                writeTime+=t1-t0;
                if (destDAO instanceof DBMapObjectDAO) ((DBMapObjectDAO)destDAO).compactDBs(false);
            }
            logger.debug("xp: {}, current readTime: {} ({}), current write time: {} ({}), total object number: {}", source.getDBName(), readTime, (double)readTime/(double)objectCount, writeTime, (double)writeTime/(double)objectCount, objectCount);
        }
        
    }
}
