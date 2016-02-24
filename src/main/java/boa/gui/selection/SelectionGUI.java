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
import boa.gui.imageInteraction.ImageWindowManager;
import dataStructure.objects.Selection;
import java.awt.Color;

/**
 *
 * @author jollion
 */
public class SelectionGUI {
    public final Selection selection;
    public Color color = ImageWindowManager.defaultRoiColor;
    public SelectionGUI(Selection selection) {
        this.selection=selection;
        selection.setMasterDAO(GUI.getDBConnection());
    }
    @Override public String toString() {
        return selection.toString();
    }
}
