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
package boa.gui;

import boa.configuration.experiment.Position;
import boa.configuration.experiment.PreProcessingChain;
import boa.configuration.parameters.Parameter;
import static boa.configuration.parameters.Parameter.logger;
import boa.configuration.parameters.ParameterUtils;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.SimpleContainerParameter;
import boa.configuration.parameters.SimpleListParameter;
import boa.configuration.parameters.TrackPostFilterSequence;
import boa.configuration.parameters.TransformationPluginParameter;
import boa.data_structure.Selection;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.data_structure.image_container.MemoryImageContainer;
import boa.data_structure.input_image.InputImagesImpl;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageWindowManager;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.gui.imageInteraction.TrackMask;
import boa.image.Image;
import boa.plugins.ConfigurableTransformation;
import boa.plugins.MultichannelTransformation;
import boa.plugins.Plugin;
import boa.plugins.ProcessingScheme;
import boa.plugins.ProcessingSchemeWithTracking;
import boa.plugins.Segmenter;
import boa.plugins.TestableProcessingPlugin;
import boa.plugins.TestableProcessingPlugin.TestDataStore;
import boa.plugins.TrackParametrizable;
import boa.plugins.Tracker;
import boa.plugins.TrackerSegmenter;
import boa.plugins.Transformation;
import boa.plugins.plugins.processing_scheme.SegmentAndTrack;
import boa.plugins.plugins.processing_scheme.SegmentOnly;
import boa.plugins.plugins.processing_scheme.SegmentThenTrack;
import boa.plugins.plugins.track_pre_filters.PreFilters;
import boa.utils.ArrayUtil;
import boa.utils.Pair;
import boa.utils.Utils;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import boa.plugins.TrackParametrizable.TrackParametrizer;
import boa.utils.HashMapGetCreate;

/**
 *
 * @author jollion
 */
public class PluginConfigurationUtils {
    public static Pair<ImageObjectInterface, List<Image>> lastTest;
    public static JMenuItem getTestCommand(final Plugin plugin, PluginParameter pp, int structureIdx) {
        JMenuItem item = new JMenuItem("Test");
        item.setAction(new AbstractAction(item.getActionCommand()) {
            @Override
            public void actionPerformed(ActionEvent ae) {
                // selected objects from GUI
                List<StructureObject> sel = ImageWindowManagerFactory.getImageManager().getSelectedLabileObjects(null);
                if ((sel == null || sel.isEmpty()) && GUI.getInstance().getSelectedPositions(false).isEmpty()) {
                    GUI.log("Select an object OR position to test parameter");
                }
                else {
                    if (sel==null) sel = new ArrayList<>(1);
                    if (sel.isEmpty()) sel.add(GUI.getDBConnection().getDao(GUI.getInstance().getSelectedPositions(false).get(0)).getRoot(0));
                    
                    // get processing scheme
                    ProcessingScheme psc=null;
                    if (pp.instanciatePlugin() instanceof ProcessingScheme) psc = (ProcessingScheme)pp.instanciatePlugin();
                    else {
                        PluginParameter pp2 = ParameterUtils.getFirstParameterFromParents(PluginParameter.class, pp, false);
                        while(pp2 != null && !(pp2.instanciatePlugin() instanceof ProcessingScheme)) pp2 = ParameterUtils.getFirstParameterFromParents(PluginParameter.class, pp2, false);
                        if (pp2!=null && pp2.instanciatePlugin() instanceof ProcessingScheme) psc = (ProcessingScheme)pp2.instanciatePlugin();
                        else {
                            GUI.log("no processing scheme found in configuration tree");
                            return;
                        }
                    }
                    
                    // get parent objects -> create graph cut
                    todo do not limit to one parent & run on all parents
                    StructureObject o = sel.get(0);
                    int parentStrutureIdx = o.getExperiment().getStructure(structureIdx).getParentStructure();
                    int segParentStrutureIdx = o.getExperiment().getStructure(structureIdx).getSegmentationParentStructure();
                    StructureObject parent = (o.getStructureIdx()>parentStrutureIdx) ? o.getParent(parentStrutureIdx) : o.getChildren(parentStrutureIdx).get(0);
                    List<StructureObject> parentTrack = StructureObjectUtils.getTrack(parent.getTrackHead(), false);
                    Map<String, StructureObject> dupMap = StructureObjectUtils.createGraphCut( parentTrack, true);  // don't modify object directly. 
                    parent = dupMap.get(parent.getId()); 
                    parentTrack = parentTrack.stream().map(p->dupMap.get(p.getId())).collect(Collectors.toList());
                    psc.getTrackPreFilters(true).filter(structureIdx, parentTrack);
                    
                    // data store for test images
                    Map<StructureObject, TestDataStore> stores = HashMapGetCreate.getRedirectedMap(so->new TestDataStore(so), HashMapGetCreate.Syncronization.SYNC_ON_MAP);
                    if (plugin instanceof TestableProcessingPlugin) ((TestableProcessingPlugin)plugin).setTestDataStore(stores);
                    
                    if (plugin instanceof Segmenter) { // case segmenter -> segment only & call to test method
                        TrackParametrizer  applyToSeg = TrackParametrizable.getTrackParametrizer(structureIdx, parentTrack, (Segmenter)plugin);
                        SegmentOnly so; 
                        if (psc instanceof SegmentOnly) {
                            so = (SegmentOnly)psc;
                            so.getPreFilters().removeAll();
                            so.getTrackPreFilters(false).removeAll();
                        }
                        else so = new SegmentOnly((Segmenter)plugin).setPostFilters(psc.getPostFilters());

                        if (segParentStrutureIdx!=parentStrutureIdx && o.getStructureIdx()==segParentStrutureIdx) {
                            final List<StructureObject> selF = sel;
                            parent.getChildren(segParentStrutureIdx).removeIf(oo -> !selF.contains(oo));
                        }
                        sel = new ArrayList<>();
                        sel.add(parent);
                        TrackParametrizer  apply = (p, s)-> {
                            if (s instanceof TestableProcessingPlugin) ((TestableProcessingPlugin)s).setTestDataStore(stores);
                            applyToSeg.apply(p, s); 
                        };
                        so.segmentAndTrack(structureIdx, sel, apply);

                    } else if (plugin instanceof Tracker) {
                        boolean segAndTrack = true; 
                        if (!(plugin instanceof TrackerSegmenter)) segAndTrack = false;
                        
                        // get first continuous parent track
                        todo: graph cut
                        sel = Utils.transform(sel, ob -> o.getStructureIdx()>parentStrutureIdx?ob.getParent(parentStrutureIdx):ob.getChildren(parentStrutureIdx).get(0));
                        Utils.removeDuplicates(sel, false);
                        Collections.sort(sel, (o1, o2)->Integer.compare(o1.getFrame(), o2.getFrame()));
                        int i = 0;
                        while (i+1<sel.size() && sel.get(i+1).getFrame()==sel.get(i).getFrame()+1) ++i;
                        sel = sel.subList(0, i+1);
                        logger.debug("getImage: {}, getXP: {}", parentTrack.get(0).getRawImage(0)!=null, parentTrack.get(0).getExperiment()!=null);
                        
                        // run testing
                        TrackPostFilterSequence tpf=null;
                        if (psc instanceof ProcessingSchemeWithTracking) tpf = ((ProcessingSchemeWithTracking)psc).getTrackPostFilters();
                        if (segAndTrack) ((TrackerSegmenter)plugin).segmentAndTrack(structureIdx, parentTrack, psc.getTrackPreFilters(true), psc.getPostFilters());
                        else ((Tracker)plugin).track(structureIdx, parentTrack);
                        if (tpf!=null) tpf.filter(structureIdx, parentTrack);

                        
                    }
                    run display command (move it to this class)
                }
            }
        });
        return item;
    }

    public static JMenuItem getTransformationTest(String name, Position position, int transfoIdx, boolean showAllSteps) {
        JMenuItem item = new JMenuItem(name);
        item.setAction(new AbstractAction(item.getActionCommand()) {
            @Override
            public void actionPerformed(ActionEvent ae) {
                InputImagesImpl images = position.getInputImages().duplicate();
                
                PreProcessingChain ppc = position.getPreProcessingChain();
                List<TransformationPluginParameter<Transformation>> transList = ppc.getTransformations(false);
                for (int i = 0; i<=transfoIdx; ++i) {
                    TransformationPluginParameter<Transformation> tpp = transList.get(i);
                    if (tpp.isActivated() || i==transfoIdx) {
                        if ((i==0 && showAllSteps) || (i==transfoIdx && !showAllSteps)) { // show before
                            int[] channels =null;
                            if (!showAllSteps) {
                                channels = tpp.getOutputChannels();
                                if (channels==null) channels = new int[]{tpp.getInputChannel()};
                            }
                            Image[][] imagesTC = images.getImagesTC(0, position.getFrameNumber(false), channels);
                            ArrayUtil.apply(imagesTC, a -> ArrayUtil.apply(a, im -> im.duplicate()));
                            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage5D("before: "+tpp.getPluginName(), imagesTC);
                        }
                        Transformation transfo = tpp.instanciatePlugin();
                        transfo.setTestMode(i==transfoIdx);
                        logger.debug("Test Transfo: adding transformation: {} of class: {} to field: {}, input channel:{}, output channel: {}", transfo, transfo.getClass(), position.getName(), tpp.getInputChannel(), tpp.getOutputChannels());
                        if (transfo instanceof ConfigurableTransformation) ((ConfigurableTransformation)transfo).computeConfigurationData(tpp.getInputChannel(), images);
                        images.addTransformation(tpp.getInputChannel(), tpp.getOutputChannels(), transfo);
                        
                        if (showAllSteps || i==transfoIdx) {
                            int[] outputChannels =null;
                            if (!showAllSteps) {
                                outputChannels = tpp.getOutputChannels();
                                if (outputChannels==null) {
                                    if (transfo instanceof MultichannelTransformation && ((MultichannelTransformation)transfo).getOutputChannelSelectionMode()==MultichannelTransformation.SelectionMode.ALL) outputChannels = ArrayUtil.generateIntegerArray(images.getChannelNumber());
                                    else outputChannels = new int[]{tpp.getInputChannel()}; 
                                }
                            }
                            Image[][] imagesTC = images.getImagesTC(0, position.getFrameNumber(false), outputChannels);
                            if (i!=transfoIdx) ArrayUtil.apply(imagesTC, a -> ArrayUtil.apply(a, im -> im.duplicate()));
                            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage5D("after: "+tpp.getPluginName(), imagesTC);
                        }
                    }
                }
                
            }
        });
        return item;
    }
    public static JMenuItem getTransformationTestOnCurrentImage(String name, Position position, int transfoIdx) {
        JMenuItem item = new JMenuItem(name);
        item.setAction(new AbstractAction(item.getActionCommand()) {
            @Override
            public void actionPerformed(ActionEvent ae) {
                Image[][] imCT = ImageWindowManagerFactory.getImageManager().getDisplayer().getCurrentImageCT();
                if (imCT==null) {
                    logger.warn("No active image");
                    return;
                }
                logger.debug("current image has: {} frames, {} channels, {} slices", imCT[0].length, imCT.length, imCT[0][0].sizeZ());
                MemoryImageContainer cont = new MemoryImageContainer(imCT);
                logger.debug("container: {} frames, {} channels", cont.getFrameNumber(), cont.getChannelNumber());
                InputImagesImpl images = cont.getInputImages(position.getName());
                logger.debug("images: {} frames, {} channels", images.getFrameNumber(), images.getChannelNumber());
                
                PreProcessingChain ppc = position.getPreProcessingChain();
                List<TransformationPluginParameter<Transformation>> transList = ppc.getTransformations(false);
                TransformationPluginParameter<Transformation> tpp = transList.get(transfoIdx);
                Transformation transfo = tpp.instanciatePlugin();

                int input = tpp.getInputChannel();
                if (images.getChannelNumber()<=input) {
                    if (images.getChannelNumber()==1) input=0;
                    else {
                        logger.debug("transformation need to be applied on channel: {}, be only {} channels in current image", input, images.getChannelNumber());
                        return;
                    }
                }
                int[] output = tpp.getOutputChannels();
                if (output!=null && output[ArrayUtil.max(output)]>=images.getChannelNumber()) {
                    List<Integer> outputL = Utils.toList(output);
                    outputL.removeIf(idx -> idx>=images.getChannelNumber());
                    output = Utils.toArray(outputL, false);
                } else if (output == null ) {
                    if (transfo instanceof MultichannelTransformation && ((MultichannelTransformation)transfo).getOutputChannelSelectionMode()==MultichannelTransformation.SelectionMode.ALL) output = ArrayUtil.generateIntegerArray(images.getChannelNumber());
                    else output = new int[]{input};
                }

                logger.debug("Test Transfo: adding transformation: {} of class: {} to field: {}, input channel:{}, output channel: {}, isConfigured?: {}", transfo, transfo.getClass(), position.getName(), input, output);
                transfo.setTestMode(true);
                if (transfo instanceof ConfigurableTransformation) ((ConfigurableTransformation)transfo).computeConfigurationData(tpp.getInputChannel(), images);
                      
                //tpp.setConfigurationData(transfo.getConfigurationData());
                images.addTransformation(input, output, transfo);

                Image[][] imagesTC = images.getImagesTC(0, images.getFrameNumber(), ArrayUtil.generateIntegerArray(images.getChannelNumber()));
                //ArrayUtil.apply(imagesTC, a -> ArrayUtil.apply(a, im -> im.duplicate()));
                ImageWindowManagerFactory.getImageManager().getDisplayer().showImage5D("after: "+tpp.getPluginName(), imagesTC);
            }
        });
        return item;
    }
    
}
