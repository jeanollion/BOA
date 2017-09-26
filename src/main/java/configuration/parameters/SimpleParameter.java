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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;

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
                Parameter p = this.getClass().getDeclaredConstructor(String.class).newInstance(name);
                p.setContentFrom(this);
                return (T)p;
            } catch (Exception ex) {
                try {
                    SimpleParameter p = (SimpleParameter)this.getClass().newInstance();
                    p.setName(name);
                    p.setContentFrom(this);
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
        this.parent=(ContainerParameter)newParent;
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
    ArrayList<ParameterListener> listeners;
    public void addListener(ParameterListener listener) {
        if (listeners == null) listeners = new ArrayList<>();
        listeners.add(listener);
    }
    public void removeListener(ParameterListener listener) {
        if (listeners != null) listeners.remove(listener);
    }
    public void fireListeners() {
        if (listeners != null) for (ParameterListener pl : listeners) pl.fire(this);
    }
    
}
