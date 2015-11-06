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

import boa.gui.configuration.ConfigurationTreeGenerator;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageObjectListener;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import static boa.gui.imageInteraction.ImageWindowManagerFactory.getImageManager;
import boa.gui.objects.DBConfiguration;
import boa.gui.objects.ObjectNode;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.ExperimentDAO;
import dataStructure.objects.ObjectDAO;
import boa.gui.objects.StructureObjectTreeGenerator;
import boa.gui.objects.TrackNode;
import boa.gui.objects.TrackTreeController;
import boa.gui.objects.TrackTreeGenerator;
import configuration.parameters.FileChooser;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.logging.Level;
import javax.swing.ComboBoxModel;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import plugins.ObjectSplitter;
import plugins.PluginFactory;
import plugins.plugins.trackers.ObjectIdxTracker;
import plugins.plugins.segmenters.SimpleThresholder;
import plugins.plugins.thresholders.ConstantValue;
import utils.MorphiumUtils;
import utils.Utils;
import static utils.Utils.addHorizontalScrollBar;

/**
 *
 * @author jollion
 */
public class GUI extends javax.swing.JFrame implements ImageObjectListener {
    public static final Logger logger = LoggerFactory.getLogger(GUI.class);
    private static GUI instance;
    
    // db-related attributes
    DBConfiguration db;
    
    // xp tree-related attributes
    ConfigurationTreeGenerator configurationTreeGenerator;
    
    //Object Tree related attributes
    boolean reloadTree=false;
    // track-related attributes
    TrackTreeController trackTreeController;
    private HashMap<Integer, JTree> currentTrees;
    // structure-related attributes
    StructureObjectTreeGenerator objectTreeGenerator;
    
    /**
     * Creates new form GUI
     */
    public GUI() {
        this.instance=this;
        initComponents();
        
        addHorizontalScrollBar(trackStructureJCB);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        PluginFactory.findPlugins("plugins.plugins");
        jTabbedPane1.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if (reloadTree && jTabbedPane1.getSelectedComponent()==DataPanel) {
                    reloadTree=false;
                    loadObjectTrees();
                }
            }
        });
    }
    
    public void setDBConnection(String dbName, String hostname) {
        Morphium m=MorphiumUtils.createMorphium(hostname, 27017, dbName);
        if (m==null) return;
        logger.info("Connection established with db: {} @ host: {}", dbName, hostName);
        setDBConnection(m);
    }
    
    public void setDBConnection(Morphium m) {
        if (m==null) return;
        this.db = new DBConfiguration(m);
        if (db.getExperiment()==null) {
            logger.warn("no experiment found in DB, using dummy experiment");
            Experiment xp = new Experiment("xp test UI");
            db.getXpDAO().store(xp);
            db.getXpDAO().clearCache();
        } else logger.info("Experiment found: {}", db.getExperiment().getName());
        
        configurationTreeGenerator = new ConfigurationTreeGenerator(db.getXpDAO());
        configurationJSP.setViewportView(configurationTreeGenerator.getTree());
        
        loadObjectTrees();
    }
    
    protected void loadObjectTrees() {
        //TODO remember treepath if existing and set them in the new trees
        
        objectTreeGenerator = new StructureObjectTreeGenerator(db);
        structureJSP.setViewportView(objectTreeGenerator.getTree());
        
        trackTreeController = new TrackTreeController(db, objectTreeGenerator);
        setTrackTreeStructures(db.getExperiment().getStructuresAsString());
    }
    
    public static GUI getInstance() {
        if (instance==null) new GUI();
        return instance;
    }
    
    // ImageObjectListener implementation
    public void fireObjectSelected(StructureObject selectedObject, boolean addToSelection, boolean track) {
        if (track) {
            // selection de la track
            
        }
        // selection de l'objet dans l'arbre d'objets
        objectTreeGenerator.selectObject(selectedObject, addToSelection);
        logger.trace("fire object selected");
    }
    
    private void setTrackTreeStructures(String[] structureNames) {
        this.trackStructureJCB.removeAllItems();
        this.interactiveStructure.removeAllItems();
        for (String s: structureNames) {
            trackStructureJCB.addItem(s);
            interactiveStructure.addItem(s);
        }
        if (structureNames.length>0) {
            trackStructureJCB.setSelectedIndex(0);
            interactiveStructure.setSelectedIndex(0);
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
                tree.setAlignmentY(TOP_ALIGNMENT);
                trackSubPanel.add(tree);
                newCurrentTrees.put(e.getKey(), tree);
            }
        }
        currentTrees = newCurrentTrees;
        logger.trace("display track tree: number of trees: {} subpanel component count: {}",trackTreeController.getGeneratorS().size(), trackSubPanel.getComponentCount() );
        trackSubPanel.revalidate();
        trackSubPanel.repaint();
    }
    
    
    /*private void initDBImages() {
            m=MorphiumUtils.createMorphium("testGUI");
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
            microChannel.setTracker(new ObjectIdxTracker());
            bacteries.setTracker(new ObjectIdxTracker());
            
            // set up fields
            Processor.importFiles(new String[]{"/data/Images/Test/syntheticData.ome.tiff"}, xp);
            xp.setOutputImageDirectory("/data/Images/Test/Output");
            
            // save to morphium
            xpDAO.store(xp);
            
            // process
            Processor.preProcessImages(xp, objectDAO, true);
            ArrayList<StructureObject> root = xp.getMicroscopyField(0).createRootObjects();
            objectDAO.store(root); 
            Processor.trackRoot(root, objectDAO);
            for (int s : xp.getStructuresInHierarchicalOrderAsArray()) {
                for (int t = 0; t<root.size(); ++t) Processor.processStructure(s, root.get(t), objectDAO, false); // process
                for (StructureObject o : StructureObjectUtils.getAllParentObjects(root.get(0), xp.getPathToRoot(s))) Processor.track(xp.getStructure(s).getTracker(), o, s, objectDAO); // structure
            }
    }*/

    private static void removeTreeSelectionListeners(JTree tree) {
        for (TreeSelectionListener t : tree.getTreeSelectionListeners()) tree.removeTreeSelectionListener(t);
    }
    
    private boolean checkConnection() {
        if (this.db==null) {
            logger.error("Connect to DB and create experiment first");
            return false;
        } else return true;
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
        ActionPanl = new javax.swing.JPanel();
        segmentButton = new javax.swing.JButton();
        importImageButton = new javax.swing.JButton();
        connectButton = new javax.swing.JButton();
        hostName = new javax.swing.JTextField();
        preProcessButton = new javax.swing.JButton();
        saveExperiment = new javax.swing.JButton();
        reProcess = new javax.swing.JButton();
        ConfigurationPanel = new javax.swing.JPanel();
        configurationJSP = new javax.swing.JScrollPane();
        DataPanel = new javax.swing.JPanel();
        ControlPanel = new javax.swing.JPanel();
        trackStructureJCB = new javax.swing.JComboBox();
        selectAllTracksButton = new javax.swing.JButton();
        collapseAllObjectButton = new javax.swing.JButton();
        nextTrackErrorButton = new javax.swing.JButton();
        selectContainingTrackToggleButton = new javax.swing.JToggleButton();
        splitObjectButton = new javax.swing.JButton();
        mergeObjectsButton = new javax.swing.JButton();
        previousTrackErrorButton = new javax.swing.JButton();
        interactiveStructure = new javax.swing.JComboBox();
        selectAllObjects = new javax.swing.JButton();
        ObjectTreeJSP = new javax.swing.JSplitPane();
        StructurePanel = new javax.swing.JPanel();
        structureJSP = new javax.swing.JScrollPane();
        TimePanel = new javax.swing.JPanel();
        TimeJSP = new javax.swing.JScrollPane();
        trackSubPanel = new javax.swing.JPanel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        segmentButton.setText("Segment");
        segmentButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                segmentButtonActionPerformed(evt);
            }
        });

        importImageButton.setText("Import Images");
        importImageButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importImageButtonActionPerformed(evt);
            }
        });

        connectButton.setText("Connect");
        connectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                connectButtonActionPerformed(evt);
            }
        });

        hostName.setText("localhost");

        preProcessButton.setText("PreProcess");
        preProcessButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                preProcessButtonActionPerformed(evt);
            }
        });

        saveExperiment.setText("Save Configuration Changes");
        saveExperiment.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveExperimentActionPerformed(evt);
            }
        });

        reProcess.setText("ReProcess");
        reProcess.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                reProcessActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout ActionPanlLayout = new javax.swing.GroupLayout(ActionPanl);
        ActionPanl.setLayout(ActionPanlLayout);
        ActionPanlLayout.setHorizontalGroup(
            ActionPanlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ActionPanlLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(ActionPanlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(ActionPanlLayout.createSequentialGroup()
                        .addComponent(preProcessButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(reProcess))
                    .addComponent(importImageButton)
                    .addGroup(ActionPanlLayout.createSequentialGroup()
                        .addComponent(connectButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(hostName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(segmentButton)
                    .addComponent(saveExperiment))
                .addContainerGap(459, Short.MAX_VALUE))
        );
        ActionPanlLayout.setVerticalGroup(
            ActionPanlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ActionPanlLayout.createSequentialGroup()
                .addGap(11, 11, 11)
                .addGroup(ActionPanlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(connectButton)
                    .addComponent(hostName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(importImageButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(ActionPanlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(preProcessButton)
                    .addComponent(reProcess))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(segmentButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(saveExperiment)
                .addContainerGap(259, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("Actions", ActionPanl);

        javax.swing.GroupLayout ConfigurationPanelLayout = new javax.swing.GroupLayout(ConfigurationPanel);
        ConfigurationPanel.setLayout(ConfigurationPanelLayout);
        ConfigurationPanelLayout.setHorizontalGroup(
            ConfigurationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(configurationJSP, javax.swing.GroupLayout.DEFAULT_SIZE, 641, Short.MAX_VALUE)
        );
        ConfigurationPanelLayout.setVerticalGroup(
            ConfigurationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(configurationJSP, javax.swing.GroupLayout.DEFAULT_SIZE, 449, Short.MAX_VALUE)
        );

        jTabbedPane1.addTab("Configuration", ConfigurationPanel);

        ControlPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createTitledBorder("Controls")));

        trackStructureJCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                trackStructureJCBActionPerformed(evt);
            }
        });

        selectAllTracksButton.setText("Select All Tracks");
        selectAllTracksButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectAllTracksButtonActionPerformed(evt);
            }
        });

        collapseAllObjectButton.setText("Collapse All Objects");
        collapseAllObjectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                collapseAllObjectButtonActionPerformed(evt);
            }
        });

        nextTrackErrorButton.setText("Go to next track error");
        nextTrackErrorButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nextTrackErrorButtonActionPerformed(evt);
            }
        });

        selectContainingTrackToggleButton.setText("Select Containing Track");
        selectContainingTrackToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectContainingTrackToggleButtonActionPerformed(evt);
            }
        });

        splitObjectButton.setText("Split Object");
        splitObjectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                splitObjectButtonActionPerformed(evt);
            }
        });

        mergeObjectsButton.setText("Merge Objects");
        mergeObjectsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mergeObjectsButtonActionPerformed(evt);
            }
        });

        previousTrackErrorButton.setText("Go to previous track error");
        previousTrackErrorButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                previousTrackErrorButtonActionPerformed(evt);
            }
        });

        interactiveStructure.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                interactiveStructureActionPerformed(evt);
            }
        });

        selectAllObjects.setText("Select All Objects");
        selectAllObjects.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectAllObjectsActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout ControlPanelLayout = new javax.swing.GroupLayout(ControlPanel);
        ControlPanel.setLayout(ControlPanelLayout);
        ControlPanelLayout.setHorizontalGroup(
            ControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(trackStructureJCB, 0, 162, Short.MAX_VALUE)
            .addComponent(collapseAllObjectButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(selectAllTracksButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(nextTrackErrorButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(selectContainingTrackToggleButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(splitObjectButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(mergeObjectsButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(previousTrackErrorButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(interactiveStructure, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(selectAllObjects, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        ControlPanelLayout.setVerticalGroup(
            ControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ControlPanelLayout.createSequentialGroup()
                .addComponent(trackStructureJCB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(interactiveStructure, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(selectAllObjects)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(selectAllTracksButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(nextTrackErrorButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(previousTrackErrorButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(splitObjectButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(mergeObjectsButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(selectContainingTrackToggleButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(collapseAllObjectButton)
                .addContainerGap())
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

        trackSubPanel.setLayout(new javax.swing.BoxLayout(trackSubPanel, javax.swing.BoxLayout.LINE_AXIS));
        TimeJSP.setViewportView(trackSubPanel);

        javax.swing.GroupLayout TimePanelLayout = new javax.swing.GroupLayout(TimePanel);
        TimePanel.setLayout(TimePanelLayout);
        TimePanelLayout.setHorizontalGroup(
            TimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(TimeJSP, javax.swing.GroupLayout.DEFAULT_SIZE, 325, Short.MAX_VALUE)
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
        if (!checkConnection()) return;
        logger.debug("trackStructureJCBActionPerformed: selected index: {} action event: {}", trackStructureJCB.getSelectedIndex(), evt);
        this.setStructure(this.trackStructureJCB.getSelectedIndex());
    }//GEN-LAST:event_trackStructureJCBActionPerformed

    private void segmentButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_segmentButtonActionPerformed
        if (!checkConnection()) return;
        Processor.processAndTrackStructures(db.getExperiment(), db.getDao());
        reloadTree=true;
    }//GEN-LAST:event_segmentButtonActionPerformed

    private void importImageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importImageButtonActionPerformed
        if (!checkConnection()) return;
        File[] selectedFiles = Utils.chooseFile("Choose images/directories to import", null, FileChooser.FileChooserOption.FILES_AND_DIRECTORIES, this);
        if (selectedFiles!=null) Processor.importFiles(this.db.getExperiment(), Utils.convertFilesToString(selectedFiles));
        db.updateExperiment(); //stores imported fields
    }//GEN-LAST:event_importImageButtonActionPerformed

    private void connectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_connectButtonActionPerformed
        String host = this.hostName.getText();
        if (host==null || host.length()==0) host = "localhost";
        //setDBConnection("testFluo595-630", host);
        //setDBConnection("testFluo", host);
        setDBConnection("fluo1510_sub60", host);
    }//GEN-LAST:event_connectButtonActionPerformed

    private void preProcessButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_preProcessButtonActionPerformed
        if (!checkConnection()) return;
        Processor.preProcessImages(this.db.getExperiment(), db.getDao(), true);
        reloadTree=true;
        db.updateExperiment(); //stores preProcessing configurations
    }//GEN-LAST:event_preProcessButtonActionPerformed

    private void saveExperimentActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveExperimentActionPerformed
        if (!checkConnection()) return;
        db.updateExperiment();
    }//GEN-LAST:event_saveExperimentActionPerformed

    private void reProcessActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reProcessActionPerformed
        if (!checkConnection()) return;
        Processor.preProcessImages(this.db.getExperiment(), db.getDao(), false);
        reloadTree=true;
    }//GEN-LAST:event_reProcessActionPerformed

    private void collapseAllObjectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_collapseAllObjectButtonActionPerformed
        this.objectTreeGenerator.collapseAll();
    }//GEN-LAST:event_collapseAllObjectButtonActionPerformed

    private void selectAllTracksButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectAllTracksButtonActionPerformed
        TrackTreeGenerator t = trackTreeController.getLastTreeGenerator();
        if (t==null) {
            logger.warn("No displayed tree");
            return;
        } else {
            Utils.expandTree(t.getTree());
            t.getTree().setSelectionInterval(0, t.getTree().getRowCount());
            t.displaySelectedTracks();
        }
        
    }//GEN-LAST:event_selectAllTracksButtonActionPerformed

    private void nextTrackErrorButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nextTrackErrorButtonActionPerformed
        if (!checkConnection()) return;
        ImageWindowManagerFactory.getImageManager().goToNextTrackError(null, this.trackTreeController.getLastTreeGenerator().getSelectedTrackHeads());
    }//GEN-LAST:event_nextTrackErrorButtonActionPerformed

    private void selectContainingTrackToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectContainingTrackToggleButtonActionPerformed
        logger.info("not implemented yet!");
    }//GEN-LAST:event_selectContainingTrackToggleButtonActionPerformed

    private void splitObjectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_splitObjectButtonActionPerformed
        if (!checkConnection()) return;
        StructureObject sel = objectTreeGenerator.getFisrtSelectedObject();
        if (sel==null) logger.warn("Select an object to Split first!");
        else {
            ObjectSplitter splitter = this.db.getExperiment().getStructure(sel.getStructureIdx()).getObjectSplitter();
            if (splitter==null) logger.warn("No ObjectSplitter defined for structure: "+db.getExperiment().getStructure(sel.getStructureIdx()).getName());
            else {
                StructureObject newObject = sel.split(splitter);
                if (newObject==null) logger.warn("Object could not be splitted!");
                else {
                    ArrayList<StructureObject> siblings = sel.getParent().getChildren(sel.getStructureIdx());
                    int idx = siblings.indexOf(sel);
                    siblings.add(idx+1, newObject);
                    ArrayList<StructureObject> modified = new ArrayList<StructureObject>(siblings.size());
                    modified.add(newObject);
                    modified.add(sel);
                    sel.getParent().relabelChildren(sel.getStructureIdx(), modified);
                    db.getDao().store(modified, false, false);
                    //Update tree
                    ObjectNode node = objectTreeGenerator.getObjectNode(sel);
                    node.getParent().createChildren();
                    objectTreeGenerator.reload(node.getParent());
                    //Update all opened images & objectImageInteraction
                    ImageWindowManagerFactory.getImageManager().reloadObjects(sel.getParent(), sel.getStructureIdx(), false);
                }
            }
        }
    }//GEN-LAST:event_splitObjectButtonActionPerformed

    private void mergeObjectsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mergeObjectsButtonActionPerformed
        if (!checkConnection()) return;
        ArrayList<StructureObject> sel = objectTreeGenerator.getSelectedObjectsFromSameParent();
        if (sel.isEmpty()) logger.warn("Merge Objects: select several objects from same parent first!");
        else {
            StructureObject res = sel.remove(0);
            ArrayList<StructureObject> siblings = res.getParent().getChildren(res.getStructureIdx());
            for (StructureObject toMerge : sel) {
                res.merge(toMerge);
                siblings.remove(toMerge);
            }
            db.getDao().delete(sel);
            ArrayList<StructureObject> modified = new ArrayList<StructureObject>(siblings.size());
            modified.add(res);
            res.getParent().relabelChildren(res.getStructureIdx(), modified);
            db.getDao().store(modified, false, false);
            //Update object tree
            ObjectNode node = objectTreeGenerator.getObjectNode(res);
            node.getParent().createChildren();
            objectTreeGenerator.reload(node.getParent());
            //Update all opened images & objectImageInteraction
            ImageWindowManagerFactory.getImageManager().reloadObjects(res.getParent(), res.getStructureIdx(), false);
        }
    }//GEN-LAST:event_mergeObjectsButtonActionPerformed

    private void previousTrackErrorButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_previousTrackErrorButtonActionPerformed
        if (!checkConnection()) return;
        getImageManager().goToPreviousTrackError(null, this.trackTreeController.getLastTreeGenerator().getSelectedTrackHeads());
    }//GEN-LAST:event_previousTrackErrorButtonActionPerformed

    private void interactiveStructureActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_interactiveStructureActionPerformed
        if (!checkConnection()) return;
        getImageManager().setInteractiveStructure(interactiveStructure.getSelectedIndex());
    }//GEN-LAST:event_interactiveStructureActionPerformed

    private void selectAllObjectsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectAllObjectsActionPerformed
        if (!checkConnection()) return;
        getImageManager().displayAllObjectsOnCurrentImage();
        
    }//GEN-LAST:event_selectAllObjectsActionPerformed

    public static DBConfiguration getDBConnection() {
        if (getInstance()==null) return null;
        return getInstance().db;
    }
    
    public static void setInteractiveStructureIdx(int structureIdx) {
        if (getInstance()==null) return;
        logger.debug("set interactive structure: {}", structureIdx);
        getInstance().interactiveStructure.setSelectedIndex(structureIdx);
        getInstance().interactiveStructureActionPerformed(null);
    }
    
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
    private javax.swing.JPanel ActionPanl;
    private javax.swing.JPanel ConfigurationPanel;
    private javax.swing.JPanel ControlPanel;
    private javax.swing.JPanel DataPanel;
    private javax.swing.JSplitPane ObjectTreeJSP;
    private javax.swing.JPanel StructurePanel;
    private javax.swing.JScrollPane TimeJSP;
    private javax.swing.JPanel TimePanel;
    private javax.swing.JButton collapseAllObjectButton;
    private javax.swing.JScrollPane configurationJSP;
    private javax.swing.JButton connectButton;
    private javax.swing.JTextField hostName;
    private javax.swing.JButton importImageButton;
    private javax.swing.JComboBox interactiveStructure;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JButton mergeObjectsButton;
    private javax.swing.JButton nextTrackErrorButton;
    private javax.swing.JButton preProcessButton;
    private javax.swing.JButton previousTrackErrorButton;
    private javax.swing.JButton reProcess;
    private javax.swing.JButton saveExperiment;
    private javax.swing.JButton segmentButton;
    private javax.swing.JButton selectAllObjects;
    private javax.swing.JButton selectAllTracksButton;
    private javax.swing.JToggleButton selectContainingTrackToggleButton;
    private javax.swing.JButton splitObjectButton;
    private javax.swing.JScrollPane structureJSP;
    private javax.swing.JComboBox trackStructureJCB;
    private javax.swing.JPanel trackSubPanel;
    // End of variables declaration//GEN-END:variables

    
}
