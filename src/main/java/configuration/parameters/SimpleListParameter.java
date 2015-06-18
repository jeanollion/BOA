/*
 * Copyright (C) 2015 ImageJ
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
package configuration.parameters;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.annotations.Transient;

/**
 *
 * @author jollion
 * @param <T> type of children
 */

@Embedded
public abstract class SimpleListParameter<T extends Parameter> implements ListParameter<T> {
    protected String name;
    @Transient protected ContainerParameter parent;
    protected ArrayList<T> children;
    
    public SimpleListParameter(String name, int childrenSize) {
        this.name = name;
        children = new ArrayList<T>(childrenSize);
    }
    
    public SimpleListParameter(String name) {
        this.name = name;
        children = new ArrayList<T>();
    }
    
    @Override
    public ArrayList<Parameter> getPath(){
        return SimpleParameter.getPath(this);
    }
    
    @Override
    public String toString() {return name;}
    
    @Override
    public void insert(MutableTreeNode child, int index) {
        if (index>=children.size()) children.add((T)child);
        else children.add(index, (T)child);
    }

    @Override
    public void remove(int index) {
        children.remove(index);
    }

    @Override
    public void remove(MutableTreeNode node) {
        System.out.println("removing node:"+((T)node).toString() +" total number: "+children.size());
        children.remove((T)node);
        System.out.println("removed node:"+((T)node).toString() +" total number: "+children.size());
    }

    @Override
    public void setUserObject(Object object) {
        this.name=object.toString();
    }

    @Override
    public void removeFromParent() {
        if (parent!=null) this.parent.remove(this);
    }

    @Override
    public void setParent(MutableTreeNode newParent) {
        this.parent=(ContainerParameter)newParent;
    }

    @Override
    public TreeNode getChildAt(int childIndex) {
        return children.get(childIndex);
    }

    @Override
    public int getChildCount() {
        return children.size();
    }

    @Override
    public TreeNode getParent() {
        return parent;
    }

    @Override
    public int getIndex(TreeNode node) {
        return children.indexOf(node);
    }

    @Override
    public boolean getAllowsChildren() {
        return true;
    }

    @Override
    public boolean isLeaf() {
        return children.isEmpty();
    }

    @Override
    public Enumeration children() {
        return Collections.enumeration(children);
    }
    
    @Override
    public Object[] getDisplayComponents() {
        return new Object[0];
    }
}
