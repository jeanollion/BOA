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

/**
 *
 * @author jollion
 */
import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.mongodb.morphia.AdvancedDatastore;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;

@SuppressWarnings("deprecation")
public abstract class TestBase {

    private final MongoClient mongoClient;
    private final Morphia morphia = new Morphia();
    private DB db;
    private Datastore ds;
    private AdvancedDatastore ads;

    protected TestBase() {
        try {
            mongoClient = new MongoClient(new MongoClientURI(System.getProperty("MONGO_URI", "mongodb://localhost:27017")));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public AdvancedDatastore getAds() {
        return ads;
    }

    public void setAds(final AdvancedDatastore ads) {
        this.ads = ads;
    }

    public DB getDb() {
        return db;
    }

    public void setDb(final DB db) {
        this.db = db;
    }

    public Datastore getDs() {
        return ds;
    }

    public void setDs(final Datastore ds) {
        this.ds = ds;
    }

    public MongoClient getMongoClient() {
        return mongoClient;
    }

    public Morphia getMorphia() {
        return morphia;
    }

    public boolean isReplicaSet() {
        return runIsMaster().get("setName") != null;
    }

    @Before
    public void setUp() {
        setDb(getMongoClient().getDB("morphia_test"));
        setDs(getMorphia().createDatastore(getMongoClient(), getDb().getName()));
        setAds((AdvancedDatastore) getDs());
        cleanup();
    }

    @After
    public void tearDown() {
        cleanup();
        getMongoClient().close();
    }

    protected void checkMaxServerVersion(final double version) {
        Assume.assumeTrue(serverIsAtMostVersion(version));
    }

    protected void checkMinServerVersion(final double version) {
        Assume.assumeTrue(serverIsAtLeastVersion(version));
    }

    protected void cleanup() {
        DB db = getDb();
        if (db != null) {
            db.dropDatabase();
        }
    }

    /**
     * @param version must be a major version, e.g. 1.8, 2,0, 2.2
     * @return true if server is at least specified version
     */
    protected boolean serverIsAtLeastVersion(final double version) {
        String serverVersion = (String) getMongoClient().getDB("admin").command("serverStatus").get("version");
        return Double.parseDouble(serverVersion.substring(0, 3)) >= version;
    }

    /**
     * @param version must be a major version, e.g. 1.8, 2,0, 2.2
     * @return true if server is at least specified version
     */
    protected boolean serverIsAtMostVersion(final double version) {
        String serverVersion = (String) getMongoClient().getDB("admin").command("serverStatus").get("version");
        return Double.parseDouble(serverVersion.substring(0, 3)) <= version;
    }

    private CommandResult runIsMaster() {
// Check to see if this is a replica set... if not, get out of here.
        return mongoClient.getDB("admin").command(new BasicDBObject("ismaster", 1));
    }
}
