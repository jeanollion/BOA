/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.configuration.experiment;

import boa.gui.GUI;
import boa.gui.imageInteraction.IJVirtualStack;
import boa.configuration.parameters.ListElementErasable;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.SimpleContainerParameter;
import boa.configuration.parameters.SimpleListParameter;
import boa.configuration.parameters.StructureParameter;
import boa.configuration.parameters.FrameParameter;
import boa.configuration.parameters.ui.ParameterUI;
import boa.core.Processor;
import boa.data_structure.dao.ImageDAO;
import boa.data_structure.input_image.InputImage;
import boa.data_structure.input_image.InputImagesImpl;
import boa.data_structure.image_container.MultipleImageContainer;
import boa.data_structure.dao.ObjectDAO;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import static boa.data_structure.StructureObjectUtils.setTrackLinks;
import boa.image.BlankMask;
import boa.image.Image;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JSeparator;
import org.json.simple.JSONObject;
import boa.plugins.PreFilter;
import boa.utils.Utils;
import java.util.Map;
import java.util.function.Consumer;

/**
 *
 * @author jollion
 */
public class Position extends SimpleContainerParameter implements ListElementErasable {
    
    private MultipleImageContainer sourceImages;
    PreProcessingChain preProcessingChain=new PreProcessingChain("Pre-Processing chain");
    FrameParameter defaultTimePoint = new FrameParameter("Default TimePoint", defaultTP, false);
    InputImagesImpl preProcessedImages;
    public static final int defaultTP = 50;
    //ui: bouton droit = selectionner un champ?
    
    @Override
    public Object toJSONEntry() {
        JSONObject res= new JSONObject();
        res.put("name", name);
        if (sourceImages!=null) res.put("images", sourceImages.toJSONEntry()); 
        res.put("preProcessingChain", preProcessingChain.toJSONEntry());
        res.put("defaultFrame", defaultTimePoint.toJSONEntry());
        return res;
    }

    @Override
    public void initFromJSONEntry(Object jsonEntry) {
        JSONObject jsonO = (JSONObject)jsonEntry;
        name = (String)jsonO.get("name");
        if (jsonO.containsKey("images")) {
            sourceImages = MultipleImageContainer.createImageContainerFromJSON((JSONObject)jsonO.get("images"));
            initFrameParameters();
        }
        preProcessingChain.initFromJSONEntry(jsonO.get("preProcessingChain"));
        defaultTimePoint.initFromJSONEntry(jsonO.get("defaultFrame"));
    }
    
    public Position(String name) {
        super(name);
        initChildList();
    }
    
    public int getIndex() {
        return getParent().getIndex(this);
    }
    
    @Override
    protected void initChildList() {
        //logger.debug("MF: {}, init list..", name);
        //if (defaultTimePoint==null) defaultTimePoint = new TimePointParameter("Default Frame", defaultTP, false);
        initChildren(preProcessingChain, defaultTimePoint);
    }
    
    public void setPreProcessingChains(PreProcessingChain ppc) {
        preProcessingChain.setContentFrom(ppc);
    }
    
    public PreProcessingChain getPreProcessingChain() {
        return preProcessingChain;
    }
    private int getEndTrimFrame() {
        if (preProcessingChain.trimFramesEnd.getSelectedFrame()==0) return sourceImages.getFrameNumber()-1;
        return preProcessingChain.trimFramesEnd.getSelectedFrame();
    }
    public int getStartTrimFrame() {
        if (preProcessingChain.trimFramesStart.getSelectedFrame()>preProcessingChain.trimFramesEnd.getSelectedFrame()) preProcessingChain.trimFramesStart.setFrame(preProcessingChain.trimFramesEnd.getSelectedFrame());
        return preProcessingChain.trimFramesStart.getSelectedFrame();
    }
    public boolean singleFrame(int structureIdx) {
        if (sourceImages==null) return false;
        int channelIdx = getExperiment().getChannelImageIdx(structureIdx);
        return singleFrameChannel(channelIdx);
    }
    public boolean singleFrameChannel(int channelIdx) {
        if (sourceImages==null) return false;
        return sourceImages.singleFrame(channelIdx);
    }
    public InputImagesImpl getInputImages() {
        if (preProcessedImages!=null && preProcessedImages.getFrameNumber()!=getFrameNumber(false)) {  
            logger.warn("current inputImages has: {} frames while there are {} input images", preProcessedImages.getFrameNumber(), getFrameNumber(false));
        }
        if (preProcessedImages==null) { // || inputImages.getFrameNumber()!=getTimePointNumber(false) // should be flushed when modified from gui
            synchronized(this) {
                if (preProcessedImages==null) { //inputImages.getFrameNumber()!=getTimePointNumber(false)
                    logger.debug("generate input images with {} frames (old: {}) ", getFrameNumber(false), preProcessedImages!=null?preProcessedImages.getFrameNumber() : "null");
                    ImageDAO dao = getExperiment().getImageDAO();
                    if (dao==null || sourceImages==null) return null;
                    int tpOff = getStartTrimFrame();
                    int tpNp = getEndTrimFrame() - tpOff+1;
                    InputImage[][] res = new InputImage[sourceImages.getChannelNumber()][];
                    for (int c = 0; c<sourceImages.getChannelNumber(); ++c) {
                        res[c] = sourceImages.singleFrame(c) ? new InputImage[1] : new InputImage[tpNp];
                        for (int t = 0; t<res[c].length; ++t) {
                            res[c][t] = new InputImage(c, t+tpOff, t, name, sourceImages, dao);
                        } 
                    }
                    int defTp = defaultTimePoint.getSelectedFrame()-tpOff;
                    if (defTp<0) defTp=0;
                    if (defTp>=tpNp) defTp=tpNp-1;   
                    preProcessedImages = new InputImagesImpl(res, defTp, getExperiment().getFocusChannelAndAlgorithm());
                }
            }
            //logger.debug("creation input images: def tp: {}, total: {}, tp: {}",defaultTimePoint.getSelectedTimePoint(),images.getTimePointNumber(), inputImages.getDefaultTimePoint());
        }
        return preProcessedImages;
    }
    
    public void flushImages(boolean raw, boolean preProcessed) {
        if (preProcessed && preProcessedImages!=null) {
            preProcessedImages.flush();
            preProcessedImages = null;
        }
        if (raw && sourceImages!=null) sourceImages.flush();
    }
    
    public BlankMask getMask() {
        BlankMask mask = getExperiment().getImageDAO().getPreProcessedImageProperties(name);
        if (mask==null) return null;
        // TODO: recreate image if configuration data has been already computed
        mask.setCalibration(sourceImages.getScaleXY(), sourceImages.getScaleZ());
        return mask;
    }
    
    public ArrayList<StructureObject> createRootObjects(ObjectDAO dao) {
        ArrayList<StructureObject> res = new ArrayList<>(getFrameNumber(false));
        if (getMask()==null) {
            logger.warn("Could not initiate root objects, perform preProcessing first");
            return null;
        }
        for (int t = 0; t<getFrameNumber(false); ++t) res.add(new StructureObject(t, getMask(), dao));
        setOpenedImageToRootTrack(res);
        setTrackLinks(res);
        return res;
    }
    public void setOpenedImageToRootTrack(List<StructureObject> rootTrack) {
        if (preProcessedImages==null) return;
        Map<Integer, List<Integer>> c2s = getExperiment().getChannelToStructureCorrespondance();
        for (int channelIdx = 0; channelIdx<getExperiment().getChannelImageCount(); ++channelIdx) {
            List<Integer> structureIndices =c2s.get(channelIdx);
            if (structureIndices==null) continue; // no structure associated to channel
            for (StructureObject root : rootTrack) {    
                if (preProcessedImages.imageOpened(channelIdx, root.getFrame())) {
                    for (int s : structureIndices) root.setRawImage(s, preProcessedImages.getImage(channelIdx, root.getFrame()));
                }
            }
        }
    }
    
    protected Experiment getExperiment() {
        return (Experiment) parent.getParent();
    }
    
    public float getScaleXY(){
        if (!preProcessingChain.useCustomScale()) {
            if (sourceImages!=null && sourceImages.getScaleXY()!=0) return sourceImages.getScaleXY();
            else return 1;
        } else return (float)preProcessingChain.getScaleXY();
        
    }
    public float getScaleZ(){
        if (!preProcessingChain.useCustomScale()) {
            if (sourceImages!=null && sourceImages.getScaleZ()!=0) return sourceImages.getScaleZ();
            else return 1;
        } else return (float)preProcessingChain.getScaleZ();
    }
    public double getFrameDuration() {
        return preProcessingChain.getFrameDuration();
    }
    
    public int getFrameNumber(boolean raw) {
        if (sourceImages!=null) {
            if (raw) return sourceImages.getFrameNumber();
            else return getEndTrimFrame() - getStartTrimFrame()+1;
        }
        else return 0;
    }
    
    public int getDefaultTimePoint() {
        return defaultTimePoint.getSelectedFrame();
    }
    public Position setDefaultFrame(int frame) {
        this.defaultTimePoint.setFrame(frame);
        return this;
    }
    
    public int getSizeZ(int channelIdx) {
        if (sourceImages!=null) return sourceImages.getSizeZ(channelIdx);
        else return -1;
    }
    
    public void setImages(MultipleImageContainer images) {
        this.sourceImages=images;
        initFrameParameters();
    }
    private void initFrameParameters() {
        if (sourceImages!=null) {
            int frameNb = sourceImages.getFrameNumber();
            preProcessingChain.trimFramesEnd.setMaxFrame(frameNb-1);
            preProcessingChain.trimFramesStart.setMaxFrame(frameNb-1);
            if (preProcessingChain.trimFramesEnd.getSelectedFrame()<=0 || preProcessingChain.trimFramesEnd.getSelectedFrame()>=frameNb) preProcessingChain.trimFramesEnd.setFrame(frameNb-1);
        }
    }
    
    @Override public Position duplicate() {
        Position mf = super.duplicate();
        if (sourceImages!=null) mf.setImages(sourceImages.duplicate());
        mf.setListeners(listeners);
        return mf;
    }
    
    @Override
    public String toString() {
        if (sourceImages!=null) return name+ "(#"+getIndex()+")";// + " number of time points: "+images.getTimePointNumber();
        return name + " no selected images";
    }
    
    @Override
    public void removeFromParent() { // when removed from GUI
        super.removeFromParent();
        
    }
    
    @Override 
    public void setContentFrom(Parameter other) {
        super.setContentFrom(other);
        if (other instanceof Position) {
            Position otherP = (Position) other;
            if (otherP.sourceImages!=null) sourceImages = otherP.sourceImages.duplicate();
        }
    }
    @Override 
    public boolean sameContent(Parameter other) {
        if (!super.sameContent(other)) return false;
        if (other instanceof Position) {
            Position otherP = (Position) other;
            if (otherP.sourceImages!=null && sourceImages!=null) {
                if (!sourceImages.sameContent(otherP.sourceImages)) {
                    logger.debug("Position: {}!={} content differs at images");
                    return true; // just warn, do not concerns configuration
                } else return true;
            } else if (otherP.sourceImages==null && sourceImages==null) return true;
            else return false;
        }
        return false;
    }
    
    @Override
    public ParameterUI getUI() {
        return new MicroscopyFieldUI();
    }
    
    // listElementErasable
    @Override 
    public boolean eraseData(boolean promptConfirm) { // do not eraseAll objects if GUI not connected
        if (promptConfirm) {
            // eraseAll all objects..
            int response = JOptionPane.showConfirmDialog(null, "Delete Field: "+name+ "(all data will be lost)", "Confirm",
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (response != JOptionPane.YES_OPTION) return false;
        }
        if (GUI.getDBConnection()!=null && GUI.getDBConnection().getDao(name)!=null) GUI.getDBConnection().getDao(name).deleteAllObjects(); //TODO : unsafe if not called from GUI.. // ne pas conditionner par callFromGUI 
        if (getInputImages()!=null) getInputImages().deleteFromDAO();
        for (int s =0; s<getExperiment().getStructureCount(); ++s) getExperiment().getImageDAO().deleteTrackImages(name, s);
        if (GUI.getDBConnection()!=null) { // delete position folder
            String posDir = GUI.getDBConnection().getDir()+File.separator+name;
            Utils.deleteDirectory(posDir);
        }
        return true;
    }

    
    
    public class MicroscopyFieldUI implements ParameterUI {
        JMenuItem[] openRawInputAll; 
        JMenuItem openRawFrame, openRawAllFrames, openPreprocessedFrame, openPreprocessedAllFrames;
        Object[] actions;
        public MicroscopyFieldUI() {
            actions = new Object[2];
            openRawAllFrames = new JMenuItem("Open Raw Input Frames");
            actions[0] = openRawAllFrames;
            openRawAllFrames.setAction(new AbstractAction(openRawAllFrames.getActionCommand()) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    getExperiment().flushImages(true, true, name);
                    IJVirtualStack.openVirtual(getExperiment(), name, false);
                }
            }
            );
            openPreprocessedAllFrames = new JMenuItem("Open Pre-processed Frames");
            actions[1] = openPreprocessedAllFrames;
            openPreprocessedAllFrames.setAction(new AbstractAction(openPreprocessedAllFrames.getActionCommand()) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        getExperiment().flushImages(true, true, name);
                        IJVirtualStack.openVirtual(getExperiment(), name, true);
                    }
                }
            );
            openPreprocessedAllFrames.setEnabled(getExperiment().getImageDAO().getPreProcessedImageProperties(name)!=null);

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
