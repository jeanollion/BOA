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
import morphiaTest.ClassEntity;
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
            for (String s : mongo.listDatabaseNames()) {
                IJ.log("db:" + s);
            }
            
            datastore = morphia.createDatastore(mongo, "testMavenMongo");
            datastore.ensureIndexes();
            try {
                ClassEntity e = new ClassEntity("testEntity3");
                Key<ClassEntity> f = datastore.save(e);
                logger.log(Level.INFO, "entity e:{0} result key:{1}", new Object[]{e.id, f.getId()});
            } catch (Exception ex) {
                logger.log(Level.INFO, "save problem", ex);
            }
            
            Query<ClassEntity> query = datastore.createQuery(ClassEntity.class);
            List<ClassEntity> entities = query.asList();
            
            for (ClassEntity r : entities) logger.log(Level.INFO, "entity found:{0} emb object{1}", new Object[]{r.getName(), r.getEm().getName()});
            
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
