/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.configuration.experiment;

import boa.gui.configuration.ConfigurationTreeModel;
import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.ConditionalParameter;
import boa.configuration.parameters.MultipleChoiceParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.ParameterUtils;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.ScaleXYZParameter;
import boa.configuration.parameters.ContainerParameterImpl;
import boa.configuration.parameters.SimpleListParameter;
import boa.configuration.parameters.FrameParameter;
import boa.configuration.parameters.TransformationPluginParameter;
import boa.configuration.parameters.ui.MultipleChoiceParameterUI;
import boa.configuration.parameters.ui.ParameterUI;
import boa.plugins.ConfigurableTransformation;
import boa.plugins.MultichannelTransformation;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.plaf.basic.BasicMenuItemUI;
import org.json.simple.JSONObject;
import boa.plugins.Transformation;
import java.util.function.Consumer;

/**
 *
 * @author jollion
 */
public class PreProcessingChain extends ContainerParameterImpl<PreProcessingChain> {
    BooleanParameter useImageScale = new BooleanParameter("Voxel Calibration", "Use Image Calibration", "Custom Calibration", true).setToolTipText("Voxel calibration (voxel size in x, y, z axis). If <em>Custom calibration</em> is set, the image calibration (if present) will be overriden");
    BoundedNumberParameter scaleXY = new BoundedNumberParameter("Scale XY", 5, 1, 0.00001, null);
    BoundedNumberParameter scaleZ = new BoundedNumberParameter("Scale Z", 5, 1, 0.00001, null);
    ConditionalParameter imageScaleCond = new ConditionalParameter(useImageScale).setActionParameters("Custom Calibration", new Parameter[]{scaleXY, scaleZ});
    BoundedNumberParameter frameDuration= new BoundedNumberParameter("Frame Duration", 4, 4, 0, null).setToolTipText("This parameter is only used when no Frame duration is found in image metadata");
    FrameParameter trimFramesStart = new FrameParameter("Trim Frames Start", 0, true).setToolTipText("First frame to be pre-processed. Must be inferior to <em>Trim Frames Stop</em>");
    FrameParameter trimFramesEnd = new FrameParameter("Trim Frames Stop", 0, true).setToolTipText("Last frame to be pre-processed (0=no trimming)");
    SimpleListParameter<TransformationPluginParameter<Transformation>> transformations = new SimpleListParameter<>("Pre-Processing pipeline", new TransformationPluginParameter<>("Transformation", Transformation.class, false));
    
    @Override
    public JSONObject toJSONEntry() {
        JSONObject res= new JSONObject();
        res.put("scaleXY", scaleXY.toJSONEntry());
        res.put("scaleZ", scaleZ.toJSONEntry());
        res.put("useImageScale", useImageScale.toJSONEntry());
        res.put("frameDuration", frameDuration.toJSONEntry());
        res.put("trimFramesStart", trimFramesStart.toJSONEntry());
        res.put("trimFramesEnd", trimFramesEnd.toJSONEntry());
        res.put("transformations", transformations.toJSONEntry());
        return res;
    }

    @Override
    public void initFromJSONEntry(Object jsonEntry) {
        JSONObject jsonO = (JSONObject)jsonEntry;
        scaleXY.initFromJSONEntry(jsonO.get("scaleXY"));
        scaleZ.initFromJSONEntry(jsonO.get("scaleZ"));
        useImageScale.initFromJSONEntry(jsonO.get("useImageScale"));
        frameDuration.initFromJSONEntry(jsonO.get("frameDuration"));
        trimFramesStart.initFromJSONEntry(jsonO.get("trimFramesStart"));
        trimFramesEnd.initFromJSONEntry(jsonO.get("trimFramesEnd"));
        transformations.initFromJSONEntry(jsonO.get("transformations"));
    }
    
    public PreProcessingChain(String name) {
        super(name);
        //logger.debug("new PPC: {}", name);
        initChildList();
        Consumer<BoundedNumberParameter> pl =  sourceParameter -> {
            Position pos = ParameterUtils.getMicroscopyField(sourceParameter);
            if (pos!=null) {
                pos.flushImages(true, true);
            }
        };
        trimFramesStart.addListener(pl);
        trimFramesEnd.addListener(pl);
    }

    public PreProcessingChain setCustomScale(double scaleXY, double scaleZ) {
        if (Double.isNaN(scaleXY) || Double.isInfinite(scaleXY)) throw new IllegalArgumentException("Invalid scale value");
        if (scaleXY<=0) throw new IllegalArgumentException("Scale should be >=0");
        if (scaleZ<=0) scaleZ=1;
        useImageScale.setSelected(false); // custom calibration
        this.scaleXY.setValue(scaleXY);
        this.scaleZ.setValue(scaleZ);
        return this;
    }
    @Override 
    public boolean isEmphasized() {
        return false;
    }
    public boolean useCustomScale() {return !useImageScale.getSelected();}
    public double getScaleXY() {return scaleXY.getValue().doubleValue();}
    public double getScaleZ() {return scaleZ.getValue().doubleValue();}
    public double getFrameDuration() {return frameDuration.getValue().doubleValue();}
    public void setFrameDuration(double frameDuration) {
        this.frameDuration.setValue(frameDuration);
    }
    @Override
    protected void initChildList() {
        //logger.debug("PreProc chain: {}, init list..", name);
        super.initChildren(imageScaleCond, transformations, trimFramesStart, trimFramesEnd, frameDuration);
    }
    
    public List<TransformationPluginParameter<Transformation>> getTransformations(boolean onlyActivated) {
        return onlyActivated ? transformations.getActivatedChildren() : transformations.getChildren();
    }
    
    public void removeAllTransformations() {
        transformations.removeAllElements();
    }
    public SimpleListParameter<TransformationPluginParameter<Transformation>> getTransformations() {
        return transformations;
    }
    public void setTrimFrames(int startFrame, int endFrame) {
        this.trimFramesStart.setFrame(startFrame);
        this.trimFramesEnd.setFrame(endFrame);
    }
    
    /**
     * 
     * @param inputChannel channel on which compute transformation parameters
     * @param outputChannel channel(s) on which apply transformation (null = all channels or same channel, depending {@link TransformationTimeIndependent#getOutputChannelSelectionMode() })
     * @param transformation 
     */
    public TransformationPluginParameter<Transformation> addTransformation(int idx, int inputChannel, int[] outputChannel, Transformation transformation) {
        if (inputChannel<-1 && (transformation instanceof ConfigurableTransformation) || (transformation instanceof MultichannelTransformation && ((MultichannelTransformation)transformation).getOutputChannelSelectionMode()==MultichannelTransformation.OUTPUT_SELECTION_MODE.SAME)) throw new IllegalArgumentException("Input channel should be >=0");
        Experiment xp = ParameterUtils.getExperiment(this);
        if (xp!=null &&  inputChannel>=xp.getChannelImageCount()) throw new IllegalArgumentException("Input channel should be < channel image count ("+xp.getChannelImageCount()+")");
        TransformationPluginParameter<Transformation> tpp= new TransformationPluginParameter<>("Transformation", Transformation.class, false);
        transformations.insert(tpp, idx);
        tpp.setPlugin(transformation);
        tpp.setInputChannel(inputChannel);
        if (transformation instanceof MultichannelTransformation) {
            MultichannelTransformation mct = (MultichannelTransformation)transformation;
            if (outputChannel==null && (mct.getOutputChannelSelectionMode()==MultichannelTransformation.OUTPUT_SELECTION_MODE.MULTIPLE || mct.getOutputChannelSelectionMode()==MultichannelTransformation.OUTPUT_SELECTION_MODE.SINGLE) ) outputChannel = new int[]{inputChannel};
            tpp.setOutputChannel(outputChannel);
        }
        return tpp;
    }
    public TransformationPluginParameter<Transformation> addTransformation(int inputChannel, int[] outputChannel, Transformation transformation) {
        return addTransformation(this.transformations.getChildCount(), inputChannel, outputChannel, transformation);
    }
    /*
    @Override 
    public void postLoad() {
        if (!postLoaded) {
            initScaleParam(useImageScale==null, true);
            useImageScale.postLoad();
            super.postLoad();
        }
    }
    */
    @Override public ParameterUI getUI() {
        return new PreProcessingChainUI(this);
    }
    
    public class PreProcessingChainUI implements ParameterUI {
        Object[] actions;
        MultipleChoiceParameter fields;
        MultipleChoiceParameterUI fieldUI;
        Experiment xp;
        PreProcessingChain ppc;
        public PreProcessingChainUI(PreProcessingChain ppc) {
            xp = ParameterUtils.getExperiment(ppc);
            this.ppc=ppc;
            fields = new MultipleChoiceParameter("Fields", xp.getPositionsAsString(), false);
        }
        public void addMenuListener(JPopupMenu menu, int X, int Y, Component parent) {
            ((MultipleChoiceParameterUI)fields.getUI()).addMenuListener(menu, X, Y, parent);
        }
        @Override
        public Object[] getDisplayComponent() {
            fieldUI = (MultipleChoiceParameterUI)fields.getUI();
            boolean isTemplate = getParent()==xp;
            int offset = isTemplate?2:3;
            actions = new Object[fieldUI.getDisplayComponent().length + offset];
            for (int i = offset; i < actions.length; i++) {
                actions[i] = fieldUI.getDisplayComponent()[i - offset];
                //if (i<actions.length-1) ((JMenuItem)actions[i]).setUI(new StayOpenMenuItemUI());
            }
            int off = 0;
            if (!isTemplate) {
                JMenuItem overwriteToTemplate = new JMenuItem("Copy pipeline to template");
                overwriteToTemplate.setAction(new AbstractAction(overwriteToTemplate.getActionCommand()) {
                        @Override
                        public void actionPerformed(ActionEvent ae) {
                            xp.getPreProcessingTemplate().setContentFrom(ppc);
                            ConfigurationTreeModel model = ParameterUtils.getModel(xp);
                            if (model!=null) model.nodeStructureChanged(xp.getPreProcessingTemplate());
                        }
                    }
                );
                actions[off++]=overwriteToTemplate;
            }
            JMenuItem overwrite = new JMenuItem("Overwrite pipeline on selected positions");
            overwrite.setAction(new AbstractAction(overwrite.getActionCommand()) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        for (int f : fields.getSelectedItems()) {
                            //logger.debug("override pp on field: {}", f);
                            Position position = xp.positions.getChildAt(f);
                            if (position.getPreProcessingChain()!=ppc) {
                                position.setPreProcessingChains(ppc);
                                ConfigurationTreeModel model = ParameterUtils.getModel(xp);
                                if (model!=null) model.nodeStructureChanged(position);
                            }
                        }
                    }
                }
            );
            actions[off++]=overwrite;
            actions[off]=new JSeparator();
            return actions;
        }
    }
    class StayOpenMenuItemUI extends BasicMenuItemUI {
        @Override
        protected void doClick(MenuSelectionManager msm) {
            menuItem.doClick(0);
        }
    }
}
