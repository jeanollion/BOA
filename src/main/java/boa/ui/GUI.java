/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.ui;

import boa.gui.configuration.ConfigurationTreeGenerator;
import boa.gui.image_interaction.IJImageDisplayer;
import boa.gui.image_interaction.IJImageWindowManager;
import boa.gui.image_interaction.IJVirtualStack;
import boa.gui.image_interaction.InteractiveImage;
import boa.gui.image_interaction.InteractiveImageKey;
import boa.gui.image_interaction.ImageObjectListener;
import boa.gui.image_interaction.ImageWindowManager;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import static boa.gui.image_interaction.ImageWindowManagerFactory.getImageManager;
import boa.configuration.experiment.Experiment;
import boa.gui.objects.TrackNode;
import boa.gui.objects.TrackTreeController;
import boa.gui.objects.TrackTreeGenerator;
import boa.data_structure.SelectionUtils;
import static boa.data_structure.SelectionUtils.setMouseAdapter;
import boa.gui.selection.SelectionRenderer;
import static boa.data_structure.SelectionUtils.fixIOI;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.FileChooser;
import boa.configuration.parameters.NumberParameter;
import boa.core.DefaultWorker;
import boa.core.Processor;
import boa.core.ProgressCallback;
import boa.core.PythonGateway;
import boa.core.Task;
import boa.configuration.experiment.Position;
import boa.configuration.experiment.PreProcessingChain;
import boa.configuration.experiment.Structure;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.Parameter;
import boa.core.Core;
import boa.data_structure.dao.ImageDAO;
import boa.data_structure.dao.BasicMasterDAO;
import boa.data_structure.dao.DBMapMasterDAO;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.MasterDAOFactory;
import boa.data_structure.dao.ObjectDAO;
import boa.data_structure.Selection;
import boa.data_structure.dao.SelectionDAO;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.ui.Shortcuts.ACTION;
import boa.ui.Shortcuts.PRESET;
import boa.gui.configuration.TransparentListCellRenderer;
import boa.gui.image_interaction.Kymograph;
import boa.gui.objects.StructureSelectorTree;
import ij.ImageJ;
import boa.image.Image;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.JTextComponent;
import javax.swing.text.TextAction;
import boa.measurement.MeasurementKeyObject;
import boa.measurement.MeasurementExtractor;
import boa.measurement.SelectionExtractor;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import boa.plugins.ManualSegmenter;
import boa.plugins.ObjectSplitter;
import boa.plugins.PluginFactory;
import boa.ui.logger.ExperimentSearchUtils;
import boa.ui.logger.FileProgressLogger;
import boa.ui.logger.MultiProgressLogger;
import boa.ui.PropertyUtils;
import static boa.plugins.PluginFactory.checkClass;
import static boa.plugins.ToolTip.formatToolTip;
import boa.utils.ArrayUtil;
import boa.utils.CommandExecuter;
import boa.utils.FileIO;
import boa.utils.FileIO.ZipReader;
import boa.utils.FileIO.ZipWriter;
import boa.utils.ImportExportJSON;
import boa.utils.JSONUtils;
import boa.utils.ListTransferHandler;
import boa.utils.Pair;
import boa.utils.ThreadRunner;
import boa.utils.Utils;
import static boa.utils.Utils.addHorizontalScrollBar;
import ij.IJ;
import java.awt.dnd.DropTarget;
import java.util.function.Consumer;
import javax.swing.ToolTipManager;
import javax.swing.tree.TreeSelectionModel;
import boa.ui.logger.ProgressLogger;
import javax.swing.JLabel;
import javax.swing.JSeparator;


/**
 *
 * @author jollion
 */
public class GUI extends javax.swing.JFrame implements ImageObjectListener, ProgressLogger {
    public static final Logger logger = LoggerFactory.getLogger(GUI.class);
    // check if mapDB is present
    public static final String DBprefix = "boa_";
    public String currentDBPrefix = "";
    private static GUI INSTANCE;
    
    // db-related attributes
    private MasterDAO db;
    
    // xp tree-related attributes
    ConfigurationTreeGenerator configurationTreeGenerator;
    final Consumer<Boolean> setConfigurationTabValid;
    // track-related attributes
    TrackTreeController trackTreeController;
    private HashMap<Integer, JTree> currentTrees;
    
    //Object Tree related attributes
    boolean reloadObjectTrees=false;
    String logFile;
    // structure-related attributes
    //StructureObjectTreeGenerator objectTreeGenerator;
    DefaultListModel<String> experimentModel = new DefaultListModel();
    DefaultListModel<String> actionPoolListModel = new DefaultListModel();
    DefaultListModel<String> actionMicroscopyFieldModel;
    DefaultListModel<Selection> selectionModel;
    StructureSelectorTree trackTreeStructureSelector, actionStructureSelector;
    PythonGateway pyGtw;
    // shortcuts
    private Shortcuts shortcuts;
    
    //JProgressBar progressBar;
    //private static int progressBarTabIndex = 3;
    // enable/disable components
    private ProgressIcon progressBar;
    private NumberParameter openedImageLimit = new BoundedNumberParameter("Limit", 0, 5, 0, null);
    private NumberParameter kymographInterval = new NumberParameter("Kymograph Interval", 0, 0).setToolTipText("Interval between images, in pixels");
    private NumberParameter localZoomFactor = new BoundedNumberParameter("Local Zoom Factor", 1, 4, 2, null);
    private NumberParameter localZoomArea = new BoundedNumberParameter("Local Zoom Area", 0, 35, 15, null);
    private NumberParameter threadNumber = new BoundedNumberParameter("Max Thread Number", 0, System.getProperty("os.name").toLowerCase().indexOf("win")>=0 ? 1 : ThreadRunner.getMaxCPUs(), 1, ThreadRunner.getMaxCPUs());
    final private List<Component> relatedToXPSet;
    final private List<Component> relatedToReadOnly;
    public static int getThreadNumber() {
        if (!hasInstance()) return System.getProperty("os.name").toLowerCase().indexOf("win")>=0 ? 1 : ThreadRunner.getMaxCPUs();
        return getInstance().threadNumber.getValue().intValue();
    }
    /**
     * Creates new form GUI
     */
    public GUI() {
        //logger.info("DBMaker: {}", checkClass("org.mapdb.DBMaker"));
        logger.info("Creating GUI instance...");
        this.INSTANCE=this;
        initComponents();
        //updateMongoDBBinActions();
        tabs.setTabComponentAt(1, new JLabel("Configuration")); // so that it can be colorized in red when configuration is not valid
        setConfigurationTabValid = v -> { // action when experiment is not valid
            tabs.getTabComponentAt(1).setForeground(v ? Color.black : Color.red);
            tabs.getTabComponentAt(1).repaint();
        };
        progressBar = new ProgressIcon(Color.darkGray, tabs);
        Component progressComponent =  new ColorPanel(progressBar);
        tabs.addTab("Progress: ", progressBar, progressComponent);
        tabs.addChangeListener(new ChangeListener() {
            int lastSelTab=0;
            @Override public void stateChanged(ChangeEvent e) {
                if (lastSelTab==1 && tabs.getSelectedIndex()!=lastSelTab) setConfigurationTabValid.accept(db==null? true : db.getExperiment().isValid());
                if (tabs.getSelectedComponent()==progressComponent) {
                    logger.debug("pb");
                    tabs.setSelectedIndex(lastSelTab);
                } else lastSelTab=tabs.getSelectedIndex();
                if (tabs.getSelectedComponent()==dataPanel) {
                    if (reloadObjectTrees) {
                        reloadObjectTrees=false;
                        loadObjectTrees();
                        displayTrackTrees();
                    }
                    setTrackTreeStructures();
                    setInteractiveStructures();
                    
                }
                if (tabs.getSelectedComponent()==actionPanel) {
                    populateActionStructureList();
                    populateActionPositionList();
                }
            }
        });
        // selections
        selectionModel = new DefaultListModel<>();
        this.selectionList.setModel(selectionModel);
        this.selectionList.setCellRenderer(new SelectionRenderer());
        setMouseAdapter(selectionList);
        // CLOSE -> clear cache properly
        addWindowListener(new WindowAdapter() {
            @Override 
            public void windowClosing(WindowEvent evt) {
                if (db!=null) {
                    db.unlockPositions();
                    db.unlockConfiguration();
                    db.clearCache();
                }
                logger.debug("Closed successfully");
            }
        });
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        toFront();
        
        // tool tips
        ToolTipManager.sharedInstance().setInitialDelay(100);
        ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);
        trackPanel.setToolTipText(formatToolTip("Element displayed are segmented tracks for each object class. Right click for actions on the track like display kymograph, run segmentation/tracking etc.."));
        trackTreeStructureJSP.setToolTipText(formatToolTip("Object class to be displayed in the <em>Segmentation & Tracking</em> panel"));
        interactiveObjectPanel.setToolTipText(formatToolTip("Object class that will be displayed and edited on interactive images. <br />ctrl+click to select/deselect object classes"));
        editPanel.setToolTipText(formatToolTip("Commands to edit segmentation/lineage of selected objects of the interactive objects on the currently active kymograph<br />See <em>Shortcuts > Object/Lineage Edition</em> menu for while list of commands and description"));
        actionStructureJSP.setToolTipText(formatToolTip("Object classes of the opened experiment. Tasks will be run only on selected objects classes, or on all object classes if none is selected"));
        experimentJSP.setToolTipText(formatToolTip("List of all experiments contained in the current experiment folder<br />If an experiment is opened, its name written in the title of this window. The opened experiment does not necessarily correspond to the selected experiment in this list<br /><br />ctrl+click to select/deselect experiments"));
        actionPositionJSP.setToolTipText(formatToolTip("Positions of the opened experiment. <br />Tasks will be run only on selected position, or on all position if no position is selected<br />ctrl+click to select/deselect positions"));
        deleteObjectsButton.setToolTipText(formatToolTip("Right-click for more delete commands"));
        experimentFolder.setToolTipText(formatToolTip("Directory containing several experiments<br />Righ-click menu to access recent list and file browser"));
        this.actionJSP.setToolTipText(formatToolTip("<b>Tasks to run on selected positions/object classes:</b> (ctrl+click to select/deselect tasks)<br/><ol>"
                + "<li><b>"+runActionList.getModel().getElementAt(0)+"</b>: Performs preprocessing pipeline on selected positions (or all if none is selected)</li>"
                + "<li><b>"+runActionList.getModel().getElementAt(1)+"</b>: Performs segmentation and tracking on selected object classes (all if none is selected) and selected positions (or all if none is selected)</li>"
                + "<li><b>"+runActionList.getModel().getElementAt(2)+"</b>: Performs Tracking on selected object classes (all if none is selected) and selected positions (or all if none is selected). Ignored if "+runActionList.getModel().getElementAt(1)+" is selected.</li>"
                + "<li><b>"+runActionList.getModel().getElementAt(3)+"</b>: Pre-computes kymographs and saves them in the experiment folder in order to have a faster display of kymograph, and to eventually allow to erase pre-processed images to save disk-space</li>"
                + "<li><b>"+runActionList.getModel().getElementAt(4)+"</b>: Computes measurements on selected positions (or all if none is selected)</li>"
                + "<li><b>"+runActionList.getModel().getElementAt(5)+"</b>: Extract measurements of selected object tpye (or all is none is selected) on selected positions (or all if none is selected), and saves them in one single .csv <em>;</em>-separated file per object class in the experiment folder</li>"
                + "<li><b>"+runActionList.getModel().getElementAt(6)+"</b>: Export data from this experiment (segmentation and tracking results, configuration...) of all selected posisions (or all if none is selected) in a single zip archive that can be imported. Exported data can be configured in the menu <em>Import/Export > Export Options</em></li></ol>"));
        shortcutMenu.setToolTipText(formatToolTip("List of all commands and associated shortcuts. <br />Change here preset to AZERTY/QWERT keyboard layout"));
        localZoomMenu.setToolTipText(formatToolTip("Local zoom is activated/desactivated with TAB"));
        this.importConfigurationMenuItem.setToolTipText(formatToolTip("Will overwrite configuration from a selected file to current experiment/selected experiments. <br />Selected configuration file must have same number of object classes<br />Overwrites configuration for each Object class<br />Overwrite preprocessing template"));
        // disable componenets when run action
        actionPoolList.setModel(actionPoolListModel);
        experimentList.setModel(experimentModel);
        relatedToXPSet = new ArrayList<Component>() {{add(saveXPMenuItem);add(exportSelectedFieldsMenuItem);add(exportXPConfigMenuItem);add(importPositionsToCurrentExperimentMenuItem);add(importConfigurationForSelectedStructuresMenuItem);add(importConfigurationForSelectedPositionsMenuItem);add(importImagesMenuItem);add(runSelectedActionsMenuItem);add(extractMeasurementMenuItem);}};
        relatedToReadOnly = new ArrayList<Component>() {{add(manualSegmentButton);add(splitObjectsButton);add(mergeObjectsButton);add(deleteObjectsButton);add(pruneTrackButton);add(linkObjectsButton);add(unlinkObjectsButton);add(resetLinksButton);add(importImagesMenuItem);add(runSelectedActionsMenuItem);add(importSubMenu);add(importPositionsToCurrentExperimentMenuItem);add(importConfigurationForSelectedPositionsMenuItem);add(importConfigurationForSelectedStructuresMenuItem);}};
        
        // persistent properties
        setLogFile(PropertyUtils.get(PropertyUtils.LOG_FILE));
        deleteMeasurementsCheckBox.setSelected(PropertyUtils.get(PropertyUtils.DELETE_MEASUREMENTS, true));
        ButtonGroup dbGroup = new ButtonGroup();
        dbGroup.add(localFileSystemDatabaseRadioButton);
        String dbType = PropertyUtils.get(PropertyUtils.DATABASE_TYPE, MasterDAOFactory.DAOType.DBMap.toString());
        if (dbType.equals(MasterDAOFactory.DAOType.Morphium.toString())) {
            currentDBPrefix=GUI.DBprefix;
            MasterDAOFactory.setCurrentType(MasterDAOFactory.DAOType.Morphium);
            localDBMenu.setEnabled(false);
        } else if (dbType.equals(MasterDAOFactory.DAOType.DBMap.toString())) {
            currentDBPrefix="";
            localFileSystemDatabaseRadioButton.setSelected(true);
            String path = PropertyUtils.get(PropertyUtils.LOCAL_DATA_PATH);
            if (path!=null) experimentFolder.setText(path);
            MasterDAOFactory.setCurrentType(MasterDAOFactory.DAOType.DBMap);
            localDBMenu.setEnabled(true);
        }
        // import / export options
        PropertyUtils.setPersistant(importConfigMenuItem, "import_config", true);
        PropertyUtils.setPersistant(importSelectionsMenuItem, "import_selections", true);
        PropertyUtils.setPersistant(importObjectsMenuItem, "import_objects", true);
        PropertyUtils.setPersistant(importPPImagesMenuItem, "import_ppimages", true);
        PropertyUtils.setPersistant(importTrackImagesMenuItem, "import_trackImages", true);
        PropertyUtils.setPersistant(exportConfigMenuItem, "export_config", true);
        PropertyUtils.setPersistant(exportSelectionsMenuItem, "export_selections", true);
        PropertyUtils.setPersistant(exportObjectsMenuItem, "export_objects", true);
        PropertyUtils.setPersistant(exportPPImagesMenuItem, "export_ppimages", true);
        PropertyUtils.setPersistant(exportTrackImagesMenuItem, "export_trackImages", true);
        // image display limit
        PropertyUtils.setPersistant(openedImageLimit, "limit_disp_images");
        ImageWindowManagerFactory.getImageManager().setDisplayImageLimit(openedImageLimit.getValue().intValue());
        openedImageLimit.addListener(p->ImageWindowManagerFactory.getImageManager().setDisplayImageLimit(openedImageLimit.getValue().intValue()));
        ConfigurationTreeGenerator.addToMenu(openedImageLimit.getName(), openedImageLimit.getUI().getDisplayComponent(), openedImageNumberLimitMenu);
        // kymograph interval
        PropertyUtils.setPersistant(kymographInterval, "kymograph_interval");
        Kymograph.INTERVAL_PIX = kymographInterval.getValue().intValue();
        kymographInterval.addListener(p->Kymograph.INTERVAL_PIX = kymographInterval.getValue().intValue());
        ConfigurationTreeGenerator.addToMenu(kymographInterval.getName(), kymographInterval.getUI().getDisplayComponent(), kymographMenu);
        // local zoom
        PropertyUtils.setPersistant(localZoomFactor, "local_zoom_factor");
        PropertyUtils.setPersistant(localZoomArea, "local_zoom_area");
        ConfigurationTreeGenerator.addToMenu(localZoomFactor.getName(), localZoomFactor.getUI().getDisplayComponent(), localZoomMenu);
        ConfigurationTreeGenerator.addToMenu(localZoomArea.getName(), localZoomArea.getUI().getDisplayComponent(), localZoomMenu);
        
        // load xp after persistent props loaded
        populateExperimentList();
        updateDisplayRelatedToXPSet();
        
        
        pyGtw = new PythonGateway();
        pyGtw.startGateway();
        
        // KEY shortcuts
        Map<ACTION, Action> actionMap = new HashMap<>();
        actionMap.put(ACTION.LINK, new AbstractAction("Link") {
            @Override
            public void actionPerformed(ActionEvent e) {
                linkObjectsButtonActionPerformed(e);
                logger.debug("L pressed: " + e);
            }
        });
        actionMap.put(ACTION.UNLINK, new AbstractAction("Unlink") {
            @Override
            public void actionPerformed(ActionEvent e) {
                unlinkObjectsButtonActionPerformed(e);
                logger.debug("U pressed: " + e);
            }
        });
        actionMap.put(ACTION.RESET_LINKS, new AbstractAction("Reset Links") {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetLinksButtonActionPerformed(e);
                logger.debug("R pressed: " + e);
            }
        });
        actionMap.put(ACTION.RESET_LINKS, new AbstractAction("Reset Links") {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetLinksButtonActionPerformed(e);
                logger.debug("R pressed: " + e);
            }
        });
        actionMap.put(ACTION.DELETE, new AbstractAction("Delete") {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteObjectsButtonActionPerformed(e);
                logger.debug("D pressed: " + e);
            }
        });
        actionMap.put(ACTION.DELETE_AFTER_FRAME, new AbstractAction("Delete") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (Utils.promptBoolean("Delete All Objects after selected Frame ? ", null)) ManualEdition.deleteAllObjectsFromFrame(db, true);
                logger.debug("D pressed: " + e);
            }
        });
        actionMap.put(ACTION.PRUNE, new AbstractAction("Create Branch") {
            @Override
            public void actionPerformed(ActionEvent e) {
                pruneTrackButtonActionPerformed(e);
            }
        });
        actionMap.put(ACTION.CREATE_TRACK, new AbstractAction("Create Track") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!checkConnection()) return;
                List<StructureObject> selList = ImageWindowManagerFactory.getImageManager().getSelectedLabileObjects(null);
                if (selList.isEmpty()) logger.warn("Select at least one object to Create track from first!");
                else if (selList.size()<=10 || Utils.promptBoolean("Create "+selList.size()+ " new tracks ? ", null)) ManualEdition.createTracks(db, selList, true);
            }
        });
        actionMap.put(ACTION.MERGE, new AbstractAction("Merge") {
            @Override
            public void actionPerformed(ActionEvent e) {
                mergeObjectsButtonActionPerformed(e);
                logger.debug("M pressed: " + e);
            }
        });
        actionMap.put(ACTION.SPLIT, new AbstractAction("Split") {
            @Override
            public void actionPerformed(ActionEvent e) {
                splitObjectsButtonActionPerformed(e);
                logger.debug("S pressed: " + e);
            }
        });
        actionMap.put(ACTION.CREATE, new AbstractAction("Create") {
            @Override
            public void actionPerformed(ActionEvent e) {
                manualSegmentButtonActionPerformed(e);
                logger.debug("C pressed: " + e);
            }
        });
        actionMap.put(ACTION.TOGGLE_CREATION_TOOL, new AbstractAction("Toggle creation tool") {
            @Override
            public void actionPerformed(ActionEvent e) {
                ImageWindowManagerFactory.getImageManager().toggleSetObjectCreationTool();
                logger.debug("C pressed: " + e);
            }
        });
        
        actionMap.put(ACTION.SELECT_ALL_OBJECTS, new AbstractAction("Select All Objects") {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectAllObjectsButtonActionPerformed(e);
                logger.debug("A pressed: " + e);
            }
        });
        actionMap.put(ACTION.SELECT_ALL_TRACKS, new AbstractAction("Select All Tracks") {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectAllTracksButtonActionPerformed(e);
                logger.debug("Q pressed: " + e);
            }
        });
        
        actionMap.put(ACTION.CHANGE_INTERACTIVE_STRUCTURE, new AbstractAction("Change Interactive structure") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (interactiveStructure.getItemCount()>1) {
                    int s = interactiveStructure.getSelectedIndex()-1;
                    s = (s+1) % (interactiveStructure.getItemCount()-1);
                    setInteractiveStructureIdx(s);
                }
                logger.debug("Current interactive structure: {}", interactiveStructure.getSelectedIndex()-1);
            }
        });
        
        actionMap.put(ACTION.TOGGLE_SELECT_MODE, new AbstractAction("Track mode") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (ImageWindowManager.displayTrackMode) ImageWindowManager.displayTrackMode = false;
                else ImageWindowManager.displayTrackMode = true;
                logger.debug("TrackMode is {}", ImageWindowManager.displayTrackMode? "ON":"OFF");
            }
        });
        actionMap.put(ACTION.TOGGLE_LOCAL_ZOOM, new AbstractAction("Local Zoom") {
            @Override
            public void actionPerformed(ActionEvent e) {
                ImageWindowManagerFactory.getImageManager().toggleActivateLocalZoom();
                logger.debug("Zoom pressed: " + e);
            }
        });
        actionMap.put(ACTION.NAV_PREV, new AbstractAction("Prev") {
            @Override
            public void actionPerformed(ActionEvent e) {
                previousTrackErrorButtonActionPerformed(e);
            }
        });
        
        actionMap.put(ACTION.NAV_NEXT, new AbstractAction("Next") {
            @Override
            public void actionPerformed(ActionEvent e) {
                nextTrackErrorButtonActionPerformed(e);
            }
        });
        actionMap.put(ACTION.OPEN_NEXT, new AbstractAction("Open Next Image") {
            @Override
            public void actionPerformed(ActionEvent e) {
                navigateToNextImage(true);
            }
        });
        actionMap.put(ACTION.OPEN_PREV, new AbstractAction("Open Previous Image") {
            @Override
            public void actionPerformed(ActionEvent e) {
                navigateToNextImage(false);
            }
        });
        
        actionMap.put(ACTION.ADD_TO_SEL0, new AbstractAction("Add to selection 0") {
            @Override
            public void actionPerformed(ActionEvent e) {
                logger.debug("Z pressed (shift)");
                addToSelectionActionPerformed(0);
            }
        });
        actionMap.put(ACTION.REM_FROM_SEL0, new AbstractAction("Remove from selection 0") {
            @Override
            public void actionPerformed(ActionEvent e) {
                logger.debug("Z pressed (alt)");
                removeFromSelectionActionPerformed(0);
            }
        });
        actionMap.put(ACTION.REM_ALL_FROM_SEL0, new AbstractAction("Remove All from selection 0") {
            @Override
            public void actionPerformed(ActionEvent e) {
                logger.debug("Z pressed (alt gr)");
                removeAllFromSelectionActionPerformed(0);
            }
        });
        actionMap.put(ACTION.TOGGLE_DISPLAY_SEL0, new AbstractAction("Toggle display selection 0") {
            @Override
            public void actionPerformed(ActionEvent e) {
                logger.debug("Z pressed (ctrl)");
                toggleDisplaySelection(0);
            }
        });
        actionMap.put(ACTION.ADD_TO_SEL1, new AbstractAction("Add to selection 1") {
            @Override
            public void actionPerformed(ActionEvent e) {
                logger.debug("E pressed (shift)");
                addToSelectionActionPerformed(1);
            }
        });
        actionMap.put(ACTION.REM_FROM_SEL1, new AbstractAction("Remove from selection 1") {
            @Override
            public void actionPerformed(ActionEvent e) {
                logger.debug("E pressed (alt)");
                removeFromSelectionActionPerformed(1);
            }
        });
        actionMap.put(ACTION.REM_ALL_FROM_SEL1, new AbstractAction("Remove All from selection 1") {
            @Override
            public void actionPerformed(ActionEvent e) {
                logger.debug("E pressed (alt gr)");
                removeAllFromSelectionActionPerformed(1);
            }
        });
        actionMap.put(ACTION.TOGGLE_DISPLAY_SEL1, new AbstractAction("Toggle display selection 1") {
            @Override
            public void actionPerformed(ActionEvent e) {
                logger.debug("E pressed (ctrl)");
                toggleDisplaySelection(1);
            }
        });
        
        ChoiceParameter shortcutPreset = new ChoiceParameter("Shortcut preset", Utils.toStringArray(PRESET.values()), PRESET.AZERTY.toString(), false);
        PropertyUtils.setPersistant(shortcutPreset, "shortcut_preset");
        
        this.shortcuts = new Shortcuts(actionMap, PRESET.valueOf(shortcutPreset.getValue()));
        
        Consumer<Parameter> setShortcut = p->{
            int selPreset = ((ChoiceParameter)p).getSelectedIndex();
            shortcuts.setPreset(PRESET.valueOf(((ChoiceParameter)p).getValue()));
            shortcuts.addToMenu(this.shortcutMenu);
            shortcutPresetMenu.removeAll();
            ConfigurationTreeGenerator.addToMenu(shortcutPreset.getName(), shortcutPreset.getUI().getDisplayComponent(), this.shortcutPresetMenu);
            shortcutMenu.add(shortcutPresetMenu);
            setDataBrowsingButtonsTitles();
        };
        shortcutPreset.addListener(setShortcut);
        setShortcut.accept(shortcutPreset);
    }
    private void setDataBrowsingButtonsTitles() {
        this.selectAllObjectsButton.setText("Select All Objects ("+shortcuts.getShortcutFor(ACTION.SELECT_ALL_OBJECTS)+")");
        this.selectAllTracksButton.setText("Select All Tracks ("+shortcuts.getShortcutFor(ACTION.SELECT_ALL_TRACKS)+")");
        this.nextTrackErrorButton.setText("Navigate Next ("+shortcuts.getShortcutFor(ACTION.NAV_NEXT)+")");
        this.previousTrackErrorButton.setText("Navigate Previous ("+shortcuts.getShortcutFor(ACTION.NAV_PREV)+")");
        this.manualSegmentButton.setText("Segment ("+shortcuts.getShortcutFor(ACTION.CREATE)+")");
        this.splitObjectsButton.setText("Split ("+shortcuts.getShortcutFor(ACTION.SPLIT)+")");
        this.mergeObjectsButton.setText("Merge ("+shortcuts.getShortcutFor(ACTION.MERGE)+")");
        this.deleteObjectsButton.setText("Delete ("+shortcuts.getShortcutFor(ACTION.DELETE)+")");
        this.pruneTrackButton.setText("Prune Track(s) ("+shortcuts.getShortcutFor(ACTION.PRUNE)+")");
        this.linkObjectsButton.setText("Link ("+shortcuts.getShortcutFor(ACTION.LINK)+")");
        this.unlinkObjectsButton.setText("UnLink ("+shortcuts.getShortcutFor(ACTION.UNLINK)+")");
        this.resetLinksButton.setText("Reset Links ("+shortcuts.getShortcutFor(ACTION.RESET_LINKS)+")");
    }
    boolean running = false;
    @Override
    public void setRunning(boolean running) {
        this.running=running;
        logger.debug("set running: "+running);
        progressBar.setValue(progressBar.getMinimum());
        this.experimentMenu.setEnabled(!running);
        this.runMenu.setEnabled(!running);
        this.optionMenu.setEnabled(!running);
        this.importExportMenu.setEnabled(!running);
        this.miscMenu.setEnabled(!running);
        
        // action tab
        this.experimentFolder.setEditable(!running);
        this.experimentList.setEnabled(!running);
        if (actionStructureSelector!=null) this.actionStructureSelector.getTree().setEnabled(!running);
        this.runActionList.setEnabled(!running);
        this.microscopyFieldList.setEnabled(!running);
        
        //config tab
        this.configurationPanel.setEnabled(!running);
        if (configurationTreeGenerator!=null && configurationTreeGenerator.getTree()!=null) this.configurationTreeGenerator.getTree().setEnabled(!running);
        // browsing tab
        if (trackTreeController!=null) this.trackTreeController.setEnabled(!running);
        if (trackTreeStructureSelector!=null) this.trackTreeStructureSelector.getTree().setEnabled(!running);
        tabs.setEnabledAt(2, !running);
        //updateDisplayRelatedToXPSet();
    }
    // gui interface method
    @Override
    public void setProgress(int i) {
        //this.progressBar.setM
        this.progressBar.setValue(i);
        //logger.info("Progress: {}/{}", i, 100);
    }
    
    @Override
    public void setMessage(String message) {
        try {
            //logger.info(message);
            this.console.getStyledDocument().insertString(console.getStyledDocument().getLength(), Utils.getFormattedTime()+": "+message+"\n", null);
        } catch (BadLocationException ex) {            
        }
    }
    public static void log(String message) {
        if (hasInstance()) getInstance().setMessage(message);
    }
    public static void setProgression(int percentage) {
        if (hasInstance()) getInstance().setProgress(percentage);
    }
    public int getLocalZoomArea() {
        return this.localZoomArea.getValue().intValue();
    }
    public double getLocalZoomLevel() {
        return this.localZoomFactor.getValue().doubleValue();
    }
    public void outputDirectoryUpdated() {
        this.reloadObjectTrees=true;
        if (this.db==null) return;
        else if (db instanceof DBMapMasterDAO)  {
            DBMapMasterDAO d = (DBMapMasterDAO)db;
            d.clearCache(false, true, true);
        }
    }
    //public StructureObjectTreeGenerator getObjectTree() {return this.objectTreeGenerator;}
    public TrackTreeController getTrackTrees() {return this.trackTreeController;}
    
    public static void updateRoiDisplay(InteractiveImage i) {
        if (INSTANCE==null) return;
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        if (iwm==null) return;
        Image image=null;
        if (i==null) {
            Object im = iwm.getDisplayer().getCurrentImage();
            if (im!=null) {
                image = iwm.getDisplayer().getImage(im);
                if (image==null) return;
                else i = iwm.getImageObjectInterface(image);
            }
        }
        if (image==null) {
            return; // todo -> actions on all images?
        }
        iwm.hideAllRois(image, true, false); // will update selections
        if (i==null) return;
        
        // look in track list
        TrackTreeGenerator gen = INSTANCE.trackTreeController.getLastTreeGenerator();
        if (gen!=null) {
            List<List<StructureObject>> tracks = gen.getSelectedTracks(true);
            iwm.displayTracks(image, i, tracks, true);
            /*int idx = 0;
            for (List<StructureObject> track : tracks) {
                iwm.displayTrack(image, i, i.pairWithOffset(track), ImageWindowManager.getColor(idx++), true);
            }*/
        }
        // look in object list
        //List<StructureObject> selectedObjects = instance.objectTreeGenerator.getSelectedObjects(true, i.getChildStructureIdx());
        //iwm.displayObjects(image, i.pairWithOffset(selectedObjects), null, true, false);
        // unselect objects that cannot be selected ?
        
        // labile objects
        //iwm.displayLabileObjects(image);
        //iwm.displayLabileTracks(image);
    }
    
    public static void updateRoiDisplayForSelections(Image image, InteractiveImage i) {
        if (INSTANCE==null) return;
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        if (iwm==null) return;
        if (i==null) {
            if (image==null) {
                Object im = iwm.getDisplayer().getCurrentImage();
                if (im!=null) image = iwm.getDisplayer().getImage(im);
                if (image==null) return;
            }
            i = iwm.getImageObjectInterface(image);
        }
        if (image==null) {
            return; // todo -> actions on all images?
        }
        ImageWindowManagerFactory.getImageManager().hideAllRois(image, false, true);
        //logger.debug("updateSelectionsDisplay");
        Enumeration<Selection> sels = INSTANCE.selectionModel.elements();
        while (sels.hasMoreElements()) {
            Selection s = sels.nextElement();
            //logger.debug("selection: {}", s);
            if (s.isDisplayingTracks()) SelectionUtils.displayTracks(s, i);
            if (s.isDisplayingObjects()) SelectionUtils.displayObjects(s, i);
        }
    }

    public void openExperiment(String dbName, String hostnameOrDir, boolean readOnly) {
        if (db!=null) closeExperiment();
        //long t0 = System.currentTimeMillis();
        if (hostnameOrDir==null) hostnameOrDir = getHostNameOrDir(dbName);
        db = MasterDAOFactory.createDAO(dbName, hostnameOrDir);
        db.setConfigurationReadOnly(readOnly);
        if (!readOnly) db.lockPositions(); // locks all positions
        if (db==null || db.getExperiment()==null) {
            logger.warn("no experiment found in DB: {}", db);
            closeExperiment();
            return;
        } else {
            logger.info("Experiment found in db: {} ", db.getDBName());
            if (db.isConfigurationReadOnly()) {
                GUI.log(dbName+ ": Config file could not be locked. Experiment already opened ? Experiment will be opened in Read Only mode: all modifications (configuration, segmentation/lineage ...) won't be saved. ");
                GUI.log("To open in read and write mode, close all other instances and re-open the experiment. ");
            } else {
               setMessage("Experiment: "+db.getDBName()+" opened"); 
            }
            
        }
        updateConfigurationTree();
        populateActionStructureList();
        populateActionPositionList();
        reloadObjectTrees=true;
        populateSelections();
        updateDisplayRelatedToXPSet();
        experimentListValueChanged(null);
        setInteractiveStructures();
    }
    
    private void updateConfigurationTree() {
        if (db==null) {
            configurationTreeGenerator=null;
            configurationJSP.setViewportView(null);
            setConfigurationTabValid.accept(true);
        } else {
            configurationTreeGenerator = new ConfigurationTreeGenerator(db.getExperiment(),setConfigurationTabValid);
            configurationJSP.setViewportView(configurationTreeGenerator.getTree());
            setConfigurationTabValid.accept(db.getExperiment().isValid());
        }
    }
    
    private void promptSaveUnsavedChanges() {
        if (db==null) return;
        if (configurationTreeGenerator!=null && configurationTreeGenerator.getTree()!=null  
                && configurationTreeGenerator.getTree().getModel()!=null 
                && configurationTreeGenerator.getTree().getModel().getRoot() != null
                && ((Experiment)configurationTreeGenerator.getTree().getModel().getRoot())!=db.getExperiment()) {
            GUI.log("WARNING: current modification cannot be saved");
            //return;
        }
        if (db.experimentChangedFromFile()) {
            if (db.isConfigurationReadOnly()) {
                this.setMessage("Configuration have changed but canno't be saved in read-only mode");
            } else {
                boolean save = Utils.promptBoolean("Current configuration has unsaved changes. Save ? ", this);
                if (save) db.updateExperiment();
            }
        }
    }
    
    private void closeExperiment() {
        promptSaveUnsavedChanges();
        String xp = db!=null ? db.getDBName() : null;
        if (db!=null) {
            db.unlockPositions();
            db.unlockConfiguration();
            db.clearCache();
        }
        db=null;
        if (configurationTreeGenerator!=null) configurationTreeGenerator.flush();
        configurationTreeGenerator=null;
        trackTreeController=null;
        reloadObjectTrees=true;
        populateActionStructureList();
        populateActionPositionList();
        updateDisplayRelatedToXPSet();
        updateConfigurationTree();
        tabs.setSelectedIndex(0);
        ImageWindowManagerFactory.getImageManager().flush();
        if (xp!=null) setMessage("XP: "+xp+ " closed");
        experimentListValueChanged(null);
    }
    
    private void updateDisplayRelatedToXPSet() {
        final boolean enable = db!=null;
        String xp = db==null ? "" : " - Experiment: "+db.getDBName();
        String v = Utils.getVersion(this);
        if (v!=null && v.length()>0) v = "- Version: "+v;
        setTitle("**BACMAN**"+v+xp);
        for (Component c: relatedToXPSet) c.setEnabled(enable);
        runActionAllXPMenuItem.setEnabled(!enable); // only available if no xp is set
        this.tabs.setEnabledAt(1, enable); // configuration
        this.tabs.setEnabledAt(2, enable); // data browsing
    
        // readOnly
        if (enable) {
            boolean rw = !db.isConfigurationReadOnly();
            for (Component c : relatedToReadOnly) c.setEnabled(rw);
        }
        importConfigurationMenuItem.setText(enable ? "Configuration to current Experiment" : (getSelectedExperiments().isEmpty()? "--" : "Configuration to selected Experiment(s)") );
    }
    
    
    public void populateSelections() {
        List<Selection> selectedValues = selectionList.getSelectedValuesList();
        
        Map<String, Selection> state = selectionModel.isEmpty() ? Collections.EMPTY_MAP : Utils.asList(selectionModel).stream().collect(Collectors.toMap(s->s.getName(), s->s));
        this.selectionModel.removeAllElements();
        if (!checkConnection()) return;
        SelectionDAO dao = this.db.getSelectionDAO();
        if (dao==null) {
            logger.error("No selection DAO. Output Directory set ? ");
            return;
        }
        List<Selection> sels = dao.getSelections();
        for (Selection sel : sels) {
            selectionModel.addElement(sel);
            sel.setState(state.get(sel.getName()));
            //logger.debug("Selection : {}, displayingObjects: {} track: {}", sel.getName(), sel.isDisplayingObjects(), sel.isDisplayingTracks());
        }
        Utils.setSelectedValues(selectedValues, selectionList, selectionModel);
        resetSelectionHighlight();
    }
    
    public void resetSelectionHighlight() {
        if (trackTreeController==null) return;
        trackTreeController.resetHighlight();
    }
    
    public List<Selection> getSelectedSelections(boolean returnAllIfNoneIsSelected) {
        List<Selection> res = selectionList.getSelectedValuesList();
        if (returnAllIfNoneIsSelected && res.isEmpty()) return db.getSelectionDAO().getSelections();
        else return res;
    }
    
    public void setSelectedSelection(Selection sel) {
        this.selectionList.setSelectedValue(sel, false);
    }
    
    public List<Selection> getSelections() {
        return Utils.asList(selectionModel);
    }
    public void addSelection(Selection s) {
        int i = 0;
        while (i<selectionModel.getSize()) {
            if (selectionModel.getElementAt(i).getName()==s.getName()) selectionModel.remove(i);
            else ++i;
        }
        this.selectionModel.addElement(s);
    }
    
    protected void loadObjectTrees() {
        if (db==null) {
            trackTreeController = null;
            return;
        }
        trackTreeController = new TrackTreeController(db);
        setInteractiveStructures();
        setTrackTreeStructures();
        resetSelectionHighlight();
    }
    
    private void populateExperimentList() {
        List<String> names = getDBNames();
        if (names==null) names = Collections.EMPTY_LIST;
        List sel = experimentList.getSelectedValuesList();
        if (sel.isEmpty()) {
            String old = PropertyUtils.get(PropertyUtils.LAST_SELECTED_EXPERIMENT);
            if (old!=null) {
                sel=new ArrayList<String>(1);
                sel.add(old);
            }
        }
        this.experimentModel.removeAllElements();
        for (String s : names) experimentModel.addElement(s);
        Utils.setSelectedValues(sel, experimentList, experimentModel);
    }
    
    public void populateActionStructureList() {
        if (db==null) {
            actionStructureJSP.setViewportView(null);
            return;
        }
        int[] sel = actionStructureSelector!=null ? actionStructureSelector.getSelectedStructures().toArray() : new int[0];
        actionStructureSelector = new StructureSelectorTree(db.getExperiment(), i->{}, TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        actionStructureSelector.selectStructures(sel);
        actionStructureJSP.setViewportView(actionStructureSelector.getTree());
    }
    public int[] getSelectedStructures(boolean returnAllIfNoneIsSelected) {
        int[] res = actionStructureSelector!=null ? actionStructureSelector.getSelectedStructures().toArray() : new int[0];
        if (res.length==0 && returnAllIfNoneIsSelected) {
            res=new int[db.getExperiment().getStructureCount()];
            for (int i = 0; i<res.length; ++i) res[i]=i;
        }
        return res;
    }
    
    public void populateActionPositionList() {
        List sel = microscopyFieldList.getSelectedValuesList();
        if (actionMicroscopyFieldModel==null) {
            actionMicroscopyFieldModel = new DefaultListModel();
            this.microscopyFieldList.setModel(actionMicroscopyFieldModel);
        } else actionMicroscopyFieldModel.removeAllElements();
        if (db!=null) {
            for (int i =0; i<db.getExperiment().getPositionCount(); ++i) actionMicroscopyFieldModel.addElement(db.getExperiment().getPosition(i).getName()+" (#"+i+")");
            Utils.setSelectedValues(sel, microscopyFieldList, actionMicroscopyFieldModel);
        }
    }
    public int[] getSelectedPositionIdx() {
        int[] res = microscopyFieldList.getSelectedIndices();
        if (res.length==0) {
            res=new int[db.getExperiment().getPositionCount()];
            for (int i = 0; i<res.length; ++i) res[i]=i;
        }
        return res;
    }
    public List<String> getSelectedPositions(boolean returnAllIfNoneSelected) {
        if (returnAllIfNoneSelected && microscopyFieldList.getSelectedIndex()<0) return new ArrayList<String>(Arrays.asList(db.getExperiment().getPositionsAsString()));
        else return Utils.transform((List<String>)microscopyFieldList.getSelectedValuesList(), s->s.substring(0, s.indexOf(" ")));
    }
    
    public void setSelectedTab(int tabIndex) {
        this.tabs.setSelectedIndex(tabIndex);
        if (tabs.getSelectedComponent()==dataPanel) {
            if (reloadObjectTrees) {
                reloadObjectTrees=false;
                loadObjectTrees();
            }
            setInteractiveStructures();
            setTrackTreeStructures();
        } 
    }
    
    
    public static GUI getInstance() {
        if (INSTANCE==null) INSTANCE=new GUI();
        return INSTANCE;
    }
    
    public static boolean hasInstance() {
        return INSTANCE!=null;
    }
    
    // ImageObjectListener implementation
    @Override public void fireObjectSelected(List<StructureObject> selectedObjects, boolean addToSelection) {
        /*objectTreeGenerator.setUpdateRoiDisplayWhenSelectionChange(false);
        objectTreeGenerator.selectObjects(selectedObjects, addToSelection);    
        objectTreeGenerator.setUpdateRoiDisplayWhenSelectionChange(true);*/
    }
    
    @Override public void fireObjectDeselected(List<StructureObject> deselectedObject) {
        /*objectTreeGenerator.setUpdateRoiDisplayWhenSelectionChange(false);
        objectTreeGenerator.unSelectObjects(deselectedObject);
        objectTreeGenerator.setUpdateRoiDisplayWhenSelectionChange(true);*/
    }
    
    @Override public void fireTracksSelected(List<StructureObject> selectedTrackHeads, boolean addToSelection) {
        trackTreeController.setUpdateRoiDisplayWhenSelectionChange(false);
        trackTreeController.selectTracks(selectedTrackHeads, addToSelection);
        trackTreeController.setUpdateRoiDisplayWhenSelectionChange(true);
    }
    
    @Override public void fireTracksDeselected(List<StructureObject> deselectedTrackHeads) {
        trackTreeController.setUpdateRoiDisplayWhenSelectionChange(false);
        trackTreeController.deselectTracks(deselectedTrackHeads);
        trackTreeController.setUpdateRoiDisplayWhenSelectionChange(true);
    }
    
    public void fireDeselectAllTracks(int structureIdx) {
        trackTreeController.setUpdateRoiDisplayWhenSelectionChange(false);
        trackTreeController.deselectAllTracks(structureIdx);
        trackTreeController.setUpdateRoiDisplayWhenSelectionChange(true);
    }

    public void fireDeselectAllObjects(int structureIdx) {
        /*objectTreeGenerator.setUpdateRoiDisplayWhenSelectionChange(false);
        objectTreeGenerator.unselectAllObjects();
        objectTreeGenerator.setUpdateRoiDisplayWhenSelectionChange(true);*/
    }
    private void setInteractiveStructures() {
        List<String> structureNames= Arrays.asList(db.getExperiment().getStructuresAsString());
        Object selectedO = interactiveStructure.getSelectedItem();
        this.interactiveStructure.removeAllItems();
        interactiveStructure.addItem("Viewfield");
        for (String s: structureNames) interactiveStructure.addItem(s);
        if (structureNames.size()>0) {
            if (selectedO!=null && structureNames.contains(selectedO)) interactiveStructure.setSelectedItem(selectedO);
            else interactiveStructure.setSelectedIndex(1);
        }
    }
    private void setTrackTreeStructures() {
        if (db==null) {
            trackTreeStructureJSP.setViewportView(null);
            trackTreeStructureSelector = null;
            return;
        }
        int[] sel = trackTreeStructureSelector !=null ? trackTreeStructureSelector.getSelectedStructures().toArray() : new int[]{0};
        if (sel.length==0) sel = new int[]{0};
        trackTreeStructureSelector = new StructureSelectorTree(db.getExperiment(), i -> setTrackTreeStructure(i), TreeSelectionModel.SINGLE_TREE_SELECTION);
        setTrackStructureIdx(sel[0]);
        trackTreeStructureJSP.setViewportView(trackTreeStructureSelector.getTree());
    }
    
    private void setTrackTreeStructure(int structureIdx) {
        trackTreeController.setStructure(structureIdx);
        displayTrackTrees();
    }
    
    public void setTrackStructureIdx(int structureIdx) {
        if (this.trackTreeStructureSelector!=null) trackTreeStructureSelector.selectStructures(structureIdx);
        this.setTrackTreeStructure(structureIdx);
    }
    
    public void displayTrackTrees() {
        this.trackSubPanel.removeAll();
        HashMap<Integer, JTree> newCurrentTrees = new HashMap<Integer, JTree>(trackTreeController.getDisplayedGeneratorS().size());
        for (Entry<Integer, TrackTreeGenerator> e : trackTreeController.getDisplayedGeneratorS().entrySet()) {
            final Entry<Integer, TrackTreeGenerator> entry = e;
            final JTree tree = entry.getValue().getTree();
            if (tree!=null) {
                if (currentTrees==null || !currentTrees.containsValue(tree)) {
                    removeTreeSelectionListeners(tree);
                    tree.addTreeSelectionListener(new TreeSelectionListener() {
                        @Override
                        public void valueChanged(TreeSelectionEvent e) {
                            if (logger.isDebugEnabled()) {
                                //logger.debug("selection changed on tree of structure: {} event: {}", entry.getKey(), e);
                            }
                            if (trackTreeController == null) {
                                return;
                            }
                            if (tree.getSelectionCount() == 1 && tree.getSelectionPath().getLastPathComponent() instanceof TrackNode) {
                                trackTreeController.updateParentTracks(trackTreeController.getTreeIdx(entry.getKey()));
                            } else {
                                trackTreeController.clearTreesFromIdx(trackTreeController.getTreeIdx(entry.getKey()) + 1);
                            }
                            INSTANCE.displayTrackTrees();
                            if (trackTreeController.isUpdateRoiDisplayWhenSelectionChange()) {
                                logger.debug("updating display: number of selected tracks: {}", tree.getSelectionCount());
                                GUI.updateRoiDisplay(null);
                            }
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
        logger.trace("display track tree: number of trees: {} subpanel component count: {}",trackTreeController.getDisplayedGeneratorS().size(), trackSubPanel.getComponentCount() );
        trackSubPanel.revalidate();
        trackSubPanel.repaint();
    }
    
    private static void removeTreeSelectionListeners(JTree tree) {
        for (TreeSelectionListener t : tree.getTreeSelectionListeners()) tree.removeTreeSelectionListener(t);
    }
    
    private boolean checkConnection() {
        if (this.db==null) {
            log("Open Experiment first (GUI:"+hashCode());
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

        jSplitPane3 = new javax.swing.JSplitPane();
        tabs = new javax.swing.JTabbedPane();
        actionPanel = new javax.swing.JPanel();
        experimentFolder = new javax.swing.JTextField();
        actionStructureJSP = new javax.swing.JScrollPane();
        actionPositionJSP = new javax.swing.JScrollPane();
        microscopyFieldList = new javax.swing.JList();
        actionJSP = new javax.swing.JScrollPane();
        runActionList = new javax.swing.JList();
        experimentJSP = new javax.swing.JScrollPane();
        experimentList = new javax.swing.JList();
        actionPoolJSP = new javax.swing.JScrollPane();
        actionPoolList = new javax.swing.JList();
        configurationPanel = new javax.swing.JPanel();
        configurationJSP = new javax.swing.JScrollPane();
        dataPanel = new javax.swing.JPanel();
        trackPanel = new javax.swing.JPanel();
        TimeJSP = new javax.swing.JScrollPane();
        trackSubPanel = new javax.swing.JPanel();
        selectionPanel = new javax.swing.JPanel();
        selectionJSP = new javax.swing.JScrollPane();
        selectionList = new javax.swing.JList();
        createSelectionButton = new javax.swing.JButton();
        reloadSelectionsButton = new javax.swing.JButton();
        controlPanelJSP = new javax.swing.JScrollPane();
        editPanel = new javax.swing.JPanel();
        selectAllTracksButton = new javax.swing.JButton();
        nextTrackErrorButton = new javax.swing.JButton();
        splitObjectsButton = new javax.swing.JButton();
        mergeObjectsButton = new javax.swing.JButton();
        previousTrackErrorButton = new javax.swing.JButton();
        selectAllObjectsButton = new javax.swing.JButton();
        deleteObjectsButton = new javax.swing.JButton();
        updateRoiDisplayButton = new javax.swing.JButton();
        manualSegmentButton = new javax.swing.JButton();
        testManualSegmentationButton = new javax.swing.JButton();
        linkObjectsButton = new javax.swing.JButton();
        unlinkObjectsButton = new javax.swing.JButton();
        resetLinksButton = new javax.swing.JButton();
        testSplitButton = new javax.swing.JButton();
        pruneTrackButton = new javax.swing.JButton();
        trackTreeStructurePanel = new javax.swing.JPanel();
        trackTreeStructureJSP = new javax.swing.JScrollPane();
        interactiveObjectPanel = new javax.swing.JPanel();
        interactiveStructure = new javax.swing.JComboBox();
        consoleJSP = new javax.swing.JScrollPane();
        console = new javax.swing.JTextPane();
        mainMenu = new javax.swing.JMenuBar();
        experimentMenu = new javax.swing.JMenu();
        refreshExperimentListMenuItem = new javax.swing.JMenuItem();
        setSelectedExperimentMenuItem = new javax.swing.JMenuItem();
        newXPMenuItem = new javax.swing.JMenuItem();
        newXPFromTemplateMenuItem = new javax.swing.JMenuItem();
        deleteXPMenuItem = new javax.swing.JMenuItem();
        duplicateXPMenuItem = new javax.swing.JMenuItem();
        saveXPMenuItem = new javax.swing.JMenuItem();
        runMenu = new javax.swing.JMenu();
        importImagesMenuItem = new javax.swing.JMenuItem();
        runSelectedActionsMenuItem = new javax.swing.JMenuItem();
        runActionAllXPMenuItem = new javax.swing.JMenuItem();
        extractMeasurementMenuItem = new javax.swing.JMenuItem();
        extractSelectionMenuItem = new javax.swing.JMenuItem();
        optionMenu = new javax.swing.JMenu();
        jMenu2 = new javax.swing.JMenu();
        deleteMeasurementsCheckBox = new javax.swing.JCheckBoxMenuItem();
        dataBaseMenu = new javax.swing.JMenu();
        localFileSystemDatabaseRadioButton = new javax.swing.JRadioButtonMenuItem();
        localDBMenu = new javax.swing.JMenu();
        compactLocalDBMenuItem = new javax.swing.JMenuItem();
        importExportMenu = new javax.swing.JMenu();
        exportSubMenu = new javax.swing.JMenu();
        exportDataMenuItem = new javax.swing.JMenuItem();
        exportSelectedFieldsMenuItem = new javax.swing.JMenuItem();
        exportXPConfigMenuItem = new javax.swing.JMenuItem();
        exportWholeXPMenuItem = new javax.swing.JMenuItem();
        exportXPObjectsMenuItem = new javax.swing.JMenuItem();
        compareToDumpFileMenuItem = new javax.swing.JMenuItem();
        importSubMenu = new javax.swing.JMenu();
        importDataMenuItem = new javax.swing.JMenuItem();
        importPositionsToCurrentExperimentMenuItem = new javax.swing.JMenuItem();
        importConfigurationMenuItem = new javax.swing.JMenuItem();
        importConfigurationForSelectedPositionsMenuItem = new javax.swing.JMenuItem();
        importConfigurationForSelectedStructuresMenuItem = new javax.swing.JMenuItem();
        importNewExperimentMenuItem = new javax.swing.JMenuItem();
        unDumpObjectsMenuItem = new javax.swing.JMenuItem();
        exportOptionsSubMenu = new javax.swing.JMenu();
        exportObjectsMenuItem = new javax.swing.JCheckBoxMenuItem();
        exportPPImagesMenuItem = new javax.swing.JCheckBoxMenuItem();
        exportTrackImagesMenuItem = new javax.swing.JCheckBoxMenuItem();
        exportConfigMenuItem = new javax.swing.JCheckBoxMenuItem();
        exportSelectionsMenuItem = new javax.swing.JCheckBoxMenuItem();
        importOptionsSubMenu = new javax.swing.JMenu();
        importObjectsMenuItem = new javax.swing.JCheckBoxMenuItem();
        importPPImagesMenuItem = new javax.swing.JCheckBoxMenuItem();
        importTrackImagesMenuItem = new javax.swing.JCheckBoxMenuItem();
        importConfigMenuItem = new javax.swing.JCheckBoxMenuItem();
        importSelectionsMenuItem = new javax.swing.JCheckBoxMenuItem();
        jSeparator1 = new javax.swing.JPopupMenu.Separator();
        miscMenu = new javax.swing.JMenu();
        jMenu1 = new javax.swing.JMenu();
        clearMemoryMenuItem = new javax.swing.JMenuItem();
        CloseNonInteractiveWindowsMenuItem = new javax.swing.JMenuItem();
        closeAllWindowsMenuItem = new javax.swing.JMenuItem();
        clearTrackImagesMenuItem = new javax.swing.JMenuItem();
        clearPPImageMenuItem = new javax.swing.JMenuItem();
        openedImageNumberLimitMenu = new javax.swing.JMenu();
        localZoomMenu = new javax.swing.JMenu();
        kymographMenu = new javax.swing.JMenu();
        logMenu = new javax.swing.JMenu();
        setLogFileMenuItem = new javax.swing.JMenuItem();
        activateLoggingMenuItem = new javax.swing.JCheckBoxMenuItem();
        appendToFileMenuItem = new javax.swing.JCheckBoxMenuItem();
        shortcutMenu = new javax.swing.JMenu();
        shortcutPresetMenu = new javax.swing.JMenu();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);

        jSplitPane3.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        tabs.setPreferredSize(new java.awt.Dimension(840, 450));

        experimentFolder.setBackground(new Color(getBackground().getRGB()));
        experimentFolder.setText("localhost");
        experimentFolder.setBorder(javax.swing.BorderFactory.createTitledBorder("Experiment Group Folder"));
        experimentFolder.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                experimentFolderMousePressed(evt);
            }
        });
        experimentFolder.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                experimentFolderActionPerformed(evt);
            }
        });

        actionStructureJSP.setBorder(javax.swing.BorderFactory.createTitledBorder("Objects"));

        actionPositionJSP.setBorder(javax.swing.BorderFactory.createTitledBorder("Positions"));

        microscopyFieldList.setBackground(new java.awt.Color(247, 246, 246));
        microscopyFieldList.setCellRenderer(new TransparentListCellRenderer());
        microscopyFieldList.setOpaque(false);
        microscopyFieldList.setSelectionBackground(new java.awt.Color(57, 105, 138));
        microscopyFieldList.setSelectionForeground(new java.awt.Color(255, 255, 254));
        microscopyFieldList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                microscopyFieldListMousePressed(evt);
            }
        });
        actionPositionJSP.setViewportView(microscopyFieldList);

        actionJSP.setBorder(javax.swing.BorderFactory.createTitledBorder("Tasks"));

        runActionList.setBackground(new java.awt.Color(247, 246, 246));
        runActionList.setModel(new javax.swing.AbstractListModel() {
            String[] strings = { "Pre-Processing", "Segment and Track", "Track only", "Generate Kymographs", "Measurements", "Extract Measurements", "Export Data" };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        runActionList.setCellRenderer(new TransparentListCellRenderer());
        runActionList.setOpaque(false);
        runActionList.setSelectionBackground(new java.awt.Color(57, 105, 138));
        runActionList.setSelectionForeground(new java.awt.Color(255, 255, 254));
        actionJSP.setViewportView(runActionList);

        experimentJSP.setBorder(javax.swing.BorderFactory.createTitledBorder("Experiments:"));

        experimentList.setBackground(new java.awt.Color(247, 246, 246));
        experimentList.setBorder(null);
        experimentList.setCellRenderer(new TransparentListCellRenderer());
        experimentList.setOpaque(false);
        experimentList.setSelectionBackground(new java.awt.Color(57, 105, 138));
        experimentList.setSelectionForeground(new java.awt.Color(255, 255, 254));
        experimentList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                experimentListValueChanged(evt);
            }
        });
        experimentJSP.setViewportView(experimentList);

        actionPoolJSP.setBorder(javax.swing.BorderFactory.createTitledBorder("Tasks to execute"));

        actionPoolList.setBackground(new java.awt.Color(247, 246, 246));
        actionPoolList.setCellRenderer(new TransparentListCellRenderer());
        actionPoolList.setOpaque(false);
        actionPoolList.setSelectionBackground(new java.awt.Color(57, 105, 138));
        actionPoolList.setSelectionForeground(new java.awt.Color(255, 255, 254));
        setTransferHandler(new ListTransferHandler());
        actionPoolList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                actionPoolListMousePressed(evt);
            }
        });
        actionPoolJSP.setViewportView(actionPoolList);

        javax.swing.GroupLayout actionPanelLayout = new javax.swing.GroupLayout(actionPanel);
        actionPanel.setLayout(actionPanelLayout);
        actionPanelLayout.setHorizontalGroup(
            actionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(actionPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(actionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(experimentJSP)
                    .addComponent(experimentFolder))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(actionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(actionPositionJSP)
                    .addComponent(actionStructureJSP))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(actionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(actionPoolJSP, javax.swing.GroupLayout.DEFAULT_SIZE, 230, Short.MAX_VALUE)
                    .addComponent(actionJSP)))
        );
        actionPanelLayout.setVerticalGroup(
            actionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(actionPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(actionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(actionPanelLayout.createSequentialGroup()
                        .addComponent(actionJSP, javax.swing.GroupLayout.PREFERRED_SIZE, 195, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(actionPoolJSP, javax.swing.GroupLayout.DEFAULT_SIZE, 194, Short.MAX_VALUE))
                    .addGroup(actionPanelLayout.createSequentialGroup()
                        .addComponent(actionPositionJSP)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(actionStructureJSP, javax.swing.GroupLayout.PREFERRED_SIZE, 194, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(actionPanelLayout.createSequentialGroup()
                        .addComponent(experimentFolder, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(experimentJSP))))
        );

        tabs.addTab("Home", actionPanel);

        configurationJSP.setBackground(new java.awt.Color(247, 246, 246));

        javax.swing.GroupLayout configurationPanelLayout = new javax.swing.GroupLayout(configurationPanel);
        configurationPanel.setLayout(configurationPanelLayout);
        configurationPanelLayout.setHorizontalGroup(
            configurationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(configurationJSP)
        );
        configurationPanelLayout.setVerticalGroup(
            configurationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(configurationJSP, javax.swing.GroupLayout.DEFAULT_SIZE, 407, Short.MAX_VALUE)
        );

        tabs.addTab("Configuration", configurationPanel);

        trackPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Segmentation & Tracking Results"));

        trackSubPanel.setLayout(new javax.swing.BoxLayout(trackSubPanel, javax.swing.BoxLayout.LINE_AXIS));
        TimeJSP.setViewportView(trackSubPanel);

        javax.swing.GroupLayout trackPanelLayout = new javax.swing.GroupLayout(trackPanel);
        trackPanel.setLayout(trackPanelLayout);
        trackPanelLayout.setHorizontalGroup(
            trackPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(TimeJSP, javax.swing.GroupLayout.DEFAULT_SIZE, 337, Short.MAX_VALUE)
        );
        trackPanelLayout.setVerticalGroup(
            trackPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(TimeJSP)
        );

        selectionPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Selections"));

        selectionList.setBackground(new java.awt.Color(247, 246, 246));
        selectionList.setOpaque(false);
        selectionJSP.setViewportView(selectionList);

        createSelectionButton.setText("Create Selection");
        createSelectionButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                createSelectionButtonActionPerformed(evt);
            }
        });

        reloadSelectionsButton.setText("Reload Selections");
        reloadSelectionsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                reloadSelectionsButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout selectionPanelLayout = new javax.swing.GroupLayout(selectionPanel);
        selectionPanel.setLayout(selectionPanelLayout);
        selectionPanelLayout.setHorizontalGroup(
            selectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(createSelectionButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(reloadSelectionsButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(selectionJSP, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 272, javax.swing.GroupLayout.PREFERRED_SIZE)
        );
        selectionPanelLayout.setVerticalGroup(
            selectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, selectionPanelLayout.createSequentialGroup()
                .addComponent(createSelectionButton, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(reloadSelectionsButton, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(selectionJSP, javax.swing.GroupLayout.DEFAULT_SIZE, 176, Short.MAX_VALUE))
        );

        controlPanelJSP.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        controlPanelJSP.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        editPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createTitledBorder("Editing")));

        selectAllTracksButton.setText("Select All Tracks (Q)");
        selectAllTracksButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectAllTracksButtonActionPerformed(evt);
            }
        });

        nextTrackErrorButton.setText("Navigate Next (X)");
        nextTrackErrorButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nextTrackErrorButtonActionPerformed(evt);
            }
        });

        splitObjectsButton.setText("Split (S)");
        splitObjectsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                splitObjectsButtonActionPerformed(evt);
            }
        });

        mergeObjectsButton.setText("Merge Objects (M)");
        mergeObjectsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mergeObjectsButtonActionPerformed(evt);
            }
        });

        previousTrackErrorButton.setText("Navigate Previous (W)");
        previousTrackErrorButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                previousTrackErrorButtonActionPerformed(evt);
            }
        });

        selectAllObjectsButton.setText("Select All Objects (A)");
        selectAllObjectsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectAllObjectsButtonActionPerformed(evt);
            }
        });

        deleteObjectsButton.setText("Delete Objects (D)");
        deleteObjectsButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                deleteObjectsButtonMousePressed(evt);
            }
        });
        deleteObjectsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteObjectsButtonActionPerformed(evt);
            }
        });

        updateRoiDisplayButton.setText("Update ROI Display");
        updateRoiDisplayButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                updateRoiDisplayButtonActionPerformed(evt);
            }
        });

        manualSegmentButton.setText("Segment (C)");
        manualSegmentButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                manualSegmentButtonActionPerformed(evt);
            }
        });

        testManualSegmentationButton.setText("Test");
        testManualSegmentationButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                testManualSegmentationButtonActionPerformed(evt);
            }
        });

        linkObjectsButton.setText("Link Objects (L)");
        linkObjectsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                linkObjectsButtonActionPerformed(evt);
            }
        });

        unlinkObjectsButton.setText("Unlink Objects (U)");
        unlinkObjectsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                unlinkObjectsButtonActionPerformed(evt);
            }
        });

        resetLinksButton.setText("Reset Links (R)");
        resetLinksButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetLinksButtonActionPerformed(evt);
            }
        });

        testSplitButton.setText("Test");
        testSplitButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                testSplitButtonActionPerformed(evt);
            }
        });

        pruneTrackButton.setText("Prune Track (P)");
        pruneTrackButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pruneTrackButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout editPanelLayout = new javax.swing.GroupLayout(editPanel);
        editPanel.setLayout(editPanelLayout);
        editPanelLayout.setHorizontalGroup(
            editPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(selectAllTracksButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(nextTrackErrorButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(mergeObjectsButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(previousTrackErrorButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(selectAllObjectsButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(deleteObjectsButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(linkObjectsButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(unlinkObjectsButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(resetLinksButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, editPanelLayout.createSequentialGroup()
                .addGroup(editPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(splitObjectsButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(manualSegmentButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(editPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(testManualSegmentationButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(testSplitButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
            .addComponent(pruneTrackButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(updateRoiDisplayButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        editPanelLayout.setVerticalGroup(
            editPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(editPanelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(updateRoiDisplayButton, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(selectAllObjectsButton, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(selectAllTracksButton, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(nextTrackErrorButton, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(previousTrackErrorButton, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(editPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(testManualSegmentationButton, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(manualSegmentButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(editPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(testSplitButton, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(splitObjectsButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(mergeObjectsButton, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(deleteObjectsButton, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pruneTrackButton, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(linkObjectsButton, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(unlinkObjectsButton, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(resetLinksButton, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        controlPanelJSP.setViewportView(editPanel);

        trackTreeStructureJSP.setBorder(javax.swing.BorderFactory.createTitledBorder("Displayed Objects"));
        trackTreeStructureJSP.setForeground(new java.awt.Color(60, 60, 60));

        javax.swing.GroupLayout trackTreeStructurePanelLayout = new javax.swing.GroupLayout(trackTreeStructurePanel);
        trackTreeStructurePanel.setLayout(trackTreeStructurePanelLayout);
        trackTreeStructurePanelLayout.setHorizontalGroup(
            trackTreeStructurePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
            .addGroup(trackTreeStructurePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(trackTreeStructureJSP, javax.swing.GroupLayout.PREFERRED_SIZE, 282, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        trackTreeStructurePanelLayout.setVerticalGroup(
            trackTreeStructurePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 125, Short.MAX_VALUE)
            .addGroup(trackTreeStructurePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(trackTreeStructureJSP, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 125, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        interactiveObjectPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Interactive Objects"));

        interactiveStructure.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                interactiveStructureActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout interactiveObjectPanelLayout = new javax.swing.GroupLayout(interactiveObjectPanel);
        interactiveObjectPanel.setLayout(interactiveObjectPanelLayout);
        interactiveObjectPanelLayout.setHorizontalGroup(
            interactiveObjectPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(interactiveStructure, javax.swing.GroupLayout.Alignment.TRAILING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        interactiveObjectPanelLayout.setVerticalGroup(
            interactiveObjectPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(interactiveStructure, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );

        javax.swing.GroupLayout dataPanelLayout = new javax.swing.GroupLayout(dataPanel);
        dataPanel.setLayout(dataPanelLayout);
        dataPanelLayout.setHorizontalGroup(
            dataPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(dataPanelLayout.createSequentialGroup()
                .addGroup(dataPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(controlPanelJSP)
                    .addComponent(interactiveObjectPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(dataPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(selectionPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(trackTreeStructurePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(trackPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        dataPanelLayout.setVerticalGroup(
            dataPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, dataPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(dataPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(trackPanel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(dataPanelLayout.createSequentialGroup()
                        .addComponent(trackTreeStructurePanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(selectionPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(dataPanelLayout.createSequentialGroup()
                        .addComponent(interactiveObjectPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(controlPanelJSP, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)))
                .addGap(4, 4, 4))
        );

        tabs.addTab("Data Browsing", dataPanel);

        jSplitPane3.setLeftComponent(tabs);

        consoleJSP.setBackground(new Color(getBackground().getRGB()));
        consoleJSP.setBorder(javax.swing.BorderFactory.createTitledBorder("Console:"));
        consoleJSP.setMinimumSize(new java.awt.Dimension(32, 100));
        consoleJSP.setOpaque(false);

        console.setEditable(false);
        console.setBackground(new Color(getBackground().getRGB()));
        console.setBorder(null);
        console.setFont(new java.awt.Font("Courier 10 Pitch", 0, 12)); // NOI18N
        JPopupMenu consoleMenu = new JPopupMenu();
        Action copy = new DefaultEditorKit.CopyAction();
        copy.putValue(Action.NAME, "Copy");
        copy.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control C"));
        consoleMenu.add( copy );
        Action selectAll = new TextAction("Select All") {
            @Override public void actionPerformed(ActionEvent e) {
                JTextComponent component = getFocusedComponent();
                component.selectAll();
                component.requestFocusInWindow();
            }
        };
        consoleMenu.add( selectAll );
        Action clear = new TextAction("Clear") {
            @Override public void actionPerformed(ActionEvent e) {
                JTextComponent component = getFocusedComponent();
                component.setText(null);
                component.requestFocusInWindow();
            }
        };
        consoleMenu.add( clear );
        console.setComponentPopupMenu( consoleMenu );
        consoleJSP.setViewportView(console);

        jSplitPane3.setBottomComponent(consoleJSP);

        experimentMenu.setText("Experiment");

        refreshExperimentListMenuItem.setText("Refresh List");
        refreshExperimentListMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshExperimentListMenuItemActionPerformed(evt);
            }
        });
        experimentMenu.add(refreshExperimentListMenuItem);

        setSelectedExperimentMenuItem.setText("Open / Close Selected Experiment");
        setSelectedExperimentMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                setSelectedExperimentMenuItemActionPerformed(evt);
            }
        });
        experimentMenu.add(setSelectedExperimentMenuItem);

        newXPMenuItem.setText("New");
        newXPMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newXPMenuItemActionPerformed(evt);
            }
        });
        experimentMenu.add(newXPMenuItem);

        newXPFromTemplateMenuItem.setText("New Experiment from Template");
        newXPFromTemplateMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newXPFromTemplateMenuItemActionPerformed(evt);
            }
        });
        experimentMenu.add(newXPFromTemplateMenuItem);

        deleteXPMenuItem.setText("Delete");
        deleteXPMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteXPMenuItemActionPerformed(evt);
            }
        });
        experimentMenu.add(deleteXPMenuItem);

        duplicateXPMenuItem.setText("Duplicate");
        duplicateXPMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                duplicateXPMenuItemActionPerformed(evt);
            }
        });
        experimentMenu.add(duplicateXPMenuItem);

        saveXPMenuItem.setText("Save Changes");
        saveXPMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveXPMenuItemActionPerformed(evt);
            }
        });
        experimentMenu.add(saveXPMenuItem);

        mainMenu.add(experimentMenu);

        runMenu.setText("Run");

        importImagesMenuItem.setText("Import/re-link Images");
        importImagesMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importImagesMenuItemActionPerformed(evt);
            }
        });
        runMenu.add(importImagesMenuItem);

        runSelectedActionsMenuItem.setText("Run Selected Tasks");
        runSelectedActionsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runSelectedActionsMenuItemActionPerformed(evt);
            }
        });
        runMenu.add(runSelectedActionsMenuItem);

        runActionAllXPMenuItem.setText("Run Selected Tasks on all Selected Experiments");
        runActionAllXPMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runActionAllXPMenuItemActionPerformed(evt);
            }
        });
        runMenu.add(runActionAllXPMenuItem);

        extractMeasurementMenuItem.setText("Extract Measurements");
        extractMeasurementMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                extractMeasurementMenuItemActionPerformed(evt);
            }
        });
        runMenu.add(extractMeasurementMenuItem);

        extractSelectionMenuItem.setText("Extract Selections");
        extractSelectionMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                extractSelectionMenuItemActionPerformed(evt);
            }
        });
        runMenu.add(extractSelectionMenuItem);

        mainMenu.add(runMenu);

        optionMenu.setText("Options");

        jMenu2.setText("Measurements");

        deleteMeasurementsCheckBox.setSelected(true);
        deleteMeasurementsCheckBox.setText("Delete existing measurements before Running Measurements");
        deleteMeasurementsCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteMeasurementsCheckBoxActionPerformed(evt);
            }
        });
        jMenu2.add(deleteMeasurementsCheckBox);

        optionMenu.add(jMenu2);

        dataBaseMenu.setText("Database Type");

        localFileSystemDatabaseRadioButton.setSelected(true);
        localFileSystemDatabaseRadioButton.setText("Local file system");
        localFileSystemDatabaseRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                localFileSystemDatabaseRadioButtonActionPerformed(evt);
            }
        });
        dataBaseMenu.add(localFileSystemDatabaseRadioButton);

        optionMenu.add(dataBaseMenu);

        localDBMenu.setText("Local DataBase");

        compactLocalDBMenuItem.setText("Compact Selected Experiment(s)");
        compactLocalDBMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                compactLocalDBMenuItemActionPerformed(evt);
            }
        });
        localDBMenu.add(compactLocalDBMenuItem);

        optionMenu.add(localDBMenu);

        mainMenu.add(optionMenu);

        importExportMenu.setText("Import/Export");

        exportSubMenu.setText("Export");

        exportDataMenuItem.setText("Data From Selected Experiment(s) (see export options)");
        exportDataMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportDataMenuItemActionPerformed(evt);
            }
        });
        exportSubMenu.add(exportDataMenuItem);

        exportSelectedFieldsMenuItem.setText("Selected Fields");
        exportSelectedFieldsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportSelectedFieldsMenuItemActionPerformed(evt);
            }
        });
        exportSubMenu.add(exportSelectedFieldsMenuItem);

        exportXPConfigMenuItem.setText("Configuration Only");
        exportXPConfigMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportXPConfigMenuItemActionPerformed(evt);
            }
        });
        exportSubMenu.add(exportXPConfigMenuItem);

        exportWholeXPMenuItem.setText("Whole Experiment(s)");
        exportWholeXPMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportWholeXPMenuItemActionPerformed(evt);
            }
        });
        exportSubMenu.add(exportWholeXPMenuItem);

        exportXPObjectsMenuItem.setText("Objects of Selected Experiment(s)");
        exportXPObjectsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportXPObjectsMenuItemActionPerformed(evt);
            }
        });
        exportSubMenu.add(exportXPObjectsMenuItem);

        compareToDumpFileMenuItem.setText("Compare To Dump file");
        compareToDumpFileMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                compareToDumpFileMenuItemActionPerformed(evt);
            }
        });
        exportSubMenu.add(compareToDumpFileMenuItem);

        importExportMenu.add(exportSubMenu);

        importSubMenu.setText("Import");

        importDataMenuItem.setText("Data From Selected File to Current Experiment (see import options)");
        importDataMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importDataMenuItemActionPerformed(evt);
            }
        });
        importSubMenu.add(importDataMenuItem);

        importPositionsToCurrentExperimentMenuItem.setText("Objects to Current Experiment");
        importPositionsToCurrentExperimentMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importPositionsToCurrentExperimentMenuItemActionPerformed(evt);
            }
        });
        importSubMenu.add(importPositionsToCurrentExperimentMenuItem);

        importConfigurationMenuItem.setText("Configuration to Current Experiment");
        importConfigurationMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importConfigurationMenuItemActionPerformed(evt);
            }
        });
        importSubMenu.add(importConfigurationMenuItem);

        importConfigurationForSelectedPositionsMenuItem.setText("Configuration for Selected Positions");
        importConfigurationForSelectedPositionsMenuItem.setEnabled(false);
        importConfigurationForSelectedPositionsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importConfigurationForSelectedPositionsMenuItemActionPerformed(evt);
            }
        });
        importSubMenu.add(importConfigurationForSelectedPositionsMenuItem);

        importConfigurationForSelectedStructuresMenuItem.setText("Configuration for Selected Object type(s)");
        importConfigurationForSelectedStructuresMenuItem.setEnabled(false);
        importConfigurationForSelectedStructuresMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importConfigurationForSelectedStructuresMenuItemActionPerformed(evt);
            }
        });
        importSubMenu.add(importConfigurationForSelectedStructuresMenuItem);

        importNewExperimentMenuItem.setText("New Experiment(s)");
        importNewExperimentMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importNewExperimentMenuItemActionPerformed(evt);
            }
        });
        importSubMenu.add(importNewExperimentMenuItem);

        unDumpObjectsMenuItem.setText("Dumped Experiment(s)");
        unDumpObjectsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                unDumpObjectsMenuItemActionPerformed(evt);
            }
        });
        importSubMenu.add(unDumpObjectsMenuItem);

        importExportMenu.add(importSubMenu);

        exportOptionsSubMenu.setText("Export Options");

        exportObjectsMenuItem.setSelected(true);
        exportObjectsMenuItem.setText("Objects");
        exportOptionsSubMenu.add(exportObjectsMenuItem);

        exportPPImagesMenuItem.setSelected(true);
        exportPPImagesMenuItem.setText("Pre Processed Images");
        exportOptionsSubMenu.add(exportPPImagesMenuItem);

        exportTrackImagesMenuItem.setSelected(true);
        exportTrackImagesMenuItem.setText("Track Images");
        exportOptionsSubMenu.add(exportTrackImagesMenuItem);

        exportConfigMenuItem.setSelected(true);
        exportConfigMenuItem.setText("Configuration");
        exportOptionsSubMenu.add(exportConfigMenuItem);

        exportSelectionsMenuItem.setSelected(true);
        exportSelectionsMenuItem.setText("Selections");
        exportOptionsSubMenu.add(exportSelectionsMenuItem);

        importExportMenu.add(exportOptionsSubMenu);

        importOptionsSubMenu.setText("Import Options");

        importObjectsMenuItem.setSelected(true);
        importObjectsMenuItem.setText("Objects");
        importObjectsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importObjectsMenuItemActionPerformed(evt);
            }
        });
        importOptionsSubMenu.add(importObjectsMenuItem);

        importPPImagesMenuItem.setSelected(true);
        importPPImagesMenuItem.setText("Pre-Processed Images");
        importOptionsSubMenu.add(importPPImagesMenuItem);

        importTrackImagesMenuItem.setSelected(true);
        importTrackImagesMenuItem.setText("Track Images");
        importOptionsSubMenu.add(importTrackImagesMenuItem);

        importConfigMenuItem.setSelected(true);
        importConfigMenuItem.setText("Configuration");
        importOptionsSubMenu.add(importConfigMenuItem);

        importSelectionsMenuItem.setSelected(true);
        importSelectionsMenuItem.setText("Selections");
        importOptionsSubMenu.add(importSelectionsMenuItem);

        importExportMenu.add(importOptionsSubMenu);
        importExportMenu.add(jSeparator1);

        mainMenu.add(importExportMenu);

        miscMenu.setText("Misc");

        jMenu1.setText("Close Image / Clear Memory");

        clearMemoryMenuItem.setText("Clear Memory");
        clearMemoryMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearMemoryMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(clearMemoryMenuItem);

        CloseNonInteractiveWindowsMenuItem.setText("Close Non Interactive Windows");
        CloseNonInteractiveWindowsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                CloseNonInteractiveWindowsMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(CloseNonInteractiveWindowsMenuItem);

        closeAllWindowsMenuItem.setText("Close all windows");
        closeAllWindowsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeAllWindowsMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(closeAllWindowsMenuItem);

        clearTrackImagesMenuItem.setText("Clear Track Images");
        clearTrackImagesMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearTrackImagesMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(clearTrackImagesMenuItem);

        clearPPImageMenuItem.setText("Clear Pre-Processed Images");
        clearPPImageMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearPPImageMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(clearPPImageMenuItem);

        miscMenu.add(jMenu1);

        openedImageNumberLimitMenu.setText("Number of opened Images Limit");
        miscMenu.add(openedImageNumberLimitMenu);

        localZoomMenu.setText("Local Zoom");
        miscMenu.add(localZoomMenu);

        kymographMenu.setText("Kymograph");
        miscMenu.add(kymographMenu);

        logMenu.setText("Log");

        setLogFileMenuItem.setText("Set Log File");
        setLogFileMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                setLogFileMenuItemActionPerformed(evt);
            }
        });
        logMenu.add(setLogFileMenuItem);

        activateLoggingMenuItem.setSelected(PropertyUtils.get(PropertyUtils.LOG_ACTIVATED, true));
        activateLoggingMenuItem.setText("Activate Logging");
        activateLoggingMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                activateLoggingMenuItemActionPerformed(evt);
            }
        });
        logMenu.add(activateLoggingMenuItem);

        appendToFileMenuItem.setSelected(PropertyUtils.get(PropertyUtils.LOG_APPEND, false));
        appendToFileMenuItem.setText("Append to File");
        appendToFileMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                appendToFileMenuItemActionPerformed(evt);
            }
        });
        logMenu.add(appendToFileMenuItem);

        miscMenu.add(logMenu);

        mainMenu.add(miscMenu);

        shortcutMenu.setText("Shortcuts");

        shortcutPresetMenu.setText("Shortcut Preset");
        shortcutMenu.add(shortcutPresetMenu);

        mainMenu.add(shortcutMenu);

        setJMenuBar(mainMenu);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 802, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jSplitPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 664, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    
    private String getCurrentHostNameOrDir() {
        return getHostNameOrDir(getSelectedExperiment());
    }
    private String getHostNameOrDir(String xpName) {
        String host = this.experimentFolder.getText();
        if (this.localFileSystemDatabaseRadioButton.isSelected()) {
            if (xpName==null) return null;
            File f = this.dbFiles.get(xpName);
            if (f!=null) {
                host = f.getAbsolutePath();
                logger.debug("xp: {} fir {}", xpName, host, f.getAbsolutePath());
            }
            else {
                f = new File(host+File.separator+xpName);
                f.mkdirs();
                logger.debug("create dir for xp: {} -> {} (is Dir: {})", xpName, f.getAbsolutePath(), f.isDirectory());
                dbFiles.put(xpName, f);
                host = f.getAbsolutePath();
            }
        }
        return host;
    }
    public void navigateToNextImage(boolean next) {
        if (trackTreeController==null) this.loadObjectTrees();
        Object activeImage = ImageWindowManagerFactory.getImageManager().getDisplayer().getCurrentImage();
        if (activeImage == null) return;
        ImageWindowManager.RegisteredImageType imageType = ImageWindowManagerFactory.getImageManager().getRegisterType(activeImage);
        logger.debug("active image type: {}", imageType);
        if (ImageWindowManager.RegisteredImageType.PreProcessed.equals(imageType) || ImageWindowManager.RegisteredImageType.RawInput.equals(imageType)) { // input image ?
            String position = ImageWindowManagerFactory.getImageManager().getPositionOfInputImage(activeImage);
            if (position == null) return;
            else {
                int pIdx = db.getExperiment().getPositionIdx(position);
                int nIdx = pIdx + (next?1:-1);
                if (nIdx<0) return;
                if (nIdx>=db.getExperiment().getPositionCount()) return;
                String nextPosition = db.getExperiment().getPosition(nIdx).getName();
                boolean pp = ImageWindowManager.RegisteredImageType.PreProcessed.equals(imageType);
                db.getExperiment().flushImages(true, true, nextPosition);
                IJVirtualStack.openVirtual(db.getExperiment(), nextPosition, pp);
            }
        } else  { // interactive: if IOI found
            InteractiveImage i = ImageWindowManagerFactory.getImageManager().getImageObjectInterface(null);
            if (i==null) return;
            // get next parent
            StructureObject nextParent = null;
            if (i.getParent().isRoot()) return;
            List<StructureObject> siblings = i.getParent().getSiblings();
            int idx = siblings.indexOf(i.getParent());
            // current image structure: 
            InteractiveImageKey key = ImageWindowManagerFactory.getImageManager().getImageObjectInterfaceKey(ImageWindowManagerFactory.getImageManager().getDisplayer().getCurrentImage2());
            int currentImageStructure = key ==null ? i.getChildStructureIdx() : key.displayedStructureIdx;
            if (i.getChildStructureIdx() == currentImageStructure) idx += (next ? 1 : -1) ; // only increment if same structure
            logger.debug("current inter: {}, current image child: {}",interactiveStructure.getSelectedIndex()-1, currentImageStructure);
            if (siblings.size()==idx || idx<0) { // next position
                List<String> positions = Arrays.asList(i.getParent().getExperiment().getPositionsAsString());
                int idxP = positions.indexOf(i.getParent().getPositionName()) + (next ? 1 : -1);
                if (idxP<0 || idxP==positions.size()) return;
                String nextPos = positions.get(idxP);
                ObjectDAO dao = i.getParent().getDAO().getMasterDAO().getDao(nextPos);
                List<StructureObject> allObjects = StructureObjectUtils.getAllObjects(dao, i.getParent().getStructureIdx());
                allObjects.removeIf(o->!o.isTrackHead());
                if (allObjects.isEmpty()) return;
                Collections.sort(allObjects);
                nextParent = next ? allObjects.get(0) : allObjects.get(allObjects.size()-1);
            } else nextParent = siblings.get(idx);
            logger.debug("open next Image : next parent: {}", nextParent);
            if (nextParent==null) return;
            List<StructureObject> parentTrack = StructureObjectUtils.getTrack(nextParent, false);
            i= ImageWindowManagerFactory.getImageManager().getImageTrackObjectInterface(parentTrack, i.getChildStructureIdx());
            Image im = ImageWindowManagerFactory.getImageManager().getImage(i, i.getChildStructureIdx());
            if (im==null) ImageWindowManagerFactory.getImageManager().addImage(i.generatemage(i.getChildStructureIdx(), true), i, i.getChildStructureIdx(), true);
            else ImageWindowManagerFactory.getImageManager().setActive(im);
        }
    }
    private int navigateCount = 0;
    public void navigateToNextObjects(boolean next, boolean nextPosition, int structureDisplay, boolean setInteractiveStructure) {
        if (trackTreeController==null) this.loadObjectTrees();
        int displaySIdx = 0;
        Selection sel = getNavigatingSelection();
        if (sel==null) ImageWindowManagerFactory.getImageManager().goToNextTrackError(null, this.trackTreeController.getLastTreeGenerator().getSelectedTrackHeads(), next);
        else {
            InteractiveImage i = ImageWindowManagerFactory.getImageManager().getImageObjectInterface(null);
            if (i!=null && i.getParent().getExperiment()!=db.getExperiment()) i=null;
            if (structureDisplay==-1 && i!=null) {
                Image im = ImageWindowManagerFactory.getImageManager().getDisplayer().getCurrentImage2();
                if (im!=null) {
                    InteractiveImageKey key = ImageWindowManagerFactory.getImageManager().getImageObjectInterfaceKey(im);
                    if (key!=null) {
                        structureDisplay = key.displayedStructureIdx;
                    }
                }
            }
            String position = i==null? null:i.getParent().getPositionName();
            if (i==null || nextPosition) navigateCount=2;
            else {
                i = fixIOI(i, sel.getStructureIdx());
                if (i!=null) displaySIdx = i.getParent().getStructureIdx();
                List<StructureObject> objects = Pair.unpairKeys(SelectionUtils.filterPairs(i.getObjects(), sel.getElementStrings(position)));
                logger.debug("#objects from selection on current image: {} (display sIdx: {}, IOI: {}, sel: {})", objects.size(), displaySIdx, i.getChildStructureIdx(), sel.getStructureIdx());
                boolean move = !objects.isEmpty() && ImageWindowManagerFactory.getImageManager().goToNextObject(null, objects, next);
                if (move) {
                    navigateCount=0;
                }
                else navigateCount++;
            }
            if (navigateCount>1) { // open next/prev image containig objects
                Collection<String> l;
                if (nextPosition || position==null) {
                    String selPos = null;
                    //if (position==null) selPos = this.trackTreeController.getSelectedPosition();
                    if (selPos!=null) position = selPos;
                    else position = SelectionUtils.getNextPosition(sel, position, next);
                    l = position==null ? null : sel.getElementStrings(position);
                    while (position!=null && (l==null || l.isEmpty())) {
                        position = SelectionUtils.getNextPosition(sel, position, next); 
                        l = position==null ? null : sel.getElementStrings(position);
                    }
                    i=null;
                    logger.debug("changing position");
                } else l = sel.getElementStrings(position);
                logger.debug("position: {}, #objects: {}, nav: {}, NextPosition? {}", position, position!=null ? l.size() : 0, navigateCount, nextPosition);
                if (position==null) return;
                this.trackTreeController.selectPosition(position);
                List<StructureObject> parents = SelectionUtils.getParentTrackHeads(sel, position, displaySIdx, db);
                Collections.sort(parents);
                if (parents.size()<=1) nextPosition=true;
                logger.debug("parent track heads: {} (sel: {}, displaySIdx: {})", parents.size(), sel.getStructureIdx(), displaySIdx);
                int nextParentIdx = 0;
                if (i!=null && !nextPosition) {
                    int idx = Collections.binarySearch(parents, i.getParent());
                    if (idx<-1) nextParentIdx = -idx-1 + (next ? 0:-1); // current image's parent is not in selection
                    else if (idx==-1) nextParentIdx=-1;
                    else nextParentIdx = idx + (next ? 1:-1) ;
                    logger.warn("next parent idx: {} (search idx: {}) parent {} all parents: {}", nextParentIdx, idx, i.getParent(), parents);
                } else if (nextPosition) {
                    nextParentIdx = next ? 0 : parents.size()-1;
                }
                if (structureDisplay<0) structureDisplay = sel.getStructureIdx();
                if ((nextParentIdx<0 || nextParentIdx>=parents.size())) {
                    logger.warn("no next parent found in objects parents: {}", parents);
                    navigateToNextObjects(next, true, structureDisplay, setInteractiveStructure);
                } else {
                    StructureObject nextParent = parents.get(nextParentIdx);
                    logger.debug("next parent: {}", nextParent);
                    List track = db.getDao(nextParent.getPositionName()).getTrack(nextParent);
                    ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
                    InteractiveImage nextI = iwm.getImageTrackObjectInterface(track, sel.getStructureIdx());
                    Image im = iwm.getImage(nextI);
                    boolean newImage = im==null;
                    if (im==null) {
                        im = nextI.generatemage(structureDisplay, true);
                        iwm.addImage(im, nextI, structureDisplay, true);
                        
                    }
                    else ImageWindowManagerFactory.getImageManager().setActive(im);
                    navigateCount=0;
                    if (newImage && setInteractiveStructure) { // new image open -> set interactive structure & navigate to next object in newly opened image
                        setInteractiveStructureIdx(sel.getStructureIdx());
                        interactiveStructureActionPerformed(null);
                    }
                    List<StructureObject> objects = Pair.unpairKeys(SelectionUtils.filterPairs(nextI.getObjects(), sel.getElementStrings(position)));
                    logger.debug("#objects from selection on next image: {}/{} (display sIdx: {}, IOI: {}, sel: {}, im:{})", objects.size(), nextI.getObjects().size(), displaySIdx, nextI.getChildStructureIdx(), sel.getStructureIdx(), im!=null);
                    if (!objects.isEmpty()) {
                        // wait so that new image is displayed -> magnification issue -> window is not well computed
                        if (iwm.getDisplayer() instanceof IJImageDisplayer) {
                            int timeLimit = 4000;
                            int time = 0;
                            double m = ((IJImageDisplayer)iwm.getDisplayer()).getImage(im).getCanvas().getMagnification();
                            while(m<1 && time<timeLimit) { 
                                try { 
                                    Thread.sleep(500);
                                    time+=500;
                                } catch (InterruptedException ex) {}
                                m = ((IJImageDisplayer)iwm.getDisplayer()).getImage(im).getCanvas().getMagnification();
                            }
                        }
                        ImageWindowManagerFactory.getImageManager().goToNextObject(im, objects, next);
                        
                    }
                }
            }
        }
    }
    private Selection getNavigatingSelection() {
        List<Selection> res = getSelections();
        res.removeIf(s->!s.isNavigate());
        if (res.isEmpty()) {
            if (selectionList.getSelectedIndex()>=0) return (Selection)selectionList.getSelectedValue();
            else return null;
        } else if (res.size()==1) return res.get(0);
        else return null;
    }
    private List<Selection> getAddObjectsSelection(int selNumber) {
        List<Selection> res = getSelections();
        res.removeIf(s->!s.isAddObjects(selNumber));
        return res;
    }
    private static String createSubdir(String path, String dbName) {
        if (!new File(path).isDirectory()) return null;
        File newDBDir = new File(path+File.separator+ExperimentSearchUtils.removePrefix(dbName, GUI.DBprefix));
        if (newDBDir.exists()) {
            logger.info("folder : {}, already exists", newDBDir.getAbsolutePath());
            if (!ExperimentSearchUtils.listExperiments(newDBDir.getAbsolutePath(), false, null).isEmpty()) {
                logger.info("folder : {}, already exists and contains xp", newDBDir.getAbsolutePath());
                return null;
            } else {
                logger.info("folder : {}, already exists", newDBDir.getAbsolutePath());
            }
        }
        newDBDir.mkdir();
        if (!newDBDir.isDirectory()) {
            logger.error("folder : {}, couldn't be created", newDBDir.getAbsolutePath());
            return null;
        }
        return newDBDir.getAbsolutePath();
    }
    private void refreshExperimentListMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshExperimentListMenuItemActionPerformed
        populateExperimentList();
    }//GEN-LAST:event_refreshExperimentListMenuItemActionPerformed

    private void setSelectedExperimentMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setSelectedExperimentMenuItemActionPerformed
        String host = getCurrentHostNameOrDir();
        String dbName = getSelectedExperiment();
        if (dbName==null || (this.db!=null && db.getDBName().equals(dbName)) || getSelectedExperiments().size()>1) closeExperiment();
        else {
            openExperiment(dbName, host, false);
            if (db!=null) PropertyUtils.set(PropertyUtils.LAST_SELECTED_EXPERIMENT, dbName);
        }
    }//GEN-LAST:event_setSelectedExperimentMenuItemActionPerformed

    private void newXPMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newXPMenuItemActionPerformed
        String name = JOptionPane.showInputDialog("New XP name:");
        if (name==null) return;
        name = ExperimentSearchUtils.addPrefix(name, currentDBPrefix);
        if (!Utils.isValid(name, false)) logger.error("Name should not contain special characters");
        else if (getDBNames().contains(name)) logger.error("XP name already exists");
        else {
            String adress = null;
            if (MasterDAOFactory.getCurrentType().equals(MasterDAOFactory.DAOType.DBMap)) { // create directory
                File dir = new File(experimentFolder.getText());
                adress = createSubdir(dir.getAbsolutePath(), name);
                logger.debug("new xp dir: {}", adress);
                if (adress==null) return;
            }
            MasterDAO db2 = MasterDAOFactory.createDAO(name, adress);
            if (db2.setConfigurationReadOnly(false)) {
                this.setMessage("Could not modify experiment "+name+" @ "+  adress);
                return;
            }
            Experiment xp2 = new Experiment(name);
            xp2.setName(name);
            if (MasterDAOFactory.getCurrentType().equals(MasterDAOFactory.DAOType.DBMap)) xp2.setOutputDirectory(adress+File.separator+"Output");
            db2.setExperiment(xp2);
            db2.updateExperiment();
            db2.unlockConfiguration();
            db2.clearCache();
            populateExperimentList();
            openExperiment(name, null, false);
            if (this.db!=null) setSelectedExperiment(name);
        }
    }//GEN-LAST:event_newXPMenuItemActionPerformed

    private void deleteXPMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteXPMenuItemActionPerformed
        List<String> xps = getSelectedExperiments();
        if (xps==null || xps.isEmpty()) return;
        if (Utils.promptBoolean( "Delete Selected Experiment"+(xps.size()>1?"s":"")+" (all data will be lost)", this)) {
            if (db!=null && xps.contains(db.getDBName())) closeExperiment();
            for (String xpName : xps) MasterDAOFactory.createDAO(xpName, getHostNameOrDir(xpName)).eraseAll();
            populateExperimentList();
        }
    }//GEN-LAST:event_deleteXPMenuItemActionPerformed

    private void duplicateXPMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_duplicateXPMenuItemActionPerformed
        String name = JOptionPane.showInputDialog("New experiment name:", getSelectedExperiment());
        name = ExperimentSearchUtils.addPrefix(name, currentDBPrefix);
        if (!Utils.isValid(name, false)) logger.error("Name should not contain special characters");
        else if (getDBNames().contains(name)) logger.error("experiment name already exists");
        else {
            closeExperiment();
            MasterDAO db1 = MasterDAOFactory.createDAO(getSelectedExperiment(), getCurrentHostNameOrDir());
            String adress = null;
            if (MasterDAOFactory.getCurrentType().equals(MasterDAOFactory.DAOType.DBMap)) { // create directory
                File dir = new File(getCurrentHostNameOrDir()).getParentFile();
                adress = createSubdir(dir.getAbsolutePath(), name);
                logger.debug("duplicate experiment dir: {}", adress);
                if (adress==null) return;
            }
            MasterDAO db2 = MasterDAOFactory.createDAO(name, adress);
            if (db2.setConfigurationReadOnly(false)) {
                this.setMessage("Could not modify experiment "+name+" @ "+  adress);
                return;
            }
            Experiment xp2 = db1.getExperiment().duplicate();
            xp2.clearPositions();
            xp2.setName(name);
            xp2.setOutputDirectory(adress+File.separator+"Output");
            xp2.setOutputImageDirectory(xp2.getOutputDirectory());
            db2.setExperiment(xp2);
            db2.updateExperiment();
            db2.clearCache();
            db2.unlockConfiguration();
            populateExperimentList();
            db1.clearCache();
            openExperiment(name, null, false);
            if (this.db!=null) setSelectedExperiment(name);
        }
    }//GEN-LAST:event_duplicateXPMenuItemActionPerformed

    private void saveXPMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveXPMenuItemActionPerformed
        if (!checkConnection()) return;
        db.updateExperiment();
    }//GEN-LAST:event_saveXPMenuItemActionPerformed
    private String promptDir(String message, String def, boolean onlyDir) {
        if (message==null) message = "Choose Directory";
        File outputFile = Utils.chooseFile(message, def, FileChooser.FileChooserOption.FILE_OR_DIRECTORY, this);
        if (outputFile ==null) return null;
        if (onlyDir && !outputFile.isDirectory()) outputFile=outputFile.getParentFile();
        return outputFile.getAbsolutePath();
    }
    private void exportWholeXPMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportWholeXPMenuItemActionPerformed
        String defDir = PropertyUtils.get(PropertyUtils.LAST_IO_DATA_DIR);
        String dir = promptDir("Choose output directory", defDir, true);
        if (dir==null) return;
        List<String> xpToExport = getSelectedExperiments();
        closeExperiment();
        int count=0;
        for (String xp : xpToExport) {
            logger.info("Exporting whole XP : {}/{}", ++count, xpToExport.size());
            //CommandExecuter.dumpDB(getCurrentHostNameOrDir(), xp, dir, jsonFormatMenuItem.isSelected());
            MasterDAO mDAO = MasterDAOFactory.createDAO(xp, this.getHostNameOrDir(xp));
            ZipWriter w = new ZipWriter(dir+File.separator+mDAO.getDBName()+".zip");
            ImportExportJSON.exportConfig(w, mDAO);
            ImportExportJSON.exportSelections(w, mDAO);
            ImportExportJSON.exportPositions(w, mDAO, true, true, true, ProgressCallback.get(INSTANCE));
            w.close();
            mDAO.clearCache();
        }
        logger.info("export done!");
        PropertyUtils.set(PropertyUtils.LAST_IO_DATA_DIR, dir);
    }//GEN-LAST:event_exportWholeXPMenuItemActionPerformed

    private void exportSelectedFieldsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportSelectedFieldsMenuItemActionPerformed
        if (!checkConnection()) return;
        String defDir = PropertyUtils.get(PropertyUtils.LAST_IO_DATA_DIR);
        String dir = promptDir("Choose output directory", defDir, true);
        if (dir==null) return;
        List<String> sel = getSelectedPositions(false);
        if (sel.isEmpty()) return;
        ZipWriter w = new ZipWriter(dir+File.separator+db.getDBName()+".zip");
        ImportExportJSON.exportConfig(w, db);
        ImportExportJSON.exportSelections(w, db);
        ImportExportJSON.exportPositions(w, db, true, true, true, sel, ProgressCallback.get(INSTANCE, sel.size()));
        w.close();
        /*
        int[] sel  = getSelectedMicroscopyFields();
        String[] fNames = db.getExperiment().getPositionsAsString();
        String dbName = db.getDBName();
        String hostname = getCurrentHostNameOrDir();
        int count = 0;
        for (int f : sel) {
            String cName = MorphiumObjectDAO.getCollectionName(fNames[f]);
            CommandExecuter.dump(hostname, dbName, cName, dir, jsonFormatMenuItem.isSelected()); 
        }*/
        PropertyUtils.set(PropertyUtils.LAST_IO_DATA_DIR, dir);
    }//GEN-LAST:event_exportSelectedFieldsMenuItemActionPerformed

    private void exportXPConfigMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportXPConfigMenuItemActionPerformed
        if (!checkConnection()) return;
        String defDir = PropertyUtils.get(PropertyUtils.LAST_IO_DATA_DIR);
        File f = Utils.chooseFile("Write config to...", defDir, FileChooser.FileChooserOption.FILES_ONLY, this);
        if (f==null || !f.getParentFile().isDirectory()) return;
        promptSaveUnsavedChanges();
        //CommandExecuter.dump(getCurrentHostNameOrDir(), db.getDBName(), "Experiment", dir, jsonFormatMenuItem.isSelected());
        //ZipWriter w = new ZipWriter(dir+File.separator+db.getDBName()+".zip");
        //ImportExportJSON.exportConfig(w, db);
        //w.close();
        // export config as text file, without positions
        String save = f.getAbsolutePath();
        if (!save.endsWith(".json")||!save.endsWith(".txt")) save+=".json";
        Experiment dup = db.getExperiment().duplicate();
        dup.clearPositions();
        try {
            FileIO.write(new RandomAccessFile(save, "rw"), dup.toJSONEntry().toJSONString(), false);
        } catch (IOException ex) {
            GUI.log("Error while exporting config to: "+f.getAbsolutePath()+ ": "+ex.getLocalizedMessage());
        }
        PropertyUtils.set(PropertyUtils.LAST_IO_DATA_DIR, f.getParent());
    }//GEN-LAST:event_exportXPConfigMenuItemActionPerformed

    private void importPositionsToCurrentExperimentMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importPositionsToCurrentExperimentMenuItemActionPerformed
        if (!checkConnection()) return;
        String defDir = db.getDir();
        File f = Utils.chooseFile("Select exported archive", defDir, FileChooser.FileChooserOption.FILES_ONLY, this);
        if (f==null) return;
        
        DefaultWorker.WorkerTask t= new DefaultWorker.WorkerTask() {
            @Override
            public String run(int i) {
                GUI.getInstance().setRunning(true);
                ProgressCallback pcb = ProgressCallback.get(INSTANCE);
                pcb.log("Will import objects from file: "+f);
                boolean error = false;
                try {
                    ImportExportJSON.importFromZip(f.getAbsolutePath(), db, false, false, true, false, false, pcb);
                } catch (Exception e) {
                    logger.error("Error while importing", e);
                    log("error while importing");
                }
                GUI.getInstance().setRunning(false);
                GUI.getInstance().populateExperimentList();
                db.updateExperiment();
                updateConfigurationTree();
                populateActionPositionList();
                loadObjectTrees();
                ImageWindowManagerFactory.getImageManager().flush();
                if (!error) pcb.log("importing done!");
                return "";
            };
        };
        DefaultWorker.execute(t, 1);       
    }//GEN-LAST:event_importPositionsToCurrentExperimentMenuItemActionPerformed

    private void importConfigurationForSelectedPositionsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importConfigurationForSelectedPositionsMenuItemActionPerformed
        if (!checkConnection()) return;
        String defDir = PropertyUtils.get(PropertyUtils.LAST_IO_DATA_DIR);
        File f = Utils.chooseFile("Choose config file", defDir, FileChooser.FileChooserOption.FILES_ONLY, this);
        if (f==null || !f.isFile()) return;
        Experiment xp = ImportExportJSON.readConfig(f);
        if (xp==null) return;
        if (!Utils.promptBoolean("This will erase configutation on current xp. Continue?", this)) return;
        for (String p : getSelectedPositions(true)) {
            Position m = xp.getPosition(p);
            if (m!=null) {
                db.getExperiment().getPosition(p).setPreProcessingChains(m.getPreProcessingChain());
            }
        }
        db.updateExperiment();
        updateConfigurationTree();
    }//GEN-LAST:event_importConfigurationForSelectedPositionsMenuItemActionPerformed

    private void importConfigurationForSelectedStructuresMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importConfigurationForSelectedStructuresMenuItemActionPerformed
        if (!checkConnection()) return;
        String defDir = PropertyUtils.get(PropertyUtils.LAST_IO_DATA_DIR);
        File f = Utils.chooseFile("Choose config file", defDir, FileChooser.FileChooserOption.FILES_ONLY, this);
        if (f==null || !f.isFile()) return;
        Experiment xp = ImportExportJSON.readConfig(f);
        if (xp==null) return;
        if (xp.getStructureCount()!=db.getExperiment().getStructureCount()) logger.error("Selected config to import should have same object class number as current xp");
        if (!Utils.promptBoolean("This will erase configutation on current xp. Continue?", this)) return;
        for (int s : getSelectedStructures(true)) {
            db.getExperiment().getStructure(s).setContentFrom(xp.getStructure(s));
        }
        db.updateExperiment();
        updateConfigurationTree();
    }//GEN-LAST:event_importConfigurationForSelectedStructuresMenuItemActionPerformed

    private void importNewExperimentMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importNewExperimentMenuItemActionPerformed
        String defDir = PropertyUtils.get(PropertyUtils.LAST_IO_DATA_DIR);
        String dir = promptDir("Select folder containing experiment or experiments", defDir, false);
        if (dir==null) return;
        File directory = new File(dir);
        List<String> dbNames = getDBNames();
        Map<String, File> allXps = ExperimentSearchUtils.listExperiments(directory.getAbsolutePath(), false, null);
        //Map<String, File> allXps = ImportExportJSON.listExperiments(directory.getAbsolutePath());
        if (allXps.size()==1) {
            String name = JOptionPane.showInputDialog("New XP name:");
            if (name==null) return;
            name = ExperimentSearchUtils.addPrefix(name, currentDBPrefix);
            if (!Utils.isValid(name, false)) {
                logger.error("Name should not contain special characters");
                return;
            } else {
                File f = allXps.values().iterator().next();
                allXps.clear();
                allXps.put(name, f);
            }
        }
        Set<String> xpNotPresent = new HashSet<>(allXps.keySet());
        xpNotPresent.removeAll(dbNames);
        Set<String> xpsToImport = allXps.keySet();
        if (xpNotPresent.size()!=allXps.size()) {
            List<String> xpPresent = new ArrayList<>(allXps.keySet());
            xpPresent.retainAll(dbNames);
            Object[] options = {"Overwrite existig experiments (data loss)", "Ignore existing experiments"};
            int n = JOptionPane.showOptionDialog(this, "Some experiments found in the directory are already present: "+Utils.toStringList(xpPresent), "Import Whole Experiment", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (n==1) xpsToImport = xpNotPresent;
        }
        closeExperiment();
        List<String> xpList = new ArrayList<>(xpsToImport);
        /*DefaultWorker.WorkerTask t= new DefaultWorker.WorkerTask() {
            @Override
            public String run(int i) {
                GUI.getInstance().setRunning(true);
                ProgressCallback pcb = ProgressCallback.get(instance);
                String xp =xpList.get(i);
                File zip = allXps.get(xp);
                MasterDAO mDAO = MasterDAOFactory.createDAO(xp, getHostNameOrDir(xp));
                mDAO.deleteAllObjects();
                ImportExportJSON.importFromFile(zip.getAbsolutePath(), mDAO, true, false, false, false, false, ProgressCallback.get(instance));
                pcb.log("Will import data from file: "+f);
                boolean error = false;
                try {
                    ImportExportJSON.importFromZip(f.getAbsolutePath(), db, importConfigMenuItem.isSelected(), importSelectionsMenuItem.isSelected(), importObjectsMenuItem.isSelected(), importPPImagesMenuItem.isSelected(), importTrackImagesMenuItem.isSelected(), pcb);
                } catch (Exception e) {
                    logger.error("Error while importing", e);
                    log("error while importing");
                }
                GUI.getInstance().setRunning(false);
                GUI.getInstance().populateExperimentList();
                db.updateExperiment();
                populateActionMicroscopyFieldList();
                loadObjectTrees();
                ImageWindowManagerFactory.getImageManager().flush();
                if (!error) pcb.log("importing done!");
                //PropertyUtils.set(PropertyUtils.LAST_IO_DATA_DIR, f.getAbsolutePath());
                return "";
            };
        };
        DefaultWorker.execute(t, 1);
        */
        populateExperimentList();
        PropertyUtils.set(PropertyUtils.LAST_IO_DATA_DIR, dir);
    }//GEN-LAST:event_importNewExperimentMenuItemActionPerformed

    private void importConfigurationMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importConfigurationMenuItemActionPerformed
        /*String defDir = PropertyUtils.get(PropertyUtils.LAST_IO_DATA_DIR);
        File outputFile = Utils.chooseFile("Select Experiment.bson of Experiment.json file (WARNING: current configuration will be lost)", defDir, FileChooser.FileChooserOption.FILE_OR_DIRECTORY, this);
        if (outputFile!=null && outputFile.getName().equals("Experiment.bson") || outputFile.getName().equals("Experiment.json")) {
            CommandExecuter.restore(getCurrentHostNameOrDir(), db.getDBName(), "Experiment", outputFile.getAbsolutePath(), true);
            String dbName = db.getDBName();
            unsetXP();
            setDBConnection(dbName, getCurrentHostNameOrDir());
            PropertyUtils.set(PropertyUtils.LAST_IO_DATA_DIR, outputFile.getAbsolutePath());
        }*/
        String defDir = PropertyUtils.get(PropertyUtils.LAST_IO_CONFIG_DIR, IJ.getDir("plugins")+File.separator+"BOA");
        File f = Utils.chooseFile("Select configuration file or exported zip containing configuration file", defDir, FileChooser.FileChooserOption.FILES_ONLY, this);
        if (f==null) return;
        if (!Utils.promptBoolean("This will erase configutation on "+(db==null ? "all selected" : "current ")+" xp", this)) return;
        if (db!=null) {
            PreProcessingChain oldppTemplate = db.getExperiment().getPreProcessingTemplate().duplicate();
            ImportExportJSON.importConfigurationFromFile(f.getAbsolutePath(), db, true, true);
            if (db.getExperiment().getPositionCount()>0 && !db.getExperiment().getPreProcessingTemplate().sameContent(oldppTemplate)) {
                if (Utils.promptBoolean("Also copy pre-processing chain to all positions?", this)) {
                    for (Position p : db.getExperiment().getPositions()) p.getPreProcessingChain().setContentFrom(db.getExperiment().getPreProcessingTemplate());
                }
            }
            db.updateExperiment();
            updateConfigurationTree();
        } else {
            boolean overwritePos= Utils.promptBoolean("Also copy pre-processing chain to all positions?", this);
            for (String xp : this.getSelectedExperiments()) {
                MasterDAO mDAO = new Task(xp).getDB();
                if (mDAO==null) {
                    this.setMessage("Could not open experiment: "+xp);
                    continue;
                }
                PreProcessingChain oldppTemplate = mDAO.getExperiment().getPreProcessingTemplate().duplicate();
                ImportExportJSON.importConfigurationFromFile(f.getAbsolutePath(), mDAO, true, true);
                if (mDAO.getExperiment().getPositionCount()>0 && !mDAO.getExperiment().getPreProcessingTemplate().sameContent(oldppTemplate)) {
                    if (overwritePos) {
                        for (Position p : mDAO.getExperiment().getPositions()) p.getPreProcessingChain().setContentFrom(mDAO.getExperiment().getPreProcessingTemplate());
                    }
                }
                mDAO.updateExperiment();
                mDAO.clearCache();
            }
        }
        PropertyUtils.set(PropertyUtils.LAST_IO_CONFIG_DIR, f.getAbsolutePath());
    }//GEN-LAST:event_importConfigurationMenuItemActionPerformed
    
    private Task getCurrentTask(String dbName) {
        
        boolean preProcess=false;
        boolean segmentAndTrack = false;
        boolean trackOnly = false;
        boolean runMeasurements=false;
        boolean generateTrackImages = false;
        boolean extract = false;
        boolean export=false;
        for (int i : this.runActionList.getSelectedIndices()) {
            if (i==0) preProcess=true;
            if (i==1) segmentAndTrack=true;
            if (i==2) trackOnly = !segmentAndTrack;
            if (i==3) generateTrackImages=true;
            if (i==4) runMeasurements=true;
            if (i==5) extract=true;
            if (i==6) export=true;
        }
        Task t;
        if (dbName==null && db!=null) {
            int[] microscopyFields = this.getSelectedPositionIdx();
            int[] selectedStructures = this.getSelectedStructures(true);
            t = new Task(db);
            t.setStructures(selectedStructures).setPositions(microscopyFields);
            if (extract) for (int sIdx : selectedStructures) t.addExtractMeasurementDir(db.getDir(), sIdx);
        } else if (dbName!=null) {
            t = new Task(dbName);
            if (extract && t.getDB()!=null) {
                int[] selectedStructures = ArrayUtil.generateIntegerArray(t.getDB().getExperiment().getStructureCount());
                for (int sIdx : selectedStructures) t.addExtractMeasurementDir(t.getDB().getDir(), sIdx);
            }
            t.getDB().clearCache(); 
        } else return null;
        t.setActions(preProcess, segmentAndTrack, segmentAndTrack || trackOnly, runMeasurements).setGenerateTrackImages(generateTrackImages);
        if (export) t.setExportData(this.exportPPImagesMenuItem.isSelected(), this.exportTrackImagesMenuItem.isSelected(), this.exportObjectsMenuItem.isSelected(), this.exportConfigMenuItem.isSelected(), this.exportSelectionsMenuItem.isSelected());
        
        return t;
    }
    private void runSelectedActionsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runSelectedActionsMenuItemActionPerformed
        if (!checkConnection()) return;
        logger.debug("will run ... unsaved changes in config: {}", db==null? false : db.experimentChangedFromFile());
        promptSaveUnsavedChanges();
        
        Task t = getCurrentTask(null);
        if (t==null) {
            log("Could not define task");
            return;
        }
        if (!t.isValid()) {
            log("invalid task");
            return;
        }
        if (t.isPreProcess() || t.isSegmentAndTrack()) this.reloadObjectTrees=true; //|| t.reRunPreProcess
        
        Task.executeTask(t, getUserInterface(), ()->{updateConfigurationTree();}); // update config because cache will be cleared
    }//GEN-LAST:event_runSelectedActionsMenuItemActionPerformed

    private void importImagesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importImagesMenuItemActionPerformed
        if (!checkConnection()) return;
        String defDir = db.getDir();
        if (!new File(defDir).exists()) defDir = PropertyUtils.get(PropertyUtils.LAST_IMPORT_IMAGE_DIR);
        File[] selectedFiles = Utils.chooseFiles("Choose images/directories to import (selected import method="+db.getExperiment().getImportImageMethod()+")", defDir, FileChooser.FileChooserOption.FILES_AND_DIRECTORIES, this);
        if (selectedFiles!=null) {
            if (Experiment.IMPORT_METHOD.SINGLE_FILE.equals(db.getExperiment().getImportImageMethod())) { // warning if a lot of files 
                for (File f : selectedFiles) {
                    File[] sub = f.listFiles();
                    if (sub!=null && sub.length>200) {
                        if (!Utils.promptBoolean("Selected import method is Single-file and there are "+sub.length+" file in one selected folder. This will create as many position as images. Are you sure to proceed ?", this)) return;
                    }
                }
            }
            Processor.importFiles(this.db.getExperiment(), true, ProgressCallback.get(this), Utils.convertFilesToString(selectedFiles));
            File dir = Utils.getOneDir(selectedFiles);
            if (dir!=null) PropertyUtils.set(PropertyUtils.LAST_IMPORT_IMAGE_DIR, dir.getAbsolutePath());
            db.updateExperiment(); //stores imported fields
            populateActionPositionList();
            updateConfigurationTree();
            // also lock all new positions
            db.lockPositions();
        }
    }//GEN-LAST:event_importImagesMenuItemActionPerformed

    private void extractMeasurementMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_extractMeasurementMenuItemActionPerformed
        if (!checkConnection()) return;
        int[] selectedStructures = this.getSelectedStructures(false);
        String defDir = PropertyUtils.get(PropertyUtils.LAST_EXTRACT_MEASUREMENTS_DIR+"_"+db.getDBName(), new File(db.getExperiment().getOutputDirectory()).getParent());
        File outputDir = Utils.chooseFile("Choose directory", defDir, FileChooser.FileChooserOption.DIRECTORIES_ONLY, this);
        if (outputDir!=null) {
            if (selectedStructures.length==0) {
                selectedStructures = this.getSelectedStructures(true);
                for (int i : selectedStructures) extractMeasurements(outputDir.getAbsolutePath(), i);
            } else extractMeasurements(outputDir.getAbsolutePath(), selectedStructures);
            if (outputDir!=null) {
                PropertyUtils.set(PropertyUtils.LAST_EXTRACT_MEASUREMENTS_DIR+"_"+db.getDBName(), outputDir.getAbsolutePath());
                PropertyUtils.set(PropertyUtils.LAST_EXTRACT_MEASUREMENTS_DIR, outputDir.getAbsolutePath());
            }
        }
    }//GEN-LAST:event_extractMeasurementMenuItemActionPerformed
    private void extractMeasurements(String dir, int... structureIdx) {
        String file = dir+File.separator+db.getDBName()+Utils.toStringArray(structureIdx, "_", "", "_")+".csv";
        logger.info("measurements will be extracted to: {}", file);
        Map<Integer, String[]> keys = db.getExperiment().getAllMeasurementNamesByStructureIdx(MeasurementKeyObject.class, structureIdx);
        MeasurementExtractor.extractMeasurementObjects(db, file, getSelectedPositions(true), keys);
    }
    private void deleteMeasurementsCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteMeasurementsCheckBoxActionPerformed
        PropertyUtils.set(PropertyUtils.DELETE_MEASUREMENTS, this.deleteMeasurementsCheckBox.isSelected());
    }//GEN-LAST:event_deleteMeasurementsCheckBoxActionPerformed

    private void runActionAllXPMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runActionAllXPMenuItemActionPerformed
        List<String> xps = this.getSelectedExperiments();
        if (xps.isEmpty()) return;
        closeExperiment();
        List<Task> tasks = new ArrayList<>(xps.size());
        for (String xp : xps) tasks.add(getCurrentTask(xp));
        Task.executeTasks(tasks, getUserInterface());
    }//GEN-LAST:event_runActionAllXPMenuItemActionPerformed

    private void closeAllWindowsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeAllWindowsMenuItemActionPerformed
        ImageWindowManagerFactory.getImageManager().flush();
    }//GEN-LAST:event_closeAllWindowsMenuItemActionPerformed

    private void pruneTrackActionPerformed(java.awt.event.ActionEvent evt) {
        if (!checkConnection()) return;
        if (db.isConfigurationReadOnly()) return;
        List<StructureObject> sel = ImageWindowManagerFactory.getImageManager().getSelectedLabileObjects(null);
        ManualEdition.prune(db, sel, ManualEdition.ALWAYS_MERGE, true);
        logger.debug("prune: {}", Utils.toStringList(sel));
    }
    
    private void compactLocalDBMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_compactLocalDBMenuItemActionPerformed
        if (this.localFileSystemDatabaseRadioButton.isSelected()) {
            closeExperiment();
            for (String xp : getSelectedExperiments()) {
                DBMapMasterDAO dao = (DBMapMasterDAO)MasterDAOFactory.createDAO(xp, this.getHostNameOrDir(xp));
                dao.lockPositions();
                GUI.log("Compacting Experiment: "+xp);
                dao.compact();
                dao.clearCache();
                dao.unlockPositions();
            }
        }
    }//GEN-LAST:event_compactLocalDBMenuItemActionPerformed

    private void clearMemoryMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearMemoryMenuItemActionPerformed
        if (!checkConnection()) return;
        ExperimentSearchUtils.clearMemory(db);
    }//GEN-LAST:event_clearMemoryMenuItemActionPerformed

    private void extractSelectionMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_extractSelectionMenuItemActionPerformed
        if (!checkConnection()) return;
        String defDir = PropertyUtils.get(PropertyUtils.LAST_EXTRACT_MEASUREMENTS_DIR+"_"+db.getDBName(), new File(db.getExperiment().getOutputDirectory()).getParent());
        File outputDir = Utils.chooseFile("Choose directory", defDir, FileChooser.FileChooserOption.DIRECTORIES_ONLY, this);
        if (outputDir!=null) {
            String file = outputDir.getAbsolutePath()+File.separator+db.getDBName()+"_Selections.csv";
            SelectionExtractor.extractSelections(db, getSelectedSelections(true), file);
            PropertyUtils.set(PropertyUtils.LAST_EXTRACT_MEASUREMENTS_DIR+"_"+db.getDBName(), outputDir.getAbsolutePath());
            PropertyUtils.set(PropertyUtils.LAST_EXTRACT_MEASUREMENTS_DIR, outputDir.getAbsolutePath());
        }
    }//GEN-LAST:event_extractSelectionMenuItemActionPerformed
    
    private void clearTrackImagesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearTrackImagesMenuItemActionPerformed
        if (!checkConnection()) return;
        if (!Utils.promptBoolean("Delete All Track Images ? (Irreversible)", this)) return;
        ImageDAO iDAO = db.getExperiment().getImageDAO();
        for (String p : getSelectedPositions(true)) {
            for (int sIdx = 0; sIdx<db.getExperiment().getStructureCount(); ++sIdx) iDAO.deleteTrackImages(p, sIdx);
        }
    }//GEN-LAST:event_clearTrackImagesMenuItemActionPerformed

    private void clearPPImageMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearPPImageMenuItemActionPerformed
        if (!checkConnection()) return;
        if (!Utils.promptBoolean("Delete All Pre-processed Images ? (Irreversible)", this)) return;
        for (String p : getSelectedPositions(true)) {
            Position f = db.getExperiment().getPosition(p);
            if (f.getInputImages()!=null) f.getInputImages().deleteFromDAO();
        }
    }//GEN-LAST:event_clearPPImageMenuItemActionPerformed
    private void setLogFile(String path) {
        this.logFile=path;
        if (path==null) this.setLogFileMenuItem.setText("Set Log File");
        else this.setLogFileMenuItem.setText("Set Log File (current: "+path+")");
        if (path!=null && !this.appendToFileMenuItem.isSelected() && new File(path).exists()) new File(path).delete();
    }
    private void setLogFileMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setLogFileMenuItemActionPerformed
        File f = Utils.chooseFile("Save Log As...", experimentFolder.getText(), FileChooser.FileChooserOption.FILES_AND_DIRECTORIES, this);
        if (f==null) {
            PropertyUtils.set(PropertyUtils.LOG_FILE, null);
            setLogFile(null);
        } else {
            if (f.isDirectory()) f = new File(f.getAbsolutePath()+File.separator+"Log.txt");
            setLogFile(f.getAbsolutePath());
            PropertyUtils.set(PropertyUtils.LOG_FILE, f.getAbsolutePath());
        }
    }//GEN-LAST:event_setLogFileMenuItemActionPerformed

    private void activateLoggingMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_activateLoggingMenuItemActionPerformed
        PropertyUtils.set(PropertyUtils.LOG_ACTIVATED, this.activateLoggingMenuItem.isSelected());
    }//GEN-LAST:event_activateLoggingMenuItemActionPerformed

    private void appendToFileMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_appendToFileMenuItemActionPerformed
        PropertyUtils.set(PropertyUtils.LOG_APPEND, this.activateLoggingMenuItem.isSelected());
    }//GEN-LAST:event_appendToFileMenuItemActionPerformed

    private void CloseNonInteractiveWindowsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_CloseNonInteractiveWindowsMenuItemActionPerformed
        ImageWindowManagerFactory.getImageManager().closeNonInteractiveWindows();
    }//GEN-LAST:event_CloseNonInteractiveWindowsMenuItemActionPerformed

    private void exportXPObjectsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportXPObjectsMenuItemActionPerformed
        exportSelectedExperiments(true, true, true, false, false, false);
    }//GEN-LAST:event_exportXPObjectsMenuItemActionPerformed
    private void exportSelectedExperiments(boolean config, boolean objects, boolean selections, boolean preProcessedImages, boolean trackImages, boolean eraseXP) {
        if (config) this.promptSaveUnsavedChanges();
        final List<String> xps = getSelectedExperiments();
        final List<String> positions = new ArrayList<>();
        if (xps.size()<=1) {
            if (db!=null && (xps.size()==1 && xps.get(0).equals(this.db.getDBName())) || xps.isEmpty()) positions.addAll(getSelectedPositions(true));
            if (xps.isEmpty() && db!=null && db.getExperiment()!=null) xps.add(db.getDBName());
        } else closeExperiment();
        
        log("dumping: "+xps.size()+ " Experiment"+(xps.size()>1?"s":""));
        DefaultWorker.WorkerTask t= new DefaultWorker.WorkerTask() {
            @Override
            public String run(int i) {
                if (i==0) GUI.getInstance().setRunning(true);
                String xp = xps.get(i);
                log("exporting: "+xp+ " config:"+config+" selections: "+selections+ " objects: "+objects+ " pp images: "+preProcessedImages+ " trackImages: "+trackImages);
                MasterDAO mDAO = positions.isEmpty() ? new Task(xp).getDB() : db;
                logger.debug("dao ok");
                String file = mDAO.getDir()+File.separator+mDAO.getDBName()+"_dump.zip";
                boolean error = false;
                try {
                    ZipWriter w = new ZipWriter(file);
                    if (objects || preProcessedImages || trackImages) {
                        if (positions.isEmpty()) ImportExportJSON.exportPositions(w, mDAO, objects, preProcessedImages, trackImages ,  ProgressCallback.get(INSTANCE, mDAO.getExperiment().getPositionCount()));
                        else ImportExportJSON.exportPositions(w, mDAO, objects, preProcessedImages, trackImages , positions, ProgressCallback.get(INSTANCE, positions.size()));
                    }
                    if (config) ImportExportJSON.exportConfig(w, mDAO);
                    if (selections) ImportExportJSON.exportSelections(w, mDAO);
                    w.close();
                } catch (Exception e) {
                    logger.error("Error while dumping");
                    error = true;
                }
                if (error) new File(file).delete();
                if (!error && eraseXP) { // eraseAll config & objects
                    MasterDAO.deleteObjectsAndSelectionAndXP(mDAO);
                    logger.debug("delete ok");
                } 
                mDAO.clearCache();
                if (i==xps.size()-1) {
                    GUI.getInstance().setRunning(false);
                    GUI.getInstance().populateExperimentList();
                    log("exporting done!");
                }
                return error ? xp+" NOT DUMPED : error": xp+" dumped!";
            };
        };
        DefaultWorker.execute(t, xps.size());
    }
    private void unDumpObjectsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_unDumpObjectsMenuItemActionPerformed
        closeExperiment();
        final List<File> dumpedFiles = Utils.seachAll(experimentFolder.getText(), s->s.endsWith("_dump.zip"), 1);
        if (dumpedFiles==null) return;
        // remove xp already undumped
        Map<String, File> dbFiles = ExperimentSearchUtils.listExperiments(experimentFolder.getText(), true, ProgressCallback.get(this));
        dumpedFiles.removeIf(f->dbFiles.containsValue(f.getParentFile()));
        log("undumping: "+dumpedFiles.size()+ " Experiment"+(dumpedFiles.size()>1?"s":""));
        
        DefaultWorker.WorkerTask t= new DefaultWorker.WorkerTask() {
            @Override
            public String run(int i) {
                if (i==0) GUI.getInstance().setRunning(true);
                File dump = dumpedFiles.get(i);
                String dbName = dump.getName().replace("_dump.zip", "");
                log("undumpig: "+dbName);
                logger.debug("dumped file: {}, parent: {}", dump.getAbsolutePath(), dump.getParent());
                MasterDAO dao = new Task(dbName, dump.getParent()).getDB();
                ImportExportJSON.importFromZip(dump.getAbsolutePath(), dao, true, true, true, false, false, ProgressCallback.get(INSTANCE));
                if (i==dumpedFiles.size()-1) {
                    GUI.getInstance().setRunning(false);
                    GUI.getInstance().populateExperimentList();
                    log("undumping done!");
                }
                return dbName+" undumped!";
            };
        };
        DefaultWorker.execute(t, dumpedFiles.size());
    }//GEN-LAST:event_unDumpObjectsMenuItemActionPerformed

    private void localFileSystemDatabaseRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_localFileSystemDatabaseRadioButtonActionPerformed
        closeExperiment();
        MasterDAOFactory.setCurrentType(MasterDAOFactory.DAOType.DBMap);
        PropertyUtils.set(PropertyUtils.DATABASE_TYPE, MasterDAOFactory.DAOType.DBMap.toString());
        experimentFolder.setText(PropertyUtils.get(PropertyUtils.LOCAL_DATA_PATH, ""));
        localDBMenu.setEnabled(true);
        populateExperimentList();
    }//GEN-LAST:event_localFileSystemDatabaseRadioButtonActionPerformed

    private void compareToDumpFileMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_compareToDumpFileMenuItemActionPerformed
        final List<File> dumpedFiles = Utils.seachAll(experimentFolder.getText(), s->s.endsWith("_dump.zip"), 1);
        if (dumpedFiles==null) return;
        closeExperiment();
        // remove xp already undumped
        Map<String, File> dbFiles = ExperimentSearchUtils.listExperiments(experimentFolder.getText(), true, ProgressCallback.get(this));
        dumpedFiles.removeIf(f->!dbFiles.containsValue(f.getParentFile()));
        // remove unselected xp if any
        Set<String> xpSel = new HashSet<>(getSelectedExperiments());
        if (!xpSel.isEmpty()) dumpedFiles.removeIf(f->!xpSel.contains(Utils.getOneKey(dbFiles, f.getParentFile())));

        log("found: "+dumpedFiles.size()+ " undumped Experimentse");
        DefaultWorker.WorkerTask t= new DefaultWorker.WorkerTask() {
            @Override
            public String run(int i) {
                if (i==0) GUI.getInstance().setRunning(true);
                File dump = dumpedFiles.get(i);
                String dbName = dump.getName().replace("_dump.zip", "");
                log("comparing dump of: "+dbName);
                logger.debug("dumped file: {}, parent: {}", dump.getAbsolutePath(), dump.getParent());
                MasterDAO dao = new Task(dbName, dump.getParent()).getDB();
                dao.setConfigurationReadOnly(true);
                MasterDAO daoDump = new BasicMasterDAO();
                ImportExportJSON.importFromZip(dump.getAbsolutePath(), daoDump, true, true, true, false, false, ProgressCallback.get(INSTANCE));
                try {
                    MasterDAO.compareDAOContent(dao, daoDump, true, true, true, ProgressCallback.get(INSTANCE));
                } catch (Exception|Error e) {
                    throw e;
                }
                
                if (i==dumpedFiles.size()-1) {
                    GUI.getInstance().setRunning(false);
                    GUI.getInstance().populateExperimentList();
                    log("dump comparison done!");
                }
                return dbName+" compared!";
            };
        };
        DefaultWorker.execute(t, dumpedFiles.size());
    }//GEN-LAST:event_compareToDumpFileMenuItemActionPerformed

    private void importObjectsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importObjectsMenuItemActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_importObjectsMenuItemActionPerformed

    private void exportDataMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportDataMenuItemActionPerformed
        exportSelectedExperiments(exportConfigMenuItem.isSelected(), exportObjectsMenuItem.isSelected(), exportSelectionsMenuItem.isSelected(), exportPPImagesMenuItem.isSelected(), exportTrackImagesMenuItem.isSelected(), false);
    }//GEN-LAST:event_exportDataMenuItemActionPerformed

    private void importDataMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importDataMenuItemActionPerformed
        if (!checkConnection()) return;
        String defDir = db.getDir(); 
        File f = Utils.chooseFile("Select exported archive", defDir, FileChooser.FileChooserOption.FILES_ONLY, this);
        if (f==null) return;
        
        DefaultWorker.WorkerTask t= new DefaultWorker.WorkerTask() {
            @Override
            public String run(int i) {
                GUI.getInstance().setRunning(true);
                ProgressCallback pcb = ProgressCallback.get(INSTANCE);
                pcb.log("Will import data from file: "+f);
                boolean error = false;
                try {
                    ImportExportJSON.importFromZip(f.getAbsolutePath(), db, importConfigMenuItem.isSelected(), importSelectionsMenuItem.isSelected(), importObjectsMenuItem.isSelected(), importPPImagesMenuItem.isSelected(), importTrackImagesMenuItem.isSelected(), pcb);
                } catch (Exception e) {
                    logger.error("Error while importing", e);
                    log("error while importing");
                }
                GUI.getInstance().setRunning(false);
                GUI.getInstance().populateExperimentList();
                db.updateExperiment();
                updateConfigurationTree();
                populateActionPositionList();
                loadObjectTrees();
                ImageWindowManagerFactory.getImageManager().flush();
                if (!error) pcb.log("importing done!");
                //PropertyUtils.set(PropertyUtils.LAST_IO_DATA_DIR, f.getAbsolutePath());
                return "";
            };
        };
        DefaultWorker.execute(t, 1);   
    }//GEN-LAST:event_importDataMenuItemActionPerformed

    private void actionPoolListMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_actionPoolListMousePressed
        if (this.running) return;
        if (SwingUtilities.isRightMouseButton(evt)) {
            JPopupMenu menu = new JPopupMenu();
            List<String> sel = actionPoolList.getSelectedValuesList();
            Action addCurrentTask = new AbstractAction("Add Current Task to Task List") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    Task t = getCurrentTask(null);
                    if (t!=null) actionPoolListModel.addElement(t.toJSON().toJSONString());
                }
            };
            menu.add(addCurrentTask);
            addCurrentTask.setEnabled(db!=null);
            Action deleteSelected = new AbstractAction("Delete Selected Tasks") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    for (String s : sel) actionPoolListModel.removeElement(s);
                }
            };
            deleteSelected.setEnabled(!sel.isEmpty());
            menu.add(deleteSelected);
            Action up = new AbstractAction("Move Up") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int[] newIndices = new int[sel.size()];
                    int idx = 0;
                    for (String s : sel) {
                        int i = actionPoolListModel.indexOf(s);
                        if (i>0) {
                            actionPoolListModel.removeElement(s);
                            actionPoolListModel.add(i-1, s);
                            newIndices[idx++] = i-1;
                        } else newIndices[idx++] = i;
                    }
                    actionPoolList.setSelectedIndices(newIndices);
                }
            };
            up.setEnabled(!sel.isEmpty() && sel.size()<actionPoolListModel.size());
            menu.add(up);
            Action down = new AbstractAction("Move Down") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int[] newIndices = new int[sel.size()];
                    int idx = 0;
                    for (String s : sel) {
                        int i = actionPoolListModel.indexOf(s);
                        if (i>=0 && i<actionPoolListModel.size()-1) {
                            actionPoolListModel.removeElement(s);
                            actionPoolListModel.add(i+1, s);
                            newIndices[idx++] = i+1;
                        } else newIndices[idx++] = i;
                    }
                    actionPoolList.setSelectedIndices(newIndices);
                }
            };
            down.setEnabled(!sel.isEmpty() && sel.size()<actionPoolListModel.size());
            menu.add(down);
            Action clearAll = new AbstractAction("Clear All") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    actionPoolListModel.clear();
                }
            };
            menu.add(clearAll);
            clearAll.setEnabled(!actionPoolListModel.isEmpty());
            Action save = new AbstractAction("Save to File") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    File out = Utils.chooseFile("Save Task list as...", experimentFolder.getText(), FileChooser.FileChooserOption.FILES_AND_DIRECTORIES, tabs);
                    if (out==null || out.isDirectory()) return;
                    String outS = out.getAbsolutePath();
                    if (!outS.endsWith(".txt")&&!outS.endsWith(".json")) outS+=".json";
                    FileIO.writeToFile(outS, Collections.list(actionPoolListModel.elements()), s->s);
                }
            };
            menu.add(save);
            save.setEnabled(!actionPoolListModel.isEmpty());
            Action saveProc = new AbstractAction("Split Processing Task per Position and Save to folder...") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    File out = Utils.chooseFile("Choose Folder", experimentFolder.getText(), FileChooser.FileChooserOption.FILES_AND_DIRECTORIES, tabs);
                    if (out==null || !out.isDirectory()) return;
                    String outDir = out.getAbsolutePath();
                    List<Task> tasks = Collections.list(actionPoolListModel.elements()).stream().map(s->JSONUtils.parse(s)).map(j->new Task().fromJSON(j)).collect(Collectors.toList());
                    Task.getProcessingTasksByPosition(tasks).entrySet().forEach(en -> {
                        String fileName = outDir + File.separator + en.getKey().dbName + "_P"+en.getKey().position+".json";
                        FileIO.writeToFile(fileName, en.getValue(), t->t.toJSON().toJSONString());
                    });
                    
                }
            };
            menu.add(saveProc);
            saveProc.setEnabled(!actionPoolListModel.isEmpty());
            
            Action load = new AbstractAction("Load from File") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String dir = experimentFolder.getText();
                    if (!new File(dir).isDirectory()) dir = null;
                    File f = Utils.chooseFile("Choose Task list file", dir, FileChooser.FileChooserOption.FILES_ONLY, tabs);
                    if (f!=null && f.exists()) {
                        List<String> jobs = FileIO.readFromFile(f.getAbsolutePath(), s->s);
                        for (String j : jobs) actionPoolListModel.addElement(j);
                    }
                }
            };
            menu.add(load);
            Action setXP = new AbstractAction("Set selected Experiment to selected Actions") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String xp = (String) experimentList.getSelectedValue();
                    String dir = experimentFolder.getText();
                    Map<Integer, String> indexSelJobMap = ((List<String>)actionPoolList.getSelectedValuesList()).stream().collect(Collectors.toMap(o->actionPoolListModel.indexOf(o), o->o));
                    for (Entry<Integer, String> en : indexSelJobMap.entrySet()) {

                        JSONObject o = JSONUtils.parse(en.getValue());
                        if (o==null) log("Error: could not parse task: "+en.getValue());
                        else {
                            Task t = new Task().fromJSON(o);
                            // look for dir in current directory
                            String d = ExperimentSearchUtils.searchLocalDirForDB(xp, dir);
                            if (d==null) log("Error: Could not find directory of XP: "+xp);
                            else {
                                t.setDBName(xp).setDir(d);
                                if (!t.isValid()) log("Error: could not set experiment to task: "+en.getValue());
                                else {
                                    actionPoolListModel.remove(en.getKey());
                                    actionPoolListModel.add(en.getKey(), t.toJSON().toJSONString());
                                }
                            }
                        }
                    }
                }
            };
            menu.add(setXP);
            setXP.setEnabled(experimentList.getSelectedValuesList().size()==1 && !sel.isEmpty());
            Action runAll = new AbstractAction("Run All Tasks") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    List<Task> jobs = new ArrayList<>();
                    List<String> jobsS = Collections.list(actionPoolListModel.elements());
                    for (String s : jobsS) {
                        JSONObject o = JSONUtils.parse(s);
                        if (o==null) log("Error: could not parse task: "+s);
                        else {
                            Task t = new Task().fromJSON(o);
                            jobs.add(t);
                        }
                    }
                    if (!jobs.isEmpty()) {
                        closeExperiment(); // avoid lock problems
                        Task.executeTasks(jobs, getUserInterface());
                    }
                }
            };
            menu.add(runAll);
            runAll.setEnabled(!actionPoolListModel.isEmpty());
            Action runSel = new AbstractAction("Run Selected Tasks") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    List<Task> jobs = new ArrayList<>();
                    List<String> jobsS = sel;
                    for (String s : jobsS) {
                        JSONObject o = JSONUtils.parse(s);
                        if (o==null) log("Error: could not parse task: "+s);
                        else {
                            Task t = new Task().fromJSON(o);
                            jobs.add(t);
                        }
                    }
                    if (!jobs.isEmpty()) {
                        closeExperiment(); // avoid lock problems
                        Task.executeTasks(jobs, getUserInterface());
                    }
                }
            };
            menu.add(runSel);
            runSel.setEnabled(!actionPoolListModel.isEmpty() && !sel.isEmpty());
            //Utils.chooseFile("Choose Directory to save Job List", DBprefix, FileChooser.FileChooserOption.FILES_ONLY, this)
            menu.show(this.actionPoolList, evt.getX(), evt.getY());
        }
    }//GEN-LAST:event_actionPoolListMousePressed
    private ProgressLogger getUserInterface() {
        if (activateLoggingMenuItem.isSelected()) {
            FileProgressLogger logUI = new FileProgressLogger(appendToFileMenuItem.isSelected());
            if (logFile!=null) logUI.setLogFile(logFile);
            return new MultiProgressLogger(this, logUI);
        } else return this;
    }
    private void experimentFolderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_experimentFolderActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_experimentFolderActionPerformed

    private void experimentFolderMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_experimentFolderMousePressed
        if (this.running) return;
        if (SwingUtilities.isRightMouseButton(evt) && localFileSystemDatabaseRadioButton.isSelected()) {
                    logger.debug("frame fore: {} , back: {}, hostName: {}", this.getForeground(), this.getBackground(), experimentFolder.getBackground());

            JPopupMenu menu = new JPopupMenu();
            Action chooseFile = new AbstractAction("Choose local data folder") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String path = PropertyUtils.get(PropertyUtils.LOCAL_DATA_PATH, null);
                    File f = Utils.chooseFile("Choose local data folder", path, FileChooser.FileChooserOption.DIRECTORIES_ONLY, experimentFolder);
                    if (f!=null) {
                        closeExperiment();
                        PropertyUtils.set(PropertyUtils.LOCAL_DATA_PATH, f.getAbsolutePath());
                        PropertyUtils.addFirstStringToList(PropertyUtils.LOCAL_DATA_PATH, f.getAbsolutePath());
                        experimentFolder.setText(f.getAbsolutePath());
                        localFileSystemDatabaseRadioButton.setSelected(true);
                        populateExperimentList();
                    }
                }
            };
            menu.add(chooseFile);
            JMenu recentFiles = new JMenu("Recent");
            menu.add(recentFiles);
            List<String> recent = PropertyUtils.getStrings(PropertyUtils.LOCAL_DATA_PATH);
            for (String s : recent) {
                Action setRecent = new AbstractAction(s) {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        File f = new File(s);
                        if (f.exists() && f.isDirectory()) {
                            closeExperiment();
                            experimentFolder.setText(s);
                            PropertyUtils.set(PropertyUtils.LOCAL_DATA_PATH, s);
                            localFileSystemDatabaseRadioButton.setSelected(true);
                            populateExperimentList();
                        }
                    }
                };
                recentFiles.add(setRecent);
            }
            if (recent.isEmpty()) recentFiles.setEnabled(false);
            Action delRecent = new AbstractAction("Delete recent list") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    PropertyUtils.setStrings(PropertyUtils.LOCAL_DATA_PATH, null);
                }
            };
            recentFiles.add(delRecent);
            menu.show(this.experimentFolder, evt.getX(), evt.getY());
        }
    }//GEN-LAST:event_experimentFolderMousePressed

    private void newXPFromTemplateMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newXPFromTemplateMenuItemActionPerformed
        String defDir = PropertyUtils.get(PropertyUtils.LAST_IO_CONFIG_DIR, IJ.getDir("plugins")+File.separator+"BOA");
        logger.debug("defDir: {}", defDir);
        String config = promptDir("Select configuration file (or zip containing config file)", defDir, false);
        if (config==null) return;
        if (!new File(config).isFile()) {
            log("Select config file");
            return;
        }
        if (!config.endsWith(".zip") && !config.endsWith(".json") && !config.endsWith(".txt")) {
            log("Config file should en in .zip, .json or .txt");
            return;
        }
        List<String> dbNames = getDBNames();
        Experiment xp = FileIO.readFisrtFromFile(config, s->JSONUtils.parse(Experiment.class, s));
        String name=null;
        if (xp!=null) {
            name = JOptionPane.showInputDialog("New XP name:", Utils.removeExtension(new File(config).getName()));
            if (name==null) return;
            name = ExperimentSearchUtils.addPrefix(name, currentDBPrefix);
            if (!Utils.isValid(name, false)) {
                log("Name should not contain special characters");
                return;
            } else if (dbNames.contains(name)) {
                log("XP already present");
                return;
            } 
        } else {
            log("No xp found in file");
            return;
        }
        MasterDAO mDAO = MasterDAOFactory.createDAO(name, this.getHostNameOrDir(name));
        mDAO.deleteAllObjects();
        ImportExportJSON.importFromFile(config, mDAO, true, false, false, false, false, ProgressCallback.get(INSTANCE));
        
        populateExperimentList();
        PropertyUtils.set(PropertyUtils.LAST_IO_CONFIG_DIR, config);
        openExperiment(name, null, false);
        if (this.db!=null) setSelectedExperiment(name);
    }//GEN-LAST:event_newXPFromTemplateMenuItemActionPerformed

    private void reloadSelectionsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadSelectionsButtonActionPerformed
        populateSelections();
    }//GEN-LAST:event_reloadSelectionsButtonActionPerformed

    private void createSelectionButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_createSelectionButtonActionPerformed
        if (!checkConnection()) return;
        String name = JOptionPane.showInputDialog("New Selection name:");
        if (!SelectionUtils.validSelectionName(db, name)) return;
        Selection sel = new Selection(name, db);
        if (this.db.getSelectionDAO()==null) {
            logger.error("No selection DAO. Output Directory set ? ");
            return;
        }
        this.db.getSelectionDAO().store(sel);
        populateSelections();
    }//GEN-LAST:event_createSelectionButtonActionPerformed

    private void microscopyFieldListMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_microscopyFieldListMousePressed
        if (!this.checkConnection()) return;
        if (SwingUtilities.isRightMouseButton(evt)) {
            List<String> positions = this.getSelectedPositions(false);
            if (positions.isEmpty()) return;
            JPopupMenu menu = new JPopupMenu();
            if (positions.size()==1) {
                String position = positions.get(0);
                Action openRaw = new AbstractAction("Open Input Images") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        db.getExperiment().flushImages(true, true, position);
                        IJVirtualStack.openVirtual(db.getExperiment(), position, false);
                    }
                };
                menu.add(openRaw);
                Action openPP = new AbstractAction("Open Pre-Processed Images") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        db.getExperiment().flushImages(true, true, position);
                        IJVirtualStack.openVirtual(db.getExperiment(), position, true);
                    }
                };
                openPP.setEnabled(db.getExperiment().getImageDAO().getPreProcessedImageProperties(position)!=null);
                menu.add(openPP);
                menu.add(new JSeparator());
            }
            Action delete = new AbstractAction("Delete") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (!Utils.promptBoolean("Delete "+(positions.size()>1?"all":"")+" selected position"+(positions.size()>1?"s":""), microscopyFieldList)) return;
                    for (String pos : positions) {
                        db.getExperiment().getPosition(pos).eraseData();
                        db.getExperiment().getPosition(pos).removeFromParent();
                    }
                    db.updateExperiment();
                    populateActionPositionList();
                    updateConfigurationTree();
                }
            };
            menu.add(delete);
            menu.show(this.microscopyFieldList, evt.getX(), evt.getY());
        }
    }//GEN-LAST:event_microscopyFieldListMousePressed

    private void experimentListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_experimentListValueChanged
        List<String> sel = getSelectedExperiments();
        if (this.db==null) {
            if (sel.size()==1) setSelectedExperimentMenuItem.setText("Open Experiment: "+sel.get(0));
            else setSelectedExperimentMenuItem.setText("--");
        } else {
            if (sel.size()==1 && !sel.get(0).equals(db.getDBName())) setSelectedExperimentMenuItem.setText("Open Experiment: "+sel.get(0));
            else setSelectedExperimentMenuItem.setText("Close Experiment: "+db.getDBName());
        }
    }//GEN-LAST:event_experimentListValueChanged

    private void interactiveStructureActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_interactiveStructureActionPerformed
        if (!checkConnection()) return;
        getImageManager().setInteractiveStructure(interactiveStructure.getSelectedIndex()-1);
    }//GEN-LAST:event_interactiveStructureActionPerformed

    private void pruneTrackButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pruneTrackButtonActionPerformed
        if (!checkConnection()) return;
        if (db.isConfigurationReadOnly()) return;
        pruneTrackActionPerformed(evt);
    }//GEN-LAST:event_pruneTrackButtonActionPerformed

    private void testSplitButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_testSplitButtonActionPerformed
        if (!checkConnection()) return;
        List<StructureObject> selList = ImageWindowManagerFactory.getImageManager().getSelectedLabileObjects(null);
        if (selList.isEmpty()) logger.warn("Select at least one object to Split first!");
        else ManualEdition.splitObjects(db, selList, false, true);
    }//GEN-LAST:event_testSplitButtonActionPerformed

    private void resetLinksButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetLinksButtonActionPerformed
        if (!checkConnection()) return;
        //if (db.isReadOnly()) return;
        List<StructureObject> sel = ImageWindowManagerFactory.getImageManager().getSelectedLabileObjects(null);
        if (sel.isEmpty()) {
            logger.warn("Select at least one object to modify its links");
            return;
        }
        ManualEdition.resetObjectLinks(db, sel, true);
    }//GEN-LAST:event_resetLinksButtonActionPerformed

    private void unlinkObjectsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_unlinkObjectsButtonActionPerformed
        if (!checkConnection()) return;
        //if (db.isReadOnly()) return;
        List<StructureObject> sel = ImageWindowManagerFactory.getImageManager().getSelectedLabileObjects(null);
        if (sel.isEmpty()) {
            logger.warn("Select at least one object to modify its links");
            return;
        }
        ManualEdition.modifyObjectLinks(db, sel, true, true);
    }//GEN-LAST:event_unlinkObjectsButtonActionPerformed

    private void linkObjectsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_linkObjectsButtonActionPerformed
        if (!checkConnection()) return;
        //if (db.isReadOnly()) return;
        List<StructureObject> sel = ImageWindowManagerFactory.getImageManager().getSelectedLabileObjects(null);
        if (sel.isEmpty()) {
            logger.warn("Select at least one object to modify its links");
            return;
        }
        ManualEdition.modifyObjectLinks(db, sel, false, true);
    }//GEN-LAST:event_linkObjectsButtonActionPerformed

    private void testManualSegmentationButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_testManualSegmentationButtonActionPerformed
        ManualEdition.manualSegmentation(db, null, true);
    }//GEN-LAST:event_testManualSegmentationButtonActionPerformed

    private void manualSegmentButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_manualSegmentButtonActionPerformed
        if (!checkConnection()) return;
        //if (db.isReadOnly()) return;
        ManualEdition.manualSegmentation(db, null, false);
    }//GEN-LAST:event_manualSegmentButtonActionPerformed

    private void updateRoiDisplayButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_updateRoiDisplayButtonActionPerformed
        GUI.updateRoiDisplay(null);
    }//GEN-LAST:event_updateRoiDisplayButtonActionPerformed

    private void deleteObjectsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteObjectsButtonActionPerformed
        if (!checkConnection()) return;
        logger.info("delete: evt source {}, evt: {}, ac: {}, param: {}", evt.getSource(), evt, evt.getActionCommand(), evt.paramString());
        //if (db.isReadOnly()) return;
        List<StructureObject> sel = ImageWindowManagerFactory.getImageManager().getSelectedLabileObjects(null);
        if (sel.size()<=10 || Utils.promptBoolean("Delete "+sel.size()+ " Objects ? ", null)) ManualEdition.deleteObjects(db, sel, ManualEdition.ALWAYS_MERGE, true);
    }//GEN-LAST:event_deleteObjectsButtonActionPerformed

    private void deleteObjectsButtonMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_deleteObjectsButtonMousePressed
        if (!checkConnection()) return;
        //if (db.isReadOnly()) return;
        if (SwingUtilities.isRightMouseButton(evt)) {
            JPopupMenu menu = new JPopupMenu();
            Action prune = new AbstractAction("Prune track (P)") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    pruneTrackActionPerformed(null);
                }
            };
            Action delAfter = new AbstractAction("Delete All objects after first selected object") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    ManualEdition.deleteAllObjectsFromFrame(db, true);
                    logger.debug("will delete all after");
                }
            };
            Action delBefore = new AbstractAction("Delete All objects before first selected object") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    ManualEdition.deleteAllObjectsFromFrame(db, false);
                    logger.debug("will delete all after");
                }
            };
            menu.add(prune);
            menu.add(delAfter);
            menu.add(delBefore);
            menu.show(this.deleteObjectsButton, evt.getX(), evt.getY());
        }
    }//GEN-LAST:event_deleteObjectsButtonMousePressed

    private void selectAllObjectsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectAllObjectsButtonActionPerformed
        getImageManager().displayAllObjects(null);
        //GUI.updateRoiDisplayForSelections(null, null);
    }//GEN-LAST:event_selectAllObjectsButtonActionPerformed

    private void previousTrackErrorButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_previousTrackErrorButtonActionPerformed
        if (!checkConnection()) return;
        navigateToNextObjects(false, false, -1, true);
    }//GEN-LAST:event_previousTrackErrorButtonActionPerformed

    private void mergeObjectsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mergeObjectsButtonActionPerformed
        if (!checkConnection()) return;
        //if (db.isReadOnly()) return;
        List<StructureObject> selList = ImageWindowManagerFactory.getImageManager().getSelectedLabileObjects(null);
        if (selList.isEmpty()) logger.warn("Select at least two objects to Merge first!");
        else if (selList.size()<=10 || Utils.promptBoolean("Merge "+selList.size()+ " Objects ? ", null))  ManualEdition.mergeObjects(db, selList, true);
    }//GEN-LAST:event_mergeObjectsButtonActionPerformed

    private void splitObjectsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_splitObjectsButtonActionPerformed
        if (!checkConnection()) return;
        //if (db.isReadOnly()) return;
        List<StructureObject> selList = ImageWindowManagerFactory.getImageManager().getSelectedLabileObjects(null);
        if (selList.isEmpty()) logger.warn("Select at least one object to Split first!");
        else if (selList.size()<=10 || Utils.promptBoolean("Split "+selList.size()+ " Objects ? ", null)) ManualEdition.splitObjects(db, selList, true, false);
    }//GEN-LAST:event_splitObjectsButtonActionPerformed

    private void nextTrackErrorButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nextTrackErrorButtonActionPerformed
        if (!checkConnection()) return;
        navigateToNextObjects(true, false, -1, true);
    }//GEN-LAST:event_nextTrackErrorButtonActionPerformed

    private void selectAllTracksButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectAllTracksButtonActionPerformed
        ImageWindowManagerFactory.getImageManager().displayAllTracks(null);
        //GUI.updateRoiDisplayForSelections(null, null);
    }//GEN-LAST:event_selectAllTracksButtonActionPerformed
    
    public void addToSelectionActionPerformed(int selNumber) {
        if (!this.checkConnection()) return;
        List<Selection> selList = this.getAddObjectsSelection(selNumber);
        if (selList.isEmpty()) return;
        SelectionUtils.addCurrentObjectsToSelections(selList, db.getSelectionDAO());
        selectionList.updateUI();
        GUI.updateRoiDisplayForSelections(null, null);
    }
    
    public void removeFromSelectionActionPerformed(int selNumber) {
        if (!this.checkConnection()) return;
        List<Selection> selList = this.getAddObjectsSelection(selNumber);
        SelectionUtils.removeCurrentObjectsFromSelections(selList, db.getSelectionDAO());
        selectionList.updateUI();
        GUI.updateRoiDisplayForSelections(null, null);
    }
    
    public void removeAllFromSelectionActionPerformed(int selNumber) {
        if (!this.checkConnection()) return;
        List<Selection> selList = this.getAddObjectsSelection(selNumber);
        SelectionUtils.removeAllCurrentImageObjectsFromSelections(selList, db.getSelectionDAO());
        selectionList.updateUI();
        GUI.updateRoiDisplayForSelections(null, null);
    }
    public void toggleDisplaySelection(int selNumber) {
        if (!this.checkConnection()) return;
        List<Selection> selList = this.getAddObjectsSelection(selNumber);
        for (Selection s : selList) s.setIsDisplayingObjects(!s.isDisplayingObjects());
        GUI.updateRoiDisplayForSelections(null, null);
    }
    
    private String getSelectedExperiment() {
        Object sel = experimentList.getSelectedValue();
        if (sel!=null) return (String) sel;
        else return null;
    }
    private List<String> getSelectedExperiments() {
        List res = experimentList.getSelectedValuesList();
        return res;
    }
    private void setSelectedExperiment(String xpName) {
        experimentList.setSelectedValue(xpName, true);
    }
    Map<String, File> dbFiles;
    
    private List<String> getDBNames() {
        if (this.localFileSystemDatabaseRadioButton.isSelected()) {
            dbFiles = ExperimentSearchUtils.listExperiments(experimentFolder.getText(), true, ProgressCallback.get(this));
            List<String> res = new ArrayList<>(dbFiles.keySet());
            Collections.sort(res);
            return res;
        } else return Collections.EMPTY_LIST;
    }
    
    
    public static MasterDAO getDBConnection() {
        if (getInstance()==null) return null;
        return getInstance().db;
    }
    
    public void setInteractiveStructureIdx(int structureIdx) {
        if (interactiveStructure.getItemCount()<=structureIdx+1) {
            logger.error("Error set interactive structure out of bounds: max: {}, current: {}, asked: {}", interactiveStructure.getItemCount()-1, interactiveStructure.getSelectedIndex()-1, structureIdx );
            return;
        }
        interactiveStructure.setSelectedIndex(structureIdx+1); // +1 because root is first
        interactiveStructureActionPerformed(null);
    }
    
    
    
    public static void setNavigationButtonNames(boolean selectionsSelected) {
        if (getInstance()==null) return;
        /*if (selectionsSelected) {
            getInstance().nextTrackErrorButton.setText("Go to Next Object in Selection (X)");
            getInstance().previousTrackErrorButton.setText("Go to Prev. Object in Selection (W)");
        } else {
            getInstance().nextTrackErrorButton.setText("Go to Next Track Error (X)");
            getInstance().previousTrackErrorButton.setText("Go to Prev. TrackError (W)");
        }*/
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
        new ImageJ();
        Core.getCore();
        java.awt.EventQueue.invokeLater(() -> {
            new GUI().setVisible(true);
        });
    }
    
    class ColorPanel extends javax.swing.JPanel {
        javax.swing.JLabel label = new javax.swing.JLabel("OK!");
        ProgressIcon icon;
        int mask;
        int count;

        public ColorPanel(ProgressIcon icon) {
            super(true);
            this.icon = icon;
            this.mask = icon.color.getRGB();
            this.setBackground(icon.color);
            label.setForeground(icon.color);
            this.add(label);
        }
    }

    class ProgressIcon implements Icon {
        int H = 12;
        int W = 100;
        Color color;
        int w;
        Component parent;
        
        public ProgressIcon(Color color, javax.swing.JTabbedPane parent) {
            this.color = color;
            this.parent=parent;
        }

        public void setValue(int i) {
            w = i % W;
            parent.repaint();
        }
        
        public int getMinimum() {
            return 0;
        }

        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(color);
            g.fillRect(x, y, w, H);
        }

        public int getIconWidth() {
            return W;
        }

        public int getIconHeight() {
            return H;
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem CloseNonInteractiveWindowsMenuItem;
    private javax.swing.JScrollPane TimeJSP;
    private javax.swing.JScrollPane actionJSP;
    private javax.swing.JPanel actionPanel;
    private javax.swing.JScrollPane actionPoolJSP;
    private javax.swing.JList actionPoolList;
    private javax.swing.JScrollPane actionPositionJSP;
    private javax.swing.JScrollPane actionStructureJSP;
    private javax.swing.JCheckBoxMenuItem activateLoggingMenuItem;
    private javax.swing.JCheckBoxMenuItem appendToFileMenuItem;
    private javax.swing.JMenuItem clearMemoryMenuItem;
    private javax.swing.JMenuItem clearPPImageMenuItem;
    private javax.swing.JMenuItem clearTrackImagesMenuItem;
    private javax.swing.JMenuItem closeAllWindowsMenuItem;
    private javax.swing.JMenuItem compactLocalDBMenuItem;
    private javax.swing.JMenuItem compareToDumpFileMenuItem;
    private javax.swing.JScrollPane configurationJSP;
    private javax.swing.JPanel configurationPanel;
    private javax.swing.JTextPane console;
    private javax.swing.JScrollPane consoleJSP;
    private javax.swing.JScrollPane controlPanelJSP;
    private javax.swing.JButton createSelectionButton;
    private javax.swing.JMenu dataBaseMenu;
    private javax.swing.JPanel dataPanel;
    private javax.swing.JCheckBoxMenuItem deleteMeasurementsCheckBox;
    private javax.swing.JButton deleteObjectsButton;
    private javax.swing.JMenuItem deleteXPMenuItem;
    private javax.swing.JMenuItem duplicateXPMenuItem;
    private javax.swing.JPanel editPanel;
    private javax.swing.JTextField experimentFolder;
    private javax.swing.JScrollPane experimentJSP;
    private javax.swing.JList experimentList;
    private javax.swing.JMenu experimentMenu;
    private javax.swing.JCheckBoxMenuItem exportConfigMenuItem;
    private javax.swing.JMenuItem exportDataMenuItem;
    private javax.swing.JCheckBoxMenuItem exportObjectsMenuItem;
    private javax.swing.JMenu exportOptionsSubMenu;
    private javax.swing.JCheckBoxMenuItem exportPPImagesMenuItem;
    private javax.swing.JMenuItem exportSelectedFieldsMenuItem;
    private javax.swing.JCheckBoxMenuItem exportSelectionsMenuItem;
    private javax.swing.JMenu exportSubMenu;
    private javax.swing.JCheckBoxMenuItem exportTrackImagesMenuItem;
    private javax.swing.JMenuItem exportWholeXPMenuItem;
    private javax.swing.JMenuItem exportXPConfigMenuItem;
    private javax.swing.JMenuItem exportXPObjectsMenuItem;
    private javax.swing.JMenuItem extractMeasurementMenuItem;
    private javax.swing.JMenuItem extractSelectionMenuItem;
    private javax.swing.JCheckBoxMenuItem importConfigMenuItem;
    private javax.swing.JMenuItem importConfigurationForSelectedPositionsMenuItem;
    private javax.swing.JMenuItem importConfigurationForSelectedStructuresMenuItem;
    private javax.swing.JMenuItem importConfigurationMenuItem;
    private javax.swing.JMenuItem importDataMenuItem;
    private javax.swing.JMenu importExportMenu;
    private javax.swing.JMenuItem importImagesMenuItem;
    private javax.swing.JMenuItem importNewExperimentMenuItem;
    private javax.swing.JCheckBoxMenuItem importObjectsMenuItem;
    private javax.swing.JMenu importOptionsSubMenu;
    private javax.swing.JCheckBoxMenuItem importPPImagesMenuItem;
    private javax.swing.JMenuItem importPositionsToCurrentExperimentMenuItem;
    private javax.swing.JCheckBoxMenuItem importSelectionsMenuItem;
    private javax.swing.JMenu importSubMenu;
    private javax.swing.JCheckBoxMenuItem importTrackImagesMenuItem;
    private javax.swing.JPanel interactiveObjectPanel;
    private javax.swing.JComboBox interactiveStructure;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenu jMenu2;
    private javax.swing.JPopupMenu.Separator jSeparator1;
    private javax.swing.JSplitPane jSplitPane3;
    private javax.swing.JMenu kymographMenu;
    private javax.swing.JButton linkObjectsButton;
    private javax.swing.JMenu localDBMenu;
    private javax.swing.JRadioButtonMenuItem localFileSystemDatabaseRadioButton;
    private javax.swing.JMenu localZoomMenu;
    private javax.swing.JMenu logMenu;
    private javax.swing.JMenuBar mainMenu;
    private javax.swing.JButton manualSegmentButton;
    private javax.swing.JButton mergeObjectsButton;
    private javax.swing.JList microscopyFieldList;
    private javax.swing.JMenu miscMenu;
    private javax.swing.JMenuItem newXPFromTemplateMenuItem;
    private javax.swing.JMenuItem newXPMenuItem;
    private javax.swing.JButton nextTrackErrorButton;
    private javax.swing.JMenu openedImageNumberLimitMenu;
    private javax.swing.JMenu optionMenu;
    private javax.swing.JButton previousTrackErrorButton;
    private javax.swing.JButton pruneTrackButton;
    private javax.swing.JMenuItem refreshExperimentListMenuItem;
    private javax.swing.JButton reloadSelectionsButton;
    private javax.swing.JButton resetLinksButton;
    private javax.swing.JMenuItem runActionAllXPMenuItem;
    private javax.swing.JList runActionList;
    private javax.swing.JMenu runMenu;
    private javax.swing.JMenuItem runSelectedActionsMenuItem;
    private javax.swing.JMenuItem saveXPMenuItem;
    private javax.swing.JButton selectAllObjectsButton;
    private javax.swing.JButton selectAllTracksButton;
    private javax.swing.JScrollPane selectionJSP;
    private javax.swing.JList selectionList;
    private javax.swing.JPanel selectionPanel;
    private javax.swing.JMenuItem setLogFileMenuItem;
    private javax.swing.JMenuItem setSelectedExperimentMenuItem;
    private javax.swing.JMenu shortcutMenu;
    private javax.swing.JMenu shortcutPresetMenu;
    private javax.swing.JButton splitObjectsButton;
    private javax.swing.JTabbedPane tabs;
    private javax.swing.JButton testManualSegmentationButton;
    private javax.swing.JButton testSplitButton;
    private javax.swing.JPanel trackPanel;
    private javax.swing.JPanel trackSubPanel;
    private javax.swing.JScrollPane trackTreeStructureJSP;
    private javax.swing.JPanel trackTreeStructurePanel;
    private javax.swing.JMenuItem unDumpObjectsMenuItem;
    private javax.swing.JButton unlinkObjectsButton;
    private javax.swing.JButton updateRoiDisplayButton;
    // End of variables declaration//GEN-END:variables

    

    
}
