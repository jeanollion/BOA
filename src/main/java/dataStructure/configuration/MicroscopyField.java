/*
 * Copyright (C) 2015 jollion
 *
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

import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import configuration.parameters.SimpleContainerParameter;
import configuration.parameters.SimpleListParameter;
import configuration.parameters.StructureParameter;
import dataStructure.containers.MultipleImageContainerSingleFile;
import plugins.PreFilter;

/**
 *
 * @author jollion
 */
public class MicroscopyField extends SimpleContainerParameter {
    StructureParameter preProcessingStructure = new StructureParameter("Pre-Processing Structure", 0, false);
    MultipleImageContainerSingleFile images;
    //ui: bouton droit = selectionner un champ?
    
    public MicroscopyField(String name) {
        super(name);
    }
    
    
    @Override
    protected void initChildList() {
        initChildren(preProcessingStructure);
    }
    
    public void setImages(MultipleImageContainerSingleFile images) {
        this.images=images;
    }
    
    public MultipleImageContainerSingleFile getImages() {
        return images;
    }
    
    
    
    @Override
    public String toString() {
        if (images!=null) return name + " number of time points: "+images.getTimePointNumber();
        return name + " no selected images";
    }
    

    @Override
    public Parameter duplicate() {
        MicroscopyField field = new MicroscopyField("new Microscopy Field");
        field.setContentFrom(this);
        return field;
    }
    
        // morphia
    public MicroscopyField(){super(); initChildList();} // mettre dans la clase abstraite SimpleContainerParameter?
    
}
