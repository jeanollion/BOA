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
package boa.configuration.experiment;

import boa.gui.GUI;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.ContainerParameter;
import boa.configuration.parameters.FileChooser;
import boa.configuration.parameters.FileChooser.FileChooserOption;
import boa.configuration.parameters.MultipleChoiceParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.SimpleContainerParameter;
import boa.configuration.parameters.SimpleListParameter;
import boa.configuration.parameters.SimpleParameter;
import boa.configuration.parameters.ui.ParameterUI;
import boa.gui.configuration.ConfigurationTreeModel;
import boa.gui.configuration.TreeModelContainer;
import boa.configuration.parameters.ChannelImageParameter;
import boa.configuration.parameters.ConditionalParameter;
import boa.configuration.parameters.GroupParameter;
import static boa.configuration.parameters.Parameter.logger;
import boa.configuration.parameters.ParameterUtils;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.TextParameter;
import boa.data_structure.dao.ImageDAO;
import boa.data_structure.dao.ImageDAOFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import boa.measurement.MeasurementKey;
import boa.measurement.MeasurementKeyObject;
import org.apache.commons.lang.ArrayUtils;
import org.json.simple.JSONObject;
import boa.plugins.Autofocus;
import boa.plugins.Measurement;
import boa.plugins.plugins.transformations.SelectBestFocusPlane;
import boa.utils.HashMapGetCreate;
import boa.utils.Pair;
import boa.utils.Utils;
import static boa.utils.Utils.toArray;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 *
 * @author jollion
 * 
 */

public class Experiment extends SimpleContainerParameter implements TreeModelContainer {
    protected FileChooser imagePath = new FileChooser("Output Image Path", FileChooserOption.DIRECTORIES_ONLY);
    protected FileChooser outputPath = new FileChooser("Output Path", FileChooserOption.DIRECTORIES_ONLY);
    SimpleListParameter<ChannelImage> channelImages= new SimpleListParameter<>("Channel Images", 0 , ChannelImage.class);
    SimpleListParameter<Structure> structures= new SimpleListParameter<>("Structures", -1 , Structure.class);
    SimpleListParameter<PluginParameter<Measurement>> measurements = new SimpleListParameter<>("Measurements", -1 , new PluginParameter<Measurement>("Measurements", Measurement.class, true));
    SimpleListParameter<Position> positions= new SimpleListParameter<>("Positions", -1 , Position.class).setAllowMoveChildren(false);
    PreProcessingChain template = new PreProcessingChain("Pre-Processing chain template");
    ChoiceParameter importMethod = new ChoiceParameter("Import Method", ImportImageMethod.getChoices(), ImportImageMethod.SINGLE_FILE.getMethod(), false);
    TextParameter positionSeparator = new TextParameter("Position Separator", "xy", true);
    ConditionalParameter importCond = new ConditionalParameter(importMethod).setActionParameters(ImportImageMethod.ONE_FILE_PER_CHANNEL_TIME_POSITION.getMethod(), new Parameter[]{positionSeparator});
    ChannelImageParameter bestFocusPlaneChannel = new ChannelImageParameter("Channel", 0, true).setToolTipText("Channel for best focus plane computation");
    PluginParameter<Autofocus> autofocus = new PluginParameter<>("Algorithm", Autofocus.class, new SelectBestFocusPlane(), true);
    GroupParameter bestFocusPlane = new GroupParameter("Best Focus plane computation", new Parameter[]{bestFocusPlaneChannel, autofocus});
    
    public enum ImageDAOTypes {LocalFileSystem};
    ImageDAOTypes imageDAOType=ImageDAOTypes.LocalFileSystem;
    ConfigurationTreeModel model;
    
    @Override
    public JSONObject toJSONEntry() {
        JSONObject res= new JSONObject();
        res.put("imagePath", imagePath.toJSONEntry());
        res.put("outputPath", outputPath.toJSONEntry());
        res.put("channelImages", channelImages.toJSONEntry());
        res.put("structures", structures.toJSONEntry());
        res.put("measurements", measurements.toJSONEntry());
        res.put("positions", positions.toJSONEntry());
        res.put("template", template.toJSONEntry());
        res.put("importMethod", importCond.toJSONEntry());
        res.put("bestFocusPlane", bestFocusPlane.toJSONEntry());
        return res;
    }

    @Override
    public void initFromJSONEntry(Object jsonEntry) {
        JSONObject jsonO = (JSONObject)jsonEntry;
        imagePath.initFromJSONEntry(jsonO.get("imagePath"));
        outputPath.initFromJSONEntry(jsonO.get("outputPath"));
        channelImages.initFromJSONEntry(jsonO.get("channelImages"));
        structures.initFromJSONEntry(jsonO.get("structures"));
        measurements.initFromJSONEntry(jsonO.get("measurements"));
        positions.initFromJSONEntry(jsonO.get("positions"));
        template.initFromJSONEntry(jsonO.get("template"));
        if (jsonO.get("importMethod") instanceof JSONObject) importCond.initFromJSONEntry(jsonO.get("importMethod"));
        else importMethod.initFromJSONEntry(jsonO.get("importMethod")); // RETRO COMPATIBILITY
        bestFocusPlane.initFromJSONEntry(jsonO.get("bestFocusPlane"));
        this.name="Configuration";
    }
    public Experiment(){
        this("");
    }
    
    public Experiment(String name) {
        super(name);
        outputPath.addListener((Parameter source) -> {
            Experiment xp = ((Experiment)source);
            if (xp.outputPath.getFirstSelectedFilePath()==null) return;
            if (xp.imagePath.getFirstSelectedFilePath()==null) xp.imagePath.setSelectedFilePath(xp.outputPath.getFirstSelectedFilePath());
            if (GUI.hasInstance()) {
                logger.debug("new output directory set : {}", xp.outputPath.getFirstSelectedFilePath());
                GUI.getInstance().outputDirectoryUpdated();
            }
        });
        structures.addListener((Parameter source) -> ((SimpleListParameter<Structure>)source).getChildren().stream().forEachOrdered((s) -> s.setMaxStructureIdx()));
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
        super.initChildren(importCond, template, positions, channelImages, structures, measurements, outputPath, imagePath, bestFocusPlane);
    }
    
    public PreProcessingChain getPreProcessingTemplate() {
        return template;
    }
    
    /**
     * 
     * @param fieldName name of the MicroscopyField
     * @return a new MicroscopyField if no MicroscopyField named {@param fieldName} are already existing, null if not. 
     */
    public Position createPosition(String fieldName) {
        if (getPosition(fieldName)!=null) return null;
        Position res =positions.createChildInstance(fieldName);
        positions.insert(res);
        res.setPreProcessingChains(template);
        return res;
    }
    
    public Position getPosition(String fieldName) {
        return positions.getChildByName(fieldName);
    }
    
    public Position getPosition(int fieldIdx) {
        return positions.getChildAt(fieldIdx);
    }
    
    public List<Position> getPositions() {
        return positions.getChildren();
    }
    
    public Pair<Integer, Autofocus> getFocusChannelAndAlgorithm() {
        if (this.bestFocusPlaneChannel.getSelectedIndex()<0 || !autofocus.isOnePluginSet()) return null;
        return new Pair<>(this.bestFocusPlaneChannel.getSelectedIndex(), this.autofocus.instanciatePlugin());
    }
    
    public void flushImages(boolean raw, boolean preProcessed, String... excludePositions) {
        List<String> pos = new ArrayList<>(Arrays.asList(getPositionsAsString()));
        pos.removeAll(Arrays.asList(excludePositions));
        for (String p : pos)  getPosition(p).flushImages(raw, preProcessed);
    }
   
    public SimpleListParameter<ChannelImage> getChannelImages() {
        return channelImages;
    }
    
    public ImportImageMethod getImportImageMethod() {
        return ImportImageMethod.getValueOf(this.importMethod.getSelectedItem());
    }
    
    public String getImportImagePositionSeparator() {
        return positionSeparator.getValue();
    }
    
    public String getOutputDirectory() {
        return outputPath.getFirstSelectedFilePath();
    }
    
    public void setOutputDirectory(String outputPath) {
        this.outputPath.setSelectedFilePath(outputPath);
        if (outputPath!=null) {
            File f = new File(outputPath);
            f.mkdirs();
        }
    }
    
    public String getOutputImageDirectory() {
        return imagePath.getFirstSelectedFilePath();
    }
    
    public void setOutputImageDirectory(String outputPath) {
        imagePath.setSelectedFilePath(outputPath);
        if (outputPath!=null) {
            File f = new File(outputPath);
            f.mkdirs();
        }
    }
    
    
    
    public void clearPositions() {
        this.positions.removeAllElements();
    }
    public void clearMeasurements() {
        this.measurements.removeAllElements();
    }
    public void addMeasurement(Measurement... measurements) {
        for (Measurement m : measurements) this.measurements.insert(new PluginParameter<Measurement>("Measurement", Measurement.class, m, false));
    }
    
    public int[] getStructureToChannelCorrespondance() {
        int[] res = new int[structures.getChildCount()];
        for (int i = 0; i<res.length; i++) res[i] = getStructure(i).getChannelImage();
        return res;
    }
    public HashMap<Integer, List<Integer>> getChannelToStructureCorrespondance() {
        HashMapGetCreate<Integer, List<Integer>> res = new HashMapGetCreate<>(new HashMapGetCreate.ListFactory());
        for (int s = 0; s<getStructureCount(); s++) res.getAndCreateIfNecessary(getStructure(s).getChannelImage()).add(s);
        return res;
    }
    
    public int getChannelImageIdx(int structureIdx) {return getStructure(structureIdx).getChannelImage();}
    
    public SimpleListParameter<Structure> getStructures() {return structures;}
    
    public Structure getStructure(int structureIdx) {
        return structures.getChildAt(structureIdx);
    }
    
    public int getStructureCount() {
        return structures.getChildCount();
    }
    
    public int getStructureIdx(String name) {
        int i = 0;
        for (Structure s: structures.getChildren()) {
            if (s.getName().equals(name)) return i;
            i++;
        }
        return -2;
    }
    
    public int getChannelImageCount() {
        return channelImages.getChildCount();
    }
    
    public int getPositionCount() {
        return positions.getChildCount();
    }
    
    public int getPositionIdx(String positionName) {
        return positions.getIndex(positionName);
    }
    
    public String[] getStructuresAsString() {return structures.getChildrenString();}
    
    public String[] getChannelImagesAsString() {return channelImages.getChildrenString();}
    
    public String[] getPositionsAsString() {return positions.getChildrenString();}
    
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
        if (childStructureIdx<=parentStructureIdx) return false;
        else if (childStructureIdx==-1) return false;
        return getStructure(childStructureIdx).getParentStructure()==parentStructureIdx;
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
        return IntStream.range(structureIdx+1, getStructureCount()).filter(s->isChildOf(structureIdx, s)).toArray();
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
        ArrayList<Integer> pathToStructure = new ArrayList<>(this.getStructureCount());
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
    public SimpleListParameter<PluginParameter<Measurement>> getMeasurements() { return measurements;}
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
            HashMapGetCreate<Integer, List<Measurement>> res = new HashMapGetCreate<>(structureIdx.length>0?structureIdx.length : this.getStructureCount(), new HashMapGetCreate.ListFactory<Integer, Measurement>());
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
    public Stream<Measurement> getMeasurements(int structureIdx) {
        return measurements.getChildren().stream().filter(pp->pp.isActivated() && pp.isOnePluginSet()).map(pp->pp.instanciatePlugin()).filter(m->m.getCallStructure()==structureIdx);
    }
    
    private static boolean contains(int[] structures, int structureIdx) {
        for (int s: structures) if (s==structureIdx) return true;
        return false;
    }
    
    // model container methods, for configuration tree in GUI
    // TODO: parameters independent of UI. No access to ConfigurationTreeModel nor UI. Make an independent mapping of parameters/UI
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
    
    
}
