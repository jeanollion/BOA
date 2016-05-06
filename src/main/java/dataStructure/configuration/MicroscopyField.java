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
import configuration.parameters.BooleanParameter;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.ConditionalParameter;
import configuration.parameters.ListElementRemovable;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import configuration.parameters.PostLoadable;
import configuration.parameters.SimpleContainerParameter;
import configuration.parameters.SimpleListParameter;
import configuration.parameters.StructureParameter;
import configuration.parameters.TimePointParameter;
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
    
    private MultipleImageContainer images;
    PreProcessingChain preProcessingChain;
    TimePointParameter defaultTimePoint;
    @Transient InputImagesImpl inputImages;
    @Transient public static final int defaultTP = 50;
    //ui: bouton droit = selectionner un champ?
    
    public MicroscopyField(String name) {
        super(name);
        preProcessingChain=new PreProcessingChain("Pre-Processing chain");
        defaultTimePoint = new TimePointParameter("Default TimePoint", defaultTP, false);
        initChildList();
    }
    
    
    @Override
    protected void initChildList() {
        //logger.debug("MF: {}, init list..", name);
        if (defaultTimePoint==null) defaultTimePoint = new TimePointParameter("Default TimePoint", defaultTP, false);
        initChildren(preProcessingChain, defaultTimePoint);
    }
    
    public void setPreProcessingChains(PreProcessingChain ppc) {
        preProcessingChain.setContentFrom(ppc);
    }
    
    public PreProcessingChain getPreProcessingChain() {
        return preProcessingChain;
    }
    private int getEndTrimFrame() {
        if (preProcessingChain.trimFramesEnd.getSelectedTimePoint()==0) preProcessingChain.trimFramesEnd.setTimePoint(images.getTimePointNumber()-1);
        return preProcessingChain.trimFramesEnd.getSelectedTimePoint();
    }
    private int getStartTrimFrame() {
        if (preProcessingChain.trimFramesEnd.getSelectedTimePoint()==0) preProcessingChain.trimFramesEnd.setTimePoint(images.getTimePointNumber()-1);
        if (preProcessingChain.trimFramesStart.getSelectedTimePoint()>preProcessingChain.trimFramesEnd.getSelectedTimePoint()) preProcessingChain.trimFramesStart.setTimePoint(preProcessingChain.trimFramesEnd.getSelectedTimePoint());
        return preProcessingChain.trimFramesStart.getSelectedTimePoint();
    }
    public InputImagesImpl getInputImages() {
        if (inputImages==null) {
            ImageDAO dao = getExperiment().getImageDAO();
            if (dao==null || images==null) return null;
            int tpOff = getStartTrimFrame();
            int tpNp = getEndTrimFrame() - tpOff+1;
            InputImage[][] res = new InputImage[tpNp][images.getChannelNumber()];
            for (int t = 0; t<res.length; ++t) {
               for (int c = 0; c<res[0].length; ++c) {
                   res[t][c] = new InputImage(c, t+tpOff, t, name, images, dao);
                } 
            }
            inputImages = new InputImagesImpl(res, Math.min(defaultTimePoint.getSelectedTimePoint()-tpOff, preProcessingChain.trimFramesEnd.getSelectedTimePoint()));
            //logger.debug("creation input images: def tp: {}, total: {}, tp: {}",defaultTimePoint.getSelectedTimePoint(),images.getTimePointNumber(), inputImages.getDefaultTimePoint());
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
        ArrayList<StructureObject> res = new ArrayList<StructureObject>(getTimePointNumber(false));
        if (getMask()==null) {
            logger.warn("Could not initiate root objects, perform preProcessing first");
            return null;
        }
        for (int t = 0; t<getTimePointNumber(false); ++t) {
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
        if (preProcessingChain.useImageScale()) {
            if (images!=null && images.getScaleXY()!=0) return images.getScaleXY();
            else return 1;
        } else return (float)preProcessingChain.getScaleXY();
        
    }
    public float getScaleZ(){
        if (preProcessingChain.useImageScale()) {
            if (images!=null && images.getScaleZ()!=0) return images.getScaleZ();
            else return 1;
        } else return (float)preProcessingChain.getScaleZ();
    }
    
    public int getTimePointNumber(boolean useRawInputFrames) {
        if (images!=null) {
            if (useRawInputFrames) return images.getTimePointNumber();
            else return getEndTrimFrame() - getStartTrimFrame()+1;
        }
        else return 0;
    }
    
    public int getSizeZ(int channelIdx) {
        if (images!=null) return images.getSizeZ(channelIdx);
        else return -1;
    }
    
    public void setImages(MultipleImageContainer images) {
        this.images=images;
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
        if (getInputImages()!=null) getInputImages().deleteFromDAO();
        return true;
    }
    
    
}
