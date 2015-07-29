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
import javax.swing.tree.MutableTreeNode;
import plugins.Tracker;

/**
 *
 * @author jollion
 */

public class Structure extends SimpleContainerParameter {
    ParentStructureParameter parentStructure =  new ParentStructureParameter("Parent Structure", -1, 0);;
    ChannelImageParameter channelImage = new ChannelImageParameter("Channel Image", 0);
    ProcessingChain processingChain = new ProcessingChain("Processing Chain");
    PluginParameter<Tracker> tracker = new PluginParameter<Tracker>("Tracker", Tracker.class, true);
    
    NameEditorUI ui;
    public Structure(String name) {
        this(name, -1);
    }
    
    public Structure(String name, int parentStructure) {
        super(name);
        this.parentStructure.setSelectedIndex(parentStructure);
        initChildList();
    }
    
    @Override
    protected void initChildList() {
        initChildren(parentStructure, channelImage, processingChain);
    }
    
    public ProcessingChain getProcessingChain() {
        return processingChain;
    }
    
    public Tracker getTracker() {
        return this.tracker.getPlugin();
    }
    
    public void setTracker(Tracker tracker) {
        this.tracker.setPlugin(tracker);
    }
    
    public int getParentStructure() {
        return parentStructure.getSelectedIndex();
    }
    
    public void setParentStructure(int parentIdx) {
        if (parentIdx>=parentStructure.getMaxStructureIdx()) throw new IllegalArgumentException("Parent Structure ("+parentIdx+") cannot be superior to max structure ("+parentStructure.getMaxStructureIdx()+")");
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
    public Structure duplicate() {
        Structure res=new Structure(name, parentStructure.getSelectedIndex());
        res.setContentFrom(this);
        return res;
    }
    
    @Override
    public ParameterUI getUI() {
        if (ui==null) ui=new NameEditorUI(this, false);
        return ui;
    }
    
    // morphia
    public Structure(){super(); initChildList();} // mettre dans la clase abstraite SimpleContainerParameter?
    

    
}
