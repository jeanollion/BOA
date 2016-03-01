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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class SelectionUtils {
    public static Map<String, Color> colors = new HashMap<String, Color>() {{
        put("Magenta", new Color(255, 0, 255, 120));
        put("Blue", new Color(0, 0, 255, 120));
        put("Cyan", new Color(0, 255, 255, 120));
        put("Green", new Color(0, 255, 0, 120));
    }};
    public static void displayObjects(Selection s, ImageObjectInterface i) {
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        if (i==null) i = iwm.getCurrentImageObjectInterface();
        if (i!=null) {
            String fieldName = i.getParent().getFieldName();
            List<StructureObject> objects = s.getElements(fieldName);
            if (objects!=null) {
                iwm.displayObjects(null, i.pairWithOffset(objects), s.getColor(), false);
            }
        }
    }
    
    public static void hideObjects(Selection s, ImageObjectInterface i) {
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        if (i==null) i = iwm.getCurrentImageObjectInterface();
        if (i!=null) {
            String fieldName = i.getParent().getFieldName();
            List<StructureObject> objects = s.getElements(fieldName);
            if (objects!=null) {
                iwm.hideObjects(null, i.pairWithOffset(objects));
            }
        }
    }
    public static void displayTracks(Selection s, ImageObjectInterface i) {
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        if (i==null) i = iwm.getCurrentImageObjectInterface();
        if (i!=null) {
            String fieldName = i.getParent().getFieldName();
            List<StructureObject> tracks = s.getTrackHeads(fieldName);
            if (tracks==null) return;
            for (StructureObject trackHead : tracks) {
                List<StructureObject> track = StructureObjectUtils.getTrack(trackHead, true);
                iwm.displayTrack(null, i, i.pairWithOffset(track), s.getColor(), false);
            }
        }
    }
    public static void hideTracks(Selection s, ImageObjectInterface i) {
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        if (i==null)  i = iwm.getCurrentImageObjectInterface();
        if (i!=null) {
            String fieldName = i.getParent().getFieldName();
            List<StructureObject> tracks = s.getTrackHeads(fieldName);
            if (tracks==null) return;
            iwm.hideTracks(null, i, tracks);
        }
    }
    
    public static void setMouseAdapter(final JList list) {
        list.addMouseListener( new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    JList list = (JList)e.getSource();
                    int row = list.locationToIndex(e.getPoint());
                    //logger.debug("right button on row: {}, ctrl {} ctrl", row, ctrl);
                    if (list.isSelectedIndex(row)) {
                        JPopupMenu menu = generateMenu(list);
                        menu.show(list, e.getX(), e.getY());
                    }
                }
            }
        });
    }
    
    private static JPopupMenu generateMenu(final JList list) {
        JPopupMenu menu = new JPopupMenu("");
        final List<Selection> selectedValues = list.getSelectedValuesList();
        int dispObjects=0;
        int dispTracks = 0;
        for (Selection s : selectedValues) {
            if (s.isDisplayingObjects()) dispObjects++;
            if (s.isDisplayingTracks()) dispTracks++;
        }
        final JCheckBoxMenuItem displayObjects = new JCheckBoxMenuItem("Display Objects");
        displayObjects.setSelected(dispObjects==selectedValues.size());
        displayObjects.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (selectedValues.isEmpty()) return;
                for (Selection s : selectedValues ) s.setIsDisplayingObjects(displayObjects.isSelected());
                GUI.updateRoiDisplay(null);
            }
        });
        menu.add(displayObjects);
        
        final JCheckBoxMenuItem displayTracks = new JCheckBoxMenuItem("Display Tracks");
        displayTracks.setSelected(dispTracks==selectedValues.size());
        displayTracks.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (selectedValues.isEmpty()) return;
                for (Selection s : selectedValues ) s.setIsDisplayingTracks(displayTracks.isSelected());
                GUI.updateRoiDisplay(null);
            }
        });
        menu.add(displayTracks);
        
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
                        SelectionDAO dao = GUI.getDBConnection().getSelectionDAO();
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
        JMenuItem add = new JMenuItem("Add to Selection");
        add.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (selectedValues.isEmpty()) return;
                SelectionDAO dao = GUI.getDBConnection().getSelectionDAO();
                StructureObjectTreeGenerator sotg = GUI.getInstance().getObjectTree();
                for (Selection s : selectedValues ) {
                    List<StructureObject> objects = sotg.getSelectedObjects(true, s.getStructureIdx());
                    s.addElements(objects);
                    dao.store(s);
                }
            }
        });
        menu.add(add);
        
        JMenuItem remove = new JMenuItem("Remove from Selection");
        remove.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (selectedValues.isEmpty()) return;
                SelectionDAO dao = GUI.getDBConnection().getSelectionDAO();
                StructureObjectTreeGenerator sotg = GUI.getInstance().getObjectTree();
                for (Selection s : selectedValues ) {
                    List<StructureObject> objects = sotg.getSelectedObjects(true, s.getStructureIdx());
                    s.removeElements(objects);
                    dao.store(s);
                }
            }
        });
        menu.add(remove);
        
        
        JMenuItem clear = new JMenuItem("Clear");
        clear.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (selectedValues.isEmpty()) return;
                SelectionDAO dao = GUI.getDBConnection().getSelectionDAO();
                for (Selection s : selectedValues ) {
                    s.clear();
                    dao.store(s);
                }
            }
        });
        menu.add(clear);
        menu.add(new JSeparator());
        JMenuItem delete = new JMenuItem("Delete Selection");
        delete.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (selectedValues.isEmpty()) return;
                DefaultListModel<Selection> model = (DefaultListModel<Selection>)list.getModel();
                SelectionDAO dao = GUI.getDBConnection().getSelectionDAO();
                for (Selection s : selectedValues ) dao.delete(s);
                for (int i : list.getSelectedIndices()) model.removeElementAt(i);
            }
        });
        menu.add(delete);
        return menu;
    }
    
}
