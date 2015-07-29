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
import de.caluga.morphium.annotations.Embedded;
import de.caluga.morphium.annotations.Transient;
import de.caluga.morphium.annotations.lifecycle.Lifecycle;
import de.caluga.morphium.annotations.lifecycle.PostLoad;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;

/**
 * 
 * @author jollion
 */

@Embedded(polymorph = true)
@Lifecycle
public class SimpleListParameter<T extends Parameter> implements ListParameter<T> {

    protected String name;
    protected ArrayList<T> children;
    protected int unMutableIndex;
    @Transient protected Class<T> childClass;
    protected String childClassName;
    protected T childInstance;
    
    @Transient protected ListParameterUI ui;
    @Transient protected ContainerParameter parent;
    
    
    /**
     * 
     * @param name : name of the parameter
     * @param unMutableIndex : index of the last object that cannot be modified
     */
    public SimpleListParameter(String name, int unMutableIndex, Class<T> childClass) { // TODO generic quand supporté par morphia
        this.name = name;
        children = new ArrayList<T>(10);
        this.unMutableIndex=unMutableIndex;
        this.childClass = childClass;
        this.childClassName=childClass.getName();
    }
    /**
     * 
     * @param name : name of the parameter
     */
    public SimpleListParameter(String name, Class<T> childClass) {
        //this.childrenClass=childrenClass;
        this.name = name;
        children = new ArrayList<T>(10);
        this.unMutableIndex=-1;
        this.childClass = childClass;
        this.childClassName=childClass.getName();
    }
    
    public SimpleListParameter(String name, int unMutableIndex, T childInstance) { // TODO generic quand supporté par morphia
        this.childInstance=childInstance;
        this.name = name;
        children = new ArrayList<T>(10);
        this.unMutableIndex=unMutableIndex;
        this.childClass=(Class<T>)childInstance.getClass();
    }
    
    public SimpleListParameter(String name, T childInstance) { // TODO generic quand supporté par morphia
        this.childInstance=childInstance;
        this.name = name;
        children = new ArrayList<T>(10);
        this.unMutableIndex=-1;
        this.childClass=(Class<T>)childInstance.getClass();
    }
    
    public boolean containsElement(String name) {
        for (Parameter p : children) if (p.getName().equals(name)) return true;
        return false;
    }

    public Class<T> getChildClass() {
        return childClass;
    }
    
    @Override
    public T createChildInstance() {
        if (childInstance == null && getChildClass() != null) {
            try {
                T instance = childClass.newInstance();
                instance.setName("new " + childClass.getSimpleName());
                return instance;
            } catch (InstantiationException ex) {
                Logger.getLogger(SimpleListParameter.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            } catch (IllegalAccessException ex) {
                Logger.getLogger(SimpleListParameter.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            }
        } else if (childInstance != null) {
            return (T)childInstance.duplicate();
        }
        return null;
    }
    
    @Override
    public boolean isDeactivatable() {
        return Deactivatable.class.isAssignableFrom(this.childClass);
    }
    
    @Override
    public void setActivatedAll(boolean activated) {
        if (isDeactivatable()) {
            for (Parameter p: children) ((Deactivatable)p).setActivated(activated);
        }
    }
    
    
    public T createChildInstance(String name) {
        T instance = createChildInstance();
        instance.setName(name);
        return instance;
    }

    public Parameter duplicate() {
        SimpleListParameter res = new SimpleListParameter(name, unMutableIndex, childClass);
        res.setContentFrom(this);
        return res;
    }
    
    public String[] getChildrenString() {
        String[] res = new String[children.size()];
        int i=0;
        for (Parameter s : children) res[i++] = s.toString();
        return res;
    }
    
    public ArrayList<T> getChildren() { // todo generic...
        return children;
    }
    
    /*public Parameter[] getChildren() { // todo generic...
        return children.toArray(new Parameter[children.size()]);
    }*/
    
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
    public void setContentFrom(Parameter other) {
        if (other instanceof ListParameter) {
            ListParameter otherLP = (ListParameter)other;
            if (otherLP.getChildClass()!=this.getChildClass()) throw new IllegalArgumentException("setContentFrom: wrong parameter type : child class is:"+getChildClass() + " but should be: "+otherLP.getChildClass());
            else {
                this.unMutableIndex = otherLP.getUnMutableIndex();
                this.name=otherLP.getName();
                this.children=new ArrayList<T>(otherLP.getChildCount());
                for (int i = 0; i<otherLP.getChildCount(); i++) this.children.add((T)(((Parameter)otherLP.getChildAt(i)).duplicate()));
                for (Parameter p : children) p.setParent(this);
                ui=null;
            }
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
        if (index>=children.size()) children.add((T)child);
        else children.add(index, (T)child);
        child.setParent(this);
    }

    @Override
    public void insert(T... child) {
        for (T c : child) {
            children.add(c);
            c.setParent(this);
        }
    }

    @Override
    public void remove(int index) {
        children.remove(index);
    }

    @Override
    public void remove(MutableTreeNode node) {
        System.out.println("removing node:"+((Parameter)node).toString() +" total number: "+children.size());
        children.remove((T)node);
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
        if (this.unMutableIndex<0) children=new ArrayList<T>(children.size());
        else for (int i = children.size()-1;i>unMutableIndex;--i) children.remove(i);
    }

    @Override
    public void setParent(MutableTreeNode newParent) {
        this.parent=(ContainerParameter)newParent;
    }

    @Override
    public T getChildAt(int childIndex) {
        return children.get(childIndex);
    }

    @Override
    public int getChildCount() {
        return children.size();
    }

    @Override
    public ContainerParameter getParent() {
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
    
    // morphium

    @PostLoad public void postLoad() {
        for (Parameter p : children) p.setParent(this);
        if (this.childInstance!=null) this.childClass=(Class<T>)childInstance.getClass();
        else try {
            childClass = (Class<T>) Class.forName(childClassName);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(SimpleListParameter.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    
}
