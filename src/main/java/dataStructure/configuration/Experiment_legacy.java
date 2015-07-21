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

import configuration.parameters.ChoiceParameter;
import configuration.parameters.ContainerParameter;
import configuration.parameters.FileChooser;
import configuration.parameters.FileChooser.FileChooserOption;
import configuration.parameters.Parameter;
import configuration.parameters.SimpleListParameter;
import configuration.parameters.SimpleParameter;
import configuration.parameters.ui.ParameterUI;
import configuration.userInterface.ConfigurationTreeModel;
import configuration.userInterface.TreeModelContainer;
import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Id;
import de.caluga.morphium.annotations.Index;
import de.caluga.morphium.annotations.Transient;
import de.caluga.morphium.annotations.lifecycle.Lifecycle;
import de.caluga.morphium.annotations.lifecycle.PostLoad;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import org.bson.types.ObjectId;

/**
 *
 * @author jollion
 * 
 */

//@Indexes(@Index(fields=@Field(value="name"), options=@IndexOptions(unique=true)))
@Entity(collectionName = "Experiment", polymorph=false)
@Index(value="name", options="unique:1")
@Lifecycle
public class Experiment_legacy implements ContainerParameter, TreeModelContainer {
    @Id protected ObjectId id;
    protected String name;
    protected FileChooser imagePath = new FileChooser("Output Image Path", FileChooserOption.DIRECTORIES_ONLY);
    SimpleListParameter<Structure> structures= new SimpleListParameter("Structures", -1 , Structure.class);
    SimpleListParameter<ChannelImage> channelImages= new SimpleListParameter("Channel Images", 0 , ChannelImage.class);
    SimpleListParameter<MicroscopyField> fields= new SimpleListParameter("Fields", 0 , MicroscopyField.class);
    ChoiceParameter importMethod = new ChoiceParameter("Import Method", ImportImageMethod.getChoices(), ImportImageMethod.BIOFORMATS.getMethod(), false);
    
    
    @Transient ConfigurationTreeModel model;
    @Transient protected ArrayList<Parameter> children;
    
    public Experiment_legacy(String name) {
        this.name=name;
        Structure channels = new Structure("Channels", -1);
        structures.insert(channels);
        Structure bacteries = new Structure("Bacteries", 0);
        structures.insert(bacteries);
        channelImages.insert(new ChannelImage());
        initChildren();
    }
    
    public Experiment_legacy(String name, Structure... defaultStructures) {
        this.name=name;
        for (Structure s : defaultStructures) structures.insert(s);
        structures.setUnmutableIndex(defaultStructures.length-1);
        channelImages.insert(new ChannelImage("Channel1"));
        initChildren();
    }
    
    protected void initChildren() {
        children = new ArrayList<Parameter>(2);
        children.add(importMethod);
        children.add(channelImages);
        children.add(structures);
        children.add(imagePath);
        // add other parameters here ...
        for (Parameter p : children) p.setParent(this);
    }
    
    public SimpleListParameter<MicroscopyField> getMicroscopyFields() {
        return fields;
    }
    
    public MicroscopyField getMicroscopyField(int fieldIdx) {
        return (MicroscopyField)fields.getChildAt(fieldIdx);
    }
    
    public SimpleListParameter getChannelImages() {
        return channelImages;
    }
    
    public ImportImageMethod getImportImageMethod() {
        return ImportImageMethod.getValueOf(this.importMethod.getSelectedItem());
    }
    
    public String getOutputImageDirectory() {
        return imagePath.getFirstSelectedFilePath();
    }
    
    public int[] getStructureToChannelCorrespondance() {
        int[] res = new int[structures.getChildCount()];
        for (int i = 0; i<res.length; i++) res[i] = getStructure(i).getChannelImage();
        return res;
    }
    
    public SimpleListParameter getStructures() {return structures;}
    
    public Structure getStructure(int structureIdx) {
        return (Structure)structures.getChildAt(structureIdx);
    }
    
    public int getStructureNB() {
        return structures.getChildCount();
    }
    
    public int getChannelImageNB() {
        return channelImages.getChildCount();
    }
    
    public String[] getStructuresAsString() {return structures.getChildrenString();}
    
    public String[] getChannelImagesAsString() {return channelImages.getChildrenString();}
    
    /**
     * 
     * @param structureIdx
     * @return the number of parent before the root (0 if the parent is the root)
     */
    public int getHierachicalOrder(int structureIdx) {
        int order=0;
        int p = getStructure(structureIdx).getParentStructure();
        while(p>=0) {
            p=getStructure(p).getParentStructure();
            ++order;
        }
        return order;
    }
    /**
     * 
     * @param structureIdx
     * @return an array of structure index, starting from the first structure after the root structure, ending at the structure index (included)
     */
    public int[] getPathToRoot(int structureIdx) {
        ArrayList<Integer> pathToRoot = new ArrayList<Integer>(this.getStructureNB());
        int p = getStructure(structureIdx).getParentStructure();
        pathToRoot.add(structureIdx);
        while(p>=0) {
            pathToRoot.add(p);
            p=getStructure(p).getParentStructure();
        }
        int[] res = new int[pathToRoot.size()];
        int idx = res.length-1;
        for (int s : pathToRoot) res[idx--] = s;
        return res;
    }
    
    /**
     * 
     * @return a matrix of structure indexes. the fisrt dimension represent the hierachical orders, the second dimension the structures at the given hierarchical order, sorted by the index of the structure
     */
    public int[][] getStructuresInHierarchicalOrder() {
        int[] orders = new int[getStructureNB()];
        int maxOrder=0;
        for (int i = 0; i<orders.length;++i) {
            orders[i]=getHierachicalOrder(i);
            if (orders[i]>maxOrder) maxOrder=orders[i];
        }
        int[] resCount = new int[maxOrder+1];
        for (int i = 0; i<orders.length;++i) resCount[orders[i]]++;
        int[][] res = new int[maxOrder+1][];
        int[] idx = new int[maxOrder+1];
        for (int i = 0; i<resCount.length;++i) res[i]=new int[resCount[i]];
        for (int i = 0; i<orders.length;++i) {
            res[orders[i]][idx[orders[i]]]=i;
            idx[orders[i]]++;
        }
        return res;
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
    public void setContentFrom(Parameter other) {
        if (other instanceof Experiment_legacy) {
            Experiment_legacy otherP = (Experiment_legacy) other;
            for (int i = 0; i<children.size(); i++) children.get(i).setContentFrom((Parameter)otherP.getChildAt(i));
        } else {
            throw new IllegalArgumentException("wrong parameter type");
        }
    }
    
    @Override
    public Experiment_legacy duplicate() {
        Experiment_legacy newXP = new Experiment_legacy(name);
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
    public Experiment_legacy(){}
    
    @PostLoad public void postLoad() {initChildren();}
    
    public enum ImportImageMethod {
        BIOFORMATS("Bio-Formats");
        private final String name;
        ImportImageMethod(String name) {
            this.name=name;
        }
        @Override
        public String toString() {return name;}
        public String getMethod(){return name;}
        public static String[] getChoices() {
            ImportImageMethod[] all = ImportImageMethod.values();
            String[] res = new String[all.length];
            int i = 0;
            for (ImportImageMethod m : all) res[i++]=m.name;
            return res;
        }
        public static ImportImageMethod getValueOf(String method) {
            for (ImportImageMethod m : ImportImageMethod.values()) if (m.getMethod().equals(method)) return m;
            return null;
        }
        /*public static ImportImageMethod getMethod(String name) {
            if (BioFormats.getMethod().equals(name)) return BioFormats;
            else return null;
        }*/
    }
    

    
    
}
