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
package boa.configuration.parameters;

import boa.utils.Utils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import org.scijava.module.ModuleItem;

/**
 *
 * @author jollion
 */

public abstract class SimpleParameter implements Parameter {
    protected String name;
    private ContainerParameter parent;
    
    
    protected SimpleParameter(String name) {
        this.name=name;
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
    
    @Override
    public String getName(){
        return name;
    }
    
    @Override
    public void setName(String name) {
        this.name=name;
    }
    
    @Override
    public String toStringFull() {return toString();}
    
    @Override
    public <T extends Parameter> T duplicate() {
        try {
                SimpleParameter p = this.getClass().getDeclaredConstructor(String.class).newInstance(name);
                p.setContentFrom(this);
                p.setListeners(listeners);
                return (T)p;
            } catch (Exception ex) {
                try {
                    SimpleParameter p = (SimpleParameter)this.getClass().newInstance();
                    p.setName(name);
                    p.setContentFrom(this);
                    p.setListeners(listeners);
                return (T)p;
                } catch (Exception ex2) {
                    logger.error("duplicate Simple Parameter", ex2);
                }
            }
        return null;
    }
    
    @Override
    public TreeNode getChildAt(int childIndex) {
        return null;
    }

    @Override
    public int getChildCount() {
        return 0;
    }

    @Override
    public TreeNode getParent() {
        return parent;
    }

    @Override
    public int getIndex(TreeNode node) {
        return 0;
    }

    @Override
    public boolean getAllowsChildren() {
        return false;
    }

    @Override
    public boolean isLeaf() {
        return true;
    }

    @Override
    public Enumeration children() {
        return Collections.emptyEnumeration();
    }

    @Override
    public void insert(MutableTreeNode child, int index) {}

    @Override
    public void remove(int index) {}

    @Override
    public void remove(MutableTreeNode node) {}

    @Override
    public void setUserObject(Object object) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        //this.name=object.toString();
    }

    @Override
    public void removeFromParent() {
        if (parent!=null) parent.remove(this);
    }

    @Override
    public void setParent(MutableTreeNode newParent) {
        if (newParent==null) parent=null;
        else this.parent=(ContainerParameter)newParent;
    }

    @Override
    public ArrayList<Parameter> getPath() {
        return getPath(this);
    }
    
    public static ArrayList<Parameter> getPath(Parameter p){
        if (p.getParent()==null) {
            ArrayList<Parameter> res = new ArrayList<Parameter>();
            res.add(p);
            return res;
        }
        else {
            ArrayList<Parameter> path = ((Parameter)p.getParent()).getPath();
            path.add(p);
            return path;
        }
    }
    
    @Override
    public String toString() {
        return name;
    }
    
    // listenable
    ArrayList<Consumer<Parameter>> listeners;
    protected boolean bypassListeners;
    public void addListener(Consumer<Parameter> listener) {
        if (listeners == null) listeners = new ArrayList<>();
        listeners.add(listener);
    }
    public void removeListener(Consumer<Parameter> listener) {
        if (listeners != null) listeners.remove(listener);
    }
    public void setListeners(List<Consumer<Parameter>> listeners) {
        if (listeners ==null ) this.listeners=null;
        else this.listeners = new ArrayList<>(listeners);
    }
    public void fireListeners() {
        if (!bypassListeners && listeners != null) for (Consumer<Parameter> pl : listeners) pl.accept(this);
    }
    // op
    ModuleItem<?> param;
    public void setModuleItem(ModuleItem<?> param) {this.param=param;}
    public ModuleItem<?> getModuleItem(){return param;}
}
