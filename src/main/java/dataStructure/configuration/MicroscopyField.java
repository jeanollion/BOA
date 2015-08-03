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
import dataStructure.containers.ImageDAO;
import dataStructure.containers.InputImage;
import dataStructure.containers.InputImagesImpl;
import dataStructure.containers.MultipleImageContainer;
import dataStructure.containers.MultipleImageContainerSingleFile;
import plugins.PreFilter;

/**
 *
 * @author jollion
 */
public class MicroscopyField extends SimpleContainerParameter {
    
    
    MultipleImageContainer images;
    PreProcessingChain preProcessingChain=new PreProcessingChain("Pre-Processing chain");
    //ui: bouton droit = selectionner un champ?
    
    public MicroscopyField(String name) {
        super(name);
        initChildList();
    }
    
    @Override
    protected void initChildList() {
        initChildren(preProcessingChain);
    }
    
    public void setPreProcessingChains(PreProcessingChain ppc) {
        preProcessingChain.setContentFrom(ppc);
    }
    
    public PreProcessingChain getPreProcessingChain() {
        return preProcessingChain;
    }
    
    public InputImagesImpl getInputImages() {
        ImageDAO dao = getExperiment().getImageDAO();
        InputImage[][] res = new InputImage[images.getTimePointNumber()][images.getChannelNumber()];
        for (int t = 0; t<res.length; ++t) {
           for (int c = 0; c<res[0].length; ++c) {
               res[t][c] = new InputImage(c, t, name, images, dao, true);
            } 
        }
        return new InputImagesImpl(res);
    }
    
    protected Experiment getExperiment() {
        return (Experiment) parent.getParent();
    }
    
    public float getScaleXY(){
        if (images!=null) return images.getScaleXY();
        else return 1;
    }
    public float getScaleZ(){
        if (images!=null) return images.getScaleZ();
        else return 1;
    }
    
    
    
    public void setImages(MultipleImageContainer images) {
        this.images=images;
    }
    
    public MultipleImageContainer getImages() {
        return images;
    }
    
    @Override
    public String toString() {
        if (images!=null) return name;// + " number of time points: "+images.getTimePointNumber();
        return name + " no selected images";
    }
    
}
