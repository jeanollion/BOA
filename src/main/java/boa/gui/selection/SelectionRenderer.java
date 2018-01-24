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

import static boa.gui.GUI.logger;
import boa.data_structure.Selection;
import java.awt.Color;
import java.awt.Component;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

/**
 *
 * @author jollion
 */
public class SelectionRenderer extends JLabel implements ListCellRenderer<Selection> {
 
    @Override
    public Component getListCellRendererComponent(JList<? extends Selection> list, Selection selection, int index,
        boolean isSelected, boolean cellHasFocus) {
        setText(selection.toString());
        setForeground(isSelected ? list.getSelectionForeground() : selection.getColor(false));
        setBackground(isSelected ? selection.getColor(false) : list.getBackground());
        this.setOpaque(true);
        //57/105/138
        return this;
    }
     
}