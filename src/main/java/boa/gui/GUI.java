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
import boa.gui.imageInteraction.ImageObjectInterfaceKey;
import boa.gui.imageInteraction.ImageObjectListener;
import boa.gui.imageInteraction.ImageWindowManager;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import static boa.gui.imageInteraction.ImageWindowManagerFactory.getImageManager;
import boa.gui.objects.ObjectNode;
import dataStructure.configuration.Experiment;
import boa.gui.objects.StructureObjectTreeGenerator;
import boa.gui.objects.TrackNode;
import boa.gui.objects.TrackTreeController;
import boa.gui.objects.TrackTreeGenerator;
import boa.gui.selection.SelectionUtils;
import static boa.gui.selection.SelectionUtils.setMouseAdapter;
import boa.gui.selection.SelectionRenderer;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoIterable;
import com.sun.java.swing.plaf.motif.MotifMenuItemUI;
import configuration.parameters.FileChooser;
import configuration.parameters.NumberParameter;
import core.Processor;
import core.PythonGateway;
import core.Task;
import dataStructure.configuration.ChannelImage;
import dataStructure.configuration.Structure;
import dataStructure.objects.MorphiumMasterDAO;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.MorphiumObjectDAO;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.Selection;
import dataStructure.objects.SelectionDAO;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import ij.ImageJ;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageInteger;
import image.TypeConverter;
import java.awt.Color;
import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import measurement.MeasurementKeyObject;
import measurement.extraction.DataExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import plugins.ManualSegmenter;
import plugins.ObjectSplitter;
import plugins.PluginFactory;
import utils.CommandExecuter;
import utils.MorphiumUtils;
import utils.Pair;
import utils.Utils;
import static utils.Utils.addHorizontalScrollBar;


/**
 *
 * @author jollion
 */
public class GUI extends javax.swing.JFrame implements ImageObjectListener {
    public static final Logger logger = LoggerFactory.getLogger(GUI.class);
    public final static String DBprefix = "boa_";
    private static GUI instance;
    
    // db-related attributes
    MasterDAO db;
    
    // xp tree-related attributes
    ConfigurationTreeGenerator configurationTreeGenerator;
    
    // track-related attributes
    TrackTreeController trackTreeController;
    private HashMap<Integer, JTree> currentTrees;
    
    //Object Tree related attributes
    boolean reloadObjectTrees=false;
    
    // structure-related attributes
    //StructureObjectTreeGenerator objectTreeGenerator;
    DefaultListModel<String> structureModel;
    DefaultListModel<String> experimentModel = new DefaultListModel();
    DefaultListModel<String> actionMicroscopyFieldModel;
    DefaultListModel<Selection> selectionModel;
    PythonGateway pyGtw;
    // shortcuts
    private HashMap<KeyStroke, Action> actionMap = new HashMap<KeyStroke, Action>();
    KeyboardFocusManager kfm;
    
    // enable/disable components
    final private List<Component> relatedToXPSet;
    /**
     * Creates new form GUI
     */
    public GUI() {
        logger.info("Creating GUI instance...");
        this.instance=this;
        initComponents();
        experimentList.setModel(experimentModel);
        relatedToXPSet = new ArrayList<Component>() {{add(saveXPMenuItem);add(exportSelectedFieldsMenuItem);add(exportXPConfigMenuItem);add(importFieldsToCurrentExperimentMenuItem);add(importConfigToCurrentExperimentMenuItem);add(importConfigurationForSelectedStructuresMenuItem);add(importConfigurationForSelectedPositionsMenuItem);add(importImagesMenuItem);add(runSelectedActionsMenuItem);add(extractMeasurementMenuItem);}};
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        toFront();
        // format button
        this.bsonFormatMenuItem.setSelected(true);
        this.jsonFormatMenuItem.setSelected(false);
        ButtonGroup group = new ButtonGroup();
        group.add(bsonFormatMenuItem);
        group.add(jsonFormatMenuItem);
        deleteMeasurementsCheckBox.setSelected(PropertyUtils.get(PropertyUtils.DELETE_MEASUREMENTS, true));
        logger.debug("del meas: {}, {} isSel: {}", PropertyUtils.get(PropertyUtils.DELETE_MEASUREMENTS, true), PropertyUtils.get(PropertyUtils.DELETE_MEASUREMENTS, "true"), deleteMeasurementsCheckBox.isSelected());
        PluginFactory.findPlugins("plugins.plugins");
        
        pyGtw = new PythonGateway();
        pyGtw.startGateway();
        
        // selections
        selectionModel = new DefaultListModel<Selection>();
        this.selectionList.setModel(selectionModel);
        this.selectionList.setCellRenderer(new SelectionRenderer());
        setMouseAdapter(selectionList);
        addHorizontalScrollBar(trackStructureJCB);
        
        populateExperimentList();
        updateDisplayRelatedToXPSet();
        //updateMongoDBBinActions();
        tabs.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if (reloadObjectTrees && tabs.getSelectedComponent()==dataPanel) {
                    reloadObjectTrees=false;
                    loadObjectTrees();
                    displayTrackTrees();
                }
                if (tabs.getSelectedComponent()==actionPanel) {
                    populateActionStructureList();
                    populateActionMicroscopyFieldList();
                }
            }
        });
        
        // KEY shortcuts
        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK), new AbstractAction("Link") {
            @Override
            public void actionPerformed(ActionEvent e) {
                linkObjectsButtonActionPerformed(e);
                logger.debug("L pressed: " + e);
            }
        });
        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_U, KeyEvent.CTRL_DOWN_MASK), new AbstractAction("Unlink") {
            @Override
            public void actionPerformed(ActionEvent e) {
                unlinkObjectsButtonActionPerformed(e);
                logger.debug("U pressed: " + e);
            }
        });
        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, KeyEvent.CTRL_DOWN_MASK), new AbstractAction("Delete") {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteObjectsButtonActionPerformed(e);
                logger.debug("D pressed: " + e);
            }
        });
        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.CTRL_DOWN_MASK), new AbstractAction("Merge") {
            @Override
            public void actionPerformed(ActionEvent e) {
                mergeObjectsButtonActionPerformed(e);
                logger.debug("M pressed: " + e);
            }
        });
        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK), new AbstractAction("Split") {
            @Override
            public void actionPerformed(ActionEvent e) {
                splitObjectsButtonActionPerformed(e);
                logger.debug("S pressed: " + e);
            }
        });
        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.CTRL_DOWN_MASK), new AbstractAction("Create") {
            @Override
            public void actionPerformed(ActionEvent e) {
                manualSegmentButtonActionPerformed(e);
                logger.debug("C pressed: " + e);
            }
        });
        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK), new AbstractAction("Reset Links") {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetLinksButtonActionPerformed(e);
                logger.debug("R pressed: " + e);
            }
        });
        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.CTRL_DOWN_MASK), new AbstractAction("Select All Objects") {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectAllObjectsActionPerformed(e);
                logger.debug("A pressed: " + e);
            }
        });
        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Q, KeyEvent.CTRL_DOWN_MASK), new AbstractAction("Select All Tracks") {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectAllTracksButtonActionPerformed(e);
                logger.debug("Q pressed: " + e);
            }
        });
        
        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_I, KeyEvent.CTRL_DOWN_MASK), new AbstractAction("Change Interactive structure") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (interactiveStructure.getItemCount()>0) {
                    int s = interactiveStructure.getSelectedIndex();
                    s = (s+1) % interactiveStructure.getItemCount();
                    setInteractiveStructureIdx(s);
                }
                logger.debug("Current interactive structure: {}", interactiveStructure.getSelectedIndex());
            }
        });
        
        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.CTRL_DOWN_MASK), new AbstractAction("Track mode") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (ImageWindowManager.displayTrackMode) ImageWindowManager.displayTrackMode = false;
                else ImageWindowManager.displayTrackMode = true;
                logger.debug("TrackMode is {}", ImageWindowManager.displayTrackMode? "ON":"OFF");
            }
        });
        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.CTRL_DOWN_MASK), new AbstractAction("Prev") {
            @Override
            public void actionPerformed(ActionEvent e) {
                previousTrackErrorButtonActionPerformed(e);
            }
        });
        
        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, KeyEvent.CTRL_DOWN_MASK), new AbstractAction("Next") {
            @Override
            public void actionPerformed(ActionEvent e) {
                nextTrackErrorButtonActionPerformed(e);
            }
        });
        
        kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        kfm.addKeyEventDispatcher( new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(e);
                if ( actionMap.containsKey(keyStroke) ) {
                    final Action a = actionMap.get(keyStroke);
                    final ActionEvent ae = new ActionEvent(e.getSource(), e.getID(), null );
                    /*SwingUtilities.invokeLater( new Runnable() {
                        @Override
                        public void run() {
                            a.actionPerformed(ae);
                        }
                    });*/
                    a.actionPerformed(ae);
                    return true;
                }
                return false;
            }
          });
    }
    
    //public StructureObjectTreeGenerator getObjectTree() {return this.objectTreeGenerator;}
    public TrackTreeController getTrackTrees() {return this.trackTreeController;}
    
    /*public static boolean isDisplayingObject(StructureObject object) {
        if (object==null) return false;
        if (instance==null) return false;
        else {
            Enumeration<Selection> sels = instance.selectionModel.elements();
            while (sels.hasMoreElements()) {
                Selection s = sels.nextElement();
                if (!s.isDisplayingObjects() || s.getStructureIdx()!=object.getStructureIdx()) continue;
                if (s.getElements(object.getFieldName()).contains(object)) return true;
            }
            return false;
        }
    }
    
    public static boolean isDisplayingTrack(StructureObject trackHead) {
        if (trackHead==null) return false;
        if (instance==null) return false;
        else {
            // look in selections
            Enumeration<Selection> sels = instance.selectionModel.elements();
            while (sels.hasMoreElements()) {
                Selection s = sels.nextElement();
                if (!s.isDisplayingTracks() || s.getStructureIdx()!=trackHead.getStructureIdx()) continue;
                if (s.getTrackHeads(trackHead.getFieldName()).contains(trackHead)) return true;
            }
            
            // look in track list
            TrackTreeGenerator gen = instance.trackTreeController.getGeneratorS().get(trackHead.getStructureIdx());
            if (gen!=null) {list
                List<StructureObject> s = gen.getSelectedTrackHeads();
                return s.contains(trackHead);
            }
            return false;
        }
    }*/
    
    public static void updateRoiDisplay(ImageObjectInterface i) {
        if (instance==null) return;
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        if (iwm==null) return;
        Image image=null;
        if (i==null) {
            Object im = iwm.getDisplayer().getCurrentImage();
            if (im!=null) image = iwm.getDisplayer().getImage(im);
            if (image==null) return;
            else i = iwm.getImageObjectInterface(image);
        }
        if (image==null) {
            return; // todo -> actions on all images?
        }
        iwm.hideAllRois(image, true, false); // will update selections
        if (i==null) return;
        
        // look in track list
        TrackTreeGenerator gen = instance.trackTreeController.getLastTreeGenerator();
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
    
    public static void updateRoiDisplayForSelections(Image image, ImageObjectInterface i) {
        if (instance==null) return;
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
        Enumeration<Selection> sels = instance.selectionModel.elements();
        while (sels.hasMoreElements()) {
            Selection s = sels.nextElement();
            //logger.debug("selection: {}", s);
            if (s.isDisplayingTracks()) SelectionUtils.displayTracks(s, i);
            if (s.isDisplayingObjects()) SelectionUtils.displayObjects(s, i);
        }
    }
    
    public void setDBConnection(String dbName, String hostname) {
        long t0 = System.currentTimeMillis();
        if (hostname==null) hostname = getHostName();
        Morphium m=MorphiumUtils.createMorphium(hostname, 27017, dbName);
        long t1 = System.currentTimeMillis();
        if (m==null) {
            unsetXP();
            return;
        }
        logger.info("Connection established with db: {} @ host: {}, in {} ms", dbName, hostName, t1-t0);
        setDBConnection(m);
        updateDisplayRelatedToXPSet();
    }
    
    public void setDBConnection(Morphium m) {
        if (m==null) {
            unsetXP();
            return;
        }
        this.db = new MorphiumMasterDAO(m);
        db.getExperiment();
        if (db.getExperiment()==null) {
            logger.warn("no experiment found in DB");
            unsetXP();
            return;
        } else logger.info("Experiment found: {} ", db.getExperiment().getName());
        updateConfigurationTree();
        populateActionStructureList();
        populateActionMicroscopyFieldList();
        reloadObjectTrees=true;
        populateSelections();
    }
    
    private void updateConfigurationTree() {
        if (db==null) {
            configurationTreeGenerator=null;
            configurationJSP.setViewportView(null);
        } else {
            configurationTreeGenerator = new ConfigurationTreeGenerator(db.getExperiment());
            configurationJSP.setViewportView(configurationTreeGenerator.getTree());
        }
    }
    
    private void unsetXP() {
        db=null;
        reloadObjectTrees=true;
        populateActionStructureList();
        populateActionMicroscopyFieldList();
        updateDisplayRelatedToXPSet();
        updateConfigurationTree();
        tabs.setSelectedIndex(0);
        ImageWindowManagerFactory.getImageManager().flush();
    }
    
    private void updateDisplayRelatedToXPSet() {
        final boolean enable = db!=null;
        String title = db==null ? "No Selected Experiment" : "Experiment: "+db.getDBName();
        String v = Utils.getVersion(this);
        if (v!=null && v.length()>0) title = "Version: "+v+" - "+title;
        setTitle(title);
        for (Component c: relatedToXPSet) c.setEnabled(enable);
        runActionAllXPMenuItem.setEnabled(!enable); // only available if no xp is set
        this.tabs.setEnabledAt(1, enable); // configuration
        this.tabs.setEnabledAt(2, enable); // data browsing
    }
    
    public void populateSelections() {
        List<Selection> selectedValues = selectionList.getSelectedValuesList();
        this.selectionModel.removeAllElements();
        if (!checkConnection()) return;
        SelectionDAO dao = this.db.getSelectionDAO();
        List<Selection> sels = dao.getSelections();
        for (Selection sel : sels) {
            selectionModel.addElement(sel);
            //logger.debug("Selection : {}, displayingObjects: {} track: {}", sel.getName(), sel.isDisplayingObjects(), sel.isDisplayingTracks());
        }
        Utils.setSelectedValues(selectedValues, selectionList, selectionModel);
        resetSelectionHighlight();
    }
    
    public void resetSelectionHighlight() {
        if (trackTreeController==null) return;
        trackTreeController.resetHighlight();
    }
    
    public List<Selection> getSelectedSelections() {
        return selectionList.getSelectedValuesList();
    }
    
    public void setSelectedSelection(Selection sel) {
        this.selectionList.setSelectedValue(sel, false);
    }
    
    public List<Selection> getSelections() {
        return Utils.asList(selectionModel);
    }
    
    protected void loadObjectTrees() {
        //TODO remember treepath if existing and set them in the new trees
        
        //objectTreeGenerator = new StructureObjectTreeGenerator(db);
        //structureJSP.setViewportView(objectTreeGenerator.getTree());
        
        trackTreeController = new TrackTreeController(db);
        setTrackTreeStructures(db.getExperiment().getStructuresAsString());
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
        List sel = structureList.getSelectedValuesList();
        if (structureModel==null) {
            structureModel = new DefaultListModel();
            this.structureList.setModel(structureModel);
        } else structureModel.removeAllElements();
        if (db!=null) {
            for (Structure s : db.getExperiment().getStructures().getChildren()) structureModel.addElement(s.getName());
            Utils.setSelectedValues(sel, structureList, structureModel);
        }
    }
    public int[] getSelectedStructures(boolean returnAllIfNoneIsSelected) {
        int[] res = structureList.getSelectedIndices();
        if (res.length==0 && returnAllIfNoneIsSelected) {
            res=new int[db.getExperiment().getStructureCount()];
            for (int i = 0; i<res.length; ++i) res[i]=i;
        }
        return res;
    }
    
    public void populateActionMicroscopyFieldList() {
        List sel = microscopyFieldList.getSelectedValuesList();
        if (actionMicroscopyFieldModel==null) {
            actionMicroscopyFieldModel = new DefaultListModel();
            this.microscopyFieldList.setModel(actionMicroscopyFieldModel);
        } else actionMicroscopyFieldModel.removeAllElements();
        if (db!=null) {
            for (int i =0; i<db.getExperiment().getPositionCount(); ++i) actionMicroscopyFieldModel.addElement(db.getExperiment().getPosition(i).getName());
            Utils.setSelectedValues(sel, microscopyFieldList, actionMicroscopyFieldModel);
        }
    }
    public int[] getSelectedMicroscopyFields() {
        int[] res = microscopyFieldList.getSelectedIndices();
        if (res.length==0) {
            res=new int[db.getExperiment().getPositionCount()];
            for (int i = 0; i<res.length; ++i) res[i]=i;
        }
        return res;
    }
    public List<String> getSelectedMicroscopyFieldNames(boolean returnAllIfNoneSelected) {
        if (returnAllIfNoneSelected && microscopyFieldList.getSelectedIndex()<0) return new ArrayList<String>(Arrays.asList(db.getExperiment().getPositionsAsString()));
        else return microscopyFieldList.getSelectedValuesList();
    }
    
    public void setSelectedTab(int tabIndex) {
        this.tabs.setSelectedIndex(tabIndex);
        if (reloadObjectTrees && tabs.getSelectedComponent()==dataPanel) {
            reloadObjectTrees=false;
            loadObjectTrees();
        }
    }
    
    
    public static GUI getInstance() {
        if (instance==null) new GUI();
        return instance;
    }
    
    public static boolean hasInstance() {
        return instance!=null;
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
        // highlight les nouveaux trees...
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
    
    /*private void initDBImages() {
            m=MorphiumUtils.createMorphium("testGUI");
            m.clearCollection(Experiment.class);
            m.clearCollection(StructureObject.class);
            xpDAO = new ExperimentDAO(m);
            objectDAO = new MorphiumObjectDAO(m, xpDAO);
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
            xpDAO.storeLater(xp);
            
            // process
            Processor.preProcessImages(xp, objectDAO, true);
            ArrayList<StructureObject> root = xp.getMicroscopyField(0).createRootObjects();
            objectDAO.storeLater(root); 
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

        tabs = new javax.swing.JTabbedPane();
        actionPanel = new javax.swing.JPanel();
        hostName = new javax.swing.JTextField();
        actionStructureJSP = new javax.swing.JScrollPane();
        structureList = new javax.swing.JList();
        actionMicroscopyFieldJSP = new javax.swing.JScrollPane();
        microscopyFieldList = new javax.swing.JList();
        actionJSP = new javax.swing.JScrollPane();
        runActionList = new javax.swing.JList();
        jScrollPane1 = new javax.swing.JScrollPane();
        experimentList = new javax.swing.JList();
        configurationPanel = new javax.swing.JPanel();
        configurationJSP = new javax.swing.JScrollPane();
        dataPanel = new javax.swing.JPanel();
        ControlPanel = new javax.swing.JPanel();
        trackStructureJCB = new javax.swing.JComboBox();
        selectAllTracksButton = new javax.swing.JButton();
        nextTrackErrorButton = new javax.swing.JButton();
        splitObjectsButton = new javax.swing.JButton();
        mergeObjectsButton = new javax.swing.JButton();
        previousTrackErrorButton = new javax.swing.JButton();
        interactiveStructure = new javax.swing.JComboBox();
        selectAllObjects = new javax.swing.JButton();
        deleteObjectsButton = new javax.swing.JButton();
        updateRoiDisplayButton = new javax.swing.JButton();
        manualSegmentButton = new javax.swing.JButton();
        testManualSegmentationButton = new javax.swing.JButton();
        linkObjectsButton = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        unlinkObjectsButton = new javax.swing.JButton();
        resetLinksButton = new javax.swing.JButton();
        testSplitButton = new javax.swing.JButton();
        trackPanel = new javax.swing.JPanel();
        TimeJSP = new javax.swing.JScrollPane();
        trackSubPanel = new javax.swing.JPanel();
        selectionPanel = new javax.swing.JPanel();
        selectionJSP = new javax.swing.JScrollPane();
        selectionList = new javax.swing.JList();
        createSelectionButton = new javax.swing.JButton();
        reloadSelectionsButton = new javax.swing.JButton();
        jMenuBar1 = new javax.swing.JMenuBar();
        experimentMenu = new javax.swing.JMenu();
        refreshExperimentListMenuItem = new javax.swing.JMenuItem();
        setSelectedExperimentMenuItem = new javax.swing.JMenuItem();
        newXPMenuItem = new javax.swing.JMenuItem();
        deleteXPMenuItem = new javax.swing.JMenuItem();
        duplicateXPMenuItem = new javax.swing.JMenuItem();
        saveXPMenuItem = new javax.swing.JMenuItem();
        runMenu = new javax.swing.JMenu();
        importImagesMenuItem = new javax.swing.JMenuItem();
        runSelectedActionsMenuItem = new javax.swing.JMenuItem();
        runActionAllXPMenuItem = new javax.swing.JMenuItem();
        extractMeasurementMenuItem = new javax.swing.JMenuItem();
        importExportMenu = new javax.swing.JMenu();
        exportSubMenu = new javax.swing.JMenu();
        exportSelectedFieldsMenuItem = new javax.swing.JMenuItem();
        exportXPConfigMenuItem = new javax.swing.JMenuItem();
        exportWholeXPMenuItem = new javax.swing.JMenuItem();
        importSubMenu = new javax.swing.JMenu();
        importFieldsToCurrentExperimentMenuItem = new javax.swing.JMenuItem();
        importConfigToCurrentExperimentMenuItem = new javax.swing.JMenuItem();
        importConfigurationForSelectedPositionsMenuItem = new javax.swing.JMenuItem();
        importConfigurationForSelectedStructuresMenuItem = new javax.swing.JMenuItem();
        importNewExperimentMenuItem = new javax.swing.JMenuItem();
        setMongoBinDirectoryMenu = new javax.swing.JMenuItem();
        jMenu1 = new javax.swing.JMenu();
        bsonFormatMenuItem = new javax.swing.JRadioButtonMenuItem();
        jsonFormatMenuItem = new javax.swing.JRadioButtonMenuItem();
        importOptionsSubMenu = new javax.swing.JMenu();
        eraseCollectionCheckbox = new javax.swing.JCheckBoxMenuItem();
        jSeparator1 = new javax.swing.JPopupMenu.Separator();
        optionMenu = new javax.swing.JMenu();
        jMenu2 = new javax.swing.JMenu();
        deleteMeasurementsCheckBox = new javax.swing.JCheckBoxMenuItem();
        miscMenu = new javax.swing.JMenu();
        closeAllWindowsMenuItem = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        hostName.setText("localhost");
        hostName.setBorder(javax.swing.BorderFactory.createTitledBorder("DataBase URL"));
        hostName.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                hostNameActionPerformed(evt);
            }
        });

        actionStructureJSP.setBorder(javax.swing.BorderFactory.createTitledBorder("Structures"));

        actionStructureJSP.setViewportView(structureList);

        actionMicroscopyFieldJSP.setBorder(javax.swing.BorderFactory.createTitledBorder("Positions"));

        actionMicroscopyFieldJSP.setViewportView(microscopyFieldList);

        actionJSP.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createTitledBorder("Actions to Run")));

        runActionList.setModel(new javax.swing.AbstractListModel() {
            String[] strings = { "Pre-Processing", "Re-Run Pre-Processing", "Segment", "Track", "Measurements" };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        runActionList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        actionJSP.setViewportView(runActionList);

        experimentList.setBackground(new java.awt.Color(254, 254, 254));
        experimentList.setBorder(javax.swing.BorderFactory.createTitledBorder("Experiments"));
        jScrollPane1.setViewportView(experimentList);

        javax.swing.GroupLayout actionPanelLayout = new javax.swing.GroupLayout(actionPanel);
        actionPanel.setLayout(actionPanelLayout);
        actionPanelLayout.setHorizontalGroup(
            actionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(actionPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(actionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 227, Short.MAX_VALUE)
                    .addComponent(hostName))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(actionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(actionJSP, javax.swing.GroupLayout.DEFAULT_SIZE, 161, Short.MAX_VALUE)
                    .addComponent(actionStructureJSP, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(actionMicroscopyFieldJSP, javax.swing.GroupLayout.DEFAULT_SIZE, 227, Short.MAX_VALUE)
                .addContainerGap())
        );
        actionPanelLayout.setVerticalGroup(
            actionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(actionPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(actionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(actionMicroscopyFieldJSP)
                    .addGroup(actionPanelLayout.createSequentialGroup()
                        .addComponent(actionStructureJSP, javax.swing.GroupLayout.DEFAULT_SIZE, 498, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(actionJSP, javax.swing.GroupLayout.PREFERRED_SIZE, 163, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(actionPanelLayout.createSequentialGroup()
                        .addComponent(hostName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane1)))
                .addContainerGap())
        );

        tabs.addTab("Actions", actionPanel);

        javax.swing.GroupLayout configurationPanelLayout = new javax.swing.GroupLayout(configurationPanel);
        configurationPanel.setLayout(configurationPanelLayout);
        configurationPanelLayout.setHorizontalGroup(
            configurationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(configurationJSP, javax.swing.GroupLayout.DEFAULT_SIZE, 651, Short.MAX_VALUE)
        );
        configurationPanelLayout.setVerticalGroup(
            configurationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(configurationJSP, javax.swing.GroupLayout.DEFAULT_SIZE, 691, Short.MAX_VALUE)
        );

        tabs.addTab("Configuration", configurationPanel);

        ControlPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createTitledBorder("Controls")));

        trackStructureJCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                trackStructureJCBActionPerformed(evt);
            }
        });

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

        interactiveStructure.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                interactiveStructureActionPerformed(evt);
            }
        });

        selectAllObjects.setText("Select All Objects (A)");
        selectAllObjects.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectAllObjectsActionPerformed(evt);
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

        jLabel1.setText("Interactive Structure:");

        jLabel2.setText("Track-tree Structure:");

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

        javax.swing.GroupLayout ControlPanelLayout = new javax.swing.GroupLayout(ControlPanel);
        ControlPanel.setLayout(ControlPanelLayout);
        ControlPanelLayout.setHorizontalGroup(
            ControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(trackStructureJCB, 0, 162, Short.MAX_VALUE)
            .addComponent(selectAllTracksButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(nextTrackErrorButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(mergeObjectsButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(previousTrackErrorButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(interactiveStructure, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(selectAllObjects, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(deleteObjectsButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(updateRoiDisplayButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(linkObjectsButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(unlinkObjectsButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(ControlPanelLayout.createSequentialGroup()
                .addGroup(ControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel1)
                    .addComponent(jLabel2))
                .addGap(0, 0, Short.MAX_VALUE))
            .addComponent(resetLinksButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, ControlPanelLayout.createSequentialGroup()
                .addGroup(ControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(splitObjectsButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(manualSegmentButton, javax.swing.GroupLayout.PREFERRED_SIZE, 1, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(ControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(testManualSegmentationButton, javax.swing.GroupLayout.DEFAULT_SIZE, 59, Short.MAX_VALUE)
                    .addComponent(testSplitButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );
        ControlPanelLayout.setVerticalGroup(
            ControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ControlPanelLayout.createSequentialGroup()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(interactiveStructure, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(trackStructureJCB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(updateRoiDisplayButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(selectAllObjects)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(selectAllTracksButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(nextTrackErrorButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(previousTrackErrorButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(ControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(manualSegmentButton)
                    .addComponent(testManualSegmentationButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(ControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(splitObjectsButton)
                    .addComponent(testSplitButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(mergeObjectsButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(deleteObjectsButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(linkObjectsButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(unlinkObjectsButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(resetLinksButton)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        trackPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Tracks"));

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
            .addComponent(TimeJSP, javax.swing.GroupLayout.DEFAULT_SIZE, 641, Short.MAX_VALUE)
        );

        selectionPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Selections"));

        selectionList.setBackground(new java.awt.Color(214, 214, 214));
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
            .addComponent(selectionJSP, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
        );
        selectionPanelLayout.setVerticalGroup(
            selectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, selectionPanelLayout.createSequentialGroup()
                .addComponent(createSelectionButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(reloadSelectionsButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(selectionJSP))
        );

        javax.swing.GroupLayout dataPanelLayout = new javax.swing.GroupLayout(dataPanel);
        dataPanel.setLayout(dataPanelLayout);
        dataPanelLayout.setHorizontalGroup(
            dataPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(dataPanelLayout.createSequentialGroup()
                .addComponent(ControlPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(selectionPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(trackPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        dataPanelLayout.setVerticalGroup(
            dataPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(dataPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(dataPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(dataPanelLayout.createSequentialGroup()
                        .addComponent(trackPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 14, Short.MAX_VALUE))
                    .addComponent(ControlPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(dataPanelLayout.createSequentialGroup()
                        .addComponent(selectionPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addContainerGap())))
        );

        tabs.addTab("Data Browsing", dataPanel);

        experimentMenu.setText("Experiment");

        refreshExperimentListMenuItem.setText("Refresh List");
        refreshExperimentListMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshExperimentListMenuItemActionPerformed(evt);
            }
        });
        experimentMenu.add(refreshExperimentListMenuItem);

        setSelectedExperimentMenuItem.setText("Set / Unset Selected");
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

        jMenuBar1.add(experimentMenu);

        runMenu.setText("Run");

        importImagesMenuItem.setText("Import/re-link Images");
        importImagesMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importImagesMenuItemActionPerformed(evt);
            }
        });
        runMenu.add(importImagesMenuItem);

        runSelectedActionsMenuItem.setText("Run Selected Actions");
        runSelectedActionsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runSelectedActionsMenuItemActionPerformed(evt);
            }
        });
        runMenu.add(runSelectedActionsMenuItem);

        runActionAllXPMenuItem.setText("Run Selected Actions on all Selected Experiments");
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

        jMenuBar1.add(runMenu);

        importExportMenu.setText("Import/Export");

        exportSubMenu.setText("Export");

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

        importExportMenu.add(exportSubMenu);

        importSubMenu.setText("Import");

        importFieldsToCurrentExperimentMenuItem.setText("Fields to Current Experiment");
        importFieldsToCurrentExperimentMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importFieldsToCurrentExperimentMenuItemActionPerformed(evt);
            }
        });
        importSubMenu.add(importFieldsToCurrentExperimentMenuItem);

        importConfigToCurrentExperimentMenuItem.setText("Configuration to Current Experiment");
        importConfigToCurrentExperimentMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importConfigToCurrentExperimentMenuItemActionPerformed(evt);
            }
        });
        importSubMenu.add(importConfigToCurrentExperimentMenuItem);

        importConfigurationForSelectedPositionsMenuItem.setText("Configuration for Selected Positions");
        importConfigurationForSelectedPositionsMenuItem.setEnabled(false);
        importConfigurationForSelectedPositionsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importConfigurationForSelectedPositionsMenuItemActionPerformed(evt);
            }
        });
        importSubMenu.add(importConfigurationForSelectedPositionsMenuItem);

        importConfigurationForSelectedStructuresMenuItem.setText("Configuration for Selected Structures");
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

        importExportMenu.add(importSubMenu);

        setMongoBinDirectoryMenu.setText("Set MongoDB Bin Directory");
        setMongoBinDirectoryMenu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                setMongoBinDirectoryMenuActionPerformed(evt);
            }
        });
        importExportMenu.add(setMongoBinDirectoryMenu);

        jMenu1.setText("Set Export Format");

        bsonFormatMenuItem.setSelected(true);
        bsonFormatMenuItem.setText("BSON (faster, safer)");
        bsonFormatMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bsonFormatMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(bsonFormatMenuItem);

        jsonFormatMenuItem.setSelected(true);
        jsonFormatMenuItem.setText("JSON (human readable)");
        jsonFormatMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jsonFormatMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(jsonFormatMenuItem);

        importExportMenu.add(jMenu1);

        importOptionsSubMenu.setText("Import Options");

        eraseCollectionCheckbox.setSelected(true);
        eraseCollectionCheckbox.setText("Erase Collections before Import (recommended)");
        eraseCollectionCheckbox.setEnabled(false);
        eraseCollectionCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                eraseCollectionCheckboxActionPerformed(evt);
            }
        });
        importOptionsSubMenu.add(eraseCollectionCheckbox);

        importExportMenu.add(importOptionsSubMenu);
        importExportMenu.add(jSeparator1);

        jMenuBar1.add(importExportMenu);

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

        jMenuBar1.add(optionMenu);

        miscMenu.setText("Misc");

        closeAllWindowsMenuItem.setText("Close all windows");
        closeAllWindowsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeAllWindowsMenuItemActionPerformed(evt);
            }
        });
        miscMenu.add(closeAllWindowsMenuItem);

        jMenuBar1.add(miscMenu);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(tabs)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(tabs)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    
    private void runAction(String fieldName, boolean preProcess, boolean reProcess, boolean segmentAndTrack, boolean trackOnly, boolean measurements, boolean deleteObjects) {
        if (preProcess || reProcess) {
            logger.info("Pre-Processing: Field: {}", fieldName);
            Processor.preProcessImages(db.getExperiment().getPosition(fieldName), db.getDao(fieldName), true, preProcess);
        }
        if (segmentAndTrack || trackOnly) {
            int[] selectedStructures = this.getSelectedStructures(true);
            Processor.processAndTrackStructures(db.getDao(fieldName), true, trackOnly, selectedStructures);
        }
        if (measurements) {
            logger.info("Measurements: Field: {}", fieldName);
            if (deleteMeasurementsCheckBox.isSelected()) db.getDao(fieldName).deleteAllMeasurements();
            Processor.performMeasurements(db.getDao(fieldName));
        }
        if (segmentAndTrack || trackOnly) populateSelections();
    }
    
    private String getHostName() {
        String host = this.hostName.getText();
        if (host==null || host.length()==0) host = "localhost";
        return host;
    }
        private int navigateCount = 0;
    public void navigateToNextObjects(boolean next, boolean nextPosition, int structureDisplay, boolean setInteractiveStructure) {
        List<Selection> sels = selectionList.getSelectedValuesList();
        if (sels.isEmpty()) ImageWindowManagerFactory.getImageManager().goToNextTrackError(null, this.trackTreeController.getLastTreeGenerator().getSelectedTrackHeads(), next);
        else {
            ImageObjectInterface i = ImageWindowManagerFactory.getImageManager().getImageObjectInterface(null);
            if (i!=null && i.getParent().getExperiment()!=db.getExperiment()) i=null;
            if (structureDisplay==-1 && i!=null) {
                Image im = ImageWindowManagerFactory.getImageManager().getDisplayer().getCurrentImage2();
                if (im!=null) {
                    ImageObjectInterfaceKey key = ImageWindowManagerFactory.getImageManager().getImageObjectInterfaceKey(im);
                    if (key!=null) {
                        structureDisplay = key.displayedStructureIdx;
                    }
                }
            }
            String position = i==null? null:i.getParent().getPositionName();
            if (i==null || nextPosition) navigateCount=2;
            else {
                boolean move = ImageWindowManagerFactory.getImageManager().goToNextObject(null, SelectionUtils.getStructureObjects(sels, position), next);
                if (move) {
                    navigateCount=0;
                }
                else navigateCount++;
            }
            if (navigateCount>1) { // open next/prev image containig objects
                List<StructureObject> l;
                if (nextPosition || position==null) {
                    String selPos = null;
                    if (position==null) selPos = this.trackTreeController.getSelectedPosition();
                    if (selPos!=null) position = selPos;
                    else position = SelectionUtils.getNextPosition(sels, position, next);
                    l = position==null ? null : SelectionUtils.getStructureObjects(sels, position);
                    while (position!=null && (l==null || l.isEmpty())) {
                        position = SelectionUtils.getNextPosition(sels, position, next); 
                        l = position==null ? null : SelectionUtils.getStructureObjects(sels, position);
                    }
                    i=null;
                    logger.debug("changing position");
                } else l = SelectionUtils.getStructureObjects(sels, position);
                logger.debug("position: {}, #objects: {}, nav: {}, NextPosition? {}", position, position!=null ? l.size() : 0, navigateCount, nextPosition);
                if (position==null) return;
                this.trackTreeController.selectPosition(position);
                Map<StructureObject, List<StructureObject>> objectsByParent = StructureObjectUtils.splitByParentTrackHead(l);
                List<StructureObject> parents = new ArrayList<>(objectsByParent.keySet());
                Collections.sort(parents);
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
                int childStructureIdx = i==null? l.get(0).getStructureIdx() : i.getChildStructureIdx();
                if (structureDisplay<0) structureDisplay = childStructureIdx;
                if ((nextParentIdx<0 || nextParentIdx>=parents.size())) {
                    logger.warn("current parent {} not found in objects parents: {}", i==null?null : i.getParent(), parents);
                    navigateToNextObjects(next, true, structureDisplay, setInteractiveStructure);
                } else {
                    StructureObject nextParent = parents.get(nextParentIdx);
                    logger.debug("next parent: {}", nextParent);
                    List track = db.getDao(nextParent.getPositionName()).getTrack(nextParent);
                    ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
                    ImageObjectInterface nextI = iwm.getImageTrackObjectInterface(track, childStructureIdx);
                    Image im = iwm.getImage(nextI, false);
                    if (im==null) iwm.addImage(nextI.generateRawImage(structureDisplay), nextI, structureDisplay, false, true);
                    else ImageWindowManagerFactory.getImageManager().setActive(im);
                    navigateCount=0;
                    if (i==null && setInteractiveStructure) { // new image open -> set interactive structure & navigate to next object in newly opened image
                        setInteractiveStructureIdx(childStructureIdx);
                        interactiveStructure.setSelectedIndex(childStructureIdx);
                        interactiveStructureActionPerformed(null);
                    }
                    if (im==null) navigateToNextObjects(next, false, structureDisplay, setInteractiveStructure);
                }
            }
        }
    }
    private void setMongoBinDirectoryMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setMongoBinDirectoryMenuActionPerformed
        File[] f = Utils.chooseFiles("Choose Directory containing MongoDB Binary Files", null, FileChooser.FileChooserOption.FILE_OR_DIRECTORY, this);
        if (f.length==1 && f[0].exists() && f[0].isDirectory()) {
            PropertyUtils.set(PropertyUtils.MONGO_BIN_PATH, f[0].getAbsolutePath());
            updateMongoDBBinActions();
        }
    }//GEN-LAST:event_setMongoBinDirectoryMenuActionPerformed

    private void refreshExperimentListMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshExperimentListMenuItemActionPerformed
        populateExperimentList();
    }//GEN-LAST:event_refreshExperimentListMenuItemActionPerformed

    private void setSelectedExperimentMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setSelectedExperimentMenuItemActionPerformed
        String host = getHostName();
        String dbName = getSelectedExperiment();
        if (dbName==null || (this.db!=null && db.getDBName().equals(dbName))) unsetXP();
        else {
            setDBConnection(dbName, host);
            PropertyUtils.set(PropertyUtils.LAST_SELECTED_EXPERIMENT, dbName);
        }
    }//GEN-LAST:event_setSelectedExperimentMenuItemActionPerformed

    private void newXPMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newXPMenuItemActionPerformed
        String name = JOptionPane.showInputDialog("New XP name:");
        if (name==null) return;
        name = DBUtil.addPrefix(name, DBprefix);
        if (!Utils.isValid(name, false)) logger.error("Name should not contain special characters");
        else if (getDBNames().contains(name)) logger.error("XP name already exists");
        else {
            MorphiumMasterDAO db2 = new MorphiumMasterDAO(name);
            Experiment xp2 = new Experiment(name);
            xp2.setName(name);
            db2.setExperiment(xp2);
            this.setDBConnection(name, getHostName());
            populateExperimentList();
            if (this.db!=null) setSelectedExperiment(name);
        }
    }//GEN-LAST:event_newXPMenuItemActionPerformed

    private void deleteXPMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteXPMenuItemActionPerformed
        List<String> xps = getSelectedExperiments();
        if (xps==null || xps.isEmpty()) return;
        int response = JOptionPane.showConfirmDialog(this, "Delete Selected Experiment"+(xps.size()>1?"s":"")+" (all data will be lost)", "Confirm",
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (response == JOptionPane.NO_OPTION || response == JOptionPane.CLOSED_OPTION) {
        } else if (response == JOptionPane.YES_OPTION) {
            for (String xpName : xps) DBUtil.dropDatabase(xpName, getHostName());
            populateExperimentList();
            if (db!=null && xps.contains(db.getDBName())) unsetXP();
        }
    }//GEN-LAST:event_deleteXPMenuItemActionPerformed

    private void duplicateXPMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_duplicateXPMenuItemActionPerformed
        String name = JOptionPane.showInputDialog("New DB name:", getSelectedExperiment());
        name = DBUtil.addPrefix(name, DBprefix);
        if (!Utils.isValid(name, false)) logger.error("Name should not contain special characters");
        else if (getDBNames().contains(name)) logger.error("DB name already exists");
        else {
            MorphiumMasterDAO db1 = new MorphiumMasterDAO(getSelectedExperiment());
            MorphiumMasterDAO db2 = new MorphiumMasterDAO(name);
            Experiment xp2 = db1.getExperiment().duplicate();
            xp2.clearPositions();
            xp2.setName(name);
            db2.setExperiment(xp2);
            populateExperimentList();
        }
    }//GEN-LAST:event_duplicateXPMenuItemActionPerformed

    private void saveXPMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveXPMenuItemActionPerformed
        if (!checkConnection()) return;
        db.updateExperiment();
    }//GEN-LAST:event_saveXPMenuItemActionPerformed
    private String promptDir(String message, String def) {
        if (message==null) message = "Choose Directory";
        File outputFile = Utils.chooseFile(message, def, FileChooser.FileChooserOption.FILE_OR_DIRECTORY, this);
        if (outputFile ==null) return null;
        if (!outputFile.isDirectory()) outputFile=outputFile.getParentFile();
        return outputFile.getAbsolutePath();
    }
    private void exportWholeXPMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportWholeXPMenuItemActionPerformed
        String defDir = PropertyUtils.get(PropertyUtils.LAST_IO_DATA_DIR);
        String dir = promptDir("Choose output directory", defDir);
        if (dir==null) return;
        List<String> xpToExport = this.getSelectedExperiments();
        int count=0;
        for (String xp : xpToExport) {
            logger.info("Exporting whole XP : {}/{}", ++count, xpToExport.size());
            CommandExecuter.dumpDB(getHostName(), xp, dir, jsonFormatMenuItem.isSelected());
        }
        PropertyUtils.set(PropertyUtils.LAST_IO_DATA_DIR, dir);
    }//GEN-LAST:event_exportWholeXPMenuItemActionPerformed

    private void exportSelectedFieldsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportSelectedFieldsMenuItemActionPerformed
        if (!checkConnection()) return;
        String defDir = PropertyUtils.get(PropertyUtils.LAST_IO_DATA_DIR);
        String dir = promptDir("Choose output directory", defDir);
        if (dir==null) return;
        int[] sel  = getSelectedMicroscopyFields();
        String[] fNames = db.getExperiment().getPositionsAsString();
        String dbName = db.getDBName();
        String hostname = getHostName();
        int count = 0;
        for (int f : sel) {
            String cName = MorphiumObjectDAO.getCollectionName(fNames[f]);
            logger.info("Exporting: {}/{}", ++count, sel.length);
            CommandExecuter.dump(hostname, dbName, cName, dir, jsonFormatMenuItem.isSelected());
        }
        PropertyUtils.set(PropertyUtils.LAST_IO_DATA_DIR, dir);
    }//GEN-LAST:event_exportSelectedFieldsMenuItemActionPerformed

    private void exportXPConfigMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportXPConfigMenuItemActionPerformed
        if (!checkConnection()) return;
        String defDir = PropertyUtils.get(PropertyUtils.LAST_IO_DATA_DIR);
        String dir = promptDir("Choose output directory", defDir);
        if (dir==null) return;
        CommandExecuter.dump(getHostName(), db.getDBName(), "Experiment", dir, jsonFormatMenuItem.isSelected());
        PropertyUtils.set(PropertyUtils.LAST_IO_DATA_DIR, dir);
    }//GEN-LAST:event_exportXPConfigMenuItemActionPerformed

    private void importFieldsToCurrentExperimentMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importFieldsToCurrentExperimentMenuItemActionPerformed
        if (!checkConnection()) return;
        String defDir = PropertyUtils.get(PropertyUtils.LAST_IO_DATA_DIR);
        File[] selectedFiles = Utils.chooseFiles("Select directory containing exported files OR directly exported files", defDir, FileChooser.FileChooserOption.FILES_AND_DIRECTORIES, this);
        if (selectedFiles==null || selectedFiles.length==0) return;
        Map<String, String> fields = CommandExecuter.getObjectDumpedCollections(selectedFiles);
        if (fields.isEmpty()) {
            logger.warn("No fields found in directory");
            return;
        }
        boolean hasFieldsAlreadyPresent = false;
        boolean ignoreExisting = false;
        for (String f : fields.keySet()) {
            if (db.getExperiment().getPosition(f)!=null) {
                hasFieldsAlreadyPresent = true;
                break;
            }
        }
        if (hasFieldsAlreadyPresent) {
            String opt = true ? "Import and override existing data" : "Import and join with existing data";
            Object[] options = {opt, "Ignore existing positions"};
            String erase = true ? "Existing Data will be earsed before import, this may result in data loss.": "Existing data will not be earsed before import, this may result in data collapse and errors, check that imported data is disjoined from data that is already present.";
            int n = JOptionPane.showOptionDialog(this, "Some positions found in the directory are already present in the current experiment. "+erase, "Import Positions", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            ignoreExisting = n==1;
        }
        if (ignoreExisting) {
            Iterator<String> it = fields.keySet().iterator();
            while (it.hasNext()) { if (db.getExperiment().getPosition(it.next())!=null) it.remove();}
        }
        String dbName = db.getDBName();
        String hostname = getHostName();
        int count = 0;
        boolean fieldsCreated = false;
        for (String f : fields.keySet()) {
            logger.info("Importing: {}/{}, collection: {} file: {}", ++count, fields.size(), f, fields.get(f));
            boolean ok = CommandExecuter.restore(hostname, dbName, MorphiumObjectDAO.getCollectionName("")+f, fields.get(f), true);
            if (ok) {
                if (db.getExperiment().getPosition(f)==null) { // create entry
                    db.getExperiment().createPosition(f);
                    fieldsCreated=true;
                    logger.info("Position: {} was created. Run \"re-link images\" to set the input images", f);
                } else db.getDao(f).clearCache();
            }
        }
        if (fieldsCreated) {
            db.updateExperiment();
            populateActionMicroscopyFieldList();
        }
        loadObjectTrees();
        
        File f = Utils.getOneDir(selectedFiles);
        if (f!=null) PropertyUtils.set(PropertyUtils.LAST_IO_DATA_DIR, f.getAbsolutePath());
    }//GEN-LAST:event_importFieldsToCurrentExperimentMenuItemActionPerformed

    private void importConfigurationForSelectedPositionsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importConfigurationForSelectedPositionsMenuItemActionPerformed
        // TODO add your handling code here:
        // import vers une nouvelle db, read XP et override seulement la config des positions selectionnées sur l'xp courante
    }//GEN-LAST:event_importConfigurationForSelectedPositionsMenuItemActionPerformed

    private void importConfigurationForSelectedStructuresMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importConfigurationForSelectedStructuresMenuItemActionPerformed
        // TODO add your handling code here:
        // import vers une nouvelle db, read XP et override seulement la config des structure sel sur l'xp courante. Attention a check qu'il y ai le bon nombre de structures
    }//GEN-LAST:event_importConfigurationForSelectedStructuresMenuItemActionPerformed

    private void importNewExperimentMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importNewExperimentMenuItemActionPerformed
        String defDir = PropertyUtils.get(PropertyUtils.LAST_IO_DATA_DIR);
        String dir = promptDir("Select folder containing experiment folders", defDir);
        if (dir==null) return;
        File directory = new File(dir);
        // whole xp are located in sub directories
        FileFilter filter = new FileFilter() {
            @Override public boolean accept(File file) {
                if (file.isDirectory()) { // contains at least experiment collection
                    for (String fName : file.list()) {
                        if (fName.equals("Experiment.bson") || fName.equals("Experiment.json")) return true;
                    }
                } 
                return false;
            }
        };
        List<File> subDirs = new ArrayList<File>(Arrays.asList(directory.listFiles(filter)));
        if (filter.accept(directory)) subDirs.add(directory);
        List<String> dbNames = getDBNames();
        boolean someDBAlreadyExist = false;
        for (File f : subDirs) {
            if (dbNames.contains(DBUtil.addPrefix(f.getName(), DBprefix))) {
                someDBAlreadyExist=true;
                break;
            }
        }
        boolean ignoreExisting=false;
        if (someDBAlreadyExist) {
            Object[] options = {"Override existig experiments (data loss)", "Ignore existing experiments"};
            int n = JOptionPane.showOptionDialog(this, "Some experiments found in the directory are already present", "Import Whole Experiment", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            ignoreExisting = n==1;
        }
        if (ignoreExisting) {
            Iterator<File> it = subDirs.iterator();
            while (it.hasNext()) { if (dbNames.contains(it.next().getName())) it.remove();}
        }
        String hostname = getHostName();
        int count = 0;
        for (File f : subDirs) {
            logger.info("Importing XP: {}/{}", ++count, subDirs.size());
            CommandExecuter.restoreDB(hostname, DBUtil.addPrefix(f.getName(), DBprefix), f.getAbsolutePath());
        }
        populateExperimentList();
        PropertyUtils.set(PropertyUtils.LAST_IO_DATA_DIR, dir);
    }//GEN-LAST:event_importNewExperimentMenuItemActionPerformed

    private void importConfigToCurrentExperimentMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importConfigToCurrentExperimentMenuItemActionPerformed
        if (!checkConnection()) return;
        String defDir = PropertyUtils.get(PropertyUtils.LAST_IO_DATA_DIR);
        File outputFile = Utils.chooseFile("Select Experiment.bson of Experiment.json file (WARNING: current configuration will be lost)", defDir, FileChooser.FileChooserOption.FILE_OR_DIRECTORY, this);
        if (outputFile!=null && outputFile.getName().equals("Experiment.bson") || outputFile.getName().equals("Experiment.json")) {
            CommandExecuter.restore(getHostName(), db.getDBName(), "Experiment", outputFile.getAbsolutePath(), true);
            String dbName = db.getDBName();
            unsetXP();
            setDBConnection(dbName, getHostName());
            PropertyUtils.set(PropertyUtils.LAST_IO_DATA_DIR, outputFile.getAbsolutePath());
        }
        
    }//GEN-LAST:event_importConfigToCurrentExperimentMenuItemActionPerformed

    private void runSelectedActionsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runSelectedActionsMenuItemActionPerformed
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
        Task t = new Task(this.db.getDBName()).setActions(preProcess, segmentAndTrack, segmentAndTrack || trackOnly, runMeasurements).setStructures(selectedStructures).setPositions(microscopyFields);
        t.run();
        t.printErrors();
        /*boolean allStructures = selectedStructures.length==db.getExperiment().getStructureCount();
        boolean needToDeleteObjects = preProcess || reRunPreProcess || segmentAndTrack;
        boolean deleteAll =  needToDeleteObjects && allStructures && microscopyFields.length==db.getExperiment().getPositionCount();
        if (deleteAll) db.deleteAllObjects();
        boolean deleteAllField = needToDeleteObjects && allStructures && !deleteAll;
        logger.debug("Run actions: preProcess: {}, rePreProcess: {}, segmentAndTrack: {}, trackOnly: {}, runMeasurements: {}, need to delete objects: {}, delete all: {}, delete all by field: {}", preProcess, reRunPreProcess, segmentAndTrack, trackOnly, runMeasurements, needToDeleteObjects, deleteAll, deleteAllField);
        for (int f : microscopyFields) {
            String fieldName = db.getExperiment().getPosition(f).getName();
            if (deleteAllField) db.getDao(fieldName).deleteAllObjects();
            this.runAction(fieldName, preProcess, reRunPreProcess, segmentAndTrack, trackOnly, runMeasurements, needToDeleteObjects && !deleteAllField && !deleteAll);
            if (preProcess) db.updateExperiment(); // save field preProcessing configuration value @ each field
            db.getDao(fieldName).clearCache();
            db.getExperiment().getPosition(fieldName).flushImages(true, true);
        }
        */
        if (preProcess || reRunPreProcess || segmentAndTrack) this.reloadObjectTrees=true;
    }//GEN-LAST:event_runSelectedActionsMenuItemActionPerformed

    private void importImagesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importImagesMenuItemActionPerformed
        if (!checkConnection()) return;
        String defDir = PropertyUtils.get(PropertyUtils.LAST_IMPORT_IMAGE_DIR);
        File[] selectedFiles = Utils.chooseFiles("Choose images/directories to import", defDir, FileChooser.FileChooserOption.FILES_AND_DIRECTORIES, this);
        if (selectedFiles!=null) {
            Processor.importFiles(this.db.getExperiment(), true,  Utils.convertFilesToString(selectedFiles));
            File dir = Utils.getOneDir(selectedFiles);
            if (dir!=null) PropertyUtils.set(PropertyUtils.LAST_IMPORT_IMAGE_DIR, dir.getAbsolutePath());
            db.updateExperiment(); //stores imported fields
           
            populateActionMicroscopyFieldList();
            updateConfigurationTree();
        }
    }//GEN-LAST:event_importImagesMenuItemActionPerformed

    private void extractMeasurementMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_extractMeasurementMenuItemActionPerformed
        if (!checkConnection()) return;
        int[] selectedStructures = this.getSelectedStructures(true);
        String defDir = PropertyUtils.get(PropertyUtils.LAST_EXTRACT_MEASUREMENTS_DIR+"_"+db.getDBName(), PropertyUtils.get(PropertyUtils.LAST_EXTRACT_MEASUREMENTS_DIR));
        File outputDir = Utils.chooseFile("Choose directory", defDir, FileChooser.FileChooserOption.DIRECTORIES_ONLY, this);
        if (outputDir!=null) {
            String file = outputDir.getAbsolutePath()+File.separator+db.getDBName()+Utils.toStringArray(selectedStructures, "_", "", "_")+".xls";
            logger.info("measurements will be extracted to: {}", file);
            Map<Integer, String[]> keys = db.getExperiment().getAllMeasurementNamesByStructureIdx(MeasurementKeyObject.class, selectedStructures);
            DataExtractor.extractMeasurementObjects(db, file, getSelectedMicroscopyFieldNames(true), keys);
            if (outputDir!=null) {
                PropertyUtils.set(PropertyUtils.LAST_EXTRACT_MEASUREMENTS_DIR+"_"+db.getDBName(), outputDir.getAbsolutePath());
                PropertyUtils.set(PropertyUtils.LAST_EXTRACT_MEASUREMENTS_DIR, outputDir.getAbsolutePath());
            }
        }
    }//GEN-LAST:event_extractMeasurementMenuItemActionPerformed

    private void hostNameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_hostNameActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_hostNameActionPerformed

    private void jsonFormatMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jsonFormatMenuItemActionPerformed
        //PropertyUtils.set(PropertyUtils.EXPORT_FORMAT, this.jsonFormatMenuItem.isSelected()?"json":"bson");
    }//GEN-LAST:event_jsonFormatMenuItemActionPerformed

    private void bsonFormatMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bsonFormatMenuItemActionPerformed
        //PropertyUtils.set(PropertyUtils.EXPORT_FORMAT, this.jsonFormatMenuItem.isSelected()?"json":"bson");
    }//GEN-LAST:event_bsonFormatMenuItemActionPerformed

    private void eraseCollectionCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_eraseCollectionCheckboxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_eraseCollectionCheckboxActionPerformed

    private void deleteMeasurementsCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteMeasurementsCheckBoxActionPerformed
        PropertyUtils.set(PropertyUtils.DELETE_MEASUREMENTS, this.deleteMeasurementsCheckBox.isSelected());
    }//GEN-LAST:event_deleteMeasurementsCheckBoxActionPerformed

    private void runActionAllXPMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runActionAllXPMenuItemActionPerformed
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
        boolean deleteAll =  preProcess || reRunPreProcess || segmentAndTrack;
        logger.debug("Will run on XP: {} / Run actions: preProcess: {}, rePreProcess: {}, segmentAndTrack: {}, trackOnly: {}, runMeasurements: {}, delete all: {}", getSelectedExperiments(), preProcess, reRunPreProcess, segmentAndTrack, trackOnly, runMeasurements,  deleteAll);
        for (String xpName : getSelectedExperiments()) {
            setDBConnection(xpName, getHostName());
            if (db!=null) {
                if (deleteAll) db.deleteAllObjects();
                logger.debug("XP: {} / Run actions: preProcess: {}, rePreProcess: {}, segmentAndTrack: {}, trackOnly: {}, runMeasurements: {}, delete all: {}", xpName, preProcess, reRunPreProcess, segmentAndTrack, trackOnly, runMeasurements,  deleteAll);
                
                for (String fieldName : Utils.asList(actionMicroscopyFieldModel)) {
                    runAction(fieldName, preProcess, reRunPreProcess, segmentAndTrack, trackOnly, runMeasurements, !deleteAll);
                    if (preProcess) db.updateExperiment(); // save field preProcessing configuration value @ each field
                    db.getDao(fieldName).clearCache();
                    db.getExperiment().getPosition(fieldName).flushImages(true, true);
                }
            }
        }
    }//GEN-LAST:event_runActionAllXPMenuItemActionPerformed

    private void closeAllWindowsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeAllWindowsMenuItemActionPerformed
        ImageWindowManagerFactory.getImageManager().flush();
    }//GEN-LAST:event_closeAllWindowsMenuItemActionPerformed

    private void reloadSelectionsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadSelectionsButtonActionPerformed
        populateSelections();
    }//GEN-LAST:event_reloadSelectionsButtonActionPerformed

    private void createSelectionButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_createSelectionButtonActionPerformed
        if (!checkConnection()) return;
        String name = JOptionPane.showInputDialog("New Selection name:");
        if (!Utils.isValid(name, false)) {
            logger.error("Name should not contain special characters");
            return;
        }
        List<String> structures = Arrays.asList(db.getExperiment().getStructuresAsString());
        if (structures.contains(name)) {
            logger.error("Name should not be a Structure's name");
            return;
        }
        populateSelections();
        for (int i = 0; i<selectionModel.size(); ++i) if (selectionModel.get(i).getName().equals(name)) {
            logger.error("Selection name already exists");
            return;
        }
        Selection sel = new Selection(name, db);
        this.db.getSelectionDAO().store(sel);
        this.selectionModel.addElement(sel);

    }//GEN-LAST:event_createSelectionButtonActionPerformed

    private void testSplitButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_testSplitButtonActionPerformed
        if (!checkConnection()) return;
        List<StructureObject> selList = ImageWindowManagerFactory.getImageManager().getSelectedLabileObjects(null);
        if (selList.isEmpty()) logger.warn("Select at least one object to Split first!");
        else ManualCorrection.splitObjects(db, selList, false, true);
    }//GEN-LAST:event_testSplitButtonActionPerformed

    private void resetLinksButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetLinksButtonActionPerformed
        if (!checkConnection()) return;
        List<StructureObject> sel = ImageWindowManagerFactory.getImageManager().getSelectedLabileObjects(null);
        if (sel.isEmpty()) {
            logger.warn("Select at least one object to modify its links");
            return;
        }
        ManualCorrection.resetObjectLinks(db, sel, true);
    }//GEN-LAST:event_resetLinksButtonActionPerformed

    private void unlinkObjectsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_unlinkObjectsButtonActionPerformed
        if (!checkConnection()) return;
        List<StructureObject> sel = ImageWindowManagerFactory.getImageManager().getSelectedLabileObjects(null);
        if (sel.isEmpty()) {
            logger.warn("Select at least one object to modify its links");
            return;
        }
        ManualCorrection.modifyObjectLinks(db, sel, true, true);
    }//GEN-LAST:event_unlinkObjectsButtonActionPerformed

    private void linkObjectsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_linkObjectsButtonActionPerformed
        if (!checkConnection()) return;
        List<StructureObject> sel = ImageWindowManagerFactory.getImageManager().getSelectedLabileObjects(null);
        if (sel.isEmpty()) {
            logger.warn("Select at least one object to modify its links");
            return;
        }
        ManualCorrection.modifyObjectLinks(db, sel, false, true);
    }//GEN-LAST:event_linkObjectsButtonActionPerformed

    private void testManualSegmentationButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_testManualSegmentationButtonActionPerformed
        ManualCorrection.manualSegmentation(db, null, true);
    }//GEN-LAST:event_testManualSegmentationButtonActionPerformed

    private void manualSegmentButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_manualSegmentButtonActionPerformed
        ManualCorrection.manualSegmentation(db, null, false);
    }//GEN-LAST:event_manualSegmentButtonActionPerformed

    private void updateRoiDisplayButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_updateRoiDisplayButtonActionPerformed
        GUI.updateRoiDisplay(null);
    }//GEN-LAST:event_updateRoiDisplayButtonActionPerformed

    private void deleteObjectsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteObjectsButtonActionPerformed
        if (!checkConnection()) return;
        List<StructureObject> sel = ImageWindowManagerFactory.getImageManager().getSelectedLabileObjects(null);
        ManualCorrection.deleteObjects(db, sel, true);
    }//GEN-LAST:event_deleteObjectsButtonActionPerformed

    private void deleteObjectsButtonMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_deleteObjectsButtonMousePressed
        if (SwingUtilities.isRightMouseButton(evt)) {
            JPopupMenu menu = new JPopupMenu();
            Action delAfter = new AbstractAction("Delete All objects after first selected object") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    ManualCorrection.deleteAllObjectsFromFrame(db, true);
                    logger.debug("will delete all after");
                }
            };
            Action delBefore = new AbstractAction("Delete All objects before first selected object") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    ManualCorrection.deleteAllObjectsFromFrame(db, false);
                    logger.debug("will delete all after");
                }
            };
            menu.add(delAfter);
            menu.add(delBefore);
            menu.show(this.deleteObjectsButton, evt.getX(), evt.getY());
        }
    }//GEN-LAST:event_deleteObjectsButtonMousePressed

    private void selectAllObjectsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectAllObjectsActionPerformed
        getImageManager().displayAllObjects(null);
        //GUI.updateRoiDisplayForSelections(null, null);

    }//GEN-LAST:event_selectAllObjectsActionPerformed

    private void interactiveStructureActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_interactiveStructureActionPerformed
        if (!checkConnection()) return;
        getImageManager().setInteractiveStructure(interactiveStructure.getSelectedIndex());
    }//GEN-LAST:event_interactiveStructureActionPerformed

    private void previousTrackErrorButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_previousTrackErrorButtonActionPerformed
        if (!checkConnection()) return;
        navigateToNextObjects(false, false, -1, true);
    }//GEN-LAST:event_previousTrackErrorButtonActionPerformed

    private void mergeObjectsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mergeObjectsButtonActionPerformed
        if (!checkConnection()) return;
        List<StructureObject> selList = ImageWindowManagerFactory.getImageManager().getSelectedLabileObjects(null);
        if (selList.isEmpty()) logger.warn("Select at least two objects to Merge first!");
        else ManualCorrection.mergeObjects(db, selList, true);
    }//GEN-LAST:event_mergeObjectsButtonActionPerformed

    private void splitObjectsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_splitObjectsButtonActionPerformed
        List<StructureObject> selList = ImageWindowManagerFactory.getImageManager().getSelectedLabileObjects(null);
        if (selList.isEmpty()) logger.warn("Select at least one object to Split first!");
        else ManualCorrection.splitObjects(db, selList, true, false);
    }//GEN-LAST:event_splitObjectsButtonActionPerformed

    private void nextTrackErrorButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nextTrackErrorButtonActionPerformed
        if (!checkConnection()) return;
        navigateToNextObjects(true, false, -1, true);
    }//GEN-LAST:event_nextTrackErrorButtonActionPerformed

    private void selectAllTracksButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectAllTracksButtonActionPerformed
        ImageWindowManagerFactory.getImageManager().displayAllTracks(null);
        //GUI.updateRoiDisplayForSelections(null, null);
    }//GEN-LAST:event_selectAllTracksButtonActionPerformed

    private void trackStructureJCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_trackStructureJCBActionPerformed
        if (!checkConnection()) return;
        logger.debug("trackStructureJCBActionPerformed: selected index: {} action event: {}", trackStructureJCB.getSelectedIndex(), evt);
        this.setStructure(this.trackStructureJCB.getSelectedIndex());
    }//GEN-LAST:event_trackStructureJCBActionPerformed
    private void updateMongoDBBinActions() {
        boolean enableDump = false, enableRestore = false;
        String mPath = PropertyUtils.get(PropertyUtils.MONGO_BIN_PATH);
        if (mPath!=null) {
            File dir = new File(mPath);
            if (dir.exists() && dir.isDirectory()) {
                for (File f : dir.listFiles()) {
                    if(f.getName().startsWith("mongodump")) enableDump = true;
                    if(f.getName().startsWith("mongorestore")) enableRestore = true;
                }
            }
        }
        exportSubMenu.setEnabled(enableDump);
        importSubMenu.setEnabled(enableRestore);
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
    
    public List<String> getDBNames() {
        return DBUtil.getDBNames(getHostName(), DBprefix);
    }
    
    
    public static MasterDAO getDBConnection() {
        if (getInstance()==null) return null;
        return getInstance().db;
    }
    
    public void setInteractiveStructureIdx(int structureIdx) {
        if (interactiveStructure.getItemCount()<=structureIdx) logger.error("Error set interactive structure out of bounds: max: {}, current: {}, asked: {}", interactiveStructure.getItemCount(), interactiveStructure.getSelectedIndex(), structureIdx );
        interactiveStructure.setSelectedIndex(structureIdx);
        interactiveStructureActionPerformed(null);
    }
    
    public void setTrackStructureIdx(int structureIdx) {
        trackStructureJCB.setSelectedIndex(structureIdx);
        trackStructureJCBActionPerformed(null);
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
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new GUI().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel ControlPanel;
    private javax.swing.JScrollPane TimeJSP;
    private javax.swing.JScrollPane actionJSP;
    private javax.swing.JScrollPane actionMicroscopyFieldJSP;
    private javax.swing.JPanel actionPanel;
    private javax.swing.JScrollPane actionStructureJSP;
    private javax.swing.JRadioButtonMenuItem bsonFormatMenuItem;
    private javax.swing.JMenuItem closeAllWindowsMenuItem;
    private javax.swing.JScrollPane configurationJSP;
    private javax.swing.JPanel configurationPanel;
    private javax.swing.JButton createSelectionButton;
    private javax.swing.JPanel dataPanel;
    private javax.swing.JCheckBoxMenuItem deleteMeasurementsCheckBox;
    private javax.swing.JButton deleteObjectsButton;
    private javax.swing.JMenuItem deleteXPMenuItem;
    private javax.swing.JMenuItem duplicateXPMenuItem;
    private javax.swing.JCheckBoxMenuItem eraseCollectionCheckbox;
    private javax.swing.JList experimentList;
    private javax.swing.JMenu experimentMenu;
    private javax.swing.JMenuItem exportSelectedFieldsMenuItem;
    private javax.swing.JMenu exportSubMenu;
    private javax.swing.JMenuItem exportWholeXPMenuItem;
    private javax.swing.JMenuItem exportXPConfigMenuItem;
    private javax.swing.JMenuItem extractMeasurementMenuItem;
    private javax.swing.JTextField hostName;
    private javax.swing.JMenuItem importConfigToCurrentExperimentMenuItem;
    private javax.swing.JMenuItem importConfigurationForSelectedPositionsMenuItem;
    private javax.swing.JMenuItem importConfigurationForSelectedStructuresMenuItem;
    private javax.swing.JMenu importExportMenu;
    private javax.swing.JMenuItem importFieldsToCurrentExperimentMenuItem;
    private javax.swing.JMenuItem importImagesMenuItem;
    private javax.swing.JMenuItem importNewExperimentMenuItem;
    private javax.swing.JMenu importOptionsSubMenu;
    private javax.swing.JMenu importSubMenu;
    private javax.swing.JComboBox interactiveStructure;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenu jMenu2;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JPopupMenu.Separator jSeparator1;
    private javax.swing.JRadioButtonMenuItem jsonFormatMenuItem;
    private javax.swing.JButton linkObjectsButton;
    private javax.swing.JButton manualSegmentButton;
    private javax.swing.JButton mergeObjectsButton;
    private javax.swing.JList microscopyFieldList;
    private javax.swing.JMenu miscMenu;
    private javax.swing.JMenuItem newXPMenuItem;
    private javax.swing.JButton nextTrackErrorButton;
    private javax.swing.JMenu optionMenu;
    private javax.swing.JButton previousTrackErrorButton;
    private javax.swing.JMenuItem refreshExperimentListMenuItem;
    private javax.swing.JButton reloadSelectionsButton;
    private javax.swing.JButton resetLinksButton;
    private javax.swing.JMenuItem runActionAllXPMenuItem;
    private javax.swing.JList runActionList;
    private javax.swing.JMenu runMenu;
    private javax.swing.JMenuItem runSelectedActionsMenuItem;
    private javax.swing.JMenuItem saveXPMenuItem;
    private javax.swing.JButton selectAllObjects;
    private javax.swing.JButton selectAllTracksButton;
    private javax.swing.JScrollPane selectionJSP;
    private javax.swing.JList selectionList;
    private javax.swing.JPanel selectionPanel;
    private javax.swing.JMenuItem setMongoBinDirectoryMenu;
    private javax.swing.JMenuItem setSelectedExperimentMenuItem;
    private javax.swing.JButton splitObjectsButton;
    private javax.swing.JList structureList;
    private javax.swing.JTabbedPane tabs;
    private javax.swing.JButton testManualSegmentationButton;
    private javax.swing.JButton testSplitButton;
    private javax.swing.JPanel trackPanel;
    private javax.swing.JComboBox trackStructureJCB;
    private javax.swing.JPanel trackSubPanel;
    private javax.swing.JButton unlinkObjectsButton;
    private javax.swing.JButton updateRoiDisplayButton;
    // End of variables declaration//GEN-END:variables

    

    
}
