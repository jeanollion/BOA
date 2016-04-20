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
import plugins.TrackCorrector;
import plugins.Tracker;
import plugins.plugins.manualSegmentation.WatershedManualSegmenter;
import plugins.plugins.manualSegmentation.WatershedObjectSplitter;
import plugins.plugins.preFilter.ImageFeature;

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
    
    @Transient NameEditorUI ui;
    
    
    public Structure(String name, int parentStructure, int channelImage) {
        super(name);
        this.parentStructure =  new ParentStructureParameter("Parent Structure", parentStructure, -1);
        segmentationParent =  new ParentStructureParameter("Segmentation Parent", -1, -1);
        this.channelImage = new ChannelImageParameter("Channel Image", channelImage);
        objectSplitter = new PluginParameter<ObjectSplitter>("Object Splitter", ObjectSplitter.class, new WatershedObjectSplitter(), false);
        processingScheme = new PluginParameter<ProcessingScheme>("Processing Scheme", ProcessingScheme.class, true);
        manualSegmenter = new PluginParameter<ManualSegmenter>("Manual Segmenter", ManualSegmenter.class, new WatershedManualSegmenter(), false);
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
        // for retro-compatibility only, to remove later
        if (processingScheme==null) processingScheme = new PluginParameter<ProcessingScheme>("Processing Scheme", ProcessingScheme.class, true); // for retro-compatibility only, to remove later
        if (manualSegmenter==null) manualSegmenter = new PluginParameter<ManualSegmenter>("Manual Segmenter", ManualSegmenter.class, new WatershedManualSegmenter(), false);
        initChildren(parentStructure, channelImage, processingScheme, objectSplitter, manualSegmenter); //segmentationParent
    }
    
    public ProcessingScheme getProcessingScheme() {
        return processingScheme.instanciatePlugin();
    }
    
    public void setProcessingScheme(ProcessingScheme ps) {
        this.processingScheme.setPlugin(ps);
    }
    
    public ObjectSplitter getObjectSplitter() {
        return this.objectSplitter.instanciatePlugin();
    }
    
    public ManualSegmenter getManualSegmenter() {
        return this.manualSegmenter.instanciatePlugin();
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
        
        return this.segmentationParent.getSelectedIndex();
    }
    
    public void setParentStructure(int parentIdx) {
        parentStructure.setSelectedIndex(parentIdx);
        segmentationParent.setMaxStructureIdx(parentIdx+1);
        int segParent = segmentationParent.getSelectedIndex();
        if (segParent<0) segmentationParent.setSelectedIndex(parentIdx);
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
