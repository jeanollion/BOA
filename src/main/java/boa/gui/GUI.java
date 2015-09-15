/*
 * Copyright (C) 2015 nasique
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
package boa.gui;

import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageObjectListener;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.ExperimentDAO;
import dataStructure.objects.ObjectDAO;
import boa.gui.objects.StructureObjectTreeGenerator;
import boa.gui.objects.TrackNode;
import boa.gui.objects.TrackTreeController;
import boa.gui.objects.TrackTreeGenerator;
import configuration.parameters.NumberParameter;
import core.Processor;
import dataStructure.configuration.ChannelImage;
import dataStructure.configuration.Structure;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import image.ImageByte;
import image.ImageFormat;
import image.ImageWriter;
import java.awt.Dimension;
import java.io.File;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.logging.Level;
import javax.swing.ComboBoxModel;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import plugins.PluginFactory;
import plugins.plugins.trackers.TrackerObjectIdx;
import plugins.plugins.segmenters.SimpleThresholder;
import plugins.plugins.thresholders.ConstantValue;
import utils.MorphiumUtils;
import static utils.Utils.addHorizontalScrollBar;

/**
 *
 * @author nasique
 */
public class GUI extends javax.swing.JFrame implements ImageObjectListener {
    public static final Logger logger = LoggerFactory.getLogger(GUI.class);
    private static GUI instance;
    
    // db-related attributes
    ExperimentDAO xpDAO;
    ObjectDAO objectDAO;
    Morphium m;
    
    // track-related attributes
    TrackTreeController trackTreeController;
    private HashMap<Integer, JTree> currentTrees;
    
    // structure-related attributes
    StructureObjectTreeGenerator structureObjectTreeGenerator;
    
    /**
     * Creates new form GUI
     */
    public GUI() {
        this.instance=this;
        initComponents();
        addHorizontalScrollBar(trackStructureJCB);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        //initDB();
        this.initDBImages();
        structureObjectTreeGenerator = new StructureObjectTreeGenerator(objectDAO, xpDAO);
        structureJSP.setViewportView(structureObjectTreeGenerator.getTree());
        structureJSP.getViewport().setOpaque(false);
        structureJSP.setOpaque(false);
        
        trackTreeController = new TrackTreeController(objectDAO, xpDAO, structureObjectTreeGenerator);
        setTrackTreeStructures(xpDAO.getExperiment().getStructuresAsString());
    }
    
    public static GUI getInstance() {
        if (instance==null) new GUI();
        return instance;
    }
    
    // ImageObjectListener implementation
    public void fireObjectSelected(StructureObject selectedObject, boolean track) {
        if (track) {
            // selection de la track
            
        }
        // selection de l'objet dans l'arbre d'objets
        structureObjectTreeGenerator.selectObject(selectedObject);
        logger.trace("fire object selected");
    }
    
    private void setTrackTreeStructures(String[] structureNames) {
        this.trackStructureJCB.removeAllItems();
        for (String s: structureNames) trackStructureJCB.addItem(s);
        if (structureNames.length>0) {
            trackStructureJCB.setSelectedIndex(0);
            setStructure(0);
        }
    }
    
    private void setStructure(int structureIdx) {
        trackTreeController.setStructure(structureIdx);
        displayTrackTrees();
    }
    
    public void displayTrackTrees() {
        this.trackSubPanel.removeAll();
        HashMap<Integer, JTree> newCurrentTrees = new HashMap<Integer, JTree>(trackTreeController.getGeneratorS().size());
        for (Entry<Integer, TrackTreeGenerator> e : trackTreeController.getGeneratorS().entrySet()) {
            final Entry<Integer, TrackTreeGenerator> entry = e;
            final JTree tree = entry.getValue().getTree();
            if (tree!=null) {
                if (currentTrees==null || !currentTrees.containsValue(tree)) {
                    removeTreeSelectionListeners(tree);
                    tree.addTreeSelectionListener(new TreeSelectionListener() {
                        @Override
                        public void valueChanged(TreeSelectionEvent e) {
                            if (logger.isDebugEnabled()) {
                                logger.debug("selection changed on tree of structure: {} event: {}", entry.getKey(), e);
                            }
                            if (trackTreeController == null) {
                                return;
                            }
                            if (tree.getSelectionCount() == 1 && tree.getSelectionPath().getLastPathComponent() instanceof TrackNode) {
                                trackTreeController.updateParentTracks(trackTreeController.getTreeIdx(entry.getKey()));
                            } else {
                                trackTreeController.clearTreesFromIdx(trackTreeController.getTreeIdx(entry.getKey()) + 1);
                            }
                            instance.displayTrackTrees();
                        }
                    });
                    //tree.setPreferredSize(new Dimension(200, 400));
                }
                /*JScrollPane jsp = new JScrollPane(tree);
                jsp.getViewport().setOpaque(false);
                jsp.setOpaque(false);*/
                trackSubPanel.add(tree);
                newCurrentTrees.put(e.getKey(), tree);
            }
        }
        currentTrees = newCurrentTrees;
        logger.trace("display track tree: number of trees: {} subpanel component count: {}",trackTreeController.getGeneratorS().size(), trackSubPanel.getComponentCount() );
        trackSubPanel.revalidate();
        trackSubPanel.repaint();
    }
    
    private void initDB() {
        try {
            MorphiumConfig cfg = new MorphiumConfig();
            cfg.setDatabase("testdb");
            cfg.addHost("localhost", 27017);
            m=new Morphium(cfg);
            xpDAO = new ExperimentDAO(m);
            objectDAO = new ObjectDAO(m, xpDAO);
            Experiment xp = xpDAO.getExperiment();
            MorphiumUtils.addDereferencingListeners(m, objectDAO, xpDAO);
            if (xp==null) {
                xp = new Experiment("xp test UI");
                xpDAO.store(xp);
                xpDAO.clearCache();
            }
            
            //.setPreferredSize(new Dimension(400, 400));
        } catch (UnknownHostException ex) {
            logger.error("db connection error", ex);
        }
    }
    
    private void initDBImages() {
        try {
            MorphiumConfig cfg = new MorphiumConfig();
            cfg.setDatabase("testGUI");
            cfg.addHost("localhost", 27017);
            m=new Morphium(cfg);
            m.clearCollection(Experiment.class);
            m.clearCollection(StructureObject.class);
            xpDAO = new ExperimentDAO(m);
            objectDAO = new ObjectDAO(m, xpDAO);
            MorphiumUtils.addDereferencingListeners(m, objectDAO, xpDAO);
            
            // generate XP
            Experiment xp = new Experiment("test");
            ChannelImage cMic = new ChannelImage("ChannelImageMicroChannel");
            xp.getChannelImages().insert(cMic);
            ChannelImage cBact = new ChannelImage("ChannelImageBact");
            xp.getChannelImages().insert(cBact);
            xp.getStructures().removeAllElements();
            Structure microChannel = new Structure("MicroChannel", -1, 0);
            Structure bacteries = new Structure("Bacteries", 0, 1);
            xp.getStructures().insert(microChannel, bacteries);
            bacteries.setParentStructure(0);
            
            // processing chains
            PluginFactory.findPlugins("plugins.plugins");
            microChannel.getProcessingChain().setSegmenter(new SimpleThresholder(new ConstantValue()));
            bacteries.getProcessingChain().setSegmenter(new SimpleThresholder(new ConstantValue()));
            
            // set-up traking
            microChannel.setTracker(new TrackerObjectIdx());
            bacteries.setTracker(new TrackerObjectIdx());
            
            // set up fields
            Processor.importFiles(new String[]{"/data/Images/Test/syntheticData.ome.tiff"}, xp);
            xp.setOutputImageDirectory("/data/Images/Test/Output");
            
            // save to morphium
            xpDAO.store(xp);
            
            // process
            Processor.preProcessImages(xp);
            StructureObject[] root = xp.getMicroscopyField(0).createRootObjects();
            objectDAO.store(root); 
            Processor.trackRoot(xp, root, objectDAO);
            for (int s : xp.getStructuresInHierarchicalOrderAsArray()) {
                for (int t = 0; t<root.length; ++t) Processor.processStructure(s, root[t], objectDAO); // process
                for (StructureObject o : StructureObjectUtils.getAllParentObjects(root[0], xp.getPathToRoot(s))) Processor.track(xp, xp.getStructure(s).getTracker(), o, s, objectDAO); // structure
            }
            
        } catch (UnknownHostException ex) {
            logger.error("db connection error", ex);
        }
        
        
        
    }

    private static void removeTreeSelectionListeners(JTree tree) {
        for (TreeSelectionListener t : tree.getTreeSelectionListeners()) tree.removeTreeSelectionListener(t);
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jTabbedPane1 = new javax.swing.JTabbedPane();
        ConfigurationPanel = new javax.swing.JPanel();
        DataPanel = new javax.swing.JPanel();
        ControlPanel = new javax.swing.JPanel();
        trackStructureJCB = new javax.swing.JComboBox();
        ObjectTreeJSP = new javax.swing.JSplitPane();
        StructurePanel = new javax.swing.JPanel();
        structureJSP = new javax.swing.JScrollPane();
        TimePanel = new javax.swing.JPanel();
        TimeJSP = new javax.swing.JScrollPane();
        trackSubPanel = new javax.swing.JPanel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        javax.swing.GroupLayout ConfigurationPanelLayout = new javax.swing.GroupLayout(ConfigurationPanel);
        ConfigurationPanel.setLayout(ConfigurationPanelLayout);
        ConfigurationPanelLayout.setHorizontalGroup(
            ConfigurationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 641, Short.MAX_VALUE)
        );
        ConfigurationPanelLayout.setVerticalGroup(
            ConfigurationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 448, Short.MAX_VALUE)
        );

        jTabbedPane1.addTab("Configuration", ConfigurationPanel);

        ControlPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createTitledBorder("Controls")));

        trackStructureJCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                trackStructureJCBActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout ControlPanelLayout = new javax.swing.GroupLayout(ControlPanel);
        ControlPanel.setLayout(ControlPanelLayout);
        ControlPanelLayout.setHorizontalGroup(
            ControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(trackStructureJCB, 0, 162, Short.MAX_VALUE)
        );
        ControlPanelLayout.setVerticalGroup(
            ControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ControlPanelLayout.createSequentialGroup()
                .addComponent(trackStructureJCB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        StructurePanel.setBorder(javax.swing.BorderFactory.createTitledBorder("SegmentedObjects"));

        javax.swing.GroupLayout StructurePanelLayout = new javax.swing.GroupLayout(StructurePanel);
        StructurePanel.setLayout(StructurePanelLayout);
        StructurePanelLayout.setHorizontalGroup(
            StructurePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(structureJSP, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 100, Short.MAX_VALUE)
        );
        StructurePanelLayout.setVerticalGroup(
            StructurePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(structureJSP, javax.swing.GroupLayout.DEFAULT_SIZE, 401, Short.MAX_VALUE)
        );

        ObjectTreeJSP.setLeftComponent(StructurePanel);
        StructurePanel.getAccessibleContext().setAccessibleName("Segmented Objects");

        TimePanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Tracks"));

        trackSubPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEADING));
        TimeJSP.setViewportView(trackSubPanel);

        javax.swing.GroupLayout TimePanelLayout = new javax.swing.GroupLayout(TimePanel);
        TimePanel.setLayout(TimePanelLayout);
        TimePanelLayout.setHorizontalGroup(
            TimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(TimeJSP, javax.swing.GroupLayout.DEFAULT_SIZE, 319, Short.MAX_VALUE)
        );
        TimePanelLayout.setVerticalGroup(
            TimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(TimeJSP, javax.swing.GroupLayout.DEFAULT_SIZE, 401, Short.MAX_VALUE)
        );

        ObjectTreeJSP.setRightComponent(TimePanel);

        javax.swing.GroupLayout DataPanelLayout = new javax.swing.GroupLayout(DataPanel);
        DataPanel.setLayout(DataPanelLayout);
        DataPanelLayout.setHorizontalGroup(
            DataPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(DataPanelLayout.createSequentialGroup()
                .addComponent(ControlPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ObjectTreeJSP)
                .addContainerGap())
        );
        DataPanelLayout.setVerticalGroup(
            DataPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(DataPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(DataPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(ControlPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(ObjectTreeJSP))
                .addContainerGap())
        );

        jTabbedPane1.addTab("Data Browsing", DataPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void trackStructureJCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_trackStructureJCBActionPerformed
        
        logger.debug("trackStructureJCBActionPerformed: selected index: {} action event: {}", trackStructureJCB.getSelectedIndex(), evt);
        this.setStructure(this.trackStructureJCB.getSelectedIndex());
    }//GEN-LAST:event_trackStructureJCBActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(GUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(GUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(GUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(GUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new GUI().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel ConfigurationPanel;
    private javax.swing.JPanel ControlPanel;
    private javax.swing.JPanel DataPanel;
    private javax.swing.JSplitPane ObjectTreeJSP;
    private javax.swing.JPanel StructurePanel;
    private javax.swing.JScrollPane TimeJSP;
    private javax.swing.JPanel TimePanel;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JScrollPane structureJSP;
    private javax.swing.JComboBox trackStructureJCB;
    private javax.swing.JPanel trackSubPanel;
    // End of variables declaration//GEN-END:variables

    
}
