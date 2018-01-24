/*
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

import boa.configuration.parameters.ContainerParameter;
import boa.configuration.parameters.ListParameter;
import boa.configuration.parameters.Parameter;
import java.util.ArrayList;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

/**
 *
 * @author jollion
 */
public class ConfigurationTreeModel extends DefaultTreeModel {
    protected JTree tree;
    public ConfigurationTreeModel(ContainerParameter root) {
        super(root);
        if (root instanceof TreeModelContainer) ((TreeModelContainer)root).setModel(this);
    }
    public void setJTree(JTree tree) {
        this.tree=tree;
    }
    public JTree getTree() {
        return tree;
    }
    
    @Override
    public void insertNodeInto(MutableTreeNode newChild, MutableTreeNode parent, int index) {
        super.insertNodeInto(newChild, parent, index);
        newChild.setParent(parent);
        if (tree!=null) tree.updateUI();
    }
    public void insertNodeInto(Parameter newChild, ListParameter parent) {
        this.insertNodeInto(newChild, parent, parent.getChildCount());
        if (tree!=null) {
            if (parent.getChildCount()==1) tree.expandPath(getPath(parent));
            tree.updateUI();
        }
    }
    
    @Override
    public void removeNodeFromParent(MutableTreeNode node) {
        super.removeNodeFromParent(node);
        if (tree!=null) tree.updateUI();
    }
    
    public void moveUp(ListParameter list, Parameter p) {
        int idx = list.getIndex(p);
        if (idx>0) {
            super.removeNodeFromParent(p);
            super.insertNodeInto(p, list, idx-1);
        }
        if (tree!=null) tree.updateUI();
    }

    public void moveDown(ListParameter list, Parameter p) {
        int idx = list.getIndex(p);
        if (idx>=0 && idx<list.getChildCount()) {
            super.removeNodeFromParent(p);
            super.insertNodeInto(p, list, idx+1);
        }
        if (tree!=null) tree.updateUI();
    }

    public static TreePath getPath(TreeNode treeNode) {
        ArrayList<Object> nodes = new ArrayList<Object>();
        if (treeNode != null) {
            nodes.add(treeNode);
            treeNode = treeNode.getParent();
            while (treeNode != null) {
                nodes.add(0, treeNode);
                treeNode = treeNode.getParent();
            }
        }

        return nodes.isEmpty() ? null : new TreePath(nodes.toArray());
    }
}
