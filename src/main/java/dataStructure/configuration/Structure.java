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

import configuration.parameters.ChannelImageParameter;
import configuration.parameters.ChoiceParameter;
import configuration.parameters.Parameter;
import configuration.parameters.SimpleContainerParameter;
import configuration.parameters.ParentStructureParameter;
import configuration.parameters.PluginParameter;
import configuration.parameters.ui.NameEditorUI;
import configuration.parameters.ui.ParameterUI;
import de.caluga.morphium.annotations.Transient;
import javax.swing.tree.MutableTreeNode;
import plugins.ObjectSplitter;
import plugins.TrackCorrector;
import plugins.Tracker;
import plugins.plugins.ObjectSplitter.WatershedObjectSplitter;

/**
 *
 * @author jollion
 */

public class Structure extends SimpleContainerParameter {
    ParentStructureParameter parentStructure =  new ParentStructureParameter("Parent Structure", -1, -1);
    ChannelImageParameter channelImage = new ChannelImageParameter("Channel Image", 0);
    ProcessingChain processingChain = new ProcessingChain("Processing Chain");
    PluginParameter<Tracker> tracker = new PluginParameter<Tracker>("Tracker", Tracker.class, true);
    PluginParameter<TrackCorrector> trackCorrector = new PluginParameter<TrackCorrector>("Track corrector", TrackCorrector.class, true);
    PluginParameter<ObjectSplitter> objectSplitter = new PluginParameter<ObjectSplitter>("Object Splitter", ObjectSplitter.class, new WatershedObjectSplitter(), false);
    @Transient NameEditorUI ui;
    public Structure(String name) {
        this(name, -1, -1);
    }
    
    public Structure(String name, int parentStructure, int channelImage) {
        super(name);
        this.parentStructure.setSelectedIndex(parentStructure);
        this.channelImage.setSelectedIndex(channelImage);
        initChildList();
    }
    
    @Override
    protected void initChildList() {
        initChildren(parentStructure, channelImage, processingChain, tracker, trackCorrector, objectSplitter);
    }
    
    public ProcessingChain getProcessingChain() {
        return processingChain;
    }
    
    public boolean hasSegmenter() {
        return processingChain.segmenter.isOnePluginSet() && processingChain.segmenter.isActivated();
    }
    
    public boolean hasTracker() {
        return tracker.isOnePluginSet();
    }
    
    public Tracker getTracker() {
        return this.tracker.getPlugin();
    }
    
    public void setTracker(Tracker tracker) {
        this.tracker.setPlugin(tracker);
    }
    
    public boolean hasTrackCorrector() {
        return trackCorrector.isOnePluginSet();
    }
    
    public TrackCorrector getTrackCorrector() {
        return this.trackCorrector.getPlugin();
    }
    
    public void setTrackCorrector(TrackCorrector trackCorrector) {
        this.trackCorrector.setPlugin(trackCorrector);
    }
    
    public ObjectSplitter getObjectSplitter() {
        return this.objectSplitter.getPlugin();
    }
    
    public void setObjectSplitter(ObjectSplitter objectSplitter) {
        this.objectSplitter.setPlugin(objectSplitter);
    }
    
    public int getParentStructure() {
        return parentStructure.getSelectedIndex();
    }
    
    public void setParentStructure(int parentIdx) {
        parentStructure.setSelectedIndex(parentIdx);
    }
    
    public int getChannelImage() {
        return channelImage.getSelectedIndex();
    }
    
    @Override 
    public void setParent(MutableTreeNode newParent) {
        super.setParent(newParent);
        parentStructure.setMaxStructureIdx(parent.getIndex(this));
    }
    
    @Override
    public ParameterUI getUI() {
        if (ui==null) ui=new NameEditorUI(this, false);
        return ui;
    }
    
}
