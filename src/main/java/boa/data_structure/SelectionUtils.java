/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.data_structure;

import boa.ui.GUI;
import static boa.ui.GUI.logger;
import boa.gui.image_interaction.InteractiveImage;
import boa.gui.image_interaction.ImageWindowManager;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.gui.objects.StructureObjectTreeGenerator;
import boa.gui.objects.TrackTreeController;
import boa.gui.objects.TrackTreeGenerator;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.ObjectDAO;
import boa.data_structure.Selection;
import boa.data_structure.dao.SelectionDAO;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.image.BoundingBox;
import boa.image.Image;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;
import boa.utils.HashMapGetCreate;
import boa.utils.Pair;
import boa.utils.Utils;

/**
 *
 * @author Jean Ollion
 */
public class SelectionUtils {
    public static Map<String, Color> colorsImageDisplay = new HashMap<String, Color>() {{
        put("Magenta", new Color(255, 0, 255, 255));
        put("Blue", new Color(0, 0, 255, 255));
        put("Cyan", new Color(0, 255, 255, 255));
        put("Green", new Color(0, 255, 0, 255));
        put("Grey", new Color(192, 192, 192, 120));
        put("Yellow", new Color(204, 204, 0, 255));
        put("Orange", new Color(255, 140, 0, 255));
    }};
    public static Map<String, Color> colors = new HashMap<String, Color>() {{
        put("Magenta", new Color(255, 0, 255));
        put("Blue", new Color(0, 0, 205));
        put("Cyan", new Color(0, 139, 139));
        put("Green", new Color(0, 100, 0));
        put("Grey", new Color(192, 192, 192));
        put("Yellow", new Color(204, 204, 0));
        put("Orange", new Color(255, 140, 0));
    }};
    
    public static boolean validSelectionName(MasterDAO db, String name) {
        if (!Utils.isValid(name, false)) {
            logger.error("Name should not contain special characters");
            return false;
        }
        List<String> structures = Arrays.asList(db.getExperiment().getStructuresAsString());
        if (structures.contains(name)) {
            logger.error("Name should not be a Structure's name");
            return false;
        }
        if (db.getSelectionDAO()==null) return false;
        List<Selection> sel = db.getSelectionDAO().getSelections();
        return !Utils.transform(sel, s->s.getName()).contains(name);
    }
    
    public static List<StructureObject> getStructureObjects(InteractiveImage i, List<Selection> selections) {
        if (i==null) ImageWindowManagerFactory.getImageManager().getCurrentImageObjectInterface();
        if (i==null) return Collections.EMPTY_LIST;
        String fieldName = i.getParent().getPositionName();
        if (selections==null || selections.isEmpty()) return Collections.EMPTY_LIST;
        selections.removeIf(s -> s.getStructureIdx()!=selections.get(0).getStructureIdx());
        List<String> allStrings=  new ArrayList<>();
        for (Selection s : selections) allStrings.addAll(s.getElementStrings(fieldName));
        return Pair.unpairKeys(filterPairs(i.getObjects(), allStrings));
    }
    public static Selection union(String name, Collection<Selection> selections) {
        if (selections.isEmpty()) return new Selection();
        Selection model = selections.iterator().next();
        selections.removeIf(s->s.getStructureIdx()!=model.getStructureIdx());
        HashMapGetCreate<String, Set<String>> elByPos = new HashMapGetCreate(new HashMapGetCreate.SetFactory());
        for (Selection sel : selections) {
            for (String pos : sel.getAllPositions()) elByPos.getAndCreateIfNecessary(pos).addAll(sel.getElementStrings(pos));
        }
        Selection res = new Selection(name,model.getStructureIdx(), model.getMasterDAO()); //"union:"+Utils.toStringList(selections, s->s.getName())
        for (Entry<String, Set<String>> e : elByPos.entrySet()) res.addElements(e.getKey(), e.getValue());
        return res;
    }
    public static Selection intersection(String name, Collection<Selection> selections) {
        if (selections.isEmpty()) return new Selection();
        Selection model = selections.iterator().next();
        selections.removeIf(s->s.getStructureIdx()!=model.getStructureIdx());
        Set<String> allPos = new HashSet<>();
        allPos.addAll(model.getAllPositions());
        for (Selection s : selections) allPos.retainAll(s.getAllPositions());
        HashMapGetCreate<String, Set<String>> elByPos = new HashMapGetCreate(new HashMapGetCreate.SetFactory());
        for (String p : allPos) elByPos.put(p, new HashSet<>(model.getElementStrings(p)));
        for (Selection s : selections) {
            if (s.equals(model)) continue;
            for (String p : allPos) elByPos.get(p).retainAll(s.getElementStrings(p));
        }
        Selection res = new Selection(name,model.getStructureIdx(), model.getMasterDAO()); //"intersection:"+Utils.toStringList(selections, s->s.getName())
        for (Entry<String, Set<String>> e : elByPos.entrySet()) res.addElements(e.getKey(), e.getValue());
        return res;
    }
    
    public static List<String> getElements(List<Selection> selections, String fieldName) {
        if (selections==null || selections.isEmpty()) return Collections.EMPTY_LIST;
        selections.removeIf(s -> s.getStructureIdx()!=selections.get(0).getStructureIdx());
        List<String> res=  new ArrayList<>();
        if (fieldName!=null) for (Selection s : selections) {
            res.addAll(s.getElementStrings(fieldName));
        } else for (Selection s : selections) {
            if (s.getAllElementStrings()!=null) res.addAll(s.getAllElementStrings());
        }
        return res;
    }
    
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
    public static String getNextPosition(Selection selection, String position, boolean next) {
        List<String> p = new ArrayList<>(selection.getAllPositions());
        Collections.sort(p);
        int idx = position ==null ? -1 : Collections.binarySearch(p, position);
        logger.debug("getNext pos: {}, cur: {}, idx: {}", p, position, idx);
        if (idx==-1) {
            if (next) return p.get(0);
            else return null;
        } else if (idx<0) {
            idx = -idx-1;
            if (!next) {
                if (idx>0) idx--;
                else return null;
            }
            if (idx>=p.size()) return next ? p.get(0) : p.get(p.size()-1);
        } else {
            if (next) {
                if (idx==p.size()-1) return null;
                else idx += 1;
            } else {
                if (idx>0) idx--;
                else return null;
            }
        }
        return p.get(idx);
    }
    public static Collection<Pair<StructureObject, BoundingBox>> filterPairs(List<Pair<StructureObject, BoundingBox>> objects, Collection<String> indices) {
        //Map<String, Pair<StructureObject, BoundingBox>> map = new HashMap<>(objects.size());
        //for (Pair<StructureObject, BoundingBox> o : objects) map.put(Selection.indicesString(o.key), o);
        Map<String, Pair<StructureObject, BoundingBox>> map = objects.stream().collect(Collectors.toMap(o->Selection.indicesString(o.key), o->o));
        map.keySet().retainAll(indices);
        return map.values();
    }
    public static Collection<StructureObject> filter(List<StructureObject> objects, Collection<String> indices) {
        //Map<String, StructureObject> map = new HashMap<>(objects.size());
        //for (StructureObject o : objects) map.put(Selection.indicesString(o), o);
        Map<String, StructureObject> map = objects.stream().collect(Collectors.toMap(o->Selection.indicesString(o), o->o));
        map.keySet().retainAll(indices);
        return map.values();
    }
    public static List<StructureObject> getParents(Selection sel, String position, MasterDAO db) {
        List<String> parentStrings = Utils.transform(sel.getElementStrings(position), s->Selection.getParent(s));
        Utils.removeDuplicates(parentStrings, false);
        return new ArrayList<>(SelectionUtils.filter(StructureObjectUtils.getAllObjects(db.getDao(position), db.getExperiment().getStructure(sel.getStructureIdx()).getParentStructure()), parentStrings));
    }
    public static List<StructureObject> getParentTrackHeads(Selection sel, String position, MasterDAO db) {
        List<StructureObject> parents = SelectionUtils.getParents(sel, position, db);
        parents = Utils.transform(parents, o -> o.getTrackHead());
        Utils.removeDuplicates(parents, false);
        return parents;
    }
    public static List<StructureObject> getParents(Selection sel, String position, int parentStructureIdx, MasterDAO db) {
        if (!(db.getExperiment().isChildOf(parentStructureIdx, sel.getStructureIdx())||parentStructureIdx==sel.getStructureIdx())) return Collections.EMPTY_LIST;
        int[] path = db.getExperiment().getPathToStructure(parentStructureIdx, sel.getStructureIdx());
        List<String> parentStrings = parentStructureIdx!=sel.getStructureIdx()?Utils.transform(sel.getElementStrings(position), s->Selection.getParent(s, path.length)):new ArrayList<>(sel.getElementStrings(position));
        Utils.removeDuplicates(parentStrings, false);
        List<StructureObject> allObjects = StructureObjectUtils.getAllObjects(db.getDao(position), parentStructureIdx);
        logger.debug("get Parents: path: {}, parent strings: {}, allObjects: {}", path, parentStrings.size(), allObjects.size());
        return new ArrayList<>(SelectionUtils.filter(allObjects, parentStrings));
    }
    public static List<StructureObject> getParentTrackHeads(Selection sel, String position, int parentStructureIdx, MasterDAO db) {
        List<StructureObject> parents = SelectionUtils.getParents(sel, position, parentStructureIdx, db);
        //logger.debug("parents: {}", parents.size());
        parents = Utils.transform(parents, o -> o.getTrackHead());
        Utils.removeDuplicates(parents, false);
        return parents;
    }
    public static InteractiveImage fixIOI(InteractiveImage i, int structureIdx) {
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        if (i!=null && i.getChildStructureIdx()!=structureIdx) {
            Image im = iwm.getImage(i);
            i = iwm.getImageObjectInterface(im, structureIdx);
        }
        if (i==null) i = iwm.getImageObjectInterface(null, structureIdx);
        return i;
    }
    public static void displayObjects(Selection s, InteractiveImage i) {
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        i = fixIOI(i, s.getStructureIdx());
        if (i!=null) {
            Collection<Pair<StructureObject, BoundingBox>> objects = filterPairs(i.getObjects(), s.getElementStrings(StructureObjectUtils.getPositions(i.getParents())));
            //Set<StructureObject> objects = s.getElements(StructureObjectUtils.getPositions(i.getParents()));
            //logger.debug("disp objects: #positions: {}, #objects: {}", StructureObjectUtils.getPositions(i.getParents()).size(), objects.size() );
            if (objects!=null) {
                //iwm.displayObjects(null, i.pairWithOffset(objects), s.getColor(true), false, false);
                iwm.displayObjects(null, objects, s.getColor(true), false, false);
            }
        }
    }
    
    public static void hideObjects(Selection s, InteractiveImage i) {
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        i = fixIOI(i, s.getStructureIdx());
        if (i!=null) {
            //Set<StructureObject> objects = s.getElements(StructureObjectUtils.getPositions(i.getParents()));
            Collection<Pair<StructureObject, BoundingBox>> objects = filterPairs(i.getObjects(), s.getElementStrings(StructureObjectUtils.getPositions(i.getParents())));
            if (objects!=null) {
                iwm.hideObjects(null, objects, false);
                //iwm.hideObjects(null, i.pairWithOffset(objects), false);
            }
        }
    }
    public static void displayTracks(Selection s, InteractiveImage i) {
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        i = fixIOI(i, s.getStructureIdx());
        if (i!=null) {
            Collection<Pair<StructureObject, BoundingBox>> objects = filterPairs(i.getObjects(), s.getElementStrings(StructureObjectUtils.getPositions(i.getParents())));
            List<StructureObject> tracks = Pair.unpairKeys(objects);
            tracks.removeIf(o->!o.isTrackHead());
            if (tracks.isEmpty()) return;
            for (StructureObject trackHead : tracks) {
                List<StructureObject> track = StructureObjectUtils.getTrack(trackHead, true);
                iwm.displayTrack(null, i, i.pairWithOffset(track), s.getColor(true), false);
            }
        }
    }
    public static void hideTracks(Selection s, InteractiveImage i) {
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        i = fixIOI(i, s.getStructureIdx());
        if (i!=null) {
            Collection<Pair<StructureObject, BoundingBox>> objects = filterPairs(i.getObjects(), s.getElementStrings(StructureObjectUtils.getPositions(i.getParents())));
            List<StructureObject> tracks = Pair.unpairKeys(objects);
            tracks.removeIf(o->!o.isTrackHead());
            if (tracks.isEmpty()) return;
            iwm.hideTracks(null, i, tracks, false);
        }
    }
    
    public static void displaySelection(Selection s, int parentStructureIdx, int displayStructureIdx) {
        // get all parent & create pseudo-track
        HashSet<StructureObject> parents = new HashSet<>();
        if (parentStructureIdx>=-1) for (StructureObject o : s.getAllElements()) parents.add(o.getParent(parentStructureIdx));
        else for (StructureObject o : s.getAllElements()) parents.add(o.getParent());
        List<StructureObject> parentList = new ArrayList<>(parents);
        Collections.sort(parentList);
        
        InteractiveImage i = ImageWindowManagerFactory.getImageManager().getImageTrackObjectInterface(parentList, s.getStructureIdx());
        ImageWindowManagerFactory.getImageManager().addImage(i.generatemage(displayStructureIdx, true), i, displayStructureIdx, true);
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
        final List<Selection> allSelections = Utils.asList(list.getModel());
        int dispObjects=0;
        int dispTracks = 0;
        int highTracks = 0;
        int addObjects0 = 0;
        int addObjects1 = 0;
        for (Selection s : selectedValues) {
            if (s.isDisplayingObjects()) dispObjects++;
            if (s.isDisplayingTracks()) dispTracks++;
            if (s.isHighlightingTracks()) highTracks++;
            if (s.isActive(0)) addObjects0++;
            if (s.isActive(1)) addObjects1++;
        }
        final SelectionDAO dao = GUI.getDBConnection().getSelectionDAO();
        if (selectedValues.size()==1) {
            final JCheckBoxMenuItem showImage = new JCheckBoxMenuItem("Display Selection as an Image");
            showImage.addActionListener((ActionEvent e) -> {
                SelectionUtils.displaySelection(selectedValues.get(0), -2, ImageWindowManagerFactory.getImageManager().getInteractiveStructure());
            });
            menu.add(showImage);
        }
        final JCheckBoxMenuItem displayObjects = new JCheckBoxMenuItem("Display Objects");
        displayObjects.setSelected(dispObjects==selectedValues.size());
        displayObjects.addActionListener((ActionEvent e) -> {
            if (selectedValues.isEmpty()) return;
            for (Selection s : selectedValues ) {
                s.setIsDisplayingObjects(displayObjects.isSelected());
                //dao.store(s); // optimize if necessary -> update
            }
            GUI.updateRoiDisplayForSelections(null, null);
            GUI.getInstance().updateSelectionListUI();
        });
        menu.add(displayObjects);
        
        final JCheckBoxMenuItem displayTracks = new JCheckBoxMenuItem("Display Tracks");
        displayTracks.setSelected(dispTracks==selectedValues.size());
        displayTracks.addActionListener((ActionEvent e) -> {
            if (selectedValues.isEmpty()) return;
            for (Selection s : selectedValues ) {
                s.setIsDisplayingTracks(displayTracks.isSelected());
                //dao.store(s); // optimize if necessary -> update
            }
            GUI.updateRoiDisplayForSelections(null, null);
            GUI.getInstance().updateSelectionListUI();
        });
        menu.add(displayTracks);
        
        final JCheckBoxMenuItem highlightTracks = new JCheckBoxMenuItem("Highlight in Track-Tree");
        highlightTracks.setSelected(highTracks==selectedValues.size());
        highlightTracks.addActionListener((ActionEvent e) -> {
            if (selectedValues.isEmpty()) return;
            //Set<Selection> switched = new HashSet<Selection>(selectedValues.size());
            for (Selection s : selectedValues ) {
                //if (s.isHighlightingTracks()!=highlightTracks.isSelected()) switched.add(s);
                s.setHighlightingTracks(highlightTracks.isSelected());
                //dao.store(s); // optimize if necessary -> update
            }
            GUI.getInstance().resetSelectionHighlight();
            GUI.getInstance().updateSelectionListUI();
        });
        menu.add(highlightTracks);
        final JCheckBoxMenuItem navigateMI = new JCheckBoxMenuItem("Enable Navigation");
        if (selectedValues.size()==1) navigateMI.setSelected(selectedValues.get(0).isNavigate());
        navigateMI.addActionListener((ActionEvent e) -> {
            if (selectedValues.isEmpty()) return;
            if (selectedValues.size()==1) {
                selectedValues.get(0).setNavigate(navigateMI.isSelected());
                for (Selection s : allSelections ) if (s!=selectedValues.get(0)) s.setNavigate(false);
            } else for (Selection s : allSelections ) s.setNavigate(false);
            GUI.getInstance().updateSelectionListUI();
        });
        menu.add(navigateMI);
        final JCheckBoxMenuItem addObjects0MI = new JCheckBoxMenuItem("Active Selection Group 0");
        addObjects0MI.setSelected(addObjects0==selectedValues.size());
        addObjects0MI.addActionListener((ActionEvent e) -> { 
            for (Selection s : selectedValues ) s.setActive(addObjects0MI.isSelected()?0:-1);
            GUI.getInstance().updateSelectionListUI();
        });
        menu.add(addObjects0MI);
        final JCheckBoxMenuItem addObjects1MI = new JCheckBoxMenuItem("Active Selection Group 1");
        addObjects1MI.setSelected(addObjects1==selectedValues.size());
        addObjects1MI.addActionListener((ActionEvent e) -> {
            for (Selection s : selectedValues ) s.setActive(addObjects1MI.isSelected()?1:-1);
            GUI.getInstance().updateSelectionListUI();
        });
        menu.add(addObjects1MI);
        menu.add(new JSeparator());
        JMenu colorMenu = new JMenu("Set Color");
        for (String s : colors.keySet()) {
            final String colorName = s;
            JMenuItem color = new JMenuItem(s);
            colorMenu.add(color);
            color.addActionListener((ActionEvent e) -> {
                if (selectedValues.isEmpty()) return;
                for (Selection s1 : selectedValues) {
                    s1.setColor(colorName);
                    dao.store(s1); // optimize if necessary -> update
                    if (s1.isDisplayingObjects()) {
                        hideObjects(s1, null);
                        displayObjects(s1, null);
                    }
                    if (s1.isDisplayingTracks()) {
                        hideTracks(s1, null);
                        displayTracks(s1, null);
                    }
                }
                list.updateUI();
            });
        }
        menu.add(colorMenu);
        menu.add(new JSeparator());
        JMenuItem add = new JMenuItem("Add objects selected on active Kymograph");
        add.addActionListener((ActionEvent e) -> {
            if (selectedValues.isEmpty()) return;
            addCurrentObjectsToSelections(selectedValues, dao);
            list.updateUI();
            GUI.updateRoiDisplayForSelections(null, null);
        });
        menu.add(add);
        
        JMenuItem remove = new JMenuItem("Remove objects selected on active Kymograph");
        remove.addActionListener((ActionEvent e) -> {
            if (selectedValues.isEmpty()) return;
            removeCurrentObjectsFromSelections(selectedValues, dao);
            GUI.updateRoiDisplayForSelections(null, null);
            list.updateUI();
        });
        menu.add(remove);
        
        JMenuItem removeFromParent = new JMenuItem("Remove all Objects from active Kymograph");
        removeFromParent.addActionListener((ActionEvent e) -> {
            if (selectedValues.isEmpty()) return;
            removeAllCurrentImageObjectsFromSelections(selectedValues, dao);
            GUI.updateRoiDisplayForSelections(null, null);
            list.updateUI();
        });
        menu.add(removeFromParent);
        
        JMenuItem clear = new JMenuItem("Clear");
        clear.addActionListener((ActionEvent e) -> {
            if (selectedValues.isEmpty()) return;
            for (Selection s : selectedValues ) {
                s.clear();
                dao.store(s);
            }
            list.updateUI();
            GUI.updateRoiDisplayForSelections(null, null);
        });
        menu.add(clear);
        
        JMenuItem delete = new JMenuItem("Delete Selection");
        delete.addActionListener((ActionEvent e) -> {
            if (selectedValues.isEmpty()) return;
            DefaultListModel<Selection> model = (DefaultListModel<Selection>)list.getModel();
            for (Selection s : selectedValues ) dao.delete(s);
            for (Selection s : selectedValues) model.removeElement(s);
            list.updateUI();
            GUI.updateRoiDisplayForSelections(null, null);
        });
        menu.add(delete);
        menu.add(new JSeparator());
        if (selectedValues.size()>=1) {
            JMenuItem union = new JMenuItem("Union");
            union.addActionListener((ActionEvent e) -> {
                String name = JOptionPane.showInputDialog("Union Selection name:");
                if (SelectionUtils.validSelectionName(selectedValues.get(0).getMasterDAO(), name)) {
                    Selection unionSel = SelectionUtils.union(name, selectedValues);
                    unionSel.getMasterDAO().getSelectionDAO().store(unionSel);
                    GUI.getInstance().populateSelections();
                }
            });
            menu.add(union);
        }
        if (selectedValues.size()>1) {
            JMenuItem union = new JMenuItem("Intersection");
            union.addActionListener((ActionEvent e) -> {
                String name = JOptionPane.showInputDialog("Union Selection name:");
                if (SelectionUtils.validSelectionName(selectedValues.get(0).getMasterDAO(), name)) {
                    Selection interSel = SelectionUtils.intersection(name, selectedValues);
                    interSel.getMasterDAO().getSelectionDAO().store(interSel);
                    GUI.getInstance().populateSelections();
                }
            });
            menu.add(union);
        }
        return menu;
    }
    public static void addCurrentObjectsToSelections(Collection<Selection> selections, SelectionDAO dao) {
        if (selections.isEmpty()) return;
        List<StructureObject> sel = ImageWindowManagerFactory.getImageManager().getSelectedLabileObjects(null);
        for (Selection s : selections ) {
            int[] structureIdx = s.getStructureIdx()==-1 ? new int[0] : new int[]{s.getStructureIdx()};
            List<StructureObject> objects = new ArrayList<>(sel);
            StructureObjectUtils.keepOnlyObjectsFromSameStructureIdx(sel, structureIdx);
            s.addElements(objects);
            dao.store(s);
        }
    }
    public static void removeCurrentObjectsFromSelections(Collection<Selection> selections, SelectionDAO dao) {
        List<StructureObject> sel = ImageWindowManagerFactory.getImageManager().getSelectedLabileObjects(null);
        for (Selection s : selections ) {
            if (s.getStructureIdx()==-1) continue;
            List<StructureObject> currentList = new ArrayList<>(sel);
            StructureObjectUtils.keepOnlyObjectsFromSameStructureIdx(currentList, s.getStructureIdx());
            s.removeElements(currentList);
            dao.store(s);
        }
    }
    public static void removeAllCurrentImageObjectsFromSelections(Collection<Selection> selections, SelectionDAO dao) {
        InteractiveImage ioi = ImageWindowManagerFactory.getImageManager().getCurrentImageObjectInterface();
        if (ioi==null) return;
        List<StructureObject> parents = ioi.getParents();
        for (Selection s : selections ) {
            s.removeChildrenOf(parents);
            dao.store(s);
        }
    }
}
