/*
 * Copyright (C) 2016 jollion
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
package boa.gui.selection;

import boa.gui.GUI;
import static boa.gui.GUI.logger;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageWindowManager;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.gui.objects.StructureObjectTreeGenerator;
import boa.gui.objects.TrackTreeController;
import boa.gui.objects.TrackTreeGenerator;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.Selection;
import dataStructure.objects.SelectionDAO;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import image.Image;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;
import utils.HashMapGetCreate;
import utils.Pair;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class SelectionUtils {
    public static Map<String, Color> colorsImageDisplay = new HashMap<String, Color>() {{
        put("Magenta", new Color(255, 0, 255, 120));
        put("Blue", new Color(0, 0, 255, 120));
        put("Cyan", new Color(0, 255, 255, 120));
        put("Green", new Color(0, 255, 0, 120));
    }};
    public static Map<String, Color> colors = new HashMap<String, Color>() {{
        put("Magenta", new Color(255, 0, 255));
        put("Blue", new Color(0, 0, 205));
        put("Cyan", new Color(0, 139, 139));
        put("Green", new Color(0, 100, 0));
    }};
    
    public static List<StructureObject> getStructureObjects(List<Selection> selections, String fieldName) {
        if (selections==null || selections.isEmpty()) return Collections.EMPTY_LIST;
        selections.removeIf(s -> s.getStructureIdx()!=selections.get(0).getStructureIdx());
        List<StructureObject> res=  new ArrayList<>();
        if (fieldName!=null) for (Selection s : selections) {
            if (s.getElements(fieldName)!=null) res.addAll(s.getElements(fieldName));
        } else for (Selection s : selections) {
            if (s.getAllElements()!=null) res.addAll(s.getAllElements());
        }
        return res;
    }
    
    public static Map<String, List<StructureObject>> getStructureObjects(List<Selection> selections) {
        if (selections==null || selections.isEmpty()) return Collections.EMPTY_MAP;
        selections.removeIf(s -> s.getStructureIdx()!=selections.get(0).getStructureIdx());
        HashMapGetCreate<String, List<StructureObject>> res=  new HashMapGetCreate<>(new HashMapGetCreate.ListFactory<>());
        for (Selection s : selections) {
            for (String p : s.getAllPositions()) res.getAndCreateIfNecessary(p).addAll(s.getElements(p));
        }
        return res;
    }
    public static Set<String> getPositions(List<Selection> selections) {
        Set<String> res = new HashSet<>();
        for (Selection s: selections) res.addAll(s.getAllPositions());
        return res;
    }
    public static String getNextPosition(List<Selection> selections, String position, boolean next) {
        List<String> p = new ArrayList<>(getPositions(selections));
        Collections.sort(p);
        logger.debug("getNext pos: {}, cur: {}", p, position);
        int idx = position ==null ? -1 : Collections.binarySearch(p, position);
        if (idx==-1) {
            if (next) return p.get(0);
            else return null;
        } else if (idx<0) {
            idx = -idx-1;
            if (!next) {
                if (idx>0) idx--;
                else return null;
            }
        } else {
            if (next) {
                if (idx==p.size()-1) return null;
                else idx = p.size()-1;
            } else {
                if (idx>0) idx--;
                else return null;
            }
        }
        return p.get(idx);
    }
    
    public static Selection getSelection(MasterDAO db, String name, boolean createIfNonExisting) {
        Selection res = db.getSelectionDAO().getObject(name);
        if (res==null && createIfNonExisting) res = new Selection(name, db);
        return res;
    }
    
    public static void displayObjects(Selection s, ImageObjectInterface i) {
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        if (i==null) i = iwm.getCurrentImageObjectInterface();
        if (i!=null) {
            String fieldName = i.getParent().getFieldName();
            Set<StructureObject> objects = s.getElements(fieldName);
            if (objects!=null) {
                iwm.displayObjects(null, i.pairWithOffset(objects), s.getColor(true), false, false);
            }
        }
    }
    
    public static void hideObjects(Selection s, ImageObjectInterface i) {
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        if (i==null) i = iwm.getCurrentImageObjectInterface();
        if (i!=null) {
            String fieldName = i.getParent().getFieldName();
            Set<StructureObject> objects = s.getElements(fieldName);
            if (objects!=null) {
                iwm.hideObjects(null, i.pairWithOffset(objects), false);
            }
        }
    }
    public static void displayTracks(Selection s, ImageObjectInterface i) {
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        if (i==null) i = iwm.getCurrentImageObjectInterface();
        if (i!=null) {
            String fieldName = i.getParent().getFieldName();
            Set<StructureObject> tracks = s.getTrackHeads(fieldName);
            if (tracks==null) return;
            for (StructureObject trackHead : tracks) {
                List<StructureObject> track = StructureObjectUtils.getTrack(trackHead, true);
                iwm.displayTrack(null, i, i.pairWithOffset(track), s.getColor(true), false);
            }
        }
    }
    public static void hideTracks(Selection s, ImageObjectInterface i) {
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        if (i==null)  i = iwm.getCurrentImageObjectInterface();
        if (i!=null) {
            String fieldName = i.getParent().getFieldName();
            Set<StructureObject> tracks = s.getTrackHeads(fieldName);
            if (tracks==null) return;
            iwm.hideTracks(null, i, tracks, false);
        }
    }
        
    public static void setMouseAdapter(final JList list) {
        list.addMouseListener( new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = list.locationToIndex(e.getPoint());
                    if (!list.isSelectedIndex(row)) list.setSelectedIndex(row);
                    //logger.debug("right button on row: {}, ctrl {} ctrl", row, ctrl);
                    if (list.isSelectedIndex(row)) {
                        JPopupMenu menu = generateMenu(list);
                        menu.show(list, e.getX(), e.getY());
                    }
                }
                GUI.setNavigationButtonNames(list.getSelectedIndex()>=0);
            }
        });
    }
    
    private static JPopupMenu generateMenu(final JList list) {
        JPopupMenu menu = new JPopupMenu("");
        final List<Selection> selectedValues = list.getSelectedValuesList();
        int dispObjects=0;
        int dispTracks = 0;
        int highTracks = 0;
        for (Selection s : selectedValues) {
            if (s.isDisplayingObjects()) dispObjects++;
            if (s.isDisplayingTracks()) dispTracks++;
            if (s.isHighlightingTracks()) highTracks++;
        }
        final JCheckBoxMenuItem displayObjects = new JCheckBoxMenuItem("Display Objects");
        final SelectionDAO dao = GUI.getDBConnection().getSelectionDAO();
        displayObjects.setSelected(dispObjects==selectedValues.size());
        displayObjects.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (selectedValues.isEmpty()) return;
                for (Selection s : selectedValues ) {
                    s.setIsDisplayingObjects(displayObjects.isSelected());
                    dao.store(s); // optimize if necessary -> update
                }
                GUI.updateRoiDisplayForSelections(null, null);
            }
        });
        menu.add(displayObjects);
        
        final JCheckBoxMenuItem displayTracks = new JCheckBoxMenuItem("Display Tracks");
        displayTracks.setSelected(dispTracks==selectedValues.size());
        displayTracks.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (selectedValues.isEmpty()) return;
                for (Selection s : selectedValues ) {
                    s.setIsDisplayingTracks(displayTracks.isSelected());
                    dao.store(s); // optimize if necessary -> update
                }
                GUI.updateRoiDisplayForSelections(null, null);
            }
        });
        menu.add(displayTracks);
        
        final JCheckBoxMenuItem highlightTracks = new JCheckBoxMenuItem("Highlight in Track-Tree");
        highlightTracks.setSelected(highTracks==selectedValues.size());
        highlightTracks.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (selectedValues.isEmpty()) return;
                //Set<Selection> switched = new HashSet<Selection>(selectedValues.size());
                for (Selection s : selectedValues ) {
                    //if (s.isHighlightingTracks()!=highlightTracks.isSelected()) switched.add(s);
                    s.setHighlightingTracks(highlightTracks.isSelected());
                    dao.store(s); // optimize if necessary -> update
                }
                GUI.getInstance().resetSelectionHighlight();
            }
        });
        menu.add(highlightTracks);
        
        menu.add(new JSeparator());
        JMenu colorMenu = new JMenu("Set Color");
        for (String s : colors.keySet()) {
            final String colorName = s;
            JMenuItem color = new JMenuItem(s);
            colorMenu.add(color);
            color.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (selectedValues.isEmpty()) return;
                    for (Selection s : selectedValues ) {
                        s.setColor(colorName);
                        
                        dao.store(s); // optimize if necessary -> update
                        if (s.isDisplayingObjects()) {
                            hideObjects(s, null);
                            displayObjects(s, null);
                        }
                        if (s.isDisplayingTracks()) {
                            hideTracks(s, null);
                            displayTracks(s, null);
                        }
                    }
                    list.updateUI();
                }
            });
        }
        menu.add(colorMenu);
        menu.add(new JSeparator());
        JMenuItem add = new JMenuItem("Add objects selected on Current Image");
        add.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (selectedValues.isEmpty()) return;
                List<StructureObject> sel = ImageWindowManagerFactory.getImageManager().getSelectedLabileObjects(null);
                for (Selection s : selectedValues ) {
                    int[] structureIdx = s.getStructureIdx()==-1 ? new int[0] : new int[]{s.getStructureIdx()};
                    List<StructureObject> objects = new ArrayList<StructureObject>(sel);
                    StructureObjectUtils.keepOnlyObjectsFromSameStructureIdx(sel, structureIdx);
                    s.addElements(objects);
                    dao.store(s);
                }
                list.updateUI();
                GUI.updateRoiDisplayForSelections(null, null);
            }
        });
        menu.add(add);
        
        JMenuItem remove = new JMenuItem("Remove objects selected on Current Image");
        remove.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (selectedValues.isEmpty()) return;
                List<StructureObject> sel = ImageWindowManagerFactory.getImageManager().getSelectedLabileObjects(null);
                for (Selection s : selectedValues ) {
                    s.removeElements(sel);
                    dao.store(s);
                }
                GUI.updateRoiDisplayForSelections(null, null);
                list.updateUI();
            }
        });
        menu.add(remove);
        
        
        JMenuItem addObjectTree = new JMenuItem("Add objects selected in ObjectTree");
        addObjectTree.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (selectedValues.isEmpty()) return;
                List<StructureObject> sel = GUI.getInstance().getObjectTree().getSelectedObjects(false);
                for (Selection s : selectedValues ) {
                    int[] structureIdx = s.getStructureIdx()==-1 ? new int[0] : new int[]{s.getStructureIdx()};
                    List<StructureObject> objects = new ArrayList<StructureObject>(sel);
                    StructureObjectUtils.keepOnlyObjectsFromSameStructureIdx(sel, structureIdx);
                    s.addElements(objects);
                    dao.store(s);
                }
                list.updateUI();
                GUI.updateRoiDisplayForSelections(null, null);
            }
        });
        menu.add(addObjectTree);
        
        JMenuItem removeObjectTree = new JMenuItem("Remove objects selected in ObjectTree");
        removeObjectTree.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (selectedValues.isEmpty()) return;
                List<StructureObject> sel = GUI.getInstance().getObjectTree().getSelectedObjects(false);
                for (Selection s : selectedValues ) {
                    s.removeElements(sel);
                    dao.store(s);
                }
                GUI.updateRoiDisplayForSelections(null, null);
                list.updateUI();
            }
        });
        menu.add(removeObjectTree);
        
        JMenuItem clear = new JMenuItem("Clear");
        clear.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (selectedValues.isEmpty()) return;
                for (Selection s : selectedValues ) {
                    s.clear();
                    dao.store(s);
                }
                list.updateUI();
                GUI.updateRoiDisplayForSelections(null, null);
            }
        });
        menu.add(clear);
        menu.add(new JSeparator());
        JMenuItem delete = new JMenuItem("Delete Selection");
        delete.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (selectedValues.isEmpty()) return;
                DefaultListModel<Selection> model = (DefaultListModel<Selection>)list.getModel();
                for (Selection s : selectedValues ) dao.delete(s);
                for (Selection s : selectedValues) model.removeElement(s);
                list.updateUI();
                GUI.updateRoiDisplayForSelections(null, null);
            }
        });
        menu.add(delete);
        return menu;
    }
    
}
