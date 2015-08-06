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

import configuration.parameters.ui.ParameterUI;
import de.caluga.morphium.annotations.Embedded;
import de.caluga.morphium.annotations.Transient;
import de.caluga.morphium.annotations.caching.Cache;
import de.caluga.morphium.annotations.lifecycle.Lifecycle;
import de.caluga.morphium.annotations.lifecycle.PostLoad;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
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
public abstract class SimpleContainerParameter implements ContainerParameter {
    protected String name;
    @Transient protected ContainerParameter parent;
    @Transient protected ArrayList<Parameter> children;

    public SimpleContainerParameter(String name) {
        this.name=name;
    }
    
    protected void initChildren(ArrayList<Parameter> parameters) {
        if (parameters==null) {
            children = new ArrayList<Parameter>(0);
        } else {
            children = new ArrayList<Parameter>(parameters.size());
            children.addAll(parameters);
            int idx = 0;
            for (Parameter p : parameters) {
                if (p==null) System.out.println("param null:"+idx);
                p.setParent(this);
                idx++;
            }
        }
    }
    
    protected void initChildren(Parameter... parameters) {
        if (parameters==null) {
            children = new ArrayList<Parameter>(0);
        } else {
            children = new ArrayList<Parameter>(parameters.length);
            children.addAll(Arrays.asList(parameters));
            int idx = 0;
            for (Parameter p : parameters) {
                if (p==null) System.out.println("param null:"+idx);
                p.setParent(this);
                idx++;
            }
        }
    }
    
    protected abstract void initChildList();
    
    @Override
    public void setContentFrom(Parameter other) {
        if (other instanceof SimpleContainerParameter) {
            SimpleContainerParameter otherP = (SimpleContainerParameter) other;
            for (int i = 0; i<children.size(); i++) children.get(i).setContentFrom((Parameter)otherP.getChildAt(i));
        } else {
            throw new IllegalArgumentException("wrong parameter type");
        }
    }
    
    @Override
    public Parameter duplicate() {
        try {
            Parameter p = (Parameter) this.getClass().getDeclaredConstructor(String.class).newInstance(name);
            p.setContentFrom(this);
            return p;
        } catch (NoSuchMethodException ex) {
            Logger.getLogger(SimpleListParameter.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SecurityException ex) {
            Logger.getLogger(SimpleListParameter.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            Logger.getLogger(SimpleListParameter.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(SimpleListParameter.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(SimpleListParameter.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            Logger.getLogger(SimpleListParameter.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
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
    public ArrayList<Parameter> getPath() {
        return SimpleParameter.getPath(this);
    }
    
    @Override
    public boolean sameContent(Parameter other) {
        if (other instanceof ContainerParameter) {
            ContainerParameter otherLP = (ContainerParameter)other;
            if (otherLP.getChildCount()==this.getChildCount()) {
                for (int i = 0; i<getChildCount(); i++) {
                    if (!((Parameter)this.getChildAt(i)).sameContent((Parameter)otherLP.getChildAt(i))) return false;
                }
                return true;
            } else return false;
        } else return false;
    }

    @Override
    public void insert(MutableTreeNode child, int index) {}
    
    @Override
    public void remove(int index) {}

    @Override
    public void remove(MutableTreeNode node) {}

    @Override public void setUserObject(Object object) {this.name=object.toString();}

    @Override
    public void removeFromParent() {
        parent.remove(this);
    }

    @Override
    public void setParent(MutableTreeNode newParent) {
        this.parent=(ContainerParameter)newParent;
    }

    @Override
    public ContainerParameter getParent() {
        return parent;
    }

    @Override
    public boolean getAllowsChildren() {
        return true;
    }

    @Override
    public boolean isLeaf() {
        return false;
    }
    
    @Override
    public String toString() {return name;}
    
    @Override
    public ParameterUI getUI() {
        return null;
    }

    @Override
    public Parameter getChildAt(int childIndex) {
        return children.get(childIndex);
    }

    @Override
    public int getChildCount() {
        return children.size();
    }

    @Override
    public int getIndex(TreeNode node) {
        return children.indexOf((Parameter)node);
    }

    @Override
    public Enumeration children() {
        return Collections.enumeration(children);
    }
    
    // morphium
    @PostLoad public void postLoad() {initChildList();}
    protected SimpleContainerParameter() {}
}
