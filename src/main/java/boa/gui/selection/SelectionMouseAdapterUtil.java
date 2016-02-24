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
public class SelectionMouseAdapterUtil {
    public static Map<String, Color> colors = new HashMap<String, Color>() {{
        put("Magenta", new Color(255, 0, 255, 120));
        put("Blue", new Color(0, 0, 255, 120));
        put("Cyan", new Color(0, 255, 255, 120));
        put("Green", new Color(0, 255, 0, 120));
    }};
    
    public static void setMouseAdapter(final JList list) {
        
        final JPopupMenu menu = new JPopupMenu("");
        
        final JMenuItem displayObjects = new JMenuItem("Display Objects");
        displayObjects.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                List<SelectionGUI> sel = list.getSelectedValuesList();
                if (sel.isEmpty()) return;
                
                for (SelectionGUI s : sel ) {
                    ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
                    ImageObjectInterface i = iwm.getCurrentImageObjectInterface();
                    if (i!=null) {
                        String fieldName = i.getParent().getFieldName();
                        List<StructureObject> objects = s.selection.getElements(fieldName);
                        if (objects!=null) {
                            iwm.displayObjects(null, i, true, i.pairWithOffset(objects), s.color);
                        }
                    }
                }
            }
        });
        menu.add(displayObjects);
        final JMenuItem hideObjects = new JMenuItem("Hide Objects");
        hideObjects.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                List<SelectionGUI> sel = list.getSelectedValuesList();
                if (sel.isEmpty()) return;
                
                for (SelectionGUI s : sel ) {
                    ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
                    ImageObjectInterface i = iwm.getCurrentImageObjectInterface();
                    if (i!=null) {
                        String fieldName = i.getParent().getFieldName();
                        List<StructureObject> objects = s.selection.getElements(fieldName);
                        if (objects!=null) {
                            iwm.unDisplayObjects(null, i, i.pairWithOffset(objects));
                        }
                    }
                }
            }
        });
        menu.add(hideObjects);
        menu.add(new JSeparator());
        
        final JMenuItem displayTracks = new JMenuItem("Display Tracks");
        displayTracks.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                List<SelectionGUI> sel = list.getSelectedValuesList();
                if (sel.isEmpty()) return;
                
                for (SelectionGUI s : sel ) {
                    ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
                    ImageObjectInterface i = iwm.getCurrentImageObjectInterface();
                    if (i!=null) {
                        String fieldName = i.getParent().getFieldName();
                        List<StructureObject> objects = s.selection.getElements(fieldName);
                        if (objects==null) return;
                        List<StructureObject> tracks = new ArrayList<StructureObject>(objects.size());
                        for (StructureObject o : objects) tracks.add(o.getTrackHead());
                        Utils.removeDuplicates(tracks, false);
                        for (StructureObject trackHead : tracks) {
                            List<StructureObject> track = StructureObjectUtils.getTrack(trackHead);
                            iwm.displayTrack(null, i, true, track, s.color);
                        }
                    }
                }
            }
        });
        menu.add(displayTracks);
        
        menu.add(new JSeparator());
        JMenu colorMenu = new JMenu("Set Color");
        for (String s : colors.keySet()) {
            final Color c = colors.get(s);
            JMenuItem color = new JMenuItem(s);
            colorMenu.add(color);
            color.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    List<SelectionGUI> sel = list.getSelectedValuesList();
                    if (sel.isEmpty()) return;
                    for (SelectionGUI s : sel ) s.color=c;
                    list.updateUI();
                }
            });
        }
        menu.add(colorMenu);
        menu.add(new JSeparator());
        JMenuItem add = new JMenuItem("Add to Selection");
        add.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                List<SelectionGUI> sel = list.getSelectedValuesList();
                if (sel.isEmpty()) return;
                SelectionDAO dao = GUI.getDBConnection().getSelectionDAO();
                StructureObjectTreeGenerator sotg = GUI.getInstance().getObjectTree();
                for (SelectionGUI s : sel ) {
                    List<StructureObject> objects = sotg.getSelectedObjects(true, s.selection.getStructureIdx());
                    s.selection.addElements(objects);
                    dao.store(s.selection);
                }
            }
        });
        menu.add(add);
        
        JMenuItem remove = new JMenuItem("Remove from Selection");
        remove.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                List<SelectionGUI> sel = list.getSelectedValuesList();
                if (sel.isEmpty()) return;
                SelectionDAO dao = GUI.getDBConnection().getSelectionDAO();
                StructureObjectTreeGenerator sotg = GUI.getInstance().getObjectTree();
                for (SelectionGUI s : sel ) {
                    List<StructureObject> objects = sotg.getSelectedObjects(true, s.selection.getStructureIdx());
                    s.selection.removeElements(objects);
                    dao.store(s.selection);
                }
            }
        });
        menu.add(remove);
        
        
        JMenuItem clear = new JMenuItem("Clear");
        clear.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                List<SelectionGUI> sel = list.getSelectedValuesList();
                if (sel.isEmpty()) return;
                SelectionDAO dao = GUI.getDBConnection().getSelectionDAO();
                for (SelectionGUI s : sel ) {
                    s.selection.clear();
                    dao.store(s.selection);
                }
            }
        });
        menu.add(clear);
        menu.add(new JSeparator());
        JMenuItem delete = new JMenuItem("Delete Selection");
        delete.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                List<SelectionGUI> sel = list.getSelectedValuesList();
                if (sel.isEmpty()) return;
                DefaultListModel<SelectionGUI> model = (DefaultListModel<SelectionGUI>)list.getModel();
                SelectionDAO dao = GUI.getDBConnection().getSelectionDAO();
                for (SelectionGUI s : sel ) dao.delete(s.selection);
                for (int i : list.getSelectedIndices()) model.removeElementAt(i);
            }
        });
        menu.add(delete);
        
        list.addMouseListener( new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    JList list = (JList)e.getSource();
                    int row = list.locationToIndex(e.getPoint());
                    //logger.debug("right button on row: {}, ctrl {} ctrl", row, ctrl);
                    if (list.isSelectedIndex(row)) {
                        menu.show(list, e.getX(), e.getY());
                    }
                }
            }
        });
    }
    
}
