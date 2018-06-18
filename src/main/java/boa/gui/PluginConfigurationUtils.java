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
import boa.configuration.experiment.Experiment;
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
import boa.gui.image_interaction.InteractiveImage;
import boa.gui.image_interaction.ImageWindowManager;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import static boa.gui.image_interaction.ImageWindowManagerFactory.getImageManager;
import boa.gui.image_interaction.Kymograph;
import boa.image.Image;
import boa.plugins.ConfigurableTransformation;
import boa.plugins.ImageProcessingPlugin;
import boa.plugins.MultichannelTransformation;
import boa.plugins.Plugin;
import boa.plugins.ProcessingScheme;
import boa.plugins.ProcessingSchemeWithTracking;
import boa.plugins.Segmenter;
import boa.plugins.TestableProcessingPlugin;
import boa.plugins.TestableProcessingPlugin.TestDataStore;
import static boa.plugins.TestableProcessingPlugin.buildIntermediateImages;
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
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 *
 * @author jollion
 */
public class PluginConfigurationUtils {
    
    public static Map<StructureObject, TestDataStore> testImageProcessingPlugin(final ImageProcessingPlugin plugin, Experiment xp, int structureIdx, List<StructureObject> parentSelection, boolean trackOnly) {
        ProcessingScheme psc=xp.getStructure(structureIdx).getProcessingScheme();
        
        // get parent objects -> create graph cut
        StructureObject o = parentSelection.get(0);
        int parentStrutureIdx = o.getExperiment().getStructure(structureIdx).getParentStructure();
        int segParentStrutureIdx = o.getExperiment().getStructure(structureIdx).getSegmentationParentStructure();
        Function<StructureObject, StructureObject> getParent = c -> (c.getStructureIdx()>parentStrutureIdx) ? c.getParent(parentStrutureIdx) : c.getChildren(parentStrutureIdx).get(0);
        List<StructureObject> wholeParentTrack = StructureObjectUtils.getTrack( getParent.apply(o).getTrackHead(), false);
        Map<String, StructureObject> dupMap = StructureObjectUtils.createGraphCut(wholeParentTrack, true, true);  // don't modify object directly. 
        List<StructureObject> wholeParentTrackDup = wholeParentTrack.stream().map(p->dupMap.get(p.getId())).collect(Collectors.toList());
        List<StructureObject> parentTrackDup = parentSelection.stream().map(getParent).distinct().map(p->dupMap.get(p.getId())).sorted().collect(Collectors.toList());

        // generate data store for test images
        Map<StructureObject, TestDataStore> stores = HashMapGetCreate.getRedirectedMap(so->new TestDataStore(so), HashMapGetCreate.Syncronization.SYNC_ON_MAP);
        if (plugin instanceof TestableProcessingPlugin) ((TestableProcessingPlugin)plugin).setTestDataStore(stores);
        parentTrackDup.forEach(p->stores.get(p).addIntermediateImage("input", p.getRawImage(structureIdx))); // add input image


        logger.debug("test processing: sel {}", parentSelection);
        logger.debug("test processing: parent track: {}", parentTrackDup);
        if (plugin instanceof Segmenter) { // case segmenter -> segment only & call to test method

            // run pre-filters on whole track -> some track preFilters need whole track to be effective. todo : parameter to limit ? 
            boolean runPreFiltersOnWholeTrack = !psc.getTrackPreFilters(false).isEmpty() || plugin instanceof TrackParametrizable; 
            if (runPreFiltersOnWholeTrack)  psc.getTrackPreFilters(true).filter(structureIdx, wholeParentTrackDup);
            else  psc.getTrackPreFilters(true).filter(structureIdx, parentTrackDup); // only segmentation pre-filter -> run only on parentTrack
            parentTrackDup.forEach(p->stores.get(p).addIntermediateImage("pre-filtered", p.getPreFilteredImage(structureIdx))); // add preFiltered image
            
            TrackParametrizer  applyToSeg = TrackParametrizable.getTrackParametrizer(structureIdx, wholeParentTrackDup, (Segmenter)plugin);
            SegmentOnly so; 
            if (psc instanceof SegmentOnly) {
                so = (SegmentOnly)psc;
            } else {
                so = new SegmentOnly((Segmenter)plugin).setPostFilters(psc.getPostFilters());
            }
            
            if (segParentStrutureIdx!=parentStrutureIdx && o.getStructureIdx()==segParentStrutureIdx) { // when selected objects are segmentation parent -> remove all others
                Set<StructureObject> selectedObjects = parentSelection.stream().map(s->dupMap.get(s.getId())).collect(Collectors.toSet());
                parentTrackDup.forEach(p->p.getChildren(segParentStrutureIdx).removeIf(c->!selectedObjects.contains(c)));
                logger.debug("remaining segmentation parents: {}", Utils.toStringList(parentTrackDup, p->p.getChildren(segParentStrutureIdx)));
            }
            TrackParametrizer  apply = (p, s)-> {
                if (s instanceof TestableProcessingPlugin) ((TestableProcessingPlugin)s).setTestDataStore(stores);
                if (applyToSeg!=null) applyToSeg.apply(p, s); 
            };
            so.segmentAndTrack(structureIdx, parentTrackDup, apply); // won't run pre-filters

        } else if (plugin instanceof Tracker) {
            
            // get continuous parent track
            int minF = parentTrackDup.stream().mapToInt(p->p.getFrame()).min().getAsInt();
            int maxF = parentTrackDup.stream().mapToInt(p->p.getFrame()).max().getAsInt();
            parentTrackDup = wholeParentTrackDup.stream().filter(p->p.getFrame()>=minF && p.getFrame()<=maxF).collect(Collectors.toList());

            // run testing
            if (!trackOnly) {
                if (!psc.getTrackPreFilters(false).isEmpty()) { // run pre-filters on whole track -> some track preFilters need whole track to be effective. TODO : parameter to limit ? 
                    psc.getTrackPreFilters(true).filter(structureIdx, wholeParentTrackDup);
                    psc.getTrackPreFilters(false).removeAll();
                }
                psc.segmentAndTrack(structureIdx, parentTrackDup);
                //((TrackerSegmenter)plugin).segmentAndTrack(structureIdx, parentTrackDup, psc.getTrackPreFilters(true), psc.getPostFilters());
            } else {
                ((Tracker)plugin).track(structureIdx, parentTrackDup);
                TrackPostFilterSequence tpf= (psc instanceof ProcessingSchemeWithTracking) ? ((ProcessingSchemeWithTracking)psc).getTrackPostFilters() : null;
                if (tpf!=null) tpf.filter(structureIdx, parentTrackDup);
            }
            
        }
        return stores;
    }
    public static List<JMenuItem> getTestCommand(ImageProcessingPlugin plugin, Experiment xp, int structureIdx) {
        Consumer<Boolean> performTest = b-> {
            List<StructureObject> sel = ImageWindowManagerFactory.getImageManager().getSelectedLabileObjects(null);
            if ((sel == null || sel.isEmpty()) && GUI.getInstance().getSelectedPositions(false).isEmpty()) {
                GUI.log("No selected objects : test will be run on first object");
            }
            String pos = GUI.getInstance().getSelectedPositions(false).isEmpty() ? GUI.getDBConnection().getExperiment().getPosition(0).getName() : GUI.getInstance().getSelectedPositions(false).get(0);
            if (sel==null) sel = new ArrayList<>(1);
            if (sel.isEmpty()) sel.add(GUI.getDBConnection().getDao(pos).getRoot(0));

            Map<StructureObject, TestDataStore> stores = testImageProcessingPlugin(plugin, xp, structureIdx, sel, b);
            if (stores!=null) displayIntermediateImages(stores, structureIdx);
        };
        List<JMenuItem> res = new ArrayList<>();
        if (plugin instanceof Tracker) {
            JMenuItem trackOnly = new JMenuItem("Test Track Only");
            trackOnly.setAction(new AbstractAction(trackOnly.getActionCommand()) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    performTest.accept(true);
                }
            });
            res.add(trackOnly);
            JMenuItem segTrack = new JMenuItem("Test Segmentation and Tracking");
            segTrack.setAction(new AbstractAction(segTrack.getActionCommand()) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    performTest.accept(false);
                }
            });
            res.add(segTrack);
        } else if (plugin instanceof Segmenter) {
            JMenuItem item = new JMenuItem("Test Segmenter");
            item.setAction(new AbstractAction(item.getActionCommand()) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    performTest.accept(true);
                }
            });
            res.add(item);
        } else throw new IllegalArgumentException("Processing plugin not supported for testing");
        return res;
    }
    
    public static void displayIntermediateImages(Map<StructureObject, TestDataStore> stores, int structureIdx) {
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        int parentStructureIdx = stores.values().stream().findAny().get().getParent().getExperiment().getStructure(structureIdx).getParentStructure();
        int segParentStrutureIdx = stores.values().stream().findAny().get().getParent().getExperiment().getStructure(structureIdx).getSegmentationParentStructure();
        
        Pair<InteractiveImage, List<Image>> res = buildIntermediateImages(stores.values(), parentStructureIdx);
        ImageWindowManagerFactory.getImageManager().setDisplayImageLimit(Math.max(ImageWindowManagerFactory.getImageManager().getDisplayImageLimit(), res.value.size()+1));
        res.value.forEach((image) -> {
            iwm.addImage(image, res.key, structureIdx, true);
            iwm.addTestData(image, stores.values());
        });
        if (parentStructureIdx!=segParentStrutureIdx) { // add a selection to diplay the segmentation parent on the intermediate image
            List<StructureObject> parentTrack = stores.values().stream().map(s->s.getParent().getParent(parentStructureIdx)).distinct().sorted().collect(Collectors.toList());
            Collection<StructureObject> bact = Utils.flattenMap(StructureObjectUtils.getChildrenByFrame(parentTrack, segParentStrutureIdx));
            //Selection bactS = parentTrack.get(0).getDAO().getMasterDAO().getSelectionDAO().getOrCreate("testTrackerSelection", true);
            Selection bactS = new Selection("testTrackerSelection", parentTrack.get(0).getDAO().getMasterDAO());
            bactS.setColor("Grey");
            bactS.addElements(bact);
            bactS.setIsDisplayingObjects(true);
            GUI.getInstance().addSelection(bactS);
            res.value.forEach((image) -> GUI.updateRoiDisplayForSelections(image, res.key));
        }
        getImageManager().setInteractiveStructure(structureIdx);
        res.value.forEach((image) -> {
            iwm.displayAllObjects(image);
            iwm.displayAllTracks(image);
        });
        
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
                                    if (transfo instanceof MultichannelTransformation && ((MultichannelTransformation)transfo).getOutputChannelSelectionMode()==MultichannelTransformation.OUTPUT_SELECTION_MODE.ALL) outputChannels = ArrayUtil.generateIntegerArray(images.getChannelNumber());
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
                    if (transfo instanceof MultichannelTransformation && ((MultichannelTransformation)transfo).getOutputChannelSelectionMode()==MultichannelTransformation.OUTPUT_SELECTION_MODE.ALL) output = ArrayUtil.generateIntegerArray(images.getChannelNumber());
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
