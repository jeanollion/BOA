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

import boa.gui.configuration.ConfigurationTreeModel;
import configuration.parameters.BooleanParameter;
import configuration.parameters.ChannelImageParameter;
import configuration.parameters.Parameter;
import static configuration.parameters.Parameter.logger;
import configuration.parameters.ParameterListener;
import configuration.parameters.ParameterUtils;
import configuration.parameters.SimpleContainerParameter;
import configuration.parameters.ParentStructureParameter;
import configuration.parameters.PluginParameter;
import configuration.parameters.PreFilterSequence;
import configuration.parameters.ui.NameEditorUI;
import configuration.parameters.ui.ParameterUI;
import de.caluga.morphium.annotations.Transient;
import de.caluga.morphium.annotations.lifecycle.PostLoad;
import javax.swing.tree.MutableTreeNode;
import plugins.ManualSegmenter;
import plugins.ObjectSplitter;
import plugins.PreFilter;
import plugins.ProcessingScheme;
import plugins.Segmenter;
import plugins.TrackCorrector;
import plugins.Tracker;
import plugins.plugins.manualSegmentation.WatershedManualSegmenter;
import plugins.plugins.manualSegmentation.WatershedObjectSplitter;
import plugins.plugins.preFilter.ImageFeature;
import utils.Utils;

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
    BooleanParameter allowSplit;
    BooleanParameter allowMerge;
    @Transient NameEditorUI ui;
    
    public Structure(String name, int parentStructure, int channelImage) {
        this(name, parentStructure, parentStructure, channelImage);
    }
    public Structure(String name, int parentStructure, int segmentationParentStructure, int channelImage) {
        super(name);
        this.parentStructure =  new ParentStructureParameter("Parent Structure", parentStructure, -1);
        segmentationParent =  new ParentStructureParameter("Segmentation Parent", segmentationParentStructure, -1);
        this.channelImage = new ChannelImageParameter("Channel Image", channelImage);
        objectSplitter = new PluginParameter<>("Object Splitter", ObjectSplitter.class, true);
        processingScheme = new PluginParameter<>("Processing Scheme", ProcessingScheme.class, true);
        manualSegmenter = new PluginParameter<>("Manual Segmenter", ManualSegmenter.class, true);
        initChildList();
    }
    
    public Structure(String name) {
        this(name, -1, -1);
    }
    
    @Override
    protected void initChildList() {
        parentStructure.addListener(new ParameterListener() {
            @Override public void fire(Parameter source) {
                
                int parentIdx = parentStructure.getSelectedIndex();
                setParentStructure(parentIdx);
                logger.debug("parent structure listener fired: parent: {}, seg parent: {}", parentStructure.getSelectedIndex(), segmentationParent.getSelectedIndex());
                //update tree
                ConfigurationTreeModel model = ParameterUtils.getModel(segmentationParent);
                if (model!=null) model.nodeChanged(segmentationParent);
                else logger.debug("no model found..");
            }
        });
        segmentationParent.addListener(new ParameterListener() {
            @Override public void fire(Parameter source) {
                logger.debug("segmentation parent structure listener fired: parent: {}, seg parent: {}", parentStructure.getSelectedIndex(), segmentationParent.getSelectedIndex());
                setSegmentationParentStructure(segmentationParent.getSelectedIndex());
                //update tree
                ConfigurationTreeModel model = ParameterUtils.getModel(segmentationParent);
                if (model!=null) model.nodeChanged(segmentationParent);
                else logger.debug("no model found..");
            }
        });
        // for retro-compatibility only, to remove later
        if (processingScheme==null) processingScheme = new PluginParameter<ProcessingScheme>("Processing Scheme", ProcessingScheme.class, true); // for retro-compatibility only, to remove later
        if (manualSegmenter==null) manualSegmenter = new PluginParameter<ManualSegmenter>("Manual Segmenter", ManualSegmenter.class, new WatershedManualSegmenter(), false);
        if (allowSplit==null) allowSplit = new BooleanParameter("Allow Split", "yes", "no", false);
        if (allowMerge==null) allowMerge = new BooleanParameter("Allow Merge", "yes", "no", false);
        initChildren(parentStructure, segmentationParent, channelImage, processingScheme, objectSplitter, manualSegmenter, allowMerge, allowSplit); //segmentationParent
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
        parentStructure.setSelectedIndex(parentIdx);
        segmentationParent.setMaxStructureIdx(parentIdx+1);
        int segParent = segmentationParent.getSelectedIndex();
        if (segParent<parentIdx) segmentationParent.setSelectedIndex(parentIdx);
    }
    public void setSegmentationParentStructure(int segmentationParentStructureIdx) {
        if (segmentationParentStructureIdx<parentStructure.getSelectedStructureIdx()) segmentationParentStructureIdx = parentStructure.getSelectedStructureIdx();
        segmentationParent.setSelectedIndex(segmentationParentStructureIdx);
    }
    
    
    public int getChannelImage() {
        return channelImage.getSelectedIndex();
    }
    
    public int getIndex() {
        return this.getParent().getIndex(this);
    }
    
    @Override 
    public void setParent(MutableTreeNode newParent) {
        super.setParent(newParent);
        parentStructure.setMaxStructureIdx(parent.getIndex(this));
        //retro compatibility: //TO BE REMOVED LATER
        if (segmentationParent==null) segmentationParent =  new ParentStructureParameter("Segmentation Parent", parentStructure.getSelectedIndex(), -1);
        segmentationParent.setMaxStructureIdx(parent.getIndex(this));
    }
    
    @Override
    public ParameterUI getUI() {
        if (ui==null) ui=new NameEditorUI(this, false);
        return ui;
    }
}
