/*
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
package dataStructure.objects.userInterface;

import configuration.userInterface.*;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.ExperimentDAO;
import dataStructure.objects.ObjectDAO;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import plugins.PluginFactory;
import utils.MorphiumUtils;

/**
 *
 * @author jollion
 */
public class TrackTreeTest extends JPanel {
    
    public TrackTreeTest() {
        super(new BorderLayout());
        TrackTreeGenerator generator = createTreeGenerator();
        
        add(new JScrollPane(generator.getTree()), BorderLayout.CENTER);
    }
    
    public static TrackTreeGenerator createTreeGenerator() {

        try {
            MorphiumConfig cfg = new MorphiumConfig();
            cfg.setDatabase("testTrack");
            cfg.addHost("localhost", 27017);
            Morphium m=new Morphium(cfg);
            
            ExperimentDAO xpDAO = new ExperimentDAO(m);
            ObjectDAO objectDAO = new ObjectDAO(m, xpDAO);
            Experiment xp = xpDAO.getExperiment();
            MorphiumUtils.addDereferencingListeners(m, objectDAO, xpDAO);
            if (xp==null) {
                xp = new Experiment("xp test UI");
                xpDAO.store(xp);
                xpDAO.clearCache();
            }
            TrackTreeGenerator generator = new TrackTreeGenerator(objectDAO, xpDAO, null);
            generator.setRootParentTrack(true, 0);
            generator.tree.setPreferredSize(new Dimension(400, 400));
            return generator;
        
        } catch (UnknownHostException ex) {
            GUI.logger.error("connexion error:", ex);
        }
        return null;
        
        
    }
    
    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Track Tree Test");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //Create and set up the content pane.
        TrackTreeTest newContentPane = new TrackTreeTest();
        newContentPane.setOpaque(true); //content panes must be opaque
        frame.setContentPane(newContentPane);

        //Display the window.
        frame.pack();
        frame.setVisible(true);
    }
    
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        //PluginFactory.findPlugins("plugins.plugins.thresholders");
        //Schedule a job for the event-dispatching thread:
        //creating and showing this application's GUI.
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                createAndShowGUI();
            }
        });
    }
}
