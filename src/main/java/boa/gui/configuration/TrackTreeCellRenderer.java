/*
 * Copyright (C) 2015 nasique
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
package boa.gui.configuration;

import boa.gui.objects.RootTrackNode;
import boa.gui.objects.TrackNode;
import dataStructure.objects.StructureObject;
import java.awt.Color;
import java.awt.Component;
import java.util.Collection;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeNode;

/**
 *
 * @author nasique
 */
public class TrackTreeCellRenderer extends DefaultTreeCellRenderer {
    final Collection<StructureObject> highlightedObjects;
    final Collection<String> highlightedRoots;
    public TrackTreeCellRenderer(Collection<StructureObject> highlightedObjects, Collection<String> highlightedRoots) {
        this.highlightedObjects=highlightedObjects;
        this.highlightedRoots=highlightedRoots;
        setLeafIcon(null);
        setClosedIcon(null);
        setOpenIcon(null);
    }
    @Override
    public Color getBackgroundNonSelectionColor() {
        return (null);
    }

    @Override
    public Color getBackground() {
        return (null);
    }
    public static final Color highlightColor = new Color(0, 100, 0);
    @Override
    public Component getTreeCellRendererComponent(final JTree tree, final Object value, final boolean sel, final boolean expanded, final boolean leaf, final int row, final boolean hasFocus) {
        final JComponent ret = (JComponent)super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        // mettre hasFocus = true?
        final TreeNode node = ((TreeNode) (value));
        this.setText(value.toString());
        if (node instanceof TrackNode && ((TrackNode)node).containsError() || node instanceof RootTrackNode && ((RootTrackNode)node).containsError()) ret.setForeground(Color.red);
        
        if (value instanceof TrackNode) {
            TrackNode tn = (TrackNode)value;
            if (highlightedObjects.contains(tn.getTrackHead())) ret.setForeground(highlightColor);
        } else if (node instanceof RootTrackNode) {
            if (highlightedRoots.contains(((RootTrackNode)node).getFieldName())) ret.setForeground(highlightColor);
        }
        return ret;
    }
}

