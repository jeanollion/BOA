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

import dataStructure.objects.Selection;
import java.awt.Component;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

/**
 *
 * @author jollion
 */
public class SelectionRenderer extends JLabel implements ListCellRenderer<SelectionGUI> {
 
    @Override
    public Component getListCellRendererComponent(JList<? extends SelectionGUI> list, SelectionGUI selection, int index,
        boolean isSelected, boolean cellHasFocus) {
          
        //ImageIcon imageIcon = new ImageIcon(getClass().getResource("/images/" + code + ".png"));
        //setIcon(imageIcon);
        setText(selection.toString());
        // todo selection fore & background color
        return this;
    }
     
}