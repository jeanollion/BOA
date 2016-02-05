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

import boa.gui.GUI;
import com.mongodb.MongoClient;
import configuration.parameters.ListElementRemovable;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import configuration.parameters.SimpleContainerParameter;
import configuration.parameters.SimpleListParameter;
import configuration.parameters.StructureParameter;
import core.Processor;
import dataStructure.containers.ImageDAO;
import dataStructure.containers.InputImage;
import dataStructure.containers.InputImagesImpl;
import dataStructure.containers.MultipleImageContainer;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;
import de.caluga.morphium.annotations.Transient;
import image.BlankMask;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import plugins.PreFilter;

/**
 *
 * @author jollion
 */
public class MicroscopyField extends SimpleContainerParameter implements ListElementRemovable {
    
    MultipleImageContainer images;
    PreProcessingChain preProcessingChain;
    @Transient InputImagesImpl inputImages;
    @Transient public static final int defaultTimePoint = 50;
    //ui: bouton droit = selectionner un champ?
    
    public MicroscopyField(String name) {
        super(name);
        preProcessingChain=new PreProcessingChain("Pre-Processing chain");
        initChildList();
    }
    
    @Override
    protected void initChildList() {
        //logger.debug("MF: {}, init list..", name);
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
                   res[t][c] = new InputImage(c, t, name, images, dao);
                } 
            }
            inputImages = new InputImagesImpl(res, Math.min(defaultTimePoint, images.getTimePointNumber()-1));
        }
        return inputImages;
    }
    
    public BlankMask getMask() {
        BlankMask mask = getExperiment().getImageDAO().getPreProcessedImageProperties(name);
        if (mask==null) return null;
        // TODO: recreate image if configuration data has been already computed
        mask.setCalibration(images.getScaleXY(), images.getScaleZ());
        return mask;
    }
    
    public ArrayList<StructureObject> createRootObjects(ObjectDAO dao) {
        ArrayList<StructureObject> res = new ArrayList<StructureObject>(getImages().getTimePointNumber());
        if (getMask()==null) {
            logger.warn("Could not initiate root objects, perform preProcessing first");
            return null;
        }
        for (int t = 0; t<getImages().getTimePointNumber(); ++t) {
            res.add(new StructureObject(t, getMask(), dao));
        }
        setTrackLinks(res);
        return res;
    }
    
    public static void setTrackLinks(List<StructureObject> list) {
        for (int i = 1; i<list.size(); ++i) list.get(i).setPreviousInTrack(list.get(i-1), false);
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
    
    public int getSizeZ(int channelIdx) {
        if (images!=null) return images.getSizeZ(channelIdx);
        else return -1;
    }
    
    public void setImages(MultipleImageContainer images) {
        this.images=images;
    }
    
    protected MultipleImageContainer getImages() {
        return images;
    }
    
    @Override public MicroscopyField duplicate() {
        MicroscopyField mf = super.duplicate();
        mf.setImages(images.duplicate());
        return mf;
    }
    
    @Override
    public String toString() {
        if (images!=null) return name;// + " number of time points: "+images.getTimePointNumber();
        return name + " no selected images";
    }
    
    @Override
    public void removeFromParent() { // when removed from GUI
        super.removeFromParent();
        
    }
    // listElementRemovable
    public boolean removeFromParentList(boolean callFromGUI) {
        if (callFromGUI) {
            // delete all objects..
            int response = JOptionPane.showConfirmDialog(null, "Delete Field: "+name+ "(all data will be lost)", "Confirm",
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (response != JOptionPane.YES_OPTION) return false;
        }
        GUI.getDBConnection().getDao(name).deleteAllObjects();
        this.getInputImages().deleteFromDAO();
        return true;
    }
}
