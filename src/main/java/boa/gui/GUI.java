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
import com.mongodb.MongoClient;
import com.mongodb.client.MongoIterable;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import measurement.MeasurementKeyObject;
import measurement.extraction.DataExtractor;
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
    DefaultListModel actionStructureModel;
    DefaultListModel actionMicroscopyFieldModel;
    /**
     * Creates new form GUI
     */
    public GUI() {
        this.instance=this;
        initComponents();
        addHorizontalScrollBar(dbNames);
        refreshDBNames();
        addHorizontalScrollBar(trackStructureJCB);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        PluginFactory.findPlugins("plugins.plugins");
        jTabbedPane1.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if (reloadTree && jTabbedPane1.getSelectedComponent()==DataPanel) {
                    reloadTree=false;
                    loadObjectTrees();
                }
                if (jTabbedPane1.getSelectedComponent()==actionPanel) {
                    populateActionStructureList();
                    populateActionMicroscopyFieldList();
                }
            }
        });
        
    }
    
    public void setDBConnection(String dbName, String hostname) {
        long t0 = System.currentTimeMillis();
        Morphium m=MorphiumUtils.createMorphium(hostname, 27017, dbName);
        long t1 = System.currentTimeMillis();
        if (m==null) {
            unsetXP();
            return;
        }
        logger.info("Connection established with db: {} @ host: {}, in {} ms", dbName, hostName, t1-t0);
        setDBConnection(m);
        if (db!=null) {
            this.setTitle("Experiment: "+db.getMorphium().getDatabase().getName());
        } else this.setTitle("No Experiment set");
    }
    
    public void setDBConnection(Morphium m) {
        if (m==null) {
            unsetXP();
            return;
        }
        this.db = new DBConfiguration(m);
        db.getExperiment();
        if (db.getExperiment()==null) {
            logger.warn("no experiment found in DB, using dummy experiment");
            Experiment xp = new Experiment("xp test UI");
            db.getXpDAO().store(xp);
            db.getXpDAO().clearCache();
        } else logger.info("Experiment found: {} ", db.getExperiment().getName());
        configurationTreeGenerator = new ConfigurationTreeGenerator(db.getXpDAO());
        configurationJSP.setViewportView(configurationTreeGenerator.getTree());
        populateActionStructureList();
        populateActionMicroscopyFieldList();
        reloadTree=true;
    }
    
    private void unsetXP() {
        db=null;
        reloadTree=true;
        populateActionStructureList();
        populateActionMicroscopyFieldList();
    }
    
    protected void loadObjectTrees() {
        //TODO remember treepath if existing and set them in the new trees
        
        objectTreeGenerator = new StructureObjectTreeGenerator(db);
        structureJSP.setViewportView(objectTreeGenerator.getTree());
        
        trackTreeController = new TrackTreeController(db, objectTreeGenerator);
        setTrackTreeStructures(db.getExperiment().getStructuresAsString());
    }
    
    public void populateActionStructureList() {
        if (actionStructureModel==null) {
            actionStructureModel = new DefaultListModel();
            this.actionStructureList.setModel(actionStructureModel);
        } else actionStructureModel.removeAllElements();
        if (db!=null) for (Structure s : db.getExperiment().getStructures().getChildren()) actionStructureModel.addElement(s.getName());
    }
    public int[] getSelectedStructures(boolean returnAllIfNoneIsSelected) {
        int[] res = actionStructureList.getSelectedIndices();
        if (res.length==0 && returnAllIfNoneIsSelected) {
            res=new int[db.getExperiment().getStructureCount()];
            for (int i = 0; i<res.length; ++i) res[i]=i;
        }
        return res;
    }
    
    public void populateActionMicroscopyFieldList() {
        if (actionMicroscopyFieldModel==null) {
            actionMicroscopyFieldModel = new DefaultListModel();
            this.actionMicroscopyFieldList.setModel(actionMicroscopyFieldModel);
        } else actionMicroscopyFieldModel.removeAllElements();
        if (db!=null) for (int i =0; i<db.getExperiment().getMicrocopyFieldCount(); ++i) actionMicroscopyFieldModel.addElement(db.getExperiment().getMicroscopyField(i).getName());
    }
    public int[] getSelectedMicroscopyFields() {
        int[] res = actionMicroscopyFieldList.getSelectedIndices();
        if (res.length==0) {
            res=new int[db.getExperiment().getMicrocopyFieldCount()];
            for (int i = 0; i<res.length; ++i) res[i]=i;
        }
        return res;
    }
    
    public static GUI getInstance() {
        if (instance==null) new GUI();
        return instance;
    }
    
    // ImageObjectListener implementation
    public void fireObjectSelected(List<StructureObject> selectedObjects, boolean addToSelection, boolean track) {
        if (track) {
            // selection de la track
            
        }
        // selection de l'objet dans l'arbre d'objets
        if (!addToSelection ) objectTreeGenerator.selectObject(null, false);
        for (StructureObject selectedObject : selectedObjects) objectTreeGenerator.selectObject(selectedObject, true);
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
        actionPanel = new javax.swing.JPanel();
        importImageButton = new javax.swing.JButton();
        connectButton = new javax.swing.JButton();
        hostName = new javax.swing.JTextField();
        saveExperiment = new javax.swing.JButton();
        actionStructureJSP = new javax.swing.JScrollPane();
        actionStructureList = new javax.swing.JList();
        actionMicroscopyFieldJSP = new javax.swing.JScrollPane();
        actionMicroscopyFieldList = new javax.swing.JList();
        extractMeasurements = new javax.swing.JButton();
        duplicateExperiment = new javax.swing.JButton();
        dbNames = new javax.swing.JComboBox();
        refreshDBNames = new javax.swing.JButton();
        actionJSP = new javax.swing.JScrollPane();
        runActionList = new javax.swing.JList();
        runActions = new javax.swing.JButton();
        newXP = new javax.swing.JButton();
        DeleteXP = new javax.swing.JButton();
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
        deleteObjectsButton = new javax.swing.JButton();
        ObjectTreeJSP = new javax.swing.JSplitPane();
        StructurePanel = new javax.swing.JPanel();
        structureJSP = new javax.swing.JScrollPane();
        TimePanel = new javax.swing.JPanel();
        TimeJSP = new javax.swing.JScrollPane();
        trackSubPanel = new javax.swing.JPanel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        importImageButton.setText("Import Images");
        importImageButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importImageButtonActionPerformed(evt);
            }
        });

        connectButton.setText("Set Experiment");
        connectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                connectButtonActionPerformed(evt);
            }
        });

        hostName.setText("localhost");

        saveExperiment.setText("Save Configuration Changes");
        saveExperiment.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveExperimentActionPerformed(evt);
            }
        });

        actionStructureJSP.setBorder(javax.swing.BorderFactory.createTitledBorder("Structures"));

        actionStructureJSP.setViewportView(actionStructureList);

        actionMicroscopyFieldJSP.setBorder(javax.swing.BorderFactory.createTitledBorder("Microscopy Fields"));

        actionMicroscopyFieldJSP.setViewportView(actionMicroscopyFieldList);

        extractMeasurements.setText("Extract Measurements");
        extractMeasurements.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                extractMeasurementsActionPerformed(evt);
            }
        });

        duplicateExperiment.setText("Duplicate Experiment");
        duplicateExperiment.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                duplicateExperimentActionPerformed(evt);
            }
        });

        dbNames.setMaximumSize(new java.awt.Dimension(140, 27));
        dbNames.setMinimumSize(new java.awt.Dimension(140, 27));
        dbNames.setPreferredSize(new java.awt.Dimension(140, 27));
        dbNames.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dbNamesActionPerformed(evt);
            }
        });

        refreshDBNames.setText("Refresh List");
        refreshDBNames.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshDBNamesActionPerformed(evt);
            }
        });

        actionJSP.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createTitledBorder("Actions to Run")));

        runActionList.setModel(new javax.swing.AbstractListModel() {
            String[] strings = { "Pre-Processing", "Re-Run Pre-Processing", "Segment", "Track", "Measurements" };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        runActionList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        actionJSP.setViewportView(runActionList);

        runActions.setText("Run Selected Actions");
        runActions.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runActionsActionPerformed(evt);
            }
        });

        newXP.setText("New Experiment");
        newXP.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newXPActionPerformed(evt);
            }
        });

        DeleteXP.setText("Delete Experiment");
        DeleteXP.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                DeleteXPActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout actionPanelLayout = new javax.swing.GroupLayout(actionPanel);
        actionPanel.setLayout(actionPanelLayout);
        actionPanelLayout.setHorizontalGroup(
            actionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(actionPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(actionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(actionPanelLayout.createSequentialGroup()
                        .addGroup(actionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(actionPanelLayout.createSequentialGroup()
                                .addComponent(hostName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(refreshDBNames))
                            .addGroup(actionPanelLayout.createSequentialGroup()
                                .addComponent(dbNames, javax.swing.GroupLayout.PREFERRED_SIZE, 144, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(connectButton))
                            .addComponent(duplicateExperiment)
                            .addComponent(saveExperiment)
                            .addGroup(actionPanelLayout.createSequentialGroup()
                                .addComponent(newXP)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(DeleteXP)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(actionStructureJSP, javax.swing.GroupLayout.PREFERRED_SIZE, 155, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(actionMicroscopyFieldJSP, javax.swing.GroupLayout.DEFAULT_SIZE, 191, Short.MAX_VALUE))
                    .addGroup(actionPanelLayout.createSequentialGroup()
                        .addGroup(actionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(runActions)
                            .addGroup(actionPanelLayout.createSequentialGroup()
                                .addComponent(actionJSP, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(actionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(importImageButton)
                                    .addComponent(extractMeasurements))))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        actionPanelLayout.setVerticalGroup(
            actionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(actionPanelLayout.createSequentialGroup()
                .addGroup(actionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(actionPanelLayout.createSequentialGroup()
                        .addGap(11, 11, 11)
                        .addGroup(actionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(hostName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(refreshDBNames))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(actionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(dbNames, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(connectButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(actionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(newXP)
                            .addComponent(DeleteXP))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(duplicateExperiment)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(saveExperiment))
                    .addGroup(actionPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(actionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(actionMicroscopyFieldJSP, javax.swing.GroupLayout.PREFERRED_SIZE, 178, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(actionStructureJSP, javax.swing.GroupLayout.PREFERRED_SIZE, 178, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(actionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(actionJSP, javax.swing.GroupLayout.PREFERRED_SIZE, 163, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(actionPanelLayout.createSequentialGroup()
                        .addComponent(importImageButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(extractMeasurements)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(runActions)
                .addContainerGap(56, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("Actions", actionPanel);

        javax.swing.GroupLayout ConfigurationPanelLayout = new javax.swing.GroupLayout(ConfigurationPanel);
        ConfigurationPanel.setLayout(ConfigurationPanelLayout);
        ConfigurationPanelLayout.setHorizontalGroup(
            ConfigurationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(configurationJSP, javax.swing.GroupLayout.DEFAULT_SIZE, 641, Short.MAX_VALUE)
        );
        ConfigurationPanelLayout.setVerticalGroup(
            ConfigurationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(configurationJSP, javax.swing.GroupLayout.DEFAULT_SIZE, 458, Short.MAX_VALUE)
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

        collapseAllObjectButton.setText("Collapse Object-tree");
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

        deleteObjectsButton.setText("Delete Objects");
        deleteObjectsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteObjectsButtonActionPerformed(evt);
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
            .addComponent(deleteObjectsButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
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
                .addGap(2, 2, 2)
                .addComponent(deleteObjectsButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(selectContainingTrackToggleButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(collapseAllObjectButton))
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
            .addComponent(structureJSP, javax.swing.GroupLayout.DEFAULT_SIZE, 410, Short.MAX_VALUE)
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
            .addComponent(TimeJSP, javax.swing.GroupLayout.DEFAULT_SIZE, 410, Short.MAX_VALUE)
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

    
    private void runAction(String fieldName, boolean preProcess, boolean reProcess, boolean segmentAndTrack, boolean trackOnly, boolean measurements, boolean deleteObjects) {
        if (preProcess || reProcess) {
            logger.info("Pre-Processing: Field: {}", fieldName);
            Processor.preProcessImages(db.getExperiment().getMicroscopyField(fieldName), db.getDao(), true, preProcess);
        }
        if (segmentAndTrack || trackOnly) {
            int[] selectedStructures = this.getSelectedStructures(true);
            List<StructureObject> roots = Processor.getOrCreateRootTrack(db.getDao(), fieldName);
            if (roots==null) {
                logger.error("Field: {} no pre-processed image found", fieldName);
                return;
            }
            logger.debug("roots: {}", roots.size());
            for (int s : selectedStructures) {
                if (!trackOnly) logger.info("Segmentation & Tracking: Field: {}, Structure: {}", fieldName, s);
                else logger.info("Tracking: Field: {}, Structure: {}", fieldName, s);
                Processor.executeProcessingScheme(roots, s, trackOnly, deleteObjects);
            }
        }
        if (measurements) {
            logger.info("Measurements: Field: {}", fieldName);
            Processor.performMeasurements(fieldName, db.getDao());
        }
    }
    
    private void preProcess(boolean computeConfigData) {
        for (int i : this.getSelectedMicroscopyFields()) {
            
        }
    }
    
    private void trackStructureJCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_trackStructureJCBActionPerformed
        if (!checkConnection()) return;
        logger.debug("trackStructureJCBActionPerformed: selected index: {} action event: {}", trackStructureJCB.getSelectedIndex(), evt);
        this.setStructure(this.trackStructureJCB.getSelectedIndex());
    }//GEN-LAST:event_trackStructureJCBActionPerformed

    private void importImageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importImageButtonActionPerformed
        if (!checkConnection()) return;
        File[] selectedFiles = Utils.chooseFiles("Choose images/directories to import", null, FileChooser.FileChooserOption.FILES_AND_DIRECTORIES, this);
        if (selectedFiles!=null) Processor.importFiles(this.db.getExperiment(), Utils.convertFilesToString(selectedFiles));
        db.updateExperiment(); //stores imported fields
    }//GEN-LAST:event_importImageButtonActionPerformed

    private void connectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_connectButtonActionPerformed
        String host = getHostName();
        //setDBConnection("testFluo595-630", host);
        //setDBConnection("testFluo", host);
        //setDBConnection("testFluo60", host);
        //setDBConnection("fluo151127", host);
        //setDBConnection("fluo151130", host);
        String dbName = Utils.getSelectedString(dbNames);
        if (dbName!=null) setDBConnection(dbName, host);
        updateConnectButton();
        //setDBConnection("chromaticShift", host);
    }//GEN-LAST:event_connectButtonActionPerformed

    private String getHostName() {
        String host = this.hostName.getText();
        if (host==null || host.length()==0) host = "localhost";
        return host;
    }
    
    private void saveExperimentActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveExperimentActionPerformed
        if (!checkConnection()) return;
        db.updateExperiment();
    }//GEN-LAST:event_saveExperimentActionPerformed

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
            db.getDao().delete(sel, false);
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

    private void extractMeasurementsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_extractMeasurementsActionPerformed
        if (!checkConnection()) return;
        int[] selectedStructures = this.getSelectedStructures(true);
        File outputDir = Utils.chooseFile("Choose directory", null, FileChooser.FileChooserOption.DIRECTORIES_ONLY, this);
        if (outputDir!=null) {
            String file = outputDir.getAbsolutePath()+File.separator+"output"+Utils.toStringArray(selectedStructures, "_", "", "_")+".xls";
            logger.info("measurements will be extracted to: {}", file);
            Map<Integer, String[]> keys = db.getExperiment().getAllMeasurementNamesByStructureIdx(MeasurementKeyObject.class, selectedStructures);
            DataExtractor.extractMeasurementObjects(db, file, keys);
        }
    }//GEN-LAST:event_extractMeasurementsActionPerformed

    private void deleteObjectsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteObjectsButtonActionPerformed
        if (!checkConnection()) return;
        ArrayList<StructureObject> sel = objectTreeGenerator.getSelectedObjects(true);
        if (sel.isEmpty()) logger.warn("Delete Objects: select one or several objects to delete first!");
        else {
            int structureIdx = sel.get(0).getStructureIdx();
            ArrayList<StructureObject> parents = new ArrayList<StructureObject>();
            for (StructureObject o : sel) {
                parents.add(o.getParent());
                o.getParent().getChildren(o.getStructureIdx()).remove(o);
            }
            Utils.removeDuplicates(parents, false);
            logger.info("Deleting {} objects, from {} parents", sel.size(), parents.size());
            db.getDao().delete(sel, true);
            ArrayList<StructureObject> modified = new ArrayList<StructureObject>();
            for (StructureObject p : parents) p.relabelChildren(structureIdx, modified);
            db.getDao().store(modified, false, false);
            //Update object tree
            for (StructureObject s : parents) {
                ObjectNode node = objectTreeGenerator.getObjectNode(s);
                node.getParent().createChildren();
                objectTreeGenerator.reload(node.getParent());
            }
            //Update all opened images & objectImageInteraction
            for (StructureObject p : parents) ImageWindowManagerFactory.getImageManager().reloadObjects(p, structureIdx, false);
        }
    }//GEN-LAST:event_deleteObjectsButtonActionPerformed

    private void duplicateExperimentActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_duplicateExperimentActionPerformed
        String name = JOptionPane.showInputDialog("New DB name:");
        if (!Utils.isValid(name, false)) logger.error("Name should not contain special characters");
        else if (getDBNames().contains(name)) logger.error("DB name already exists");
        else {
            DBConfiguration db2 = new DBConfiguration(name);
            Experiment xp2 = db.getExperiment().duplicate();
            xp2.setName(name);
            db2.getXpDAO().store(xp2);
        }
    }//GEN-LAST:event_duplicateExperimentActionPerformed

    private void refreshDBNamesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshDBNamesActionPerformed
        this.refreshDBNames();
    }//GEN-LAST:event_refreshDBNamesActionPerformed

    private void dbNamesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dbNamesActionPerformed
        updateConnectButton();
        
    }//GEN-LAST:event_dbNamesActionPerformed

    private void runActionsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runActionsActionPerformed
        if (!checkConnection()) return;
        boolean preProcess=false;
        boolean reRunPreProcess=false;
        boolean segmentAndTrack = false;
        boolean trackOnly = false;
        boolean runMeasurements=false;
        for (int i : this.runActionList.getSelectedIndices()) {
            if (i==0) preProcess=true;
            if (i==1) reRunPreProcess=!preProcess;
            if (i==2) segmentAndTrack=true;
            if (i==3) trackOnly = !segmentAndTrack;
            if (i==4) runMeasurements=true;
        }
        int[] microscopyFields = this.getSelectedMicroscopyFields();
        int[] selectedStructures = this.getSelectedStructures(true);
        boolean allStructures = selectedStructures.length==db.getExperiment().getStructureCount();
        boolean needToDeleteObjects = preProcess || reRunPreProcess || segmentAndTrack;
        boolean deleteAll =  needToDeleteObjects && allStructures && microscopyFields.length==db.getExperiment().getMicrocopyFieldCount();
        if (deleteAll) db.getDao().deleteAllObjects();  
        boolean deleteAllField = needToDeleteObjects && allStructures && !deleteAll;
        logger.debug("Run actions: preProcess: {}, rePreProcess: {}, segmentAndTrack: {}, trackOnly: {}, runMeasurements: {}, need to delete objects: {}, delete all: {}, delete all by field: {}", preProcess, reRunPreProcess, segmentAndTrack, trackOnly, runMeasurements, needToDeleteObjects, deleteAll, deleteAllField);
        for (int f : microscopyFields) {
            String fieldName = db.getExperiment().getMicroscopyField(f).getName();
            if (deleteAllField) db.getDao().deleteObjectsFromField(fieldName);
            this.runAction(fieldName, preProcess, reRunPreProcess, segmentAndTrack, trackOnly, runMeasurements, needToDeleteObjects && !deleteAllField && !deleteAll);
            if (preProcess) db.updateExperiment(); // save field preProcessing configuration value @ each field
            db.getDao().clearCache();
        }
        if (needToDeleteObjects) this.reloadTree=true;
    }//GEN-LAST:event_runActionsActionPerformed

    private void newXPActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newXPActionPerformed
        String name = JOptionPane.showInputDialog("New XP name:");
        if (!Utils.isValid(name, false)) logger.error("Name should not contain special characters");
        else if (getDBNames().contains(name)) logger.error("XP name already exists");
        else {
            DBConfiguration db2 = new DBConfiguration(name);
            Experiment xp2 = new Experiment(name);
            xp2.setName(name);
            db2.getXpDAO().store(xp2);
            this.setDBConnection(name, getHostName());
            refreshDBNames();
            if (this.db!=null) dbNames.setSelectedItem(name);
        }
    }//GEN-LAST:event_newXPActionPerformed

    private void DeleteXPActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_DeleteXPActionPerformed
        String name = Utils.getSelectedString(dbNames);
        if (name==null || name.length()==0) return;
        int response = JOptionPane.showConfirmDialog(this, "Delete Experiment (all data will be lost)", "Confirm",
        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (response == JOptionPane.NO_OPTION || response == JOptionPane.CLOSED_OPTION) {
        } else if (response == JOptionPane.YES_OPTION) {
            MongoClient mongoClient = new MongoClient(getHostName(), 27017);
            mongoClient.dropDatabase(name);
            refreshDBNames();
        }
    }//GEN-LAST:event_DeleteXPActionPerformed
    
    private void updateConnectButton() {
        String s = Utils.getSelectedString(dbNames);
        logger.debug("DBName action: selected : {}, current: {} ", s, db!=null? db.getMorphium().getDatabase().getName() : null);
        if (s==null || s.length()==0) this.connectButton.setEnabled(false);
        else {
            if (this.db!=null && db.getMorphium().getDatabase().getName().equals(s)) this.connectButton.setEnabled(false);
            else this.connectButton.setEnabled(true);
        }
    }
    
    private void refreshDBNames() {
        List<String> names = getDBNames();
        String old = (String)dbNames.getSelectedItem();
        this.dbNames.removeAllItems();
        this.dbNames.addItem("");
        for (String s : names) dbNames.addItem(s);
        if (names.contains(old)) dbNames.setSelectedItem(old);
    }
    
    public List<String> getDBNames() {
        return getDBNames(getHostName(), 27017);
    }
    
    public static List<String> getDBNames(String hostName, int portNumber) {
        long t0 = System.currentTimeMillis();
        MongoClient mongoClient = new MongoClient(hostName, portNumber);
        MongoIterable<String> dbs = mongoClient.listDatabaseNames();
        List<String> res = new ArrayList<String>();
        for (String s : dbs) res.add(s);
        Collections.sort(res);
        long t1 = System.currentTimeMillis();
        logger.info("{} db names retrieved in: {}ms", res.size(), t1-t0);
        return res;
    }
    
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
    private javax.swing.JPanel ConfigurationPanel;
    private javax.swing.JPanel ControlPanel;
    private javax.swing.JPanel DataPanel;
    private javax.swing.JButton DeleteXP;
    private javax.swing.JSplitPane ObjectTreeJSP;
    private javax.swing.JPanel StructurePanel;
    private javax.swing.JScrollPane TimeJSP;
    private javax.swing.JPanel TimePanel;
    private javax.swing.JScrollPane actionJSP;
    private javax.swing.JScrollPane actionMicroscopyFieldJSP;
    private javax.swing.JList actionMicroscopyFieldList;
    private javax.swing.JPanel actionPanel;
    private javax.swing.JScrollPane actionStructureJSP;
    private javax.swing.JList actionStructureList;
    private javax.swing.JButton collapseAllObjectButton;
    private javax.swing.JScrollPane configurationJSP;
    private javax.swing.JButton connectButton;
    private javax.swing.JComboBox dbNames;
    private javax.swing.JButton deleteObjectsButton;
    private javax.swing.JButton duplicateExperiment;
    private javax.swing.JButton extractMeasurements;
    private javax.swing.JTextField hostName;
    private javax.swing.JButton importImageButton;
    private javax.swing.JComboBox interactiveStructure;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JButton mergeObjectsButton;
    private javax.swing.JButton newXP;
    private javax.swing.JButton nextTrackErrorButton;
    private javax.swing.JButton previousTrackErrorButton;
    private javax.swing.JButton refreshDBNames;
    private javax.swing.JList runActionList;
    private javax.swing.JButton runActions;
    private javax.swing.JButton saveExperiment;
    private javax.swing.JButton selectAllObjects;
    private javax.swing.JButton selectAllTracksButton;
    private javax.swing.JToggleButton selectContainingTrackToggleButton;
    private javax.swing.JButton splitObjectButton;
    private javax.swing.JScrollPane structureJSP;
    private javax.swing.JComboBox trackStructureJCB;
    private javax.swing.JPanel trackSubPanel;
    // End of variables declaration//GEN-END:variables

    
}
