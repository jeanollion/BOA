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
package boa.gui.configuration;

import boa.configuration.parameters.Parameter;
import static boa.ui.GUI.logger;
import com.itextpdf.text.Font;
import java.awt.Color;
import java.awt.Component;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;

/**
 *
 * @author jean ollion
 */
public class TransparentListCellRenderer extends DefaultListCellRenderer {

    public TransparentListCellRenderer() {
        super();
        //this.setOpaque(false);
    }


    /*@Override
    public Color getBackground() {
        return (null);
    }*/
    @Override
    public Component getListCellRendererComponent(
        JList<?> list,
        Object value,
        int index,
        boolean isSelected,
        boolean cellHasFocus)
        {
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (isSelected) {
                setOpaque(true);
                setForeground( list.getSelectionForeground() );
                setBackground( list.getSelectionBackground());
            }
            else {
                setOpaque(false);
                setForeground(list.getForeground());
            }
            return c;
        }
}

