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
import java.util.logging.Level;
import java.util.logging.Logger;
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
public class SimpleListParameter implements ListParameter {

    protected String name;
    protected ArrayList<Parameter> children;
    protected int unMutableIndex;
    protected Class<? extends Parameter> childrenClass;
    @Transient protected ListParameterUI ui;
    @Transient protected ContainerParameter parent;
    /**
     * 
     * @param name : name of the parameter
     * @param unMutableIndex : index of the last object that cannot be modified
     */
    public SimpleListParameter(String name, int unMutableIndex, Class<? extends Parameter> childrenClass) { // TODO generic quand supporté par morphia
        this.childrenClass=childrenClass;
        this.name = name;
        children = new ArrayList<Parameter>(10);
        this.unMutableIndex=unMutableIndex;
    }
    /**
     * 
     * @param name : name of the parameter
     */
    public SimpleListParameter(String name, Class<? extends Parameter> childrenClass) {
        this.childrenClass=childrenClass;
        this.name = name;
        children = new ArrayList<Parameter>(10);
        this.unMutableIndex=-1;
    }
    
    @Override
    public Parameter createChildInstance() {
        try {
            Parameter instance = childrenClass.newInstance();
            instance.setName("new "+childrenClass.getSimpleName());
            return instance;
        } catch (InstantiationException ex) {
            Logger.getLogger(SimpleListParameter.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(SimpleListParameter.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
        }
        return null;
    }
    
    public Parameter createChildInstance(String name) {
        Parameter instance = createChildInstance();
        instance.setName(name);
        return instance;
    }

    public Parameter duplicate() {
        SimpleListParameter res = new SimpleListParameter(name, unMutableIndex, childrenClass);
        res.setContentFrom(this);
        return res;
    }
    
    public String[] getChildrenString() {
        String[] res = new String[children.size()];
        int i=0;
        for (Parameter s : children) res[i++] = s.toString();
        return res;
    }
    
    @Override
    public String getName(){
        return name;
    }
    
    @Override
    public void setName(String name) {
        this.name=name;
    }
    
    @Override
    public ArrayList<Parameter> getPath(){
        return SimpleParameter.getPath(this);
    }
    
    @Override
    public boolean sameContent(Parameter other) { // ne check pas le nom ni l'index unMutable..
        if (other instanceof ListParameter) {
            ListParameter otherLP = (ListParameter)other;
            if (otherLP.getChildCount()==this.getChildCount()) {
                for (int i = 0; i<getChildCount(); i++) {
                    if (!((Parameter)this.getChildAt(i)).sameContent((Parameter)otherLP.getChildAt(i))) return false;
                }
                return true;
            } else return false;
        } else return false;
    }

    @Override
    public void setContentFrom(Parameter other) { // TODO type-safe copy?
        if (other instanceof ListParameter) {
            ListParameter otherLP = (ListParameter)other;
            this.unMutableIndex = otherLP.getUnMutableIndex();
            this.name=otherLP.getName();
            this.children=new ArrayList<Parameter>(otherLP.getChildCount());
            for (int i = 0; i<otherLP.getChildCount(); i++) this.children.add(((Parameter)otherLP.getChildAt(i)).duplicate());
            for (Parameter p : children) p.setParent(this);
            ui=null;
        } else throw new IllegalArgumentException("wrong parameter type");
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
    
    // morphia
    SimpleListParameter(){}
    @PostLoad void postLoad() {for (Parameter p : children) p.setParent(this);}

    
}
