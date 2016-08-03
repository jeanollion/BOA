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
import configuration.parameters.PluginParameter;
import dataStructure.containers.ImageDAO;
import dataStructure.containers.ImageDAOFactory;
import dataStructure.objects.MorphiumObjectDAO;
import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Id;
import de.caluga.morphium.annotations.Index;
import de.caluga.morphium.annotations.Transient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import measurement.MeasurementKey;
import measurement.MeasurementKeyObject;
import org.apache.commons.lang.ArrayUtils;
import org.bson.types.ObjectId;
import plugins.Measurement;
import utils.HashMapGetCreate;
import utils.Utils;
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
    protected FileChooser imagePath;
    SimpleListParameter<Structure> structures;
    SimpleListParameter<ChannelImage> channelImages;
    SimpleListParameter<PluginParameter<Measurement>> measurements;
    SimpleListParameter<MicroscopyField> fields;
    PreProcessingChain template;
    ChoiceParameter importMethod;
    public enum ImageDAOTypes {LocalFileSystem};
    ImageDAOTypes imageDAOType=ImageDAOTypes.LocalFileSystem;
    @Transient ConfigurationTreeModel model;
    
    public Experiment(String name) {
        super(name);
        structures= new SimpleListParameter<Structure>("Structures", -1 , Structure.class);
        imagePath = new FileChooser("Output Image Path", FileChooserOption.DIRECTORIES_ONLY);
        channelImages= new SimpleListParameter<ChannelImage>("Channel Images", 0 , ChannelImage.class);
        measurements = new SimpleListParameter<PluginParameter<Measurement>>("Measurements", -1 , new PluginParameter<Measurement>("Measurement", Measurement.class, true));
        fields= new SimpleListParameter<MicroscopyField>("Fields", -1 , MicroscopyField.class);
        template = new PreProcessingChain("Pre-Processing chain template");
        importMethod = new ChoiceParameter("Import Method", ImportImageMethod.getChoices(), ImportImageMethod.SINGLE_FILE.getMethod(), false);
        initChildList();
    }
    
    public Experiment(String name, Structure... defaultStructures) {
        this(name);
        for (Structure s : defaultStructures) structures.insert(s);
        structures.setUnmutableIndex(defaultStructures.length-1);
        initChildList();
    }
    
    public void setImportImageMethod(ImportImageMethod method) {this.importMethod.setValue(method.getMethod());}
    
    public void setImageDAOType(ImageDAOTypes type) {
        this.imageDAOType=type;
    }
    public ImageDAO getImageDAO() {
        return ImageDAOFactory.getLocalFileSystemImageDAO(getOutputImageDirectory()); //if (imageDAOType.equals(ImageDAOTypes.LocalFileSystem))
    }
    
    protected void initChildList() {
        super.initChildren(importMethod, template, fields, channelImages, structures, measurements, imagePath);
    }
    
    protected void checkInit() {
        if (children==null) initChildList();
    }
    
    public PreProcessingChain getPreProcessingTemplate() {
        return template;
    }
    
    /**
     * 
     * @param fieldName name of the MicroscopyField
     * @return a new MicroscopyField if no MicroscopyField named {@param fieldName} are already existing, null if not. 
     */
    public MicroscopyField createMicroscopyField(String fieldName) {
        checkInit();
        if (getMicroscopyField(fieldName)!=null) return null;
        MicroscopyField res =fields.createChildInstance(fieldName);
        fields.insert(res);
        res.setPreProcessingChains(template);
        return res;
    }
    
    public MicroscopyField getMicroscopyField(String fieldName) {
        checkInit();
        return fields.getChildByName(fieldName);
    }
    
    public MicroscopyField getMicroscopyField(int fieldIdx) {
        checkInit();
        return fields.getChildAt(fieldIdx);
    }   
   
    public ObjectId getId() {return id;}
    
    public SimpleListParameter<ChannelImage> getChannelImages() {
        checkInit();
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
    
    public void addMeasurement(Measurement... measurements) {
        for (Measurement m : measurements) this.measurements.insert(new PluginParameter<Measurement>("Measurement", Measurement.class, m, false));
    }
    
    public int[] getStructureToChannelCorrespondance() {
        int[] res = new int[structures.getChildCount()];
        for (int i = 0; i<res.length; i++) res[i] = getStructure(i).getChannelImage();
        return res;
    }
    
    public int getChannelImageIdx(int structureIdx) {checkInit(); return getStructure(structureIdx).getChannelImage();}
    
    public SimpleListParameter<Structure> getStructures() {checkInit(); return structures;}
    
    public Structure getStructure(int structureIdx) {
        checkInit();
        return structures.getChildAt(structureIdx);
    }
    
    public int getStructureCount() {
        return structures.getChildCount();
    }
    
    public int getChannelImageCount() {
        return channelImages.getChildCount();
    }
    
    public int getMicrocopyFieldCount() {
        return fields.getChildCount();
    }
    
    
    
    public String[] getStructuresAsString() {return structures.getChildrenString();}
    
    public String[] getChannelImagesAsString() {return channelImages.getChildrenString();}
    
    public String[] getFieldsAsString() {return fields.getChildrenString();}
    
    public String[] getChildStructuresAsString(int structureIdx) {
        int[] childIdx = getAllChildStructures(structureIdx);
        return getStructureNames(childIdx);
    }
    
    public String[] getStructureNames(int[] structureIndicies) {
        String[] res = new String[structureIndicies.length];
        for (int i = 0; i<res.length; ++i) {
            if (structureIndicies[i]<0) res[i]="root";
            else res[i] = this.getStructure(structureIndicies[i]).getName();
        }
        return res;
    }
    
    public int getFirstCommonParentStructureIdx(int structureIdx1, int structureIdx2) {
        while (structureIdx1>=0 && structureIdx2>=0) {
            if (structureIdx1>structureIdx2) structureIdx1 = getStructure(structureIdx1).getParentStructure();
            else if (structureIdx1<structureIdx2) structureIdx2 = getStructure(structureIdx2).getParentStructure();
            else return structureIdx1;
        }
        return -1;
    }
    
    public boolean isDirectChildOf(int parentStructureIdx, int childStructureIdx) {
        return this.getStructure(childStructureIdx).getParentStructure()==parentStructureIdx;
    }
    
    public boolean isChildOf(int parentStructureIdx, int childStructureIdx) {
        if (childStructureIdx<=parentStructureIdx) return false;
        else if (parentStructureIdx==-1) return true;
        Structure child = getStructure(childStructureIdx);
        while(true) {
            int p = child.getParentStructure();
            if (p==parentStructureIdx) return true;
            if (p<parentStructureIdx) return false;
            child = getStructure(p);
        }
    }
    
    public ArrayList<Integer> getAllDirectChildStructures(int parentStructureIdx) {
        int max = this.getStructureCount();
        ArrayList<Integer> res = new ArrayList<Integer>(max);
        for (int s = parentStructureIdx+1; s<max; ++s) {
            if (isDirectChildOf(parentStructureIdx, s)) res.add(s);
        }
        return res;
    }
    
    /**
     * 
     * @param structureIdx
     * @return indexes of structures that are direct children of the structure of index {@param structureIdx}
     */
    public int[] getAllDirectChildStructuresAsArray(int structureIdx) {
        return Utils.toArray(getAllDirectChildStructures(structureIdx), false);
    }
    /**
     * 
     * @param structureIdx
     * @return return the direct or indirect children of the structure of index: {@param structureIdx}
     */
    public int[] getAllChildStructures(int structureIdx) {
        if (structureIdx==-1) { // all structures
            int[] res = new int[this.getStructureCount()];
            for (int i = 1; i<res.length; ++i) res[i] = i;
            return res;
        }
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
     * @param startStructureIdx start structure (excluded), must be anterior to {@param stopStructureIdx} in the structure hierarchy
     * @param stopStructureIdx stop structure (included), must be posterior to {@param stopStructureIdx} in the structure hierarchy
     * @return 
     */
    public int[] getPathToStructure(int startStructureIdx, int stopStructureIdx) {
        ArrayList<Integer> pathToStructure = new ArrayList<Integer>(this.getStructureCount());
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
        int[] orders = new int[getStructureCount()];
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
        int[] res = new int[this.getStructureCount()];
        int idx=0;
        for (int[] o : so) for (int s:o) res[idx++]=s;
        return res;
    }
    
    // measurement-related methods
    
    public List<MeasurementKey> getAllMeasurementKeys() {
        if (this.measurements.getChildCount()==0) return Collections.emptyList();
        else {
            ArrayList<MeasurementKey> res= new ArrayList<MeasurementKey>();
            for (PluginParameter<Measurement> p : measurements.getActivatedChildren()) {
                Measurement m = p.instanciatePlugin();
                if (m!=null) res.addAll(m.getMeasurementKeys());
            }
            return res;
        }
    }
    
    public List<MeasurementKeyObject> getAllMeasurementKeyObject() {
        if (this.measurements.getChildCount()==0) return Collections.emptyList();
        else {
            ArrayList<MeasurementKeyObject> res= new ArrayList<MeasurementKeyObject>();
            for (PluginParameter<Measurement> p : measurements.getActivatedChildren()) {
                Measurement m = p.instanciatePlugin();
                if (m!=null) for (MeasurementKey k : m.getMeasurementKeys()) if (k instanceof MeasurementKeyObject) res.add((MeasurementKeyObject)k);
            }
            return res;
        }
    }
    
    public Map<Integer, String[]> getAllMeasurementNamesByStructureIdx(Class<? extends MeasurementKey> classFilter, int... structures) {
        HashMapGetCreate<Integer, ArrayList<String>> map = new HashMapGetCreate<Integer, ArrayList<String>>(this.getStructureCount(), new HashMapGetCreate.ArrayListFactory<Integer, String>());
        List<MeasurementKey> allKeys = getAllMeasurementKeys();
        for (MeasurementKey k : allKeys) {
            if (classFilter==null || classFilter.equals(k.getClass())) {
                if (structures.length==0 || ArrayUtils.contains(structures, k.getStoreStructureIdx())) map.getAndCreateIfNecessary(k.getStoreStructureIdx()).add(k.getKey());
            }
        }
        Map<Integer, String[]> mapRes = new HashMap<Integer, String[]>(map.size());
        for (Entry<Integer, ArrayList<String>> e : map.entrySet()) mapRes.put(e.getKey(), e.getValue().toArray(new String[e.getValue().size()]));
        return mapRes;
    }
    
    public Map<Integer, List<Measurement>> getMeasurementsByCallStructureIdx(int... structureIdx) {
        if (this.measurements.getChildCount()==0) return Collections.emptyMap();
        else {
            HashMapGetCreate<Integer, List<Measurement>> res = new HashMapGetCreate<Integer, List<Measurement>>(structureIdx.length>0?structureIdx.length : this.getStructureCount(), new HashMapGetCreate.ListFactory<Integer, Measurement>());
            for (PluginParameter<Measurement> p : measurements.getActivatedChildren()) {
                Measurement m = p.instanciatePlugin();
                if (m!=null) {
                    if (structureIdx.length==0 || contains(structureIdx, m.getCallStructure())) {
                        res.getAndCreateIfNecessary(m.getCallStructure()).add(m);
                    }
                }
            }
            return res;
        }
    }
    
    
    private static boolean contains(int[] structures, int structureIdx) {
        for (int s: structures) if (s==structureIdx) return true;
        return false;
    }
    
    // model container methods, for configuration tree in GUI
    
    @Override
    public ConfigurationTreeModel getModel() {
        return model;
    }

    @Override
    public void setModel(ConfigurationTreeModel model) {
        this.model=model;
    }

    public enum ImportImageMethod {
        SINGLE_FILE("Single-file"),
        ONE_FILE_PER_CHANNEL_AND_FIELD("One File Per Channel And Position"),
        ONE_FILE_PER_CHANNEL_TIME_POSITION("One File Per Position, Channel And Frame");
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
