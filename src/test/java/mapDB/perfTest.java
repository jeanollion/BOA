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
package mapDB;

import static core.generateXP.DbConverter.copy;
import static core.generateXP.DbConverter.logger;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.MasterDAOFactory;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jollion
 */
public class perfTest {
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();
    public static final Logger logger = LoggerFactory.getLogger(perfTest.class);
    
    //@Test
    public void testIOPerf() {
        //testIOPerfDBMapToDBMap("boa_fluo151127", "/data/Images/Fluo/film151127_dbMap1");
        testIOPerfDBMapToMongo("boa_fluo151127", "/data/Images/Fluo/film151127_dbMap1");
    }
    public void testIOPerfDBMapToDBMap(String dBName, String dir) {
        MasterDAO source = MasterDAOFactory.createDAO(dBName, dir, MasterDAOFactory.DAOType.DBMap);
        MasterDAO dest = MasterDAOFactory.createDAO(dBName, testFolder.newFolder("testPerf").getAbsolutePath(), MasterDAOFactory.DAOType.DBMap);
        dest.setExperiment(source.getExperiment().duplicate());
        dest.getExperiment().setOutputDirectory(testFolder.newFolder("testPerf").getAbsolutePath());
        copy(source, dest, false);
    }
    public void testIOPerfDBMapToMongo(String dBName, String dir) {
        MasterDAO source = MasterDAOFactory.createDAO(dBName, dir, MasterDAOFactory.DAOType.DBMap);
        MasterDAO dest = MasterDAOFactory.createDAO(dBName+"test1", null, MasterDAOFactory.DAOType.Morphium);
        dest.setExperiment(source.getExperiment().duplicate());
        dest.getExperiment().setOutputDirectory(testFolder.newFolder("testPerf").getAbsolutePath());
        copy(source, dest, false);
        dest.delete();
    }

}
