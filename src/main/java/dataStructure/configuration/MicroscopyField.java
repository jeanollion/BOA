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
import static boa.gui.GUI.logger;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import com.mongodb.MongoClient;
import configuration.parameters.BooleanParameter;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.ConditionalParameter;
import configuration.parameters.ListElementErasable;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import configuration.parameters.PostLoadable;
import configuration.parameters.SimpleContainerParameter;
import configuration.parameters.SimpleListParameter;
import configuration.parameters.StructureParameter;
import configuration.parameters.TimePointParameter;
import configuration.parameters.ui.ParameterUI;
import core.Processor;
import dataStructure.containers.ImageDAO;
import dataStructure.containers.InputImage;
import dataStructure.containers.InputImagesImpl;
import dataStructure.containers.MultipleImageContainer;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import static dataStructure.objects.StructureObjectUtils.setTrackLinks;
import de.caluga.morphium.annotations.Transient;
import image.BlankMask;
import image.Image;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JSeparator;
import plugins.PreFilter;

/**
 *
 * @author jollion
 */
public class MicroscopyField extends SimpleContainerParameter implements ListElementErasable {
    
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
    
    public int getIndex() {
        return getParent().getIndex(this);
    }
    
    @Override
    protected void initChildList() {
        //logger.debug("MF: {}, init list..", name);
        if (defaultTimePoint==null) defaultTimePoint = new TimePointParameter("Default Frame", defaultTP, false);
        initChildren(preProcessingChain, defaultTimePoint);
    }
    
    public void setPreProcessingChains(PreProcessingChain ppc) {
        preProcessingChain.setContentFrom(ppc);
    }
    
    public PreProcessingChain getPreProcessingChain() {
        return preProcessingChain;
    }
    private int getEndTrimFrame() {
        if (preProcessingChain.trimFramesEnd.getSelectedTimePoint()==0) preProcessingChain.trimFramesEnd.setTimePoint(images.getFrameNumber()-1);
        return preProcessingChain.trimFramesEnd.getSelectedTimePoint();
    }
    public int getStartTrimFrame() {
        if (preProcessingChain.trimFramesEnd.getSelectedTimePoint()==0) preProcessingChain.trimFramesEnd.setTimePoint(images.getFrameNumber()-1);
        if (preProcessingChain.trimFramesStart.getSelectedTimePoint()>preProcessingChain.trimFramesEnd.getSelectedTimePoint()) preProcessingChain.trimFramesStart.setTimePoint(preProcessingChain.trimFramesEnd.getSelectedTimePoint());
        return preProcessingChain.trimFramesStart.getSelectedTimePoint();
    }
    public boolean singleFrame(int structureIdx) {
        if (images==null) return false;
        int channelIdx = getExperiment().getChannelImageIdx(structureIdx);
        return singleFrameChannel(channelIdx);
    }
    public boolean singleFrameChannel(int channelIdx) {
        return images.singleFrame(channelIdx);
    }
    public InputImagesImpl getInputImages() {
        if (inputImages==null || inputImages.getTimePointNumber()!=getTimePointNumber(false)) {
            logger.debug("generate input images with {} frames (old: {}) ", getTimePointNumber(false), inputImages!=null?inputImages.getTimePointNumber() : "null");
            ImageDAO dao = getExperiment().getImageDAO();
            if (dao==null || images==null) return null;
            int tpOff = getStartTrimFrame();
            int tpNp = getEndTrimFrame() - tpOff+1;
            InputImage[][] res = new InputImage[images.getChannelNumber()][];
            for (int c = 0; c<images.getChannelNumber(); ++c) {
                res[c] = images.singleFrame(c) ? new InputImage[1] : new InputImage[tpNp-tpOff];
                for (int t = 0; t<res[c].length; ++t) {
                    res[c][t] = new InputImage(c, t+tpOff, t, name, images, dao);
                } 
            }
            int defTp = defaultTimePoint.getSelectedTimePoint()-tpOff;
            if (defTp<0) defTp=0;
            if (defTp>=tpNp) defTp=tpNp-1;   
            inputImages = new InputImagesImpl(res, defTp);
            //logger.debug("creation input images: def tp: {}, total: {}, tp: {}",defaultTimePoint.getSelectedTimePoint(),images.getTimePointNumber(), inputImages.getDefaultTimePoint());
        }
        return inputImages;
    }
    
    public void flushImages(boolean raw, boolean preProcessed) {
        if (raw && inputImages!=null) {
            inputImages.flush();
            inputImages = null;
        }
        if (preProcessed && images!=null) images.close();
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
    
    protected Experiment getExperiment() {
        return (Experiment) parent.getParent();
    }
    
    public float getScaleXY(){
        if (!preProcessingChain.useCustomScale()) {
            if (images!=null && images.getScaleXY()!=0) return images.getScaleXY();
            else return 1;
        } else return (float)preProcessingChain.getScaleXY();
        
    }
    public float getScaleZ(){
        if (!preProcessingChain.useCustomScale()) {
            if (images!=null && images.getScaleZ()!=0) return images.getScaleZ();
            else return 1;
        } else return (float)preProcessingChain.getScaleZ();
    }
    public double getFrameDuration() {
        return preProcessingChain.getFrameDuration();
    }
    
    public int getTimePointNumber(boolean useRawInputFrames) {
        if (images!=null) {
            if (useRawInputFrames) return images.getFrameNumber();
            else return getEndTrimFrame() - getStartTrimFrame()+1;
        }
        else return 0;
    }
    
    public int getDefaultTimePoint() {
        return defaultTimePoint.getSelectedTimePoint();
    }
    public MicroscopyField setDefaultFrame(int frame) {
        this.defaultTimePoint.setTimePoint(frame);
        return this;
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
    
    @Override
    public ParameterUI getUI() {
        return new MicroscopyFieldUI();
    }
    
    // listElementErasable
    @Override 
    public boolean eraseData(boolean callFromGUI) { // do not delete objects if GUI not connected
        if (callFromGUI) {
            // delete all objects..
            int response = JOptionPane.showConfirmDialog(null, "Delete Field: "+name+ "(all data will be lost)", "Confirm",
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (response != JOptionPane.YES_OPTION) return false;
        }
        if (GUI.getDBConnection()!=null && GUI.getDBConnection().getDao(name)!=null) GUI.getDBConnection().getDao(name).deleteAllObjects(); //TODO : unsafe if not called from GUI.. // ne pas conditionner par callFromGUI 
        if (getInputImages()!=null) getInputImages().deleteFromDAO();
        return true;
    }
    
    public class MicroscopyFieldUI implements ParameterUI {
        JMenuItem[] openRawInputAll; 
        JMenuItem openRawFrame, openRawAllFrames, openPreprocessedFrame, openPreprocessedAllFrames;
        Object[] actions;
        public MicroscopyFieldUI() {
            actions = new Object[4];
            final String[] channelNames = getExperiment().getChannelImagesAsString();
            openRawFrame = new JMenuItem("Open Raw Input Image (default frame)");
            actions[0] = openRawFrame;
            openRawFrame.setAction(new AbstractAction(openRawFrame.getActionCommand()) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    getExperiment().flushImages(true, true, name);
                    Image[][] imagesTC = getInputImages().getImagesTC(defaultTimePoint.getSelectedTimePoint(), defaultTimePoint.getSelectedTimePoint()+1);
                    ImageWindowManagerFactory.getImageManager().getDisplayer().showImage5D("Raw Image of Position: "+name+ " Frame: "+defaultTimePoint.getSelectedTimePoint(), imagesTC);
                }
            }
            );
            
            openRawAllFrames = new JMenuItem("Open Raw Input Frames");
            actions[1] = openRawAllFrames;
            openRawAllFrames.setAction(new AbstractAction(openRawAllFrames.getActionCommand()) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    getExperiment().flushImages(true, true, name);
                    Image[][] imagesTC = getInputImages().getImagesTC();
                    ImageWindowManagerFactory.getImageManager().getDisplayer().showImage5D("Raw Image of Position: "+name, imagesTC);
                }
            }
            );
            
            /*JMenu rawInputSubMenuAll = new JMenu("Open Raw Input Images");
            actions[1] = rawInputSubMenuAll;
            openRawInputAll=new JMenuItem[channelNames.length];
            for (int i = 0; i < openRawInputAll.length; i++) {
                openRawInputAll[i] = new JMenuItem(channelNames[i]);
                openRawInputAll[i].setAction(new AbstractAction(channelNames[i]) {
                        @Override
                        public void actionPerformed(ActionEvent ae) {
                            int channelIdx = getStructureIdx(ae.getActionCommand(), channelNames);
                            int frames = getTimePointNumber(true);
                            Image[][] imagesTC = new Image[frames][1];
                            for (int frame = 0; frame<frames; ++frame) imagesTC[frame][0] = getInputImages().getImage(channelIdx, frame);
                            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage5D("Position: "+name+" Channel: "+ae.getActionCommand(), imagesTC);
                        }
                    }
                );
                rawInputSubMenuAll.add(openRawInputAll[i]);
            }*/
            openPreprocessedFrame = new JMenuItem("Open Pre-processed (Default Frame)");
            openPreprocessedFrame.setAction(new AbstractAction(openPreprocessedFrame.getActionCommand()) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        getExperiment().flushImages(true, true, name);
                        int channels = getExperiment().getChannelImageCount();
                        Image[][] imagesTC = new Image[1][channels];
                        for (int channel = 0; channel<channels; ++channel) {
                            int frame = singleFrameChannel(channel) ? 0 :defaultTimePoint.getSelectedTimePoint();
                            imagesTC[0][channel] = getExperiment().getImageDAO().openPreProcessedImage(channel, frame, name);
                            if (imagesTC[0][channel]==null) return;
                        }
                        ImageWindowManagerFactory.getImageManager().getDisplayer().showImage5D("PreProcessed Image of Position: "+name+ " Frame: "+defaultTimePoint.getSelectedTimePoint(), imagesTC);
                    }
                }
            );
            actions[2] = openPreprocessedFrame;
            openPreprocessedAllFrames = new JMenuItem("Open Pre-processed Frames");
            openPreprocessedAllFrames.setAction(new AbstractAction(openPreprocessedAllFrames.getActionCommand()) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        getExperiment().flushImages(true, true, name);
                        int channels = getExperiment().getChannelImageCount();
                        int frames = getTimePointNumber(false);
                        Image[][] imagesTC = new Image[frames][channels];
                        for (int channel = 0; channel<channels; ++channel) {
                            for (int frame = 0; frame<frames; ++frame) {
                                int fr = singleFrameChannel(channel) ? 0 :frame;
                                imagesTC[frame][channel] = getExperiment().getImageDAO().openPreProcessedImage(channel, fr, name);
                                if (imagesTC[frame][channel]==null) return;
                            }
                        }
                        ImageWindowManagerFactory.getImageManager().getDisplayer().showImage5D("PreProcessed Images of Position: "+name, imagesTC);
                    }
                }
            );
            actions[3] = openPreprocessedAllFrames;
        }
        public Object[] getDisplayComponent() {
            return actions;
        }
        private int getStructureIdx(String name, String[] structureNames) {
            for (int i = 0; i<structureNames.length; ++i) if (structureNames[i].equals(name)) return i;
            return -1;
        }
    }
    
}
