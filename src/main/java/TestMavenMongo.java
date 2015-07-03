/*
 * To the extent possible under law, the Fiji developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

import com.mongodb.MongoClient;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.Structure;
import dataStructure.configuration.ExperimentDAO;
import configuration.parameters.SimpleListParameter;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;

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
		ImagePlus image = IJ.openImage("http://imagej.net/images/clown.jpg");
		image.show();

		// run the plugin
		IJ.runPlugIn(clazz.getName(), "");
                */
                
                //TestMavenMongo test = new TestMavenMongo();
                //test.run("");
                //test.testExperiment();
	}
    /**
     * 
     * @param string 
     */
    @Override
    public void run(String string) {
        System.out.println("run ok!");
        
    }
    
    public void testExperiment() {
        morphia.mapPackage("configuration.dataStructure");
        morphia.mapPackage("configuration.parameters");
        Logger logger = Logger.getLogger("testMavenMongo");
        MongoClient mongo=new MongoClient("localhost");
        ExperimentDAO dao = new ExperimentDAO(Experiment.class, mongo, morphia, "testMavenMongo");
        if (isConnected(mongo)) {
            //if (dao.count()==0) { // remove 
                Experiment exp = new Experiment("test morphia2");
                dao.save(exp);
            //}
            for (Experiment xp : dao.find()) {
                logger.log(Level.INFO, "entity found: "+xp.toString()+ " childs nb:"+xp.getChildCount());
                SimpleListParameter list = xp.getStructures();
                if (list.getChildCount()>0) {
                    Structure s = (Structure)list.getChildAt(0);
                    logger.log(Level.INFO, "structure 0: "+s.toString());
                }
            }
            
            
            //ClassEntityDerivedDAO dao2 = new ClassEntityDerivedDAO(ClassEntityDerived.class, mongo, morphia, "testMavenMongo");
            //for (ClassEntityDerived r : dao2.find()) logger.log(Level.INFO, "derived entity found:{0}", new Object[]{r.getName()});
            
        } else {
            logger.log(Level.SEVERE, "no connection to database");
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
