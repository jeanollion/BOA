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
package morphia;

import com.mongodb.MongoClient;
import configuration.dataStructure.Experiment;
import configuration.dataStructure.dao.ExperimentDAO;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mongodb.morphia.Morphia;

/**
 *
 * @author jollion
 */
public class MorphiaTest {
    private Morphia morphia;
    private MongoClient mongo;
    
    @BeforeClass
    public static void setUpClass() {
        
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
        mongo=new MongoClient();
        morphia = new Morphia();
    }
    
    @After
    public void tearDown() {
    }
    
    /**
     * Test of mapping.
     */
    @org.junit.Test
    public void testMapping() throws Exception {
        try {
            morphia.mapPackage("configuration.dataStructure");
            morphia.mapPackage("configuration.parameters");
        } catch(Exception e) {
            fail("Test Morphia Mapping failed");
            throw(e);
        }
        System.out.println("morphia mapping ok");
    }
    
    /**
     * Test of connection.
     */
    @org.junit.Test
    public void testConnection() {
        try {
            mongo.listDatabaseNames();
        } catch (Exception e) {
            fail("Couldn't connect to localhost data base");
        }
        System.out.println("connection ok");
    }
    
    /**
     * Test of Experiment creation save and retrive in morphia.
     */
    @org.junit.Test
    public void testMorphiaExperiment() {
        ExperimentDAO dao = new ExperimentDAO(Experiment.class, mongo, morphia, "testMavenMongo");
        for (String id : dao.findIds()) dao.deleteById(id);
        Experiment xp = new Experiment("test maven mongo");
        dao.save(xp);
        xp = new Experiment("test maven mongo2");
        dao.save(xp);
        assertEquals(2, dao.count());
    }
    
    /**
     * Test of Id in experiments.
     */
    @org.junit.Test
    public void testMorphiaExperimentId() {
        ExperimentDAO dao = new ExperimentDAO(Experiment.class, mongo, morphia, "testMavenMongo");
        for (String id : dao.findIds()) dao.deleteById(id);
        Experiment xp = new Experiment("test maven mongo");
        dao.save(xp);
        xp = new Experiment("test maven mongo");
        dao.save(xp);
        assertEquals(1, dao.count());
    }
}
