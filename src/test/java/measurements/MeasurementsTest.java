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
package measurements;

import dataStructure.objects.MorphiumMasterDAO;
import core.Processor;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import plugins.PluginFactory;
import plugins.plugins.measurements.BacteriaLineageIndex;
import processing.TestTrackCorrection;
import utils.MorphiumUtils;

/**
 *
 * @author jollion
 */
public class MeasurementsTest {
    MorphiumMasterDAO db;
    
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    @Before
    public void setUp() {
        PluginFactory.findPlugins("plugins.plugins");
        PluginFactory.findPlugins("testPlugins.dummyPlugins");
    }
    
    public void setUpDB() {
        if (db==null) db = new MorphiumMasterDAO(MorphiumUtils.createMorphium("testTrackCorrection"));
        db.getDao().waiteForWrites();
        db.reset();
        db.generateDAOs();
    }
    
    
    @Test
    public void testBacteriaLineageIndex() throws IOException {
        setUpDB();
        TestTrackCorrection.generateData(testFolder.newFolder(), testFolder.newFolder(), db, 100, true, true, 4, 4, 5, 6, 6, 6, 6, 7, 7, 7, 8);
        db.getExperiment().addMeasurement(new BacteriaLineageIndex(0));
        
    }
}
