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
package configuration.parameters;

import configuration.parameters.ui.SimpleListParameterUI;
import configuration.parameters.ui.ListParameterUI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.annotations.PostLoad;
import org.mongodb.morphia.annotations.Transient;

/**
 * 
 * @author jollion
 */

@Embedded
public abstract class SimpleListParameter implements ListParameter {

    protected String name;
    protected ArrayList<Parameter> children;
    protected int unMutableIndex;
    @Transient protected ListParameterUI ui;
    @Transient protected ContainerParameter parent;
    /**
     * 
     * @param name : name of the parameter
     * @param unMutableIndex : index of the last object that cannot be modified
     */
    public SimpleListParameter(String name, int unMutableIndex) {
        this.name = name;
        children = new ArrayList<Parameter>(10);
        this.unMutableIndex=unMutableIndex;
    }
    /**
     * 
     * @param name : name of the parameter
     */
    public SimpleListParameter(String name) {
        this.name = name;
        children = new ArrayList<Parameter>(10);
        this.unMutableIndex=-1;
    }
    
    @Override
    public ArrayList<Parameter> getPath(){
        return SimpleParameter.getPath(this);
    }
    
    public void setUnmutableIndex(int unMutableIndex) {
        this.unMutableIndex=unMutableIndex;
    }
    
    public int getUnMutableIndex() {
        return unMutableIndex;
    }
    
    @Override
    public String toString() {return name;}
    
    @Override
    public void insert(MutableTreeNode child, int index) {
        if (index>=children.size()) children.add((Parameter)child);
        else children.add(index, (Parameter)child);
        child.setParent(this);
    }
    
    @Override
    public void insert(Parameter child) {
        children.add((Parameter)child);
        child.setParent(this);
    }
    

    @Override
    public void remove(int index) {
        children.remove(index);
    }

    @Override
    public void remove(MutableTreeNode node) {
        System.out.println("removing node:"+((Parameter)node).toString() +" total number: "+children.size());
        children.remove((Parameter)node);
        System.out.println("removed node:"+((Parameter)node).toString() +" total number: "+children.size());
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
    public void removeAllElements() {
        children=new ArrayList<Parameter>(children.size());
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
    public ListParameterUI getUI() {
        if (ui==null) ui=new SimpleListParameterUI(this);
        return ui;
    }
    @PostLoad void postLoad() {for (Parameter p : children) p.setParent(this);}
}
