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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import org.json.simple.JSONArray;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;
import utils.Utils;

/**
 * 
 * @author jollion
 */

public class SimpleListParameter<T extends Parameter> implements ListParameter<T> {

    protected String name;
    protected ArrayList<T> children;
    protected int unMutableIndex;
    protected Class<T> childClass;
    protected String childClassName;
    protected T childInstance;
    
    protected ListParameterUI ui;
    protected ContainerParameter parent;
    protected Function<Integer, String> newInstanceNameFunction = i->"new "+childClass.getSimpleName();
    
    @Override
    public JSONAware toJSONEntry() {
        JSONObject res= new JSONObject();
        JSONArray list= new JSONArray();
        for (T p : children) list.add(p.toJSONEntry());
        res.put("list", list);
        return res;
    }

    @Override
    public void initFromJSONEntry(Object json) {
        if (json instanceof JSONObject && ((JSONObject)json).containsKey("list")) {
            JSONObject jsonO = (JSONObject)json;
            JSONArray list = (JSONArray)jsonO.get("list");
            for (Object o : list) {
                T newI = createChildInstance();
                newI.initFromJSONEntry(o);
                insert(newI);
            }
        } else { // try to init with one single element (if element was replaced by list)
            T newI = createChildInstance();
            newI.initFromJSONEntry(json);
            insert(newI);
        }
    }
    
    protected String toolTipText;
    @Override
    public String getToolTipText() {
        return toolTipText;
    }
    @Override
    public <T extends Parameter> T setToolTipText(String txt) {
        this.toolTipText=txt;
        return (T)this;
    }
    
    /**
     * 
     * @param name : name of the parameter
     * @param unMutableIndex : index of the last object that cannot be modified
     */
    public SimpleListParameter(String name, int unMutableIndex, Class<T> childClass) {
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
    
    public SimpleListParameter(String name, int unMutableIndex, T childInstance) {
        this.childInstance=childInstance;
        this.name = name;
        children = new ArrayList<T>(10);
        this.unMutableIndex=unMutableIndex;
        this.childClass=(Class<T>)childInstance.getClass();
    }
    
    public SimpleListParameter(String name, T childInstance) {
        this.childInstance=childInstance;
        this.name = name;
        children = new ArrayList<T>(10);
        this.unMutableIndex=-1;
        this.childClass=(Class<T>)childInstance.getClass();
    }
    
    public boolean containsElement(String name) {
        for (Parameter p : getChildren()) if (p.getName().equals(name)) return true;
        return false;
    }

    @Override public Class<T> getChildClass() {
        if (childClass==null) {
            if (this.childInstance!=null) this.childClass=(Class<T>)childInstance.getClass();
            else try {
                childClass = (Class<T>) Class.forName(childClassName);
            } catch (ClassNotFoundException ex) {
                logger.error("childClass search error", ex);
            }
        }
        return childClass;
    }
    
    @Override
    public T createChildInstance() {
        if (childInstance == null && getChildClass() != null) {
            try {
                T instance;
                instance = childClass.getDeclaredConstructor(String.class).newInstance(this.newInstanceNameFunction.apply(getChildCount()));
                return instance;
            } catch (NoSuchMethodException ex) {
                logger.error("duplicate error", ex);
            } catch (SecurityException ex) {
                logger.error("duplicate error", ex);
            } catch (InstantiationException ex) {
                logger.error("duplicate error", ex);
            } catch (IllegalAccessException ex) {
                logger.error("duplicate error", ex);
            } catch (IllegalArgumentException ex) {
                logger.error("duplicate error", ex);
            } catch (InvocationTargetException ex) {
                logger.error("duplicate error", ex);
            }
        } else if (childInstance != null) {
            return (T)childInstance.duplicate();
            //if (res instanceof SimpleContainerParameter) ((SimpleContainerParameter)res).setListeners(((SimpleContainerParameter)childInstance).listeners);
        }
        return null;
    }
    
    @Override
    public boolean isDeactivatable() {
        return Deactivatable.class.isAssignableFrom(this.getChildClass());
    }
    
    @Override
    public void setActivatedAll(boolean activated) {
        if (isDeactivatable()) {
            for (Parameter p: getChildren()) ((Deactivatable)p).setActivated(activated);
        }
    }
    
    @Override
    public List<T> getActivatedChildren() {
        if (!isDeactivatable()) return getChildren();
        else {
            List<T> res = new ArrayList<T>(this.getChildCount());
            for (T p: getChildren()) if (((Deactivatable)p).isActivated()) res.add(p);
            return res;
        }
    }
    
    public T createChildInstance(String name) {
        T instance = createChildInstance();
        instance.setName(name);
        return instance;
    }

    public SimpleListParameter<T> duplicate() {
        SimpleListParameter<T> res = new SimpleListParameter<T>(name, unMutableIndex, getChildClass());
        res.setContentFrom(this);
        return res;
    }
    
    public String[] getChildrenString() {
        String[] res = new String[getChildren().size()];
        int i=0;
        for (Parameter s : children) res[i++] = s.getName();
        return res;
    }
    
    @Override
    public List<T> getChildren() {
        //postLoad();
        return children;
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
                    if (!((Parameter)this.getChildAt(i)).sameContent((Parameter)otherLP.getChildAt(i))) {
                        logger.debug("{}!={} class {}, children differ at {}", name, other.getName(), getClass().getSimpleName(), i);
                        return false;
                    }
                }
                return true;
            } else {
                logger.debug("{}!={} class {}, child number: {} vs {}", name, other.getName(), getClass().getSimpleName(), getChildCount(), other.getChildCount());
                return false;
            }
        } else return false;
    }

    @Override
    public void setContentFrom(Parameter other) {
        if (other instanceof ListParameter) {
            ListParameter<? extends Parameter> otherLP = (ListParameter)other;
            if (otherLP.getChildClass()!=this.getChildClass()) throw new IllegalArgumentException("setContentFrom: wrong parameter type : child class is:"+getChildClass() + " but should be: "+otherLP.getChildClass());
            else {
                //this.unMutableIndex = otherLP.getUnMutableIndex();
                //this.name=otherLP.getName();
                if (children==null) children = new ArrayList<>();
                this.children.clear();
                for (Parameter p : otherLP.getChildren()) {
                    T newP = createChildInstance(p.getName());
                    newP.setContentFrom(p);
                    insert(newP);
                }
                //for (int i = 0; i<otherLP.getChildCount(); i++) this.children.add((T)((otherLP.getChildAt(i)).duplicate()));
                //for (Parameter p : children) p.setParent(this);
                ui=null;
            }
        } else throw new IllegalArgumentException("wrong parameter type");
    }
    
    protected void setChildrenNumber(int number) {
        if (this.getChildCount()>number) {
            int c = this.getChildCount();
            for (int i = c; i>number; --i) remove(i-1);
        } else if (this.getChildCount()<number) {
            int c = this.getChildCount();
            for (int i = c; i<number; ++i) this.insert(createChildInstance());
        }
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
    public String toStringFull() {return name+":"+Utils.toStringList(children, p->p.toStringFull());}
    
    @Override
    public void insert(MutableTreeNode child, int index) {
        if (index>=getChildren().size()) children.add((T)child);
        else children.add(index, (T)child);
        child.setParent(this);
    }

    @Override
    public void insert(T... child) {
        for (T c : child) {
            getChildren().add(c);
            c.setParent(this);
        }
    }

    @Override
    public void remove(int index) {
        getChildren().remove(index);
    }

    @Override
    public void remove(MutableTreeNode node) {
        //System.out.println("removing node:"+((Parameter)node).toString() +" total number: "+children.size());
        logger.info("(list) removing node:"+((Parameter)node).toString() +" total number: "+children.size());
        getChildren().remove((T)node);
    }

    @Override
    public void setUserObject(Object object) {
        this.name=object.toString();
    }

    @Override
    public void removeFromParent() {
        logger.info("(list) removing node from parent:"+((Parameter)this).toString() +" total number: "+children.size());
        if (parent!=null) this.parent.remove(this);
    }
    
    @Override 
    public void removeAllElements() {
        if (this.unMutableIndex<0) children=new ArrayList<T>(children.size());
        else for (int i = getChildren().size()-1;i>unMutableIndex;--i) children.remove(i);
    }

    @Override
    public void setParent(MutableTreeNode newParent) {
        this.parent=(ContainerParameter)newParent;
    }
    
    @Override
    public T getChildByName(String name) { // returns the first occurence..
        if (children==null) {
            logger.error("no children for list: {}( child type:{})", name, this.childClassName);
        }
        for (T child : getChildren()) {
            if (name.equals(child.getName())) return child;
        }
        return null;
    }
    public int getIndex(String name) {
        T child = getChildByName(name);
        if (child==null) return -1;
        else return getIndex(child);
    }
    @Override
    public T getChildAt(int childIndex) {
        return getChildren().get(childIndex);
    }

    @Override
    public int getChildCount() {
        return getChildren().size();
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
        return getChildren().isEmpty();
    }

    @Override
    public Enumeration children() {
        return Collections.enumeration(getChildren());
    }
    
    @Override
    public ListParameterUI getUI() {
        if (ui==null) ui=new SimpleListParameterUI(this);
        return ui;
    }
    
    // morphium

    /*public void postLoad() {
        if (postLoaded) return;
        //logger.debug("LIST post load on : {}, of class: {}, alreadyPostLoaded: {}, parent: {}", name, this.getClass().getSimpleName(), postLoaded, parent!=null? parent.getName():null);
        for (Parameter p : children) {
            if (p!=null) {
                p.setParent(this);
                if (p instanceof PostLoadable) ((PostLoadable)p).postLoad();
            }
            else logger.error("postload parameter null: {}", getName());
        }
        postLoaded=true;
    }
    */

    
}
