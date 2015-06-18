/*
 * To the extent possible under law, the Fiji developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

import com.mongodb.MongoClient;
import com.mongodb.MongoSocketOpenException;
import com.mongodb.client.MongoDatabase;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import morphiaTest2.ClassEntity;
import morphiaTest2.ClassEntityDerived;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.query.Query;

/**
 * ProcessPixels
 *
 * Test Mongo DB Morphia etc..
 *
 * @author Jean Ollion
 */
public class TestMavenMongo implements PlugIn {
	
        public final static Morphia morphia = new Morphia();
        public static Datastore datastore;
        protected ImagePlus image;

	// image property members
	private int width;
	private int height;

	// plugin parameters
	public double value;
	public String name;


	/**
	 * Main method for debugging.
	 *
	 * For debugging, it is convenient to have a method that starts ImageJ, loads an
	 * image and calls the plugin, e.g. after setting breakpoints.
	 *
	 * @param args unused
	 */
	public static void main(String[] args) {
		// set the plugins.dir property to make the plugin appear in the Plugins menu
		/*Class<?> clazz = TestMavenMongo.class;
		String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
		String pluginsDir = url.substring(5, url.length() - clazz.getName().length() - 6);
		System.setProperty("plugins.dir", pluginsDir);
                
		// start ImageJ
		new ImageJ();
                
		// open the Clown sample
		//ImagePlus image = IJ.openImage("http://imagej.net/images/clown.jpg");
		//image.show();

		// run the plugin
		IJ.runPlugIn(clazz.getName(), "");
                */
                
                TestMavenMongo test = new TestMavenMongo();
                test.run("");
	}
    /**
     * 
     * @param string 
     */
    @Override
    public void run(String string) {
        morphia.mapPackage("morphiaTest");
        Logger logger = Logger.getLogger("testMavenMongo");
        MongoClient mongo=new MongoClient("localhost");
        
        if (isConnected(mongo)) {
            datastore = morphia.createDatastore(mongo, "testMavenMongo");
            datastore.ensureIndexes();
            
            //datastore.save(new ClassEntity("testEntity"));
            //datastore.save(new ClassEntity("testEntity2"));
            //datastore.save(new ClassEntityDerived("DerivedtestEntity"));
            
            ClassEntityDAO dao = new ClassEntityDAO(ClassEntity.class, mongo, morphia, "testMavenMongo");
            dao.update(dao.createQuery().disableValidation().filter("className", "morphiaTest.ClassEntity"), dao.createUpdateOperations().disableValidation().set("className", ClassEntity.class.getName()));
            dao.update(dao.createQuery().disableValidation().filter("className", "morphiaTest.ClassEntityDerived"), dao.createUpdateOperations().disableValidation().set("className", ClassEntityDerived.class.getName()));
            
            for (ClassEntity r : dao.find()) {
                boolean b = (r instanceof ClassEntityDerived);
                logger.log(Level.INFO, "entity found: "+r.getName()+" instance of derived:"+b);
                if ("DerivedtestEntity".equals(r.getName())) {
                    ClassEntityDerived d = (ClassEntityDerived)r;
                    logger.log(Level.INFO, "derived entity "+d.getNewIntParameter());
                }
            }
            
            
            //ClassEntityDerivedDAO dao2 = new ClassEntityDerivedDAO(ClassEntityDerived.class, mongo, morphia, "testMavenMongo");
            //for (ClassEntityDerived r : dao2.find()) logger.log(Level.INFO, "derived entity found:{0}", new Object[]{r.getName()});
            
        }
        
    }
    /**
     * 
     * @param mongo
     * @return true is the connexion could be established with the database server
     */
    public static boolean isConnected(MongoClient mongo) {
        try {
            mongo.listDatabaseNames();
        } catch (Exception e) {
            return false;
        }
        return true;
    }
}
