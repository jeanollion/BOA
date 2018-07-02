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

import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;

/**
 *
 * @author Jean Ollion
 */
public class Shortcuts {
    public enum PRESET {QWERTY, AZERTY}
    public enum ACTION {LINK, UNLINK, RESET_LINKS, CREATE_TRACK,
        DELETE, DELETE_AFTER_FRAME, PRUNE, MERGE, SPLIT, CREATE, TOGGLE_CREATION_TOOL,
        SELECT_ALL_OBJECTS, SELECT_ALL_TRACKS, TOGGLE_SELECT_MODE, TOGGLE_LOCAL_ZOOM, CHANGE_INTERACTIVE_STRUCTURE,
        NAV_NEXT, NAV_PREV, OPEN_NEXT, OPEN_PREV,
        ADD_TO_SEL0, REM_FROM_SEL0, REM_ALL_FROM_SEL0, TOGGLE_DISPLAY_SEL0,
        ADD_TO_SEL1, REM_FROM_SEL1, REM_ALL_FROM_SEL1, TOGGLE_DISPLAY_SEL1
    }
    private final Map<KeyStroke, ACTION> keyMapAction = new HashMap<>();
    private final Map<ACTION, KeyStroke> actionMapKey = new HashMap<>();
    private final Map<ACTION, Action> actionMap; 
    private final KeyboardFocusManager kfm;
    
    public Shortcuts(Map<ACTION, Action> actionMap, PRESET preset) {
        this.actionMap = actionMap;
        setPreset(preset);
        kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        kfm.addKeyEventDispatcher((KeyEvent e) -> {
            KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(e);
            if ( this.keyMapAction.containsKey(keyStroke) ) {
                final ACTION A = this.keyMapAction.get(keyStroke);
                final Action a = this.actionMap.get(A);
                final ActionEvent ae = new ActionEvent(e.getSource(), e.getID(), null );
                a.actionPerformed(ae);
                return true;
            }
            return false;
        });
    }
    public String getShortcutFor(ACTION action) {
        KeyStroke k = this.actionMapKey.get(action);
        return KeyEvent.getKeyText(k.getKeyCode());
    }
    public void setPreset(PRESET preset) {
        keyMapAction.clear();
        switch(preset) {
            case AZERTY:
            default:
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK), ACTION.LINK);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_U, KeyEvent.CTRL_DOWN_MASK), ACTION.UNLINK);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK), ACTION.RESET_LINKS);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, KeyEvent.CTRL_DOWN_MASK), ACTION.CREATE_TRACK);
                
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, KeyEvent.CTRL_DOWN_MASK), ACTION.DELETE);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, KeyEvent.ALT_DOWN_MASK), ACTION.DELETE_AFTER_FRAME);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.CTRL_DOWN_MASK), ACTION.PRUNE);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.CTRL_DOWN_MASK), ACTION.MERGE);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK), ACTION.SPLIT);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.CTRL_DOWN_MASK), ACTION.CREATE);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, 0 ), ACTION.TOGGLE_CREATION_TOOL);
                
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.CTRL_DOWN_MASK), ACTION.SELECT_ALL_OBJECTS);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_Q, KeyEvent.CTRL_DOWN_MASK), ACTION.SELECT_ALL_TRACKS);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_I, 0), ACTION.CHANGE_INTERACTIVE_STRUCTURE);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0), ACTION.TOGGLE_SELECT_MODE);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), ACTION.TOGGLE_LOCAL_ZOOM);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.CTRL_DOWN_MASK), ACTION.NAV_PREV);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, KeyEvent.CTRL_DOWN_MASK), ACTION.NAV_NEXT);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, KeyEvent.ALT_DOWN_MASK), ACTION.OPEN_NEXT);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.ALT_DOWN_MASK), ACTION.OPEN_PREV);
                
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.SHIFT_DOWN_MASK), ACTION.ADD_TO_SEL0);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.ALT_DOWN_MASK), ACTION.REM_FROM_SEL0);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.ALT_GRAPH_DOWN_MASK), ACTION.REM_ALL_FROM_SEL0);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK), ACTION.TOGGLE_DISPLAY_SEL0);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.SHIFT_DOWN_MASK), ACTION.ADD_TO_SEL1);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.ALT_DOWN_MASK), ACTION.REM_FROM_SEL1);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.ALT_GRAPH_DOWN_MASK), ACTION.REM_ALL_FROM_SEL1);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.CTRL_DOWN_MASK), ACTION.TOGGLE_DISPLAY_SEL1);
                break;
            case QWERTY:
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK), ACTION.LINK);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_U, KeyEvent.CTRL_DOWN_MASK), ACTION.UNLINK);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK), ACTION.RESET_LINKS);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, KeyEvent.CTRL_DOWN_MASK), ACTION.CREATE_TRACK);
                
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, KeyEvent.CTRL_DOWN_MASK), ACTION.DELETE);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, KeyEvent.ALT_DOWN_MASK), ACTION.DELETE_AFTER_FRAME);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.CTRL_DOWN_MASK), ACTION.PRUNE);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.CTRL_DOWN_MASK), ACTION.MERGE);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK), ACTION.SPLIT);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.CTRL_DOWN_MASK), ACTION.CREATE);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, 0 ), ACTION.TOGGLE_CREATION_TOOL);
                
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.CTRL_DOWN_MASK), ACTION.SELECT_ALL_OBJECTS);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_Q, KeyEvent.CTRL_DOWN_MASK), ACTION.SELECT_ALL_TRACKS);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_I, 0), ACTION.CHANGE_INTERACTIVE_STRUCTURE);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0), ACTION.TOGGLE_SELECT_MODE);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), ACTION.TOGGLE_LOCAL_ZOOM);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK), ACTION.NAV_PREV);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.ALT_DOWN_MASK), ACTION.OPEN_PREV);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, KeyEvent.CTRL_DOWN_MASK), ACTION.NAV_NEXT);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, KeyEvent.ALT_DOWN_MASK), ACTION.OPEN_NEXT);
                
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.SHIFT_DOWN_MASK), ACTION.ADD_TO_SEL0);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.ALT_DOWN_MASK), ACTION.REM_FROM_SEL0);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.ALT_GRAPH_DOWN_MASK), ACTION.REM_ALL_FROM_SEL0);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.CTRL_DOWN_MASK), ACTION.TOGGLE_DISPLAY_SEL0);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.SHIFT_DOWN_MASK), ACTION.ADD_TO_SEL1);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.ALT_DOWN_MASK), ACTION.REM_FROM_SEL1);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.ALT_GRAPH_DOWN_MASK), ACTION.REM_ALL_FROM_SEL1);
                keyMapAction.put(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.CTRL_DOWN_MASK), ACTION.TOGGLE_DISPLAY_SEL1);
                break;
        }
        actionMapKey.clear();
        actionMapKey.putAll(keyMapAction.entrySet().stream().collect(Collectors.toMap(e->e.getValue(), e->e.getKey())));
    }
    public void addToMenu(JMenu menu) {
        menu.removeAll();
        JMenu selection = new JMenu("Selections");
        menu.add(selection);
        selection.add("Navigation selection is set through right-click menu on selection");
        selection.add(getMenuLabelFor(ACTION.NAV_PREV, "Navigate to previous objects of the selection enabled for navigation (or selected selection if no selection is enabled for navigation)"));
        selection.add(getMenuLabelFor(ACTION.NAV_NEXT, "Navigate to next objects of the selection enabled for navigation (or selected selection if no active selection is set)"));
        selection.add(new JSeparator());
        selection.add(new JSeparator());
        selection.add("Active selections are set through right-click menu on selections");
        selection.add(new JSeparator());
        selection.add(getMenuLabelFor(ACTION.TOGGLE_DISPLAY_SEL0, "Toggle Display Objects for active selection group 0"));
        selection.add(getMenuLabelFor(ACTION.ADD_TO_SEL0, "Add selected object(s) to active selection group 0"));
        selection.add(getMenuLabelFor(ACTION.REM_FROM_SEL0, "Remove selected object(s) from active selection group 0"));
        selection.add(getMenuLabelFor(ACTION.REM_ALL_FROM_SEL0, "Remove all objects contained in active image from active selection group 0"));
        selection.add(new JSeparator());
        selection.add(getMenuLabelFor(ACTION.TOGGLE_DISPLAY_SEL1, "Toggle Display Objects for active selection group 1"));
        selection.add(getMenuLabelFor(ACTION.ADD_TO_SEL1, "Add selected object(s) to active selection group 1"));
        selection.add(getMenuLabelFor(ACTION.REM_FROM_SEL1, "Remove selected object(s) from active selection group 1"));
        selection.add(getMenuLabelFor(ACTION.REM_ALL_FROM_SEL1, "Remove all objects contained in active image from active selection group 1"));
        
        
        JMenu nav = new JMenu("Navigation/Display");
        menu.add(nav);
        nav.add("Shift + mouse wheel mouve: fast scroll");
        nav.add("Navigation selection is set through right-click menu on selections");
        nav.add(getMenuLabelFor(ACTION.NAV_PREV, "Navigate to previous objects of the selection enabled for navigation (or selected selection if no selection is enabled for navigation)"));
        nav.add(getMenuLabelFor(ACTION.NAV_NEXT, "Navigate to next objects of the selection enabled for navigation (or selected selection if no selection is enabled for navigation)"));
        nav.add(getMenuLabelFor(ACTION.OPEN_NEXT, "Display next image"));
        nav.add(getMenuLabelFor(ACTION.OPEN_PREV, "Display previous image"));
        nav.add(new JSeparator());
        nav.add(getMenuLabelFor(ACTION.SELECT_ALL_OBJECTS, "Display all objects on active image"));
        nav.add(getMenuLabelFor(ACTION.SELECT_ALL_TRACKS, "Display all tracks on active image"));
        nav.add(getMenuLabelFor(ACTION.TOGGLE_SELECT_MODE, "Toggle display object/track"));
        nav.add(getMenuLabelFor(ACTION.CHANGE_INTERACTIVE_STRUCTURE, "Change interactive structure"));
        nav.add(new JSeparator());
        nav.add(getMenuLabelFor(ACTION.TOGGLE_LOCAL_ZOOM, "Toggle local zoom"));
        
        JMenu objectModif = new JMenu("Object/Lineage Edition");
        menu.add(objectModif);
        objectModif.add("All action are performed on objects selected on active image");
        objectModif.add(getMenuLabelFor(ACTION.DELETE, "Delete object(s)"));
        objectModif.add(getMenuLabelFor(ACTION.DELETE_AFTER_FRAME, "Delete all object(s) after first selected object"));
        objectModif.add(getMenuLabelFor(ACTION.PRUNE, "Prune track starting from selected object(s)"));
        objectModif.add(getMenuLabelFor(ACTION.TOGGLE_CREATION_TOOL, "Switch to object creation tool / rectangle selection tool"));
        objectModif.add(getMenuLabelFor(ACTION.CREATE, "Create object(s) from selected point(s)"));
        objectModif.add(getMenuLabelFor(ACTION.MERGE, "Merge objects"));
        objectModif.add(getMenuLabelFor(ACTION.SPLIT, "Split object(s)"));
        objectModif.add("Ctrl + freehand line: manual split objects");
        objectModif.add(new JSeparator());
        objectModif.add(getMenuLabelFor(ACTION.RESET_LINKS, "Reset lineage of selected object(s)"));
        objectModif.add(getMenuLabelFor(ACTION.LINK, "Link selected objects"));
        objectModif.add(getMenuLabelFor(ACTION.UNLINK, "Unlink selected objects"));
        objectModif.add(getMenuLabelFor(ACTION.CREATE_TRACK, "Create track starting from selected object(s)"));
        
        //objectModif.add("Ctrl + straight line: strech objects");
    }
    private String getMenuLabelFor(ACTION action, String desc) {
        return actionMapKey.get(action).toString() +": "+ desc;
    }
}
