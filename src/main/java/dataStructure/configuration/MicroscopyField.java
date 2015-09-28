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
import dataStructure.objects.StructureObject;
import de.caluga.morphium.annotations.Transient;
import image.BlankMask;
import plugins.PreFilter;

/**
 *
 * @author jollion
 */
public class MicroscopyField extends SimpleContainerParameter {
    
    MultipleImageContainer images;
    PreProcessingChain preProcessingChain=new PreProcessingChain("Pre-Processing chain");
    @Transient InputImagesImpl inputImages;
    @Transient public static final int defaultTimePoint = 50;
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
        if (inputImages==null) {
            ImageDAO dao = getExperiment().getImageDAO();
            InputImage[][] res = new InputImage[images.getTimePointNumber()][images.getChannelNumber()];
            for (int t = 0; t<res.length; ++t) {
               for (int c = 0; c<res[0].length; ++c) {
                   res[t][c] = new InputImage(c, t, name, images, dao, true);
                } 
            }
            inputImages = new InputImagesImpl(res, Math.min(defaultTimePoint, images.getTimePointNumber()-1));
        }
        return inputImages;
    }
    
    public BlankMask getMask() {
        BlankMask mask = getExperiment().getImageDAO().getPreProcessedImageProperties(name);
        mask.setCalibration(images.getScaleXY(), images.getScaleZ());
        return mask;
    }
    
    public StructureObject[] createRootObjects() {
        StructureObject[] res = new StructureObject[getImages().getTimePointNumber()];
        for (int t = 0; t<res.length; ++t) {
            res[t] = new StructureObject(this.name, t, getMask(), getExperiment());
        }
        return res;
    }
    
    
    protected Experiment getExperiment() {
        return (Experiment) parent.getParent();
    }
    
    public float getScaleXY(){
        if (images!=null && images.getScaleXY()!=0) return images.getScaleXY();
        else return 1;
    }
    public float getScaleZ(){
        if (images!=null && images.getScaleZ()!=0) return images.getScaleZ();
        else return 1;
    }
    
    public int getTimePointNumber() {
        if (images!=null) return images.getTimePointNumber();
        else return 0;
    }
    
    public void setImages(MultipleImageContainer images) {
        this.images=images;
    }
    
    protected MultipleImageContainer getImages() {
        return images;
    }
    
    @Override
    public String toString() {
        if (images!=null) return name;// + " number of time points: "+images.getTimePointNumber();
        return name + " no selected images";
    }
    
}