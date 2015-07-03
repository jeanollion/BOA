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
package dataStructure.configuration;

import configuration.parameters.BooleanParameter;
import configuration.parameters.ChoiceParameter;
import configuration.parameters.ConditionalParameter;
import configuration.parameters.ContainerParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import configuration.parameters.SimpleContainerParameter;
import configuration.parameters.SimpleListParameter;
import configuration.parameters.SimpleParameter;
import configuration.parameters.ui.ParameterUI;
import configuration.userInterface.ConfigurationTreeModel;
import configuration.userInterface.TreeModelContainer;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Field;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Index;
import org.mongodb.morphia.annotations.IndexOptions;
import org.mongodb.morphia.annotations.Indexes;
import org.mongodb.morphia.annotations.PostLoad;
import org.mongodb.morphia.annotations.Transient;
import plugins.Thresholder;

/**
 *
 * @author jollion
 * 
 */

@Entity(value = "Experiment", noClassnameStored = true)
@Indexes(@Index(fields=@Field(value="name"), options=@IndexOptions(unique=true)))
public class Experiment implements ContainerParameter, TreeModelContainer {
    @Id protected ObjectId id;
    protected String name;
    //@Transient ChoiceParameter choiceCond = new ChoiceParameter("test cond",new String[]{"1", "2", "3"}, "1");
    @Transient ChoiceParameter choiceCond = new BooleanParameter("test cond", true);
    ConditionalParameter cond = new ConditionalParameter(choiceCond);
    @Transient PluginParameter cond1 = new PluginParameter("thres1", Thresholder.class);
    @Transient PluginParameter cond2 = new PluginParameter("thres2", Thresholder.class);
    SimpleListParameter structures= new SimpleListParameter("Structures", 1 , Structure.class);
    
    @Transient ConfigurationTreeModel model;
    @Transient protected ArrayList<Parameter> children;
    
    public Experiment(String name) {
        this.name=name;
        structures.insert(structures.createChildInstance("Channels"));
        structures.insert(structures.createChildInstance("Bacteries"));
        //cond.setAction("1", new Parameter[]{cond1});
        //cond.setAction("2", new Parameter[]{cond2});
        cond.setAction(true, new Parameter[]{cond1});
        cond.setAction(false, new Parameter[]{cond2});
        initChildren();
    }
    
    public SimpleListParameter getStructures() {return structures;}
    
    public Structure getStructure(int structureIdx) {
        return (Structure)structures.getChildAt(structureIdx);
    }
    
    public String[] getStructuresAsString() {return structures.getChildrenString();}
    
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
    public void setContentFrom(Parameter other) {
        if (other instanceof Experiment) {
            Experiment otherXP = (Experiment) other;
            this.structures.setContentFrom(otherXP.structures);
            cond.setContentFrom(otherXP.cond);
            // add other parameters here ...
        } else throw new IllegalArgumentException("wrong parameter type");
    }
    
    protected void initChildren() {
        children = new ArrayList<Parameter>(2);
        children.add(structures);
        children.add(cond);
        // add other parameters here ...
        for (Parameter p : children) p.setParent(this);
    }
    
    @Override
    public Experiment duplicate() {
        Experiment newXP = new Experiment(name);
        newXP.setContentFrom(this);
        return this;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public void setName(String name) {
        this.name=name;
    }
    
    @Override
    public ConfigurationTreeModel getModel() {
        return model;
    }

    @Override
    public void setModel(ConfigurationTreeModel model) {
        this.model=model;
    }
    
    
    
    @Override
    public ArrayList<Parameter> getPath() {
        return SimpleParameter.getPath(this);
    }

    @Override
    public void insert(MutableTreeNode child, int index) {}
    
    @Override
    public void remove(int index) {}

    @Override
    public void remove(MutableTreeNode node) {}

    @Override public void setUserObject(Object object) {this.name=object.toString();}

    @Override
    public void removeFromParent() { }

    @Override
    public void setParent(MutableTreeNode newParent) {}

    @Override
    public TreeNode getParent() {
        return null;
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
    public TreeNode getChildAt(int childIndex) {
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
    
    
    // morphia
    private Experiment(){}
    
    @PostLoad void postLoad() {initChildren();}

    

    

    
    
}
