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

import boa.gui.configuration.ConfigurationTreeModel;
import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.ChannelImageParameter;
import boa.configuration.parameters.Parameter;
import static boa.configuration.parameters.Parameter.logger;
import boa.configuration.parameters.ParameterUtils;
import boa.configuration.parameters.SimpleContainerParameter;
import boa.configuration.parameters.ParentStructureParameter;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.PreFilterSequence;
import boa.configuration.parameters.SimpleListParameter;
import boa.configuration.parameters.ui.NameEditorUI;
import boa.configuration.parameters.ui.ParameterUI;
import javax.swing.tree.MutableTreeNode;
import org.json.simple.JSONObject;
import boa.plugins.ManualSegmenter;
import boa.plugins.ObjectSplitter;
import boa.plugins.PreFilter;
import boa.plugins.ProcessingScheme;
import boa.plugins.Segmenter;
import boa.plugins.TrackCorrector;
import boa.plugins.Tracker;
import boa.plugins.plugins.manual_segmentation.WatershedManualSegmenter;
import boa.plugins.plugins.manual_segmentation.WatershedObjectSplitter;
import boa.plugins.plugins.pre_filters.ImageFeature;
import boa.plugins.plugins.processing_scheme.Duplicate;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */

public class Structure extends SimpleContainerParameter {
    ParentStructureParameter parentStructure;
    ParentStructureParameter segmentationParent;
    ChannelImageParameter channelImage;
    PluginParameter<ObjectSplitter> objectSplitter;
    PluginParameter<ManualSegmenter> manualSegmenter;
    PluginParameter<ProcessingScheme> processingScheme;
    BooleanParameter brightObject = new BooleanParameter("Object Type", "Bright object over dark background", "Dark object over bright background", false);
    BoundedNumberParameter manualObjectStrechThreshold = new BoundedNumberParameter("Manual Object Streching Threshold", 2, 0.5, 0, 1);
    BooleanParameter allowSplit = new BooleanParameter("Allow Split", "yes", "no", false);
    BooleanParameter allowMerge = new BooleanParameter("Allow Merge", "yes", "no", false);
    NameEditorUI ui;
    
    @Override
    public JSONObject toJSONEntry() {
        JSONObject res= new JSONObject();
        res.put("name", name);
        res.put("parentStructure", parentStructure.toJSONEntry());
        res.put("segmentationParent", segmentationParent.toJSONEntry());
        res.put("channelImage", channelImage.toJSONEntry());
        res.put("objectSplitter", objectSplitter.toJSONEntry());
        res.put("manualSegmenter", manualSegmenter.toJSONEntry());
        res.put("processingScheme", processingScheme.toJSONEntry());
        res.put("brightObject", brightObject.toJSONEntry());
        res.put("manualObjectStrechThreshold", manualObjectStrechThreshold.toJSONEntry());
        res.put("allowSplit", allowSplit.toJSONEntry());
        res.put("allowMerge", allowMerge.toJSONEntry());
        return res;
    }

    @Override
    public void initFromJSONEntry(Object jsonEntry) {
        JSONObject jsonO = (JSONObject)jsonEntry;
        name = (String)jsonO.get("name");
        parentStructure.initFromJSONEntry(jsonO.get("parentStructure"));
        segmentationParent.initFromJSONEntry(jsonO.get("segmentationParent"));
        channelImage.initFromJSONEntry(jsonO.get("channelImage"));
        objectSplitter.initFromJSONEntry(jsonO.get("objectSplitter"));
        manualSegmenter.initFromJSONEntry(jsonO.get("manualSegmenter"));
        processingScheme.initFromJSONEntry(jsonO.get("processingScheme"));
        brightObject.initFromJSONEntry(jsonO.get("brightObject"));
        manualObjectStrechThreshold.initFromJSONEntry(jsonO.get("manualObjectStrechThreshold"));
        allowSplit.initFromJSONEntry(jsonO.get("allowSplit"));
        allowMerge.initFromJSONEntry(jsonO.get("allowMerge"));
    }
    
    public Structure(String name, int parentStructure, int channelImage) {
        this(name, parentStructure, parentStructure, channelImage);
    }
    public Structure(String name, int parentStructure, int segmentationParentStructure, int channelImage) {
        super(name);
        this.parentStructure =  new ParentStructureParameter("Parent Structure", parentStructure, -1);
        segmentationParent =  new ParentStructureParameter("Segmentation Parent", segmentationParentStructure, -1);
        this.channelImage = new ChannelImageParameter("Channel Image", channelImage);
        objectSplitter = new PluginParameter<>("Object Splitter", ObjectSplitter.class, true).setEmphasized(false);
        processingScheme = new PluginParameter<>("Processing Scheme", ProcessingScheme.class, true).setEmphasized(false);
        manualSegmenter = new PluginParameter<>("Manual Segmenter", ManualSegmenter.class, true).setEmphasized(false);
        this.parentStructure.addListener((Parameter source) -> {
            Structure s = ParameterUtils.getFirstParameterFromParents(Structure.class, source, false);
            s.setMaxStructureIdx();
            int parentIdx = s.parentStructure.getSelectedIndex();
            s.setParentStructure(parentIdx);
            logger.debug("parent structure listener fired: parent: {}, seg parent: {}", s.parentStructure.getSelectedIndex(), s.segmentationParent.getSelectedIndex());
            //update tree
            ConfigurationTreeModel model = ParameterUtils.getModel(s.segmentationParent);
            if (model!=null) model.nodeChanged(s.segmentationParent);
            else logger.debug("no model found..");
        });
        segmentationParent.addListener((Parameter source) -> {
            Structure s = ParameterUtils.getFirstParameterFromParents(Structure.class, source, false);
            s.setMaxStructureIdx();
            s.setSegmentationParentStructure(s.segmentationParent.getSelectedIndex());
            logger.debug("segmentation parent structure listener fired: parent: {}, seg parent: {}", this.parentStructure.getSelectedIndex(), segmentationParent.getSelectedIndex());
            //update tree
            ConfigurationTreeModel model = ParameterUtils.getModel(s.segmentationParent);
            if (model!=null) model.nodeChanged(s.segmentationParent);
            else logger.debug("no model found..");
        });
        processingScheme.addListener((Parameter source) -> {
            Structure s = ParameterUtils.getFirstParameterFromParents(Structure.class, source, false);
            s.setMaxStructureIdx();
        });
        initChildList();
    }
    public Structure() {
        this("");
    }
    public Structure(String name) {
        this(name, -1, -1);
    }
    @Override 
    public boolean isEmphasized() {
        return false;
    }
    @Override
    protected void initChildList() {
        
        initChildren(parentStructure, segmentationParent, channelImage, processingScheme, objectSplitter, manualSegmenter, allowMerge, allowSplit, brightObject, manualObjectStrechThreshold); 
    }
    public Structure setBrightObject(boolean bright) {
        this.brightObject.setSelected(bright);
        return this;
    }
    public Structure setManualObjectStrechThreshold(double threshold) {
        this.manualObjectStrechThreshold.setValue(threshold);
        return this;
    }
    public boolean isBrightObject() {
        return this.brightObject.getSelected();
    }
    public double getManualObjectStrechThreshold() {
        return this.manualObjectStrechThreshold.getValue().doubleValue();
    }
    public boolean allowSplit() {
        return allowSplit.getSelected();
    }
    
    public boolean allowMerge() {
        return allowMerge.getSelected();
    }
    
    public Structure setAllowSplit(boolean allowSplit) {
        this.allowSplit.setSelected(allowSplit);
        return this;
    }
    
    public Structure setAllowMerge(boolean allowMerge) {
        this.allowMerge.setSelected(allowMerge);
        return this;
    }
    
    public ProcessingScheme getProcessingScheme() {
        return processingScheme.instanciatePlugin();
    }
    
    public void setProcessingScheme(ProcessingScheme ps) {
        this.processingScheme.setPlugin(ps);
    }
    
    public ObjectSplitter getObjectSplitter() {
        ObjectSplitter res = objectSplitter.instanciatePlugin();
        if (res == null) {
            ProcessingScheme ps = this.processingScheme.instanciatePlugin();
            if (ps!=null) {
                Segmenter s = ps.getSegmenter();
                if (s instanceof ObjectSplitter) return (ObjectSplitter)s;
            }
        }
        return res;
    }
    
    public ManualSegmenter getManualSegmenter() {
        ManualSegmenter res= manualSegmenter.instanciatePlugin();
        if (res == null) {
            ProcessingScheme ps = this.processingScheme.instanciatePlugin();
            if (ps!=null) {
                Segmenter s = ps.getSegmenter();
                if (s instanceof ManualSegmenter) return (ManualSegmenter)s;
            }
        }
        return res;
    }
    
    public void setManualSegmenter(ManualSegmenter manualSegmenter) {
        this.manualSegmenter.setPlugin(manualSegmenter);
    }
    
    public void setObjectSplitter(ObjectSplitter objectSplitter) {
        this.objectSplitter.setPlugin(objectSplitter);
    }
    
    public int getParentStructure() {
        return parentStructure.getSelectedIndex();
    }
    
    public int getSegmentationParentStructure() {
        return segmentationParent.getSelectedIndex()<parentStructure.getSelectedIndex() ? parentStructure.getSelectedIndex() : segmentationParent.getSelectedIndex();
    }
    
    public void setParentStructure(int parentIdx) {
        if (parentStructure.getSelectedIndex()!=parentIdx) parentStructure.setSelectedIndex(parentIdx); // avoid loop with listeners
        segmentationParent.setMaxStructureIdx(parentIdx+1);
        int segParent = segmentationParent.getSelectedIndex();
        if (segParent<parentIdx) segmentationParent.setSelectedIndex(parentIdx);
    }
    public void setSegmentationParentStructure(int segmentationParentStructureIdx) {
        if (segmentationParentStructureIdx<parentStructure.getSelectedStructureIdx()) segmentationParentStructureIdx = parentStructure.getSelectedStructureIdx();
        if (segmentationParentStructureIdx != segmentationParent.getSelectedStructureIdx()) segmentationParent.setSelectedIndex(segmentationParentStructureIdx);
    }
    
    
    public int getChannelImage() {
        return channelImage.getSelectedIndex();
    }
    
    public int getIndex() {
        return getParent().getIndex(this);
    }
    
    @Override 
    public void setParent(MutableTreeNode newParent) {
        super.setParent(newParent);
        setMaxStructureIdx();
    }
    
    protected void setMaxStructureIdx() {
        if (parent ==null) return;
        int idx = getIndex();
        parentStructure.setMaxStructureIdx(idx);
        segmentationParent.setMaxStructureIdx(idx);
        if (processingScheme.isOnePluginSet() && processingScheme.instanciatePlugin() instanceof Duplicate) {
            processingScheme.getParameters().stream().filter(p->p instanceof ParentStructureParameter).map(p->(ParentStructureParameter)p).forEach(p->p.setMaxStructureIdx(idx));
        }
    }
    
    @Override
    public ParameterUI getUI() {
        if (ui==null) ui=new NameEditorUI(this, false);
        return ui;
    }
}
