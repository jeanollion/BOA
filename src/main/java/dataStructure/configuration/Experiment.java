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
import configuration.parameters.MultipleChoiceParameter;
import configuration.parameters.Parameter;
import configuration.parameters.SimpleContainerParameter;
import configuration.parameters.SimpleListParameter;
import configuration.parameters.SimpleParameter;
import configuration.parameters.ui.ParameterUI;
import boa.gui.configuration.ConfigurationTreeModel;
import boa.gui.configuration.TreeModelContainer;
import dataStructure.containers.ImageDAO;
import dataStructure.containers.ImageDAOFactory;
import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Id;
import de.caluga.morphium.annotations.Index;
import de.caluga.morphium.annotations.Transient;
import de.caluga.morphium.annotations.caching.Cache;
import de.caluga.morphium.annotations.lifecycle.Lifecycle;
import de.caluga.morphium.annotations.lifecycle.PostLoad;
import java.util.ArrayList;
import org.bson.types.ObjectId;
import static utils.Utils.toArray;

/**
 *
 * @author jollion
 * 
 */

@Entity(collectionName = "Experiment", polymorph=false)
@Index(value="name", options="unique:1")
public class Experiment extends SimpleContainerParameter implements TreeModelContainer {
    @Id protected ObjectId id;
    protected FileChooser imagePath = new FileChooser("Output Image Path", FileChooserOption.DIRECTORIES_ONLY);
    SimpleListParameter<Structure> structures= new SimpleListParameter<Structure>("Structures", -1 , Structure.class);
    SimpleListParameter<ChannelImage> channelImages= new SimpleListParameter<ChannelImage>("Channel Images", 0 , ChannelImage.class);
    SimpleListParameter<MicroscopyField> fields= new SimpleListParameter<MicroscopyField>("Fields", 0 , MicroscopyField.class);
    ChoiceParameter importMethod = new ChoiceParameter("Import Method", ImportImageMethod.getChoices(), ImportImageMethod.BIOFORMATS.getMethod(), false);
    public enum ImageDAOTypes {LocalFileSystem}; //Simulation
    ImageDAOTypes imageDAOType=ImageDAOTypes.LocalFileSystem;
    
    @Transient ConfigurationTreeModel model;
    
    public Experiment(String name) {
        super(name);
        initChildList();
    }
    
    public Experiment(String name, Structure... defaultStructures) {
        super(name);
        for (Structure s : defaultStructures) structures.insert(s);
        structures.setUnmutableIndex(defaultStructures.length-1);
        initChildList();
    }
    
    public void setImageDAOType(ImageDAOTypes type) {
        this.imageDAOType=type;
    }
    public ImageDAO getImageDAO() {
        return ImageDAOFactory.getLocalFileSystemImageDAO(getOutputImageDirectory()); //if (imageDAOType.equals(ImageDAOTypes.LocalFileSystem))
    }
    
    protected void initChildList() {
        super.initChildren(importMethod, fields, channelImages, structures, imagePath);
    }
    
    public SimpleListParameter<MicroscopyField> getMicroscopyFields() {
        return fields;
    }
    
    public MicroscopyField getMicroscopyField(String fieldName) {
        return fields.getChildByName(fieldName);
    }
    
    public MicroscopyField getMicroscopyField(int fieldIdx) {
        return fields.getChildAt(fieldIdx);
    }
    
    public int getTimePointNumber() {
        if (fields.getChildCount()==0) return 0;
        MicroscopyField f= fields.getChildAt(0);
        if (f!=null && f.images!=null) {
            return f.images.getTimePointNumber();
        } else return 0;
    }
    
    public ObjectId getId() {return id;}
    
    public SimpleListParameter<ChannelImage> getChannelImages() {
        return channelImages;
    }
    
    public ImportImageMethod getImportImageMethod() {
        return ImportImageMethod.getValueOf(this.importMethod.getSelectedItem());
    }
    
    public String getOutputImageDirectory() {
        return imagePath.getFirstSelectedFilePath();
    }
    
    public void setOutputImageDirectory(String outputPath) {
        imagePath.setSelectedFilePath(outputPath);
    }
    
    public int[] getStructureToChannelCorrespondance() {
        int[] res = new int[structures.getChildCount()];
        for (int i = 0; i<res.length; i++) res[i] = getStructure(i).getChannelImage();
        return res;
    }
    
    public int getChannelImageIdx(int structureIdx) {return getStructure(structureIdx).getChannelImage();}
    
    public SimpleListParameter<Structure> getStructures() {return structures;}
    
    public Structure getStructure(int structureIdx) {
        return structures.getChildAt(structureIdx);
    }
    
    public int getStructureNB() {
        return structures.getChildCount();
    }
    
    public int getChannelImageNB() {
        return channelImages.getChildCount();
    }
    
    public int getMicrocopyFieldNB() {
        return fields.getChildCount();
    }
    
    
    
    public String[] getStructuresAsString() {return structures.getChildrenString();}
    
    public String[] getChannelImagesAsString() {return channelImages.getChildrenString();}
    
    public String[] getFieldsAsString() {return fields.getChildrenString();}
    
    /**
     * 
     * @param structureIdx
     * @return indexes of structures that are direct children of the structure of index {@param structureIdx}
     */
    public int[] getChildStructures(int structureIdx) {
        ArrayList<Integer> childrenAL = new ArrayList<Integer>(5);
        int idx = 0;
        for (Structure s : structures.getChildren()) {
            if (s.getParentStructure()==structureIdx) childrenAL.add(idx);
            idx++;
        }
        int [] childrenArray=new int[childrenAL.size()];
        idx = 0;
        for (int i : childrenAL) childrenArray[idx++]=i;
        return childrenArray;
    }
    /**
     * 
     * @param structureIdx
     * @return return the direct or indirect children of the structure of index: {@param structureIdx}
     */
    public int[] getAllChildStructures(int structureIdx) {
        ArrayList<Integer> allChildrenAL = new ArrayList<Integer>(10);
        int[][] orders = getStructuresInHierarchicalOrder();
        int startIdx = this.getHierachicalOrder(structureIdx)+1;
        for (int i = startIdx; i<orders.length; ++i) {
            int childrenAtCurrentOrderCount = 0;
            for (int s : orders[i]) {
                int p = getStructure(s).getParentStructure();
                if (p==structureIdx || allChildrenAL.contains(p)) allChildrenAL.add(s); childrenAtCurrentOrderCount++;
            }
            if (childrenAtCurrentOrderCount==0) break;
        }
        return toArray(allChildrenAL, false);
    }
    
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
        return getPathToStructure(-1, structureIdx);
    }
    /**
     * 
     * @param startStructureIdx start structure (excluded) anterior to {@param stopStructureIdx} in the structure hierarchy
     * @param stopStructureIdx stop structure (included) posterior to {@param stopStructureIdx} in the structure hierarchy
     * @return 
     */
    public int[] getPathToStructure(int startStructureIdx, int stopStructureIdx) {
        ArrayList<Integer> pathToStructure = new ArrayList<Integer>(this.getStructureNB());
        pathToStructure.add(stopStructureIdx);
        if (startStructureIdx!=stopStructureIdx) {
            int p = getStructure(stopStructureIdx).getParentStructure();
            while(p!=startStructureIdx) {
                if (p<0) return new int[0]; // no path found between structures
                pathToStructure.add(p);
                p=getStructure(p).getParentStructure();
            }
        }
        return toArray(pathToStructure, true);
    }
    
    /**
     * 
     * @return a matrix of structure indexes. the fisrt dimension represent the hierarchical orders, the second dimension the structures at the given hierarchical order, sorted by the index of the structure
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
    
    public int[] getStructuresInHierarchicalOrderAsArray() {
        int[][] so=getStructuresInHierarchicalOrder();
        int[] res = new int[this.getStructureNB()];
        int idx=0;
        for (int[] o : so) for (int s:o) res[idx++]=s;
        return res;
    }
    
    // model container methods
    
    @Override
    public ConfigurationTreeModel getModel() {
        return model;
    }

    @Override
    public void setModel(ConfigurationTreeModel model) {
        this.model=model;
    }

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
    
    // morphium
    public Experiment(){}
    public void callLazyLoading(){}
}
