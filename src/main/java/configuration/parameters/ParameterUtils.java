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
package configuration.parameters;

import boa.gui.GUI;
import dataStructure.configuration.Experiment;
import boa.gui.configuration.ConfigurationTreeModel;
import boa.gui.configuration.TreeModelContainer;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageWindowManager;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import static configuration.parameters.Parameter.logger;
import core.Processor;
import dataStructure.configuration.MicroscopyField;
import dataStructure.configuration.PreProcessingChain;
import dataStructure.configuration.Structure;
import dataStructure.containers.InputImage;
import dataStructure.containers.InputImages;
import dataStructure.containers.InputImagesImpl;
import dataStructure.containers.MemoryImageContainer;
import dataStructure.objects.Selection;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import image.BoundingBox;
import image.Image;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import plugins.ParameterSetup;
import plugins.ParameterSetupTracker;
import plugins.ProcessingScheme;
import plugins.Segmenter;
import plugins.Tracker;
import plugins.TrackerSegmenter;
import plugins.Transformation;
import static plugins.Transformation.SelectionMode.ALL;
import static plugins.Transformation.SelectionMode.SAME;
import plugins.UseMaps;
import plugins.plugins.processingScheme.SegmentAndTrack;
import plugins.plugins.processingScheme.SegmentOnly;
import plugins.plugins.processingScheme.SegmentThenTrack;
import utils.ArrayUtil;
import utils.Pair;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class ParameterUtils {

    public static ConfigurationTreeModel getModel(Parameter p) {
        if (p instanceof TreeModelContainer) {
            return ((TreeModelContainer) p).getModel();
        }
        Parameter root = p;
        while (root.getParent() != null) {
            root = (Parameter) root.getParent();
            if (root instanceof TreeModelContainer) {
                return ((TreeModelContainer) root).getModel();
            }
        }
        return null;
    }

    public static Experiment getExperiment(Parameter p) {
        if (p instanceof Experiment) {
            return (Experiment) p;
        }
        Parameter root = p;
        //logger.trace("getExperiment: {}", p.getName());
        while (root.getParent() != null) {
            root = (Parameter) root.getParent();
            if (root instanceof Experiment) {
                return (Experiment) root;
            }
        }
        return null;
    }
    
    public static MicroscopyField getMicroscopyField(Parameter p) {
        //logger.debug("get microscopy field from tree... {}", p.getName());
        if (p instanceof MicroscopyField) {
            return (MicroscopyField) p;
        }
        Parameter parent = p;
        while (parent.getParent() != null) {
            parent = (Parameter) parent.getParent();
            //logger.debug("get microscopy field from tree... {}", parent.getName());
            if (parent instanceof MicroscopyField) {
                return (MicroscopyField) parent;
            }
        }
        return null;
    }
    
    public static int getTimePointNumber(Parameter p, boolean useRawInputFrames) {
        MicroscopyField f = getMicroscopyField(p);
        if (f!=null) {
            return f.getTimePointNumber(useRawInputFrames);
        } else {
            logger.warn("parameter: {}, no microscopy Field found in tree to get timePoint number", p.getName());
            /*Experiment xp = getExperiment(p);
            if (xp==null) {
                logger.warn("parameter: {}, no experiment found in tree to get timePoint number", p.getName());
                return 0;
            }
            else return xp.getTimePointNumber(afterTrim);*/
            return -1;
        }
    }

    public static boolean setContent(Parameter[] recieve, Parameter[] give) {
        if (recieve==null || give== null || recieve.length!=give.length) return false;
        boolean ok = true;
        for (int i = 0; i < recieve.length; i++) {
            try {
                recieve[i].setContentFrom(give[i]);
            } catch (Error e) {
                logger.debug("set content list error @ {} : r={} / s={}", i, recieve[i]!=null ? recieve[i].getName() : "null", give[i]!=null ? give[i].getName() : "null");
                logger.error("set content error :", e);
                ok = false;
            }
        }
        return ok;
    }
    public static boolean sameContent(List<Parameter> recieve, List<Parameter> give) {return sameContent(recieve, give, null);}
    public static boolean sameContent(List<Parameter> recieve, List<Parameter> give, String message) {
        if (recieve==null && give!=null) {
            if (message!=null) logger.debug(message+" 1st null");
            return false;
        }
        if (recieve!=null && give==null) {
            if (message!=null) logger.debug(message+" 2nd null");
            return false;
        }
        if (recieve==null && give==null) return true;
        if (recieve.size()!=give.size()) {
            if (message!=null) logger.debug(message+" differ in size: {} vs {}", recieve.size(), give.size());
            return false;
        }
        for (int i = 0; i < recieve.size(); i++) {
            if (!recieve.get(i).sameContent(give.get(i))) {
                if (message!=null) logger.debug(message+" differ at index: {}", i);
                return false;
            }
        }
        return true;
    }
    public static boolean setContent(List<Parameter> recieve, List<Parameter> give) {
        if (recieve==null || give== null || recieve.size()!=give.size()) {
            setContentMap(recieve, give);
            return false;
        }
        boolean ok = true;
        for (int i = 0; i < recieve.size(); i++) {
            try {
                recieve.get(i).setContentFrom(give.get(i));
            } catch (IllegalArgumentException e) {
                logger.debug("set content list error @ {} : r={} / s={}", i, recieve.get(i)!=null ? recieve.get(i).getName() : "null", give.get(i)!=null ? give.get(i).getName() : "null");
                logger.error("set content list error : ", e);
                ok = false;
            }
        }
        return ok;
    }
    private static void setContentMap(List<Parameter> recieve, List<Parameter> give) {
        if (recieve==null || recieve.isEmpty() || give==null || give.isEmpty()) return;
        Map<String, Parameter> recieveMap = recieve.stream().collect(Collectors.toMap(Parameter::getName, Function.identity()));
        for (Parameter p : give) {
            if (recieveMap.containsKey(p.getName())) {
                Parameter r = recieveMap.get(p.getName());
                if (r.getClass()==p.getClass()) r.setContentFrom(p);
            }
        }
    }

    public static Parameter[] duplicateArray(Parameter[] parameters) {
        if (parameters==null) return null;
        Parameter[] res = new Parameter[parameters.length];
        for (int i = 0; i < parameters.length; ++i) {
            res[i] = parameters[i].duplicate();
        }
        return res;
    }
    
    public static List<Parameter> duplicateList(List<Parameter> parameters) {
        if (parameters==null) return null;
        ArrayList<Parameter> res = new ArrayList<Parameter>(parameters.size());
        for (Parameter p : parameters) res.add(p.duplicate());
        return res;
    }

    public static boolean arraysEqual(int[] array1, int[] array2) {
        if (array1.length == array2.length) {
            for (int i = 0; i < array1.length; ++i) {
                if (array1[i] != array2[i]) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    public static String[] createChoiceList(int startElement, int endElement) {
        String[] res = new String[endElement - startElement + 1];
        int paddingSize = String.valueOf(endElement).length();
        for (int i = startElement; i <= endElement; ++i) {
            res[i - startElement] = Utils.formatInteger(paddingSize, i);
        }
        return res;
    }

    public static Object duplicateConfigurationData(Object in) {
        //logger.debug("duplicating config data of class: {}, {} ", in.getClass(), in);
        if (in != null) {
            if (in instanceof Number) {
                if (in instanceof Double || in instanceof Float) {
                    return ((Number) in).doubleValue();
                } else if (in instanceof Long) {
                    return ((Number) in).longValue();
                } else {
                    return ((Number) in).intValue();
                }
            } else if (in instanceof String) {
                return (String) in;
            } else if (in instanceof int[]) return copyArray((int[])in);
            else if (in instanceof double[]) return copyArray((double[])in);
            else if (in.getClass().isArray()) {
                if (in instanceof Object[]) {
                    return duplicateConfigurationDataArray((Object[]) in);
                }
            } else if (in.getClass()==ArrayList.class) {
                return duplicateConfigurationDataList((ArrayList)in);
            }
        }
        return null;
    }

    private static int[] copyArray(int[] source) {
        int[] res = new int[source.length];
        System.arraycopy(source, 0, res, 0, source.length);
        return res;
    }
    private static double[] copyArray(double[] source) {
        double[] res = new double[source.length];
        System.arraycopy(source, 0, res, 0, source.length);
        return res;
    } 

    public static Object[] duplicateConfigurationDataArray(Object[] in) {
        if (in != null) {
            Object[] res = new Object[in.length];
            for (int i = 0; i < res.length; ++i) {
                res[i] = duplicateConfigurationData(in[i]);
            }
            return res;
        } else {
            return null;
        }
    }
    public static ArrayList duplicateConfigurationDataList(List in) {
        if (in != null) {
            ArrayList res = new ArrayList(in.size());
            for (Object o : in) {
                res.add(duplicateConfigurationData(o));
            }
            return res;
        } else {
            return null;
        }
    }
    public static Parameter[] aggregate(Parameter[] array, Parameter... parameters) {
        if (parameters.length==0) return array;
        else {
            Parameter[] res = new Parameter[array.length+parameters.length];
            System.arraycopy(array, 0, res, 0, array.length);
            System.arraycopy(parameters, 0, res, array.length, parameters.length);
            return res;
        }
    }
    
    // Configuration by hints
    public static <T extends Parameter> T getFirstParameterFromParents(Class<T> clazz, Parameter parameter, boolean lookInIndirectParents) {
        if (parameter==null) return null;
        Parameter parent=parameter;
        while (parent.getParent()!=null) {
            parent = ((Parameter)parent.getParent());
            // look in siblings/uncles
            if (lookInIndirectParents && parent instanceof ListParameter) {
                for (Parameter p : ((ListParameter<Parameter>)parent).getActivatedChildren()) if (clazz.equals(p.getClass())) return (T)p;
            } else if (lookInIndirectParents && parent instanceof ContainerParameter) {
                for (Parameter p : ((SimpleContainerParameter)parent).getChildren())  if (clazz.equals(p.getClass())) return (T)p;
            } else  if (clazz.equals(parent.getClass())) return (T)parent;
        }
        return null;
    }
    public static void configureStructureParametersFromParent(Parameter parameter) {
        Structure s = getFirstParameterFromParents(Structure.class, parameter, false);
        if (s!=null) configureStructureParameters(s.getIndex(), parameter);
    }
    public static void configureStructureParameters(final int structureIdxHint, Parameter parameter) {
        if (structureIdxHint==-1) return;
        ParameterConfiguration config = new ParameterConfiguration() {
            public void configure(Parameter p) {
                if (((StructureParameter)p).getSelectedStructureIdx()==-1) {
                    ((StructureParameter)p).setSelectedStructureIdx(structureIdxHint);
                    //logger.debug("Configuring: {}, with value: {}", p.getName(), structureIdxHint);
                }
            }
            public boolean isConfigurable(Parameter p) {
                return p instanceof StructureParameter;
            }
        };
        configureParameter(config, parameter);
    }
    
    public static void configureParameter(final ParameterConfiguration config, Parameter parameter) {       
        if (config.isConfigurable(parameter)) config.configure(parameter);
        else if (parameter instanceof ListParameter) {
            for (Parameter p : ((ListParameter<Parameter>)parameter).getActivatedChildren()) configureParameter(config, p);
        } else if (parameter instanceof ContainerParameter) {
            for (Parameter p : ((SimpleContainerParameter)parameter).getChildren()) configureParameter(config, p);
        }
    }
    public interface ParameterConfiguration {
        public void configure(Parameter p);
        public boolean isConfigurable(Parameter p);
    }
    static Pair<Image, ImageObjectInterface> lastTest;
    public static JMenu getTestMenu(String name, final ParameterSetup ps, Parameter parameter, final Parameter[] parameters, int structureIdx) {
        JMenu subMenu = new JMenu(name);
        List<JMenuItem> items = new ArrayList<>();
        for (int i = 0; i<parameters.length; ++i) { // todo: case of parameters with subparameters -> plain...
            final int idx = i;
            if (ps.canBeTested(parameters[idx].getName())) {
                JMenuItem item = new JMenuItem(parameters[idx].getName());
                item.setAction(new AbstractAction(item.getActionCommand()) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        List<StructureObject> sel = ImageWindowManagerFactory.getImageManager().getSelectedLabileObjects(null);
                        if ((sel == null || sel.isEmpty()) && GUI.getInstance().getSelectedPositions(false).isEmpty()) {
                            GUI.log("Select an object OR position to test parameter");
                        }
                        else {
                            if (sel==null) sel = new ArrayList<>(1);
                            if (sel.isEmpty()) sel.add(GUI.getDBConnection().getDao(GUI.getInstance().getSelectedPositions(false).get(0)).getRoot(0));
                            ProcessingScheme psc=null;
                            PluginParameter pp = ParameterUtils.getFirstParameterFromParents(PluginParameter.class, parameter, false);
                            if (pp.instanciatePlugin() instanceof ProcessingScheme) psc = (ProcessingScheme)pp.instanciatePlugin();
                            else pp = ParameterUtils.getFirstParameterFromParents(PluginParameter.class, pp, false);
                            if (pp.instanciatePlugin() instanceof ProcessingScheme) psc = (ProcessingScheme)pp.instanciatePlugin();
                            StructureObject o = sel.get(0);
                            int parentStrutureIdx = o.getExperiment().getStructure(structureIdx).getParentStructure();
                            int segParentStrutureIdx = o.getExperiment().getStructure(structureIdx).getSegmentationParentStructure();
                            if (ps instanceof Segmenter) { // case segmenter -> segment only & call to test method
                                SegmentOnly so; 
                                if (psc instanceof SegmentOnly) so = (SegmentOnly)psc;
                                else so = new SegmentOnly((Segmenter)ps).setPreFilters(psc.getPreFilters()).setPostFilters(psc.getPostFilters());
                                StructureObject parent = (o.getStructureIdx()>parentStrutureIdx) ? o.getParent(parentStrutureIdx) : o.getChildren(parentStrutureIdx).get(0);
                                Map<StructureObject, StructureObject> dupMap = StructureObjectUtils.duplicateRootTrackAndChangeDAO(parent);
                                parent = dupMap.get(parent); // don't modify object directly. 
                                if (segParentStrutureIdx!=parentStrutureIdx && o.getStructureIdx()==segParentStrutureIdx) {
                                    final List<StructureObject> selF = sel;
                                    parent.getChildren(segParentStrutureIdx).removeIf(oo -> !selF.contains(oo));
                                }
                                sel = new ArrayList<>();
                                sel.add(parent);
                                so.segmentAndTrack(structureIdx, sel, null, (p, s)->((ParameterSetup)s).setTestParameter(parameters[idx].getName()));
                                
                            } else if (ps instanceof Tracker) {
                                boolean segAndTrack = false;
                                if (psc instanceof SegmentAndTrack && ps instanceof TrackerSegmenter) {
                                    if (ps instanceof ParameterSetupTracker) segAndTrack = ((ParameterSetupTracker)ps).runSegmentAndTrack(parameters[idx].getName());
                                    else segAndTrack = true;
                                }
                                // get first continuous parent track
                                sel = Utils.transform(sel, ob -> o.getStructureIdx()>parentStrutureIdx?ob.getParent(parentStrutureIdx):ob.getChildren(parentStrutureIdx).get(0));
                                Utils.removeDuplicates(sel, false);
                                Collections.sort(sel, (o1, o2)->Integer.compare(o1.getFrame(), o2.getFrame()));
                                int i = 0;
                                while (i+1<sel.size() && sel.get(i+1).getFrame()==sel.get(i).getFrame()+1) ++i;
                                sel = sel.subList(0, i+1);
                                Map<StructureObject, StructureObject> dupMap = StructureObjectUtils.createGraphCut(sel);
                                List<StructureObject> parentTrack = Utils.transform(sel, oo->dupMap.get(oo));
                                logger.debug("getImage: {}, getXP: {}", parentTrack.get(0).getRawImage(0)!=null, parentTrack.get(0).getExperiment()!=null);
                                // run testing
                                logger.debug("testing parameter: {}, seg & track: {}", parameters[idx], segAndTrack);
                                logger.debug("parents for testing: {}", Utils.toStringList(parentTrack, oo->oo.getId()+"->"+oo.getTrackHeadId()));
                                ps.setTestParameter(parameters[idx].getName());
                                TrackPostFilterSequence tpf=null;
                                if (psc instanceof SegmentAndTrack) tpf = ((SegmentAndTrack)psc).getTrackPostFilters();
                                if (psc instanceof SegmentThenTrack) tpf = ((SegmentThenTrack)psc).getTrackPostFilters();
                                if (segAndTrack) ((TrackerSegmenter)ps).segmentAndTrack(structureIdx, parentTrack, psc.getPreFilters(), psc.getPostFilters());
                                else ((Tracker)ps).track(structureIdx, parentTrack);
                                if (tpf!=null) tpf.filter(structureIdx, parentTrack, null);
                                
                                // dispay track interactively
                                ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
                                if (lastTest!=null) { // only one interactive image at the same time -> if 2 test on same track -> collapse
                                   iwm.removeImage(lastTest.key);
                                   iwm.removeImageObjectInterface(lastTest.value.getKey()); 
                                   GUI.getInstance().populateSelections(); 
                                   iwm.getDisplayer().close(lastTest.key);
                                   lastTest=null;
                                }
                                ... ici bug affiche sur le meme trackimage que la source. depuis les changement dans le createGraph -> load trackMap ..
                                ImageObjectInterface ioi = iwm.generateTrackMask(parentTrack, structureIdx);
                                Image interactiveImage = ioi.generateRawImage(structureIdx, true);
                                iwm.addImage(interactiveImage, ioi, structureIdx, false, true);
                                lastTest= new Pair<>(interactiveImage, ioi);
                                iwm.addWindowClosedListener(interactiveImage, e->{iwm.removeImage(interactiveImage);iwm.removeImageObjectInterface(ioi.getKey()); GUI.getInstance().populateSelections(); lastTest=null; logger.debug("cloooooose image"); return null;});
                                if (parentStrutureIdx!=segParentStrutureIdx) {
                                    Collection<StructureObject> bact = Utils.flattenMap(StructureObjectUtils.getChildrenMap(parentTrack, segParentStrutureIdx));
                                    Selection bactS = new Selection("testTrackerSelection", o.getDAO().getMasterDAO());
                                    bactS.setColor("Grey");
                                    bactS.addElements(bact);
                                    bactS.setIsDisplayingObjects(true);
                                    GUI.getInstance().addSelection(bactS);
                                    GUI.updateRoiDisplayForSelections(interactiveImage, ioi);
                                }
                                GUI.getInstance().setInteractiveStructureIdx(structureIdx);
                                iwm.displayAllObjects(interactiveImage);
                                iwm.displayAllTracks(interactiveImage);
                            }
                            
                        }
                    }
                });
                items.add(item);
            }
            if (parameters[i] instanceof SimpleContainerParameter) {
                JMenu m = getTestMenu(parameters[i].getName(), ps, parameter, ((SimpleContainerParameter)parameters[i]).getChildren().toArray(new Parameter[0]), structureIdx);
                if (m.getItemCount()>0) items.add(m);
            } else if (parameters[i] instanceof SimpleListParameter) {
                JMenu m = getTestMenu(parameters[i].getName(), ps, parameter, ((ArrayList<? extends Parameter>)((SimpleListParameter)parameters[i]).getChildren()).toArray(new Parameter[0]), structureIdx);
                if (m.getItemCount()>0) items.add(m);
            }
        }
        for (JMenuItem i : items) subMenu.add(i);
        return subMenu;
    }

    public static JMenuItem getTransformationTest(String name, MicroscopyField position, int transfoIdx, boolean showAllSteps) {
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
                            Image[][] imagesTC = images.getImagesTC(0, position.getTimePointNumber(false), channels);
                            ArrayUtil.apply(imagesTC, a -> ArrayUtil.apply(a, im -> im.duplicate()));
                            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage5D("before: "+tpp.getPluginName(), imagesTC);
                        }
                        Transformation transfo = tpp.instanciatePlugin();
                        logger.debug("Test Transfo: adding transformation: {} of class: {} to field: {}, input channel:{}, output channel: {}, isConfigured?: {}", transfo, transfo.getClass(), position.getName(), tpp.getInputChannel(), tpp.getOutputChannels(), transfo.isConfigured(images.getChannelNumber(), images.getFrameNumber()));
                        transfo.computeConfigurationData(tpp.getInputChannel(), images);
                        tpp.setConfigurationData(transfo.getConfigurationData());
                        images.addTransformation(tpp.getInputChannel(), tpp.getOutputChannels(), transfo);
                        
                        if (showAllSteps || i==transfoIdx) {
                            int[] outputChannels =null;
                            if (!showAllSteps) {
                                outputChannels = tpp.getOutputChannels();
                                if (outputChannels==null) {
                                    if (transfo.getOutputChannelSelectionMode()==SAME) outputChannels = new int[]{tpp.getInputChannel()};
                                    else outputChannels = ArrayUtil.generateIntegerArray(images.getChannelNumber());
                                }
                            }
                            Image[][] imagesTC = images.getImagesTC(0, position.getTimePointNumber(false), outputChannels);
                            if (i!=transfoIdx) ArrayUtil.apply(imagesTC, a -> ArrayUtil.apply(a, im -> im.duplicate()));
                            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage5D("after: "+tpp.getPluginName(), imagesTC);
                        }
                    }
                }
                
            }
        });
        return item;
    }
    public static JMenuItem getTransformationTestOnCurrentImage(String name, MicroscopyField position, int transfoIdx) {
        JMenuItem item = new JMenuItem(name);
        item.setAction(new AbstractAction(item.getActionCommand()) {
            @Override
            public void actionPerformed(ActionEvent ae) {
                Image[][] imCT = ImageWindowManagerFactory.getImageManager().getDisplayer().getCurrentImageCT();
                if (imCT==null) {
                    logger.warn("No active image");
                    return;
                }
                logger.debug("current image has: {} frames, {} channels, {} slices", imCT[0].length, imCT.length, imCT[0][0].getSizeZ());
                MemoryImageContainer cont = new MemoryImageContainer(imCT);
                logger.debug("container: {} frames, {} channels", cont.getFrameNumber(), cont.getChannelNumber());
                InputImage[][] inputCT = new InputImage[cont.getChannelNumber()][cont.getFrameNumber()];
                for (int t = 0; t<cont.getFrameNumber(); ++t) {
                    for (int c = 0; c<cont.getChannelNumber(); ++c) {
                        inputCT[c][t] = new InputImage(c, t, t, position.getName(), cont, null);
                    }
                }
                InputImagesImpl images = new InputImagesImpl(inputCT, 0);
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
                    if (transfo.getOutputChannelSelectionMode()==ALL) output = ArrayUtil.generateIntegerArray(images.getChannelNumber());
                    else output = new int[]{input};
                }

                logger.debug("Test Transfo: adding transformation: {} of class: {} to field: {}, input channel:{}, output channel: {}, isConfigured?: {}", transfo, transfo.getClass(), position.getName(), input, output, transfo.isConfigured(images.getChannelNumber(), images.getFrameNumber()));

                transfo.computeConfigurationData(input, images);
                tpp.setConfigurationData(transfo.getConfigurationData());
                images.addTransformation(input, output, transfo);

                Image[][] imagesTC = images.getImagesTC(0, images.getFrameNumber(), ArrayUtil.generateIntegerArray(images.getChannelNumber()));
                //ArrayUtil.apply(imagesTC, a -> ArrayUtil.apply(a, im -> im.duplicate()));
                ImageWindowManagerFactory.getImageManager().getDisplayer().showImage5D("after: "+tpp.getPluginName(), imagesTC);
            }
        });
        return item;
    }
    
}
