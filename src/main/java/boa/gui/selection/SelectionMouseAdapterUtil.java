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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JList;
import javax.swing.SwingUtilities;

/**
 *
 * @author jollion
 */
public class SelectionMouseAdapterUtil {
    public static void setMouseAdapter(JList list) {
        list.addMouseListener( new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    
                    JList list = (JList)e.getSource();
                    int row = list.locationToIndex(e.getPoint());
                    logger.debug("right button on row: {}", row);
                    //Selection s = list.getModel().getElementAt(row);
                    list.setSelectedIndex(row);
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    JList list = (JList)e.getSource();
                    int row = list.locationToIndex(e.getPoint());
                    //Selection s = list.getModel().getElementAt(row);
                    logger.debug("left button on row: {}", row);
                    list.setSelectedIndex(row);
                    
                }
            }
        });
    }
}
